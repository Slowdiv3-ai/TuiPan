package com.yourname.terminalpan

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Black,
                    surface = Color.Black,
                    primary = Color(0xFFFFB300)
                )
            ) {
                AppContent(controllerFuture)
            }
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}

@Composable
fun AppContent(controllerFuture: ListenableFuture<MediaController>?) {
    var controller by remember { mutableStateOf<MediaController?>(null) }

    LaunchedEffect(controllerFuture) {
        controllerFuture?.let {
            it.addListener({
                controller = it.get()
            }, MoreExecutors.directExecutor())
        }
    }

    if (controller == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Connecting to audio engine...", color = Color.White, fontFamily = FontFamily.Monospace)
        }
    } else {
        TerminalScreen(controller!!)
    }
}

@Composable
fun TerminalScreen(player: MediaController) {
    var isPlaying by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(3) }
    var currentTrackName by remember { mutableStateOf("No file selected") }
    var currentPanValue by remember { mutableStateOf(0f) }
    
    var selectedPattern by remember { mutableStateOf("smooth") }
    var speed by remember { mutableStateOf(1.0f) }
    var panMode by remember { mutableStateOf("volume") } // "volume" or "frequency"

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(selectedPattern, speed, panMode) {
        val args = Bundle().apply {
            putString("pattern", selectedPattern)
            putFloat("speed", speed)
            putBoolean("freqPan", panMode == "frequency")
        }
        player.sendCustomCommand(SessionCommand("SET_PAN", Bundle.EMPTY), args)
    }

    LaunchedEffect(isPlaying, selectedPattern, speed) {
        if (isPlaying) {
            var time = 0f
            while (isPlaying) {
                val freq = (2 * Math.PI / 4.0) * speed
                val phase = time * freq
                currentPanValue = when(selectedPattern) {
                    "smooth" -> (kotlin.math.sin(phase) * 0.8).toFloat()
                    "wave" -> ((2.0 / Math.PI) * kotlin.math.asin(kotlin.math.sin(phase)) * 0.8).toFloat()
                    "subtle" -> (kotlin.math.sin(phase) * 0.4).toFloat()
                    else -> 0f
                }
                delay(50)
                time += 0.05f
            }
        } else {
            currentPanValue = 0f
        }
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
            player.sendCustomCommand(SessionCommand("START_PAN", Bundle.EMPTY), Bundle.EMPTY)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text(
            text = "■ TuiPan - Background Ready",
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        
        Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (activeTab == 3) {
                Column {
                    Text("// AutoPan Engine\n", color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val barLength = 20
                    val center = barLength / 2
                    val pos = (center + (currentPanValue * center)).toInt().coerceIn(0, barLength - 1)
                    val asciiBar = buildString {
                        for (i in 0 until barLength) { if (i == pos) append("o") else append("-") }
                    }
                    Text("[ $asciiBar ]", fontFamily = FontFamily.Monospace, color = Color(0xFFFFB300), fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text("pattern", color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val patterns = listOf("smooth", "wave", "subtle")
                        patterns.forEach { p ->
                            BracketButton(p, isActive = selectedPattern == p) { selectedPattern = p }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("mode", color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        BracketButton("volume", isActive = panMode == "volume") { panMode = "volume" }
                        BracketButton("frequency", isActive = panMode == "frequency") { panMode = "frequency" }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("speed [${String.format("%.2f", speed)}x]", color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(8.dp))
                    TerminalSlider(value = speed, onValueChange = { speed = it }, range = 0.05f..2.0f)
                }
            } else {
                Text("Empty Tab", color = Color.Gray, fontFamily = FontFamily.Monospace)
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BracketButton("open") { filePickerLauncher.launch("audio/*") }
                BracketButton("play") {
                    if (!isPlaying) {
                        if (player.mediaItemCount == 0) {
                            filePickerLauncher.launch("audio/*")
                        } else {
                            player.play()
                            player.sendCustomCommand(SessionCommand("START_PAN", Bundle.EMPTY), Bundle.EMPTY)
                        }
                    } else {
                        player.pause()
                        player.sendCustomCommand(SessionCommand("STOP_PAN", Bundle.EMPTY), Bundle.EMPTY)
                    }
                }
                BracketButton("stop") {
                    player.pause()
                    player.seekTo(0)
                    player.sendCustomCommand(SessionCommand("STOP_PAN", Bundle.EMPTY), Bundle.EMPTY)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val tabs = listOf("~", "&", "?", "pan", "cfg")
            tabs.forEachIndexed { index, label ->
                BracketButton(label, isActive = activeTab == index) { activeTab = index }
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
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(range) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onValueChange(range.start + newProgress * (range.endInclusive - range.start))
                }
            }
            .pointerInput(range) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        onValueChange(range.start + newProgress * (range.endInclusive - range.start))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        onValueChange(range.start + newProgress * (range.endInclusive - range.start))
                    }
                )
            }
    ) {
        val barLength = (maxWidth / 10.dp).toInt().coerceAtLeast(10)
        val progress = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        val filled = (progress * barLength).toInt().coerceIn(0, barLength)
        
        val asciiBar = buildString {
            append("|".repeat(filled))
            append(".".repeat(barLength - filled))
        }

        Text("[$asciiBar]", fontFamily = FontFamily.Monospace, color = Color(0xFFFFB300), fontSize = 14.sp)
    }
}

@Composable
fun BracketButton(label: String, isActive: Boolean = false, onClick: () -> Unit) {
    val textColor = if (isActive) Color(0xFFFFB300) else Color.White
    val borderColor = if (isActive) Color(0xFFFFB300) else Color.DarkGray
    Box(modifier = Modifier.border(1.dp, borderColor).clickable { onClick() }.padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text("[ $label ]", fontFamily = FontFamily.Monospace, color = textColor, fontSize = 12.sp)
    }
}
