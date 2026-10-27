package com.ahmad.gbrowser.downloads

// We are using the simplified DownloadWorker instead.
// This file is stubbed out to prevent build errors.
object ParallelDownloader {
    suspend fun download(context: android.content.Context, item: DownloadItem): Boolean {
        DownloadWorker.run(context, item)
        return true
    }
} 
