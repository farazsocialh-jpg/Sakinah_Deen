package com.sakinah.deen.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
 * The notification exposes Stop and Snooze (10 min); playback fades in over 5
 * seconds, pauses on transient audio-focus loss (phone calls, navigation prompts)
 * and ducks when the system requests it.
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

    private enum class PlaybackSource(val rawResourceName: String) {
        ADHAN("adhan_call"),
        DUA_SAFAR("dua_safar")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var fadeJob: Job? = null

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
                currentPrayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "Salah"
                currentPrayerType = intent.getStringExtra(EXTRA_PRAYER_TYPE)
                    ?.let { name -> runCatching { PrayerType.valueOf(name) }.getOrNull() }
                    ?: PrayerType.DHUHR
                isDuaSafar = false

                startPlaybackForeground(buildAdhanNotification(currentPrayerName))
                playCue(PlaybackSource.ADHAN)
            }

            ACTION_PLAY_DUA_SAFAR -> {
                isDuaSafar = true
                startPlaybackForeground(buildDuaSafarNotification())
                playCue(PlaybackSource.DUA_SAFAR)
            }

            ACTION_SNOOZE -> {
                PrayerAlarmScheduler(this, com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl(), preferences)
                    .scheduleSnooze(
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
            // Keep the notification (and therefore the user-visible reminder) alive briefly,
            // then release the foreground slot instead of crashing on an empty data source.
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

        fadeJob?.cancel()
        fadeJob = scope.launch {
            for (step in 1..FADE_STEPS) {
                delay(FADE_STEP_MS)
                val target = player ?: break
                target.volume = step.toFloat() / FADE_STEPS.toFloat()
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

    private fun resolveCueUri(source: PlaybackSource): Uri? {
        val bundled = NotificationChannels.resolveRawSound(this, source.rawResourceName)
        if (bundled != null) return bundled

        val override = when (source) {
            PlaybackSource.ADHAN -> preferences.adhanSoundUri
            PlaybackSource.DUA_SAFAR -> preferences.duaSafarSoundUri
        }
        return override?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    }

    private fun finishPlayback() {
        fadeJob?.cancel()
        fadeJob = null
        try {
            player?.stop()
        } catch (e: Exception) {
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
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Adhan: Time for $prayerName Prayer")
            .setContentText("حي على الصلاة ، حي على الفلاح — Come to Prayer, Come to Success")
            .setContentIntent(contentPendingIntent())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_pause, "Stop", actionPendingIntent(1, ACTION_STOP_ADHAN))
            .addAction(android.R.drawable.ic_lock_silent_mode, "Mute", actionPendingIntent(2, ACTION_MUTE_ADHAN))
            .addAction(android.R.drawable.ic_menu_recent_history, "Snooze 10m", actionPendingIntent(3, ACTION_SNOOZE))
            .build()

    private fun buildDuaSafarNotification(): Notification =
        NotificationCompat.Builder(this, NotificationChannels.CHANNEL_TRAVEL_SAFAR)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Safar: Dua al-Safar")
            .setContentText("سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَٰذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ")
            .setContentIntent(contentPendingIntent())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_media_pause, "Stop", actionPendingIntent(1, ACTION_STOP_ADHAN))
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        fadeJob?.cancel()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        scope.cancel()
        super.onDestroy()
    }
}
