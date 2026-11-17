package com.yourname.terminalpan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

class PlaybackService : Service() {

    private var exoPlayer: ExoPlayer? = null
    lateinit var panProcessor: PanAudioProcessor
        private set
    lateinit var panEngine: PanEngine
        private set

    private val channelId = "tuipan_playback_channel"
    private val notificationId = 101

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("TuiPan", "PlaybackService onCreate started")

        createNotificationChannel()
        
        // Force foreground immediately
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("TuiPan")
            .setContentText("Audio engine active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(notificationId, notification)

        panProcessor = PanAudioProcessor()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                Log.d("TuiPan", "Building AudioSink with PanProcessor")
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(panProcessor))
                    .build()
            }
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val dataSourceFactory = DefaultDataSource.Factory(this)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(audioAttributes, true)
            .build()
            
        Log.d("TuiPan", "ExoPlayer built successfully")

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("TuiPan", "SERVICE PLAYER ERROR: ${error.message}", error)
            }
            override fun onPlaybackStateChanged(state: Int) {
                Log.d("TuiPan", "SERVICE PLAYBACK STATE: $state")
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d("TuiPan", "SERVICE IS PLAYING: $isPlaying")
            }
        })

        panEngine = PanEngine(panProcessor) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TuiPan", "PlaybackService onStartCommand")
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "TuiPan Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        Log.d("TuiPan", "PlaybackService destroyed")
        panEngine.stop()
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }
}
