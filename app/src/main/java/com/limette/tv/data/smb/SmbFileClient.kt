package com.limette.tv.data.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File as SmbFile
import com.limette.tv.data.remote.RemoteFileClient
import com.limette.tv.data.webdav.WebDavFile
import com.limette.tv.domain.model.MediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SMB / CIFS 媒体源（SMB2/3，基于 smbj）。
 * 目录枚举 / 增量扫描全部复用 [RemoteFileClient] 接口；
 * 播放 URL 交给 [SmbStreamServer] 做成本地 HTTP 地址，供 ExoPlayer 读取。
 */
class SmbFileClient(
    private val source: MediaSource,
    private val proxyBaseUrl: String = SmbStreamServer.baseUrl()
) : RemoteFileClient {

    private val smbClient = SMBClient()
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    companion object {
        val VIDEO_EXTENSIONS = listOf(
            ".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".webm", ".m4v",
            ".ts", ".m2ts", ".rmvb", ".mpg", ".mpeg", ".vob", ".iso", ".strm"
        )
        private val COVER_NAMES = listOf(
            "poster.jpg", "poster.png", "cover.jpg", "cover.png", "folder.jpg",
            "folder.png", "fanart.jpg", "thumb.jpg", "default.jpg"
        )
        fun isVideo(name: String): Boolean =
            VIDEO_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }

        // [MS-FSCC] FILE_ATTRIBUTE_DIRECTORY = 0x00000010
        // 直接用目录项返回的属性位判断，避免对每一项再发一次 folderExists 请求。
        private const val FILE_ATTRIBUTE_DIRECTORY_BIT = 0x10L

        const val MAX_DIRS = 2000
        const val MAX_TEXT_BYTES = 256 * 1024
    }

    private fun rootPath(): String = source.smbRootPath.trim().trim('/')

    private fun absolutize(relativePath: String): String {
        val rel = relativePath.trim().trimStart('/').trimEnd('/')
        val root = rootPath()
        return when {
            root.isBlank() -> rel
            rel.isBlank() -> root
            else -> "$root/$rel"
        }
    }

    @Synchronized
    private fun diskShare(): DiskShare {
        share?.let { return it }
        val host = source.host.trim()
        require(host.isNotBlank()) { "SMB 服务器地址为空" }
        require(source.shareName.isNotBlank()) { "SMB 共享名为空" }
        val conn = smbClient.connect(host)
        val auth = AuthenticationContext(
            source.username.trim(),
            source.password.toCharArray(),
            source.domain.trim().ifBlank { null }
        )
        val sess = conn.authenticate(auth)
        val disk = sess.connectShare(source.shareName) as DiskShare
        connection = conn
        session = sess
        share = disk
        return disk
    }

    override suspend fun listFiles(relativePath: String): Result<List<WebDavFile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val disk = diskShare()
                val dir = absolutize(relativePath)
                val relBase = relativePath.trim('/')
                disk.list(dir).mapNotNull { info ->
                    val name = info.fileName
                    if (name.isNullOrBlank() || name == "." || name == "..") return@mapNotNull null
                    // 返回相对共享根的路径，供上层拼播放地址 / 继续递归
                    val relChild = if (relBase.isBlank()) name else "$relBase/$name"
                    WebDavFile(
                        name = name,
                        path = relChild,
                        isDirectory = (info.fileAttributes and FILE_ATTRIBUTE_DIRECTORY_BIT) != 0L,
                        size = info.endOfFile
                    )
                }
            }
        }

    override suspend fun listAllVideoFiles(onProgress: ((Int) -> Unit)?): Result<List<WebDavFile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val roots = if (source.selectedPaths.isEmpty()) listOf("")
                else source.selectedPaths.map { it.trim('/') }
                val result = ArrayList<WebDavFile>()
                val queue = ArrayDeque<String>()
                roots.forEach { queue.add(it) }
                var visited = 0
                while (queue.isNotEmpty() && visited < MAX_DIRS) {
                    val current = queue.removeFirst()
                    visited++
                    val entries = listFiles(current).getOrNull() ?: continue
                    entries.forEach { entry ->
                        if (entry.isDirectory) queue.add(entry.path)
                        else if (isVideo(entry.name)) {
                            result.add(entry)
                            onProgress?.invoke(result.size)
                        }
                    }
                }
                result
            }
        }

    override fun getFileUrl(relativePath: String): String {
        val encoded = relativePath.trimStart('/').split('/')
            .joinToString("/") { URLEncoder.encode(it, "UTF-8") }
        return "${proxyBaseUrl.trimEnd('/')}/smb/${source.id}/$encoded"
    }

    override suspend fun getCoverImage(directoryPath: String): String? = withContext(Dispatchers.IO) {
        val entries = listFiles(directoryPath).getOrNull() ?: return@withContext null
        val lowerMap = entries.associateBy { it.name.lowercase() }
        COVER_NAMES.firstNotNullOfOrNull { candidate ->
            lowerMap[candidate]?.let { getFileUrl(it.path) }
        } ?: entries.firstOrNull {
            !it.isDirectory && (it.name.endsWith(".jpg", true) || it.name.endsWith(".png", true))
        }?.let { getFileUrl(it.path) }
    }

    override suspend fun readTextFile(relativePath: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            openHandle(relativePath).streamFrom(0).use { stream ->
                val buffer = ByteArray(MAX_TEXT_BYTES)
                var total = 0
                while (total < buffer.size) {
                    val n = stream.read(buffer, total, buffer.size - total)
                    if (n <= 0) break
                    total += n
                }
                String(buffer, 0, total, Charsets.UTF_8)
            }
        }.getOrNull()
    }

    override suspend fun fileExists(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { diskShare().fileExists(absolutize(relativePath)) }.getOrDefault(false)
    }

    /**
     * 供本地 HTTP 代理按 Range 读取文件。
     * 一次 openFile 同时拿到长度与可带偏移读取的流，避免每次请求重新枚举目录，
     * 也避免 ExoPlayer 续播 seek 时用顺序 skip 白传几个 GB 的数据。
     */
    fun openHandle(relativePath: String): SmbFileHandle {
        val disk = diskShare()
        val file = disk.openFile(
            absolutize(relativePath),
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        return SmbFileHandle(file)
    }

    @Synchronized
    override fun close() {
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close() }
        runCatching { smbClient.close() }
        share = null; session = null; connection = null
    }
}

/**
 * 一次 SMB openFile 的句柄：长度只查询一次；流的 close 与 [close] 共享
 * 同一个释放标记，保证 SMB 文件句柄不会泄漏、也不会被重复关闭。
 */
class SmbFileHandle internal constructor(private val file: SmbFile) {

    private val released = AtomicBoolean(false)

    val length: Long = runCatching {
        file.getFileInformation(FileStandardInformation::class.java).endOfFile
    }.getOrDefault(0L)

    fun streamFrom(offset: Long): InputStream =
        SmbOffsetInputStream(file, offset.coerceAtLeast(0L), released)

    fun close() {
        if (released.compareAndSet(false, true)) runCatching { file.closeSilently() }
    }
}

/** 用 SMB2 READ 的 fileOffset 实现随机读取，天然支持 HTTP Range / 记忆续播。 */
private class SmbOffsetInputStream(
    private val file: SmbFile,
    startOffset: Long,
    private val released: AtomicBoolean
) : InputStream() {

    private var position = startOffset
    @Volatile private var closed = false
    private val oneByte = ByteArray(1)

    override fun read(): Int {
        val n = read(oneByte, 0, 1)
        return if (n <= 0) -1 else oneByte[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (closed) throw IOException("SMB stream already closed")
        if (len == 0) return 0
        if (off < 0 || len < 0 || off + len > b.size) throw IndexOutOfBoundsException()
        // 保守块大小，避免单次 READ 超过 SMB2 协商的 MaxReadSize
        val wanted = minOf(len, MAX_READ_CHUNK)
        val count = runCatching { file.read(b, position, off, wanted) }
            .getOrElse { throw IOException("SMB read failed at offset $position", it) }
        if (count <= 0) return -1
        position += count
        return count
    }

    override fun skip(n: Long): Long {
        if (n <= 0 || closed) return 0
        position += n
        return n
    }

    override fun available(): Int = 0

    override fun close() {
        if (closed) return
        closed = true
        if (released.compareAndSet(false, true)) runCatching { file.closeSilently() }
    }

    companion object {
        const val MAX_READ_CHUNK = 64 * 1024
    }
}
