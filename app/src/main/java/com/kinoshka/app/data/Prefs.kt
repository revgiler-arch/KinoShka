package com.kinoshka.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Настройки и всё, что храним на устройстве: ключ API, белый список, история, позиции. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("kinoshka", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "").orEmpty()
        set(v) = sp.edit().putString("api_key", v.trim()).apply()

    /** Какой из адресов kinopoisk.dev ответил последним (сервис менял домен). */
    var apiBase: String?
        get() = sp.getString("api_base", null)
        set(v) = sp.edit().putString("api_base", v).apply()

    /** Каналы правообладателей, которым доверяем (части названий, через запятую/строку). */
    var whitelist: List<String>
        get() = sp.getString("whitelist", null)?.let(::splitList) ?: DEFAULT_WHITELIST
        set(v) = sp.edit().putString("whitelist", v.joinToString("\n")).apply()

    /** Доверять ли каналам с официальной отметкой площадки (галочка YouTube, «официальный» на RuTube). */
    var trustVerified: Boolean
        get() = sp.getBoolean("trust_verified", true)
        set(v) = sp.edit().putBoolean("trust_verified", v).apply()

    /** Максимальное качество YouTube: 720 или 1080. */
    var maxHeight: Int
        get() = sp.getInt("max_height", 1080)
        set(v) = sp.edit().putInt("max_height", v).apply()

    // ---- Кеш найденных источников: id фильма -> список ----

    fun cachedSources(movieId: Int): List<Source>? {
        val raw = sp.getString("src_$movieId", null) ?: return null
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (System.currentTimeMillis() - o.optLong("at") > SOURCE_TTL) return null
        val arr = o.optJSONArray("list") ?: return null
        return (0 until arr.length()).mapNotNull { Source.fromJson(arr.getJSONObject(it)) }
    }

    fun saveSources(movieId: Int, list: List<Source>) {
        val o = JSONObject().put("at", System.currentTimeMillis())
            .put("list", JSONArray(list.map { it.toJson() }))
        sp.edit().putString("src_$movieId", o.toString()).apply()
    }

    fun clearSourceCache() {
        val e = sp.edit()
        for (k in sp.all.keys) if (k.startsWith("src_")) e.remove(k)
        e.apply()
    }

    // ---- «Продолжить просмотр» ----

    fun history(): List<Movie> {
        val arr = runCatching { JSONArray(sp.getString("history", "[]")) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { Movie.fromJson(arr.getJSONObject(it)) }
    }

    fun addToHistory(m: Movie) {
        val list = listOf(m) + history().filter { it.id != m.id }
        sp.edit().putString("history", JSONArray(list.take(30).map { it.toJson() }).toString()).apply()
    }

    fun removeFromHistory(id: Int) {
        val list = history().filter { it.id != id }
        sp.edit().putString("history", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    fun position(key: String): Long = sp.getLong("pos_$key", 0)
    fun savePosition(key: String, ms: Long) = sp.edit().putLong("pos_$key", ms).apply()

    companion object {
        private const val SOURCE_TTL = 3L * 24 * 3_600_000 // 3 дня

        val DEFAULT_WHITELIST = listOf(
            "Мосфильм", "Mosfilm",
            "Ленфильм", "Lenfilm",
            "Киностудия Горького", "Gorky Film",
            "Союзмультфильм", "Soyuzmultfilm",
            "Беларусьфильм", "Belarusfilm",
            "Одесская киностудия",
            "Свердловская киностудия",
            "Star Media", "StarMedia",
        )

        fun splitList(s: String): List<String> =
            s.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
