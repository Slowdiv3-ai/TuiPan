package com.yourname.terminalpan

import android.content.Context
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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var panProcessor: PanAudioProcessor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        panProcessor = PanAudioProcessor()

        // Custom Renderers Factory to inject the PanProcessor
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(panProcessor))
                    .setEnableFloatOutput(enableFloatOutput)
                    .build()
            }
        }

        exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .build()

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
    var activeTab by remember { mutableStateOf(3) }
    var currentTrackName by remember { mutableStateOf("No file selected") }
    var currentPanValue by remember { mutableStateOf(0f) }
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            currentTrackName = uri.lastPathSegment ?: "Unknown File"
            player.play()
            isPlaying = true
        }
    }

    // Animation Loop for Auto-Pan
    LaunchedEffect(isPlaying) {
        var time = 0f
        while (isPlaying) {
            // 0.2f speed means one full L->R->L cycle takes ~10 seconds
            val speed = 0.2f 
            
            // Sine wave from -0.8 to 0.8
            val newPan = (kotlin.math.sin(time) * 0.8).toFloat()
            
            currentPanValue = newPan
            panProcessor.panValue = newPan 
            
            delay(50) // Update 20 times a second
            time += speed * 0.05f 
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text(
            text = "■ TuiPan - v1.0 (Panning Test)",
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
                        text = "// AutoPan Engine\n",
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = "Loaded: $currentTrackName\n",
                        color = Color(0xFFFFB300),
                        fontFamily = FontFamily.Monospace,
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // ASCII Visualizer
                    val barLength = 20
                    val center = barLength / 2
                    val pos = (center + (currentPanValue * center)).toInt().coerceIn(0, barLength - 1)
                    
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
                        text = String.format("Val: %+.2f", currentPanValue),
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
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
                BracketButton("open") {
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
                    panProcessor.panValue = 0f
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
