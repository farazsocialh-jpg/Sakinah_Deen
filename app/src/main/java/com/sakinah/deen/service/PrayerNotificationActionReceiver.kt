package com.sakinah.deen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.sakinah.deen.data.database.DatabasePassphraseProvider
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.data.database.dao.SalahDao
import com.sakinah.deen.data.database.entity.SalahRecordEntity
import com.sakinah.deen.data.model.PrayerType
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Handles the prayer card's buttons and its swipe-away dismissal.
 *
 * Mute and swipe both silence the Azaan immediately; Mute deliberately leaves the card in
 * the shade so the reminder — and its live countdown / "Mark as Prayed" — is still available.
 * Mark as Prayed writes the Room record with goAsync() so SalahScreen's checkbox is already
 * ticked the next time the app is opened.
 */
class PrayerNotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MUTE_ADHAN = "com.sakinah.deen.ACTION_MUTE_ADHAN"
        const val ACTION_DISMISS_PRAYER = "com.sakinah.deen.ACTION_DISMISS_PRAYER"
        const val ACTION_MARK_PRAYED = "com.sakinah.deen.ACTION_MARK_PRAYED"

        const val EXTRA_PRAYER_TYPE = "extra_prayer_type"
        const val EXTRA_PRAYER_DATE = "extra_prayer_date"

        private const val TAG = "PrayerNotifAction"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            ACTION_MUTE_ADHAN -> {
                PrayerAlertSilencer.muteAzaan(appContext)
            }

            ACTION_DISMISS_PRAYER -> {
                // Swiping the card away silences the Azaan as well; nothing is left behind.
                PrayerAlertSilencer.stopAzaan(appContext)
                NotificationChannels.cancelPrayerNotification(appContext)
            }

            ACTION_MARK_PRAYED -> {
                val prayerType = intent.getStringExtra(EXTRA_PRAYER_TYPE)
                    ?.let { name -> runCatching { PrayerType.valueOf(name) }.getOrNull() }
                if (prayerType == null) {
                    Log.w(TAG, "Mark as Prayed without a valid prayer type")
                    PrayerAlertSilencer.stopAzaan(appContext)
                    NotificationChannels.cancelPrayerNotification(appContext)
                    return
                }
                val epochDay = intent.getLongExtra(EXTRA_PRAYER_DATE, defaultEpochDay(appContext))
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        markPrayed(appContext, prayerType, epochDay)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Log.w(TAG, "Could not mark $prayerType as prayed", e)
                    } finally {
                        PrayerAlertSilencer.stopAzaan(appContext)
                        NotificationChannels.cancelPrayerNotification(appContext)
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private fun defaultEpochDay(context: Context): Long = runCatching {
        LocalDate.now(AzaanScheduling.activeZoneId(com.sakinah.deen.data.prefs.SakinahPreferences(context)))
            .toEpochDay()
    }.getOrDefault(LocalDate.now().toEpochDay())

    private suspend fun markPrayed(context: Context, prayerType: PrayerType, epochDay: Long) {
        val dao = salahDao(context)
        if (dao == null) {
            Log.w(TAG, "No SalahDao available; prayer completion not recorded")
            return
        }
        val existing = dao.getSalahRecord(epochDay, prayerType.name)
        val completed = (existing ?: SalahRecordEntity(
            dateEpochDay = epochDay,
            prayerType = prayerType.name,
            isCompleted = false,
            isJamaah = false,
            isQadha = false
        )).copy(
            isCompleted = true,
            completedTimestamp = System.currentTimeMillis()
        )
        if (existing != null) {
            dao.updateSalahRecord(completed)
        } else {
            dao.upsertSalahRecord(completed)
        }
    }

    /** Koin normally has the DAO; the encrypted database is a last-resort fallback. */
    private fun salahDao(context: Context): SalahDao? {
        runCatching { GlobalContext.getOrNull()?.get<SalahDao>() }.getOrNull()?.let { return it }
        return runCatching {
            SakinahEncryptedDatabase
                .getInstance(context, DatabasePassphraseProvider.getPassphrase(context))
                .salahDao()
        }.getOrNull()
    }
}

/** Shared silencing helpers so Mute behaves identically from the card, swipe and volume keys. */
internal object PrayerAlertSilencer {

    fun muteAzaan(context: Context) {
        AdhanPlaybackService.mutePlaybackIfActive()
        cancelVibration(context)
    }

    fun stopAzaan(context: Context) {
        AdhanPlaybackService.stopPlaybackIfActive()
        cancelVibration(context)
    }

    /** Stops any channel-driven vibration still running for the reminder card. */
    fun cancelVibration(context: Context) {
        val vibrator = vibratorOf(context) ?: return
        try {
            vibrator.cancel()
        } catch (e: SecurityException) {
            // VIBRATE is a normal install-time permission, so this is only a safety net.
        }
    }

    /** Kept for callers that want a one-shot buzz (e.g. tests or future modes). */
    fun pulse(context: Context, milliseconds: Long = 400) {
        val vibrator = vibratorOf(context) ?: return
        try {
            vibrator.vibrate(
                VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (e: SecurityException) {
            // Ignore a revoked VIBRATE permission.
        }
    }

    private fun vibratorOf(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
