package com.ahmad.gbrowser

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import android.graphics.Bitmap
import androidx.compose.animation.core.animate
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Everything the on-screen bars need to know, kept in one place. */
class Chrome {
    var url by mutableStateOf("")
    var home by mutableStateOf(true)
    var loading by mutableIntStateOf(0)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var tabCount by mutableIntStateOf(1)
    var editing by mutableStateOf(false)
    var tabKey by mutableIntStateOf(0)
    var slideDir by mutableIntStateOf(0)
    var menu by mutableStateOf(false)
    var screen by mutableIntStateOf(0)
    var historyVersion by mutableIntStateOf(0)
    var tabIndex by mutableIntStateOf(0)
    var engineName by mutableStateOf("DuckDuckGo")
    val tabList = mutableStateListOf<TabInfo>()
    val addons = mutableStateListOf<AddonInfo>()
    var engineIndex by mutableIntStateOf(0)
    val autoFlags = mutableStateListOf(false, false, false, false)
    var undoTitle by mutableStateOf<String?>(null)
    var undoKey by mutableIntStateOf(0)
    var ctxTitle by mutableStateOf("")
    var ctx by mutableStateOf<List<CtxAction>?>(null)
}

class CtxAction(val glyph: String, val label: String, val run: () -> Unit)

class TabInfo(val id: Int, val title: String, val url: String, val home: Boolean, val thumb: Bitmap? = null)

class AddonInfo(
    val name: String,
    val optionsUrl: String?,
    val version: String = "",
    val enabled: Boolean = true,
    val icon: Bitmap? = null
)

fun displayUrl(u: String): String {
    if (u == "about:blank") return ""
    return u.removePrefix("https://").removePrefix("http://").removeSuffix("/")
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Horizontal swipe anywhere on the element; taps on children still work. */
private fun Modifier.swipeTabs(enabled: Boolean, onLeft: () -> Unit, onRight: () -> Unit): Modifier =
    if (!enabled) this else this.pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        val thr = 40.dp.toPx()
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var total = 0f
            var dragging = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: break
                if (!change.pressed) break
                total += change.positionChange().x
                if (!dragging && kotlin.math.abs(total) > slop) dragging = true
                if (dragging) change.consume()
            }
            if (dragging) {
                if (total < -thr) onLeft() else if (total > thr) onRight()
            }
        }
    }

@Composable
fun BottomBar(
    ch: Chrome,
    onGo: (String) -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var gotFocus by remember { mutableStateOf(false) }
    val slide = remember { Animatable(1f) }
    val badge = remember { Animatable(1f) }
    var dir by remember { mutableIntStateOf(0) }

    LaunchedEffect(ch.tabKey, ch.tabCount) {
        dir = ch.slideDir
        ch.slideDir = 0
        slide.snapTo(0f)
        slide.animateTo(1f, tween(300))
    }
    LaunchedEffect(ch.tabCount) {
        badge.snapTo(1.35f)
        badge.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 400f))
    }

    LaunchedEffect(ch.editing) {
        if (ch.editing) {
            field = TextFieldValue(ch.url, TextRange(0, ch.url.length))
            requester.requestFocus()
        }
    }

    Column(Modifier.fillMaxWidth().background(cs.surface).swipeTabs(!ch.editing, onSwipeLeft, onSwipeRight)) {
        if (ch.loading in 1..99) {
            LinearProgressIndicator(
                progress = { ch.loading / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp)
            )
        }
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = cs.surfaceContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth()
        ) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!ch.editing) {
                    IconButton(onClick = onHome) { Icon(Icons.Default.Home, "Home") }
                }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = cs.surfaceContainerLowest,
                    modifier = Modifier.weight(1f).height(48.dp).padding(horizontal = 4.dp).graphicsLayer {
                        val sc = 0.94f + 0.06f * slide.value
                        scaleX = sc
                        scaleY = sc
                    }
                ) {
                    if (ch.editing) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicTextField(
                                value = field,
                                onValueChange = { field = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f).focusRequester(requester).onFocusChanged { f ->
                                    if (f.isFocused) {
                                        gotFocus = true
                                    } else if (gotFocus) {
                                        gotFocus = false
                                        ch.editing = false
                                    }
                                },
                                textStyle = TextStyle(color = cs.onSurface, fontSize = 16.sp),
                                cursorBrush = SolidColor(cs.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                                keyboardActions = KeyboardActions(onGo = {
                                    ch.editing = false
                                    focus.clearFocus()
                                    onGo(field.text)
                                }),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (field.text.isEmpty()) {
                                            Text("Search or enter address", color = cs.onSurfaceVariant, fontSize = 16.sp)
                                        }
                                        inner()
                                    }
                                }
                            )
                        }
                    } else {
                        Row(
                            Modifier.fillMaxSize()
                                .pointerInput(Unit) { detectTapGestures(onTap = { ch.editing = true }) }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (ch.url.isEmpty()) "Search or enter address" else displayUrl(ch.url),
                                color = if (ch.url.isEmpty()) cs.onSurfaceVariant else cs.onSurface,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).graphicsLayer {
                                    translationX = dir * (1f - slide.value) * 80.dp.toPx()
                                    alpha = 0.3f + 0.7f * slide.value
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                if (ch.url.startsWith("https")) Icons.Default.Lock else Icons.Default.Search,
                                null, Modifier.size(18.dp), tint = cs.onSurfaceVariant
                            )
                        }
                    }
                }
                if (!ch.editing) {
                    IconButton(onClick = onTabs) {
                        Box(
                            Modifier.size(26.dp).graphicsLayer {
                                scaleX = badge.value
                                scaleY = badge.value
                            }.border(2.dp, LocalContentColor.current, RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(ch.tabCount.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = onMenu) { Icon(Icons.Default.MoreVert, "Menu") }
                }
            }
        }
    }
}

private fun newId(): String = java.util.UUID.randomUUID().toString()

private fun fixUrl(u: String): String {
    val t = u.trim()
    if (t.isEmpty()) return ""
    return if (t.contains("://")) t else "https://$t"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(
    title: String,
    count: Int,
    folder: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLong: (() -> Unit)?
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (folder) {
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).background(cs.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (r in 0..1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (c in 0..1) {
                                val filled = (r * 2 + c) < count
                                Box(
                                    Modifier.size(15.dp).clip(RoundedCornerShape(5.dp)).background(
                                        if (filled) cs.primary else cs.onSecondaryContainer.copy(alpha = 0.15f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = cs.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title, style = MaterialTheme.typography.labelMedium, color = cs.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AddTile(modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable { onClick() }.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, "Add", tint = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Text("Add", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun ListDialog(title: String, options: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (label, action) ->
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { action() }.padding(vertical = 14.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EditDialog(item: HomeItem, isNew: Boolean, onSave: (HomeItem) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(item.title) }
    var url by remember { mutableStateOf(item.url) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (item.folder) { if (isNew) "New folder" else "Rename folder" }
                else { if (isNew) "Add shortcut" else "Edit shortcut" }
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Name") }, singleLine = true
                )
                if (!item.folder) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text("Address") }, singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val u = fixUrl(url)
                if (item.folder) {
                    if (title.isNotBlank()) onSave(item.copy(title = title.trim()))
                } else if (u.isNotEmpty()) {
                    onSave(item.copy(title = title.trim().ifEmpty { displayUrl(u) }, url = u))
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun HomeScreen(store: Store, onGo: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val recent = remember { store.history().take(5) }
    val items = remember { mutableStateListOf<HomeItem>().apply { addAll(store.loadHome()) } }
    var q by remember { mutableStateOf("") }
    var showRecent by remember { mutableStateOf(store.showRecent()) }
    var showSettings by remember { mutableStateOf(false) }
    var addChoice by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<HomeItem?>(null) }
    var draft by remember { mutableStateOf<HomeItem?>(null) }
    var moveItem by remember { mutableStateOf<HomeItem?>(null) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var pendingMove by remember { mutableStateOf<String?>(null) }
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = if (hour < 12) "Good morning" else if (hour < 18) "Good afternoon" else "Good evening"

    fun persist() {
        store.saveHome(items.toList())
    }

    fun upsert(h: HomeItem) {
        val i = items.indexOfFirst { x -> x.id == h.id }
        if (i >= 0) items[i] = h else items.add(h)
        persist()
    }

    fun moveTo(h: HomeItem, parent: String) {
        val i = items.indexOfFirst { x -> x.id == h.id }
        if (i >= 0) items[i] = items[i].copy(parent = parent)
        persist()
    }

    fun remove(h: HomeItem) {
        items.removeAll { x -> x.id == h.id }
        if (h.folder) {
            for (i in items.indices) {
                if (items[i].parent == h.id) items[i] = items[i].copy(parent = "")
            }
        }
        persist()
    }

    Column(
        Modifier.fillMaxSize().background(cs.surface).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Home settings") }
        }
        Spacer(Modifier.height(16.dp))
        Text("Gecko", style = MaterialTheme.typography.displayMedium, color = cs.primary, fontWeight = FontWeight.Bold)
        Text(greeting, style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))

        Surface(
            shape = RoundedCornerShape(32.dp),
            color = cs.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = cs.onSurface, fontSize = 17.sp),
                    cursorBrush = SolidColor(cs.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                    keyboardActions = KeyboardActions(onGo = { onGo(q) }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (q.isEmpty()) {
                                Text("Search or enter address", color = cs.onSurfaceVariant, fontSize = 17.sp)
                            }
                            inner()
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        val top = items.filter { it.parent.isEmpty() }
        val cells: List<HomeItem?> = top + listOf<HomeItem?>(null)
        cells.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { c ->
                    if (c == null) {
                        AddTile(Modifier.weight(1f)) { addChoice = true }
                    } else {
                        Tile(
                            title = c.title,
                            count = items.count { x -> x.parent == c.id },
                            folder = c.folder,
                            modifier = Modifier.weight(1f),
                            onClick = { if (c.folder) openFolder = c.id else onGo(c.url) },
                            onLong = { menuFor = c }
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        if (showRecent && recent.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Recently visited",
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            recent.forEach { v ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cs.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp))
                        .clickable { onGo(v.url) }
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            v.title.ifEmpty { displayUrl(v.url) },
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            displayUrl(v.url),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    // ---------- dialogs ----------

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Home screen") },
            text = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Show recently visited", Modifier.weight(1f))
                    Switch(checked = showRecent, onCheckedChange = {
                        showRecent = it
                        store.setShowRecent(it)
                    })
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } }
        )
    }

    if (addChoice) {
        val opts = listOf<Pair<String, () -> Unit>>(
            "Shortcut" to {
                draft = HomeItem(newId(), false, "", "", "")
                addChoice = false
            },
            "Folder" to {
                draft = HomeItem(newId(), true, "", "", "")
                addChoice = false
            }
        )
        ListDialog("Add to home screen", opts) { addChoice = false }
    }

    menuFor?.let { m ->
        val opts = ArrayList<Pair<String, () -> Unit>>()
        if (m.folder) {
            opts.add("Rename" to { draft = m; menuFor = null })
            opts.add("Remove folder" to { remove(m); menuFor = null })
        } else {
            opts.add("Edit name and address" to { draft = m; menuFor = null })
            opts.add("Move to folder" to { moveItem = m; menuFor = null })
            if (m.parent.isNotEmpty()) opts.add("Move out of folder" to { moveTo(m, ""); menuFor = null })
            opts.add("Remove" to { remove(m); menuFor = null })
        }
        ListDialog(m.title, opts) { menuFor = null }
    }

    moveItem?.let { m ->
        val opts = ArrayList<Pair<String, () -> Unit>>()
        opts.add("Home screen" to { moveTo(m, ""); moveItem = null })
        items.filter { it.folder }.forEach { f ->
            opts.add(f.title to { moveTo(m, f.id); moveItem = null })
        }
        opts.add("＋ New folder…" to {
            pendingMove = m.id
            draft = HomeItem(newId(), true, "", "", "")
            moveItem = null
        })
        ListDialog("Move to", opts) { moveItem = null }
    }

    draft?.let { d ->
        val isNew = items.none { x -> x.id == d.id }
        EditDialog(
            item = d,
            isNew = isNew,
            onSave = { saved ->
                upsert(saved)
                val pm = pendingMove
                if (isNew && saved.folder && pm != null) {
                    val target = items.firstOrNull { x -> x.id == pm }
                    if (target != null) moveTo(target, saved.id)
                }
                pendingMove = null
                draft = null
            },
            onDismiss = {
                draft = null
                pendingMove = null
            }
        )
    }

    openFolder?.let { fid ->
        val f = items.firstOrNull { x -> x.id == fid }
        if (f == null) {
            openFolder = null
        } else {
            val kids = items.filter { x -> x.parent == fid }
            AlertDialog(
                onDismissRequest = { openFolder = null },
                title = { Text(f.title) },
                text = {
                    if (kids.isEmpty()) {
                        Text("Empty. Long-press a shortcut on the home screen and choose Move to folder.")
                    } else {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            kids.chunked(3).forEach { row ->
                                Row(Modifier.fillMaxWidth()) {
                                    row.forEach { k ->
                                        Tile(
                                            title = k.title, count = 0, folder = false,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onGo(k.url) },
                                            onLong = { menuFor = k }
                                        )
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { openFolder = null }) { Text("Close") } },
                dismissButton = { TextButton(onClick = { draft = f }) { Text("Rename") } }
            )
        }
    }
}


// =====================================================================
// Menu (bottom sheet)
// =====================================================================

@Composable
private fun PillRow(
    icon: ImageVector,
    label: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable { onClick() }
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = cs.onPrimaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Text(
                label, style = MaterialTheme.typography.titleMedium, color = cs.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            if (trailing != null) trailing()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(
    ch: Chrome,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onReload: () -> Unit,
    onAction: (Int) -> Unit,
    onAddonSettings: (String?) -> Unit,
    onAddonClick: (Int) -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val state = rememberModalBottomSheetState()
    var extOpen by remember { mutableStateOf(false) }
    val pick = { id: Int ->
        ch.menu = false
        onAction(id)
    }

    ModalBottomSheet(
        onDismissRequest = { ch.menu = false },
        sheetState = state,
        containerColor = cs.surface
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = cs.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { ch.menu = false; onBack() }, enabled = ch.canBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                    IconButton(onClick = { ch.menu = false; onForward() }, enabled = ch.canForward) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward")
                    }
                    IconButton(onClick = { ch.menu = false; onHome() }) {
                        Icon(Icons.Default.Home, "Home")
                    }
                    IconButton(onClick = { ch.menu = false; onReload() }) {
                        Icon(Icons.Default.Refresh, "Reload")
                    }
                }
            }

            PillRow(
                Icons.Default.Build, "Extensions",
                trailing = { Icon(if (extOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null) }
            ) { extOpen = !extOpen }

            if (extOpen) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = cs.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        if (ch.addons.isEmpty()) {
                            Text(
                                "No extensions installed yet",
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        ch.addons.forEachIndexed { idx, a ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { ch.menu = false; onAddonClick(idx) }
                                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AddonIcon(a, 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    a.name, style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { ch.menu = false; onAddonSettings(a.optionsUrl) }) {
                                    Icon(Icons.Default.Settings, "Extension settings")
                                }
                            }
                        }
                        TextButton(onClick = { pick(2) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Icon(Icons.Default.Settings, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Manage")
                        }
                    }
                }
            }

            PillRow(Icons.Default.KeyboardArrowDown, "Downloads") { pick(6) }
            PillRow(Icons.Default.Add, "New tab") { pick(0) }
            PillRow(Icons.Default.Share, "Share URL") { pick(8) }
            PillRow(Icons.AutoMirrored.Filled.List, "History") { pick(1) }
            PillRow(Icons.Default.Close, "Exit") { pick(7) }
            PillRow(Icons.Default.Settings, "Settings") { pick(9) }
        }
    }
}

// =====================================================================
// Tabs screen
// =====================================================================

@Composable
private fun TabCard(
    t: TabInfo,
    selected: Boolean,
    modifier: Modifier,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var dragX by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(1) }
    val closeCb by rememberUpdatedState(onClose)
    val label = if (t.home) "New tab" else t.title.ifEmpty { displayUrl(t.url) }
    val barColor = if (selected) cs.primary else cs.surfaceContainerHigh
    val onBar = if (selected) cs.onPrimary else cs.onSurface
    val bmp = remember(t.thumb) { t.thumb?.asImageBitmap() }

    Column(
        modifier
            .onSizeChanged { width = it.width }
            .graphicsLayer {
                translationX = dragX
                compositingStrategy = CompositingStrategy.ModulateAlpha
                alpha = 1f - (kotlin.math.abs(dragX) / width).coerceIn(0f, 1f) * 0.7f
            }
            .clip(RoundedCornerShape(26.dp))
            .background(barColor)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val x = dragX
                        if (kotlin.math.abs(x) > width * 0.35f) {
                            scope.launch {
                                animate(x, if (x > 0) width * 1.3f else -width * 1.3f, animationSpec = tween(140)) { v, _ ->
                                    dragX = v
                                }
                                closeCb()
                            }
                        } else {
                            scope.launch {
                                animate(x, 0f, animationSpec = spring()) { v, _ -> dragX = v }
                            }
                        }
                    },
                    onDragCancel = { dragX = 0f }
                ) { change, dx ->
                    change.consume()
                    dragX += dx
                }
            }
            .clickable { onSelect() }
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(onBar.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(label.take(1).uppercase(), color = onBar, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                label, style = MaterialTheme.typography.titleSmall, color = onBar,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, "Close tab", tint = onBar)
            }
        }
        Box(
            Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp).fillMaxWidth().aspectRatio(0.8f)
                .clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainerLowest),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null && !t.home) {
                Image(
                    bitmap = bmp, contentDescription = null, contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Search, null, tint = cs.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (t.home) "New tab" else displayUrl(t.url),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UndoBar(ch: Chrome, onUndo: () -> Unit, onExpire: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var secs by remember { mutableIntStateOf(3) }
    LaunchedEffect(ch.undoKey) {
        if (ch.undoTitle != null) {
            for (s in 3 downTo 1) {
                secs = s
                delay(1000)
            }
            ch.undoTitle = null
            onExpire()
        }
    }
    val title = ch.undoTitle
    if (title != null) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = cs.inverseSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
        ) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Closed " + title, color = cs.inverseOnSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    ch.undoTitle = null
                    onUndo()
                }) {
                    Text("UNDO  " + secs, color = cs.inversePrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TabsScreen(
    ch: Chrome,
    onClose: () -> Unit,
    onSelect: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onNew: () -> Unit,
    onCloseAll: () -> Unit,
    onUndo: () -> Unit,
    onUndoExpire: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val gridState = rememberLazyGridState()
    LaunchedEffect(Unit) {
        if (ch.tabIndex in ch.tabList.indices) gridState.scrollToItem(ch.tabIndex)
    }
    Column(Modifier.fillMaxSize().background(cs.surface)) {
        Text(
            "Tabs  \u00B7  " + ch.tabList.size,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
        )
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom)
        ) {
            itemsIndexed(ch.tabList, key = { _, t -> t.id }) { _, t ->
                TabCard(
                    t = t,
                    selected = ch.tabList.indexOfFirst { x -> x.id == t.id } == ch.tabIndex,
                    modifier = Modifier.animateItem(),
                    onSelect = { onSelect(ch.tabList.indexOfFirst { x -> x.id == t.id }) },
                    onClose = { onCloseTab(ch.tabList.indexOfFirst { x -> x.id == t.id }) }
                )
            }
        }
        UndoBar(ch, onUndo, onUndoExpire)
        Surface(
            color = cs.surfaceContainer,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCloseAll) { Text("Close all") }
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = cs.primary,
                    modifier = Modifier.size(width = 96.dp, height = 56.dp).clip(RoundedCornerShape(22.dp))
                        .clickable { onNew() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, "New tab", tint = cs.onPrimary)
                    }
                }
                TextButton(onClick = onClose) { Text("Done") }
            }
        }
    }
}

// =====================================================================
// History screen
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    ch: Chrome,
    store: Store,
    onClose: () -> Unit,
    onOpen: (String) -> Unit,
    onClear: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val list = remember(ch.historyVersion) { store.history().take(300) }
    var confirm by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = cs.surface,
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (list.isNotEmpty()) {
                        IconButton(onClick = { confirm = true }) { Icon(Icons.Default.Delete, "Clear history") }
                    }
                }
            )
        }
    ) { pad ->
        if (list.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No history yet", color = cs.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list) { v ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = cs.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onOpen(v.url) }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(cs.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    v.title.ifEmpty { displayUrl(v.url) }.take(1).uppercase(),
                                    color = cs.onPrimaryContainer, fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    v.title.ifEmpty { displayUrl(v.url) },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    displayUrl(v.url),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear all history?") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onClear()
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}


// =====================================================================
// Settings screen
// =====================================================================

private val clearLabels = listOf("History", "Cookies & site data", "Open tabs", "Cache")

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onChange(!checked) }
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = onChange)
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    ch: Chrome,
    engineNames: List<String>,
    onClose: () -> Unit,
    onEngine: (Int) -> Unit,
    onAuto: (Int, Boolean) -> Unit,
    onClearNow: (BooleanArray) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val sel = remember { mutableStateListOf(true, true, false, true) }
    var confirm by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = cs.surface,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionTitle("Search engine")
            Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                engineNames.forEachIndexed { i, n ->
                    FilterChip(selected = ch.engineIndex == i, onClick = { onEngine(i) }, label = { Text(n) })
                }
            }
            SectionTitle("Clear automatically when you exit")
            clearLabels.forEachIndexed { i, n -> SwitchRow(n, ch.autoFlags[i]) { onAuto(i, it) } }
            SectionTitle("Clear data now")
            clearLabels.forEachIndexed { i, n -> CheckRow(n, sel[i]) { sel[i] = it } }
            Button(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Clear selected data")
            }
            SectionTitle("About")
            Text(
                "Gecko Browser, powered by the Firefox engine (GeckoView).",
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear selected data?") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onClearNow(sel.toBooleanArray())
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

// =====================================================================
// Add-ons screen
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonsScreen(
    ch: Chrome,
    recommended: List<Pair<String, String>>,
    onClose: () -> Unit,
    onToggle: (Int, Boolean) -> Unit,
    onRemove: (Int) -> Unit,
    onOpenSettings: (String?) -> Unit,
    onInstall: (String) -> Unit,
    onPickFile: () -> Unit,
    onOpen: (Int) -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    var link by remember { mutableStateOf("") }
    var removeIdx by remember { mutableIntStateOf(-1) }
    Scaffold(
        containerColor = cs.surface,
        topBar = {
            TopAppBar(
                title = { Text("Add-ons") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SectionTitle("Installed") }
            if (ch.addons.isEmpty()) {
                item {
                    Text(
                        "Nothing installed yet. Pick one below.",
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
            itemsIndexed(ch.addons) { i, a ->
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = cs.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AddonIcon(a, 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    a.name, style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                if (a.version.isNotEmpty()) {
                                    Text(
                                        "Version " + a.version,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = a.enabled,
                                onCheckedChange = { onToggle(i, it) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        Row {
                            TextButton(onClick = { onOpen(i) }) { Text("Open") }
                            val u = a.optionsUrl
                            if (!u.isNullOrEmpty()) {
                                TextButton(onClick = { onOpenSettings(u) }) { Text("Settings") }
                            }
                            TextButton(onClick = { removeIdx = i }) { Text("Remove") }
                        }
                    }
                }
            }
            item { SectionTitle("Add an extension") }
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = cs.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = link,
                            onValueChange = { link = it },
                            label = { Text("Link, name or .xpi address") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                onInstall(link)
                                link = ""
                            }) { Text("Install") }
                            FilledTonalButton(onClick = onPickFile) { Text("Choose .xpi file") }
                        }
                    }
                }
            }
            item { SectionTitle("Recommended") }
            items(recommended) { r ->
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = cs.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(r.first, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = { onInstall(r.second) }) { Text("Add") }
                    }
                }
            }
        }
    }
    if (removeIdx in ch.addons.indices) {
        AlertDialog(
            onDismissRequest = { removeIdx = -1 },
            title = { Text("Remove " + ch.addons[removeIdx].name + "?") },
            confirmButton = {
                TextButton(onClick = {
                    val i = removeIdx
                    removeIdx = -1
                    onRemove(i)
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removeIdx = -1 }) { Text("Cancel") } }
        )
    }
}


// =====================================================================
// Long-press menu for links and images
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextSheet(ch: Chrome, actions: List<CtxAction>) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = { ch.ctx = null }, containerColor = cs.surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                ch.ctxTitle,
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            actions.forEach { a ->
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = cs.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable {
                        ch.ctx = null
                        a.run()
                    }
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(cs.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(a.glyph, color = cs.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(a.label, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    }
                }
            }
        }
    }
}


@Composable
private fun AddonIcon(a: AddonInfo, size: Dp) {
    val cs = MaterialTheme.colorScheme
    val bmp = remember(a.icon) { a.icon?.asImageBitmap() }
    if (bmp != null) {
        Image(
            bitmap = bmp, contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(cs.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(a.name.take(1).uppercase(), color = cs.onPrimaryContainer, fontWeight = FontWeight.Bold)
        }
    }
}
