package com.ahmad.gbrowser.downloads

import android.content.Context

object DownloadCustomScheduleMonitor {
    fun rearmAll(context: Context) {}
    fun cancel(context: Context, id: Int) {}
    fun schedule(context: Context, id: Int, timeMillis: Long) {}
}
