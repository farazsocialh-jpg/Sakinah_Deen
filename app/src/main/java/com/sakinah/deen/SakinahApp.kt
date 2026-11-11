package com.sakinah.deen

import android.app.Application
import com.sakinah.deen.di.appModules
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl
import com.sakinah.deen.service.NotificationChannels
import com.sakinah.deen.service.PrayerAlarmScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class SakinahApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@SakinahApp)
            modules(appModules)
        }

        // Initialize notification channels across all Android versions
        NotificationChannels.createChannels(this)

        // Schedule exact alarms for astronomical prayer entry times
        val scheduler = PrayerAlarmScheduler(this, PrayerCalculationEngineImpl())
        scheduler.scheduleAllDailyAlarms(Pair(51.5074, -0.1278))
    }
}
