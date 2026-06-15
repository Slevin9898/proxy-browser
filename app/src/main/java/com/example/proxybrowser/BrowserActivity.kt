package com.example.proxybrowser

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature

class BrowserActivity : AppCompatActivity() {

private lateinit var webView: WebView
private var proxyServer: LocalProxyServer? = null

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    webView = WebView(this)
    setContentView(webView)

    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)

    val s = webView.settings
    s.javaScriptEnabled = true
    s.domStorageEnabled = true
    s.databaseEnabled = true
    s.cacheMode = WebSettings.LOAD_DEFAULT

    webView.webViewClient = WebViewClient()

    val prefs = getSharedPreferences("settings", MODE_PRIVATE)
    val proxyStr = prefs.getString("proxy", "") ?: ""

    if (proxyStr.isNotEmpty() && WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
        startWithProxy(proxyStr)
    } else {
        loadSite()
    }
}

private fun startWithProxy(proxyStr: String) {
    try {
        val parts = proxyStr.split(":")
        val host = parts[0]
        val port = parts[1].toInt()
        val user = if (parts.size > 2) parts[2] else null
        val pass = if (parts.size > 3) parts[3] else null

        val server = LocalProxyServer(host, port, user, pass)
        val localPort = server.start()
        proxyServer = server

        val config = ProxyConfig.Builder()
            .addProxyRule("127.0.0.1:" + localPort)
            .build()

        ProxyController.getInstance().setProxyOverride(
            config,
            { r -> r.run() },
            { runOnUiThread { loadSite() } }
        )
    } catch (e: Exception) {
        Toast.makeText(this, "Ошибка прокси: " + e.message, Toast.LENGTH_LONG).show()
        loadSite()
    }
}

private fun loadSite() {
    if (webView.url == null) {
        webView.loadUrl("https://claude.ai")
    }
}

override fun onPause() {
    super.onPause()
    CookieManager.getInstance().flush()
}

override fun onDestroy() {
    super.onDestroy()
    proxyServer?.stop()
}

@Deprecated("Deprecated in Java")
override fun onBackPressed() {
    if (webView.canGoBack()) {
        webView.goBack()
    } else {
        super.onBackPressed()
    }
}
}
