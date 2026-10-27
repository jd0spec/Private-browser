package com.ahmad.gbrowser.downloads
import java.io.File

object DownloadCategories {
    fun isEnabled(context: android.content.Context): Boolean = false
    fun resolveDir(enabled: Boolean, baseDir: File, filename: String): File = baseDir
}
