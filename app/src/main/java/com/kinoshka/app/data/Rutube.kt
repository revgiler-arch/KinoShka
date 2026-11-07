package com.kinoshka.app.data

import com.kinoshka.app.net.Http
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** Открытый веб-API RuTube: поиск видео и ссылка на поток HLS. Все вызовы блокирующие. */
object Rutube {
    private const val BASE = "https://rutube.ru"
    val HEADERS = mapOf("Referer" to "https://rutube.ru/", "Origin" to "https://rutube.ru")

    class Hit(
        val id: String,
        val title: String,
        val channel: String,
        val durationSec: Long,
        val thumb: String?,
        val official: Boolean,
    )

    fun search(query: String): List<Hit> {
        val url = "$BASE/api/search/video/?query=${URLEncoder.encode(query, "UTF-8")}&page=1"
        val json = Http.getJson(url, HEADERS)
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val o = results.optJSONObject(i) ?: return@mapNotNull null
            if (o.optBoolean("is_hidden") || o.optBoolean("is_deleted") || o.optBoolean("is_livestream")) {
                return@mapNotNull null
            }
            val id = o.str("id") ?: return@mapNotNull null
            val author = o.optJSONObject("author")
            Hit(
                id = id,
                title = o.str("title").orEmpty(),
                channel = author?.str("name").orEmpty(),
                durationSec = o.optLong("duration", 0),
                thumb = o.str("thumbnail_url"),
                official = o.optBoolean("is_official") || author?.optBoolean("is_official") == true,
            )
        }
    }

    /** Ссылка на мастер-плейлист HLS (.m3u8) со всеми качествами. */
    fun playlistUrl(videoId: String): String {
        val json: JSONObject = try {
            Http.getJson("$BASE/api/play/options/$videoId/?no_404=true&referer=&pver=v2", HEADERS)
        } catch (e: Http.HttpError) {
            throw IOException("RuTube не отдал видео (HTTP ${e.code})", e)
        }
        json.optJSONObject("detail")?.let { d ->
            if (d.optString("type") == "blocking_rule") {
                throw IOException("RuTube: видео недоступно в вашем регионе")
            }
        }
        return json.optJSONObject("video_balancer")?.str("m3u8")
            ?: throw IOException("RuTube: у видео нет открытого потока (возможно, оно платное)")
    }
}
