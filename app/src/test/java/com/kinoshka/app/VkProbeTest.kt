package com.kinoshka.app

import com.kinoshka.app.net.Http
import com.kinoshka.app.net.OkDownloader
import okhttp3.FormBody
import okhttp3.Request
import org.junit.Test

/** Разведка: что VK Видео отдаёт без входа в аккаунт. */
class VkProbeTest {
    private val jar = object : okhttp3.CookieJar {
        val store = mutableMapOf<String, okhttp3.Cookie>()
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) { cookies.forEach { store[it.domain + it.name] = it } }
        override fun loadForRequest(url: okhttp3.HttpUrl) = store.values.filter { it.matches(url) }
    }
    private val client = Http.client.newBuilder().cookieJar(jar).build()

    private fun get(url: String): Pair<Int, String> {
        val r = client.newCall(Request.Builder().url(url).header("User-Agent", OkDownloader.USER_AGENT).build()).execute()
        return r.use { it.code to (it.body?.string() ?: "") }
    }

    private fun post(form: Map<String, String>): Pair<Int, String> {
        val b = FormBody.Builder(); form.forEach { (k, v) -> b.add(k, v) }; b.add("al", "1")
        val req = Request.Builder().url("https://vk.com/al_video.php").post(b.build())
            .header("User-Agent", OkDownloader.USER_AGENT)
            .header("Referer", "https://vk.com/al_video.php")
            .header("X-Requested-With", "XMLHttpRequest").build()
        return client.newCall(req).execute().use { it.code to (it.body?.string() ?: "") }
    }

    @Test
    fun probe() {
        val out = StringBuilder()
        val names = listOf("mosfilm", "mosfilmofficial", "lenfilm", "gorkyfilmstudio", "soyuzmultfilm", "starmedia", "belarusfilm", "kinoshka_test_none")
        var firstOid: String? = null
        for (n in names) {
            for (host in listOf("https://vk.com/video/@$n", "https://vkvideo.ru/@$n")) {
                val (code, body) = runCatching { get(host) }.getOrElse { -1 to "EXC ${it.javaClass.simpleName} ${it.message}" }
                val oid = Regex("""var newCur\s*=\s*\{.*?"oid"\s*:\s*"?(-?\d+)""", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)
                val title = Regex("<title>(.*?)</title>").find(body)?.groupValues?.get(1)
                out.append("PAGE $host -> $code oid=$oid title=$title len=${body.length} ${if (code < 0) body.take(150) else ""}\n")
                if (oid != null && firstOid == null) firstOid = oid
            }
        }
        for (vid in listOf("-18403220_456239696", "-50883936_456244102", "-127553155_456242961")) {
            val (c2, b2) = runCatching { post(mapOf("act" to "show", "video" to vid)) }.getOrElse { -1 to "EXC ${it.message}" }
            val hls = Regex("\"hls\"\\s*:\\s*\"([^\"]+)\"").find(b2)?.groupValues?.get(1)
            val keys = Regex("\"(url\\d+|hls\\w*|dash\\w*|md_title|duration)\"\\s*:").findAll(b2).map { it.groupValues[1] }.toSet()
            out.append("SHOW $vid -> $c2 len=${b2.length} keys=$keys hls=${hls?.take(90)} head=${b2.take(250).replace('\n',' ')}\n")
        }
        for (oid in listOf("-17892518")) {
            val (c, b) = runCatching { post(mapOf("act" to "load_videos_silent", "offset" to "0", "oid" to oid, "section" to "all")) }.getOrElse { -1 to "EXC ${it.message}" }
            out.append("LIST $oid -> $c ${b.take(900).replace('\n',' ')}\n")
        }
        for (q in listOf("Бриллиантовая рука")) {
            val (c, b) = runCatching { post(mapOf("act" to "search_video", "q" to q, "offset" to "0")) }.getOrElse { -1 to "EXC ${it.message}" }
            out.append("SEARCH $q -> $c ${b.take(600).replace('\n',' ')}\n")
        }
        out.lines().forEach { println("VKPROBE| $it") }
    }
}
