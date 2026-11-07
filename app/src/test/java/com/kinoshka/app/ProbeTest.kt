package com.kinoshka.app

import com.kinoshka.app.data.Movie
import com.kinoshka.app.data.Platform
import com.kinoshka.app.data.Rutube
import com.kinoshka.app.data.SourceFinder
import com.kinoshka.app.data.YouTube
import com.kinoshka.app.net.OkDownloader
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

/** Диагностика на настоящей сети: находит ли поиск фильмы у правообладателей. */
class ProbeTest {
    private fun movie(id: Int, name: String, year: Int, min: Int) = Movie(
        id, name, null, year, null, min, 8.0, 8.0, null, null, emptyList(), listOf("СССР"), false,
    )

    @Test
    fun probe() {
        NewPipe.init(OkDownloader.instance, Localization("ru", "RU"), ContentCountry("RU"))
        val movies = listOf(
            movie(1, "Кин-дза-дза!", 1986, 135),
            movie(2, "Бриллиантовая рука", 1968, 94),
            movie(3, "Иван Васильевич меняет профессию", 1973, 88),
            movie(4, "Москва слезам не верит", 1979, 150),
            movie(5, "Ёжик в тумане", 1975, 10),
        )
        val finder = SourceFinder(null)
        val out = StringBuilder()
        for (u in listOf(
            "https://rutube.ru/api/search/video/?query=" + java.net.URLEncoder.encode("Кин-дза-дза", "UTF-8"),
            "https://rutube.ru/api/search/video/?query=" + java.net.URLEncoder.encode("Кин-дза-дза", "UTF-8") + "&format=json",
            "https://rutube.ru/api/video/person/1069744/?page=1",
        )) {
            val req = okhttp3.Request.Builder().url(u).header("User-Agent", OkDownloader.USER_AGENT)
                .header("Referer", "https://rutube.ru/").header("Accept", "application/json").build()
            val line = runCatching {
                com.kinoshka.app.net.Http.client.newCall(req).execute().use { r ->
                    "HTTP ${r.code} ${r.header("Content-Type")} " + (r.body?.string() ?: "").take(700).replace('\n', ' ')
                }
            }.getOrElse { "EXC ${it.message}" }
            out.append("RAW $u\n   $line\n")
        }
        for (m in movies) {
            val q = "${m.name} ${m.year} фильм"
            out.append("=== ${m.name} (${m.year})\n")
            runCatching { Rutube.search(q) }.fold(
                { hits -> out.append("RT raw ${hits.size}: ").append(hits.take(4).joinToString(" || ") { "${it.title} | ${it.channel} | off=${it.official} | ${it.durationSec}s" }).append('\n') },
                { out.append("RT EXC ${it.javaClass.simpleName}: ${it.message}\n") },
            )
            runCatching { YouTube.search(q) }.fold(
                { hits -> out.append("YT raw ${hits.size}: ").append(hits.take(4).joinToString(" || ") { "${it.title} | ${it.channel} | ver=${it.verified} | ${it.durationSec}s" }).append('\n') },
                { out.append("YT EXC ${it.javaClass.simpleName}: ${it.message}\n") },
            )
            val r = runBlocking { finder.find(m, force = true) }
            out.append("FOUND ${r.sources.size}: ").append(r.sources.joinToString(" || ") { "${it.platform} ${it.channel} score=${it.score} ${it.durationSec}s ${it.title}" }).append('\n')
            r.errors.forEach { out.append("ERR $it\n") }
            r.sources.firstOrNull { it.platform == Platform.RUTUBE }?.let { s ->
                out.append("RT play: ").append(runCatching { Rutube.playlistUrl(s.videoId).take(80) }.getOrElse { "EXC ${it.message}" }).append('\n')
            }
            r.sources.firstOrNull { it.platform == Platform.YOUTUBE }?.let { s ->
                out.append("YT play: ").append(
                    runCatching {
                        val st = YouTube.streams(s.videoId, 1080)
                        "video ${st.video.resolution} ${st.video.codec} itag=${st.video.itagItem != null} audio=${st.audio?.format} dur=${st.durationSec}"
                    }.getOrElse { "EXC ${it.javaClass.simpleName}: ${it.message}" },
                ).append('\n')
            }
        }
        out.lines().forEach { println("PROBE| $it") }
    }
}
