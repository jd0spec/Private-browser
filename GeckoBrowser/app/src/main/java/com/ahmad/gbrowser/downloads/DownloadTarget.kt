package com.ahmad.gbrowser.downloads

import java.io.File

class DownloadTarget(val file: File) {
    val length: Long get() = if (file.exists()) file.length() else 0L
    val finalNameOf: String get() = file.name
    fun delete() { file.delete() }
}
