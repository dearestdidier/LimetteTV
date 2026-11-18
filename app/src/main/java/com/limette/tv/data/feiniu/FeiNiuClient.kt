package com.limette.tv.data.feiniu

import com.limette.tv.data.preferences.FeiNiuPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 全局鉴权状态，供 Coil 的 OkHttp 拦截器给 /v/api/v1/sys/img 请求附加 token。 */
object FeiNiuAuth {
    @Volatile var token: String? = null
    @Volatile var baseUrl: String = ""
}

/**
 * 飞牛影视（fnOS 5666 端口）只读客户端。
 *
 * 参考第三方客户端（fnOSTV / fntv-electron / synctv fnos provider）的调用方式：
 *  - POST /v/api/v1/login                  body {username,password,app_name:"trimemedia-web"} → data.token
 *  - POST /v/api/v1/item/list              body {tags:{type:[...]},page,page_size,...} → data.list[]
 *  - GET  /v/api/v1/sys/img/{poster}?w=400 图片代理（需 Authorization 头）
 *
 * 只读取 NAS 上已经刮削好的海报/缩略图，不上传、不写任何数据。
 */
@Singleton
class FeiNiuClient @Inject constructor(
    private val preferences: FeiNiuPreferences
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** key = 归一化后的文件名 / 路径，value = 完整海报 URL */
    private val posterIndex = ConcurrentHashMap<String, String>()
    @Volatile private var lastBaseUrl: String = ""
    @Volatile var lastError: String? = null
        private set

    val isIndexReady: Boolean get() = posterIndex.isNotEmpty()

    /** 登录并重建「文件 → 海报」索引，返回索引到的条目数。 */
    suspend fun refreshIndex(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val config = preferences.current()
            if (!config.isUsable) {
                posterIndex.clear()
                FeiNiuAuth.token = null
                return@runCatching 0
            }
            val base = config.baseUrl.trim().trimEnd('/')
            val token = login(base, config.username, config.password)
                ?: throw IllegalStateException("飞牛影视登录失败，请检查地址与账号密码")
            FeiNiuAuth.token = token
            FeiNiuAuth.baseUrl = base
            lastBaseUrl = base

            val fresh = HashMap<String, String>()
            var page = 1
            while (page <= MAX_PAGES) {
                val items = fetchItemPage(base, token, page)
                if (items.isEmpty()) break
                items.forEach { item -> indexItem(fresh, base, item) }
                if (items.size < PAGE_SIZE) break
                page++
            }
            posterIndex.clear()
            posterIndex.putAll(fresh)
            lastError = null
            posterIndex.size
        }.onFailure { lastError = it.message }
    }

    private fun login(base: String, username: String, password: String): String? {
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("app_name", "trimemedia-web")
            .toString()
        val request = Request.Builder()
            .url("$base/v/api/v1/login")
            .post(body.toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string().orEmpty())
            if (json.optInt("code", 0) != 0) return null
            val data = json.optJSONObject("data")
            return (data?.optString("token")?.takeIf { it.isNotBlank() }
                ?: json.optString("token").takeIf { it.isNotBlank() })
        }
    }

    private fun fetchItemPage(base: String, token: String, page: Int): List<JSONObject> {
        val tags = JSONObject().put(
            "type",
            org.json.JSONArray().put("Movie").put("TV").put("Directory").put("Video")
        )
        val payload = JSONObject()
            .put("tags", tags)
            .put("sort_type", "DESC")
            .put("sort_column", "create_time")
            .put("exclude_grouped_video", 1)
            .put("page", page)
            .put("page_size", PAGE_SIZE)
            .toString()
        val request = Request.Builder()
            .url("$base/v/api/v1/item/list")
            .post(payload.toRequestBody(jsonMediaType))
            .header("Authorization", token)
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val json = JSONObject(response.body?.string().orEmpty())
            if (json.optInt("code", 0) != 0) return emptyList()
            val list = json.optJSONObject("data")?.optJSONArray("list") ?: return emptyList()
            return (0 until list.length()).mapNotNull { i -> list.optJSONObject(i) }
        }
    }

    private fun indexItem(target: HashMap<String, String>, base: String, item: JSONObject) {
        val poster = firstNonBlank(
            item.optString("poster"),
            item.optString("poster_path"),
            item.optString("thumb"),
            item.optString("thumbnail")
        )
        if (poster.isBlank()) return
        val imageUrl = buildPosterUrl(base, poster) ?: return

        val path = firstNonBlank(
            item.optString("path"),
            item.optString("file_path"),
            item.optString("location")
        )
        val title = firstNonBlank(item.optString("title"), item.optString("name"))
        val fileName = firstNonBlank(item.optString("file_name"), path.substringAfterLast('/'))

        listOf(fileName, fileName.substringBeforeLast('.'), path, title)
            .map { normalizeKey(it) }
            .filter { it.length >= 2 }
            .forEach { target.putIfAbsent(it, imageUrl) }
    }

    /** 从索引里为本地扫描到的视频找一张飞牛影视缩略图。 */
    fun findPoster(fileName: String, relativePath: String): String? {
        if (posterIndex.isEmpty()) return null
        val candidates = listOf(
            fileName,
            fileName.substringBeforeLast('.'),
            relativePath,
            relativePath.substringAfterLast('/').substringBeforeLast('.'),
            normalizeTitle(fileName.substringBeforeLast('.'))
        )
        candidates.forEach { key ->
            posterIndex[normalizeKey(key)]?.let { return it }
        }
        // 兜底：路径尾段包含关系（应对 NAS 路径前缀差异）
        val tail = normalizeKey(relativePath.substringAfterLast('/').substringBeforeLast('.'))
        if (tail.length >= 4) {
            posterIndex.entries.firstOrNull { (k, _) -> k.contains(tail) || tail.contains(k) }
                ?.let { return it.value }
        }
        return null
    }

    fun buildPosterUrl(base: String, posterPath: String): String? {
        val p = posterPath.trim()
        if (p.isBlank()) return null
        if (p.startsWith("http://") || p.startsWith("https://")) return p
        return "${base.trimEnd('/')}/v/api/v1/sys/img/${p.trimStart('/')}?w=${POSTER_WIDTH}"
    }

    private fun normalizeKey(value: String): String =
        value.lowercase()
            .replace('\\', '/')
            .trim()
            .trim('/')
            .substringAfterLast('/')
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
        private const val PAGE_SIZE = 500
        private const val MAX_PAGES = 20
        const val POSTER_WIDTH = 400
    }
}
