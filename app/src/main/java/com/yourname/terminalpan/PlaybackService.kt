package com.yourname.terminalpan

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    lateinit var panProcessor: PanAudioProcessor
        private set
    lateinit var panEngine: PanEngine
        private set

    override fun onCreate() {
        super.onCreate()

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

        val player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setHandleAudioBecomingNoisy(true)
            .build()

        panEngine = PanEngine(panProcessor) { /* UI will observe via player or state */ }

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        panEngine.stop()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
