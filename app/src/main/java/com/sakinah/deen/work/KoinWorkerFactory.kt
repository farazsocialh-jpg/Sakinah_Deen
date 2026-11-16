package com.sakinah.deen.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.repository.TafseerDownloadRepository
import com.sakinah.deen.network.ContentDownloadWorker
import org.koin.core.Koin

/**
 * WorkManager instantiates workers reflectively, so constructor-injected Koin
 * dependencies must be supplied here instead of through the default factory.
 *
 * This factory is also the registry of live workers: Azaan reminders run through
 * AlarmManager ([com.sakinah.deen.service.PrayerAlarmScheduler]), and travel distance is
 * evaluated by the Travel screen and the car receiver, so no workers are duplicated for them.
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
                koin.get(SakinahEncryptedDatabase::class),
                koin.get(SakinahPreferences::class)
            )

        ContentDownloadWorker::class.java.name ->
            ContentDownloadWorker(
                appContext,
                workerParameters,
                koin.get(TafseerDownloadRepository::class),
                koin.get(SakinahEncryptedDatabase::class)
            )

        else -> null
    }
}
