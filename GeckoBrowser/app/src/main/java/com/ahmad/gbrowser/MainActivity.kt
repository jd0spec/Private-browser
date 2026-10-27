package com.ahmad.gbrowser

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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

// --- ADM IMPORTS ---
import com.ahmad.gbrowser.downloads.ClintDownloadManager
import com.ahmad.gbrowser.downloads.DownloadsActivity

class MainActivity : ComponentActivity() {

    companion object {
        var runtime: GeckoRuntime? = null
        const val HOME = "https://duckduckgo.com"
        const val REQ_XPI = 11
        const val REQ_FILE = 12
    }

    class Tab(var url: String, var title: String = "") {
        var session: GeckoSession? = null
        var canBack = false
        var canForward = false
    }

    private lateinit var store: Store
    private lateinit var rt: GeckoRuntime
    private lateinit var geckoView: GeckoView
    
    // Compose UI State
    private var uiUrl by mutableStateOf("")
    private var uiProgress by mutableStateOf(0)
    private var uiIsLoading by mutableStateOf(false)
    private var uiTabCount by mutableStateOf(1)
    private var uiCanBack by mutableStateOf(false)
    private var uiCanForward by mutableStateOf(false)
    private var uiIsFullscreen by mutableStateOf(false)

    private val tabs = ArrayList<Tab>()
    private var current = -1
    private var fullscreen = false
    private var skipSave = false
    private var lastBack = 0L
    private val ui = Handler(Looper.getMainLooper())
    private val prompts by lazy { Prompts(this) }

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

    val freshProcess = runtime == null
    if (freshProcess) {
        runtime = GeckoRuntime.create(applicationContext, GeckoRuntimeSettings.Builder().build())
    }
    rt = runtime!!
    rt.webExtensionController.setPromptDelegate(addonPrompt)
    
    geckoView = GeckoView(this)

    setContent {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF6750A4),
                surface = androidx.compose.ui.graphics.Color(0xFFFFFBFE),
                surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE7E0EC),
                onSurface = androidx.compose.ui.graphics.Color(0xFF1C1B1F)
            )
        ) {
            BrowserScreen()
        }
    }

    val auto = BooleanArray(4) { store.flag(autoKeys[it]) }
    if (freshProcess && auto.any { it } && store.prefs.getBoolean("dirty", false)) {
        clearNow(auto, false) {}
    }
    store.prefs.edit().putBoolean("dirty", true).apply()

    restoreTabs()
    handleIntent(intent)
}

@Composable
fun BrowserScreen() {
    Scaffold(
        topBar = { if (!uiIsFullscreen) TopBar() },
        bottomBar = { if (!uiIsFullscreen) BottomBar() }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            AndroidView(
                factory = { geckoView },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar() {
    var textFieldValue by remember { mutableStateOf(uiUrl) }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiUrl) { 
        if (textFieldValue != uiUrl) textFieldValue = uiUrl 
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                placeholder = { Text("Search or enter address", fontSize = 14.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color(0xFFE7E0EC),
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color(0xFFE7E0EC),
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Go,
                    keyboardType = KeyboardType.Uri
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onGo = { 
                        go(textFieldValue) 
                        keyboardController?.hide()
                    }
                )
            )
            IconButton(onClick = { curTab()?.session?.reload(GeckoSession.LOAD_FLAGS_NONE) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = androidx.compose.ui.graphics.Color(0xFF6750A4))
            }
        }
        
        if (uiIsLoading) {
            LinearProgressIndicator(
                progress = { uiProgress / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = androidx.compose.ui.graphics.Color(0xFF6750A4)
            )
        }
    }
}

@Composable
fun BottomBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color(0xFFFFFBFE))
            .navigationBarsPadding()
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { if (uiCanBack) curTab()?.session?.goBack(true) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (uiCanBack) androidx.compose.ui.graphics.Color(0xFF1C1B1F) else androidx.compose.ui.graphics.Color.Gray)
        }
        IconButton(onClick = { if (uiCanForward) curTab()?.session?.goForward(true) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", tint = if (uiCanForward) androidx.compose.ui.graphics.Color(0xFF1C1B1F) else androidx.compose.ui.graphics.Color.Gray)
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .border(2.dp, androidx.compose.ui.graphics.Color(0xFF6750A4), RoundedCornerShape(6.dp))
                .clickable { showTabs() },
            contentAlignment = Alignment.Center
        ) {
            Text(uiTabCount.toString(), color = androidx.compose.ui.graphics.Color(0xFF6750A4), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        IconButton(onClick = { showMenu() }) {
            Icon(Icons.Default.MoreVert, "Menu", tint = androidx.compose.ui.graphics.Color(0xFF1C1B1F))
        }
    }
}
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIntent(intent)
}

private fun handleIntent(i: Intent?) {
    if (i?.action == Intent.ACTION_VIEW) {
        val u = i.dataString
        if (!u.isNullOrEmpty()) newTab(u)
    }
}

override fun onStop() {
    super.onStop()
    if (!skipSave) saveTabs()
}

override fun onDestroy() {
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
    if (fullscreen) {
        curTab()?.session?.exitFullScreen()
        return
    }
    val t = curTab()
    if (t != null && t.canBack) {
        t.session?.goBack(true)
        return
    }
    if (tabs.size > 1) {
        closeTab(current)
        return
    }
    val now = SystemClock.uptimeMillis()
    if (now - lastBack < 2000) {
        exitApp()
    } else {
        lastBack = now
        toast("Press back again to exit")
    }
}

@Deprecated("Deprecated in Java")
@Suppress("DEPRECATION")
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == REQ_FILE) {
        prompts.onFileResult(resultCode, data)
    } else if (requestCode == REQ_XPI) {
        val uri = data?.data
        if (resultCode == RESULT_OK && uri != null) {
            Thread {
                try {
                    val f = File(cacheDir, "addon-" + System.currentTimeMillis() + ".xpi")
                    contentResolver.openInputStream(uri)!!.use { i ->
                        f.outputStream().use { o -> i.copyTo(o) }
                    }
                    runOnUiThread { installAddon(Uri.fromFile(f).toString()) }
                } catch (e: Exception) {
                    toast("Could not read that file")
                }
            }.start()
        }
    }
}

private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
private fun toast(m: String) = runOnUiThread { Toast.makeText(this, m, Toast.LENGTH_LONG).show() }

private fun updateButtons() {
    val t = curTab()
    runOnUiThread {
        uiTabCount = tabs.size
        uiCanBack = t?.canBack == true
        uiCanForward = t?.canForward == true
    }
}

private fun hideKeyboard() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(geckoView.windowToken, 0)
}

private fun display(u: String): String {
    if (u == "about:blank") return ""
    return u.removePrefix("https://").removePrefix("http://").removeSuffix("/")
}

private fun setFullscreen(on: Boolean) {
    runOnUiThread {
        fullscreen = on
        uiIsFullscreen = on
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
    if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("about:") ||
        lower.startsWith("file:") || lower.startsWith("moz-extension:")
    ) return t
    if (lower == "localhost" || lower.startsWith("localhost:")) return "http://$t"
    if (!t.contains(' ') && t.contains('.')) return "https://$t"
    return store.searchUrl() + Uri.encode(t)
}

private fun go(text: String) {
    val url = toUrl(text)
    val t = curTab()
    if (t == null) newTab(url) else {
        t.url = url
        t.session?.loadUri(url)
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

private fun selectTab(i: Int) {
    if (i !in tabs.indices) return
    tabs.getOrNull(current)?.session?.let { if (it.isOpen) it.setActive(false) }
    current = i
    val t = tabs[i]
    val s = ensureSession(t)
    geckoView.releaseSession()
    geckoView.setSession(s)
    s.setActive(true)
    runOnUiThread { uiUrl = display(t.url) }
    uiProgress = 0
    uiIsLoading = false
    updateButtons()
}

private fun closeTab(i: Int) {
    if (i !in tabs.indices) return
    val t = tabs[i]
    val wasCurrent = (i == current)
    if (wasCurrent) geckoView.releaseSession()
    t.session?.let { if (it.isOpen) it.close() }
    t.session = null
    tabs.removeAt(i)
    if (tabs.isEmpty()) {
        current = -1
        newTab(HOME)
        return
    }
    if (wasCurrent) {
        current = -1
        selectTab(minOf(i, tabs.size - 1))
    } else {
        if (i < current) current--
        updateButtons()
    }
}

private fun closeAllTabs() {
    geckoView.releaseSession()
    for (t in tabs) {
        t.session?.let { if (it.isOpen) it.close() }
        t.session = null
    }
    tabs.clear()
    current = -1
}

private fun saveTabs() = store.saveTabs(tabs.map { Pair(it.url, it.title) }, current)

private fun restoreTabs() {
    val (saved, cur) = store.loadTabs()
    if (saved.isEmpty()) {
        newTab(HOME)
        return
    }
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

inner class TabDelegates(private val tab: Tab) :
    GeckoSession.ContentDelegate, GeckoSession.ProgressDelegate, GeckoSession.NavigationDelegate {

    private fun isCurrent() = curTab() === tab

    override fun onTitleChange(session: GeckoSession, title: String?) { tab.title = title ?: "" }
    override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) { setFullscreen(fullScreen) }
    override fun onExternalResponse(session: GeckoSession, response: WebResponse) { handleDownload(response) }
    override fun onCrash(session: GeckoSession) { recover(tab) }
    override fun onKill(session: GeckoSession) { recover(tab) }
    
    override fun onCloseRequest(session: GeckoSession) {
        runOnUiThread {
            val i = tabs.indexOf(tab)
            if (i >= 0) closeTab(i)
        }
    }

    override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: GeckoSession.ContentDelegate.ContextElement) {
        val link = element.linkUri
        val img = element.srcUri
        val names = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        if (link != null) {
            names.add("Open link in new tab")
            acts.add { newTab(link) }
            names.add("Copy link")
            acts.add { copyText(link) }
        }
        if (img != null) {
            names.add("Open image/media in new tab")
            acts.add { newTab(img) }
        }
        if (names.isEmpty()) return
        runOnUiThread {
            AlertDialog.Builder(this@MainActivity).setItems(names.toTypedArray()) { _, i -> acts[i]() }.show()
        }
    }

    override fun onPageStart(session: GeckoSession, url: String) {
        if (isCurrent()) runOnUiThread { uiProgress = 5; uiIsLoading = true }
    }

    override fun onProgressChange(session: GeckoSession, p: Int) {
        if (isCurrent()) runOnUiThread { uiProgress = p; uiIsLoading = p in 1..99 }
    }

    override fun onPageStop(session: GeckoSession, success: Boolean) {
        if (isCurrent()) runOnUiThread { uiIsLoading = false }
        if (success && tab.url.startsWith("http")) {
            store.addVisit(tab.url, tab.title)
            // injectPlayerSkin(session)
        }
    }

    override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
        tab.url = url ?: ""
        if (isCurrent()) runOnUiThread { uiUrl = display(tab.url) }
    }

    override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
        tab.canBack = canGoBack
        if (isCurrent()) runOnUiThread { uiCanBack = canGoBack }
    }

    override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
        tab.canForward = canGoForward
        if (isCurrent()) runOnUiThread { uiCanForward = canGoForward }
    }

    override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
        val u = request.uri
        
        if (u.startsWith("gbrowser-dl://")) {
            val videoUrl = Uri.decode(u.removePrefix("gbrowser-dl://"))
            if (videoUrl.startsWith("blob:")) {
                toast("Cannot download live/DRM streams directly.")
            } else {
                val name = URLUtil.guessFileName(videoUrl, null, "video/mp4")
                
                // SEND TO ADM (Fixed parameters)
                ClintDownloadManager.enqueue(
                    context = this@MainActivity,
                    url = videoUrl,
                    filename = name,
                    userAgent = ""
                )
                toast("Downloading video via ADM...")
            }
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }

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
        private fun openExternal(u: String) {
        try {
            val i = if (u.startsWith("intent:")) Intent.parseUri(u, Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, Uri.parse(u))
            i.addCategory(Intent.CATEGORY_BROWSABLE)
            i.component = null
            i.selector = null
            startActivity(i)
        } catch (e: Exception) {
            toast("No app can open this link")
        }
    }

    private fun copyText(s: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("link", s))
        toast("Copied")
    }

    private fun handleDownload(r: WebResponse) {
        val uri = r.uri
        if (uri.contains(".xpi")) {
            runOnUiThread { installAddon(uri) }
            return
        }
        
        val ct = r.headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value ?: "application/octet-stream"
        val cd = r.headers.entries.firstOrNull { it.key.equals("content-disposition", true) }?.value
        val name = URLUtil.guessFileName(uri, cd, ct)
        
        // SEND NORMAL DOWNLOADS TO ADM (Fixed parameters)
        ClintDownloadManager.enqueue(
            context = this@MainActivity,
            url = uri,
            filename = name,
            userAgent = ""
        )
        toast("Download started via ADM")
    }

    private fun twoLineRow(title: String, sub: String, bold: Boolean): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val a = TextView(this)
        a.text = if (title.isEmpty()) sub else title
        a.textSize = 16f
        a.maxLines = 1
        a.ellipsize = TextUtils.TruncateAt.END
        if (bold) a.typeface = Typeface.DEFAULT_BOLD
        val b = TextView(this)
        b.text = sub
        b.textSize = 12f
        b.alpha = 0.6f
        b.maxLines = 1
        b.ellipsize = TextUtils.TruncateAt.END
        col.addView(a)
        col.addView(b)
        return col
    }

    private fun showTabs() {
        val lv = ListView(this)
        var dlg: AlertDialog? = null
        lv.adapter = object : BaseAdapter() {
            override fun getCount() = tabs.size
            override fun getItem(position: Int): Any = tabs[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val t = tabs[position]
                val row = LinearLayout(this@MainActivity)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(dp(16), dp(8), dp(4), dp(8))
                row.addView(twoLineRow(t.title, t.url, position == current), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val x = TextView(this@MainActivity)
                x.text = "✕"
                x.textSize = 18f
                x.gravity = Gravity.CENTER
                x.setOnClickListener { closeTab(position); notifyDataSetChanged() }
                row.addView(x, LinearLayout.LayoutParams(dp(48), dp(48)))
                return row
            }
        }
        lv.setOnItemClickListener { _, _, p, _ -> selectTab(p); dlg?.dismiss() }
        dlg = AlertDialog.Builder(this)
            .setTitle("Tabs")
            .setView(lv)
            .setPositiveButton("New tab") { _, _ -> newTab(HOME) }
            .setNegativeButton("Close all") { _, _ -> closeAllTabs(); newTab(HOME) }
            .setNeutralButton("Done", null)
            .show()
    }

    private fun showHistory() {
        val list = store.history().take(300)
        if (list.isEmpty()) { toast("No history yet"); return }
        val lv = ListView(this)
        var dlg: AlertDialog? = null
        lv.adapter = object : BaseAdapter() {
            override fun getCount() = list.size
            override fun getItem(position: Int): Any = list[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val v = list[position]
                val row = twoLineRow(v.title, v.url, false)
                row.setPadding(dp(16), dp(8), dp(16), dp(8))
                return row
            }
        }
        lv.setOnItemClickListener { _, _, p, _ -> go(list[p].url); dlg?.dismiss() }
        dlg = AlertDialog.Builder(this)
            .setTitle("History")
            .setView(lv)
            .setPositiveButton("Clear all") { _, _ -> store.clearHistory(); toast("History cleared") }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showMenu() {
        val items = arrayOf("New tab", "History", "Add-ons", "Clear data now…", "Auto-clear on exit…", "Search engine: " + store.engineNames[store.engineIndex()], "Downloads", "Exit")
        AlertDialog.Builder(this).setItems(items) { _, i ->
            when (i) {
                0 -> newTab(HOME)
                1 -> showHistory()
                2 -> showAddons()
                3 -> chooseTypes("Clear data now", booleanArrayOf(true, true, false, true), "Clear") { st -> clearNow(st, false) { toast("Cleared") } }
                4 -> chooseTypes("Auto-clear on exit", BooleanArray(4) { store.flag(autoKeys[it]) }, "Save") { st -> for (k in 0 until 4) store.setFlag(autoKeys[k], st[k]); toast("Saved") }
                5 -> AlertDialog.Builder(this).setTitle("Search engine").setItems(store.engineNames) { _, k -> store.setEngine(k) }.show()
                6 -> try { 
    startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) 
} catch (e: Exception) { 
    toast("Downloads are saving in the background!") 
                }
                7 -> exitApp()
                else -> {}
            }
        }.show()
    }

    private fun chooseTypes(title: String, initial: BooleanArray, okLabel: String, onOk: (BooleanArray) -> Unit) {
        val state = initial.copyOf()
        AlertDialog.Builder(this).setTitle(title).setMultiChoiceItems(dataLabels, state) { _, i, on -> state[i] = on }
            .setPositiveButton(okLabel) { _, _ -> onOk(state) }.setNegativeButton("Cancel", null).show()
    }

    private fun clearNow(st: BooleanArray, forExit: Boolean, done: () -> Unit) {
        if (st[0]) store.clearHistory()
        if (st[2]) {
            store.clearTabs()
            if (forExit) skipSave = true else { closeAllTabs(); newTab(HOME) }
        }
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
                finishAndRemoveTask()
                ui.postDelayed({ Process.killProcess(Process.myPid()) }, 300)
            }
        }
        if (st.any { it }) {
            clearNow(st, true) { finish(true) }
            ui.postDelayed({ finish(false) }, 2500)
        } else {
            store.prefs.edit().putBoolean("dirty", false).commit()
            finish(true)
        }
    }

    private val addonPrompt = object : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(extension: WebExtension, permissions: Array<String>, origins: Array<String>, dataCollectionPermissions: Array<String>): GeckoResult<WebExtension.PermissionPromptResponse>? {
            val res = GeckoResult<WebExtension.PermissionPromptResponse>()
            runOnUiThread {
                val name = extension.metaData?.name ?: "this add-on"
                val msg = StringBuilder()
                if (permissions.isNotEmpty()) msg.append("It can:\n• ").append(permissions.joinToString("\n• "))
                if (origins.isNotEmpty()) {
                    if (msg.isNotEmpty()) msg.append("\n\n")
                    msg.append("On these sites:\n• ").append(origins.joinToString("\n• "))
                }
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
        rt.webExtensionController.list().accept({ list ->
            runOnUiThread {
                val exts = ArrayList<WebExtension>()
                if (list != null) exts.addAll(list)
                val labels = ArrayList<String>()
                for (e in exts) {
                    val m = e.metaData
                    var s = (m?.name ?: e.id)
                    if (m != null) s += "  " + (m.version ?: "")
                    if (m != null && !m.enabled) s += "  (off)"
                    labels.add(s)
                }
                labels.add("＋ Recommended add-ons")
                labels.add("＋ Install from link or name")
                labels.add("＋ Install from .xpi file")
                AlertDialog.Builder(this).setTitle("Add-ons").setItems(labels.toTypedArray()) { _, i ->
                    if (i < exts.size) manageAddon(exts[i]) else {
                        when (i - exts.size) {
                            0 -> AlertDialog.Builder(this).setTitle("Recommended").setItems(recommended.map { it.first }.toTypedArray()) { _, k -> installFromInput(recommended[k].second) }.show()
                            1 -> askLink()
                            2 -> pickXpi()
                            else -> {}
                        }
                    }
                }.show()
            }
        }, { toast("Could not load the add-on list") })
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
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        @Suppress("DEPRECATION")
        startActivityForResult(i, REQ_XPI)
    }

    private fun manageAddon(e: WebExtension) {
        val m = e.metaData
        val ctl = rt.webExtensionController
        val enabled = m?.enabled ?: true
        val names = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        names.add(if (enabled) "Turn off" else "Turn on")
        acts.add {
            val r = if (enabled) ctl.disable(e, WebExtensionController.EnableSource.USER) else ctl.enable(e, WebExtensionController.EnableSource.USER)
            r.accept({ toast("Done") }, { toast("Could not change the add-on") })
        }
        val opts = m?.optionsPageUrl
        if (!opts.isNullOrEmpty()) {
            names.add("Open its settings page")
            acts.add { newTab(opts) }
        }
        names.add("Remove")
        acts.add {
            AlertDialog.Builder(this).setTitle("Remove ${m?.name ?: "add-on"}?")
                .setPositiveButton("Remove") { _, _ -> ctl.uninstall(e).accept({ toast("Removed") }, { toast("Could not remove it") }) }
                .setNegativeButton("Cancel", null).show()
        }
        AlertDialog.Builder(this).setTitle(m?.name ?: e.id).setItems(names.toTypedArray()) { _, i -> acts[i]() }.show()
    }

    private fun installFromInput(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) return
        if (t.contains(".xpi")) { installAddon(t); return }
        val slug = if (t.contains("/addon/")) t.substringAfter("/addon/").substringBefore('/').substringBefore('?') else t
        toast("Looking up add-on…")
        Thread {
            try {
                val api = "https://addons.mozilla.org/api/v5/addons/addon/" + Uri.encode(slug) + "/"
                val conn = URL(api).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("Accept", "application/json")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val cv = JSONObject(body).getJSONObject("current_version")
                val file = cv.optJSONObject("file") ?: cv.getJSONArray("files").getJSONObject(0)
                val xpi = file.getString("url")
                runOnUiThread { installAddon(xpi) }
            } catch (e: Exception) {
                toast("Could not find that add-on (" + e.javaClass.simpleName + ")")
            }
        }.start()
    }

    private fun installAddon(uri: String) {
        rt.webExtensionController.install(uri).accept({ ext ->
            toast("Installed " + (ext?.metaData?.name ?: "add-on"))
        }, { err ->
            toast("Install failed: " + (err?.toString() ?: "unknown error"))
        })
    }
}
