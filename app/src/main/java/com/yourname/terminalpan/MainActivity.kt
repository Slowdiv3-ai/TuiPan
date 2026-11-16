package com.yourname.terminalpan

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var panProcessor: PanAudioProcessor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize the Audio Processor
        panProcessor = PanAudioProcessor()

        // 2. Initialize ExoPlayer and inject the Processor

        // 2. Initialize ExoPlayer and inject the Processor
        val renderersFactory = DefaultRenderersFactory(this)
            .setAudioProcessors(arrayOf(panProcessor)) // This is the modern, clean way

        exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .build()

        // 3. Load a test song (Replace with local file later)
        val testAudioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        exoPlayer.setMediaItem(MediaItem.fromUri(testAudioUrl))
        exoPlayer.prepare()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Black,
                    surface = Color.Black,
                    primary = Color(0xFFFFB300) // Amber
                )
            ) {
                TerminalScreen(exoPlayer, panProcessor)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
    }
}

@Composable
fun TerminalScreen(player: ExoPlayer, panProcessor: PanAudioProcessor) {
    var isPlaying by remember { mutableStateOf(false) }
    var panValue by remember { mutableStateOf(0f) }
    var activeTab by remember { mutableStateOf(3) } // 0=~, 1=&, 2=?, 3=pan, 4=cfg

    // Coroutine to animate the pan value over time (Auto-pan sine wave)
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            // Simple sine wave auto-pan
            val time = System.currentTimeMillis() / 1000.0
            val newPan = (kotlin.math.sin(time * 2.0) * 0.8).toFloat() // Range -0.8 to 0.8
            
            panValue = newPan
            panProcessor.panValue = newPan // Send to audio engine
            
            delay(50) // Update 20 times a second
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "■ TerminalPan - v1.0",
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        
        Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))

        // Content Area based on Tab
        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                3 -> PanControlScreen(panValue)
                else -> Text("Empty Terminal Tab", color = Color.Gray, fontFamily = FontFamily.Monospace)
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
                    panProcessor.panValue = 0f
                }
            }
        }

        // Bottom Navigation
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
fun PanControlScreen(currentPan: Float) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("// AutoPan Engine", color = Color.Gray, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(32.dp))
        
        // Visual ASCII representation of the pan
        val barLength = 20
        val center = barLength / 2
        val pos = (center + (currentPan * center)).toInt().coerceIn(0, barLength - 1)
        
        val asciiBar = buildString {
            for (i in 0 until barLength) {
                if (i == pos) append("o") else append("-")
            }
        }

        Text(
            text = "[ $asciiBar ]",
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFFFB300),
            fontSize = 20.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = String.format("Val: %+.2f", currentPan),
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
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
