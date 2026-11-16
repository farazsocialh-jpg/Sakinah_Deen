package com.sakinah.deen

import android.app.Application
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sakinah.deen.data.database.OfflineContentSeeder
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.repository.PrayerTimeRepository
import com.sakinah.deen.di.appModules
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngine
import com.sakinah.deen.service.NotificationChannels
import com.sakinah.deen.service.PrayerAlarmScheduler
import com.sakinah.deen.work.DailyHabitResetWorker
import com.sakinah.deen.work.KoinWorkerFactory
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin

class SakinahApp : Application(), Configuration.Provider, KoinComponent {

    private val preferences: SakinahPreferences by inject()
    private val workerFactory: KoinWorkerFactory by inject()
    private val astronomyEngine: PrayerCalculationEngine by inject()
    private val contentSeeder: OfflineContentSeeder by inject()
    private val prayerTimeRepository: PrayerTimeRepository by inject()

    /** Outlives the Activity: background refresh of the cached prayer timetable. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        if (com.sakinah.deen.BuildConfig.DEBUG) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }

        // SQLCipher native libraries must be loaded before Room opens any encrypted database.
        System.loadLibrary("sqlcipher")

        startKoin {
            androidLogger()
            androidContext(this@SakinahApp)
            modules(appModules)
        }

        // Seed the bundled Quran/hadith/adhkar content before the first screen query so a
        // fresh install works in airplane mode. Data reads wait on this (bounded).
        applicationScope.launch(Dispatchers.IO) { contentSeeder.start() }

        NotificationChannels.createChannels(this)

        // Startup disk reads stay off the main thread: StrictMode is enabled in DEBUG and
        // SharedPreferences/AlarmManager/WorkManager all touch the filesystem.
        applicationScope.launch(Dispatchers.IO) {
            scheduleDailyHabitReset()

            // Restore exact alarms on cold start only for a location the user actually saved.
            // Never arm reminders for a bootstrap/default city before onboarding.
            val coordinates = preferences.locationCoordinates
            if (coordinates != null && preferences.alarmsEnabled) {
                val scheduler =
                    PrayerAlarmScheduler(this@SakinahApp, astronomyEngine, preferences, prayerTimeRepository)
                runCatching {
                    scheduler.scheduleAllDailyAlarms(
                        coordinates = coordinates,
                        method = preferences.calculationMethodOverride,
                        madhhab = preferences.madhhab
                    )
                }
                runCatching { scheduler.scheduleAllDailyAlarmsRefreshed(coordinates = coordinates) }
            }
        }
    }

    /**
     * Seeds today's Salah slots and rolls yesterday's missed prayers into the Qadha ledger once
     * a day. KEEP preserves WorkManager's periodic schedule across app restarts.
     */
    private fun scheduleDailyHabitReset() {
        runCatching {
            val now = ZonedDateTime.now()
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            val initialDelayMillis = Duration.between(now, nextMidnight)
                .toMillis()
                .coerceAtLeast(60_000L)
            val request = PeriodicWorkRequestBuilder<DailyHabitResetWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                DailyHabitResetWorker.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
