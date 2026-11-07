package com.kinoshka.app.data

import android.content.Context
import com.kinoshka.app.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder

/** Подборка на главном экране: название и фильтр запроса к kinopoisk.dev. */
data class Collection(val key: String, val title: String, val params: List<Pair<String, String>>)

/**
 * Каталог через неофициальный API kinopoisk.dev (v1.4).
 * Бесплатный ключ выдаёт Telegram-бот @kinopoiskdev_bot; лимит — порядка 200 запросов в сутки,
 * поэтому ответы кешируем на диске.
 */
class Kinopoisk(private val ctx: Context, private val prefs: Prefs) {

    class NoKey : IOException("Нужен ключ API Кинопоиска (Настройки)")

    private val cacheDir = File(ctx.cacheDir, "kp").apply { mkdirs() }

    suspend fun collection(c: Collection, page: Int = 1): List<Movie> =
        movies("/v1.4/movie", BASE_FILTER + c.params + listOf("page" to "$page", "limit" to "30"))

    suspend fun search(query: String): List<Movie> =
        movies("/v1.4/movie/search", listOf("query" to query, "page" to "1", "limit" to "30"), ttl = SEARCH_TTL)
            .filter { it.poster != null }

    private suspend fun movies(
        path: String,
        params: List<Pair<String, String>>,
        ttl: Long = CATALOG_TTL,
    ): List<Movie> = withContext(Dispatchers.IO) {
        val json = request(path, params, ttl)
        val docs = json.optJSONArray("docs") ?: return@withContext emptyList()
        (0 until docs.length()).mapNotNull { docs.optJSONObject(it)?.let(Movie::fromJson) }
    }

    private fun request(path: String, params: List<Pair<String, String>>, ttl: Long): JSONObject {
        val key = prefs.apiKey.ifBlank { throw NoKey() }
        val query = params.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
        val cacheFile = File(cacheDir, "${(path + "?" + query).hashCode().toUInt()}.json")
        if (cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < ttl) {
            runCatching { return JSONObject(cacheFile.readText()) }
        }

        // Сначала адрес, который сработал в прошлый раз, затем остальные
        val bases = (listOfNotNull(prefs.apiBase) + BASES).distinct()
        var last: Exception? = null
        for (base in bases) {
            try {
                val json = Http.getJson("$base$path?$query", mapOf("X-API-KEY" to key))
                prefs.apiBase = base
                runCatching { cacheFile.writeText(json.toString()) }
                return json
            } catch (e: Http.HttpError) {
                // 401/403 — ключ неверный или лимит исчерпан: другой домен не поможет
                if (e.code == 401 || e.code == 403) {
                    // если есть старый кеш — лучше показать его, чем ошибку
                    if (cacheFile.exists()) runCatching { return JSONObject(cacheFile.readText()) }
                    throw IOException("Кинопоиск: ${e.message} (проверьте ключ или дневной лимит)", e)
                }
                last = e
            } catch (e: IOException) {
                last = e
            }
        }
        if (cacheFile.exists()) runCatching { return JSONObject(cacheFile.readText()) }
        throw IOException("Кинопоиск недоступен: ${last?.message}", last)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        private val BASES = listOf("https://api.kinopoisk.dev", "https://api.poiskkino.dev")
        private const val CATALOG_TTL = 12L * 3_600_000
        private const val SEARCH_TTL = 24L * 3_600_000

        private val BASE_FILTER = listOf(
            "notNullFields" to "poster.url",
            "notNullFields" to "name",
            "sortField" to "votes.kp",
            "sortType" to "-1",
            "selectFields" to "id", "selectFields" to "name", "selectFields" to "alternativeName",
            "selectFields" to "enName", "selectFields" to "year", "selectFields" to "description",
            "selectFields" to "shortDescription", "selectFields" to "movieLength",
            "selectFields" to "rating", "selectFields" to "poster", "selectFields" to "backdrop",
            "selectFields" to "genres", "selectFields" to "countries", "selectFields" to "isSeries",
        )

        /**
         * Подборки идут от тех, что чаще всего есть у правообладателей бесплатно
         * (советское кино у «Мосфильма» и других студий), к остальным.
         */
        val COLLECTIONS = listOf(
            Collection(
                "soviet", "Советская классика",
                listOf("countries.name" to "СССР", "type" to "movie", "rating.kp" to "7-10"),
            ),
            Collection(
                "soviet_comedy", "Советские комедии",
                listOf("countries.name" to "СССР", "type" to "movie", "genres.name" to "комедия"),
            ),
            Collection(
                "cartoons", "Мультфильмы Союзмультфильма",
                listOf("countries.name" to "СССР", "type" to "cartoon"),
            ),
            Collection(
                "soviet_fantasy", "Сказки и фантастика",
                listOf("countries.name" to "СССР", "type" to "movie", "genres.name" to "фэнтези"),
            ),
            Collection(
                "russian", "Российское кино",
                listOf("countries.name" to "Россия", "type" to "movie", "year" to "2000-2026", "rating.kp" to "6.5-10"),
            ),
            Collection(
                "war", "Фильмы о войне",
                listOf("countries.name" to "СССР", "type" to "movie", "genres.name" to "военный"),
            ),
        )
    }
}
