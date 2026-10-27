package com.ahmad.gbrowser.downloads

import android.util.Log

object DownloadLog {
    @Volatile var enabled = false
    
    fun init(context: android.content.Context) {}
    fun i(id: Int, tag: String, msg: () -> String) { if (enabled) Log.i("Download-$id", msg()) }
    fun d(id: Int, tag: String, msg: () -> String) { if (enabled) Log.d("Download-$id", msg()) }
    fun w(id: Int, tag: String, msg: () -> String) { if (enabled) Log.w("Download-$id", msg()) }
    fun e(id: Int, tag: String, msg: () -> String) { Log.e("Download-$id", msg()) }
    
    fun section(id: Int, title: String, block: () -> Unit) {}
    fun describe(item: DownloadItem): String = item.filename
    fun observe(before: DownloadItem?, after: DownloadItem) {}
    fun deleteItemLog(id: Int) {}
    
    fun formatSize(bytes: Long): String = "$bytes B"
    fun formatTime(ms: Long): String = ms.toString()
    fun formatElapsed(ms: Long): String = ms.toString()
}
