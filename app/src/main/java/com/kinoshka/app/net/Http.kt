package com.kinoshka.app.net

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Общий HTTP-клиент для каталога и RuTube. */
object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    class HttpError(val code: Int, message: String) : IOException(message)

    /** GET с JSON-ответом. Блокирующий вызов — звать из Dispatchers.IO. */
    fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject {
        val b = Request.Builder().url(url).header("User-Agent", OkDownloader.USER_AGENT)
        headers.forEach { (k, v) -> b.header(k, v) }
        client.newCall(b.build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "HTTP ${r.code}"
                throw HttpError(r.code, msg)
            }
            return JSONObject(body.ifBlank { "{}" })
        }
    }
}
