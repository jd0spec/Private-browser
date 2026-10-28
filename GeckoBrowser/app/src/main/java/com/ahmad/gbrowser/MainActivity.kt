package com.ahmad.gbrowser

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
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
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import java.util.UUID
import com.ahmad.gbrowser.downloads.ClintDownloadManager
import com.ahmad.gbrowser.downloads.DownloadsActivity

class MainActivity : ComponentActivity() {

    companion object {
        var runtime: GeckoRuntime? = null
        const val HOME = "browser://home"
        const val REQ_XPI = 11
        const val REQ_FILE = 12
    }

    class Tab(var url: String, var title: String = "") {
        val id: String = UUID.randomUUID().toString()
        var session: GeckoSession? = null
        var canBack = false
        var canForward = false
        var bitmap: Bitmap? = null
    }

    data class Shortcut(
        val id: String,
        val name: String,
        val url: String = "",
        val items: List<Shortcut> = emptyList(),
        val iconUrl: String? = null
    ) {
        val isFolder: Boolean get() = items.isNotEmpty()
    }

    private lateinit var store: Store
    private lateinit var rt: GeckoRuntime
    private lateinit var geckoView: GeckoView

    private var uiUrl by mutableStateOf("")
    private var uiProgress by mutableStateOf(0)
    private var uiIsLoading by mutableStateOf(false)
    private var uiTabCount by mutableStateOf(1)
    private var uiIsFullscreen by mutableStateOf(false)
    
    private var showMainMenu by mutableStateOf(false)
    private var showContextMenu by mutableStateOf(false)
    private var showAddonsDialog by mutableStateOf(false)
    private var showSearchEngineDialog by mutableStateOf(false)
    private var showSettingsScreen by mutableStateOf(false)
    private var showTabManager by mutableStateOf(false)
    
    private var openFolder by mutableStateOf<Shortcut?>(null)

    private var contextLink by mutableStateOf<String?>(null)
    private var contextText by mutableStateOf<String?>(null)
    private var contextImg by mutableStateOf<String?>(null)

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
        "moz-extension", "resource", "view-source", "ws", "wss", "chrome", "browser"
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
    ClintDownloadManager.init(this)

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    setContent {
        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
        val colorScheme = if (isDark) {
            darkColorScheme(
                primary = ComposeColor(0xFFD0BCFF),
                onPrimary = ComposeColor(0xFF381E72),
                background = ComposeColor.Black,
                surface = ComposeColor.Black,
                surfaceVariant = ComposeColor(0xFF1E1E1E),
                onSurface = ComposeColor(0xFFE6E1E5),
                onSurfaceVariant = ComposeColor(0xFFCAC4D0),
                outline = ComposeColor(0xFF49454F)
            )
        } else {
            lightColorScheme(
                primary = ComposeColor(0xFF6750A4),
                onPrimary = ComposeColor.White,
                background = ComposeColor(0xFFFFFBFE),
                surface = ComposeColor(0xFFFFFBFE),
                surfaceVariant = ComposeColor(0xFFE7E0EC),
                onSurface = ComposeColor(0xFF1C1B1F),
                onSurfaceVariant = ComposeColor(0xFF49454F),
                outline = ComposeColor(0xFF79747E)
            )
        }
        
        MaterialTheme(colorScheme = colorScheme) {
            if (showSettingsScreen) {
                SettingsScreen()
            } else {
                BrowserScreen()
            }
            
            if (showMainMenu) MainMenuSheet()
            if (showContextMenu) ContextMenuSheet()
            if (showAddonsDialog) AddonsDialog()
            if (showSearchEngineDialog) SearchEngineDialog()
            if (openFolder != null) FolderDialog()
            if (showTabManager) TabManagerSheet()
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
        bottomBar = { if (!uiIsFullscreen) BottomBar() }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            
            if (uiIsLoading) {
                LinearProgressIndicator(
                    progress = { uiProgress / 100f },
                    modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (uiUrl == HOME || uiUrl == "about:blank" || uiUrl.isEmpty()) {
                HomePage { searchQuery ->
                    go(searchQuery)
                }
            } else {
                AndroidView(
                    factory = { geckoView },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomePage(onSearch: (String) -> Unit) {
    val coroutineScope = rememberCoroutineScope()

    var shortcuts by remember { mutableStateOf(listOf(
        Shortcut("1", "Web", "https://duckduckgo.com"),
        Shortcut("2", "Video", "https://youtube.com"),
        Shortcut("3", "Social", items = listOf(
            Shortcut("3_1", "X", "https://x.com"),
            Shortcut("3_2", "Reddit", "https://reddit.com")
        ))
    )) }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editingShortcut by remember { mutableStateOf<Shortcut?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text("Gecko", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Browse the web privately", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(48.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(shortcuts) { shortcut ->
                if (shortcut.isFolder) {
                    FolderIcon(shortcut = shortcut, onClick = { openFolder = shortcut }, onLongClick = { editingShortcut = shortcut })
                } else {
                    QuickLink(name = shortcut.name, url = shortcut.url, iconUrl = shortcut.iconUrl, onClick = { onSearch(shortcut.url) }, onLongClick = { editingShortcut = shortcut })
                }
            }
            
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showAddDialog = true }) {
                    Surface(modifier = Modifier.size(56.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, "Add", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Add", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }

    if (showAddDialog || editingShortcut != null) {
        var nameState by remember { mutableStateOf(editingShortcut?.name ?: "") }
        var urlState by remember { mutableStateOf(editingShortcut?.url ?: "") }
        val isFolder = editingShortcut?.isFolder == true

        AlertDialog(
            onDismissRequest = { showAddDialog = false; editingShortcut = null },
            title = { Text(if (editingShortcut == null) "Add Shortcut" else if (isFolder) "Edit Folder" else "Edit Shortcut") },
            text = {
                Column {
                    OutlinedTextField(value = nameState, onValueChange = { nameState = it }, label = { Text("Name") }, singleLine = true)
                    if (!isFolder) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = urlState, onValueChange = { urlState = it }, label = { Text("URL") }, singleLine = true)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (nameState.isNotBlank() && (isFolder || urlState.isNotBlank())) {
                        if (editingShortcut == null) {
                            coroutineScope.launch {
                                val fetchedIcon = fetchSiteIcon(urlState)
                                shortcuts = shortcuts + Shortcut(System.currentTimeMillis().toString(), nameState, urlState, iconUrl = fetchedIcon)
                            }
                        } else {
                            shortcuts = shortcuts.map { 
                                if (it.id == editingShortcut?.id) {
                                    if (isFolder) it.copy(name = nameState) else it.copy(name = nameState, url = urlState)
                                } else it 
                            }
                        }
                    }
                    showAddDialog = false; editingShortcut = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (editingShortcut != null) {
                        TextButton(onClick = { shortcuts = shortcuts.filter { it.id != editingShortcut?.id }; showAddDialog = false; editingShortcut = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showAddDialog = false; editingShortcut = null }) { Text("Cancel") }
                }
            }
        )
    }
}

suspend fun fetchSiteIcon(url: String): String? = withContext(Dispatchers.IO) {
    try {
        val formattedUrl = if (!url.startsWith("http")) "https://$url" else url
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder().url(formattedUrl).build()
        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: return@withContext null
        
        val regex = Regex("""<link[^>]+rel=["'](?:apple-touch-icon|icon|shortcut icon)["'][^>]+href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val match = regex.find(html)
        
        if (match != null) {
            val href = match.groupValues[1]
            if (href.startsWith("http")) return@withContext href
            if (href.startsWith("//")) return@withContext "https:$href"
            val baseUrl = java.net.URL(formattedUrl)
            return@withContext java.net.URL(baseUrl, href).toString()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickLink(name: String, url: String, iconUrl: String?, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Surface(modifier = Modifier.size(56.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                if (iconUrl != null) {
                    AsyncImage(model = iconUrl, contentDescription = name, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
                } else if (url.isNotBlank()) {
                    AsyncImage(model = "https://www.google.com/s2/favicons?domain=${url}&sz=128", contentDescription = name, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
                } else {
                    Text(name.take(1).uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun FolderDialog() {
    val folder = openFolder ?: return
    Dialog(onDismissRequest = { openFolder = null }) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.padding(16.dp).wrapContentSize()) {
            Column(modifier = Modifier.padding(24.dp).widthIn(min = 200.dp, max = 350.dp)) {
                Text(folder.name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(24.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(4), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                    items(folder.items) { subItem ->
                        QuickLink(name = subItem.name, url = subItem.url, iconUrl = subItem.iconUrl, onClick = { openFolder = null; go(subItem.url) }, onLongClick = { })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderIcon(shortcut: Shortcut, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Surface(modifier = Modifier.size(56.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(modifier = Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FolderItemPreview(shortcut.items.getOrNull(0), Modifier.weight(1f))
                    FolderItemPreview(shortcut.items.getOrNull(1), Modifier.weight(1f))
                }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FolderItemPreview(shortcut.items.getOrNull(2), Modifier.weight(1f))
                    FolderItemPreview(shortcut.items.getOrNull(3), Modifier.weight(1f))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(shortcut.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun FolderItemPreview(item: Shortcut?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        if (item != null) {
            if (item.iconUrl != null) {
                AsyncImage(model = item.iconUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else if (item.url.isNotBlank()) {
                AsyncImage(model = "https://www.google.com/s2/favicons?domain=${item.url}&sz=128", contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(item.name.take(1).uppercase(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomBar() {
    var textFieldValue by remember { mutableStateOf(uiUrl) }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiUrl) { if (textFieldValue != uiUrl && uiUrl != HOME && uiUrl != "about:blank") textFieldValue = uiUrl }

    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = { uiUrl = HOME; curTab()?.url = HOME; geckoView.releaseSession() }) {
            Icon(Icons.Default.Home, "Home", tint = MaterialTheme.colorScheme.onSurface)
        }

        Surface(modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (textFieldValue.startsWith("https://") || textFieldValue == HOME) {
                    Icon(Icons.Default.Lock, "Secure", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Icon(Icons.Default.Search, "Search", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = if (textFieldValue == HOME || textFieldValue == "about:blank") "" else textFieldValue,
                    onValueChange = { textFieldValue = it },
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true, maxLines = 1,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { go(textFieldValue); keyboardController?.hide() }),
                    decorationBox = { innerTextField ->
                        if (textFieldValue.isEmpty() || textFieldValue == HOME || textFieldValue == "about:blank") {
                            Text("Search or enter address", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        innerTextField()
                    }
                )
            }
        }

        Box(modifier = Modifier.size(40.dp).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)).clickable { showTabManager = true }, contentAlignment = Alignment.Center) {
            Text(uiTabCount.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        IconButton(onClick = { showMainMenu = true }) {
            Icon(Icons.Default.MoreVert, "Menu", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuSheet() {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(onDismissRequest = { showMainMenu = false }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            MenuItem(icon = Icons.Default.Add, text = "New Tab") { showMainMenu = false; newTab(HOME) }
            MenuItem(icon = Icons.Default.History, text = "History") { showMainMenu = false; showHistory() }
            MenuItem(icon = Icons.Default.SettingsSuggest, text = "Add-ons") { showMainMenu = false; showAddonsDialog = true }
            MenuItem(icon = Icons.Default.Download, text = "Downloads") { showMainMenu = false; startActivity(Intent(this@MainActivity, DownloadsActivity::class.java)) }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            MenuItem(icon = Icons.Default.Settings, text = "Settings") { 
                showMainMenu = false
                showSettingsScreen = true
            }
            MenuItem(icon = Icons.Default.Info, text = "Exit") { 
                showMainMenu = false; exitApp() 
            }
        }
    }
}

@Composable
fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { onClick() }) {
        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = text, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextMenuSheet() {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(onDismissRequest = { showContextMenu = false }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            contextLink?.let { link ->
                MenuItem(icon = Icons.Default.OpenInNew, text = "Open link in new tab") { showContextMenu = false; newTab(link) }
                MenuItem(icon = Icons.Default.ContentCopy, text = "Copy link") { showContextMenu = false; copyText(link) }
            }
            contextText?.let { txt ->
                MenuItem(icon = Icons.Default.ContentCopy, text = "Copy text") { showContextMenu = false; copyText(txt) }
            }
            contextImg?.let { img ->
                // FIXED: Changed Icons.Default.Image to Icons.Default.Photo
                MenuItem(icon = Icons.Default.Photo, text = "Open image in new tab") { showContextMenu = false; newTab(img) }
                MenuItem(icon = Icons.Default.ContentCopy, text = "Copy image link") { showContextMenu = false; copyText(img) }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            IconButton(onClick = { showSettingsScreen = false }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            MenuItem(icon = Icons.Default.ClearAll, text = "Clear data now…") { chooseTypes("Clear data now", booleanArrayOf(true, true, false, true), "Clear") { st -> clearNow(st, false) { toast("Cleared") } } }
            MenuItem(icon = Icons.Default.Delete, text = "Auto-clear on exit…") { chooseTypes("Auto-clear on exit", BooleanArray(4) { store.flag(autoKeys[it]) }, "Save") { st -> for (k in 0 until 4) store.setFlag(autoKeys[k], st[k]); toast("Saved") } }
            MenuItem(icon = Icons.Default.Search, text = "Search engine: " + store.engineNames[store.engineIndex()]) { showSearchEngineDialog = true }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            MenuItem(icon = Icons.Default.SettingsSuggest, text = "Add-ons") { showAddonsDialog = true }
            MenuItem(icon = Icons.Default.History, text = "History") { showHistory() }
        }
    }
}

@Composable
fun ModernPillButton(text: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(50), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onClick() }) {
        Text(text, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
    }
}

@Composable
fun AddonsDialog() {
    Dialog(onDismissRequest = { showAddonsDialog = false }) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.background, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.padding(16.dp).wrapContentSize()) {
            Column(modifier = Modifier.padding(24.dp).widthIn(min = 300.dp, max = 400.dp)) {
                Text("Add-ons", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(16.dp))
                ModernPillButton("＋ Recommended add-ons") { showAddonsDialog = false; AlertDialog.Builder(this@MainActivity).setTitle("Recommended").setItems(recommended.map { it.first }.toTypedArray()) { _, k -> installFromInput(recommended[k].second) }.show() }
                ModernPillButton("＋ Install from link or name") { showAddonsDialog = false; askLink() }
                ModernPillButton("＋ Install from .xpi file") { showAddonsDialog = false; pickXpi() }
            }
        }
    }
}

@Composable
fun SearchEngineDialog() {
    Dialog(onDismissRequest = { showSearchEngineDialog = false }) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.background, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.padding(16.dp).wrapContentSize()) {
            Column(modifier = Modifier.padding(24.dp).widthIn(min = 300.dp, max = 400.dp)) {
                Text("Search Engine", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(16.dp))
                store.engineNames.forEachIndexed { index, name ->
                    ModernPillButton(name) { store.setEngine(index); showSearchEngineDialog = false }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TabManagerSheet() {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = { showTabManager = false }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Text("Tabs", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                    Button(onClick = { showTabManager = false; newTab(HOME) }) {
                        Text("New Tab")
                    }
                }
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        
                        val index = tabs.indexOf(tab)
                        
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                                    true
                                } else false
                            }
                        )

                        // Trigger removal AFTER the swipe animation completes to prevent glitch
                        LaunchedEffect(dismissState.currentValue) {
                            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart || dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
                                val tabToClose = tab
                                val idx = tabs.indexOf(tabToClose)
                                
                                // Reset state safely
                                dismissState.reset()
                                
                                // Close tab and show undo
                                closeTab(idx)
                                if (tabs.isEmpty()) showTabManager = false
                                
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Tab closed",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        tabs.add(idx, tabToClose)
                                        selectTab(idx)
                                        updateButtons()
                                    }
                                }
                            }
                        }

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = true,
                            enableDismissFromEndToStart = true,
                            backgroundContent = {
                                val color = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(color).padding(16.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onError)
                                }
                            }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(if (index == current) 2.dp else 1.dp, if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                modifier = Modifier.height(160.dp).clickable {
                                    selectTab(index)
                                    showTabManager = false
                                }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        if (tab.url.isNotBlank() && tab.url != HOME) {
                                            AsyncImage(
                                                model = "https://www.google.com/s2/favicons?domain=${tab.url}&sz=64",
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)),
                                                contentScale = ContentScale.Fit
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(tab.title.ifBlank { "New Tab" }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    // LIVE PREVIEW CAPTURED VIA GECKOVIEW API
                                    Box(modifier = Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                                        if (tab.bitmap != null) {
                                            Image(
                                                bitmap = tab.bitmap!!.asImageBitmap(),
                                                contentDescription = "Live Preview",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else if (tab.url == HOME || tab.url == "about:blank") {
                                            Text("Start Page", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        } else {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                if (tab.url.isNotBlank()) {
                                                    AsyncImage(
                                                        model = "https://www.google.com/s2/favicons?domain=${tab.url}&sz=128",
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)),
                                                        contentScale = ContentScale.Fit
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                }
                                                Text("Loading...", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            FloatingActionButton(
                onClick = { showTabManager = false; newTab(HOME) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "New Tab")
            }
            
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

private fun handleIntent(i: Intent?) {
    if (i?.action == Intent.ACTION_VIEW) { val u = i.dataString; if (!u.isNullOrEmpty()) newTab(u) }
}

override fun onStop() { super.onStop(); if (!skipSave) saveTabs() }

override fun onDestroy() {
    try { geckoView.releaseSession() } catch (e: Exception) {}
    for (t in tabs) { 
        try { t.session?.let { if (it.isOpen) it.close() } } catch (e: Exception) {} 
        t.session = null
        t.bitmap?.recycle()
    }
    super.onDestroy()
}

@Deprecated("Deprecated in Java")
@Suppress("DEPRECATION")
override fun onBackPressed() {
    if (showTabManager) { showTabManager = false; return }
    if (openFolder != null) { openFolder = null; return }
    if (showMainMenu) { showMainMenu = false; return }
    if (showContextMenu) { showContextMenu = false; return }
    if (showAddonsDialog) { showAddonsDialog = false; return }
    if (showSearchEngineDialog) { showSearchEngineDialog = false; return }
    if (showSettingsScreen) { showSettingsScreen = false; return }
    
    if (fullscreen) { curTab()?.session?.exitFullScreen(); return }
    val t = curTab()
    if (t != null && t.canBack) { t.session?.goBack(true); return }
    if (tabs.size > 1) { closeTab(current); return }
    
    val now = SystemClock.uptimeMillis()
    if (now - lastBack < 2000) exitApp() else { lastBack = now; toast("Press back again to exit") }
}

@Deprecated("Deprecated in Java")
@Suppress("DEPRECATION")
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == REQ_FILE) { prompts.onFileResult(resultCode, data) } else if (requestCode == REQ_XPI) {
        val uri = data?.data
        if (resultCode == RESULT_OK && uri != null) {
            Thread {
                try {
                    val f = File(cacheDir, "addon-" + System.currentTimeMillis() + ".xpi")
                    contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                    runOnUiThread { installAddon(Uri.fromFile(f).toString()) }
                } catch (e: Exception) { toast("Could not read that file") }
            }.start()
        }
    }
}

private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
private fun toast(m: String) = runOnUiThread { Toast.makeText(this, m, Toast.LENGTH_LONG).show() }
private fun updateButtons() { runOnUiThread { uiTabCount = tabs.size } }
private fun hideKeyboard() { val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager; imm.hideSoftInputFromWindow(geckoView.windowToken, 0) }

private fun setFullscreen(on: Boolean) {
    runOnUiThread {
        fullscreen = on; uiIsFullscreen = on
        val c = window.insetsController
        if (c != null) {
            if (on) { c.hide(WindowInsets.Type.systemBars()); c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
            else { c.show(WindowInsets.Type.systemBars()) }
        }
    }
}

private fun curTab(): Tab? = tabs.getOrNull(current)

private fun toUrl(input: String): String {
    val t = input.trim()
    if (t.isEmpty() || t == HOME) return HOME
    val lower = t.lowercase()
    if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("about:") || lower.startsWith("file:") || lower.startsWith("moz-extension:")) return t
    if (lower == "localhost" || lower.startsWith("localhost:")) return "http://$t"
    if (!t.contains(' ') && t.contains('.')) return "https://$t"
    return store.searchUrl() + Uri.encode(t)
}

private fun go(text: String) {
    val url = toUrl(text)
    val t = curTab()
    if (t == null) newTab(url) else {
        t.url = url
        if (url == HOME) { t.session?.loadUri("about:blank"); uiUrl = HOME; geckoView.releaseSession() } else { t.session?.loadUri(url) }
    }
    hideKeyboard(); geckoView.requestFocus()
}

private fun newTab(url: String, select: Boolean = true): Tab {
    val t = Tab(url); tabs.add(t)
    if (select) selectTab(tabs.size - 1) else updateButtons()
    return t
}

private fun ensureSession(t: Tab): GeckoSession {
    t.session?.let { return it }
    val s = GeckoSession(); val d = TabDelegates(t)
    s.contentDelegate = d; s.progressDelegate = d; s.navigationDelegate = d; s.promptDelegate = prompts
    s.open(rt); t.session = s
    if (t.url.isNotEmpty() && t.url != HOME) s.loadUri(t.url)
    return s
}

private fun selectTab(i: Int) {
    if (i !in tabs.indices) return
    tabs.getOrNull(current)?.session?.let { if (it.isOpen) it.setActive(false) }
    current = i; val t = tabs[i]; val s = ensureSession(t)
    geckoView.releaseSession(); geckoView.setSession(s); s.setActive(true)
    runOnUiThread { uiUrl = t.url }; uiProgress = 0; uiIsLoading = false; updateButtons()
}

private fun closeTab(i: Int) {
    if (i !in tabs.indices) return
    val t = tabs[i]; val wasCurrent = (i == current)
    if (wasCurrent) geckoView.releaseSession()
    t.session?.let { if (it.isOpen) it.close() }; t.session = null; t.bitmap?.recycle(); tabs.removeAt(i)
    
    if (tabs.isEmpty()) { current = -1; newTab(HOME); return }
    if (wasCurrent) { current = -1; selectTab(minOf(i, tabs.size - 1)) } else { if (i < current) current--; updateButtons() }
}

private fun closeAllTabs() {
    geckoView.releaseSession()
    for (t in tabs) { t.session?.let { if (it.isOpen) it.close() }; t.session = null; t.bitmap?.recycle() }
    tabs.clear(); current = -1
}

private fun saveTabs() = store.saveTabs(tabs.map { Pair(it.url, it.title) }, current)

private fun restoreTabs() {
    val (saved, cur) = store.loadTabs()
    if (saved.isEmpty() || (saved.size == 1 && saved[0].first == "https://duckduckgo.com")) { store.clearTabs(); newTab(HOME); return }
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
            runOnUiThread { val i = tabs.indexOf(tab); if (i >= 0) closeTab(i) }
        }

        override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: GeckoSession.ContentDelegate.ContextElement) {
            contextLink = element.linkUri
            contextText = element.textContent
            contextImg = element.srcUri
            if (contextLink != null || contextImg != null || contextText != null) { runOnUiThread { showContextMenu = true } }
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
                
                // THE OFFICIAL GECKOVIEW SCREENSHOT API
                runOnUiThread {
                    try {
                        geckoView.capturePixels().accept({ bitmap ->
                            if (bitmap != null) {
                                // Scale down the bitmap to save memory for the tab grid
                                val scaled = Bitmap.createScaledBitmap(bitmap, bitmap.width / 3, bitmap.height / 3, true)
                                tab.bitmap = scaled
                                runOnUiThread { uiTabCount = tabs.size } // Triggers UI refresh
                            }
                        }, { error ->
                            // Ignore capture errors quietly
                        })
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
            tab.url = url ?: ""
            if (isCurrent()) {
                if (tab.url == "about:blank" && uiUrl == HOME) { uiUrl = HOME } else { runOnUiThread { uiUrl = tab.url } }
            }
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { tab.canBack = canGoBack }
        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) { tab.canForward = canGoForward }

        override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
            val u = request.uri
            if (u.startsWith("gbrowser-dl://")) {
                val videoUrl = Uri.decode(u.removePrefix("gbrowser-dl://"))
                if (videoUrl.startsWith("blob:")) { toast("Cannot download live/DRM streams directly.") } 
                else {
                    val name = URLUtil.guessFileName(videoUrl, null, "video/mp4")
                    ClintDownloadManager.enqueue(context = this@MainActivity, url = videoUrl, filename = name, userAgent = "")
                    toast("Downloading video via ADM...")
                }
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }
            if (u == HOME) return GeckoResult.fromValue(AllowOrDeny.DENY)
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
            i.addCategory(Intent.CATEGORY_BROWSABLE); i.component = null; i.selector = null; startActivity(i)
        } catch (e: Exception) { toast("No app can open this link") }
    }

    private fun copyText(s: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("text", s))
        toast("Copied to clipboard")
    }

    private fun handleDownload(r: WebResponse) {
        val uri = r.uri
        if (uri.contains(".xpi")) { runOnUiThread { installAddon(uri) }; return }
        val ct = r.headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value ?: "application/octet-stream"
        val cd = r.headers.entries.firstOrNull { it.key.equals("content-disposition", true) }?.value
        val name = URLUtil.guessFileName(uri, cd, ct)
        ClintDownloadManager.enqueue(context = this@MainActivity, url = uri, filename = name, userAgent = "")
        toast("Download started via ADM")
    }

    private fun twoLineRow(title: String, sub: String, bold: Boolean): LinearLayout {
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL
        val a = TextView(this); a.text = if (title.isEmpty()) sub else title; a.textSize = 16f; a.maxLines = 1; a.ellipsize = TextUtils.TruncateAt.END; if (bold) a.typeface = Typeface.DEFAULT_BOLD
        val b = TextView(this); b.text = sub; b.textSize = 12f; b.alpha = 0.6f; b.maxLines = 1; b.ellipsize = TextUtils.TruncateAt.END
        col.addView(a); col.addView(b)
        return col
    }

    private fun showHistory() {
        val list = store.history().take(300)
        if (list.isEmpty()) { toast("No history yet"); return }
        val lv = ListView(this); var dlg: AlertDialog? = null
        lv.adapter = object : BaseAdapter() {
            override fun getCount() = list.size
            override fun getItem(position: Int): Any = list[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val v = list[position]; val row = twoLineRow(v.title, v.url, false)
                row.setPadding(dp(16), dp(8), dp(16), dp(8))
                return row
            }
        }
        lv.setOnItemClickListener { _, _, p, _ -> go(list[p].url); dlg?.dismiss() }
        dlg = AlertDialog.Builder(this).setTitle("History").setView(lv)
            .setPositiveButton("Clear all") { _, _ -> store.clearHistory(); toast("History cleared") }
            .setNegativeButton("Close", null).show()
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

    private fun askLink() {
        val input = EditText(this); input.setSingleLine(true); input.hint = "addons.mozilla.org link, add-on name, or .xpi link"
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
        }, { err ->
            toast("Install failed: " + (err?.toString() ?: "unknown error"))
        })
    }
}
