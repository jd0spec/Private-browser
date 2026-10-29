package com.ahmad.gbrowser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.IBinder
import android.os.Looper

object DlNotify {
    const val CH_PROGRESS = "dl_progress"
    const val CH_DONE = "dl_done"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "Downloads in progress", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun openApp(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun done(ctx: Context, name: String) {
        try {
            channels(ctx)
            val n = Notification.Builder(ctx, CH_DONE)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download finished")
                .setContentText(name)
                .setContentIntent(openApp(ctx))
                .setAutoCancel(true)
                .build()
            ctx.getSystemService(NotificationManager::class.java)
                .notify((System.nanoTime() and 0x7fffffff).toInt(), n)
        } catch (e: Exception) {
        }
    }
}

class DownloadService : Service() {
    private val h = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            val active = Dl.items.filter { it.state == DlState.RUNNING || it.state == DlState.QUEUED }
            val paused = Dl.items.filter { it.state == DlState.PAUSED }
            val nm = getSystemService(NotificationManager::class.java)
            when {
                active.isNotEmpty() -> {
                    nm.notify(ID, build(active, false))
                    h.postDelayed(this, 1000)
                }
                paused.isNotEmpty() -> {
                    // Keep a normal (swipe-able) notification with Resume / Cancel buttons.
                    stopForeground(STOP_FOREGROUND_DETACH)
                    nm.notify(ID, build(paused, true))
                    stopSelf()
                }
                else -> {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    nm.cancel(ID)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Dl.init(applicationContext)
        DlNotify.channels(this)
        when (intent?.action) {
            ACT_PAUSE -> Dl.pauseAll()
            ACT_RESUME -> Dl.resumeAll()
            ACT_CANCEL -> Dl.items.filter { it.state != DlState.DONE }.toList().forEach { Dl.cancel(it) }
        }
        val active = Dl.items.filter { it.state == DlState.RUNNING || it.state == DlState.QUEUED }
        startForeground(ID, build(active, false), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        h.removeCallbacks(tick)
        h.post(tick)
        return START_NOT_STICKY
    }

    private fun action(label: String, icon: Int, act: String, code: Int): Notification.Action {
        val i = Intent(this, DownloadService::class.java).setAction(act)
        val pi = PendingIntent.getForegroundService(
            this, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build()
    }

    private fun build(list: List<DlItem>, paused: Boolean): Notification {
        val speed = if (paused) 0L else list.sumOf { it.speed }
        val known = list.filter { it.total > 0 }
        val total = known.sumOf { it.total }
        val done = known.sumOf { it.done }
        val pct = if (total > 0) (done * 100 / total).toInt() else -1
        val many = list.size > 1
        val title = when {
            list.isEmpty() -> "Downloads"
            many -> (if (paused) "Paused " else "Downloading ") + list.size + " files"
            else -> list[0].name
        }

        val parts = ArrayList<String>()
        if (paused) parts.add("Paused")
        if (pct >= 0) parts.add("$pct%")
        if (total > 0) parts.add(sizePair(done, total))
        if (!paused) parts.add(speedText(speed))
        val line1 = parts.joinToString("  ·  ")
        val eta = if (!paused && speed > 0 && total > 0) etaText((total - done) / speed) else ""
        val big = if (eta.isEmpty()) line1 else "$line1\n$eta"

        val b = Notification.Builder(this, DlNotify.CH_PROGRESS)
            .setSmallIcon(if (paused) android.R.drawable.ic_media_pause else android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(line1)
            .setStyle(Notification.BigTextStyle().bigText(big))
            .setProgress(100, pct.coerceAtLeast(0), pct < 0 && !paused)
            .setOngoing(!paused)
            .setOnlyAlertOnce(true)
            .setContentIntent(DlNotify.openApp(this))
        if (paused) {
            b.addAction(action(if (many) "Resume all" else "Resume", android.R.drawable.ic_media_play, ACT_RESUME, 1))
        } else {
            b.addAction(action(if (many) "Pause all" else "Pause", android.R.drawable.ic_media_pause, ACT_PAUSE, 2))
        }
        b.addAction(action(if (many) "Cancel all" else "Cancel", android.R.drawable.ic_menu_close_clear_cancel, ACT_CANCEL, 3))
        return b.build()
    }

    private fun sizePair(done: Long, total: Long): String = when {
        total >= 1_000_000_000L -> String.format("%.2f / %.2f GB", done / 1e9, total / 1e9)
        total >= 1_000_000L -> String.format("%.1f / %.1f MB", done / 1e6, total / 1e6)
        else -> "${done / 1000} / ${total / 1000} KB"
    }

    private fun speedText(bps: Long): String =
        if (bps >= 1_000_000L) String.format("%.1f MB/s", bps / 1e6) else "${bps / 1000} KB/s"

    private fun etaText(sec: Long): String = when {
        sec < 60 -> "${sec}s left"
        sec < 3600 -> "${sec / 60}m ${sec % 60}s left"
        else -> "${sec / 3600}h ${(sec % 3600) / 60}m left"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val ID = 4711
        private const val ACT_PAUSE = "com.ahmad.gbrowser.dl.PAUSE"
        private const val ACT_RESUME = "com.ahmad.gbrowser.dl.RESUME"
        private const val ACT_CANCEL = "com.ahmad.gbrowser.dl.CANCEL"
    }
}
