package com.sakinah.deen.work

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.data.database.entity.SalahRecordEntity
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prefs.SakinahPreferences
import java.io.IOException
import java.time.LocalDate

/**
 * Daily rollover: adds yesterday's missed fard prayers to the Qadha ledger and seeds today's
 * empty Salah slots.
 *
 * The whole write runs in one Room transaction and the rollover is recorded by date, so a
 * retried run (or a duplicated WorkManager request) can never increment Qadha counts twice.
 */
class DailyHabitResetWorker(
    appContext: Context,
    params: WorkerParameters,
    private val database: SakinahEncryptedDatabase,
    private val preferences: SakinahPreferences = SakinahPreferences(appContext)
) : CoroutineWorker(appContext, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "sakinah_daily_habit_reset"

        /** Give transient database failures a few runs before WorkManager gives up. */
        private const val MAX_ATTEMPTS = 3

        /** A device that was off for weeks must not roll an unbounded number of days at once. */
        private const val MAX_ROLLOVER_DAYS = 7
    }

    override suspend fun doWork(): Result {
        return try {
            // Compute today once to avoid midnight races between Worker runs.
            val today = LocalDate.now()
            val todayEpochDay = today.toEpochDay()
            val yesterdayEpochDay = today.minusDays(1).toEpochDay()

            database.withTransaction {
                val salahDao = database.salahDao()
                val qadhaDao = database.qadhaDao()

                // Catch up every unrolled day since the last successful run, but cap at a week
                // (a first run has no anchor at all and must not loop from Long.MIN_VALUE).
                val lastRollover = preferences.lastQadhaRolloverEpochDay
                if (lastRollover < yesterdayEpochDay) {
                    val firstDay = (lastRollover + 1)
                        .coerceAtLeast(yesterdayEpochDay - MAX_ROLLOVER_DAYS + 1L)
                    for (epochDay in firstDay..yesterdayEpochDay) {
                        salahDao.getUncompletedFard(epochDay).forEach { record ->
                            qadhaDao.incrementMissed(record.prayerType, 1)
                        }
                    }
                }

                // Seed today's fresh slots only when none exist, so existing progress survives.
                if (salahDao.slotCountForDay(todayEpochDay) == 0) {
                    salahDao.insertAll(
                        listOf(
                            SalahRecordEntity(todayEpochDay, PrayerType.FAJR.name, isCompleted = false, isJamaah = false, isQadha = false),
                            SalahRecordEntity(todayEpochDay, PrayerType.SUNRISE.name, isCompleted = false, isJamaah = false, isQadha = false),
                            SalahRecordEntity(todayEpochDay, PrayerType.DHUHR.name, isCompleted = false, isJamaah = false, isQadha = false),
                            SalahRecordEntity(todayEpochDay, PrayerType.ASR.name, isCompleted = false, isJamaah = false, isQadha = false),
                            SalahRecordEntity(todayEpochDay, PrayerType.MAGHRIB.name, isCompleted = false, isJamaah = false, isQadha = false),
                            SalahRecordEntity(todayEpochDay, PrayerType.ISHA.name, isCompleted = false, isJamaah = false, isQadha = false)
                        )
                    )
                }
            }

            // Only mark the rollover done after the transaction committed, so a rollback
            // re-runs the increment instead of silently skipping it.
            preferences.lastQadhaRolloverEpochDay = yesterdayEpochDay

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
