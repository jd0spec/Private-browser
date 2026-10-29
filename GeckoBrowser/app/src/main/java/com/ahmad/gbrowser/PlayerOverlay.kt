package com.ahmad.gbrowser

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NativeVideoOverlay(
    isPlaying: Boolean,
    currentTime: Float,
    duration: Float,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onDownload: () -> Unit,
    onClose: () -> Unit
) {
    // Fullscreen Black Background (Hides the website completely)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Brightness / Volume Gesture Zones
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f).fillMaxHeight()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            println("Brightness swipe: $dragAmount")
                        }
                    }
            )
            Box(
                modifier = Modifier
                    .weight(1f).fillMaxHeight()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            println("Volume swipe: $dragAmount")
                        }
                    }
            )
        }

        // Top Bar (Close & Download)
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onDownload,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Icon(Icons.Default.Download, "Download", tint = Color.White)
            }
            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
        }

        // Bottom Controls (Play/Pause & Seek)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E), RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Text(formatTime(currentTime), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                Slider(
                    value = if (duration > 0) currentTime / duration else 0f,
                    onValueChange = { onSeek(it * duration) },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.DarkGray
                    )
                )

                Text(formatTime(duration), color = Color.Gray, fontSize = 14.sp)
            }
        }
    }
}

private fun formatTime(seconds: Float): String {
    val m = (seconds / 60).toInt()
    val s = (seconds % 60).toInt()
    return String.format("%02d:%02d", m, s)
}
