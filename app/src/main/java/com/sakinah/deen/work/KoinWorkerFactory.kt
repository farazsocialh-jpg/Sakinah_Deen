package com.sakinah.deen.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.repository.TafseerDownloadRepository
import com.sakinah.deen.domain.travel.TravelStatusManager
import com.sakinah.deen.network.ContentDownloadWorker
import com.sakinah.deen.work.DailyHabitResetWorker
import com.sakinah.deen.work.PrayerAlarmWorker
import com.sakinah.deen.work.TravelDistanceWorker
import org.koin.core.Koin

/**
 * WorkManager instantiates workers reflectively, so constructor-injected Koin
 * dependencies must be supplied here instead of through the default factory.
 */
class KoinWorkerFactory(private val koin: Koin) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = when (workerClassName) {
        DailyHabitResetWorker::class.java.name ->
            DailyHabitResetWorker(
                appContext,
                workerParameters,
                koin.get(SakinahEncryptedDatabase::class)
            )

        ContentDownloadWorker::class.java.name ->
            ContentDownloadWorker(
                appContext,
                workerParameters,
                koin.get(TafseerDownloadRepository::class),
                koin.get(SakinahEncryptedDatabase::class)
            )

        PrayerAlarmWorker::class.java.name ->
            PrayerAlarmWorker(
                appContext,
                workerParameters
            )

        TravelDistanceWorker::class.java.name ->
            TravelDistanceWorker(
                appContext,
                workerParameters,
                koin.get(TravelStatusManager::class),
                koin.get(SakinahPreferences::class)
            )

        else -> null
    }
}
