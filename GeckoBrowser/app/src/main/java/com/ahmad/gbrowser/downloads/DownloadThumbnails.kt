package com.ahmad.gbrowser.downloads

import android.content.Context
import java.io.File

object DownloadThumbnails {
    // Stubs to ignore thumbnail generation
    suspend fun generate(context: Context, item: DownloadItem) { /* Do nothing */ }
    fun get(context: Context, item: DownloadItem): File? = null
    fun clear(context: Context) { /* Do nothing */ }
}
