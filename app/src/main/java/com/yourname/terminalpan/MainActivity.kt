package com.yourname.terminalpan

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class MainActivity : ComponentActivity() {

    private lateinit var exoPlayer: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        exoPlayer = ExoPlayer.Builder(this).build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("TuiPan", "PLAYER ERROR: ${error.message}", error)
            }
        })

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Black,
                    surface = Color.Black,
                    primary = Color(0xFFFFB300)
                )
            ) {
                TerminalScreen(exoPlayer)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
    }
}

@Composable
fun TerminalScreen(player: ExoPlayer) {
    var isPlaying by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(3) }
    var currentTrackName by remember { mutableStateOf("No file selected") }
    val context = LocalContext.current

    // This is the launcher that opens the Android file picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // User picked a file, load it into ExoPlayer
            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            
            // Try to get the file name (just a simple string representation for now)
            currentTrackName = uri.lastPathSegment ?: "Unknown File"
            
            // Auto-play
            player.play()
            isPlaying = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text(
            text = "■ TuiPan - v1.0 (Local Audio Test)",
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        
        Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (activeTab == 3) {
                Column {
                    Text(
                        text = "// Local Audio Mode\n",
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = "Loaded: $currentTrackName",
                        color = Color(0xFFFFB300),
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 24.sp
                    )
                }
            } else {
                Text("Empty Tab", color = Color.Gray, fontFamily = FontFamily.Monospace)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isPlaying) "Status: PLAYING" else "Status: STOPPED",
                fontFamily = FontFamily.Monospace,
                color = if (isPlaying) Color(0xFFFFB300) else Color.Gray
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // NEW: File Picker Button
                BracketButton("open") {
                    // "audio/*" tells the picker to only show audio files
                    filePickerLauncher.launch("audio/*")
                }
                
                BracketButton("play") {
                    if (!isPlaying) {
                        player.play()
                        isPlaying = true
                    } else {
                        player.pause()
                        isPlaying = false
                    }
                }
                BracketButton("stop") {
                    player.pause()
                    player.seekTo(0)
                    isPlaying = false
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val tabs = listOf("~", "&", "?", "pan", "cfg")
            tabs.forEachIndexed { index, label ->
                BracketButton(label, isActive = activeTab == index) {
                    activeTab = index
                }
            }
        }
    }
}

@Composable
fun BracketButton(
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val textColor = if (isActive) Color(0xFFFFB300) else Color.White
    val borderColor = if (isActive) Color(0xFFFFB300) else Color.DarkGray

    Box(
        modifier = Modifier
            .border(1.dp, borderColor)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "[ $label ]",
            fontFamily = FontFamily.Monospace,
            color = textColor,
            fontSize = 14.sp
        )
    }
}
