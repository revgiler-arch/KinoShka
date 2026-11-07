package com.kinoshka.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Фильм из каталога Кинопоиска. */
data class Movie(
    val id: Int,
    val name: String,
    val altName: String?,
    val year: Int,
    val description: String?,
    val lengthMin: Int,
    val ratingKp: Double,
    val ratingImdb: Double,
    val poster: String?,
    val backdrop: String?,
    val genres: List<String>,
    val countries: List<String>,
    val isSeries: Boolean,
) {
    val kinopoiskUrl get() = "https://www.kinopoisk.ru/${if (isSeries) "series" else "film"}/$id/"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("alternativeName", altName).put("year", year)
        .put("description", description).put("movieLength", lengthMin)
        .put("rating", JSONObject().put("kp", ratingKp).put("imdb", ratingImdb))
        .put("poster", JSONObject().put("previewUrl", poster))
        .put("backdrop", JSONObject().put("url", backdrop))
        .put("genres", JSONArray(genres.map { JSONObject().put("name", it) }))
        .put("countries", JSONArray(countries.map { JSONObject().put("name", it) }))
        .put("isSeries", isSeries)

    companion object {
        /** Разбор объекта фильма из ответа kinopoisk.dev v1.4 (и из нашего же toJson). */
        fun fromJson(o: JSONObject): Movie? {
            val id = o.optInt("id", 0).takeIf { it > 0 } ?: return null
            val name = o.str("name") ?: o.str("alternativeName") ?: o.str("enName") ?: return null
            val rating = o.optJSONObject("rating")
            val poster = o.optJSONObject("poster")
            return Movie(
                id = id,
                name = name,
                altName = o.str("alternativeName") ?: o.str("enName"),
                year = o.optInt("year", 0),
                description = o.str("description") ?: o.str("shortDescription"),
                lengthMin = o.optInt("movieLength", 0).takeIf { it > 0 }
                    ?: o.optInt("seriesLength", 0),
                ratingKp = rating?.optDouble("kp", 0.0)?.takeIf { !it.isNaN() } ?: 0.0,
                ratingImdb = rating?.optDouble("imdb", 0.0)?.takeIf { !it.isNaN() } ?: 0.0,
                poster = poster?.str("previewUrl") ?: poster?.str("url"),
                backdrop = o.optJSONObject("backdrop")?.str("url"),
                genres = o.optJSONArray("genres").names(),
                countries = o.optJSONArray("countries").names(),
                isSeries = o.optBoolean("isSeries", false),
            )
        }

        private fun JSONArray?.names(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { optJSONObject(it)?.str("name") }
        }
    }
}

/** Где можно посмотреть фильм. */
enum class Platform(val title: String) { RUTUBE("RuTube"), YOUTUBE("YouTube") }

/** Найденное видео с фильмом на одной из площадок. */
data class Source(
    val platform: Platform,
    val videoId: String,
    val title: String,
    val channel: String,
    val durationSec: Long,
    val thumb: String?,
    val official: Boolean,
    val score: Int,
) {
    val pageUrl get() = when (platform) {
        Platform.RUTUBE -> "https://rutube.ru/video/$videoId/"
        Platform.YOUTUBE -> "https://www.youtube.com/watch?v=$videoId"
    }

    fun toJson(): JSONObject = JSONObject()
        .put("platform", platform.name).put("videoId", videoId).put("title", title)
        .put("channel", channel).put("durationSec", durationSec).put("thumb", thumb)
        .put("official", official).put("score", score)

    companion object {
        fun fromJson(o: JSONObject): Source? = runCatching {
            Source(
                platform = Platform.valueOf(o.getString("platform")),
                videoId = o.getString("videoId"),
                title = o.optString("title"),
                channel = o.optString("channel"),
                durationSec = o.optLong("durationSec"),
                thumb = o.str("thumb"),
                official = o.optBoolean("official"),
                score = o.optInt("score"),
            )
        }.getOrNull()
    }
}

/** optString без "null" и пустых строк. */
fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }
