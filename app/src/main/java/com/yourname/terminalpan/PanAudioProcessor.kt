package com.yourname.terminalpan

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

class PanAudioProcessor : BaseAudioProcessor() {
    
    // UI updates this value from -1.0f (Left) to 1.0f (Right)
    @Volatile
    var panValue: Float = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // Make sure the output buffer is large enough
        val outputBuffer = replaceOutputBuffer(inputBuffer.remaining())
        val channelCount = inputAudioFormat.channelCount

        if (channelCount == 2) {
            while (inputBuffer.hasRemaining()) {
                // Read Left and Right samples
                val leftSample = inputBuffer.short
                val rightSample = inputBuffer.short

                // Calculate volume (0.0 to 1.0)
                val leftVolume = if (panValue > 0) 1.0f - panValue else 1.0f
                val rightVolume = if (panValue < 0) 1.0f + panValue else 1.0f

                // Apply volume and clamp to Short range (-32768 to 32767) to prevent distortion
                val pannedLeft = (leftSample * leftVolume).toInt().coerceIn(-32768, 32767).toShort()
                val pannedRight = (rightSample * rightVolume).toInt().coerceIn(-32768, 32767).toShort()

                outputBuffer.putShort(pannedLeft)
                outputBuffer.putShort(pannedRight)
            }
        } else {
            // If not stereo, just pass through without panning
            outputBuffer.put(inputBuffer)
        }
        
        outputBuffer.flip()
    }
}
