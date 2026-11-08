package com.kinoshka.app.player

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.kinoshka.app.App
import com.kinoshka.app.data.Movie
import com.kinoshka.app.data.Platform
import com.kinoshka.app.data.Rutube
import com.kinoshka.app.data.Source
import com.kinoshka.app.data.YouTube
import com.kinoshka.app.net.OkDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.Stream
import java.io.ByteArrayInputStream

/** Полноэкранный плеер. Пульт ТВ: ОК — пауза/меню, ←/→ — перемотка, «Назад» — выход. */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private lateinit var movie: Movie
    private lateinit var source: Source
    private lateinit var playerView: PlayerView
    private lateinit var status: TextView
    private var player: ExoPlayer? = null
    private var retried = false
    private val handler = Handler(Looper.getMainLooper())
    private val prefs get() = App.instance.prefs
    private val posKey get() = "${movie.id}"

    private val saver = object : Runnable {
        override fun run() {
            savePosition()
            handler.postDelayed(this, 10_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val m = intent.getStringExtra(EXTRA_MOVIE)?.let { Movie.fromJson(JSONObject(it)) }
        val s = intent.getStringExtra(EXTRA_SOURCE)?.let { Source.fromJson(JSONObject(it)) }
        if (m == null || s == null) {
            finish()
            return
        }
        movie = m
        source = s

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            setShowNextButton(false)
            setShowPreviousButton(false)
            controllerShowTimeoutMs = 3500
            keepScreenOn = true
        }
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            text = "Загружаю «${movie.name}»…"
        }
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                addView(playerView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            },
        )

        val p = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
        player = p
        playerView.player = p
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) status.visibility = TextView.GONE
                if (state == Player.STATE_ENDED) prefs.savePosition(posKey, 0)
            }

            override fun onPlayerError(error: PlaybackException) {
                // Ссылки YouTube/RuTube временные: один раз пробуем получить свежие
                if (!retried) {
                    retried = true
                    savePosition()
                    start()
                } else {
                    showError("Не удалось воспроизвести: ${error.errorCodeName}")
                }
            }
        })
        start()
    }

    private fun start() {
        val p = player ?: return
        status.visibility = TextView.VISIBLE
        lifecycleScope.launch {
            val media = try {
                withContext(Dispatchers.IO) { buildSource() }
            } catch (e: Exception) {
                showError(e.message ?: "Ошибка загрузки")
                return@launch
            }
            p.setMediaSource(media)
            p.prepare()
            val saved = prefs.position(posKey)
            if (saved > 60_000) {
                p.seekTo(saved)
                if (!retried) Toast.makeText(this@PlayerActivity, "Продолжаем с ${fmt(saved)}", Toast.LENGTH_SHORT).show()
            }
            p.playWhenReady = true
        }
    }

    /** Сетевая часть: получить ссылки и собрать источник для ExoPlayer. */
    private fun buildSource(): MediaSource = when (source.platform) {
        Platform.RUTUBE -> {
            val url = Rutube.playlistUrl(source.videoId)
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent(OkDownloader.USER_AGENT)
                .setDefaultRequestProperties(Rutube.HEADERS)
                .setAllowCrossProtocolRedirects(true)
            HlsMediaSource.Factory(http).createMediaSource(
                MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build(),
            )
        }
        Platform.YOUTUBE -> {
            val s = YouTube.streams(source.videoId, prefs.maxHeight)
            val video = youtubeSource(s.video, s.durationSec)
            val audio = s.audio?.let { youtubeSource(it, s.durationSec) }
            if (audio != null) MergingMediaSource(true, video, audio) else video
        }
    }

    /**
     * Поток YouTube отдаём плееру как DASH-манифест с диапазонами (как делает NewPipe):
     * так видео грузится кусками и YouTube не обрывает скачивание. Если не вышло — обычным файлом.
     */
    private fun youtubeSource(stream: Stream, durationSec: Long): MediaSource {
        val url = stream.content
        val ua = if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
            YoutubeParsingHelper.getVisionOsUserAgent(null)
        } else {
            OkDownloader.USER_AGENT
        }
        val http = DefaultHttpDataSource.Factory().setUserAgent(ua).setAllowCrossProtocolRedirects(true)
        val itag = stream.itagItem
        if (itag != null) {
            runCatching {
                val xml = YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(url, itag, durationSec)
                val manifest = DashManifestParser().parse(Uri.parse(url), ByteArrayInputStream(xml.toByteArray()))
                return DashMediaSource.Factory(http).createMediaSource(manifest, MediaItem.fromUri(url))
            }
        }
        return ProgressiveMediaSource.Factory(http).createMediaSource(MediaItem.fromUri(url))
    }

    private fun showError(text: String) {
        status.visibility = TextView.VISIBLE
        status.text = "$text\n\nНажмите «Назад», чтобы вернуться и выбрать другой источник."
    }

    /** Все кнопки пульта сначала получает плеер: ОК показывает меню, стрелки перематывают. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        val view = if (::playerView.isInitialized) playerView else null
        if (view != null && !view.isControllerFullyVisible && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { player?.seekBack(); view.showController(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { player?.seekForward(); view.showController(); return true }
            }
        }
        return view?.dispatchKeyEvent(event) == true || super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        handler.post(saver)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(saver)
        savePosition()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }

    private fun savePosition() {
        val p = player ?: return
        if (!::movie.isInitialized) return
        val pos = p.currentPosition
        val dur = p.duration
        if (pos <= 0 || dur == C.TIME_UNSET) return
        // Досмотрели почти до конца — в следующий раз начнём сначала
        prefs.savePosition(posKey, if (dur - pos < 3 * 60_000) 0 else pos)
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    companion object {
        private const val EXTRA_MOVIE = "movie"
        private const val EXTRA_SOURCE = "source"

        fun intent(ctx: Context, movie: Movie, source: Source): Intent =
            Intent(ctx, PlayerActivity::class.java)
                .putExtra(EXTRA_MOVIE, movie.toJson().toString())
                .putExtra(EXTRA_SOURCE, source.toJson().toString())
    }
}
