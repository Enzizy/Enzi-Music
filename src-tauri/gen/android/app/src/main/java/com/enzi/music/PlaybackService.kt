package com.enzi.music

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Keeps Enzi Music playing in the background. Android freezes an app that leaves the screen unless
 * a foreground service says it is playing media, so this holds one for as long as there is a track,
 * with the media notification, lock-screen controls and headphone buttons that go with it.
 *
 * mpv does the playing, in Rust. This only mirrors the track and sends presses back through
 * [MediaBridge]. State is kept in the companion so an update that arrives before the service has
 * started isn't lost.
 */
class PlaybackService : Service() {
    companion object {
        private const val CHANNEL = "playback"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_PLAY = "com.enzi.music.PLAY"
        private const val ACTION_PAUSE = "com.enzi.music.PAUSE"
        private const val ACTION_NEXT = "com.enzi.music.NEXT"
        private const val ACTION_PREVIOUS = "com.enzi.music.PREVIOUS"
        private const val ACTION_STOP = "com.enzi.music.STOP"

        private val main = Handler(Looper.getMainLooper())
        private var instance: PlaybackService? = null

        private var title = ""
        private var artist = ""
        private var album: String? = null
        private var cover: String? = null
        private var duration = 0.0
        private var playing = false
        private var position = 0.0
        private var art: Bitmap? = null
        private var artFor: String? = null

        fun setMetadata(context: Context, title: String, artist: String, album: String?, cover: String?, duration: Double) =
            main.post {
                this.title = title
                this.artist = artist
                this.album = album
                this.duration = duration
                if (cover != this.cover) {
                    this.cover = cover
                    art = null
                    artFor = null
                }
                ensureRunning(context)
                instance?.refresh()
            }

        fun setPlayback(context: Context, playing: Boolean, position: Double) = main.post {
            this.playing = playing
            this.position = position
            ensureRunning(context)
            instance?.refresh()
        }

        /** Started once, while the app is on screen, which is the only time Android allows it. */
        private fun ensureRunning(context: Context) {
            if (instance != null || title.isEmpty()) return
            val intent = Intent(context, PlaybackService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.w("EnziMusic", "couldn't start the playback service", e)
            }
        }
    }

    private lateinit var session: MediaSession
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        session = MediaSession(this, "EnziMusic").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = send("play")
                override fun onPause() = send("pause")
                override fun onSkipToNext() = send("next")
                override fun onSkipToPrevious() = send("previous")
                override fun onStop() = send("stop")
                override fun onSeekTo(pos: Long) = send("seek", pos / 1000.0)
            })
            setSessionActivity(openApp())
            isActive = true
        }
        instance = this
        refresh()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> send("play")
            ACTION_PAUSE -> send("pause")
            ACTION_NEXT -> send("next")
            ACTION_PREVIOUS -> send("previous")
            ACTION_STOP -> {
                send("pause")
                stop()
            }
        }
        // Android must see startForeground soon after a start, whatever brought us here.
        refresh()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        releaseLocks()
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    /** The user swiped the app away: stop with it rather than play on with no way back. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        send("pause")
        stop()
    }

    private fun stop() {
        stopForegroundCompat()
        stopSelf()
    }

    private fun send(action: String, value: Double = 0.0) {
        try {
            MediaBridge.onAction(action, value)
        } catch (e: Throwable) {
            android.util.Log.w("EnziMusic", "media action $action didn't reach Rust", e)
        }
    }

    private fun refresh() {
        val meta = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, album ?: "")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, (duration * 1000).toLong())
        art?.let { meta.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
        session.setMetadata(meta.build())

        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    (position * 1000).toLong(),
                    if (playing) 1f else 0f
                )
                .build()
        )

        // Paused, it stays a foreground service too: Android won't let an app in the background
        // become one again, so letting go here would leave the play button unable to resume.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification())
            }
        } catch (e: Exception) {
            android.util.Log.w("EnziMusic", "couldn't stay in the foreground", e)
        }
        if (playing) holdLocks() else releaseLocks()
        loadArt()
    }

    private fun notification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        val toggle = if (playing) action(android.R.drawable.ic_media_pause, "Pause", ACTION_PAUSE)
        else action(android.R.drawable.ic_media_play, "Play", ACTION_PLAY)
        return builder
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(art)
            .setContentIntent(openApp())
            .setDeleteIntent(pending(ACTION_STOP))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(playing)
            .addAction(action(android.R.drawable.ic_media_previous, "Previous", ACTION_PREVIOUS))
            .addAction(toggle)
            .addAction(action(android.R.drawable.ic_media_next, "Next", ACTION_NEXT))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    @Suppress("DEPRECATION")
    private fun action(icon: Int, label: String, action: String) =
        Notification.Action.Builder(icon, label, pending(action)).build()

    private fun pending(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(), Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun loadArt() {
        val url = cover ?: return
        if (artFor == url) return
        artFor = url
        thread(name = "cover") {
            val bitmap = try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
            main.post {
                // A newer track may have taken over while this one downloaded.
                if (bitmap != null && cover == url) {
                    art = bitmap
                    instance?.refresh()
                }
            }
        }
    }

    // Without these the CPU and Wi-Fi sleep with the screen, and the stream stops a minute later.
    @SuppressLint("WakelockTimeout")
    private fun holdLocks() {
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EnziMusic:playback")
                .apply { setReferenceCounted(false); acquire() }
        }
        if (wifiLock == null) {
            @Suppress("DEPRECATION")
            wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "EnziMusic:playback")
                .apply { setReferenceCounted(false); acquire() }
        }
    }

    private fun releaseLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
    }
}
