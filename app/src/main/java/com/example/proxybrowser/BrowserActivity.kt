package com.example.proxybrowser

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
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

    private val pinnedSites = linkedMapOf(
        "Claude" to "https://claude.ai",
        "ChatGPT" to "https://chatgpt.com",
        "YouTube" to "https://www.youtube.com",
        "Google" to "https://www.google.com"
    )
    private val pinnedWebViews = mutableMapOf<String, WebView>()

    // Переводит dp (условные единицы) в реальные пиксели экрана
    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    // Параметры для кнопок верхней панели (побольше, с отступом друг от друга)
    private fun navButtonLayoutParams(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56))
        p.marginEnd = dp(6)
        return p
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        topBar.setPadding(dp(4), dp(6), dp(4), dp(6))

        addressBar = EditText(this)
        addressBar.hint = "Адрес сайта"
        addressBar.setSingleLine(true)
        addressBar.textSize = 16f
        addressBar.setPadding(dp(10), 0, dp(10), 0)
        addressBar.layoutParams = LinearLayout.LayoutParams(
            0, dp(56), 1f)

        val backButton = Button(this)
        backButton.text = "◀"
        backButton.textSize = 20f
        backButton.minWidth = dp(56)
        backButton.minimumWidth = dp(56)
        backButton.setPadding(dp(4), dp(4), dp(4), dp(4))
        backButton.layoutParams = navButtonLayoutParams()

        val forwardButton = Button(this)
        forwardButton.text = "▶"
        forwardButton.textSize = 20f
        forwardButton.minWidth = dp(56)
        forwardButton.minimumWidth = dp(56)
        forwardButton.setPadding(dp(4), dp(4), dp(4), dp(4))
        forwardButton.layoutParams = navButtonLayoutParams()

        val reloadButton = Button(this)
        reloadButton.text = "⟳"
        reloadButton.textSize = 20f
        reloadButton.minWidth = dp(56)
        reloadButton.minimumWidth = dp(56)
        reloadButton.setPadding(dp(4), dp(4), dp(4), dp(4))
        reloadButton.layoutParams = navButtonLayoutParams()

        val goButton = Button(this)
        goButton.text = "OK"
        goButton.textSize = 16f
        goButton.minWidth = dp(64)
        goButton.minimumWidth = dp(64)
        goButton.setPadding(dp(8), dp(4), dp(8), dp(4))
        goButton.layoutParams = navButtonLayoutParams()

        val newTabButton = Button(this)
        newTabButton.text = "+"
        newTabButton.textSize = 22f
        newTabButton.minWidth = dp(56)
        newTabButton.minimumWidth = dp(56)
        newTabButton.setPadding(dp(4), dp(4), dp(4), dp(4))
        newTabButton.layoutParams = navButtonLayoutParams()

        topBar.addView(backButton)
        topBar.addView(forwardButton)
        topBar.addView(reloadButton)
        topBar.addView(addressBar)
        topBar.addView(goButton)
        topBar.addView(newTabButton)

        val pinnedBar = LinearLayout(this)
        pinnedBar.orientation = LinearLayout.HORIZONTAL
        for ((name, url) in pinnedSites) {
            val btn = Button(this)
            btn.text = name
            btn.textSize = 12f
            btn.setAllCaps(false)
            btn.setSingleLine(true)
            btn.setPadding(4, 8, 4, 8)
            btn.layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            btn.setOnClickListener { openPinned(name, url) }
            pinnedBar.addView(btn)
        }

        val tabScroll = HorizontalScrollView(this)
        tabBar = LinearLayout(this)
        tabBar.orientation = LinearLayout.HORIZONTAL
        tabBar.setPadding(dp(4), dp(4), dp(4), dp(4))
        tabScroll.addView(tabBar)

        container = FrameLayout(this)
        container.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)

        root.addView(topBar)
        root.addView(pinnedBar)
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
        backButton.setOnClickListener {
            val wv = currentWebView()
            if (wv != null && wv.canGoBack()) wv.goBack()
        }
        forwardButton.setOnClickListener {
            val wv = currentWebView()
            if (wv != null && wv.canGoForward()) wv.goForward()
        }
        reloadButton.setOnClickListener {
            currentWebView()?.reload()
        }

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val useProxy = prefs.getBoolean("use_proxy", false)
        val proxyStr = prefs.getString("proxy", "") ?: ""

        if (useProxy && proxyStr.isNotEmpty() && WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            startProxyThenOpen(proxyStr)
        } else {
            openStartTabs()
        }
    }

    private fun openStartTabs() {
        openPinned("Claude", homeUrl)
    }

    private fun openPinned(name: String, url: String) {
        val existing = pinnedWebViews[name]
        if (existing != null && webViews.contains(existing)) {
            selectTab(webViews.indexOf(existing))
            return
        }
        addTab(url)
        pinnedWebViews[name] = webViews[webViews.size - 1]
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
                { runOnUiThread { openStartTabs() } }
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка прокси: " + e.message, Toast.LENGTH_LONG).show()
            openStartTabs()
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

        // Заставляет страницу правильно подстраиваться под ширину экрана
        s.useWideViewPort = true
        s.loadWithOverviewMode = true

        // Разрешает щипковый зум (pinch-zoom), если где-то элементы всё же мелкие
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false

        // Мобильный User-Agent — сайты присылают мобильную (адаптивную) версию страниц
        s.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

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
        tabView.setPadding(dp(12), dp(10), dp(12), dp(10))
        val tabParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tabParams.marginEnd = dp(6)
        tabParams.topMargin = dp(4)
        tabParams.bottomMargin = dp(4)
        tabView.layoutParams = tabParams

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

        val pinnedKey = pinnedWebViews.entries.firstOrNull { it.value === wv }?.key
        if (pinnedKey != null) {
            pinnedWebViews.remove(pinnedKey)
        }

        val tabView = tabButtons[index]
        webViews.removeAt(index)
        tabButtons.removeAt(index)
        tabBar.removeView(tabView)
        container.removeView(wv)
        wv.destroy()

        if (webViews.isEmpty()) {
            openStartTabs()
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

    // Рисует каждой вкладке рамку, чтобы они не сливались друг с другом
    private fun styleTab(view: View, selected: Boolean) {
        val bg = GradientDrawable()
        bg.cornerRadius = dp(8).toFloat()
        bg.setStroke(dp(1), 0xFF999999.toInt())
        bg.setColor(if (selected) 0xFFB0C4DE.toInt() else 0xFFF2F2F2.toInt())
        view.background = bg
    }

    private fun highlightTabs() {
        for (i in tabButtons.indices) {
            styleTab(tabButtons[i], i == currentIndex)
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
