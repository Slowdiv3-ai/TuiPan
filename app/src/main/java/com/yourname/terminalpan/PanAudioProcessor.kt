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
        val outputBuffer = replaceOutputBuffer(inputBuffer.remaining())
        val channelCount = inputAudioFormat.channelCount

        if (channelCount == 2) {
            while (inputBuffer.hasRemaining()) {
                val leftSample = inputBuffer.short
                val rightSample = inputBuffer.short

                // Volume multipliers
                val leftVolume = if (panValue > 0) 1.0f - panValue else 1.0f
                val rightVolume = if (panValue < 0) 1.0f + panValue else 1.0f

                val pannedLeft = (leftSample * leftVolume).toInt().toShort()
                val pannedRight = (rightSample * rightVolume).toInt().toShort()

                outputBuffer.putShort(pannedLeft)
                outputBuffer.putShort(pannedRight)
            }
        } else {
            outputBuffer.put(inputBuffer)
        }
        outputBuffer.flip()
    }
}
