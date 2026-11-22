package com.sakinah.deen.service

import androidx.core.net.toUri

import android.annotation.SuppressLint

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.util.Log
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
 * "As-salatu khayrun min an-nawm"). All recordings are bundled, so playback works with no
 * network and even after the app process was killed.
 *
 * Audio is announced as notification content (USAGE_NOTIFICATION), never as an alarm, and
 * the reminder card is the notification built by [PrayerNotificationFactory]. Muting — from
 * the card, a swipe, or a hardware volume key — stops the sound immediately while the card
 * itself stays in the shade with its live countdown and Mark as Prayed action.
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
        const val EXTRA_WINDOW_START_MILLIS = "extra_window_start_millis"
        const val EXTRA_WINDOW_END_MILLIS = "extra_window_end_millis"
        const val NOTIFICATION_ID = 4001

        /** Observable flag so the Activity can gate volume-key handling on real playback. */
        @Volatile
        var isAdhanActive: Boolean = false
            private set

        /**
         * Live service handle. Broadcast actions arrive in the same process, so muting through
         * this reference avoids background `startService` restrictions on Android 12+.
         */
        @Volatile
        private var activeInstance: AdhanPlaybackService? = null

        private const val FADE_STEPS = 20
        private const val FADE_STEP_MS = 250L
        private const val SNOOZE_MINUTES = 10
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"

        /**
         * How long a mute request stays valid while the service is still starting. A volume key
         * pressed between the alarm firing and the service becoming active used to be lost.
         */
        private const val MUTE_REQUEST_GRACE_MS = 5_000L

        /**
         * Deadline of a mute request that arrived before playback was ready. Written from the
         * receiver/Activity thread and read on the service's main thread.
         */
        @Volatile
        private var pendingMuteDeadline: Long = 0L

        /**
         * Mute from the card, swipe or volume keys: stop the sound, keep the reminder.
         *
         * When no instance is active yet (the alarm just fired, or the foreground service is
         * still being created) the request is remembered briefly and honoured as soon as the
         * playback action is handled, instead of being dropped.
         */
        fun mutePlaybackIfActive() {
            val instance = activeInstance
            if (instance != null) {
                instance.mutePlayback()
            } else {
                pendingMuteDeadline = SystemClock.elapsedRealtime() + MUTE_REQUEST_GRACE_MS
            }
        }

        /** Consumes a mute request that arrived before the service was ready. */
        private fun consumePendingMute(): Boolean {
            val deadline = pendingMuteDeadline
            if (deadline == 0L) return false
            pendingMuteDeadline = 0L
            return SystemClock.elapsedRealtime() <= deadline
        }

        /** Clears any mute that was requested but never consumed by a playback start. */
        fun clearPendingMute() {
            pendingMuteDeadline = 0L
        }

        /** Stop audio and drop the card (swipe-away, Mark as Prayed). */
        fun stopPlaybackIfActive() {
            activeInstance?.stopPlaybackAndNotification()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var fadeJob: Job? = null

    @Volatile private var isPlayingAdhan: Boolean = false
    private var volumeReceiver: BroadcastReceiver? = null
    private var volumeReceiverRegistered: Boolean = false
    private val foregroundGate by lazy { AdhanForegroundGate(this) }

    private var currentPrayerName: String = "Salah"
    private var currentPrayerType: PrayerType = PrayerType.DHUHR
    private var currentMode: PrayerAlertMode = PrayerAlertMode.FULL_AZAAN
    private var currentMuezzin: Muezzin = Muezzin.MAKKAH
    private var currentWindowStart: Long = 0L
    private var currentWindowEnd: Long = 0L
    private var playerPolicyStopsOnEnd: Boolean = true
    private var isDuaSafar: Boolean = false
    private lateinit var preferences: SakinahPreferences

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
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
                currentWindowStart = intent.getLongExtra(
                    EXTRA_WINDOW_START_MILLIS,
                    System.currentTimeMillis()
                )
                currentWindowEnd = intent.getLongExtra(
                    EXTRA_WINDOW_END_MILLIS,
                    currentWindowStart + 10 * 60_000L
                )
                // Live preferences win over the extras baked in when the alarm was armed:
                // the user may have switched the mode, muezzin or master switch since then.
                currentMode = preferences.alertModeFor(currentPrayerType)
                currentMuezzin = preferences.azaanSettings.muezzin
                isDuaSafar = false

                // A volume key pressed while the service was still being created silences this
                // Adhan too, instead of leaving it playing unexpectedly.
                if (consumePendingMute()) {
                    promoteToForegroundAndStop()
                    return START_NOT_STICKY
                }

                if (!preferences.alarmsEnabled ||
                    currentMode == PrayerAlertMode.OFF ||
                    (currentMode != PrayerAlertMode.FULL_AZAAN && currentMode != PrayerAlertMode.SHORT_TONE)
                ) {
                    // OFF/Silent/Vibrate are handled by the alarm receiver as plain cards.
                    // OFF must still satisfy the foreground-service contract before stopping.
                    promoteToForegroundAndStop()
                    return START_NOT_STICKY
                }

                if (!startPlaybackForeground(buildPrayerNotification())) {
                    return START_NOT_STICKY
                }
                if (currentMode == PrayerAlertMode.FULL_AZAAN) {
                    playCue(
                        uri = selectedAdhanUri(),
                        stopWhenPlaybackEnds = true
                    )
                } else {
                    // Short chime: the sound-enabled channel rings, so no extra audio here.
                    finishTonePlayback()
                }
            }

            ACTION_PLAY_DUA_SAFAR -> {
                isDuaSafar = true
                currentMode = PrayerAlertMode.FULL_AZAAN
                if (consumePendingMute()) {
                    promoteToForegroundAndStop()
                    return START_NOT_STICKY
                }
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
                stopPlaybackAndNotification()
            }

            ACTION_MUTE_ADHAN -> mutePlayback()

            ACTION_STOP_ADHAN -> stopPlaybackAndNotification()
        }

        return START_NOT_STICKY
    }

    private fun selectedAdhanUri(): Uri {
        preferences.adhanSoundUri?.takeIf { it.isNotBlank() }?.let { return it.toUri() }
        return AdhanAudio.muezzinUri(
            context = this,
            muezzin = currentMuezzin,
            fajrSpecialAdhan = preferences.azaanSettings.fajrSpecialAdhan,
            prayerTypeName = currentPrayerType.name
        )
    }

    private fun resolveDuaSafarUri(): Uri =
        preferences.duaSafarSoundUri?.takeIf { it.isNotBlank() }?.let { it.toUri() }
            ?: "android.resource://$packageName/${R.raw.dua_al_safar}".toUri()

    /**
     * Entering the foreground can be refused (Android 14+ background-start rules, revoked
     * FGS/media permission). In that case post the reminder as a normal notification and
     * stop instead of letting the exception crash the process.
     */
    /**
     * The media-playback foreground-service type. The constant only exists from API 29; on
     * API 28 ServiceCompat drops the argument anyway, so 0 keeps that path unchanged.
     */
    private fun mediaPlaybackForegroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }

    private fun startPlaybackForeground(notification: Notification): Boolean {
        val promoted = foregroundGate.enter(notification) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                mediaPlaybackForegroundType(),
            )
        }
        if (!promoted) {
            // Foreground promotion refused (Android 12/14+): fall back to the sound channel so
            // the user never gets a silent vibration-only reminder when FULL_AZAAN is selected.
            postFallbackSoundNotification()
            stopSelf()
        }
        return promoted
    }

    @SuppressLint("MissingPermission")
    private fun postFallbackSoundNotification() {
        runCatching {
            val notif = PrayerNotificationFactory.build(
                context = this,
                spec = PrayerNotificationFactory.Spec(
                    prayerType = currentPrayerType,
                    prayerName = currentPrayerName,
                    startMillis = currentWindowStart,
                    endMillis = currentWindowEnd,
                    alertMode = PrayerAlertMode.SHORT_TONE,
                    criticalFajrChannel = runCatching {
                        preferences.azaanSettings.criticalFajrChannel
                    }.getOrDefault(false)
                )
            )
            androidx.core.app.NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID, notif)
        }
    }

    private fun playCue(uri: Uri, stopWhenPlaybackEnds: Boolean) {
        val exo = ensurePlayer(stopWhenPlaybackEnds) ?: run {
            // Audio focus/codec initialisation failed: keep the reminder visible, no audio.
            finishTonePlayback()
            return
        }
        // Mark the session active and listen for volume keys *before* the first frame of audio:
        // a key press during preparation must still mute.
        isPlayingAdhan = true
        isAdhanActive = true
        registerVolumeReceiver()

        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.volume = 1.0f
        runCatching { exo.prepare() }
        exo.play()

        fadeJob?.cancel()
        fadeJob = null
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
                    // Notification content, not an alarm: hardware volume/mute keys, Do Not
                    // Disturb and audio focus all follow the notification stream.
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                setHandleAudioBecomingNoisy(true)
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            if (stopWhenPlaybackEnds) stopPlaybackAndNotification() else finishTonePlayback()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        // A missing/corrupt recording must not crash or leave a silent
                        // foreground service: fall back to the sound channel so the user
                        // still hears a notification, then keep the reminder card.
                        Log.w("AdhanPlaybackService", "ExoPlayer error; falling back to sound channel", error)
                        postFallbackSoundNotification()
                        finishTonePlayback()
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

    /**
     * Mute: release the audio immediately (and its focus) but keep the reminder card in the
     * shade, so the countdown and Mark as Prayed stay usable. Future prayers are unaffected.
     */
    private fun mutePlayback() {
        isPlayingAdhan = false
        isAdhanActive = false
        unregisterVolumeReceiver()
        fadeJob?.cancel()
        fadeJob = null
        runCatching { player?.stop() }
        PrayerAlertSilencer.cancelVibration(this)
        // DETACH leaves the notification posted instead of removing it.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    /** Stop audio and remove the card (swipe-away, Mark as Prayed, playback finished). */
    private fun stopPlaybackAndNotification() {
        isPlayingAdhan = false
        isAdhanActive = false
        unregisterVolumeReceiver()
        fadeJob?.cancel()
        fadeJob = null
        runCatching { player?.stop() }
        PrayerAlertSilencer.cancelVibration(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        NotificationChannels.cancelPrayerNotification(this)
        stopSelf()
    }

    /** Short tone/silent leave the prayer notification visible after foreground stops. */
    private fun finishTonePlayback() {
        isPlayingAdhan = false
        isAdhanActive = false
        unregisterVolumeReceiver()
        fadeJob?.cancel()
        fadeJob = null
        runCatching { player?.stop() }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    /**
     * Satisfies Android's foreground-service start contract on early-exit paths (no action,
     * OFF mode) without leaving a stale notification behind.
     */
    private fun promoteToForegroundAndStop() {
        val notification = NotificationCompat.Builder(this, NotificationChannels.CHANNEL_PRAYER_SILENT)
            .setSmallIcon(R.drawable.ic_stat_app)
            .setContentTitle(getString(R.string.adhan_body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                mediaPlaybackForegroundType(),
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

    /**
     * The card is the same rich reminder the alarm receiver posts: countdown, progress,
     * rotating reminder, Mute and Mark as Prayed, all themed from the active palette.
     */
    private fun buildPrayerNotification(): Notification =
        PrayerNotificationFactory.build(
            context = this,
            spec = PrayerNotificationFactory.Spec(
                prayerType = currentPrayerType,
                prayerName = currentPrayerName,
                startMillis = currentWindowStart,
                endMillis = currentWindowEnd,
                alertMode = currentMode,
                criticalFajrChannel = runCatching {
                    preferences.azaanSettings.criticalFajrChannel
                }.getOrDefault(false)
            )
        )

    private fun buildDuaSafarNotification(): Notification =
        NotificationCompat.Builder(this, NotificationChannels.CHANNEL_TRAVEL_SAFAR)
            .setSmallIcon(R.drawable.ic_stat_app)
            .setContentTitle(getString(R.string.safar_title))
            .setContentText(getString(R.string.safar_dua_arabic))
            .setContentIntent(contentPendingIntent())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                R.drawable.ic_stat_app,
                getString(R.string.adhan_action_stop),
                actionPendingIntent(1, ACTION_STOP_ADHAN)
            )
            .build()

    /**
     * Hardware volume keys: while Azaan audio plays, any volume change (up, down or mute)
     * silences it. Only registered during playback and removed as soon as it stops.
     */
    @Synchronized
    private fun registerVolumeReceiver() {
        if (volumeReceiverRegistered) return
        val receiver = volumeReceiver ?: object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == VOLUME_CHANGED_ACTION && isPlayingAdhan) {
                    mutePlayback()
                }
            }
        }.also { volumeReceiver = it }
        try {
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(VOLUME_CHANGED_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            volumeReceiverRegistered = true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Registration can race a teardown; the flag stays false so a later start retries.
            Log.w("AdhanPlaybackService", "Could not register the volume-key receiver", e)
            volumeReceiver = null
            volumeReceiverRegistered = false
        }
    }

    @Synchronized
    private fun unregisterVolumeReceiver() {
        val receiver = volumeReceiver
        if (receiver != null && volumeReceiverRegistered) {
            runCatching { unregisterReceiver(receiver) }
                .onFailure { Log.w("AdhanPlaybackService", "Volume receiver already gone", it) }
        }
        volumeReceiver = null
        volumeReceiverRegistered = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isPlayingAdhan = false
        isAdhanActive = false
        if (activeInstance === this) activeInstance = null
        clearPendingMute()
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
