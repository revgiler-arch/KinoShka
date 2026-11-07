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
        val (_, page) = get("https://vkvideo.ru/@mosfilm")
        for (kw in listOf("anonym", "client_id", "api.vkvideo", "access_token", "\"oid\"", "owner_id", "group_id", "mosfilm")) {
            Regex(Regex.escape(kw)).findAll(page).take(3).forEach { m ->
                val a = (m.range.first - 120).coerceAtLeast(0); val b = (m.range.last + 160).coerceAtMost(page.length)
                out.append("KW[$kw] ").append(page.substring(a, b).replace('\n', ' ')).append('\n')
            }
        }
        val oid = firstOid
        if (oid != null) {
            val (c, b) = post(mapOf("act" to "load_videos_silent", "offset" to "0", "oid" to oid, "section" to "all"))
            out.append("LIST $oid -> $c ${b.take(1500)}\n")
            val vid = Regex("""\[\s*(-?\d+)\s*,\s*(\d+)\s*,""").find(b.substringAfter("\"list\""))?.let { "${it.groupValues[1]}_${it.groupValues[2]}" }
            if (vid != null) {
                val (c2, b2) = post(mapOf("act" to "show", "video" to vid))
                val hls = Regex("\"hls\"\\s*:\\s*\"([^\"]+)\"").find(b2)?.groupValues?.get(1)
                val keys = Regex("\"(url\\d+|hls\\w*|dash\\w*)\"\\s*:").findAll(b2).map { it.groupValues[1] }.toSet()
                out.append("SHOW $vid -> $c2 keys=$keys hls=${hls?.take(100)} head=${b2.take(300)}\n")
            }
        }
        out.lines().forEach { println("VKPROBE| $it") }
    }
}
