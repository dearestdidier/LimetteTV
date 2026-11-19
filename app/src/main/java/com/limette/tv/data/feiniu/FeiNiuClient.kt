package com.limette.tv.data.feiniu

import android.content.Context
import com.limette.tv.domain.model.MediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 飞牛影视（fnOS Trimedia）客户端。
 *
 * 登录走用户名密码（POST /v/api/v1/login，带 Authx 签名 + nonce），
 * 视频列表先取媒体库（mediadb/list）再逐库浏览（item/list），电视剧展开分集（episode/list）。
 * 播放时动态换取直链（stream）或回退到 NAS 代理（media/range）。
 *
 * 全部 API 语义参考 FnTV_danmu-master 抓包实现，见 FeiNiuApi。
 */
@Singleton
class FeiNiuClient @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** 按 host 保存 token，供 Coil 给 /v/api/v1/sys/img 图片请求附加鉴权。 */
    object Auth {
        private val tokens = ConcurrentHashMap<String, String>()
        @Volatile var lastHost: String = ""

        fun register(host: String, token: String) {
            if (host.isNotBlank() && token.isNotBlank()) {
                tokens[host] = token
                lastHost = host
            }
        }

        fun tokenFor(host: String): String? = tokens[host]
        fun clear(host: String) { tokens.remove(host) }
    }

    /** 一条可播放媒体（电影，或剧集的某一分集）。 */
    data class FeiNiuMedia(
        val guid: String,
        val title: String,
        val posterUrl: String?,
        val year: Int?,
        val seasonNumber: Int?,
        val episodeNumber: Int?,
        val isMovie: Boolean,
        val fileSize: Long,
        val overview: String? = null
    )

    /** 播放信息：真实 URL + 需附加的请求头。 */
    data class FeiNiuPlayback(
        val url: String,
        val headers: Map<String, String> = emptyMap()
    )

    companion object {
        const val POSTER_WIDTH = 400
        private const val PAGE_SIZE = 200
    }

    /** 文件名 → 海报 URL 索引，供本地 SMB/WebDAV 视频匹配飞牛已刮削的海报。 */
    private val posterIndex = ConcurrentHashMap<String, String>()
    /** host → token 的会话缓存，避免每次扫描重复登录。 */
    private val tokenCache = ConcurrentHashMap<String, String>()

    // ---------------- 登录 ----------------

    /** 登录并把 token 注册到全局 Auth，返回 token。 */
    suspend fun login(source: MediaSource): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = source.baseUrl()
            tokenCache[source.host]?.let {
                Auth.register(source.host, it)
                return@runCatching it
            }
            require(source.username.isNotBlank() && source.password.isNotBlank()) {
                "请先填写飞牛影视的账号和密码"
            }
            val token = FeiNiuApi.login(base, source.username, source.password)
            tokenCache[source.host] = token
            Auth.register(source.host, token)
            token
        }
    }

    /** 供外部（WebView OAuth）注入 token。 */
    fun registerToken(source: MediaSource, token: String) {
        tokenCache[source.host] = token
        Auth.register(source.host, token)
    }

    // ---------------- 视频列表 ----------------

    /** 拉取影视库中的全部视频：先取库 guid，再逐库浏览；电视剧展开分集。 */
    suspend fun listVideos(
        source: MediaSource,
        onProgress: ((Int) -> Unit)? = null
    ): Result<List<FeiNiuMedia>> = withContext(Dispatchers.IO) {
        runCatching {
            val base = source.baseUrl()
            val token = login(source).getOrElse { throw it }
            val result = mutableListOf<FeiNiuMedia>()

            // 1) 取媒体库列表；若接口不可用则回退到 root("") 浏览
            val libraries = runCatching { FeiNiuApi.mediaDbList(base, token) }.getOrDefault(emptyList())
            val guids = libraries.mapNotNull { it.guid }.ifEmpty { listOf("") }

            // 2) 逐库浏览条目
            for (libGuid in guids) {
                var page = 0
                while (page < 30) {
                    val data = FeiNiuApi.itemList(base, token, libGuid, PAGE_SIZE)
                    val items = data.list.orEmpty()
                    if (items.isEmpty()) break
                    for (item in items) {
                        when {
                            item.isFolder() -> continue
                            item.isPlayable() -> result.add(toMedia(item, source))
                            item.type.equals("TV", true) || item.type.equals("Series", true) -> {
                                // 电视剧：展开分集
                                val epGuid = item.guid ?: continue
                                val episodes = runCatching { FeiNiuApi.episodeList(base, token, epGuid) }
                                    .getOrDefault(emptyList())
                                for (ep in episodes) {
                                    if (!ep.isPlayable()) continue
                                    val season = ep.seasonNumber.takeIf { it > 0 }
                                    val number = ep.episodeNumber.takeIf { it > 0 }
                                    result.add(FeiNiuMedia(
                                        guid = ep.guid ?: continue,
                                        title = buildEpisodeTitle(item.displayTitle(), season, number, null),
                                        posterUrl = buildPosterUrl(source, ep.poster ?: item.poster),
                                        year = null,
                                        seasonNumber = season,
                                        episodeNumber = number,
                                        isMovie = false,
                                        fileSize = 0L,
                                        overview = ep.overview
                                    ))
                                }
                            }
                        }
                    }
                    onProgress?.invoke(result.size)
                    if (items.size < PAGE_SIZE) break
                    page++
                }
            }
            // 3) 顺带刷新海报索引（失败不影响列表）
            runCatching { refreshPosterIndexInternal(base, token, source, guids) }
            result
        }
    }

    /** 重建「文件 → 海报」索引，供本地 SMB/WebDAV 视频匹配飞牛已刮削的海报。 */
    suspend fun refreshPosterIndex(source: MediaSource): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val base = source.baseUrl()
            val token = login(source).getOrElse { throw it }
            val libraries = runCatching { FeiNiuApi.mediaDbList(base, token) }.getOrDefault(emptyList())
            val guids = libraries.mapNotNull { it.guid }.ifEmpty { listOf("") }
            refreshPosterIndexInternal(base, token, source, guids)
        }
    }

    private suspend fun refreshPosterIndexInternal(
        base: String, token: String, source: MediaSource, guids: List<String>
    ): Int {
        val fresh = mutableMapOf<String, String>()
        for (libGuid in guids) {
            runCatching {
                for (page in 0 until 10) {
                    val items = FeiNiuApi.itemList(base, token, libGuid, PAGE_SIZE).list.orEmpty()
                    if (items.isEmpty()) break
                    for (item in items) {
                        val poster = buildPosterUrl(source, item.poster) ?: continue
                        val key = item.displayTitle()
                        if (key.isNotBlank()) fresh.putIfAbsent(normalizeKey(key), poster)
                    }
                    if (items.size < PAGE_SIZE) break
                }
            }
        }
        if (fresh.isNotEmpty()) {
            posterIndex.clear()
            posterIndex.putAll(fresh)
        }
        return fresh.size
    }

    fun findPoster(fileName: String, relativePath: String): String? {
        val candidates = listOf(
            fileName,
            fileName.substringBeforeLast('.'),
            relativePath,
            relativePath.substringAfterLast('/').substringBeforeLast('.'),
            normalizeTitle(fileName.substringBeforeLast('.'))
        )
        candidates.forEach { key -> posterIndex[normalizeKey(key)]?.let { return it } }
        val tail = normalizeKey(relativePath.substringAfterLast('/').substringBeforeLast('.'))
        if (tail.length >= 4) {
            posterIndex.entries.firstOrNull { (k, _) -> k.contains(tail) || tail.contains(k) }
                ?.let { return it.value }
        }
        return null
    }

    // ---------------- 播放链接 ----------------

    /** 生成真实播放链接：play/info 拿 media_guid → stream 拿直链，失败回退 NAS 代理。 */
    suspend fun buildPlaybackInfo(source: MediaSource, ref: String): Result<FeiNiuPlayback> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = source.baseUrl()
                val token = login(source).getOrElse { throw it }

                // 1) play/info → media_guid
                val info = runCatching { FeiNiuApi.playInfo(base, token, ref) }.getOrNull()
                val mediaGuid = info?.mediaGuid?.takeIf { it.isNotBlank() }
                    ?: info?.item?.guid?.takeIf { it.isNotBlank() }
                    ?: ref

                // 2) stream → 直链
                val account = source.username.ifBlank { "video" }
                val stream = runCatching { FeiNiuApi.stream(base, token, mediaGuid, account) }.getOrNull()
                val directUrl = stream?.directLinkQualities?.firstOrNull { !it.url.isNullOrBlank() }
                    ?.url?.replace("\\u0026", "&")

                val url = when {
                    !directUrl.isNullOrBlank() ->
                        if (directUrl.startsWith("http")) directUrl else base + directUrl
                    else -> "$base/v/api/v1/media/range/$mediaGuid"
                }
                FeiNiuPlayback(url = url, headers = mapOf("Authorization" to token))
            }
        }

    // ---------------- 工具函数 ----------------

    private fun toMedia(item: FeiNiuApi.Item, source: MediaSource): FeiNiuMedia {
        val season = item.seasonNumber.takeIf { it > 0 }
        val number = item.episodeNumber.takeIf { it > 0 }
        val isEpisode = item.type.equals("Episode", true) || number != null
        return FeiNiuMedia(
            guid = item.guid ?: "",
            title = if (isEpisode) buildEpisodeTitle(item.tvTitle ?: item.title.orEmpty(), season, number, null)
                    else item.displayTitle(),
            posterUrl = buildPosterUrl(source, item.poster),
            year = null,
            seasonNumber = season,
            episodeNumber = number,
            isMovie = !isEpisode,
            fileSize = 0L,
            overview = item.overview
        )
    }

    fun buildPosterUrl(source: MediaSource, posterPath: String?): String? {
        val p = posterPath?.trim()
        if (p.isNullOrBlank()) return null
        if (p.startsWith("http://") || p.startsWith("https://")) return p
        val path = if (p.startsWith("/")) p else "/$p"
        return "${source.baseUrl()}/v/api/v1/sys/img$path?w=$POSTER_WIDTH"
    }

    private fun buildEpisodeTitle(tv: String, season: Int?, number: Int?, ep: String?): String {
        val prefix = if (season != null && number != null) "S%02dE%02d ".format(season, number)
        else if (number != null) "第${number}集 " else ""
        return "$tv $prefix${ep ?: ""}".trim()
    }

    private fun normalizeKey(value: String): String =
        value.lowercase().replace('\\', '/').trim().trim('/').substringAfterLast('/')
            .let { normalizeTitle(it) }

    private fun normalizeTitle(value: String): String =
        value.lowercase()
            .replace(Regex("[._\\-\\[\\]()（）]+"), "")
            .replace(Regex("\\s+"), "")
            .replace(Regex("(2160p|1080p|720p|4k|bluray|webdl|h264|h265|x264|x265|hdr|remux|aac|dts)"), "")
            .trim()
}
