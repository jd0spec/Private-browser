package com.ahmad.gbrowser.downloads

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

object DownloadForegroundService {
    fun start(context: Context) {
        val intent = Intent(context, DownloadService::class.java)
        context.startForegroundService(intent)
    }
}

class DownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notification.Builder(this, "clint_downloads")
            .setContentTitle("Downloads running")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
        startForeground(1, notification)
        return START_NOT_STICKY
    }
}
