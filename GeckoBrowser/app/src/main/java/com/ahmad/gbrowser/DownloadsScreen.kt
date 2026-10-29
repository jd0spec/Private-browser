package com.ahmad.gbrowser

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------

private fun dlSize(b: Long): String = when {
    b < 0 -> "?"
    b >= 1_000_000_000L -> String.format("%.2f GB", b / 1e9)
    b >= 1_000_000L -> String.format("%.1f MB", b / 1e6)
    b >= 1_000L -> "${b / 1000} KB"
    else -> "$b B"
}

private fun dlSpeed(b: Long): String =
    if (b >= 1_000_000L) String.format("%.1f MB/s", b / 1e6) else "${b / 1000} KB/s"

private fun dlEta(sec: Long): String = when {
    sec < 0 -> "-"
    sec < 60 -> "${sec}s"
    sec < 3600 -> "${sec / 60}m ${sec % 60}s"
    else -> "${sec / 3600}h ${(sec % 3600) / 60}m"
}

private fun freeBytes(): Long = try {
    StatFs(Environment.getExternalStorageDirectory().path).availableBytes
} catch (e: Exception) {
    0L
}

private fun extOf(name: String): String {
    val e = name.substringAfterLast('.', "")
    return if (e.isEmpty() || e.length > 4) "FILE" else e.uppercase()
}

@Composable
private fun PauseGlyph(color: Color, h: Dp = 16.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(width = 4.dp, height = h).clip(RoundedCornerShape(1.dp)).background(color))
        Box(Modifier.size(width = 4.dp, height = h).clip(RoundedCornerShape(1.dp)).background(color))
    }
}

@Composable
private fun Ring(frac: Float, ext: String) {
    val cs = MaterialTheme.colorScheme
    val track = cs.surfaceVariant
    val prog = cs.primary
    Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 5.dp.toPx()
            val topLeft = Offset(w / 2, w / 2)
            val sz = Size(size.width - w, size.height - w)
            drawArc(
                color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = sz, style = Stroke(width = w)
            )
            drawArc(
                color = prog, startAngle = -90f, sweepAngle = 360f * frac.coerceIn(0f, 1f), useCenter = false,
                topLeft = topLeft, size = sz, style = Stroke(width = w, cap = StrokeCap.Round)
            )
        }
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Text(
                ext, style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant, fontWeight = FontWeight.Bold, maxLines = 1
            )
        }
    }
}

@Composable
private fun Spark(values: List<Long>, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val n = 60
        val maxV = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        val stepX = size.width / (n - 1)
        drawLine(base, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
        if (values.size >= 2) {
            val path = Path()
            val off = n - values.size
            values.forEachIndexed { i, v ->
                val x = (off + i) * stepX
                val y = size.height - (v.toFloat() / maxV) * (size.height - 10f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, line, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun Chip(text: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface) {
        Text(
            text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun Stat(big: String, small: String) {
    Column {
        Text(big, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(small, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TabPill(label: String, count: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fg = if (selected) cs.onPrimaryContainer else cs.onSurface
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) cs.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = fg, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("$count", color = fg.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------------
// Queue tab pieces
// ---------------------------------------------------------------------

@Composable
private fun SpeedHeader(queue: List<DlItem>, hist: List<Long>) {
    val cs = MaterialTheme.colorScheme
    val running = queue.filter { it.state == DlState.RUNNING }
    val speed = running.sumOf { it.speed }
    val waiting = queue.count { it.state == DlState.QUEUED }
    val left = queue
        .filter { (it.state == DlState.RUNNING || it.state == DlState.QUEUED) && it.total > 0 }
        .sumOf { (it.total - it.done).coerceAtLeast(0L) }
    val eta = if (speed > 0) left / speed else -1L
    val free = remember(hist.size) { freeBytes() }
    val peak = hist.maxOrNull() ?: 0L
    val big = if (speed >= 1_000_000L) String.format("%.1f", speed / 1e6) else "${speed / 1000}"
    val unit = if (speed >= 1_000_000L) "MB/s" else "KB/s"
    val anyActive = queue.any { it.state == DlState.RUNNING || it.state == DlState.QUEUED }
    val anyPaused = queue.any { it.state == DlState.PAUSED }

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(cs.primary))
            Spacer(Modifier.width(10.dp))
            Text("Live speed", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(18.dp), color = cs.surfaceContainerHigh) {
                Text(
                    "${dlSize(free)} free",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(big, fontSize = 46.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text(unit, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            Spacer(Modifier.weight(1f))
            Text(
                "Peak ${dlSpeed(peak)}", color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Spark(hist, Modifier.fillMaxWidth().height(70.dp))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("${running.size} active", "$waiting queued")
            Stat(dlSize(left), "left in total")
            Stat(dlEta(eta), "until all done")
        }
        Spacer(Modifier.height(14.dp))
        if (anyActive) {
            Button(onClick = { Dl.pauseAll() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                PauseGlyph(MaterialTheme.colorScheme.onPrimary, 14.dp)
                Spacer(Modifier.width(10.dp))
                Text("Pause all", fontWeight = FontWeight.Bold)
            }
        } else if (anyPaused) {
            Button(onClick = { Dl.resumeAll() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Resume all", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DlCard(d: DlItem) {
    val cs = MaterialTheme.colorScheme
    val done = d.done
    val total = d.total
    val st = d.state
    val running = st == DlState.RUNNING
    val frac = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    val eta = if (running && d.speed > 0 && total > 0) (total - done) / d.speed else -1L
    val parts = d.parts
    val finished = parts.count { it.finished }
    val connections = if (running) parts.count { !it.finished } else 0

    Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring(frac, extOf(d.name))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        d.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (total > 0) {
                            Text(
                                "${(frac * 100).toInt()}%", color = cs.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (total > 0) "${dlSize(done)} of ${dlSize(total)}" else dlSize(done),
                            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(cs.surfaceContainerHighest)
                        .clickable {
                            when (st) {
                                DlState.RUNNING, DlState.QUEUED -> Dl.pause(d)
                                DlState.PAUSED, DlState.ERROR -> Dl.resume(d)
                                else -> {}
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when (st) {
                        DlState.RUNNING, DlState.QUEUED -> PauseGlyph(cs.onSurface)
                        DlState.ERROR -> Icon(Icons.Default.Refresh, "Retry")
                        else -> Icon(Icons.Default.PlayArrow, "Resume")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when (st) {
                        DlState.RUNNING -> {
                            Chip(dlSpeed(d.speed))
                            if (eta >= 0) Chip("${dlEta(eta)} left")
                        }
                        DlState.QUEUED -> Chip("Waiting in queue")
                        DlState.PAUSED -> Chip("Paused")
                        DlState.ERROR -> Chip("Failed")
                        else -> {}
                    }
                    if (parts.isNotEmpty()) Chip(if (d.resumable) "Resumable" else "Single connection")
                }
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable { Dl.cancel(d) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, "Cancel", tint = cs.onSurfaceVariant)
                }
            }

            if (st == DlState.ERROR && d.error.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(d.error, color = cs.error, style = MaterialTheme.typography.bodySmall)
            }

            if (parts.size > 1) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    parts.forEach { p ->
                        val len = if (p.end >= 0) p.end - p.start + 1 else 0L
                        val pf = if (p.finished) 1f
                        else if (len > 0) (p.done.toFloat() / len).coerceIn(0f, 1f)
                        else 0f
                        Box(
                            Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(cs.surfaceVariant)
                        ) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(pf).background(cs.primary))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Parts: $finished/${parts.size} · Connections: $connections",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------
// Library tab piece
// ---------------------------------------------------------------------

@Composable
private fun LibCard(d: DlItem, onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable { onOpen() }
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    extOf(d.name), color = cs.onPrimaryContainer,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(dlSize(d.total), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete file") }
        }
    }
}

// ---------------------------------------------------------------------
// The screen
// ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onClose: () -> Unit,
    onAdd: (String) -> Unit,
    onOpenFile: (DlItem) -> Unit,
    onShareFile: (DlItem) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<DlItem?>(null) }
    val hist = remember { mutableStateListOf<Long>() }

    LaunchedEffect(Unit) {
        while (true) {
            val s = Dl.items.filter { it.state == DlState.RUNNING }.sumOf { it.speed }
            hist.add(s)
            if (hist.size > 60) hist.removeAt(0)
            delay(1000)
        }
    }

    val queue = Dl.items
        .filter { it.state != DlState.DONE }
        .sortedBy { if (it.state == DlState.RUNNING) 0 else 1 }
    val library = Dl.items.filter { it.state == DlState.DONE }

    Scaffold(
        containerColor = cs.surface,
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (tab == 1 && library.isNotEmpty()) {
                        IconButton(onClick = { Dl.clearFinished() }) { Icon(Icons.Default.Delete, "Clear list") }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add download") }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                TabPill("Queue", queue.size, tab == 0, Modifier.weight(1f)) { tab = 0 }
                TabPill("Library", library.size, tab == 1, Modifier.weight(1f)) { tab = 1 }
            }

            if (tab == 0) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { SpeedHeader(queue, hist.toList()) }
                    if (queue.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                                Text("No active downloads", color = cs.onSurfaceVariant)
                            }
                        }
                    } else {
                        item {
                            Row(
                                Modifier.padding(start = 8.dp, top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Downloading", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(10.dp))
                                Surface(shape = RoundedCornerShape(12.dp), color = cs.surfaceContainerHigh) {
                                    Text(
                                        "${queue.size}",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        items(queue, key = { it.id }) { d -> DlCard(d) }
                    }
                }
            } else {
                if (library.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No finished downloads yet", color = cs.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(library, key = { it.id }) { d ->
                            LibCard(
                                d,
                                onOpen = { onOpenFile(d) },
                                onShare = { onShareFile(d) },
                                onDelete = { deleting = d }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add download") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    singleLine = true, label = { Text("Link") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) {
                        onAdd(text.trim())
                        showAdd = false
                        tab = 0
                    }
                }) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }

    val del = deleting
    if (del != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete file?") },
            text = { Text("${del.name} will be deleted from your phone.") },
            confirmButton = {
                TextButton(onClick = {
                    Dl.deleteFile(del)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------------
// "Download this file?" question
// ---------------------------------------------------------------------

class PendingDl(val name: String, val url: String, val referer: String, val exists: Boolean)

object DlPrompt {
    var pending by mutableStateOf<PendingDl?>(null)
}

@Composable
fun DownloadPromptHost(onCopy: (String) -> Unit, onStarted: (String) -> Unit) {
    val p = DlPrompt.pending ?: return
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = { DlPrompt.pending = null },
        title = { Text(if (p.exists) "Already downloaded" else "Download file?") },
        text = {
            Column {
                Text(p.name, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (p.exists) "This file is already in your library. Re-download saves another copy with a number added to its name."
                    else p.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Dl.add(p.name, p.url, p.referer)
                DlPrompt.pending = null
                onStarted(p.name)
            }) { Text(if (p.exists) "Re-download" else "Download") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onCopy(p.url)
                    DlPrompt.pending = null
                }) { Text("Copy link") }
                TextButton(onClick = { DlPrompt.pending = null }) { Text("Cancel") }
            }
        }
    )
}
