package com.limette.tv.domain.model

/**
 * Limette TV 的一个媒体源。
 * 支持三类协议：WebDAV、SMB、飞牛影视（fnOS Trimedia）。
 * 任意多个媒体源可以同时启用，首页会聚合展示所有启用源里的视频文件。
 */
data class MediaSource(
    val id: String,
    val name: String,
    val type: SourceType,
    val enabled: Boolean = true,

    // ---- WebDAV / 飞牛影视通用：服务地址 ----
    val protocol: String = "http",
    val host: String = "",
    val port: Int = 0,
    val path: String = "/",
    val username: String = "",
    val password: String = "",

    // ---- SMB ----
    val shareName: String = "",
    val domain: String = "",
    val smbRootPath: String = "",

    // 可选：只扫描这些子目录；为空表示扫描根目录（仅 SMB / WebDAV 使用）
    val selectedPaths: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    enum class SourceType { WEBDAV, SMB, FENIU }

    fun typeLabel(): String = when (type) {
        SourceType.WEBDAV -> "WebDAV"
        SourceType.SMB -> "SMB"
        SourceType.FENIU -> "飞牛影视"
    }

    fun displayUrl(): String = when (type) {
        SourceType.WEBDAV -> "${protocol}://${host}:${port}${path}"
        SourceType.SMB -> "smb://${host}/${shareName}${if (smbRootPath.isBlank()) "" else "/${smbRootPath.trim('/')}"}"
        SourceType.FENIU -> "${protocol}://${host}:${port}"
    }

    /** 飞牛影视 API 的 base URL（不含末尾斜杠）。 */
    fun baseUrl(): String = "${protocol}://${host}:${port}".trimEnd('/')
}
