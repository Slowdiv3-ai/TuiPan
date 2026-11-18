package com.yourname.terminalpan

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private var boundService: PlaybackService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as PlaybackService.LocalBinder
            boundService = binder.getService()
            isBound = true
            Log.d("TuiPan", "Service Connected")
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start and bind the service
        Intent(this, PlaybackService::class.java).also { intent ->
            startForegroundService(intent)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Black,
                    surface = Color.Black,
                    primary = Color(0xFFFFB300)
                )
            ) {
                // Pass the bound state and service to Compose
                AppContent(isBound, boundService)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}

@Composable
fun AppContent(isBound: Boolean, service: PlaybackService?) {
    if (!isBound || service == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Connecting to audio engine...", color = Color.White, fontFamily = FontFamily.Monospace)
        }
    } else {
        TerminalScreen(service)
    }
}

@Composable
fun TerminalScreen(service: PlaybackService) {
    val player = service.exoPlayer
    var isPlaying by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(3) }
    var currentTrackName by remember { mutableStateOf("No file selected") }
    var currentPanValue by remember { mutableStateOf(0f) }
    
    var selectedPattern by remember { mutableStateOf("smooth") }
    var speed by remember { mutableStateOf(1.0f) }
    var panMode by remember { mutableStateOf("volume") }
    
    var logMessage by remember { mutableStateOf("Ready.") }
    val scope = rememberCoroutineScope()

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
                logMessage = if (isPlayingNow) "Playing audio..." else "Paused."
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    logMessage = "Ready. Press play!"
                } else if (state == Player.STATE_BUFFERING) {
                    logMessage = "Buffering..."
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                logMessage = "ERROR: ${error.message}"
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    LaunchedEffect(selectedPattern, speed, panMode) {
        service.panEngine.pattern = selectedPattern
        service.panEngine.speed = speed
        service.panProcessor.useFrequencyPan = panMode == "frequency"
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
            logMessage = "Copying file..."
            
            scope.launch {
                val localFile = copyUriToCache(context, uri)
                if (localFile != null) {
                    withContext(Dispatchers.Main) {
                        val uriToPlay = Uri.fromFile(localFile)
                        Log.d("TuiPan", "SETTING MEDIA ITEM TO: $uriToPlay")
                        val mediaItem = MediaItem.fromUri(uriToPlay)
                        player?.setMediaItem(mediaItem)
                        player?.prepare()
                        player?.play()
                        currentTrackName = localFile.name
                        logMessage = "Loaded: ${localFile.name}"
                        
                        service.panEngine.start()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        logMessage = "ERROR: Failed to copy file."
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text(
            text = "■ TuiPan - Debug Mode",
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        
        Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (activeTab == 3) {
                Column {
                    Text("// Debug Console\n", color = Color.Gray, fontFamily = FontFamily.Monospace)
                    
                    Text(
                        text = logMessage,
                        color = Color(0xFFFFB300),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    
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
                        player?.play()
                        service.panEngine.start()
                    } else {
                        player?.pause()
                        service.panEngine.stop()
                    }
                }
                BracketButton("stop") {
                    player?.pause()
                    player?.seekTo(0)
                    service.panEngine.stop()
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

suspend fun copyUriToCache(context: android.content.Context, uri: Uri): File? {
    return withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val tempFile = File(context.cacheDir, "temp_audio.mp3")
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                inputStream.close()
                tempFile
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
