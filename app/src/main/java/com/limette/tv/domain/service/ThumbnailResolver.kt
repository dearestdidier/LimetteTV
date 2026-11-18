package com.limette.tv.domain.service

import com.limette.tv.data.feiniu.FeiNiuClient
import com.limette.tv.data.remote.RemoteFileClient
import com.limette.tv.data.webdav.WebDavFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 封面解析优先级：
 *  1. 飞牛影视已刮削好的海报/缩略图（/v/api/v1/sys/img/...）
 *  2. 媒体目录内的 poster.jpg / cover.jpg / folder.jpg
 *  3. 返回 null —— UI 层用带片名的渐变卡片兜底，绝不显示黑图
 */
@Singleton
class ThumbnailResolver @Inject constructor(
    private val feiNiuClient: FeiNiuClient
) {
    suspend fun resolve(file: WebDavFile, client: RemoteFileClient): String? {
        feiNiuClient.findPoster(file.name, file.path)?.let { return it }

        val directory = file.path.substringBeforeLast('/', "")
        if (directory.isNotBlank()) {
            runCatching { client.getCoverImage(directory) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return null
    }
}
