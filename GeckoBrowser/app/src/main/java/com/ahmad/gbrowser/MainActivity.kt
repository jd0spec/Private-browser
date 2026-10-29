package com.ahmad.gbrowser

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.URLUtil
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private var idCounter = 0

class MainActivity : ComponentActivity() {

    companion object {
        var runtime: GeckoRuntime? = null
        const val HOME = ""
        const val REQ_XPI = 11
        const val REQ_FILE = 12
    }

    class Tab(var url: String, var title: String = "") {
        var session: GeckoSession? = null
        var canBack = false
        var canForward = false
        var home = url.isEmpty()
        var thumb: Bitmap? = null
        val id = ++idCounter
    }

    private lateinit var store: Store
    private lateinit var rt: GeckoRuntime
    private lateinit var geckoView: GeckoView
    private val ch = Chrome()
    private lateinit var homeView: ComposeView
    private lateinit var overlayView: ComposeView
    private lateinit var topBar: View
    private lateinit var bottomBar: View
    private lateinit var videoOverlayView: ComposeView

    private val tabs = ArrayList<Tab>()
    private var current = -1
    private var fullscreen = false
    private var skipSave = false
    private var lastBack = 0L
    private val ui = Handler(Looper.getMainLooper())
    private val prompts by lazy { Prompts(this) }

    private lateinit var popup: ExtPopupState
    private lateinit var popupView: ComposeView
    private var popupExtId: String? = null
    private val actions = HashMap<String, WebExtension.Action>()
    private var popupAsked = false

    // Native Video Player State
    private var showNativePlayer by mutableStateOf(false)
    private var isVideoPlaying by mutableStateOf(false)
    private var videoCurrentTime by mutableStateOf(0f)
    private var videoDuration by mutableStateOf(0f)
    private var videoUrl by mutableStateOf("")
    private var videoPort: WebExtension.Port? = null

    private val dataLabels = arrayOf("History", "Cookies & site data", "Open tabs", "Cache")
    private val autoKeys = arrayOf("ac_history", "ac_cookies", "ac_tabs", "ac_cache")

    private val recommended = listOf(
        "uBlock Origin (ad blocker)" to "ublock-origin",
        "Dark Reader (dark mode for sites)" to "darkreader",
        "Privacy Badger (tracker blocker)" to "privacy-badger17",
        "I still don't care about cookies" to "istilldontcareaboutcookies",
        "SponsorBlock (skip YouTube sponsors)" to "sponsorblock"
    )

    private val okSchemes = setOf(
        "http", "https", "about", "file", "data", "blob", "javascript",
        "moz-extension", "resource", "view-source", "ws", "wss", "chrome"
    )
    override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    store = Store(this)
    popup = ExtPopupState(store.prefs)
    Dl.init(this)
    if (android.os.Build.VERSION.SDK_INT >= 33 &&
        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 77)
    }

    val freshProcess = runtime == null
    if (freshProcess) {
        runtime = GeckoRuntime.create(applicationContext, GeckoRuntimeSettings.Builder().build())
    }
    rt = runtime!!
    rt.webExtensionController.setPromptDelegate(addonPrompt)

    rt.webExtensionController.ensureBuiltIn("resource://android/assets/video_player/", "video_player@ahmad.gbrowser").accept({
    toast("Extension Installed Successfully!")
}, { error ->
    // Let's print the exact error to the screen
    toast("Extension Failed: ${error?.localizedMessage ?: error.toString()}")
})

    buildUi()

    val auto = BooleanArray(4) { store.flag(autoKeys[it]) }
    if (freshProcess && auto.any { it } && store.prefs.getBoolean("dirty", false)) {
        clearNow(auto, false) {}
    }
    store.prefs.edit().putBoolean("dirty", true).apply()

    restoreTabs()
    handleIntent(intent)
    refreshAddonInfo()
}

override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

private fun handleIntent(i: Intent?) {
    if (i?.action == Intent.ACTION_VIEW) {
        val u = i.dataString
        if (!u.isNullOrEmpty()) newTab(u)
    }
}

override fun onStop() { super.onStop(); if (!skipSave) saveTabs() }

override fun onDestroy() {
    try { closePopup() } catch (e: Exception) {}
    try { geckoView.releaseSession() } catch (e: Exception) {}
    for (t in tabs) {
        try { t.session?.let { if (it.isOpen) it.close() } } catch (e: Exception) {}
        t.session = null
    }
    super.onDestroy()
}

@Deprecated("Deprecated in Java")
@Suppress("DEPRECATION")
override fun onBackPressed() {
    if (showNativePlayer) { 
        showNativePlayer = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        setFullscreen(false)
        videoOverlayView.visibility = View.GONE
        return 
    }
    if (popup.session != null) { closePopup(); return }
    if (ch.editing) { ch.editing = false; hideKeyboard(); return }
    if (ch.screen != 0) { showScreen(0); return }
    if (fullscreen) { curTab()?.session?.exitFullScreen(); return }
    val t = curTab()
    if (t != null && t.home && t.session != null) { t.home = false; selectTab(current); return }
    if (t != null && !t.home && t.canBack) { t.session?.goBack(true); return }
    if (tabs.size > 1) { closeTab(current); return }
    val now = SystemClock.uptimeMillis()
    if (now - lastBack < 2000) exitApp() else { lastBack = now; toast("Press back again to exit") }
}

private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
private fun toast(m: String) { runOnUiThread { Toast.makeText(this, m, Toast.LENGTH_LONG).show() } }

private fun buildUi() {
    val match = ViewGroup.LayoutParams.MATCH_PARENT
    val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
    val root = LinearLayout(this)
    root.orientation = LinearLayout.VERTICAL
    topBar = View(this)

    geckoView = GeckoView(this)
    homeView = ComposeView(this)
    homeView.setContent { AppTheme { if (ch.home) HomeScreen(store, onGo = { go(it) }) } }
    val frame = FrameLayout(this)
    frame.addView(geckoView, FrameLayout.LayoutParams(match, match))
    frame.addView(homeView, FrameLayout.LayoutParams(match, match))
    root.addView(frame, LinearLayout.LayoutParams(match, 0, 1f))

    val bottom = ComposeView(this)
    bottom.setContent {
        AppTheme {
            BottomBar(ch, onGo = { go(it) }, onHome = { goHome() }, onTabs = { showTabs() }, onMenu = { showMenu() }, onSwipeLeft = { swipeNext() }, onSwipeRight = { swipePrev() })
            if (ch.menu) {
                MenuSheet(ch, onBack = { curTab()?.session?.goBack(true) }, onForward = { curTab()?.session?.goForward(true) }, onHome = { goHome() }, onReload = { curTab()?.session?.reload(GeckoSession.LOAD_FLAGS_NONE) }, onAction = { menuAction(it) }, onAddonSettings = { u -> if (u.isNullOrEmpty()) toast("This extension has no settings page") else newTab(u) }, onAddonClick = { addonClick(it) })
            }
            ch.ctx?.let { ContextSheet(ch, it) }
        }
    }
    bottomBar = bottom
    root.addView(bottom, LinearLayout.LayoutParams(match, wrap))

    overlayView = ComposeView(this)
    overlayView.visibility = View.GONE
    overlayView.setContent {
        AppTheme {
            when (ch.screen) {
                1 -> TabsScreen(ch, onClose = { showScreen(0) }, onSelect = { i -> selectTab(i); showScreen(0) }, onCloseTab = { i -> closeTabUndoable(i) }, onNew = { newTab(HOME); showScreen(0) }, onCloseAll = { finalizeUndo(); closeAllTabs(); newTab(HOME); showScreen(0) }, onUndo = { undoClose() }, onUndoExpire = { finalizeUndo() })
                2 -> HistoryScreen(ch, store, onClose = { showScreen(0) }, onOpen = { u -> showScreen(0); go(u) }, onClear = { store.clearHistory(); ch.historyVersion++ })
                3 -> SettingsScreen(ch, store.engineNames.toList(), onClose = { showScreen(0) }, onEngine = { i -> store.setEngine(i); ch.engineIndex = i; ch.engineName = store.engineNames[i] }, onAuto = { i, v -> store.setFlag(autoKeys[i], v); ch.autoFlags[i] = v }, onClearNow = { st -> clearNow(st, false) { toast("Cleared"); ch.historyVersion++ } })
                4 -> AddonsScreen(ch, recommended, onClose = { showScreen(0) }, onToggle = { i, on -> addonToggle(i, on) }, onRemove = { i -> addonRemove(i) }, onOpenSettings = { u -> if (u.isNullOrEmpty()) toast("This extension has no settings page") else { showScreen(0); newTab(u) } }, onInstall = { installFromInput(it) }, onPickFile = { pickXpi() }, onOpen = { addonClick(it) })
                5 -> DownloadsScreen(onClose = { showScreen(0) }, onAdd = { addDownloadFromInput(it) }, onOpenFile = { openDownloaded(it) }, onShareFile = { shareDownloaded(it) })
                else -> {}
            }
        }
    }
    
    popupView = ComposeView(this)
    popupView.visibility = View.GONE
    popupView.setContent { AppTheme { ExtensionPopupWindow(popup, onClose = { closePopup() }) } }
    
    videoOverlayView = ComposeView(this)
    videoOverlayView.visibility = View.GONE
    videoOverlayView.setContent {
        AppTheme {
            if (showNativePlayer) {
                NativeVideoOverlay(
                    isPlaying = isVideoPlaying, currentTime = videoCurrentTime, duration = videoDuration,
                    onPlayPause = { videoPort?.postMessage(JSONObject().put("action", if (isVideoPlaying) "pause" else "play")) },
                    onSeek = { time -> videoPort?.postMessage(JSONObject().put("action", "seek").put("time", time)) },
                    onDownload = { if (videoUrl.isNotEmpty()) { Dl.add(URLUtil.guessFileName(videoUrl, null, "video/mp4"), videoUrl, curTab()?.url ?: ""); toast("Download started") } },
                    onClose = { 
                        showNativePlayer = false
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        setFullscreen(false)
                        videoOverlayView.visibility = View.GONE
                        videoPort?.postMessage(JSONObject().put("action", "pause"))
                    }
                )
            }
        }
    }

    val outer = FrameLayout(this)
    outer.addView(root, FrameLayout.LayoutParams(match, match))
    outer.addView(overlayView, FrameLayout.LayoutParams(match, match))
    outer.addView(popupView, FrameLayout.LayoutParams(match, match))
    outer.addView(videoOverlayView, FrameLayout.LayoutParams(match, match))
    
    val promptView = ComposeView(this)
    promptView.setContent { AppTheme { DownloadPromptHost(onCopy = { copyText(it) }, onStarted = { toast("Downloading $it") }) } }
    outer.addView(promptView, FrameLayout.LayoutParams(1, 1))
    setContentView(outer)
}
private fun updateButtons() {
    val t = curTab()
    ch.tabCount = if (current in tabs.indices) current + 1 else tabs.size
    ch.canBack = t != null && !t.home && t.canBack
    ch.canForward = t != null && !t.home && t.canForward
    syncTabs()
}

private fun swipeNext() {
    if (current in 0 until tabs.size - 1) {
        ch.slideDir = 1
        window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        selectTab(current + 1)
    } else {
        ch.slideDir = 1
        window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        newTab(HOME)
    }
}

private fun swipePrev() {
    if (current > 0) {
        ch.slideDir = -1
        window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        selectTab(current - 1)
    }
}

private fun setHome(v: Boolean) {
    ch.home = v
    homeView.visibility = if (v) View.VISIBLE else View.GONE
}

private fun goHome() {
    val t = curTab()
    if (t == null) { newTab(HOME); return }
    t.home = true
    t.session?.let { if (it.isOpen) deactivate(it) }
    geckoView.releaseSession()
    setHome(true)
    ch.url = ""
    ch.loading = 0
    updateButtons()
}

private fun hideKeyboard() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(geckoView.windowToken, 0)
}

private fun setFullscreen(on: Boolean) {
    runOnUiThread {
        fullscreen = on
        topBar.visibility = if (on) View.GONE else View.VISIBLE
        bottomBar.visibility = if (on) View.GONE else View.VISIBLE
        val c = window.insetsController
        if (c != null) {
            if (on) {
                c.hide(WindowInsets.Type.systemBars())
                c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                c.show(WindowInsets.Type.systemBars())
            }
        }
    }
}

private fun curTab(): Tab? = tabs.getOrNull(current)

private fun toUrl(input: String): String {
    val t = input.trim()
    if (t.isEmpty()) return HOME
    val lower = t.lowercase()
    if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("about:") || lower.startsWith("file:") || lower.startsWith("moz-extension:")) return t
    if (lower == "localhost" || lower.startsWith("localhost:")) return "http://$t"
    if (!t.contains(' ') && t.contains('.')) return "https://$t"
    return store.searchUrl() + Uri.encode(t)
}

private fun go(text: String) {
    if (text.isBlank()) return
    val url = toUrl(text)
    val t = curTab()
    if (t == null) {
        newTab(url)
    } else {
        val had = t.session != null
        t.url = url
        t.home = false
        attachSession(t)
        if (had) t.session?.loadUri(url)
        setHome(false)
        ch.url = url
        updateButtons()
    }
    hideKeyboard()
    geckoView.requestFocus()
}

private fun newTab(url: String, select: Boolean = true): Tab {
    val t = Tab(url)
    tabs.add(t)
    if (select) selectTab(tabs.size - 1) else updateButtons()
    return t
}

private fun ensureSession(t: Tab): GeckoSession {
    t.session?.let { return it }
    val s = GeckoSession()
    val d = TabDelegates(t)
    s.contentDelegate = d
    s.progressDelegate = d
    s.navigationDelegate = d
    s.promptDelegate = prompts
    s.open(rt)
    t.session = s
    if (t.url.isNotEmpty()) s.loadUri(t.url)
    return s
}

private fun attachSession(t: Tab) {
    val s = ensureSession(t)
    if (geckoView.session !== s) {
        geckoView.releaseSession()
        geckoView.setSession(s)
    }
    s.setActive(true)
    try { rt.webExtensionController.setTabActive(s, true) } catch (e: Exception) {}
}

private fun deactivate(s: GeckoSession) {
    s.setActive(false)
    try { rt.webExtensionController.setTabActive(s, false) } catch (e: Exception) {}
}

private fun selectTab(i: Int) {
    if (i !in tabs.indices) return
    tabs.getOrNull(current)?.session?.let { if (it.isOpen) deactivate(it) }
    current = i
    val t = tabs[i]
    geckoView.releaseSession()
    if (!t.home) attachSession(t)
    setHome(t.home)
    ch.url = t.url
    ch.tabKey++
    ch.loading = 0
    updateButtons()
}
private fun closeTab(i: Int) { removeTab(i, true) }

private var undoTab: Tab? = null
private var undoIndex = 0
private var undoWasCurrent = false
private var undoOnly = false

private fun closeTabUndoable(i: Int) {
    if (i !in tabs.indices) return
    finalizeUndo()
    val t = tabs[i]
    undoTab = t
    undoIndex = i
    undoWasCurrent = (i == current)
    undoOnly = tabs.size == 1
    ch.undoTitle = if (t.home) "New tab" else t.title.ifEmpty { displayUrl(t.url) }
    ch.undoKey++
    removeTab(i, false)
}

private fun undoClose() {
    val t = undoTab ?: return
    undoTab = null
    ch.undoTitle = null
    if (undoOnly && tabs.size == 1) {
        val blank = tabs[0]
        if (blank.home && blank.session == null) { tabs.clear(); current = -1 }
    }
    val idx = minOf(undoIndex, tabs.size)
    tabs.add(idx, t)
    if (current >= idx) current++
    if (undoWasCurrent || current < 0) selectTab(idx) else updateButtons()
}

private fun finalizeUndo() {
    val t = undoTab ?: return
    undoTab = null
    t.session?.let { if (it.isOpen) it.close() }
    t.session = null
}

private fun captureThumb(t: Tab) {
    try {
        val m = geckoView.javaClass.getMethod("capturePixels")
        @Suppress("UNCHECKED_CAST")
        val res = m.invoke(geckoView) as GeckoResult<Bitmap>
        res.accept({ bmp ->
            if (bmp != null && bmp.width > 0) {
                val w = 360
                val h = (bmp.height * (w.toFloat() / bmp.width)).toInt().coerceAtLeast(1)
                t.thumb = Bitmap.createScaledBitmap(bmp, w, h, true)
                runOnUiThread { syncTabs() }
            }
        }, { })
    } catch (e: Exception) {}
}

private fun removeTab(i: Int, destroy: Boolean) {
    if (i !in tabs.indices) return
    val t = tabs[i]
    val wasCurrent = (i == current)
    if (wasCurrent) geckoView.releaseSession()
    if (destroy) {
        t.session?.let { if (it.isOpen) it.close() }
        t.session = null
    } else {
        t.session?.let { if (it.isOpen) deactivate(it) }
    }
    tabs.removeAt(i)
    if (tabs.isEmpty()) { current = -1; newTab(HOME); return }
    if (wasCurrent) { current = -1; selectTab(minOf(i, tabs.size - 1)) } else { if (i < current) current--; updateButtons() }
}

private fun closeAllTabs() {
    geckoView.releaseSession()
    for (t in tabs) { t.session?.let { if (it.isOpen) it.close() }; t.session = null }
    tabs.clear(); current = -1
}

private fun saveTabs() { store.saveTabs(tabs.map { Pair(it.url, it.title) }, current) }

private fun restoreTabs() {
    val (saved, cur) = store.loadTabs()
    if (saved.isEmpty()) { newTab(HOME); return }
    for ((u, t) in saved) tabs.add(Tab(u, t))
    selectTab(cur.coerceIn(0, tabs.size - 1))
}

private fun recover(t: Tab) {
    runOnUiThread {
        val wasCurrent = tabs.indexOf(t) == current
        try { t.session?.let { if (it.isOpen) it.close() } } catch (e: Exception) {}
        t.session = null
        if (wasCurrent) selectTab(current)
    }
}

private fun showTabs() {
    val cur = curTab()
    if (cur != null && !cur.home) captureThumb(cur)
    syncTabs()
    showScreen(1)
}

private fun showScreen(n: Int) {
    if (n != 1) { finalizeUndo(); ch.undoTitle = null }
    ch.screen = n
    overlayView.visibility = if (n == 0) View.GONE else View.VISIBLE
    if (n == 2) ch.historyVersion++
    if (n == 3) { for (k in 0 until 4) ch.autoFlags[k] = store.flag(autoKeys[k]); ch.engineIndex = store.engineIndex() }
}

private fun syncTabs() {
    ch.tabList.clear()
    for (t in tabs) ch.tabList.add(TabInfo(t.id, t.title, t.url, t.home, t.thumb))
    ch.tabIndex = current
}

inner class TabDelegates(private val tab: Tab) : GeckoSession.ContentDelegate, GeckoSession.ProgressDelegate, GeckoSession.NavigationDelegate {
    private fun isCurrent() = curTab() === tab

    override fun onTitleChange(session: GeckoSession, title: String?) {
        tab.title = title ?: ""
        if (ch.screen == 1) syncTabs()
    }

    override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) { setFullscreen(fullScreen) }
    override fun onExternalResponse(session: GeckoSession, response: WebResponse) { handleDownload(response) }
    override fun onCrash(session: GeckoSession) { recover(tab) }
    override fun onKill(session: GeckoSession) { recover(tab) }

    override fun onCloseRequest(session: GeckoSession) {
        runOnUiThread { val i = tabs.indexOf(tab); if (i >= 0) closeTab(i) }
    }

    override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: GeckoSession.ContentDelegate.ContextElement) {
        val link = element.linkUri
        val img = element.srcUri
        val list = ArrayList<CtxAction>()
        if (link != null) {
            list.add(CtxAction("+", "Open link in new tab") { newTab(link) })
            list.add(CtxAction("\uD83D\uDD17", "Copy link") { copyText(link) })
            list.add(CtxAction("Aa", "Copy text") { copyLinkText(element) })
        }
        if (img != null) { list.add(CtxAction("\u25A3", "Open image/media in new tab") { newTab(img) }) }
        if (list.isEmpty()) return
        runOnUiThread { ch.ctxTitle = displayUrl(link ?: img ?: ""); ch.ctx = list }
    }

    override fun onPageStart(session: GeckoSession, url: String) { if (isCurrent()) ch.loading = 5 }
    override fun onProgressChange(session: GeckoSession, p: Int) { if (isCurrent()) ch.loading = if (p in 1..99) p else 0 }

    override fun onPageStop(session: GeckoSession, success: Boolean) {
        if (isCurrent()) ch.loading = 0
        if (success && tab.url.startsWith("http")) store.addVisit(tab.url, tab.title)
        if (success && isCurrent()) { ui.postDelayed({ if (curTab() === tab && !tab.home && ch.screen == 0) captureThumb(tab) }, 900) }
    }

    override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
        tab.url = url ?: ""
        if (isCurrent() && !tab.home) ch.url = tab.url
    }

    override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { tab.canBack = canGoBack; if (isCurrent()) updateButtons() }
    override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) { tab.canForward = canGoForward; if (isCurrent()) updateButtons() }

    override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
        val u = request.uri
        val scheme = u.substringBefore(':', "").lowercase()
        if (scheme.isEmpty() || scheme in okSchemes) return null
        openExternal(u)
        return GeckoResult.fromValue(AllowOrDeny.DENY)
    }

    override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
        runOnUiThread { newTab(uri) }
        return null
    }
}
    private val extMessageDelegate = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            if (port.name == "browser") {
                videoPort = port
                toast("JS Bridge Connected!")
                port.setDelegate(object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, port: WebExtension.Port) {
                        if (message is JSONObject) {
                            runOnUiThread {
                                val type = message.optString("type")
                                when (type) {
                                    "PING" -> {
                                        toast("JS to Kotlin bridge is working!")
                                    }
                                    "OPEN_NATIVE_PLAYER" -> {
                                        videoUrl = message.optString("url")
                                        videoDuration = message.optDouble("duration", 0.0).toFloat()
                                        videoCurrentTime = message.optDouble("currentTime", 0.0).toFloat()
                                        val w = message.optInt("width", 0)
                                        val h = message.optInt("height", 0)
                                        if (w > 0 && h > 0) {
                                            requestedOrientation = if (w > h) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                        } else {
                                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                        }
                                        showNativePlayer = true
                                        isVideoPlaying = true
                                        setFullscreen(true)
                                        videoOverlayView.visibility = View.VISIBLE
                                    }
                                    "DOWNLOAD_VIDEO" -> {
                                        val url = message.optString("url")
                                        if (url.isNotEmpty()) {
                                            val name = URLUtil.guessFileName(url, null, "video/mp4")
                                            Dl.add(name, url, curTab()?.url ?: "")
                                            toast("Download started: $name")
                                        }
                                    }
                                }
                            }
                        }
                    }
                })
            }
        }
    }

    private val actionDelegate = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            if (session == null || !actions.containsKey(extension.id)) actions[extension.id] = action
        }
        override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? = openPopupFor(extension, true)
        override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? = openPopupFor(extension, false)
    }

    private val extTabDelegate = object : WebExtension.TabDelegate {
        override fun onNewTab(source: WebExtension, createDetails: WebExtension.CreateTabDetails): GeckoResult<GeckoSession>? {
            val u = createDetails.url
            if (!u.isNullOrEmpty()) { closePopup(); newTab(u) }
            return null
        }
        override fun onOpenOptionsPage(source: WebExtension) {
            val u = source.metaData?.optionsPageUrl
            if (!u.isNullOrEmpty()) { closePopup(); newTab(u) }
        }
    }

    private inner class PopupDelegates : GeckoSession.ContentDelegate {
        override fun onCloseRequest(session: GeckoSession) { runOnUiThread { if (popup.session === session) closePopup() } }
        override fun onExternalResponse(session: GeckoSession, response: WebResponse) { handleDownload(response) }
    }

    private fun openPopupFor(ext: WebExtension, toggle: Boolean): GeckoResult<GeckoSession>? {
        popupAsked = true
        if (toggle && popup.session != null && popupExtId == ext.id) { closePopup(); return null }
        closePopup()
        val s = GeckoSession()
        s.contentDelegate = PopupDelegates()
        s.promptDelegate = prompts
        popupExtId = ext.id
        popup.title = ext.metaData?.name ?: ""
        popup.session = s
        popupView.visibility = View.VISIBLE
        ui.postDelayed({
            if (popup.session === s && !s.isOpen) { try { s.open(rt) } catch (e: Exception) {} }
        }, 250)
        return GeckoResult.fromValue(s)
    }

    private fun closePopup() {
        val s = popup.session
        popup.session = null; popupExtId = null
        try { popup.gecko?.releaseSession() } catch (e: Exception) {}
        popup.gecko = null
        try { s?.let { if (it.isOpen) it.close() } } catch (e: Exception) {}
        popupView.visibility = View.GONE
    }

    private fun openExternal(u: String) {
        try {
            val i = if (u.startsWith("intent:")) Intent.parseUri(u, Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, Uri.parse(u))
            i.addCategory(Intent.CATEGORY_BROWSABLE); i.component = null; i.selector = null; startActivity(i)
        } catch (e: Exception) { toast("No app can open this link") }
    }

    private fun copyLinkText(element: GeckoSession.ContentDelegate.ContextElement) {
        var text: String? = null
        for (name in listOf("textContent", "title", "altText")) {
            try {
                val v = element.javaClass.getField(name).get(element) as? String
                if (!v.isNullOrBlank()) { text = v.trim(); break }
            } catch (e: Exception) {}
        }
        if (text == null) toast("No text found for this link") else copyText(text)
    }

    private fun copyText(s: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("link", s))
        toast("Copied")
    }

    private fun addDownloadFromInput(input: String) {
        val t = input.trim()
        val url = if (t.contains("://")) t else "https://$t"
        if (!url.startsWith("http")) { toast("Enter a web link"); return }
        Dl.add(URLUtil.guessFileName(url, null, null), url, "")
        toast("Download added")
    }

    private fun mimeOf(name: String): String = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "*/*"

    private fun openDownloaded(d: DlItem) {
        try {
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(Uri.parse(d.savedUri), mimeOf(d.name))
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(i)
        } catch (e: Exception) { toast("No app found to open this file") }
    }

    private fun shareDownloaded(d: DlItem) {
        try {
            val i = Intent(Intent.ACTION_SEND)
            i.type = mimeOf(d.name)
            i.putExtra(Intent.EXTRA_STREAM, Uri.parse(d.savedUri))
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(i, "Share file"))
        } catch (e: Exception) { toast("Could not share this file") }
    }

    private fun handleDownload(r: WebResponse) {
        val uri = r.uri
        if (uri.contains(".xpi")) { runOnUiThread { installAddon(uri) }; return }
        if (!uri.startsWith("http")) { toast("This kind of download is not supported yet"); return }
        val ct = r.headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value
        val cd = r.headers.entries.firstOrNull { it.key.equals("content-disposition", true) }?.value
        val name = URLUtil.guessFileName(uri, cd, ct)
        runOnUiThread {
            val exists = Dl.items.any { it.state == DlState.DONE && (it.url == uri || it.name == name) }
            DlPrompt.pending = PendingDl(name, uri, curTab()?.url ?: "", exists)
        }
    }

    private val addonExts = ArrayList<WebExtension>()

    private fun loadAddonIcon(idx: Int, e: WebExtension) {
        try {
            val m = e.metaData ?: return
            val icon = m.javaClass.getField("icon").get(m) ?: return
            val method = icon.javaClass.getMethod("getBitmap", Int::class.javaPrimitiveType)
            @Suppress("UNCHECKED_CAST")
            val res = method.invoke(icon, 96) as GeckoResult<Bitmap>
            res.accept({ bmp ->
                if (bmp != null) {
                    runOnUiThread {
                        if (idx in addonExts.indices && addonExts[idx] === e && idx in ch.addons.indices) {
                            val old = ch.addons[idx]
                            ch.addons[idx] = AddonInfo(old.name, old.optionsUrl, old.version, old.enabled, bmp)
                        }
                    }
                }
            }, { })
        } catch (ex: Exception) {}
    }

    private fun refreshAddonInfo() {
        rt.webExtensionController.list().accept({ list ->
            runOnUiThread {
                addonExts.clear(); ch.addons.clear()
                if (list != null) {
                    for (e in list) {
                        addonExts.add(e)
                        e.setActionDelegate(actionDelegate)
                        e.tabDelegate = extTabDelegate
                        if (e.id == "video_player@ahmad.gbrowser") {
                            e.setMessageDelegate(extMessageDelegate, "browser")
                        }
                        val m = e.metaData
                        ch.addons.add(AddonInfo(m?.name ?: e.id, m?.optionsPageUrl, m?.version ?: "", m?.enabled ?: true))
                        loadAddonIcon(addonExts.size - 1, e)
                    }
                }
            }
        }, { })
    }

    private fun addonClick(i: Int) {
        val e = addonExts.getOrNull(i) ?: return
        val m = e.metaData
        if (m?.enabled == false) { toast("Turn this extension on first"); return }
        val a = actions[e.id]
        if (a != null) {
            popupAsked = false
            a.click()
            ui.postDelayed({
                if (!popupAsked && popup.session == null) toast(if (curTab()?.home == true) "Open a website first" else "This extension did not open a popup")
            }, 1500)
            return
        }
        val opts = m?.optionsPageUrl
        if (!opts.isNullOrEmpty()) newTab(opts) else toast("This extension has no popup")
    }

    private fun addonToggle(i: Int, on: Boolean) {
        val e = addonExts.getOrNull(i) ?: return
        val ctl = rt.webExtensionController
        val r = if (on) ctl.enable(e, WebExtensionController.EnableSource.USER) else ctl.disable(e, WebExtensionController.EnableSource.USER)
        r.accept({ refreshAddonInfo() }, { toast("Could not change the add-on") })
    }

    private fun addonRemove(i: Int) {
        val e = addonExts.getOrNull(i) ?: return
        rt.webExtensionController.uninstall(e).accept({ toast("Removed"); refreshAddonInfo() }, { toast("Could not remove it") })
    }

    private fun showMenu() {
        ch.engineName = store.engineNames[store.engineIndex()]
        ch.menu = true
        refreshAddonInfo()
    }

    private fun sharePage() {
        val t = curTab()
        if (t == null || t.home || t.url.isEmpty()) { toast("Nothing to share yet"); return }
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"; i.putExtra(Intent.EXTRA_TEXT, t.url)
        startActivity(Intent.createChooser(i, "Share link"))
    }

    private fun menuAction(id: Int) {
        when (id) {
            0 -> newTab(HOME)
            1 -> showScreen(2)
            2 -> showAddons()
            6 -> showScreen(5)
            7 -> exitApp()
            8 -> sharePage()
            9 -> showScreen(3)
            else -> {}
        }
    }

    private fun chooseTypes(title: String, initial: BooleanArray, okLabel: String, onOk: (BooleanArray) -> Unit) {
        val state = initial.copyOf()
        AlertDialog.Builder(this).setTitle(title).setMultiChoiceItems(dataLabels, state) { _, i, on -> state[i] = on }
            .setPositiveButton(okLabel) { _, _ -> onOk(state) }.setNegativeButton("Cancel", null).show()
    }

    private fun clearNow(st: BooleanArray, forExit: Boolean, done: () -> Unit) {
        if (st[0]) store.clearHistory()
        if (st[2]) { store.clearTabs(); if (forExit) skipSave = true else { closeAllTabs(); newTab(HOME) } }
        var flags = 0L
        if (st[1]) flags = flags or StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.DOM_STORAGES
        if (st[3]) flags = flags or StorageController.ClearFlags.NETWORK_CACHE or StorageController.ClearFlags.IMAGE_CACHE
        if (flags != 0L) rt.storageController.clearData(flags).accept({ done() }, { done() }) else done()
    }

    private fun exitApp() {
        val st = BooleanArray(4) { store.flag(autoKeys[it]) }
        if (!st[2]) saveTabs()
        var finished = false
        val finish = { ok: Boolean ->
            if (!finished) {
                finished = true
                if (ok) store.prefs.edit().putBoolean("dirty", false).commit()
                finishAndRemoveTask(); ui.postDelayed({ Process.killProcess(Process.myPid()) }, 300)
            }
        }
        if (st.any { it }) { clearNow(st, true) { finish(true) }; ui.postDelayed({ finish(false) }, 2500) }
        else { store.prefs.edit().putBoolean("dirty", false).commit(); finish(true) }
    }

    private val addonPrompt = object : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(extension: WebExtension, permissions: Array<String>, origins: Array<String>, dataCollectionPermissions: Array<String>): GeckoResult<WebExtension.PermissionPromptResponse>? {
            val res = GeckoResult<WebExtension.PermissionPromptResponse>()
            runOnUiThread {
                val name = extension.metaData?.name ?: "this add-on"
                val msg = StringBuilder()
                if (permissions.isNotEmpty()) msg.append("It can:\n• ").append(permissions.joinToString("\n• "))
                if (origins.isNotEmpty()) { if (msg.isNotEmpty()) msg.append("\n\n"); msg.append("On these sites:\n• ").append(origins.joinToString("\n• ")) }
                if (msg.isEmpty()) msg.append("It needs no special permissions.")
                AlertDialog.Builder(this@MainActivity).setTitle("Add $name?").setMessage(msg.toString())
                    .setPositiveButton("Add") { _, _ -> res.complete(WebExtension.PermissionPromptResponse(true, false, false)) }
                    .setNegativeButton("Cancel") { _, _ -> res.complete(WebExtension.PermissionPromptResponse(false, false, false)) }
                    .setOnCancelListener { res.complete(WebExtension.PermissionPromptResponse(false, false, false)) }.show()
            }
            return res
        }
    }

    private fun showAddons() {
        refreshAddonInfo()
        showScreen(4)
    }

    private fun askLink() {
        val input = EditText(this)
        input.setSingleLine(true)
        input.hint = "addons.mozilla.org link, add-on name, or .xpi link"
        AlertDialog.Builder(this).setTitle("Install add-on").setView(input)
            .setPositiveButton("Install") { _, _ -> installFromInput(input.text.toString()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun pickXpi() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.type = "*/*"
        @Suppress("DEPRECATION") startActivityForResult(i, REQ_XPI)
    }

    private fun installFromInput(raw: String) {
        val t = raw.trim(); if (t.isEmpty()) return
        if (t.contains(".xpi")) { installAddon(t); return }
        val slug = if (t.contains("/addon/")) t.substringAfter("/addon/").substringBefore('/').substringBefore('?') else t
        toast("Looking up add-on…")
        Thread {
            try {
                val api = "https://addons.mozilla.org/api/v5/addons/addon/" + Uri.encode(slug) + "/"
                val conn = URL(api).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 15000; conn.setRequestProperty("Accept", "application/json")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val cv = JSONObject(body).getJSONObject("current_version")
                val file = cv.optJSONObject("file") ?: cv.getJSONArray("files").getJSONObject(0)
                val xpi = file.getString("url")
                runOnUiThread { installAddon(xpi) }
            } catch (e: Exception) { toast("Could not find that add-on (" + e.javaClass.simpleName + ")") }
        }.start()
    }

    private fun installAddon(uri: String) {
        rt.webExtensionController.install(uri).accept({ ext ->
            toast("Installed " + (ext?.metaData?.name ?: "add-on"))
            refreshAddonInfo()
        }, { err ->
            toast("Install failed: " + (err?.toString() ?: "unknown error"))
        })
    }
}
