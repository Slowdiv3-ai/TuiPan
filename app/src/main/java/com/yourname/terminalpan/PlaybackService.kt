package com.yourname.terminalpan

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

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
                context: android.content.Context,
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

        panEngine = PanEngine(panProcessor) { /* UI updates independently */ }

        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val availableCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand("SET_PAN", Bundle.EMPTY))
                    .add(SessionCommand("START_PAN", Bundle.EMPTY))
                    .add(SessionCommand("STOP_PAN", Bundle.EMPTY))
                    .build()
                return MediaSession.ConnectionResult.accept(availableCommands, Player.Commands.EMPTY)
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    "SET_PAN" -> {
                        val pattern = args.getString("pattern") ?: "smooth"
                        val speed = args.getFloat("speed", 1.0f)
                        panEngine.pattern = pattern
                        panEngine.speed = speed
                    }
                    "START_PAN" -> panEngine.start()
                    "STOP_PAN" -> panEngine.stop()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .build()
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
