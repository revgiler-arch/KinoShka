package com.kinoshka.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat

/** Experimental embedded browser for kinogo.online. It loads the site itself;
 * it does not extract or bypass protected video links. */
class KinoGoActivity : ComponentActivity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val root = FrameLayout(this)
        webView = WebView(this)
        val progress = ProgressBar(this)
        val message = TextView(this).apply {
            text = "Загрузка KinoGo… Если сайт блокирует доступ или воспроизведение, это ограничение самого сайта."
            textSize = 16f
            setPadding(24, 24, 24, 24)
        }
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))
        root.addView(progress, FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER))
        root.addView(message, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.TOP))
        setContentView(root)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = true
            allowContentAccess = true
            allowFileAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = userAgentString + " KinoShkaWebView"
        }
        android.webkit.CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                message.visibility = if (newProgress < 15) View.VISIBLE else View.GONE
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                // Keep normal navigation in the embedded browser. External schemes
                // (intent:, market:, etc.) are not launched from this prototype.
                return uri.scheme != "https" && uri.scheme != "http"
            }
        }
        webView.loadUrl("https://kinogo.online/")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
