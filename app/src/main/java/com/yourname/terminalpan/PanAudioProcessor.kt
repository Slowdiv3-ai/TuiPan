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

    // State variables for the low-pass filter
    private var lowPassL = 0f
    private var lowPassR = 0f
    private val cutoff = 0.05f 

    override fun isActive(): Boolean {
        // Always true so the processor stays in the chain
        return true
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        
        // CRITICAL FIX: Reset filter state on every new track or format change 
        // to prevent DC offset, clipping, or silence on resume.
        lowPassL = 0f
        lowPassR = 0f
        
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return

        val outputBuffer = replaceOutputBuffer(size)
        val channelCount = inputAudioFormat.channelCount

        if (channelCount == 2) {
            while (inputBuffer.hasRemaining()) {
                // FIX 1: Use getShort() to correctly read 16-bit little-endian samples
                var leftSample = inputBuffer.getShort().toFloat()
                var rightSample = inputBuffer.getShort().toFloat()

                if (useFrequencyPan) {
                    // Simple 1-pole low-pass filter
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

                // Clamp to 16-bit range to prevent distortion
                val outL = leftSample.toInt().coerceIn(-32768, 32767).toShort()
                val outR = rightSample.toInt().coerceIn(-32768, 32767).toShort()

                outputBuffer.putShort(outL)
                outputBuffer.putShort(outR)
            }
        } else {
            // Mono fallback: just pass through
            outputBuffer.put(inputBuffer)
        }
        
        // FIX 2: DO NOT call outputBuffer.flip() here. 
        // Media3's BaseAudioProcessor.getOutput() will automatically flip it 
        // before passing it to the AudioSink. Flipping it twice results in 0 bytes read (silence).
        
        // Advance the input buffer position to show we consumed all data
        inputBuffer.position(inputBuffer.limit())
    }

    override fun flush() {
        super.flush()
        // Also reset state on flush (e.g., when seeking)
        lowPassL = 0f
        lowPassR = 0f
    }
}
