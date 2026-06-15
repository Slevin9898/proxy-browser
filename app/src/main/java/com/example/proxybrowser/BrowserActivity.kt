package com.example.proxybrowser

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature

class BrowserActivity : AppCompatActivity() {

private var proxyServer: LocalProxyServer? = null

private val webViews = ArrayList<WebView>()
private val tabButtons = ArrayList<View>()
private var currentIndex = -1

private lateinit var container: FrameLayout
private lateinit var tabBar: LinearLayout
private lateinit var addressBar: EditText

private val homeUrl = "https://claude.ai"

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val root = LinearLayout(this)
    root.orientation = LinearLayout.VERTICAL

    val topBar = LinearLayout(this)
    topBar.orientation = LinearLayout.HORIZONTAL

    addressBar = EditText(this)
    addressBar.hint = "Адрес сайта"
    addressBar.setSingleLine(true)
    addressBar.layoutParams = LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    val goButton = Button(this)
    goButton.text = "OK"

    val newTabButton = Button(this)
    newTabButton.text = "+"

    topBar.addView(addressBar)
    topBar.addView(goButton)
    topBar.addView(newTabButton)

    val tabScroll = HorizontalScrollView(this)
    tabBar = LinearLayout(this)
    tabBar.orientation = LinearLayout.HORIZONTAL
    tabScroll.addView(tabBar)

    container = FrameLayout(this)
    container.layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)

    root.addView(topBar)
    root.addView(tabScroll)
    root.addView(container)
    setContentView(root)

    goButton.setOnClickListener {
        val url = normalizeUrl(addressBar.text.toString().trim())
        currentWebView()?.loadUrl(url)
    }
    newTabButton.setOnClickListener {
        addTab(homeUrl)
    }

    val prefs = getSharedPreferences("settings", MODE_PRIVATE)
    val useProxy = prefs.getBoolean("use_proxy", false)
    val proxyStr = prefs.getString("proxy", "") ?: ""

    if (useProxy && proxyStr.isNotEmpty() && WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
        startProxyThenOpen(proxyStr)
    } else {
        addTab(homeUrl)
    }
}

private fun startProxyThenOpen(proxyStr: String) {
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
            { runOnUiThread { addTab(homeUrl) } }
        )
    } catch (e: Exception) {
        Toast.makeText(this, "Ошибка прокси: " + e.message, Toast.LENGTH_LONG).show()
        addTab(homeUrl)
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(): WebView {
    val wv = WebView(this)
    val cm = CookieManager.getInstance()
    cm.setAcceptCookie(true)
    cm.setAcceptThirdPartyCookies(wv, true)
    val s = wv.settings
    s.javaScriptEnabled = true
    s.domStorageEnabled = true
    s.databaseEnabled = true
    s.cacheMode = WebSettings.LOAD_DEFAULT

    wv.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            if (view === currentWebView()) {
                addressBar.setText(url ?: "")
            }
        }
        override fun onPageFinished(view: WebView?, url: String?) {
            updateTabTitles()
        }
    }
    wv.webChromeClient = object : WebChromeClient() {
        override fun onReceivedTitle(view: WebView?, title: String?) {
            updateTabTitles()
        }
    }
    return wv
}

private fun addTab(url: String) {
    val wv = createWebView()
    webViews.add(wv)

    val tabView = LinearLayout(this)
    tabView.orientation = LinearLayout.HORIZONTAL
    tabView.setPadding(16, 8, 16, 8)

    val titleView = TextView(this)
    titleView.text = "Новая вкладка"
    titleView.maxWidth = 300
    titleView.setSingleLine(true)

    val closeView = TextView(this)
    closeView.text = "  ✕"

    tabView.addView(titleView)
    tabView.addView(closeView)
    tabBar.addView(tabView)
    tabButtons.add(tabView)

    tabView.setOnClickListener { selectTab(webViews.indexOf(wv)) }
    closeView.setOnClickListener { closeTab(webViews.indexOf(wv)) }

    selectTab(webViews.size - 1)
    wv.loadUrl(url)
}

private fun selectTab(index: Int) {
    if (index < 0 || index >= webViews.size) return
    currentIndex = index
    container.removeAllViews()
    val wv = webViews[index]
    container.addView(wv, FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    addressBar.setText(wv.url ?: "")
    highlightTabs()
}

private fun closeTab(index: Int) {
    if (index < 0 || index >= webViews.size) return
    val wv = webViews[index]
    val tabView = tabButtons[index]
    webViews.removeAt(index)
    tabButtons.removeAt(index)
    tabBar.removeView(tabView)
    container.removeView(wv)
    wv.destroy()

    if (webViews.isEmpty()) {
        addTab(homeUrl)
        return
    }
    val newIndex = if (index >= webViews.size) webViews.size - 1 else index
    selectTab(newIndex)
}

private fun currentWebView(): WebView? {
    if (currentIndex < 0 || currentIndex >= webViews.size) return null
    return webViews[currentIndex]
}

private fun updateTabTitles() {
    for (i in webViews.indices) {
        val tv = (tabButtons[i] as LinearLayout).getChildAt(0) as TextView
        val title = webViews[i].title
        tv.text = if (title.isNullOrEmpty()) "Вкладка" else title
    }
}

private fun highlightTabs() {
    for (i in tabButtons.indices) {
        if (i == currentIndex) {
            tabButtons[i].setBackgroundColor(0xFFB0C4DE.toInt())
        } else {
            tabButtons[i].setBackgroundColor(0xFFEEEEEE.toInt())
        }
    }
}

private fun normalizeUrl(input: String): String {
    if (input.isEmpty()) return homeUrl
    if (input.startsWith("http://") || input.startsWith("https://")) return input
    if (input.contains(".") && !input.contains(" ")) return "https://" + input
    return "https://www.google.com/search?q=" + android.net.Uri.encode(input)
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
    val wv = currentWebView()
    if (wv != null && wv.canGoBack()) {
        wv.goBack()
    } else {
        super.onBackPressed()
    }
}
}
