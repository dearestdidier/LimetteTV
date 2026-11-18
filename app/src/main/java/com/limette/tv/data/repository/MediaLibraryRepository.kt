package com.limette.tv.data.repository

import com.limette.tv.data.feiniu.FeiNiuClient
import com.limette.tv.data.local.database.dao.WebDavMediaDao
import com.limette.tv.data.local.database.entity.WebDavMediaEntity
import com.limette.tv.data.preferences.MediaSourceRepository
import com.limette.tv.data.remote.LocalImageAuth
import com.limette.tv.data.remote.RemoteFileClientFactory
import com.limette.tv.data.webdav.WebDavFile
import com.limette.tv.domain.model.MediaSource
import com.limette.tv.domain.service.ThumbnailResolver
import com.limette.tv.utils.LocalMediaParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ScanState {
    data object Idle : ScanState
    data class Running(val found: Int, val currentSource: String) : ScanState
    data class Done(val total: Int) : ScanState
    data class Failed(val message: String) : ScanState
}

/**
 * 扫描所有启用的媒体源（SMB / WebDAV），把视频文件写入本地数据库。
 * 不做任何在线刮削；封面来自飞牛影视或 NAS 目录内的本地图片。
 */
@Singleton
class MediaLibraryRepository @Inject constructor(
    private val mediaSourceRepository: MediaSourceRepository,
    private val webDavMediaDao: WebDavMediaDao,
    private val clientFactory: RemoteFileClientFactory,
    private val feiNiuClient: FeiNiuClient,
    private val thumbnailResolver: ThumbnailResolver
) {
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    suspend fun scanAll(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val sources = mediaSourceRepository.getSources().filter { it.enabled }
            // 飞牛影视海报索引（未配置或失败时静默降级到本地封面 / 渐变卡片）
            runCatching { feiNiuClient.refreshIndex() }

            var total = 0
            sources.forEach { source ->
                _scanState.value = ScanState.Running(0, source.name)
                LocalImageAuth.register(source)
                total += scanSource(source)
            }
            _scanState.value = ScanState.Done(total)
            total
        }.onFailure { e -> _scanState.value = ScanState.Failed(e.message ?: "扫描失败") }
    }

    private suspend fun scanSource(source: MediaSource): Int {
        val client = clientFactory.create(source)
        val files = client.listAllVideoFiles { found ->
            _scanState.value = ScanState.Running(found, source.name)
        }.getOrElse { throw it }

        val existing = webDavMediaDao.getFingerprintsByLibraryId(source.id)
            .associateBy { it.filePath }
        val seenPaths = HashSet<String>(files.size)
        val batch = ArrayList<WebDavMediaEntity>(files.size)

        files.forEach { file ->
            // 路径加源前缀，保证多源下 filePath 全局唯一，播放时可精确反查媒体源
            val storedPath = "${source.id}::${file.path}"
            if (!seenPaths.add(storedPath)) return@forEach
            val fingerprint = fingerprint(file)
            val old = existing[storedPath]
            val parsed = LocalMediaParser.parse(file.name, file.path)
            val poster = if (old?.fileFingerprint == fingerprint) {
                // 文件未变化，沿用已有封面，避免每次扫描重复请求 NAS
                webDavMediaDao.getByFilePath(storedPath)?.posterUrl
                    ?: thumbnailResolver.resolve(file, client)
            } else {
                thumbnailResolver.resolve(file, client)
            }

            batch.add(
                WebDavMediaEntity(
                    id = storedPath,
                    libraryId = source.id,
                    title = parsed.title,
                    originalTitle = file.name,
                    posterUrl = poster,
                    backdropUrl = poster,
                    year = parsed.year,
                    type = parsed.type,
                    isMovie = parsed.episodeNumber == null,
                    seasonNumber = parsed.seasonNumber,
                    episodeNumber = parsed.episodeNumber,
                    filePath = storedPath,
                    fileName = file.name,
                    fileSize = file.size,
                    fileFingerprint = fingerprint,
                    source = when (source.type) {
                        MediaSource.SourceType.SMB -> "smb"
                        MediaSource.SourceType.WEBDAV -> "webdav"
                    }
                )
            )
        }

        // 清掉已从 NAS 删除的文件
        val removed = existing.keys.filterNot { it in seenPaths }
        if (removed.isNotEmpty()) webDavMediaDao.deleteByFilePaths(source.id, removed)
        if (batch.isNotEmpty()) webDavMediaDao.insertAll(batch)
        return batch.size
    }

    /** 删除某个媒体源的全部索引数据。 */
    suspend fun clearSource(sourceId: String) {
        webDavMediaDao.deleteByLibraryId(sourceId)
    }

    /** 清理已不存在的媒体源残留数据。 */
    suspend fun pruneOrphanSources() {
        val validIds = mediaSourceRepository.getSources().map { it.id }.toSet()
        webDavMediaDao.getAllSync().map { it.libraryId }.distinct()
            .filterNot { it in validIds }
            .forEach { webDavMediaDao.deleteByLibraryId(it) }
    }

    suspend fun totalCount(): Int = webDavMediaDao.getAllSync().size

    private fun fingerprint(file: WebDavFile): String {
        val raw = "${file.path}|${file.size}|${file.lastModified}"
        return MessageDigest.getInstance("MD5")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
