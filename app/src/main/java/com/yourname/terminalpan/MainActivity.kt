package com.yourname.terminalpan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var exoPlayer: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize ExoPlayer (NO custom processor for now)
        exoPlayer = ExoPlayer.Builder(this).build()

        // 2. Add listener to check for errors
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("TuiPan", "PLAYER ERROR: ${error.message}", error)
            }
            override fun onPlaybackStateChanged(state: Int) {
                android.util.Log.d("TuiPan", "PLAYER STATE: $state")
            }
        })

        // 3. Load test audio
        val testAudioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        exoPlayer.setMediaItem(MediaItem.fromUri(testAudioUrl))
        exoPlayer.prepare()

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "■ TuiPan - v1.0 (Audio Test)",
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        
        Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))

        // Content Area
        Box(modifier = Modifier.weight(1f)) {
            if (activeTab == 3) {
                Text(
                    text = "// Audio Test Mode\n\nNo panning processor active.\nIf you hear music, the network \nand ExoPlayer are working fine.",
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 24.sp
                )
            } else {
                Text("Empty Tab", color = Color.Gray, fontFamily = FontFamily.Monospace)
            }
        }

        // Playback Controls
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

        // Bottom Nav
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
