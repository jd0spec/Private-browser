package com.ahmad.gbrowser.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ahmad.gbrowser.MainActivity

object DownloadNotificationHelper {

    fun createNotificationChannel(context: Context) {
        openChannel(context)
    }

    fun openChannel(context: Context) {
        val channel = NotificationChannel("clint_downloads", "Downloads", NotificationManager.IMPORTANCE_LOW)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun getBuilder(context: Context, item: DownloadItem): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, item.id, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, "clint_downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(item.filename)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
    }

    fun showProgressNotification(context: Context, item: DownloadItem) {
        val progress = if (item.totalBytes > 0) ((item.bytesDownloaded * 100) / item.totalBytes).toInt() else 0
        val notification = getBuilder(context, item)
            .setContentText("Downloading... $progress%")
            .setProgress(100, progress, item.totalBytes <= 0)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(item.id, notification)
    }

    fun showProgressNotificationThrottled(context: Context, item: DownloadItem) {
        // We just call the standard one. The OS handles notification throttling automatically.
        showProgressNotification(context, item)
    }

    fun showCompleteNotification(context: Context, item: DownloadItem) {
        val notification = NotificationCompat.Builder(context, "clint_downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText(item.filename)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(item.id, notification)
    }

    fun showPausedNotification(context: Context, item: DownloadItem) {
        val notification = getBuilder(context, item).setContentText("Paused").setOngoing(false).build()
        context.getSystemService(NotificationManager::class.java).notify(item.id, notification)
    }

    fun showQueuedNotification(context: Context, item: DownloadItem) {
        val notification = getBuilder(context, item).setContentText("Queued").build()
        context.getSystemService(NotificationManager::class.java).notify(item.id, notification)
    }

    fun showWaitingCustomScheduleNotification(context: Context, item: DownloadItem) {
        val notification = getBuilder(context, item).setContentText("Waiting for schedule").build()
        context.getSystemService(NotificationManager::class.java).notify(item.id, notification)
    }

    fun showWaitingScheduleNotification(context: Context, item: DownloadItem) {
        val notification = getBuilder(context, item).setContentText("Waiting for schedule").build()
        context.getSystemService(NotificationManager::class.java).notify(item.id, notification)
    }

    fun showWaitingUnmeteredNotification(context: Context, item: DownloadItem) {
        val notification = getBuilder(context, item).setContentText("Waiting for Wi-Fi").build()
        context.getSystemService(NotificationManager::class.java).notify(item.id, notification)
    }

    fun cancelCompleteNotification(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(id)
    }
}
