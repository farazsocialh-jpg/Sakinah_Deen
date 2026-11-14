package com.sakinah.deen.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.data.database.entity.SalahRecordEntity
import com.sakinah.deen.data.model.PrayerType
import java.io.IOException
import java.time.LocalDate

class DailyHabitResetWorker(
    appContext: Context,
    params: WorkerParameters,
    private val database: SakinahEncryptedDatabase
) : CoroutineWorker(appContext, params) {

    companion object {
        /** Give transient database failures a few runs before WorkManager gives up. */
        private const val MAX_ATTEMPTS = 3
    }

    override suspend fun doWork(): Result {
        return try {
            // Compute today once to avoid midnight races between Worker runs.
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)

            val salahDao = database.salahDao()
            val qadhaDao = database.qadhaDao()

            // Qadha rollover: every uncompleted fard prayer from yesterday is added to
            // the ledger for its own prayer type.
            salahDao.getUncompletedFard(yesterday.toEpochDay()).forEach { record ->
                qadhaDao.incrementMissed(record.prayerType, 1)
            }

            // Check whether today's SalahRecordEntity slots already exist before inserting
            // so existing progress isn't overwritten on re-runs.
            val todayExists = salahDao.slotCountForDay(today.toEpochDay()) > 0
            if (!todayExists) {
                // Seed today's fresh uncompleted prayer slots
                val todaySlots = listOf(
                    SalahRecordEntity(today.toEpochDay(), PrayerType.FAJR.name, isCompleted = false, isJamaah = false, isQadha = false),
                    SalahRecordEntity(today.toEpochDay(), PrayerType.SUNRISE.name, isCompleted = false, isJamaah = false, isQadha = false),
                    SalahRecordEntity(today.toEpochDay(), PrayerType.DHUHR.name, isCompleted = false, isJamaah = false, isQadha = false),
                    SalahRecordEntity(today.toEpochDay(), PrayerType.ASR.name, isCompleted = false, isJamaah = false, isQadha = false),
                    SalahRecordEntity(today.toEpochDay(), PrayerType.MAGHRIB.name, isCompleted = false, isJamaah = false, isQadha = false),
                    SalahRecordEntity(today.toEpochDay(), PrayerType.ISHA.name, isCompleted = false, isJamaah = false, isQadha = false)
                )
                salahDao.insertAll(todaySlots)
            }

            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Room/SQLCipher write failures are usually transient (locked or low storage).
            if ((e is IOException || e is android.database.SQLException) &&
                runAttemptCount < MAX_ATTEMPTS
            ) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
