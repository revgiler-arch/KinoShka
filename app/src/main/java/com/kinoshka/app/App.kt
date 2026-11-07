package com.kinoshka.app

import android.app.Application
import com.kinoshka.app.data.Prefs
import com.kinoshka.app.net.OkDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class App : Application() {
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashRecorder()
        prefs = Prefs(this)
        // Фильмы ищем на русском — так YouTube выдаёт русские каналы киностудий
        runCatching {
            NewPipe.init(OkDownloader.instance, Localization("ru", "RU"), ContentCountry("RU"))
        }.onFailure { NewPipe.init(OkDownloader.instance) }
    }

    /** При падении сохраняем текст ошибки, чтобы показать его при следующем запуске. */
    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val text = buildString {
                    append("КиноШка ").append(versionName()).append('\n')
                    append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL)
                    append(", Android ").append(android.os.Build.VERSION.RELEASE).append("\n")
                    append("Поток: ").append(thread.name).append("\n\n")
                    append(android.util.Log.getStackTraceString(e))
                }
                crashFile(this).writeText(text.take(20_000))
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun versionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull() ?: "?"

    companion object {
        fun crashFile(ctx: android.content.Context) = java.io.File(ctx.filesDir, "last_crash.txt")

        lateinit var instance: App
            private set
    }
}
