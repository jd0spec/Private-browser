package com.ahmad.gbrowser.downloads

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class DownloadsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFF6750A4),
                    surface = androidx.compose.ui.graphics.Color(0xFFFFFBFE),
                    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE7E0EC),
                    onSurface = androidx.compose.ui.graphics.Color(0xFF1C1B1F)
                )
            ) {
                DownloadsScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DownloadsScreen() {
        // Listen to the live engine data!
        val downloads by ClintDownloadManager.downloadsFlow.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Downloads") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF6750A4),
                        titleContentColor = androidx.compose.ui.graphics.Color.White
                    )
                )
            }
        ) { padding ->
            if (downloads.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No downloads yet", color = androidx.compose.ui.graphics.Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    items(downloads) { item ->
                        DownloadItemRow(item)
                    }
                }
            }
        }
    }

    @Composable
    fun DownloadItemRow(item: DownloadItem) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = item.filename, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            
            when (item.status) {
                DownloadStatus.COMPLETE -> Text("Complete", color = androidx.compose.ui.graphics.Color(0xFF4CAF50))
                DownloadStatus.FAILED -> Text("Failed: ${item.errorMessage}", color = androidx.compose.ui.graphics.Color.Red)
                DownloadStatus.PAUSED -> Text("Paused", color = androidx.compose.ui.graphics.Color.Gray)
                else -> {
                    val progress = if (item.totalBytes > 0) item.bytesDownloaded.toFloat() / item.totalBytes else 0f
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("Downloading... ${(progress * 100).toInt()}%", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.Gray)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
        }
    }
}
