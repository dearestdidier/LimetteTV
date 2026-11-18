package com.limette.tv.data.feiniu

import com.limette.tv.data.remote.RemoteFileClient
import com.limette.tv.data.webdav.WebDavFile
import com.limette.tv.domain.model.MediaSource

/**
 * 把飞牛影视账号包装成统一的 RemoteFileClient。
 * 与 SMB/WebDAV 不同，飞牛没有"目录树"概念：listAllVideoFiles 直接返回影视库条目，
 * 每个条目的 path 就是它的 guid，播放时由 MediaUrlResolver 交给 FeiNiuClient 动态换取 play_link。
 */
class FeiNiuFileClient(
    private val source: MediaSource,
    private val feiNiuClient: FeiNiuClient
) : RemoteFileClient {

    override suspend fun listFiles(relativePath: String): Result<List<WebDavFile>> =
        listAllVideoFiles()

    override suspend fun listAllVideoFiles(onProgress: ((Int) -> Unit)?): Result<List<WebDavFile>> =
        feiNiuClient.listVideos(source, onProgress).map { list ->
            list.map { media ->
                WebDavFile(
                    name = media.guid,
                    path = media.guid,
                    isDirectory = false,
                    size = media.fileSize,
                    displayTitle = media.title,
                    posterUrl = media.posterUrl,
                    year = media.year,
                    seasonNumber = media.seasonNumber,
                    episodeNumber = media.episodeNumber
                )
            }
        }

    /** 飞牛播放链接需动态构建，这里返回占位；实际解析走 MediaUrlResolver 的 FENIU 分支。 */
    override fun getFileUrl(relativePath: String): String =
        "${source.baseUrl()}/v/api/v1/play/play#$relativePath"

    override suspend fun getCoverImage(directoryPath: String): String? = null

    override suspend fun readTextFile(relativePath: String): String? = null

    override suspend fun fileExists(relativePath: String): Boolean = true
}
