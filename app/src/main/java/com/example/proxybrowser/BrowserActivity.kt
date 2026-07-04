package com.example.proxybrowser

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature

class BrowserActivity : AppCompatActivity() {

    private var proxyServer: LocalProxyServer? = null

    private val webViews = ArrayList<WebView>()
    private val tabButtons = ArrayList<View>()
    private var currentIndex = -1

    private lateinit var rootLayout: LinearLayout
    private lateinit var container: FrameLayout
    private lateinit var tabBar: LinearLayout
    private lateinit var addressBar: EditText

    // --- для полноэкранного видео (YouTube и т.п.) ---
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreenContainer: FrameLayout? = null

    // --- для загрузки файлов с сайтов (кнопка "прикрепить файл") ---
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback
        filePathCallback = null
        if (callback == null) return@registerForActivityResult

        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val uris: Array<Uri>? = when {
                data?.clipData != null -> {
                    val clipData = data.clipData!!
                    Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                else -> null
            }
            callback.onReceiveValue(uris)
        } else {
            callback.onReceiveValue(null)
        }
    }

    private val homeUrl = "https://claude.ai"

    private val pinnedWebViews = mutableMapOf<String, WebView>()

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun iconButtonLayoutParams(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(dp(44), dp(40))
        p.marginEnd = dp(4)
        return p
    }

    private fun siteButtonLayoutParams(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40))
        p.marginEnd = dp(4)
        return p
    }

    // ---------- Хранение списка избранных сайтов ----------

    private fun loadFavorites(): MutableList<Pair<String, String>> {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val raw = prefs.getString("favorites", null)
        if (raw.isNullOrEmpty()) {
            return mutableListOf(
                "Claude" to "https://claude.ai",
                "ChatGPT" to "https://chatgpt.com",
                "YouTube" to "https://www.youtube.com",
                "Google" to "https://www.google.com"
            )
        }
        val result = mutableListOf<Pair<String, String>>()
        for (line in raw.split("\n")) {
            if (line.isBlank()) continue
            val idx = line.indexOf("\t")
            if (idx < 0) continue
            val name = line.substring(0, idx)
            val siteUrl = line.substring(idx + 1)
            result.add(name to siteUrl)
        }
        return result
    }

    private fun saveFavorites(list: List<Pair<String, String>>) {
        val raw = list.joinToString("\n") { it.first + "\t" + it.second }
        getSharedPreferences("settings", MODE_PRIVATE).edit().putString("favorites", raw).apply()
    }

    // Строит HTML-страницу со списком избранного и формой добавления
    private fun buildNewTabHtml(): String {
        val favorites = loadFavorites()
        val sb = StringBuilder()
        sb.append("<html><head><title>Избранное</title>")
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
        sb.append("<style>")
        sb.append("body{font-family:sans-serif;background:#fafafa;padding:16px;margin:0;}")
        sb.append("h2{font-size:20px;color:#333;}")
        sb.append(".item{display:flex;align-items:center;justify-content:space-between;background:#fff;border:1px solid #ccc;border-radius:10px;padding:16px;margin-bottom:10px;}")
        sb.append(".item a.open{flex:1;text-decoration:none;color:#1a0dab;font-size:18px;}")
        sb.append(".item a.del{color:#999;text-decoration:none;font-size:20px;padding-left:12px;}")
        sb.append("form{background:#fff;border:1px solid #ccc;border-radius:10px;padding:16px;margin-top:20px;}")
        sb.append("input{display:block;width:100%;box-sizing:border-box;font-size:16px;padding:12px;margin-bottom:10px;border:1px solid #ccc;border-radius:8px;}")
        sb.append("button{width:100%;font-size:16px;padding:14px;background:#1a73e8;color:#fff;border:none;border-radius:8px;}")
        sb.append("</style></head><body>")
        sb.append("<h2>Избранные сайты</h2>")
        if (favorites.isEmpty()) {
            sb.append("<p>Список пуст. Добавьте сайт ниже.</p>")
        }
        for (i in favorites.indices) {
            val name = favorites[i].first
            val siteUrl = favorites[i].second
            val safeName = android.text.Html.escapeHtml(name)
            val encodedUrl = android.net.Uri.encode(siteUrl)
            val encodedName = android.net.Uri.encode(name)
            sb.append("<div class='item'>")
            sb.append("<a class='open' href='favorites://open?url=" + encodedUrl + "&name=" + encodedName + "'>" + safeName + "</a>")
            sb.append("<a class='del' href='favorites://delete?index=" + i + "'>&#10005;</a>")
            sb.append("</div>")
        }
        sb.append("<form action='favorites://add' method='GET'>")
        sb.append("<input type='text' name='name' placeholder='Название сайта' required>")
        sb.append("<input type='text' name='url' placeholder='Адрес сайта (https://...)' required>")
        sb.append("<button type='submit'>Добавить в избранное</button>")
        sb.append("</form>")
        sb.append("</body></html>")
        return sb.toString()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rootLayout = LinearLayout(this)
        rootLayout.orientation = LinearLayout.VERTICAL

        // Строка 1: кнопки навигации + быстрые кнопки сайтов (объединены в одну строку с прокруткой)
        val navScroll = HorizontalScrollView(this)
        navScroll.isHorizontalScrollBarEnabled = false
        val navBar = LinearLayout(this)
        navBar.orientation = LinearLayout.HORIZONTAL
        navBar.gravity = Gravity.CENTER_VERTICAL
        navBar.setPadding(dp(4), dp(3), dp(4), dp(3))
        navScroll.addView(navBar)

        val backButton = Button(this)
        backButton.text = "◀"
        backButton.textSize = 15f
        backButton.setPadding(0, 0, 0, 0)
        backButton.layoutParams = iconButtonLayoutParams()

        val forwardButton = Button(this)
        forwardButton.text = "▶"
        forwardButton.textSize = 15f
        forwardButton.setPadding(0, 0, 0, 0)
        forwardButton.layoutParams = iconButtonLayoutParams()

        val reloadButton = Button(this)
        reloadButton.text = "⟳"
        reloadButton.textSize = 17f
        reloadButton.setPadding(0, 0, 0, 0)
        reloadButton.layoutParams = iconButtonLayoutParams()

        val newTabButton = Button(this)
        newTabButton.text = "+"
        newTabButton.textSize = 18f
        newTabButton.setPadding(0, 0, 0, 0)
        newTabButton.layoutParams = iconButtonLayoutParams()

        navBar.addView(backButton)
        navBar.addView(forwardButton)
        navBar.addView(reloadButton)
        navBar.addView(newTabButton)

        // Кнопка "Нейросети" — объединяет Claude и ChatGPT, при нажатии показывает выбор
        val aiButton = Button(this)
        aiButton.text = "Нейросети ▾"
        aiButton.textSize = 11f
        aiButton.setAllCaps(false)
        aiButton.setSingleLine(true)
        aiButton.setPadding(dp(10), 0, dp(10), 0)
        aiButton.layoutParams = siteButtonLayoutParams()

        val youTubeButton = Button(this)
        youTubeButton.text = "YouTube"
        youTubeButton.textSize = 11f
        youTubeButton.setAllCaps(false)
        youTubeButton.setSingleLine(true)
        youTubeButton.setPadding(dp(10), 0, dp(10), 0)
        youTubeButton.layoutParams = siteButtonLayoutParams()

        val googleButton = Button(this)
        googleButton.text = "Google"
        googleButton.textSize = 11f
        googleButton.setAllCaps(false)
        googleButton.setSingleLine(true)
        googleButton.setPadding(dp(10), 0, dp(10), 0)
        googleButton.layoutParams = siteButtonLayoutParams()

        navBar.addView(aiButton)
        navBar.addView(youTubeButton)
        navBar.addView(googleButton)

        // Строка 2: адресная строка + кнопка OK
        val addressRow = LinearLayout(this)
        addressRow.orientation = LinearLayout.HORIZONTAL
        addressRow.gravity = Gravity.CENTER_VERTICAL
        addressRow.setPadding(dp(4), dp(3), dp(4), dp(4))

        addressBar = EditText(this)
        addressBar.hint = "Адрес сайта"
        addressBar.setSingleLine(true)
        addressBar.textSize = 15f
        addressBar.setPadding(dp(12), 0, dp(12), 0)
        val addressParams = LinearLayout.LayoutParams(0, dp(46), 1f)
        addressParams.marginEnd = dp(6)
        addressBar.layoutParams = addressParams

        val goButton = Button(this)
        goButton.text = "OK"
        goButton.textSize = 14f
        goButton.minWidth = dp(60)
        goButton.minimumWidth = dp(60)
        goButton.setPadding(dp(6), dp(2), dp(6), dp(2))
        goButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(46))

        addressRow.addView(addressBar)
        addressRow.addView(goButton)

        val tabScroll = HorizontalScrollView(this)
        tabBar = LinearLayout(this)
        tabBar.orientation = LinearLayout.HORIZONTAL
        tabBar.setPadding(dp(4), dp(2), dp(4), dp(2))
        tabScroll.addView(tabBar)

        container = FrameLayout(this)
        container.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)

        rootLayout.addView(navScroll)
        rootLayout.addView(addressRow)
        rootLayout.addView(tabScroll)
        rootLayout.addView(container)
        setContentView(rootLayout)

        goButton.setOnClickListener {
            val url = normalizeUrl(addressBar.text.toString().trim())
            currentWebView()?.loadUrl(url)
        }
        newTabButton.setOnClickListener {
            addTab("", true)
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
            val wv = currentWebView() ?: return@setOnClickListener
            // Принудительная перезагрузка: не берём страницу из кэша, а качаем заново с сайта
            wv.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            wv.reload()
            wv.settings.cacheMode = WebSettings.LOAD_DEFAULT
        }
        aiButton.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menu.add("Claude")
            popup.menu.add("ChatGPT")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Claude" -> openPinned("Claude", "https://claude.ai")
                    "ChatGPT" -> openPinned("ChatGPT", "https://chatgpt.com")
                }
                true
            }
            popup.show()
        }
        youTubeButton.setOnClickListener {
            openPinned("YouTube", "https://www.youtube.com")
        }
        googleButton.setOnClickListener {
            openPinned("Google", "https://www.google.com")
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
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (view === currentWebView()) {
                    if (view?.tag == "newtab") {
                        addressBar.setText("")
                    } else {
                        addressBar.setText(url ?: "")
                    }
                }
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                updateTabTitles()
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                val webView = view ?: return false

                if (url.startsWith("favorites://open")) {
                    val uri = android.net.Uri.parse(url)
                    val target = uri.getQueryParameter("url")
                    if (!target.isNullOrEmpty()) {
                        webView.tag = null
                        webView.loadUrl(target)
                    }
                    return true
                }
                if (url.startsWith("favorites://add")) {
                    val uri = android.net.Uri.parse(url)
                    val name = uri.getQueryParameter("name")?.trim()
                    val siteUrl = uri.getQueryParameter("url")?.trim()
                    if (!name.isNullOrEmpty() && !siteUrl.isNullOrEmpty()) {
                        val normalized = if (siteUrl.startsWith("http://") || siteUrl.startsWith("https://")) siteUrl else "https://" + siteUrl
                        val favorites = loadFavorites()
                        favorites.add(name to normalized)
                        saveFavorites(favorites)
                    }
                    webView.loadDataWithBaseURL(null, buildNewTabHtml(), "text/html", "UTF-8", null)
                    return true
                }
                if (url.startsWith("favorites://delete")) {
                    val uri = android.net.Uri.parse(url)
                    val index = uri.getQueryParameter("index")?.toIntOrNull()
                    if (index != null) {
                        val favorites = loadFavorites()
                        if (index in favorites.indices) {
                            favorites.removeAt(index)
                            saveFavorites(favorites)
                        }
                    }
                    webView.loadDataWithBaseURL(null, buildNewTabHtml(), "text/html", "UTF-8", null)
                    return true
                }
                return false
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                updateTabTitles()
            }

            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@BrowserActivity.filePathCallback?.onReceiveValue(null)
                this@BrowserActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT)
                intent.type = intent.type ?: "*/*"
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }

                return try {
                    fileChooserLauncher.launch(Intent.createChooser(intent, "Выберите файл"))
                    true
                } catch (e: Exception) {
                    this@BrowserActivity.filePathCallback = null
                    Toast.makeText(this@BrowserActivity, "Не удалось открыть выбор файла", Toast.LENGTH_SHORT).show()
                    false
                }
            }

            // --- Полноэкранное видео (кнопка "развернуть на весь экран" на YouTube и др.) ---
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null || callback == null) return

                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }

                customView = view
                customViewCallback = callback

                val decor = window.decorView as FrameLayout
                val fsContainer = FrameLayout(this@BrowserActivity)
                fsContainer.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                fsContainer.setBackgroundColor(0xFF000000.toInt())
                fsContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                decor.addView(fsContainer)
                fullscreenContainer = fsContainer

                rootLayout.visibility = View.GONE

                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                supportActionBar?.hide()
            }

            override fun onHideCustomView() {
                exitFullscreenVideo()
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }
        }
        return wv
    }

    private fun exitFullscreenVideo() {
        if (customView == null) return
        val decor = window.decorView as FrameLayout
        fullscreenContainer?.let { decor.removeView(it) }
        fullscreenContainer = null
        customView = null

        rootLayout.visibility = View.VISIBLE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        supportActionBar?.show()
    }

    private fun addTab(url: String, isNewTabPage: Boolean = false) {
        val wv = createWebView()
        webViews.add(wv)

        val tabView = LinearLayout(this)
        tabView.orientation = LinearLayout.HORIZONTAL
        tabView.setPadding(dp(10), dp(5), dp(10), dp(5))
        val tabParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tabParams.marginEnd = dp(4)
        tabParams.topMargin = dp(2)
        tabParams.bottomMargin = dp(2)
        tabView.layoutParams = tabParams

        val titleView = TextView(this)
        titleView.text = "Новая вкладка"
        titleView.maxWidth = 300
        titleView.textSize = 13f
        titleView.setSingleLine(true)

        val closeView = TextView(this)
        closeView.text = "  ✕"
        closeView.textSize = 13f

        tabView.addView(titleView)
        tabView.addView(closeView)
        tabBar.addView(tabView)
        tabButtons.add(tabView)

        tabView.setOnClickListener { selectTab(webViews.indexOf(wv)) }
        closeView.setOnClickListener { closeTab(webViews.indexOf(wv)) }

        selectTab(webViews.size - 1)

        if (isNewTabPage) {
            wv.tag = "newtab"
            wv.loadDataWithBaseURL(null, buildNewTabHtml(), "text/html", "UTF-8", null)
        } else {
            wv.loadUrl(url)
        }
    }

    private fun selectTab(index: Int) {
        if (index < 0 || index >= webViews.size) return
        currentIndex = index
        container.removeAllViews()
        val wv = webViews[index]
        container.addView(wv, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addressBar.setText(if (wv.tag == "newtab") "" else (wv.url ?: ""))
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
        if (customView != null) {
            customViewCallback?.onCustomViewHidden()
            exitFullscreenVideo()
            customViewCallback = null
            return
        }
        val wv = currentWebView()
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
