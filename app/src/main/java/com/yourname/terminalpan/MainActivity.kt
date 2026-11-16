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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

class MainActivity : ComponentActivity() {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var panProcessor: PanAudioProcessor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        panProcessor = PanAudioProcessor()

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

    val panEngine = remember {
        PanEngine(panProcessor) { newVal -> currentPanValue = newVal }
    }

    var selectedPattern by remember { mutableStateOf("smooth") }
    var speed by remember { mutableStateOf(1.0f) }

    LaunchedEffect(selectedPattern, speed) {
        panEngine.pattern = selectedPattern
        panEngine.speed = speed
    }

    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            currentTrackName = uri.lastPathSegment ?: "Unknown File"
            player.play()
            isPlaying = true
            panEngine.reset()
            panEngine.start()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text(
            text = "■ TuiPan - Pan Module",
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
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(16.dp))

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

                    Spacer(modifier = Modifier.height(32.dp))

                    Text("pattern", color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val patterns = listOf("smooth", "wave", "subtle")
                        patterns.forEach { p ->
                            BracketButton(p, isActive = selectedPattern == p) {
                                selectedPattern = p
                                panEngine.reset()
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "speed [${String.format("%.1f", speed)}x]",
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TerminalSlider(
                        value = speed,
                        onValueChange = { speed = it },
                        range = 0.05f..2.0f
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BracketButton("open") { filePickerLauncher.launch("audio/*") }
                BracketButton("play") {
                    if (!isPlaying) {
                        player.play()
                        isPlaying = true
                        panEngine.start()
                    } else {
                        player.pause()
                        isPlaying = false
                        panEngine.stop()
                    }
                }
                BracketButton("stop") {
                    player.pause()
                    player.seekTo(0)
                    isPlaying = false
                    panEngine.stop()
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
fun TerminalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>
) {
    val barLength = 20
    val progress = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val filled = (progress * barLength).toInt().coerceIn(0, barLength)

    val asciiBar = buildString {
        append("|".repeat(filled))
        append(".".repeat(barLength - filled))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(range) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    val newValue = range.start + newProgress * (range.endInclusive - range.start)
                    onValueChange(newValue)
                }
            }
            .pointerInput(range) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        val newValue = range.start + newProgress * (range.endInclusive - range.start)
                        onValueChange(newValue)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        val newValue = range.start + newProgress * (range.endInclusive - range.start)
                        onValueChange(newValue)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "[$asciiBar]",
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFFFB300),
            fontSize = 16.sp
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
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "[ $label ]",
            fontFamily = FontFamily.Monospace,
            color = textColor,
            fontSize = 12.sp
        )
    }
}
