package com.ahmad.gbrowser.downloads

import android.content.Context
import java.io.File

object DownloadBlobStore {
    fun sweep(context: Context, time: Long) {}
    fun moveTo(source: File, dest: File) {
        source.inputStream().use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        source.delete()
    }
}
