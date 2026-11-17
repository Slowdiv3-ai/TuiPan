package com.yourname.terminalpan

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

class PanAudioProcessor : BaseAudioProcessor() {
    
    // UI updates this value from -1.0f (Left) to 1.0f (Right)
    @Volatile
    var panValue: Float = 0f

    // Toggle for the Frequency Split mode
    @Volatile
    var useFrequencyPan: Boolean = false

    // State for the Low-Pass filter (must persist between buffers)
    private var lowPassL = 0f
    private var lowPassR = 0f

    // Cutoff frequency for the filter
    private val cutoff = 0.05f 

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val outputBuffer = replaceOutputBuffer(inputBuffer.remaining())
        val channelCount = inputAudioFormat.channelCount

        if (channelCount == 2) {
            while (inputBuffer.hasRemaining()) {
                var leftSample = inputBuffer.short.toFloat()
                var rightSample = inputBuffer.short.toFloat()

                if (useFrequencyPan) {
                    // --- FREQUENCY SPLIT MODE ---
                    // 1. Extract the Lows using a simple Low-Pass Filter
                    lowPassL += cutoff * (leftSample - lowPassL)
                    lowPassR += cutoff * (rightSample - lowPassR)
                    
                    val lowL = lowPassL
                    val lowR = lowPassR

                    // 2. Extract the Highs by subtracting Lows from the original
                    val highL = leftSample - lowL
                    val highR = rightSample - lowR

                    // 3. Pan the Highs and Lows in OPPOSITE directions
                    val highVolume = 1.0f - (panValue * 0.8f) 
                    val lowVolume = 1.0f + (panValue * 0.8f)

                    // Mix them back together
                    val mixedL = (highL * highVolume) + (lowL * lowVolume)
                    val mixedR = (highR * highVolume) + (lowR * lowVolume)

                    leftSample = mixedL
                    rightSample = mixedR

                } else {
                    // --- NORMAL BROADBAND PAN MODE ---
                    val leftVolume = if (panValue > 0) 1.0f - panValue else 1.0f
                    val rightVolume = if (panValue < 0) 1.0f + panValue else 1.0f

                    leftSample *= leftVolume
                    rightSample *= rightVolume
                }

                // Clamp to 16-bit limits to prevent distortion
                val outL = leftSample.toInt().coerceIn(-32768, 32767).toShort()
                val outR = rightSample.toInt().coerceIn(-32768, 32767).toShort()

                outputBuffer.putShort(outL)
                outputBuffer.putShort(outR)
            }
        } else {
            // Mono passthrough
            outputBuffer.put(inputBuffer)
        }
        
        outputBuffer.flip()
    }
}
