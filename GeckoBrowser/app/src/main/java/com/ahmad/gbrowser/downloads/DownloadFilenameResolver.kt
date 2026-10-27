package com.ahmad.gbrowser.downloads

import java.io.InputStream

object DownloadFilenameResolver {
    fun readHead(inputStream: InputStream): ByteArray? = null
    fun refine(filename: String, mimeType: String?, head: ByteArray?): String = filename
    fun extractFilenameFromContentDisposition(header: String?): String? = null
    fun detectExtFromMagicBytes(bytes: ByteArray): String? = null
    fun isValidExtension(ext: String): Boolean = true
}
