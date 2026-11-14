package com.sakinah.deen.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sakinah.deen.R
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.domain.travel.TravelStatusManager

class TravelDistanceWorker(
    appContext: Context,
    params: WorkerParameters,
    private val travelStatusManager: TravelStatusManager = TravelStatusManager(),
    private val sakinahPreferences: SakinahPreferences = SakinahPreferences(appContext)
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_LATITUDE = "current_latitude"
        const val KEY_LONGITUDE = "current_longitude"
        private const val TRAVEL_CHANNEL_ID = "sakinah_travel_channel"
    }

    override suspend fun doWork(): Result {
        val lat = inputData.getDouble(KEY_LATITUDE, 0.0)
        val lon = inputData.getDouble(KEY_LONGITUDE, 0.0)

        // Missing coordinates are not a successful no-op: retry/abort explicitly.
        if (lat == 0.0 && lon == 0.0) {
            return Result.failure()
        }

        val travelState = travelStatusManager.evaluateTravelState(Pair(lat, lon))

        sakinahPreferences.travelModeActive = travelState.isTraveler
        sakinahPreferences.qasrActive = travelState.isTraveler
        sakinahPreferences.lastDistanceFromHomeKm = travelState.distanceKm.toFloat()

        // When exceeding the Safar threshold, surface the transition so Qasr is visible.
        if (travelState.isTraveler) {
            val notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        TRAVEL_CHANNEL_ID,
                        "Sakinah Travel Mode",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
            val notify = NotificationCompat.Builder(applicationContext, TRAVEL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle("Travel mode enabled")
                .setContentText("You are traveling. Salah will be shortened.")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(12345, notify)
        }

        return Result.success()
    }
}
