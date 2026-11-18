package com.limette.tv.domain.service

import android.util.Base64
import com.limette.tv.data.feiniu.FeiNiuClient
import com.limette.tv.data.local.database.dao.WebDavMediaDao
import com.limette.tv.data.preferences.MediaSourceRepository
import com.limette.tv.data.remote.RemoteFileClient
import com.limette.tv.data.remote.RemoteFileClientFactory
import com.limette.tv.domain.model.MediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class SubtitleInfo(
    val url: String,
    val language: String?,
    val label: String,
    val mimeType: String
)

data class AudioTrackInfo(val index: Int, val label: String)

data class MediaPlaybackInfo(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleInfo> = emptyList(),
    val audioTracks: List<AudioTrackInfo> = emptyList()
)

/** 把数据库里的相对文件路径解析成播放器可读取的 URL（含鉴权头与外挂字幕）。 */
@Singleton
class MediaUrlResolver @Inject constructor(
    private val mediaSourceRepository: MediaSourceRepository,
    private val webDavMediaDao: WebDavMediaDao,
    private val clientFactory: RemoteFileClientFactory,
    private val feiNiuClient: FeiNiuClient
) {
    suspend fun resolvePlaybackUrl(videoPath: String): Result<MediaPlaybackInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                // filePath 形如 "<sourceId>::<相对路径>"，可精确反查媒体源
                val (embeddedSourceId, relativePath) = if (videoPath.contains("::")) {
                    val parts = videoPath.split("::", limit = 2)
                    parts[0] to parts[1]
                } else "" to videoPath

                val entity = webDavMediaDao.getByFilePath(videoPath)
                val sourceId = embeddedSourceId.ifBlank { entity?.libraryId.orEmpty() }
                val source = sourceId.takeIf { it.isNotBlank() }?.let { mediaSourceRepository.getById(it) }
                    ?: mediaSourceRepository.getSources().firstOrNull { it.enabled }
                    ?: throw IllegalStateException("尚未添加或启用任何媒体源")

                when (source.type) {
                    MediaSource.SourceType.FENIU -> {
                        // 飞牛 play_link 有时效，播放时动态换取真实 m3u8 地址并附 token
                        val playback = feiNiuClient.buildPlaybackInfo(source, relativePath)
                            .getOrElse { throw it }
                        MediaPlaybackInfo(url = playback.url, headers = playback.headers)
                    }

                    MediaSource.SourceType.SMB, MediaSource.SourceType.WEBDAV -> {
                        val client = clientFactory.create(source)
                        val headers = if (source.type == MediaSource.SourceType.WEBDAV &&
                            source.username.isNotBlank()
                        ) {
                            mapOf("Authorization" to basicAuth(source.username, source.password))
                        } else emptyMap()
                        MediaPlaybackInfo(
                            url = client.getFileUrl(relativePath),
                            headers = headers,
                            subtitles = findSubtitles(client, relativePath)
                        )
                    }
                }
            }
        }

    private suspend fun findSubtitles(client: RemoteFileClient, videoPath: String): List<SubtitleInfo> {
        val parent = videoPath.substringBeforeLast('/', "")
        val baseName = videoPath.substringAfterLast('/').substringBeforeLast('.')
        val entries = runCatching { client.listFiles(parent).getOrNull().orEmpty() }.getOrDefault(emptyList())
        return entries.filter { entry ->
            !entry.isDirectory &&
                entry.name.startsWith(baseName, ignoreCase = true) &&
                SUBTITLE_EXTENSIONS.any { entry.name.endsWith(it, ignoreCase = true) }
        }.map { entry ->
            val ext = entry.name.substringAfterLast('.').lowercase()
            SubtitleInfo(
                url = client.getFileUrl(entry.path),
                language = if (entry.name.contains("chi", true) || entry.name.contains("zh", true) ||
                    entry.name.contains("中", true)
                ) "zh" else null,
                label = entry.name,
                mimeType = when (ext) {
                    "srt" -> "application/x-subrip"
                    "vtt" -> "text/vtt"
                    "ass", "ssa" -> "text/x-ssa"
                    else -> "text/plain"
                }
            )
        }
    }

    fun basicAuth(username: String, password: String): String =
        "Basic " + Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)

    private fun encodePath(path: String): String =
        path.trimStart('/').split('/').joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }

    companion object {
        private val SUBTITLE_EXTENSIONS = listOf(".srt", ".ass", ".ssa", ".vtt", ".sub")
    }
}
