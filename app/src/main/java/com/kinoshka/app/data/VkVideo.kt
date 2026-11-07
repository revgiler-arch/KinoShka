package com.kinoshka.app.data

import com.kinoshka.app.net.Http
import com.kinoshka.app.net.OkDownloader
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * VK Видео через веб-интерфейс vk.com (тот же, что использует yt-dlp). Вход не нужен.
 * Все вызовы блокирующие.
 */
object VkVideo {
    val HEADERS = mapOf("Referer" to "https://vkvideo.ru/", "Origin" to "https://vkvideo.ru")

    /** VK раздаёт cookies через цепочку редиректов — без их хранения запросы зацикливаются. */
    private val jar = object : CookieJar {
        private val store = ConcurrentHashMap<String, Cookie>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { store[it.domain + "|" + it.path + "|" + it.name] = it }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            return store.values.filter { it.expiresAt > now && it.matches(url) }
        }
    }
    private val client: OkHttpClient = Http.client.newBuilder().cookieJar(jar).build()

    @Volatile
    private var warmedUp = false

    class Hit(
        val id: String, // "<owner>_<video>", например -20286388_456239942
        val title: String,
        val channel: String,
        val durationSec: Long,
        val thumb: String?,
        val verified: Boolean,
    )

    fun search(query: String): List<Hit> {
        val json = al(mapOf("act" to "search_video", "q" to query, "offset" to "0"))
        val list = findList(json.optJSONArray("payload")?.optJSONArray(1)) ?: return emptyList()
        return (0 until list.length()).mapNotNull { i -> list.optJSONArray(i)?.let(::parseItem) }
    }

    /** Ссылка на мастер-плейлист HLS. */
    fun playlistUrl(id: String): String {
        val json = al(mapOf("act" to "show", "video" to id))
        val payload = json.optJSONArray("payload")?.optJSONArray(1)
            ?: throw IOException("VK: пустой ответ")
        var params: JSONObject? = null
        for (i in payload.length() - 1 downTo 0) {
            val p = payload.optJSONObject(i)?.optJSONObject("player")?.optJSONArray("params")?.optJSONObject(0)
            if (p != null) { params = p; break }
        }
        val p = params ?: throw IOException(
            "VK: видео недоступно" + (payload.optString(0).takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""),
        )
        return p.str("hls") ?: p.str("hls_fmp4")
            ?: throw IOException("VK: у видео нет потока HLS")
    }

    private fun al(form: Map<String, String>): JSONObject {
        warmUp()
        val body = FormBody.Builder().apply {
            form.forEach { (k, v) -> add(k, v) }
            add("al", "1")
        }.build()
        val req = Request.Builder().url("https://vk.com/al_video.php").post(body)
            .header("User-Agent", OkDownloader.USER_AGENT)
            .header("Referer", "https://vk.com/al_video.php")
            .header("X-Requested-With", "XMLHttpRequest")
            .build()
        val text = client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("VK ответил HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
        val json = runCatching { JSONObject(text.substring(text.indexOf('{').coerceAtLeast(0))) }
            .getOrElse { throw IOException("VK: непонятный ответ") }
        val code = json.optJSONArray("payload")?.opt(0)?.toString()
        when (code) {
            "3" -> throw IOException("VK просит войти в аккаунт")
            "8" -> throw IOException("VK: ошибка доступа")
        }
        return json
    }

    /** Первый заход на сайт, чтобы получить cookies. */
    private fun warmUp() {
        if (warmedUp) return
        runCatching {
            val req = Request.Builder().url("https://vkvideo.ru/").header("User-Agent", OkDownloader.USER_AGENT).build()
            client.newCall(req).execute().close()
        }
        warmedUp = true
    }

    /** Ищет в ответе массив "list" (его положение в ответе VK иногда меняется). */
    private fun findList(arr: JSONArray?): JSONArray? {
        if (arr == null) return null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            o.optJSONArray("list")?.let { return it }
            // формат списков сообществ: {"all": {"list": [...]}}
            o.keys().forEach { k -> o.optJSONObject(k)?.optJSONArray("list")?.let { return it } }
        }
        return null
    }

    /** Элемент списка: [owner, id, превью, название, флаги, "1:34:19", "", "", html-владельца, ...]. */
    private fun parseItem(a: JSONArray): Hit? {
        val owner = a.optLong(0, 0)
        val vid = a.optLong(1, 0)
        if (owner == 0L || vid == 0L) return null
        val ownerHtml = a.optString(8)
        val channel = Regex(""">([^<]+)</a>""").find(ownerHtml)?.groupValues?.get(1)?.let(::unescape).orEmpty()
        return Hit(
            id = "${owner}_$vid",
            title = unescape(a.optString(3)),
            channel = channel,
            durationSec = parseDuration(a.optString(5)),
            thumb = a.optString(2).takeIf { it.startsWith("http") },
            verified = ownerHtml.contains("page_verified"),
        )
    }

    fun parseDuration(s: String): Long {
        val parts = s.split(':').mapNotNull { it.trim().toLongOrNull() }
        if (parts.isEmpty()) return 0
        return parts.fold(0L) { acc, p -> acc * 60 + p }
    }

    private fun unescape(s: String) = s
        .replace("&quot;", "\"").replace("&#33;", "!").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&")
}
