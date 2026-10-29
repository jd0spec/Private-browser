package com.ahmad.gbrowser

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

enum class DlState { QUEUED, RUNNING, PAUSED, DONE, ERROR }

/** One slice of a file. [end] is inclusive, or -1 when the size is unknown. */
class DlPart(val start: Long, val end: Long, var done: Long = 0) {
    @Volatile var finished = false
    @Volatile var conn: HttpURLConnection? = null
}

class DlItem(val id: Long, val url: String, var name: String, val referer: String) {
    var state by mutableStateOf(DlState.QUEUED)
    var total by mutableLongStateOf(-1L)
    var done by mutableLongStateOf(0L)
    var speed by mutableLongStateOf(0L)
    var error by mutableStateOf("")
    var resumable by mutableStateOf(true)
    var savedUri by mutableStateOf("")
    var finalUrl: String = url
    var parts: List<DlPart> = emptyList()
    @Volatile var stop = false
    @Volatile var failed = false
    @Volatile var cancelled = false
}

/**
 * The download engine. All state changes of the queue happen on the main thread;
 * the byte-moving work happens on background threads.
 */
object Dl {
    private const val UA = "Mozilla/5.0 (Android 14; Mobile; rv:150.0) Gecko/150.0 Firefox/150.0"

    lateinit var app: Context
    val items = mutableStateListOf<DlItem>()

    private val pool = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    private var nextId = 1L

    // ---- settings (read from the same prefs as the rest of the app) ----
    private fun prefs() = app.getSharedPreferences("gb", Context.MODE_PRIVATE)
    var maxConcurrent: Int
        get() = prefs().getInt("dl_conc", 3).coerceIn(1, 20)
        set(v) { prefs().edit().putInt("dl_conc", v).apply() }
    var maxParts: Int
        get() = prefs().getInt("dl_parts", 8).coerceIn(1, 32)
        set(v) { prefs().edit().putInt("dl_parts", v).apply() }
    var retryCount: Int            // 0 = unlimited
        get() = prefs().getInt("dl_retry", 10).coerceAtLeast(0)
        set(v) { prefs().edit().putInt("dl_retry", v).apply() }
    var retrySeconds: Int
        get() = prefs().getInt("dl_retry_sec", 5).coerceIn(1, 120)
        set(v) { prefs().edit().putInt("dl_retry_sec", v).apply() }
    var limitKbps: Int             // per download, 0 = unlimited
        get() = prefs().getInt("dl_limit_kbps", 0).coerceAtLeast(0)
        set(v) { prefs().edit().putInt("dl_limit_kbps", v).apply() }

    // ------------------------------------------------------------------
    // Public controls
    // ------------------------------------------------------------------

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        load()
        pump()
    }

    /** If a file with this name is already in the list, adds a number: name (1).ext */
    fun uniqueName(name: String): String {
        fun taken(n: String) = items.any { x -> x.name.equals(n, true) }
        if (!taken(name)) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (taken("$base ($i)$ext")) i++
        return "$base ($i)$ext"
    }

    fun add(name: String, url: String, referer: String) {
        val it = DlItem(nextId++, url, uniqueName(name), referer)
        items.add(0, it)
        save()
        pump()
    }

    fun pause(it: DlItem) {
        when (it.state) {
            DlState.QUEUED -> it.state = DlState.PAUSED
            DlState.RUNNING -> stopWork(it)
            else -> {}
        }
        save()
    }

    fun resume(it: DlItem) {
        if (it.state == DlState.PAUSED || it.state == DlState.ERROR) {
            it.error = ""
            it.failed = false
            it.state = DlState.QUEUED
            save()
            pump()
        }
    }

    fun pauseAll() = items.toList().forEach { pause(it) }

    fun resumeAll() = items.toList().forEach { resume(it) }

    /** Stops the download and removes it from the list. The saved file stays. */
    fun cancel(it: DlItem) {
        it.cancelled = true
        if (it.state == DlState.RUNNING) stopWork(it) else tempFile(it).delete()
        items.remove(it)
        save()
        pump()
    }

    fun clearFinished() {
        items.removeAll { it.state == DlState.DONE }
        save()
    }

    /** Deletes the saved file from the phone and removes the entry from the list. */
    fun deleteFile(it: DlItem) {
        val u = it.savedUri
        if (u.isNotEmpty()) {
            pool.execute {
                try {
                    app.contentResolver.delete(android.net.Uri.parse(u), null, null)
                } catch (e: Exception) {
                }
            }
        }
        items.remove(it)
        save()
    }

    // ------------------------------------------------------------------
    // Queue
    // ------------------------------------------------------------------

    private fun pump() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { pump() }
            return
        }
        var running = items.count { it.state == DlState.RUNNING }
        for (it in items.toList().reversed()) {      // oldest first
            if (running >= maxConcurrent) break
            if (it.state == DlState.QUEUED) {
                it.state = DlState.RUNNING
                it.stop = false
                running++
                pool.execute { run(it) }
            }
        }
        if (items.any { it.state == DlState.RUNNING }) startService()
    }

    private fun startService() {
        try {
            app.startForegroundService(Intent(app, DownloadService::class.java))
        } catch (e: Exception) {
        }
    }

    private fun stopWork(it: DlItem) {
        it.stop = true
        // Closing the sockets wakes up any thread that is waiting for data.
        for (p in it.parts) {
            val c = p.conn
            if (c != null) pool.execute { try { c.disconnect() } catch (e: Exception) { } }
        }
    }

    // ------------------------------------------------------------------
    // The actual download
    // ------------------------------------------------------------------

    private fun tempFile(it: DlItem): File {
        val dir = File(app.getExternalFilesDir(null) ?: app.filesDir, "dl")
        dir.mkdirs()
        return File(dir, "${it.id}.part")
    }

    private fun run(it: DlItem) {
        val tmp = tempFile(it)
        try {
            if (it.parts.isEmpty()) probe(it)
            if (!tmp.exists()) it.parts.forEach { p -> p.done = 0; p.finished = false }
            if (!it.resumable) it.parts.forEach { p -> p.done = 0; p.finished = false }
            RandomAccessFile(tmp, "rw").use { f ->
                if (!it.resumable) f.setLength(0)
                else if (it.total > 0 && f.length() != it.total) f.setLength(it.total)
            }

            val limiter = Limiter()
            val alive = AtomicInteger(0)
            for (p in it.parts) {
                if (p.finished) continue
                alive.incrementAndGet()
                pool.execute {
                    try {
                        worker(it, p, tmp, limiter)
                    } finally {
                        alive.decrementAndGet()
                    }
                }
            }

            // Monitor: progress and speed, until every worker is finished.
            var lastBytes = it.parts.sumOf { p -> p.done }
            var lastTime = System.nanoTime()
            var lastSave = lastTime
            while (alive.get() > 0) {
                Thread.sleep(400)
                val now = System.nanoTime()
                val sum = it.parts.sumOf { p -> p.done }
                val dt = (now - lastTime).coerceAtLeast(1L)
                it.speed = ((sum - lastBytes) * 1_000_000_000L / dt).coerceAtLeast(0L)
                it.done = sum
                lastBytes = sum
                lastTime = now
                if (now - lastSave > 3_000_000_000L) {
                    save()
                    lastSave = now
                }
            }
            it.speed = 0
            it.done = it.parts.sumOf { p -> p.done }

            val complete = !it.failed && it.parts.isNotEmpty() && it.parts.all { p -> p.finished }
            var uri = ""
            if (complete && !it.cancelled) {
                if (it.total <= 0) it.total = it.done
                uri = publish(it, tmp)
            }
            main.post {
                when {
                    it.cancelled -> tmp.delete()
                    it.failed -> it.state = DlState.ERROR
                    complete -> {
                        it.savedUri = uri
                        it.done = it.total
                        it.state = DlState.DONE
                        DlNotify.done(app, it.name)
                    }
                    else -> it.state = DlState.PAUSED
                }
                save()
                pump()
            }
        } catch (e: Exception) {
            it.speed = 0
            it.failed = true
            it.error = e.message ?: "Download failed"
            main.post {
                if (!it.cancelled) it.state = DlState.ERROR
                save()
                pump()
            }
        }
    }

    /** Finds the real address, the file size and whether the server allows resuming. */
    private fun probe(it: DlItem) {
        var u = it.url
        for (hop in 0 until 8) {
            val c = URL(u).openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 15000
                c.readTimeout = 20000
                c.setRequestProperty("User-Agent", UA)
                c.setRequestProperty("Accept-Encoding", "identity")
                c.setRequestProperty("Range", "bytes=0-0")
                if (it.referer.isNotEmpty()) c.setRequestProperty("Referer", it.referer)
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw IOException("Bad redirect")
                    u = URL(URL(u), loc).toString()
                    continue
                }
                when (code) {
                    206 -> {
                        val t = c.getHeaderField("Content-Range")
                            ?.substringAfter('/', "")?.trim()?.toLongOrNull() ?: -1L
                        it.total = t
                        it.resumable = t > 0
                    }
                    200 -> {
                        it.total = c.contentLengthLong
                        it.resumable = false
                    }
                    else -> throw IOException("Server answered HTTP $code")
                }
                it.finalUrl = u
                val n = if (it.resumable && it.total > 0)
                    (it.total / (1L shl 20)).toInt().coerceIn(1, maxParts)
                else 1
                val size = if (it.total > 0) (it.total + n - 1) / n else 0L
                it.parts = (0 until n).map { i ->
                    if (it.total > 0) {
                        val s = i * size
                        DlPart(s, minOf(it.total - 1, s + size - 1))
                    } else DlPart(0, -1)
                }
                return
            } finally {
                try { c.disconnect() } catch (e: Exception) { }
            }
        }
        throw IOException("Too many redirects")
    }

    private fun worker(it: DlItem, p: DlPart, tmp: File, limiter: Limiter) {
        var attempt = 0
        val buf = ByteArray(65536)
        while (!it.stop) {
            val progressBefore = p.done
            var c: HttpURLConnection? = null
            try {
                val from = p.start + p.done
                if (p.end >= 0 && from > p.end) {
                    p.finished = true
                    return
                }
                c = URL(it.finalUrl).openConnection() as HttpURLConnection
                p.conn = c
                c.connectTimeout = 15000
                c.readTimeout = 20000
                c.setRequestProperty("User-Agent", UA)
                c.setRequestProperty("Accept-Encoding", "identity")
                if (it.referer.isNotEmpty()) c.setRequestProperty("Referer", it.referer)
                if (it.resumable) {
                    c.setRequestProperty("Range", "bytes=$from-" + (if (p.end >= 0) p.end.toString() else ""))
                }
                val code = c.responseCode
                if (code in 400..499 && code != 408 && code != 429) {
                    it.error = "Server refused the download (HTTP $code)"
                    it.failed = true
                    it.stop = true
                    return
                }
                if (it.resumable && code != 206) throw IOException("Server ignored the range request (HTTP $code)")
                if (!it.resumable && code != 200) throw IOException("Server answered HTTP $code")

                RandomAccessFile(tmp, "rw").use { raf ->
                    raf.seek(from)
                    val input = c.inputStream
                    while (!it.stop) {
                        val n = input.read(buf)
                        if (n < 0) break
                        val room = if (p.end >= 0) p.end - (p.start + p.done) + 1 else Long.MAX_VALUE
                        val len = minOf(n.toLong(), room).toInt()
                        if (len > 0) {
                            raf.write(buf, 0, len)
                            p.done += len
                        }
                        throttle(it, limiter, n)
                        if (len < n) break
                    }
                }
                if (it.stop) return
                if (p.end < 0 || p.start + p.done > p.end) {
                    p.finished = true
                    return
                }
                throw IOException("Connection closed early")
            } catch (e: Exception) {
                if (it.stop) return
                if (p.done > progressBefore) attempt = 0
                attempt++
                val max = retryCount
                if (max > 0 && attempt > max) {
                    it.error = e.message ?: "Network error"
                    it.failed = true
                    it.stop = true
                    return
                }
                sleepUnlessStopped(it, retrySeconds * 1000L)
            } finally {
                p.conn = null
                try { c?.disconnect() } catch (e: Exception) { }
            }
        }
    }

    // ---- speed limit ----
    private class Limiter {
        private var next = 0L

        @Synchronized
        fun waitNanos(bytes: Int, bytesPerSec: Long): Long {
            val now = System.nanoTime()
            if (next < now) next = now
            val wait = next - now
            next += bytes * 1_000_000_000L / bytesPerSec
            return wait
        }
    }

    private fun throttle(it: DlItem, l: Limiter, n: Int) {
        val bps = limitKbps * 1024L
        if (bps <= 0) return
        val w = l.waitNanos(n, bps)
        if (w > 0) sleepUnlessStopped(it, w / 1_000_000L)
    }

    private fun sleepUnlessStopped(it: DlItem, ms: Long) {
        var left = ms
        while (left > 0 && !it.stop) {
            val s = minOf(left, 200L)
            try { Thread.sleep(s) } catch (e: InterruptedException) { return }
            left -= s
        }
    }

    // ------------------------------------------------------------------
    // Save to the public Downloads folder
    // ------------------------------------------------------------------

    private fun publish(it: DlItem, tmp: File): String {
        val ext = it.name.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        val cv = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, it.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val r = app.contentResolver
        val uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
            ?: throw IOException("Could not create the file in Downloads")
        r.openOutputStream(uri)?.use { out ->
            tmp.inputStream().use { inp -> inp.copyTo(out, 65536) }
        } ?: throw IOException("Could not write the file")
        cv.clear()
        cv.put(MediaStore.Downloads.IS_PENDING, 0)
        r.update(uri, cv, null, null)
        tmp.delete()
        try {
            r.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) it.name = c.getString(0) ?: it.name
            }
        } catch (e: Exception) {
        }
        return uri.toString()
    }

    // ------------------------------------------------------------------
    // Remember the list across restarts
    // ------------------------------------------------------------------

    private fun save() {
        try {
            val arr = JSONArray()
            for (it in items.toList()) {
                val o = JSONObject()
                o.put("id", it.id)
                o.put("url", it.url)
                o.put("final", it.finalUrl)
                o.put("name", it.name)
                o.put("ref", it.referer)
                o.put("state", it.state.name)
                o.put("total", it.total)
                o.put("done", it.done)
                o.put("res", it.resumable)
                o.put("err", it.error)
                o.put("uri", it.savedUri)
                val ps = JSONArray()
                for (p in it.parts) ps.put(JSONArray().put(p.start).put(p.end).put(p.done))
                o.put("parts", ps)
                arr.put(o)
            }
            prefs().edit().putString("dl_items", arr.toString()).putLong("dl_next", nextId).apply()
        } catch (e: Exception) {
        }
    }

    private fun load() {
        try {
            nextId = prefs().getLong("dl_next", 1L)
            val arr = JSONArray(prefs().getString("dl_items", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val it = DlItem(o.getLong("id"), o.getString("url"), o.getString("name"), o.optString("ref"))
                it.finalUrl = o.optString("final", it.url)
                it.total = o.optLong("total", -1L)
                it.done = o.optLong("done", 0L)
                it.resumable = o.optBoolean("res", true)
                it.error = o.optString("err")
                it.savedUri = o.optString("uri")
                val ps = o.optJSONArray("parts") ?: JSONArray()
                it.parts = (0 until ps.length()).map { k ->
                    val a = ps.getJSONArray(k)
                    val p = DlPart(a.getLong(0), a.getLong(1), a.getLong(2))
                    p.finished = p.end >= 0 && p.done >= p.end - p.start + 1
                    p
                }
                val saved = DlState.valueOf(o.getString("state"))
                // Downloads that were running when the app closed continue by themselves.
                it.state = if (saved == DlState.RUNNING) DlState.QUEUED else saved
                items.add(it)
            }
        } catch (e: Exception) {
        }
    }
}
