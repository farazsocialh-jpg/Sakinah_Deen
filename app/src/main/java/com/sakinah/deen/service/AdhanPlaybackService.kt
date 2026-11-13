package com.sakinah.deen.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.sakinah.deen.MainActivity
import com.sakinah.deen.R
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prefs.SakinahPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground Adhan playback built on Media3/ExoPlayer.
 *
 * Fajr uses the recording that includes "As-salatu khayrun min an-nawm"; the other
 * prayers use the standard Makkah Adhan. Both are bundled, so playback works with
 * no network and even after the app process was killed.
 */
class AdhanPlaybackService : Service() {

    companion object {
        const val ACTION_PLAY_ADHAN = "com.sakinah.deen.ACTION_PLAY_ADHAN"
        const val ACTION_STOP_ADHAN = "com.sakinah.deen.ACTION_STOP_ADHAN"
        const val ACTION_MUTE_ADHAN = "com.sakinah.deen.ACTION_MUTE_ADHAN"
        const val ACTION_SNOOZE = "com.sakinah.deen.ACTION_SNOOZE"
        const val ACTION_PLAY_DUA_SAFAR = "com.sakinah.deen.ACTION_PLAY_DUA_SAFAR"

        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_PRAYER_TYPE = "extra_prayer_type"
        const val NOTIFICATION_ID = 4001

        private const val FADE_STEPS = 20
        private const val FADE_STEP_MS = 250L
        private const val SNOOZE_MINUTES = 10
        private const val MISSING_AUDIO_STOP_DELAY_MS = 4000L
    }

    private enum class PlaybackSource(val rawResourceId: Int) {
        ADHAN_MAKKAH(R.raw.adhan_makkah),
        ADHAN_FAJR(R.raw.adhan_fajr),
        DUA_SAFAR(R.raw.dua_al_safar)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var fadeJob: Job? = null

    @Volatile private var isPlayingAdhan: Boolean = false
    private var volumeReceiver: BroadcastReceiver? = null

    private var currentPrayerName: String = "Salah"
    private var currentPrayerType: PrayerType = PrayerType.DHUHR
    private var isDuaSafar: Boolean = false
    private lateinit var preferences: SakinahPreferences

    override fun onCreate() {
        super.onCreate()
        preferences = SakinahPreferences(this)
        NotificationChannels.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_PLAY_ADHAN -> {
                currentPrayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: getString(R.string.nav_salah)
                currentPrayerType = intent.getStringExtra(EXTRA_PRAYER_TYPE)
                    ?.let { name -> runCatching { PrayerType.valueOf(name) }.getOrNull() }
                    ?: PrayerType.DHUHR
                isDuaSafar = false

                startPlaybackForeground(buildAdhanNotification(currentPrayerName))
                playCue(
                    if (currentPrayerType == PrayerType.FAJR) PlaybackSource.ADHAN_FAJR
                    else PlaybackSource.ADHAN_MAKKAH
                )
            }

            ACTION_PLAY_DUA_SAFAR -> {
                isDuaSafar = true
                startPlaybackForeground(buildDuaSafarNotification())
                playCue(PlaybackSource.DUA_SAFAR)
            }

            ACTION_SNOOZE -> {
                PrayerAlarmScheduler(
                    this,
                    com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl(),
                    preferences
                ).scheduleSnooze(
                    prayerType = currentPrayerType,
                    prayerName = currentPrayerName,
                    minutes = SNOOZE_MINUTES
                )
                finishPlayback()
            }

            ACTION_MUTE_ADHAN -> {
                player?.volume = 0f
            }

            ACTION_STOP_ADHAN -> finishPlayback()
        }

        return START_NOT_STICKY
    }

    private fun startPlaybackForeground(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    private fun playCue(source: PlaybackSource) {
        val uri = resolveCueUri(source)
        if (uri == null) {
            scope.launch {
                delay(MISSING_AUDIO_STOP_DELAY_MS)
                finishPlayback()
            }
            return
        }

        val exo = ensurePlayer()
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.volume = 0f
        exo.prepare()
        exo.play()
        isPlayingAdhan = true
        registerVolumeReceiver()

        fadeJob?.cancel()
        fadeJob = scope.launch {
            for (step in 1..FADE_STEPS) {
                delay(FADE_STEP_MS)
                val activePlayer = player ?: break
                activePlayer.volume = step.toFloat() / FADE_STEPS.toFloat()
            }
        }
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }

        val exoPlayer = ExoPlayer.Builder(this)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                setHandleAudioBecomingNoisy(true)
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            finishPlayback()
                        }
                    }
                })
            }

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(contentPendingIntent())
            .build()

        player = exoPlayer
        return exoPlayer
    }

    /**
     * A user-supplied recording wins; otherwise the bundled raw resource is used.
     */
    private fun resolveCueUri(source: PlaybackSource): Uri? {
        val override = when (source) {
            PlaybackSource.DUA_SAFAR -> preferences.duaSafarSoundUri
            PlaybackSource.ADHAN_MAKKAH -> preferences.adhanSoundUri
            PlaybackSource.ADHAN_FAJR -> preferences.adhanSoundUri
        }
        if (!override.isNullOrBlank()) {
            return Uri.parse(override)
        }
        return Uri.parse("android.resource://$packageName/${source.rawResourceId}")
    }

    private fun finishPlayback() {
        isPlayingAdhan = false
        unregisterVolumeReceiver()
        fadeJob?.cancel()
        fadeJob = null
        try {
            player?.stop()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Ignore release races.
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun contentPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun actionPendingIntent(requestCode: Int, action: String): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, AdhanPlaybackService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildAdhanNotification(prayerName: String): Notification =
        NotificationCompat.Builder(this, NotificationChannels.CHANNEL_SALAH_ADHAN)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            // Title format requested for the prayer-time notification, e.g. "Maghrib prayer time started".
            .setContentTitle("$prayerName prayer time started")
            .setContentText(getString(R.string.adhan_body))
            .setContentIntent(contentPendingIntent())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.adhan_action_stop),
                actionPendingIntent(1, ACTION_STOP_ADHAN)
            )
            .addAction(
                android.R.drawable.ic_lock_silent_mode,
                getString(R.string.adhan_action_mute),
                actionPendingIntent(2, ACTION_MUTE_ADHAN)
            )
            .addAction(
                android.R.drawable.ic_menu_recent_history,
                getString(R.string.adhan_action_snooze),
                actionPendingIntent(3, ACTION_SNOOZE)
            )
            .build()

    private fun buildDuaSafarNotification(): Notification =
        NotificationCompat.Builder(this, NotificationChannels.CHANNEL_TRAVEL_SAFAR)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.safar_title))
            .setContentText(getString(R.string.safar_dua_arabic))
            .setContentIntent(contentPendingIntent())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.adhan_action_stop),
                actionPendingIntent(1, ACTION_STOP_ADHAN)
            )
            .build()

    private fun registerVolumeReceiver() {
        if (volumeReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (isPlayingAdhan) finishPlayback()
            }
        }
        volumeReceiver = receiver
        try {
            registerReceiver(receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Ignore registration races.
        }
    }

    private fun unregisterVolumeReceiver() {
        volumeReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) { /* ignore */ }
        }
        volumeReceiver = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isPlayingAdhan = false
        unregisterVolumeReceiver()
        fadeJob?.cancel()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        scope.cancel()
        super.onDestroy()
    }
}
