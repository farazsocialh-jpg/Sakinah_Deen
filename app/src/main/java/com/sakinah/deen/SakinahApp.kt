package com.sakinah.deen

import android.app.Application
import androidx.work.Configuration
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.di.appModules
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngine
import com.sakinah.deen.service.NotificationChannels
import com.sakinah.deen.service.PrayerAlarmScheduler
import com.sakinah.deen.work.KoinWorkerFactory
import net.sqlcipher.database.SQLiteDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin

class SakinahApp : Application(), Configuration.Provider, KoinComponent {

    private val preferences: SakinahPreferences by inject()
    private val workerFactory: KoinWorkerFactory by inject()
    private val astronomyEngine: PrayerCalculationEngine by inject()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // SQLCipher native libraries must be loaded before Room opens any encrypted database.
        SQLiteDatabase.loadLibs(this)

        startKoin {
            androidLogger()
            androidContext(this@SakinahApp)
            modules(appModules)
        }

        NotificationChannels.createChannels(this)

        // Restore exact alarms on cold start. Coordinates come from the persisted
        // location selection; the documented default city is only a bootstrap value
        // until onboarding stores a real fix.
        val coordinates = preferences.locationCoordinates
            ?: Pair(SakinahPreferences.DEFAULT_LATITUDE, SakinahPreferences.DEFAULT_LONGITUDE)

        PrayerAlarmScheduler(this, astronomyEngine, preferences)
            .scheduleAllDailyAlarms(coordinates = coordinates)
    }
}
