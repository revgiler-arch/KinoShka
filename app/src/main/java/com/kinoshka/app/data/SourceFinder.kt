package com.kinoshka.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Ищет фильм на RuTube, YouTube и VK Видео, но берёт только каналы правообладателей:
 * из белого списка или с официальной отметкой площадки (если это включено).
 * Трейлеры, обзоры и нарезки отсекаются по длительности и названию.
 */
class SourceFinder(private val prefs: Prefs?) {
    private val whitelist get() = prefs?.whitelist ?: Prefs.DEFAULT_WHITELIST
    private val trustVerified get() = prefs?.trustVerified ?: true

    class Result(val sources: List<Source>, val errors: List<String>)

    suspend fun find(movie: Movie, force: Boolean = false): Result = withContext(Dispatchers.IO) {
        if (!force) prefs?.cachedSources(movie.id)?.let { return@withContext Result(it, emptyList()) }

        val query = buildString {
            append(movie.name)
            if (movie.year > 0) append(' ').append(movie.year)
            append(if (movie.isSeries) " сериал" else " фильм")
        }
        val errors = java.util.Collections.synchronizedList(mutableListOf<String>())
        val (rt, yt, vk) = coroutineScope {
            val rt = async {
                runCatching { Rutube.search(query) }
                    .onFailure { errors += "RuTube: ${it.message}" }
                    .getOrDefault(emptyList())
                    .mapNotNull { judge(movie, Platform.RUTUBE, it.id, it.title, it.channel, it.durationSec, it.thumb, it.official) }
            }
            val yt = async {
                runCatching { YouTube.search(query) }
                    .onFailure { errors += "YouTube: ${it.message}" }
                    .getOrDefault(emptyList())
                    .mapNotNull { judge(movie, Platform.YOUTUBE, it.id, it.title, it.channel, it.durationSec, it.thumb, it.verified) }
            }
            val vk = async {
                runCatching { VkVideo.search(query) }
                    .onFailure { errors += "VK: ${it.message}" }
                    .getOrDefault(emptyList())
                    .mapNotNull { judge(movie, Platform.VK, it.id, it.title, it.channel, it.durationSec, it.thumb, it.verified) }
            }
            Triple(rt.await(), yt.await(), vk.await())
        }
        val list = (rt + yt + vk).sortedByDescending { it.score }.distinctBy { it.platform to it.videoId }.take(8)
        // Пустой результат тоже кешируем, но только если обе площадки ответили без ошибок
        if (list.isNotEmpty() || errors.isEmpty()) prefs?.saveSources(movie.id, list)
        Result(list, errors)
    }

    /** null — видео не подходит. Иначе источник с оценкой (больше = лучше). */
    private fun judge(
        movie: Movie,
        platform: Platform,
        id: String,
        title: String,
        channel: String,
        durationSec: Long,
        thumb: String?,
        official: Boolean,
    ): Source? {
        val inWhitelist = inWhitelist(channel, official)
        // Галочку VK получают и частные лица — там доверяем только белому списку
        val trusted = official && trustVerified && platform != Platform.VK
        if (!inWhitelist && !trusted) return null

        val t = norm(title)
        if (BAD_WORDS.any { t.contains(it) }) return null

        // Название фильма должно встречаться в названии видео
        val names = listOfNotNull(movie.name, movie.altName).map(::norm).filter { it.length >= 2 }
        if (names.none { t.contains(it) }) return null

        // Год: если в названии видео есть другой год — это скорее ремейк или другой фильм
        val years = Regex("""\b(19[0-9]{2}|20[0-9]{2})\b""").findAll(title).map { it.value.toInt() }.toList()
        if (movie.year > 0 && years.isNotEmpty() && years.none { abs(it - movie.year) <= 1 }) return null

        // Длительность: полный фильм, а не трейлер или фрагмент
        val expected = movie.lengthMin * 60L
        if (!movie.isSeries) {
            val min = if (expected > 0) (expected * 0.6).toLong().coerceAtMost(50 * 60) else 40 * 60L
            if (durationSec in 1 until min) return null
            // Сильно длиннее фильма — скорее сборник или разбор (важно для коротких мультфильмов)
            if (expected > 0 && durationSec > expected * 2 + 10 * 60) return null
        } else if (durationSec in 1 until 15 * 60) return null

        var score = 0
        if (inWhitelist) score += 50
        if (official) score += 20
        if (movie.year > 0 && years.any { abs(it - movie.year) <= 1 }) score += 15
        if (expected > 0 && durationSec > 0) {
            val diff = abs(durationSec - expected).toDouble() / expected
            score += when {
                diff < 0.05 -> 30
                diff < 0.15 -> 15
                diff < 0.3 -> 5
                else -> -10
            }
        }
        if (names.any { t.startsWith(it) }) score += 10
        if (platform != Platform.YOUTUBE) score += 2 // HLS: качество подстраивается под сеть

        return Source(platform, id, title, channel, durationSec, thumb, official, score)
    }

    /**
     * Канал из белого списка: точное совпадение названия или название вида «Киноконцерн "Мосфильм"».
     * Частичное совпадение («СОЮЗМУЛЬТФИЛЬМ И НЕ ТОЛЬКО») принимаем, только если у канала
     * есть официальная отметка площадки — иначе это обычно чужой канал-перезалив.
     */
    private fun inWhitelist(channel: String, official: Boolean): Boolean {
        val c = norm(channel)
        return whitelist.map(::norm).filter { it.isNotEmpty() }.any { w ->
            c == w || c.endsWith(" $w") || c.startsWith("$w ") && c.length - w.length <= 12 ||
                (official && c.contains(w))
        }
    }

    companion object {
        private val BAD_WORDS = listOf(
            "трейлер", "trailer", "тизер", "teaser", "обзор", "review", "фрагмент", "отрывок",
            "нарезка", "за кадром", "как снимали", "making of", "реакция", "разбор", "киногрехи",
            "интервью", "клип", "саундтрек", "soundtrack", "shorts", "о чем", "смысл",
            "на самом деле", "тифлокомментар", "пародия", "сборник", "все серии подряд",
        )

        fun norm(s: String): String = s.lowercase()
            .replace('ё', 'е')
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
    }
}
