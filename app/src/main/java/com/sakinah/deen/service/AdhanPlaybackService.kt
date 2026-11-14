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
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.sakinah.deen.MainActivity
import com.sakinah.deen.R
import com.sakinah.deen.data.model.Muezzin
import com.sakinah.deen.data.model.PrayerAlertMode
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
 * Full Azaan plays the Muezzin chosen in Settings (Fajr can use the recording with
 * "As-salatu khayrun min an-nawm"). Short Tone plays a brief chime, and Silent posts the
 * high-priority notification without audio. All recordings are bundled, so playback works
 * with no network and even after the app process was killed.
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
        const val EXTRA_ALERT_MODE = "extra_alert_mode"
        const val EXTRA_MUEZZIN_ID = "extra_muezzin_id"
        const val NOTIFICATION_ID = 4001

        /** Observable flag so the Activity can gate volume-key handling on real playback. */
        @Volatile
        var isAdhanActive: Boolean = false

        private const val FADE_STEPS = 20
        private const val FADE_STEP_MS = 250L
        private const val SNOOZE_MINUTES = 10
        private const val MISSING_AUDIO_STOP_DELAY_MS = 4000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var fadeJob: Job? = null

    @Volatile private var isPlayingAdhan: Boolean = false
    private var volumeReceiver: BroadcastReceiver? = null
    private val foregroundGate by lazy { AdhanForegroundGate(this) }

    private var currentPrayerName: String = "Salah"
    private var currentPrayerType: PrayerType = PrayerType.DHUHR
    private var currentMode: PrayerAlertMode = PrayerAlertMode.FULL_AZAAN
    private var currentMuezzin: Muezzin = Muezzin.MAKKAH
    private var playerPolicyStopsOnEnd: Boolean = true
    private var isDuaSafar: Boolean = false
    private lateinit var preferences: SakinahPreferences

    override fun onCreate() {
        super.onCreate()
        preferences = SakinahPreferences(this)
        NotificationChannels.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == null) {
            // Started as a foreground service without an action: promote immediately
            // so Android does not throw ForegroundServiceDidNotStartInTimeException, then stop.
            promoteToForegroundAndStop()
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_PLAY_ADHAN -> {
                currentPrayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: getString(R.string.nav_salah)
                currentPrayerType = intent.getStringExtra(EXTRA_PRAYER_TYPE)
                    ?.let { name -> runCatching { PrayerType.valueOf(name) }.getOrNull() }
                    ?: PrayerType.DHUHR
                currentMode = intent.getStringExtra(EXTRA_ALERT_MODE)
                    ?.let { name -> runCatching { PrayerAlertMode.valueOf(name) }.getOrNull() }
                    ?: preferences.alertModeFor(currentPrayerType)
                currentMuezzin = Muezzin.fromId(intent.getStringExtra(EXTRA_MUEZZIN_ID))
                isDuaSafar = false

                if (currentMode == PrayerAlertMode.OFF) {
                    // OFF must still satisfy the foreground-service contract before stopping.
                    promoteToForegroundAndStop()
                    return START_NOT_STICKY
                }

                if (!startPlaybackForeground(buildAdhanNotification(currentPrayerName))) {
                    return START_NOT_STICKY
                }
                when (currentMode) {
                    PrayerAlertMode.FULL_AZAAN -> playCue(
                        uri = selectedAdhanUri(),
                        stopWhenPlaybackEnds = true
                    )

                    PrayerAlertMode.SHORT_TONE -> playCue(
                        uri = AdhanAudio.shortToneUri(this),
                        stopWhenPlaybackEnds = false
                    )

                    PrayerAlertMode.SILENT -> finishTonePlayback()
                    PrayerAlertMode.OFF -> stopSelf()
                }
            }

            ACTION_PLAY_DUA_SAFAR -> {
                isDuaSafar = true
                currentMode = PrayerAlertMode.FULL_AZAAN
                if (!startPlaybackForeground(buildDuaSafarNotification())) {
                    return START_NOT_STICKY
                }
                playCue(
                    uri = resolveDuaSafarUri(),
                    stopWhenPlaybackEnds = true
                )
            }

            ACTION_SNOOZE -> {
                runCatching {
                    PrayerAlarmScheduler(
                        this,
                        com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl(),
                        preferences
                    ).scheduleSnooze(
                        prayerType = currentPrayerType,
                        prayerName = currentPrayerName,
                        minutes = SNOOZE_MINUTES
                    )
                }
                finishPlayback()
            }

            ACTION_MUTE_ADHAN -> {
                player?.volume = 0f
            }

            ACTION_STOP_ADHAN -> finishPlayback()
        }

        return START_NOT_STICKY
    }

    private fun selectedAdhanUri(): Uri {
        preferences.adhanSoundUri?.takeIf { it.isNotBlank() }?.let { return Uri.parse(it) }
        return AdhanAudio.muezzinUri(
            context = this,
            muezzin = currentMuezzin,
            fajrSpecialAdhan = preferences.azaanSettings.fajrSpecialAdhan,
            prayerTypeName = currentPrayerType.name
        )
    }

    private fun resolveDuaSafarUri(): Uri =
        preferences.duaSafarSoundUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: Uri.parse("android.resource://$packageName/${R.raw.dua_al_safar}")

    /**
     * Entering the foreground can be refused (Android 14+ background-start rules, revoked
     * FGS/media permission). In that case post the reminder as a normal notification and
     * stop instead of letting the exception crash the process.
     */
    private fun startPlaybackForeground(notification: Notification): Boolean {
        val promoted = foregroundGate.enter(notification) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        }
        if (!promoted) stopSelf()
        return promoted
    }

    private fun playCue(uri: Uri, stopWhenPlaybackEnds: Boolean) {
        val exo = ensurePlayer(stopWhenPlaybackEnds) ?: run {
            // Audio focus/codec initialisation failed: keep the reminder visible, no audio.
            finishTonePlayback()
            return
        }
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.volume = 0f
        exo.prepare()
        exo.play()
        isPlayingAdhan = true
        isAdhanActive = true
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

    private fun ensurePlayer(stopWhenPlaybackEnds: Boolean): ExoPlayer? {
        player?.let { existing ->
            // Media3 listeners are immutable, so recreate only when the stop policy changes.
            if (playerPolicyStopsOnEnd != stopWhenPlaybackEnds) {
                existing.release()
                player = null
            } else {
                return existing
            }
        }

        val exoPlayer = try {
            ExoPlayer.Builder(this).build()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return null
        }
        try {
            exoPlayer.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_ALARM)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                setHandleAudioBecomingNoisy(true)
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            if (stopWhenPlaybackEnds) finishPlayback() else finishTonePlayback()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (stopWhenPlaybackEnds) finishFallbackNotification() else finishTonePlayback()
                    }
                })
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            runCatching { exoPlayer.release() }
            return null
        }

        mediaSession?.release()
        mediaSession = try {
            MediaSession.Builder(this, exoPlayer)
                .setSessionActivity(contentPendingIntent())
                .build()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            runCatching { exoPlayer.release() }
            return null
        }

        player = exoPlayer
        playerPolicyStopsOnEnd = stopWhenPlaybackEnds
        return exoPlayer
    }

    private fun finishPlayback() {
        isPlayingAdhan = false
        isAdhanActive = false
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

    /** Short tone/silent leave the prayer notification visible after foreground stops. */
    private fun finishTonePlayback() {
        isPlayingAdhan = false
        isAdhanActive = false
        unregisterVolumeReceiver()
        fadeJob?.cancel()
        fadeJob = null
        try {
            player?.stop()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun finishFallbackNotification() {
        // A missing/corrupt recording must not crash or leave a silent foreground service.
        finishTonePlayback()
    }

    /**
     * Satisfies Android's foreground-service start contract on early-exit paths (no action,
     * OFF mode) without leaving a stale notification behind.
     */
    private fun promoteToForegroundAndStop() {
        val notification = NotificationCompat.Builder(this, NotificationChannels.CHANNEL_SALAH_ADHAN)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.adhan_body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun contentPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun actionPendingIntent(requestCode: Int, action: String): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, AdhanPlaybackService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildAdhanNotification(prayerName: String): Notification {
        val channel = if (currentMode == PrayerAlertMode.SILENT) {
            NotificationChannels.CHANNEL_SALAH_SILENT
        } else {
            NotificationChannels.CHANNEL_SALAH_ADHAN
        }
        return NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("$prayerName prayer time started")
            .setContentText(getString(R.string.adhan_body))
            .setContentIntent(contentPendingIntent())
            .setOngoing(currentMode == PrayerAlertMode.FULL_AZAAN)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(contentPendingIntent(), true)
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
    }

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
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
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
        isAdhanActive = false
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
