package com.ahmad.gbrowser.downloads

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object DownloadWorker {
    suspend fun run(context: Context, item: DownloadItem) {
        withContext(Dispatchers.IO) {
            try {
                val destFile = File(DownloadFileHelper.resolveDownloadDir(), item.filename)
                destFile.parentFile?.mkdirs()

                val request = Request.Builder().url(item.url).build()
                val response = ClintDownloadManager.httpClient.newCall(request).execute()
                val body = response.body

                if (!response.isSuccessful || body == null) {
                    fail(context, item, "HTTP Error: ${response.code}")
                    return@withContext
                }

                val contentLength = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(destFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    
                    val updated = item.copy(
                        bytesDownloaded = totalRead,
                        totalBytes = if (contentLength > 0) contentLength else item.totalBytes,
                        status = DownloadStatus.DOWNLOADING
                    )
                    ClintDownloadManager.publish(updated)
                DownloadNotificationHelper.showProgressNotification(context, updated)
                }

                outputStream.close()
                inputStream.close()

                val complete = item.copy(
                    bytesDownloaded = totalRead,
                    totalBytes = if (contentLength > 0) contentLength else totalRead,
                    status = DownloadStatus.COMPLETE,
                    completedAt = System.currentTimeMillis(),
                    file = destFile
                )
                ClintDownloadManager.persistDownload(complete)
                ClintDownloadManager.publish(complete)
                DownloadNotificationHelper.showCompleteNotification(context, complete)
                ClintDownloadManager.tryDequeueNext(context)

            } catch (e: Exception) {
                fail(context, item, e.message ?: "Unknown Error")
            }
        }
    }

    fun fail(context: Context, item: DownloadItem, error: String, scheduleRetry: Boolean = true) {
        val failed = item.copy(
            status = DownloadStatus.FAILED,
            errorMessage = error
        )
        ClintDownloadManager.publish(failed)
        ClintDownloadManager.persistLater(failed)
        DownloadNotificationHelper.showPausedNotification(context, failed)
        ClintDownloadManager.tryDequeueNext(context)
    }

    fun moveTempToSaf(context: Context, item: DownloadItem) { }
}
