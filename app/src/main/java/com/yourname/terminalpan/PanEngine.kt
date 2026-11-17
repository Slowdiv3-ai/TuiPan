package com.yourname.terminalpan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

class PanEngine(
    private val panProcessor: PanAudioProcessor,
    private val onPanUpdate: (Float) -> Unit
) {
    var pattern: String = "smooth"
    var speed: Float = 1.0f
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
                
                panProcessor.panValue = panValue
                onPanUpdate(panValue)
                
                delay(16) 
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
        val freq = (2 * PI / 4.0) * speed
        val phase = time * freq

        return when (pattern) {
            "smooth" -> (sin(phase) * 0.8).toFloat()
            "wave" -> ((2.0 / PI) * kotlin.math.asin(sin(phase)) * 0.8).toFloat()
            "subtle" -> (sin(phase) * 0.4).toFloat()
            "custom" -> {
                if (customCurve.isEmpty()) 0f
                else {
                    val cycleProgress = (phase % (2 * PI)) / (2 * PI)
                    val index = (cycleProgress * customCurve.size).toInt().coerceIn(0, customCurve.size - 1)
                    customCurve[index]
                }
            }
            else -> 0f
        }
    }
}
