package com.limette.tv.data.feiniu

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 飞牛影视（fnOS Trimedia）HTTP API。
 *
 * 完全基于 FnTV_danmu-master 抓包还原的契约：
 *  - 每个请求都要带 Authx 签名头（API_KEY + url + nonce + timestamp + bodyMd5 + API_SECRET）
 *  - 同时带 Cookie: mode=relay 和 Authorization: <token>
 *  - 所有接口前缀为 /v/api/v1/
 *
 * 本类是无状态的纯请求封装；baseUrl / token 由 FeiNiuClient 在每次调用时传入。
 */
internal object FeiNiuApi {
    private const val TAG = "FeiNiuApi"
    private const val API_KEY = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh"
    private const val API_SECRET = "16CCEB3D-AB42-077D-36A1-F355324E4237"

    val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // ---------------- 通用响应 ----------------
    open class Resp<T>(
        val code: Int = -1,
        val msg: String? = null,
        val data: T? = null
    )

    // ---------------- 数据模型 ----------------
    data class LoginData(@SerializedName("token") val token: String? = null)

    data class MediaDb(
        val guid: String? = null,
        val title: String? = null,
        val poster: String? = null,
        val posters: List<String>? = null,
        val category: String? = null
    ) {
        fun firstPoster(): String? = poster?.takeIf { it.isNotBlank() }
            ?: posters?.firstOrNull { it.isNotBlank() }
    }

    data class ItemListData(
        @SerializedName("mdb_name") val mdbName: String? = null,
        val total: Int = 0,
        val list: List<Item>? = null
    )

    data class Item(
        val guid: String? = null,
        val title: String? = null,
        val type: String? = null,
        val poster: String? = null,
        @SerializedName("tv_title") val tvTitle: String? = null,
        @SerializedName("parent_guid") val parentGuid: String? = null,
        @SerializedName("parent_title") val parentTitle: String? = null,
        @SerializedName("episode_number") val episodeNumber: Int = 0,
        @SerializedName("season_number") val seasonNumber: Int = 0,
        val duration: Long = 0,
        val overview: String? = null,
        @SerializedName("media_guid") val mediaGuid: String? = null,
        @SerializedName("video_guid") val videoGuid: String? = null,
        @SerializedName("audio_guid") val audioGuid: String? = null
    ) {
        fun isFolder() = type in setOf("Directory", "TV", "Folder", "folder", "Series")
        fun isPlayable() = type in setOf("Movie", "Video", "Episode")
        fun displayTitle(): String = tvTitle?.takeIf { it.isNotBlank() }
            ?: title?.takeIf { it.isNotBlank() } ?: "未命名"
    }

    data class PlayInfoData(
        @SerializedName("media_guid") val mediaGuid: String? = null,
        @SerializedName("video_guid") val videoGuid: String? = null,
        @SerializedName("audio_guid") val audioGuid: String? = null,
        @SerializedName("subtitle_guid") val subtitleGuid: String? = null,
        val item: PlayInfoItem? = null
    )

    data class PlayInfoItem(
        val guid: String? = null,
        val title: String? = null,
        val poster: String? = null,
        val posters: String? = null
    )

    data class DirectQuality(
        val url: String? = null,
        val resolution: String? = null,
        val bitrate: Int = 0,
        @SerializedName("is_m3u8") val isM3u8: Boolean = false
    )

    data class VideoStream(
        val width: Int = 0,
        val height: Int = 0,
        val bps: Int = 0,
        @SerializedName("codec_name") val codecName: String? = null
    )

    data class StreamData(
        @SerializedName("direct_link_qualities") val directLinkQualities: List<DirectQuality>? = null,
        @SerializedName("video_stream") val videoStream: VideoStream? = null
    )

    // ---------------- 签名 ----------------
    private fun md5(input: String): String = try {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        bytes.joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        ""
    }

    private fun authx(urlPath: String, jsonBody: String?): String {
        val nonce = Random.nextInt(100000, 999999).toString()
        val timestamp = System.currentTimeMillis().toString()
        val bodyMd5 = md5(jsonBody ?: "")
        val signStr = "${API_KEY}_${urlPath}_${nonce}_${timestamp}_${bodyMd5}_${API_SECRET}"
        return "nonce=$nonce&timestamp=$timestamp&sign=${md5(signStr)}"
    }

    /** 统一的请求执行：拼 URL、算签名、加头、解析泛型响应。 */
    private suspend fun <T> call(
        baseUrl: String,
        token: String?,
        method: String,
        path: String,
        body: Any?,
        clazz: Class<T>
    ): Resp<T> = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/v" + path
        val jsonBody = body?.let { gson.toJson(it) }
        val reqBody = jsonBody?.toRequestBody(jsonMediaType)
        val builder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Authx", authx("/v$path", jsonBody))
            .header("Cookie", "mode=relay")
        if (!token.isNullOrBlank()) builder.header("Authorization", token)
        when (method) {
            "POST" -> builder.post(reqBody ?: "{}".toRequestBody(jsonMediaType))
            else -> builder.get()
        }
        val response = http.newCall(builder.build()).execute()
        response.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}: ${text.take(200)}")
            }
            val type = com.google.gson.reflect.TypeToken.getParameterized(Resp::class.java, clazz).type
            val parsed = gson.fromJson<Resp<T>>(text, type) ?: Resp<T>()
            Log.d(TAG, "$method $path -> code=${parsed.code} msg=${parsed.msg}")
            parsed
        }
    }

    private suspend fun <T> ok(resp: Resp<T>): T {
        if (resp.code != 0) {
            throw IllegalStateException("飞牛接口错误(${resp.code}): ${resp.msg ?: "未知错误"}")
        }
        return resp.data ?: throw IllegalStateException("飞牛接口返回空数据")
    }

    // ---------------- 具体接口 ----------------
    suspend fun login(baseUrl: String, username: String, password: String): String {
        val body = mapOf(
            "app_name" to "trimemedia-web",
            "username" to username,
            "password" to password,
            "nonce" to Random.nextInt(100000, 999999).toString()
        )
        val resp = call(baseUrl, null, "POST", "/api/v1/login", body, LoginData::class.java)
        return ok(resp).token ?: throw IllegalStateException("登录未返回 token")
    }

    /** FN ID OAuth 用授权码换 token。 */
    suspend fun authByCode(baseUrl: String, code: String): String {
        val body = mapOf("source" to "Trim-NAS", "code" to code)
        val resp = call(baseUrl, null, "POST", "/api/v1/auth", body, LoginData::class.java)
        return ok(resp).token ?: throw IllegalStateException("授权未返回 token")
    }

    suspend fun mediaDbList(baseUrl: String, token: String): List<MediaDb> {
        val url = baseUrl.trimEnd('/') + "/v/api/v1/mediadb/list"
        val text = rawRequest(url, token, "GET", null)
        val type = com.google.gson.reflect.TypeToken.getParameterized(Resp::class.java,
            com.google.gson.reflect.TypeToken.getParameterized(List::class.java, MediaDb::class.java).type).type
        val resp = gson.fromJson<Resp<List<MediaDb>>>(text, type) ?: Resp()
        return ok(resp)
    }

    suspend fun itemList(baseUrl: String, token: String, ancestorGuid: String, pageSize: Int = 200): ItemListData {
        val body = mapOf(
            "ancestor_guid" to ancestorGuid,
            "tags" to mapOf("type" to listOf("Movie", "TV", "Directory", "Video")),
            "exclude_grouped_video" to 1,
            "sort_type" to "DESC",
            "sort_column" to "create_time",
            "page_size" to pageSize
        )
        val resp = call(baseUrl, token, "POST", "/api/v1/item/list", body, ItemListData::class.java)
        return ok(resp)
    }

    suspend fun episodeList(baseUrl: String, token: String, guid: String): List<Item> {
        val url = baseUrl.trimEnd('/') + "/v/api/v1/episode/list/$guid"
        val text = rawRequest(url, token, "GET", null)
        val type = com.google.gson.reflect.TypeToken.getParameterized(Resp::class.java,
            com.google.gson.reflect.TypeToken.getParameterized(List::class.java, Item::class.java).type).type
        val resp = gson.fromJson<Resp<List<Item>>>(text, type) ?: Resp()
        return ok(resp)
    }

    suspend fun playInfo(baseUrl: String, token: String, itemGuid: String): PlayInfoData {
        val body = mapOf("item_guid" to itemGuid)
        val resp = call(baseUrl, token, "POST", "/api/v1/play/info", body, PlayInfoData::class.java)
        return ok(resp)
    }

    suspend fun stream(baseUrl: String, token: String, mediaGuid: String, account: String): StreamData {
        val ipMd5 = try {
            MessageDigest.getInstance("MD5").digest(account.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        } catch (e: Exception) { "" }
        val body = mapOf(
            "header" to mapOf("User-Agent" to listOf("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")),
            "level" to 1,
            "media_guid" to mediaGuid,
            "ip" to ipMd5,
            "nonce" to Random.nextInt(100000, 999999).toString()
        )
        val resp = call(baseUrl, token, "POST", "/api/v1/stream", body, StreamData::class.java)
        return ok(resp)
    }

    /** 返回原始文本的 GET（用于泛型 List 解析）。 */
    private suspend fun rawRequest(url: String, token: String?, method: String, body: Any?): String =
        withContext(Dispatchers.IO) {
            val path = url.substringAfter("/v", "")
            val jsonBody = body?.let { gson.toJson(it) }
            val builder = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Authx", authx("/v$path", jsonBody))
                .header("Cookie", "mode=relay")
            if (!token.isNullOrBlank()) builder.header("Authorization", token)
            if (method == "POST") builder.post(jsonBody?.toRequestBody(jsonMediaType)
                ?: "{}".toRequestBody(jsonMediaType)) else builder.get()
            val response = http.newCall(builder.build()).execute()
            response.use { it.body?.string().orEmpty() }
        }
}
