package com.ahmad.gbrowser.downloads

class SpeedLimiter(initialLimit: Long) {
    @Volatile private var limitBytesPerSec: Long = initialLimit
    fun updateLimit(limit: Long) { limitBytesPerSec = limit }
    fun acquire(bytes: Long) { /* Stub for compilation */ }
}
