package com.ahmad.gbrowser.downloads

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object DownloadNetworkMonitor {
    val unmeteredPausedIds: MutableSet<Int> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    val networkWaitingIds: MutableSet<Int> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    fun register(context: Context) {}
    
    fun isNetworkUnmetered(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun onUnmeteredOnlyChanged(context: Context, enabled: Boolean) {}
}

