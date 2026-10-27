package com.ahmad.gbrowser.downloads

import android.content.Context

object DownloadScheduleMonitor {
    val scheduleWaitingIds: MutableSet<Int> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    fun reconcile(context: Context) {}
    fun isWithinWindow(context: Context): Boolean = true
}
