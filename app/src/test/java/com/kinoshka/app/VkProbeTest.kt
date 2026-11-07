package com.kinoshka.app

import com.kinoshka.app.net.Http
import com.kinoshka.app.net.OkDownloader
import okhttp3.FormBody
import okhttp3.Request
import org.junit.Test

/** Разведка: что VK Видео отдаёт без входа в аккаунт. */
class VkProbeTest {
    private fun get(url: String): Pair<Int, String> {
        val r = Http.client.newCall(Request.Builder().url(url).header("User-Agent", OkDownloader.USER_AGENT).build()).execute()
        return r.use { it.code to (it.body?.string() ?: "") }
    }

    private fun post(form: Map<String, String>): Pair<Int, String> {
        val b = FormBody.Builder(); form.forEach { (k, v) -> b.add(k, v) }; b.add("al", "1")
        val req = Request.Builder().url("https://vk.com/al_video.php").post(b.build())
            .header("User-Agent", OkDownloader.USER_AGENT)
            .header("Referer", "https://vk.com/al_video.php")
            .header("X-Requested-With", "XMLHttpRequest").build()
        return Http.client.newCall(req).execute().use { it.code to (it.body?.string() ?: "") }
    }

    @Test
    fun probe() {
        val out = StringBuilder()
        val names = listOf("mosfilm", "mosfilmofficial", "lenfilm", "gorkyfilmstudio", "soyuzmultfilm", "starmedia", "belarusfilm", "kinoshka_test_none")
        var firstOid: String? = null
        for (n in names) {
            for (host in listOf("https://vk.com/video/@$n", "https://vkvideo.ru/@$n")) {
                val (code, body) = runCatching { get(host) }.getOrElse { -1 to "EXC ${it.message}" }
                val oid = Regex("""var newCur\s*=\s*\{.*?"oid"\s*:\s*"?(-?\d+)""", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)
                val title = Regex("<title>(.*?)</title>").find(body)?.groupValues?.get(1)
                out.append("PAGE $host -> $code oid=$oid title=$title len=${body.length}\n")
                if (oid != null && firstOid == null) firstOid = oid
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
