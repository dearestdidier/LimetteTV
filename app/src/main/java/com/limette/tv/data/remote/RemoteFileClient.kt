package com.limette.tv.data.remote

import com.limette.tv.data.webdav.WebDavFile

/**
 * 远程文件访问统一接口。WebDAV 与 SMB 各自实现，
 * 扫描 / 入库 / 封面查找逻辑对协议无感知。
 */
interface RemoteFileClient {
    suspend fun listFiles(relativePath: String = ""): Result<List<WebDavFile>>

    suspend fun listAllVideoFiles(onProgress: ((Int) -> Unit)? = null): Result<List<WebDavFile>>

    /** 返回播放器可直接读取的 URL（WebDAV 为原始地址，SMB 为本地代理地址） */
    fun getFileUrl(relativePath: String): String

    /** 目录内查找 poster.jpg / cover.jpg / folder.jpg 等本地封面 */
    suspend fun getCoverImage(directoryPath: String): String?

    /** 读取 .nfo 等文本文件 */
    suspend fun readTextFile(relativePath: String): String?

    suspend fun fileExists(relativePath: String): Boolean

    fun close() {}
}
