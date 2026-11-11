package com.sakinah.deen.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sakinah.deen.domain.travel.TravelStatusManager

class TravelDistanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_LATITUDE = "current_latitude"
        const val KEY_LONGITUDE = "current_longitude"
    }

    override suspend fun doWork(): Result {
        val lat = inputData.getDouble(KEY_LATITUDE, 0.0)
        val lon = inputData.getDouble(KEY_LONGITUDE, 0.0)

        if (lat == 0.0 && lon == 0.0) {
            return Result.success()
        }

        val travelManager = TravelStatusManager()
        val travelState = travelManager.evaluateTravelState(Pair(lat, lon))

        // When exceeding 80 km without holding a persistent wakelock,
        // state transitions to isTraveler = true, enabling Qasr prayer shortening
        if (travelState.isTraveler) {
            // Emits notification or logs state transition
        }

        return Result.success()
    }
}
