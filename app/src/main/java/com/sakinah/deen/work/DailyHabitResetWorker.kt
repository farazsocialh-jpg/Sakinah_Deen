package com.sakinah.deen.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.data.database.entity.SalahRecordEntity
import com.sakinah.deen.data.model.PrayerType
import java.time.LocalDate

class DailyHabitResetWorker(
    appContext: Context,
    params: WorkerParameters,
    private val database: SakinahEncryptedDatabase
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val yesterdayEpochDay = LocalDate.now().minusDays(1).toEpochDay()
            val todayEpochDay = LocalDate.now().toEpochDay()

            val salahDao = database.salahDao()
            val qadhaDao = database.qadhaDao()

            // Check yesterday's records; any uncompleted fard prayers become Qadha
            val requiredPrayers = listOf(
                PrayerType.FAJR.name,
                PrayerType.DHUHR.name,
                PrayerType.ASR.name,
                PrayerType.MAGHRIB.name,
                PrayerType.ISHA.name
            )

            // Seed today's fresh uncompleted prayer slots
            val todaySlots = listOf(
                SalahRecordEntity(todayEpochDay, PrayerType.FAJR.name, isCompleted = false, isJamaah = false, isQadha = false),
                SalahRecordEntity(todayEpochDay, PrayerType.SUNRISE.name, isCompleted = false, isJamaah = false, isQadha = false),
                SalahRecordEntity(todayEpochDay, PrayerType.DHUHR.name, isCompleted = false, isJamaah = false, isQadha = false),
                SalahRecordEntity(todayEpochDay, PrayerType.ASR.name, isCompleted = false, isJamaah = false, isQadha = false),
                SalahRecordEntity(todayEpochDay, PrayerType.MAGHRIB.name, isCompleted = false, isJamaah = false, isQadha = false),
                SalahRecordEntity(todayEpochDay, PrayerType.ISHA.name, isCompleted = false, isJamaah = false, isQadha = false)
            )

            salahDao.insertAll(todaySlots)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
