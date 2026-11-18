package com.limette.tv.data.remote

import android.util.Base64
import com.limette.tv.domain.model.MediaSource
import java.util.concurrent.ConcurrentHashMap

/**
 * 图片请求鉴权注册表。
 * Coil 的 OkHttp 拦截器是同步的，这里保存「host:port → Basic 认证值」以及飞牛 token，
 * 供加载 WebDAV 本地封面 / 飞牛影视缩略图时使用。
 */
object LocalImageAuth {
    private val basicByAuthority = ConcurrentHashMap<String, String>()

    fun register(source: MediaSource) {
        if (source.type != MediaSource.SourceType.WEBDAV) return
        if (source.username.isBlank()) return
        val authority = "${source.host.trim()}:${source.port}"
        val value = "Basic " + Base64.encodeToString(
            "${source.username}:${source.password}".toByteArray(), Base64.NO_WRAP
        )
        basicByAuthority[authority] = value
    }

    fun unregister(source: MediaSource) {
        basicByAuthority.remove("${source.host.trim()}:${source.port}")
    }

    fun basicFor(authority: String): String? = basicByAuthority[authority]
}
