package com.yourname.terminalpan

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

class PanAudioProcessor : BaseAudioProcessor() {
    
    @Volatile
    var panValue: Float = 0f

    @Volatile
    var useFrequencyPan: Boolean = false

    private var lowPassL = 0f
    private var lowPassR = 0f
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
                    lowPassL += cutoff * (leftSample - lowPassL)
                    lowPassR += cutoff * (rightSample - lowPassR)
                    
                    val lowL = lowPassL
                    val lowR = lowPassR
                    val highL = leftSample - lowL
                    val highR = rightSample - lowR

                    val highVolume = 1.0f - (panValue * 0.8f) 
                    val lowVolume = 1.0f + (panValue * 0.8f)

                    leftSample = (highL * highVolume) + (lowL * lowVolume)
                    rightSample = (highR * highVolume) + (lowR * lowVolume)
                } else {
                    val leftVolume = if (panValue > 0) 1.0f - panValue else 1.0f
                    val rightVolume = if (panValue < 0) 1.0f + panValue else 1.0f
                    leftSample *= leftVolume
                    rightSample *= rightVolume
                }

                val outL = leftSample.toInt().coerceIn(-32768, 32767).toShort()
                val outR = rightSample.toInt().coerceIn(-32768, 32767).toShort()

                outputBuffer.putShort(outL)
                outputBuffer.putShort(outR)
            }
        } else {
            outputBuffer.put(inputBuffer)
        }
        
        outputBuffer.flip()
    }
}
