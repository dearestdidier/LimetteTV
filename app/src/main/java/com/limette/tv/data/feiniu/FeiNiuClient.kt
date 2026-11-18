package com.limette.tv.data.feiniu

import com.limette.tv.domain.model.MediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 全局鉴权状态：按 host 保存 token，供 Coil 给 /v/api/v1/sys/img 图片请求附加。 */
object FeiNiuAuth {
    private val tokens = ConcurrentHashMap<String, String>()
    @Volatile var lastHost: String = ""

    fun register(host: String, token: String) {
        if (host.isNotBlank() && token.isNotBlank()) {
            tokens[host] = token
            lastHost = host
        }
    }

    fun tokenFor(host: String): String? = tokens[host]

    fun clear(host: String) {
        tokens.remove(host)
    }
}

/** 飞牛影视里的一条可播放媒体（电影，或剧集的某一分集）。 */
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

/** 飞牛影视播放信息：真实可播放的 m3u8/文件 URL + 需要附加的请求头。 */
data class FeiNiuPlayback(
    val url: String,
    val headers: Map<String, String> = emptyMap()
)

/**
 * 飞牛影视（fnOS Trimedia，默认 5666 端口）只读客户端。
 * 参考第三方客户端（fnOSTV / fnos-tv-web / synctv fnos provider）的调用方式：
 *  - POST /v/api/v1/login               {username,password,app_name}          → token
 *  - POST /v/api/v1/item/list           {tags,page,page_size,...}             → data.list[]
 *  - GET  /v/api/v1/episode/list/{guid}                                       → 剧集分集
 *  - POST /v/api/v1/play/info           {item_guid}                           → data.item
 *  - GET  /v/api/v1/stream/list/{guid}                                        → video/audio streams
 *  - POST /v/api/v1/play/quality        {media_guid}                          → 清晰度
 *  - POST /v/api/v1/play/play           {media_guid,video_guid,...}           → play_link
 *  - GET  /v/api/v1/sys/img/{poster}?w=400                                    → 图片代理（需 token）
 *
 * 只读取 NAS 上已刮削好的媒体与海报，不上传、不写入任何数据。
 */
@Singleton
class FeiNiuClient @Inject constructor() {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /** key = 归一化后的文件名 / 路径，value = 完整海报 URL（供本地 SMB/WebDAV 文件配图） */
    private val posterIndex = ConcurrentHashMap<String, String>()

    /** 登录并注册 token 到全局 FeiNiuAuth，返回 token。 */
    suspend fun login(source: MediaSource): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = source.baseUrl()
            val body = JSONObject()
                .put("username", source.username)
                .put("password", source.password)
                .put("app_name", "trimemedia-web")
                .toString()
            val resp = post(base, "/v/api/v1/login", body, null)
                ?: throw IllegalStateException("飞牛影视服务无响应，请检查地址与端口")
            val token = extractToken(resp)
                ?: throw IllegalStateException("飞牛影视登录失败，请检查账号密码")
            FeiNiuAuth.register(source.host, token)
            token
        }
    }

    /** 拉取飞牛影视库中的全部视频：电影、未刮削视频，以及电视剧的所有分集。 */
    suspend fun listVideos(
        source: MediaSource,
        onProgress: ((Int) -> Unit)? = null
    ): Result<List<FeiNiuMedia>> = withContext(Dispatchers.IO) {
        runCatching {
            val base = source.baseUrl()
            val token = login(source).getOrElse { throw it }
            val result = ArrayList<FeiNiuMedia>()
            val tvs = ArrayList<JSONObject>()

            var page = 1
            while (page <= MAX_PAGES) {
                val tags = JSONObject().put(
                    "type",
                    JSONArray().put("Movie").put("TV").put("Video")
                )
                val payload = JSONObject()
                    .put("tags", tags)
                    .put("exclude_grouped_video", 1)
                    .put("sort_type", "DESC")
                    .put("sort_column", "create_time")
                    .put("page", page)
                    .put("page_size", PAGE_SIZE)
                    .toString()
                val resp = post(base, "/v/api/v1/item/list", payload, token)
                val list = resp?.let { asArray(dataOf(it)?.opt("list")) } ?: JSONArray()
                if (list.length() == 0) break

                for (i in 0 until list.length()) {
                    val item = list.optJSONObject(i) ?: continue
                    val type = item.optString("type", "")
                    when {
                        type.equals("TV", true) || type.equals("Series", true) -> tvs.add(item)
                        else -> result += toMedia(item, isMovie = true, base = base)
                    }
                }
                onProgress?.invoke(result.size)
                if (list.length() < PAGE_SIZE) break
                page++
            }

            // 电视剧逐部展开分集
            tvs.forEach { tv ->
                val tvGuid = firstNonBlank(tv.optString("guid"), tv.optString("id"))
                val tvTitle = firstNonBlank(tv.optString("title"), tv.optString("name"), "未命名剧集")
                val tvPoster = buildPosterUrl(
                    base, firstNonBlank(tv.optString("poster"), tv.optString("poster_path"))
                )
                if (tvGuid.isBlank()) return@forEach
                val epResp = get(base, "/v/api/v1/episode/list/$tvGuid", token)
                val episodes = epResp?.let {
                    asArray(dataOf(it)?.opt("list") ?: dataOf(it)?.opt("episodes"))
                } ?: JSONArray()
                for (ei in 0 until episodes.length()) {
                    val ep = episodes.optJSONObject(ei) ?: continue
                    val epGuid = firstNonBlank(ep.optString("guid"), ep.optString("id"))
                    if (epGuid.isBlank()) continue
                    val season = intOrNull(ep.optString("season_number")) ?: intOrNull(tv.optString("season_number"))
                    val number = intOrNull(ep.optString("episode_number")) ?: intOrNull(ep.optString("episode"))
                    val epTitle = firstNonBlank(ep.optString("title"), ep.optString("name"))
                    val display = buildEpisodeTitle(tvTitle, season, number, epTitle)
                    result += FeiNiuMedia(
                        guid = epGuid,
                        title = display,
                        posterUrl = buildPosterUrl(base, firstNonBlank(ep.optString("poster"), ep.optString("thumb"))) ?: tvPoster,
                        year = intOrNull(tv.optString("year")),
                        seasonNumber = season,
                        episodeNumber = number,
                        isMovie = false,
                        fileSize = ep.optLong("size", 0L),
                        overview = ep.optString("overview").takeIf { it.isNotBlank() }
                    )
                }
                onProgress?.invoke(result.size)
            }
            result
        }
    }

    /** 重建「文件 → 海报」索引，供本地 SMB/WebDAV 视频匹配飞牛已刮削的海报。 */
    suspend fun refreshPosterIndex(source: MediaSource): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val base = source.baseUrl()
            val token = login(source).getOrElse { throw it }
            val fresh = HashMap<String, String>()
            var page = 1
            while (page <= MAX_PAGES) {
                val payload = JSONObject()
                    .put("tags", JSONObject().put("type", JSONArray().put("Movie").put("TV").put("Video")))
                    .put("exclude_grouped_video", 1)
                    .put("page", page)
                    .put("page_size", PAGE_SIZE)
                    .toString()
                val resp = post(base, "/v/api/v1/item/list", payload, token)
                val list = resp?.let { asArray(dataOf(it)?.opt("list")) } ?: JSONArray()
                if (list.length() == 0) break
                for (i in 0 until list.length()) {
                    val item = list.optJSONObject(i) ?: continue
                    val poster = buildPosterUrl(
                        base, firstNonBlank(item.optString("poster"), item.optString("poster_path"), item.optString("thumb"))
                    ) ?: continue
                    val path = firstNonBlank(item.optString("path"), item.optString("file_path"))
                    val title = firstNonBlank(item.optString("title"), item.optString("name"))
                    val fileName = firstNonBlank(item.optString("file_name"), path.substringAfterLast('/'))
                    listOf(fileName, fileName.substringBeforeLast('.'), path, title)
                        .map { normalizeKey(it) }
                        .filter { it.length >= 2 }
                        .forEach { fresh.putIfAbsent(it, poster) }
                }
                if (list.length() < PAGE_SIZE) break
                page++
            }
            posterIndex.clear()
            posterIndex.putAll(fresh)
            fresh.size
        }
    }

    fun findPoster(fileName: String, relativePath: String): String? {
        if (posterIndex.isEmpty()) return null
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

    /**
     * 生成真实播放链接。飞牛 play_link 有时效且依赖流信息，必须在播放时动态构建。
     * @param ref 飞牛媒体的 guid（电影即 item.guid，剧集即分集 guid）
     */
    suspend fun buildPlaybackInfo(source: MediaSource, ref: String): Result<FeiNiuPlayback> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = source.baseUrl()
                val token = login(source).getOrElse { throw it }

                // 1) play/info → 得到真正用于取流的 item guid
                val infoResp = post(base, "/v/api/v1/play/info", JSONObject().put("item_guid", ref).toString(), token)
                val item = dataOf(infoResp ?: JSONObject())?.optJSONObject("item")
                    ?: dataOf(infoResp ?: JSONObject())
                    ?: JSONObject()
                val streamGuid = firstNonBlank(item.optString("guid"), ref)

                // 2) stream/list → 视频流 / 音频流
                val streamResp = get(base, "/v/api/v1/stream/list/$streamGuid", token)
                val streamData = dataOf(streamResp ?: JSONObject()) ?: JSONObject()
                val videos = asArray(streamData.opt("video_streams"))
                val audios = asArray(streamData.opt("audio_streams"))
                val video = (0 until videos.length()).mapNotNull { videos.optJSONObject(it) }.firstOrNull()
                    ?: throw IllegalStateException("飞牛影视未返回可播放的视频流")
                val audio = (0 until audios.length()).mapNotNull { audios.optJSONObject(it) }.firstOrNull()

                val mediaGuid = firstNonBlank(video.optString("media_guid"), "")
                val videoGuid = firstNonBlank(video.optString("guid"), "")
                val videoEncoder = video.optString("codec_name").ifBlank { "h264" }
                val bitrate = video.optLong("bps", 0L)

                // 3) play/quality → 取第一档清晰度（失败则用默认值兜底）
                var resolution = "${video.optInt("width", 1920)}x${video.optInt("height", 1080)}"
                var qualityBitrate = bitrate
                val qualityResp = runCatching {
                    post(base, "/v/api/v1/play/quality", JSONObject().put("media_guid", mediaGuid).toString(), token)
                }.getOrNull()
                val qualities = qualityResp?.let { asArray(dataOf(it)) } ?: JSONArray()
                if (qualities.length() > 0) {
                    val q = qualities.optJSONObject(0)
                    if (q != null) {
                        resolution = firstNonBlank(q.optString("resolution"), resolution)
                        qualityBitrate = q.optLong("bitrate", bitrate)
                    }
                }

                // 4) play/play → play_link
                val payload = JSONObject()
                    .put("media_guid", mediaGuid)
                    .put("video_guid", videoGuid)
                    .put("video_encoder", videoEncoder)
                    .put("resolution", resolution)
                    .put("bitrate", qualityBitrate)
                    .put("startTimestamp", 0)
                    .put("audio_encoder", "aac")
                    .put("audio_guid", firstNonBlank(audio?.optString("guid").orEmpty(), ""))
                    .put("subtitle_guid", "")
                    .put("channels", maxOf(2, audio?.optInt("channels", 2) ?: 2))
                    .toString()
                val playResp = post(base, "/v/api/v1/play/play", payload, token)
                    ?: throw IllegalStateException("飞牛影视未返回播放链接")
                val playLink = firstNonBlank(
                    dataOf(playResp)?.optString("play_link").orEmpty(),
                    playResp.optString("play_link")
                )
                if (playLink.isBlank()) throw IllegalStateException("飞牛影视播放链接为空")
                val url = if (playLink.startsWith("http://") || playLink.startsWith("https://"))
                    playLink else base + playLink

                FeiNiuPlayback(url = url, headers = mapOf("Authorization" to token))
            }
        }

    // ---------- 基础 HTTP ----------

    private fun post(base: String, path: String, body: String, token: String?): JSONObject? {
        val builder = Request.Builder()
            .url(base + path)
            .post(body.toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
        token?.let { builder.header("Authorization", it) }
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (text.isBlank()) return null
            return runCatching { JSONObject(text) }.getOrNull()
        }
    }

    private fun get(base: String, path: String, token: String?): JSONObject? {
        val builder = Request.Builder().url(base + path).header("Accept", "application/json")
        token?.let { builder.header("Authorization", it) }
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (text.isBlank()) return null
            return runCatching { JSONObject(text) }.getOrNull()
        }
    }

    // ---------- JSON 兼容解析 ----------

    /** 飞牛返回可能是 {code,data} 包裹，也可能平铺；data 本身或许就是目标数组/对象。 */
    private fun dataOf(json: JSONObject): JSONObject? =
        json.optJSONObject("data") ?: json.takeIf { it.has("list") || it.has("video_streams") }

    private fun asArray(value: Any?): JSONArray = when (value) {
        is JSONArray -> value
        is JSONObject -> value.optJSONArray("list") ?: JSONArray()
        else -> JSONArray()
    }

    private fun extractToken(json: JSONObject): String? {
        json.optJSONObject("data")?.optString("token")?.takeIf { it.isNotBlank() }?.let { return it }
        json.optString("token").takeIf { it.isNotBlank() }?.let { return it }
        json.optString("access_token").takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun toMedia(item: JSONObject, isMovie: Boolean, base: String): FeiNiuMedia {
        val title = firstNonBlank(item.optString("title"), item.optString("name"), item.optString("file_name"), "未命名")
        val path = firstNonBlank(item.optString("path"), item.optString("file_path"))
        val fileName = firstNonBlank(item.optString("file_name"), path.substringAfterLast('/'))
        val guid = firstNonBlank(item.optString("guid"), item.optString("id"), fileName + "|" + path)
        return FeiNiuMedia(
            guid = guid,
            title = title,
            posterUrl = buildPosterUrl(
                base, firstNonBlank(item.optString("poster"), item.optString("poster_path"), item.optString("thumb"))
            ),
            year = intOrNull(item.optString("year")) ?: intOrNull(item.optString("release_date")?.take(4)),
            seasonNumber = intOrNull(item.optString("season_number")),
            episodeNumber = intOrNull(item.optString("episode_number")),
            isMovie = isMovie,
            fileSize = item.optLong("size", 0L),
            overview = item.optString("overview").takeIf { it.isNotBlank() }
        )
    }

    fun buildPosterUrl(base: String, posterPath: String): String? {
        val p = posterPath.trim()
        if (p.isBlank()) return null
        if (p.startsWith("http://") || p.startsWith("https://")) return p
        return "${base.trimEnd('/')}/v/api/v1/sys/img/${p.trimStart('/')}?w=$POSTER_WIDTH"
    }

    private fun buildEpisodeTitle(tv: String, season: Int?, number: Int?, ep: String): String {
        val prefix = if (season != null && number != null) "S%02dE%02d ".format(season, number)
        else if (number != null) "第${number}集 " else ""
        return "$tv $prefix$ep".trim()
    }

    private fun intOrNull(value: String?): Int? = value?.trim()?.takeIf { it.isNotBlank() }
        ?.let { Regex("-?\\d+").find(it)?.value?.toIntOrNull() }

    private fun normalizeKey(value: String): String =
        value.lowercase().replace('\\', '/').trim().trim('/').substringAfterLast('/')
            .let { normalizeTitle(it) }

    private fun normalizeTitle(value: String): String =
        value.lowercase()
            .replace(Regex("[._\\-\\[\\]()（）]+"), "")
            .replace(Regex("\\s+"), "")
            .replace(Regex("(2160p|1080p|720p|4k|bluray|webdl|h264|h265|x264|x265|hdr|remux|aac|dts)"), "")
            .trim()

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }.orEmpty()

    companion object {
        private const val PAGE_SIZE = 200
        private const val MAX_PAGES = 30
        const val POSTER_WIDTH = 400
    }
}
