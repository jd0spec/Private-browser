
package com.ahmad.gbrowser.downloads

import android.content.Context
import java.io.File

object DownloadFileHelper {
    fun tempDownloadDir(context: Context): File = File(context.cacheDir, "downloads").apply { mkdirs() }
    fun resolveDownloadDir(): File = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
    fun isCustomLocationAccessible(context: Context, mode: String, uri: String?): Boolean = true
    fun resolveDirectCustomDir(context: Context, item: DownloadItem? = null): File? = null
    fun isSafCustomMode(context: Context, item: DownloadItem? = null): Boolean = false
    fun needsLegacyStoragePermission(context: Context): Boolean = false
    fun isInTempDir(context: Context, file: File): Boolean = file.absolutePath.startsWith(context.cacheDir.absolutePath)
    fun isPartFile(file: File): Boolean = file.name.endsWith(".part")
    fun sanitizeFileName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    
    fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        var i = 1
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        while (file.exists()) {
            file = File(dir, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
            i++
        }
        return file
    }
    fun previewUniqueName(dir: File, name: String): String = uniqueFile(dir, name).name
}
