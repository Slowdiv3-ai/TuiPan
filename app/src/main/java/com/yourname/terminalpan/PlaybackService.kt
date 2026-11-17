package com.yourname.terminalpan

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
        Log.d("TuiPan", "PlaybackService onCreate started")

        try {
            panProcessor = PanAudioProcessor()
            Log.d("TuiPan", "PanProcessor created")

            val renderersFactory = object : DefaultRenderersFactory(this) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean
                ): AudioSink? {
                    Log.d("TuiPan", "Building AudioSink")
                    return DefaultAudioSink.Builder(context)
                        .setAudioProcessors(arrayOf(panProcessor))
                        .setEnableFloatOutput(enableFloatOutput)
                        .build()
                }
            }

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            Log.d("TuiPan", "Building ExoPlayer")
            val player = ExoPlayer.Builder(this)
                // .setRenderersFactory(renderersFactory) // <--- TEMPORARILY BYPASSED
                .setHandleAudioBecomingNoisy(true)
                .setAudioAttributes(audioAttributes, true)
                .build()
                
            Log.d("TuiPan", "ExoPlayer built successfully")

            panEngine = PanEngine(panProcessor) { /* UI updates independently */ }

            val sessionActivityIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val sessionCallback = object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    Log.d("TuiPan", "Controller connected")
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
                    Log.d("TuiPan", "Custom Command: ${customCommand.customAction}")
                    when (customCommand.customAction) {
                        "SET_PAN" -> {
                            val pattern = args.getString("pattern") ?: "smooth"
                            val speed = args.getFloat("speed", 1.0f)
                            val freqPan = args.getBoolean("freqPan", false)
                            
                            panEngine.pattern = pattern
                            panEngine.speed = speed
                            panProcessor.useFrequencyPan = freqPan
                        }
                        "START_PAN" -> panEngine.start()
                        "STOP_PAN" -> panEngine.stop()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }

            mediaSession = MediaSession.Builder(this, player)
                .setCallback(sessionCallback)
                .setSessionActivity(sessionActivityIntent)
                .build()
                
            Log.d("TuiPan", "MediaSession built successfully")
            
        } catch (e: Exception) {
            Log.e("TuiPan", "CRASH IN SERVICE ONCREATE: ${e.message}", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        Log.d("TuiPan", "PlaybackService destroyed")
        panEngine.stop()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
