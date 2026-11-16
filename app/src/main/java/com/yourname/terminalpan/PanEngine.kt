package com.yourname.terminalpan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class PanEngine(
    private val panProcessor: PanAudioProcessor,
    private val onPanUpdate: (Float) -> Unit // Callback to update the UI
) {
    var pattern: String = "smooth"
    var speed: Float = 1.0f // 0.1x to 5.0x
    var customCurve: List<Float> = emptyList()

    private var engineJob: Job? = null
    private var startTime: Long = 0L
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        startTime = System.nanoTime()

        engineJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && isRunning) {
                val elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0
                val panValue = calculatePan(elapsedSeconds.toFloat())
                
                // Send to audio processor immediately
                panProcessor.panValue = panValue
                
                // Update UI (throttled slightly to save battery, ~60fps)
                onPanUpdate(panValue)
                
                delay(16) // ~60 updates per second
            }
        }
    }

    fun stop() {
        isRunning = false
        engineJob?.cancel()
        panProcessor.panValue = 0f
        onPanUpdate(0f)
    }

    fun reset() {
        startTime = System.nanoTime()
    }

    private fun calculatePan(time: Float): Float {
        // Base frequency of the wave (speed multiplier)
        // A speed of 1.0 means 1 full cycle every 4 seconds
        val freq = (2 * PI / 4.0) * speed
        val phase = time * freq

        return when (pattern) {
            "smooth" -> {
                // Classic sine wave: -0.8 to +0.8
                (sin(phase) * 0.8).toFloat()
            }
            "wave" -> {
                // Triangle wave: Linear sweep left to right
                val triangle = (2.0 / PI) * kotlin.math.asin(sin(phase))
                (triangle * 0.8).toFloat()
            }
            "subtle" -> {
                // Gentle drift: Slower, lower depth (-0.4 to +0.4)
                (sin(phase) * 0.4).toFloat()
            }
            "hard" -> {
                // Square wave: Instant jumps between L and R
                if (sin(phase) >= 0) 0.8f else -0.8f
            }
            "custom" -> {
                // Loop through custom curve
                if (customCurve.isEmpty()) {
                    0f
                } else {
                    // Determine where we are in the loop (0.0 to 1.0)
                    val cycleProgress = (phase % (2 * PI)) / (2 * PI)
                    val index = (cycleProgress * customCurve.size).toInt().coerceIn(0, customCurve.size - 1)
                    customCurve[index]
                }
            }
            else -> 0f
        }
    }
}
