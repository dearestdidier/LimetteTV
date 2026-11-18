package com.limette.tv.data.smb

import android.util.Log
import com.limette.tv.domain.model.MediaSource
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import java.io.InputStream
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMB → 本地 HTTP 代理。
 * ExoPlayer 不能直接读 smb://，因此把 SMB 文件通过 127.0.0.1 暴露成 http 流，
 * 并支持 HTTP Range（记忆续播 seek 必需）。数据全程不出本机。
 */
@Singleton
class SmbStreamServer @Inject constructor() : NanoHTTPD(PORT) {

    private val clients = ConcurrentHashMap<String, SmbFileClient>()
    private val registeredSources = ConcurrentHashMap<String, MediaSource>()
    @Volatile private var started = false

    fun ensureStarted() {
        if (started) return
        synchronized(this) {
            if (started) return
            runCatching { start(SOCKET_READ_TIMEOUT, false) }
                .onSuccess { started = true; Log.i(TAG, "SMB proxy started on $PORT") }
                .onFailure { Log.w(TAG, "SMB proxy start failed: ${it.message}") }
        }
    }

    /** 同一媒体源且配置未变时复用连接，避免扫描 / 播放解析反复重建 SMB 会话。 */
    fun register(source: MediaSource): SmbFileClient {
        ensureStarted()
        val cached = clients[source.id]
        if (cached != null && registeredSources[source.id] == source) return cached
        clients.remove(source.id)?.close()
        val client = SmbFileClient(source, baseUrl())
        clients[source.id] = client
        registeredSources[source.id] = source
        return client
    }

    fun unregister(id: String) {
        clients.remove(id)?.close()
        registeredSources.remove(id)
    }

    fun shutdown() {
        clients.values.forEach { runCatching { it.close() } }
        clients.clear()
        registeredSources.clear()
        runCatching { stop() }
        started = false
    }

    override fun serve(session: IHTTPSession): Response {
        var handle: SmbFileHandle? = null
        return try {
            val uri = session.uri.orEmpty()
            if (!uri.startsWith("/smb/")) return notFound()
            val rest = uri.removePrefix("/smb/")
            val slash = rest.indexOf('/')
            if (slash <= 0) return notFound()
            val sourceId = rest.substring(0, slash)
            val relativePath = URLDecoder.decode(rest.substring(slash + 1), "UTF-8")
            val client = clients[sourceId] ?: return notFound()

            // 一次 openFile 拿到长度；Range 起点直接用 SMB2 READ 的 fileOffset 跳转。
            val file = client.openHandle(relativePath)
            handle = file
            val total = file.length
            val range = session.headers["range"] ?: session.headers["Range"]
            var start = 0L
            if (!range.isNullOrBlank() && range.startsWith("bytes=")) {
                start = range.removePrefix("bytes=").substringBefore('-')
                    .trim().toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            }
            if (total > 0 && start >= total) start = 0L

            val stream: InputStream = file.streamFrom(start)
            val length = if (total > 0) total - start else -1L
            val status = if (start > 0) Response.Status.PARTIAL_CONTENT else Response.Status.OK
            val response = if (length >= 0) {
                NanoHTTPD.newFixedLengthResponse(status, mimeTypeOf(relativePath), stream, length)
            } else {
                NanoHTTPD.newChunkedResponse(Response.Status.OK, mimeTypeOf(relativePath), stream)
            }
            response.addHeader("Accept-Ranges", "bytes")
            if (start > 0 && total > 0) {
                response.addHeader("Content-Range", "bytes $start-${total - 1}/$total")
            }
            response
        } catch (e: Exception) {
            // 构造响应失败时 NanoHTTPD 不会接触到底层流，需要在这里释放 SMB 句柄
            handle?.close()
            Log.w(TAG, "serve failed: ${e.message}")
            NanoHTTPD.newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain; charset=utf-8",
                e.message ?: "SMB stream error"
            )
        }
    }

    private fun notFound() = NanoHTTPD.newFixedLengthResponse(
        Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "Not found"
    )

    private fun mimeTypeOf(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4", "m4v", "mov" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "ts", "m2ts" -> "video/mp2t"
            "webm" -> "video/webm"
            "flv" -> "video/x-flv"
            "mp3" -> "audio/mpeg"
            "srt" -> "application/x-subrip"
            "ass", "ssa" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }
    }

    companion object {
        private const val TAG = "SmbStreamServer"
        const val PORT = 18999
        private const val SOCKET_READ_TIMEOUT = 30_000
        fun baseUrl(): String = "http://127.0.0.1:$PORT"
    }
}
