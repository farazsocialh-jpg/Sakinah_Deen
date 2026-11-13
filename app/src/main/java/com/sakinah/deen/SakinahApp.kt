package com.sakinah.deen

import android.app.Application
import androidx.work.Configuration
import com.sakinah.deen.data.database.OfflineContentSeeder
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.repository.PrayerTimeRepository
import com.sakinah.deen.di.appModules
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngine
import com.sakinah.deen.service.NotificationChannels
import com.sakinah.deen.service.PrayerAlarmScheduler
import com.sakinah.deen.work.KoinWorkerFactory
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

        // Restore exact alarms on cold start. Coordinates come from the persisted
        // location selection; the documented default city is only a bootstrap value
        // until onboarding stores a real fix.
        val coordinates = preferences.locationCoordinates
            ?: Pair(SakinahPreferences.DEFAULT_LATITUDE, SakinahPreferences.DEFAULT_LONGITUDE)

        val scheduler = PrayerAlarmScheduler(this, astronomyEngine, preferences, prayerTimeRepository)

        // Register alarms immediately from the local astronomical table so reminders exist
        // even in airplane mode, then upgrade to the cached/online authority timetable.
        scheduler.scheduleAllDailyAlarms(coordinates = coordinates)
        applicationScope.launch {
            runCatching { scheduler.scheduleAllDailyAlarmsRefreshed(coordinates = coordinates) }
        }
    }
}
