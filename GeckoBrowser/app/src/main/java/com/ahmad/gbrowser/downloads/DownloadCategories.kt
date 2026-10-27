package com.ahmad.gbrowser.downloads

import java.io.File

object DownloadCategories {
    const val CATEGORY_VIDEOS = "Videos"
    const val CATEGORY_AUDIO = "Audio"
    const val CATEGORY_IMAGES = "Images"
    const val CATEGORY_DOCUMENTS = "Documents"
    const val CATEGORY_ARCHIVES = "Archives"
    const val CATEGORY_APPS = "Apps"
    const val CATEGORY_OTHER = "Other"

    fun categoryForFilename(filename: String): String = CATEGORY_OTHER
    fun isEnabled(context: android.content.Context): Boolean = false
    fun resolveDir(enabled: Boolean, baseDir: File, filename: String): File = baseDir
}
