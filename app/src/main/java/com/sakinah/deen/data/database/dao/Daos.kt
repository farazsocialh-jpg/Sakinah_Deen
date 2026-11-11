package com.sakinah.deen.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sakinah.deen.data.database.entity.HifzMistakeEntity
import com.sakinah.deen.data.database.entity.QadhaLedgerEntity
import com.sakinah.deen.data.database.entity.SadaqahLogEntity
import com.sakinah.deen.data.database.entity.SalahRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SalahDao {
    @Query("SELECT * FROM salah_records WHERE dateEpochDay = :epochDay")
    fun observeTodaySalah(epochDay: Long): Flow<List<SalahRecordEntity>>

    @Query("SELECT COUNT(*) FROM salah_records WHERE dateEpochDay BETWEEN :startDay AND :endDay AND isCompleted = 1")
    fun observeCompletedCountBetween(startDay: Long, endDay: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM salah_records WHERE dateEpochDay BETWEEN :startDay AND :endDay")
    fun observeTotalCountBetween(startDay: Long, endDay: Long): Flow<Int>

    @Query("SELECT * FROM salah_records WHERE isQadha = 1 ORDER BY dateEpochDay DESC")
    fun observePendingQadhaRecords(): Flow<List<SalahRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSalahRecord(record: SalahRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<SalahRecordEntity>)

    @Query("DELETE FROM salah_records WHERE dateEpochDay = :epochDay")
    suspend fun deleteForDay(epochDay: Long)
}

@Dao
interface QadhaDao {
    @Query("SELECT * FROM qadha_ledger ORDER BY prayerType ASC")
    fun observeQadhaLedger(): Flow<List<QadhaLedgerEntity>>

    @Query("SELECT * FROM qadha_ledger WHERE prayerType = :prayerType LIMIT 1")
    suspend fun getQadhaForPrayer(prayerType: String): QadhaLedgerEntity?

    @Query("SELECT * FROM qadha_ledger WHERE prayerType = :prayerType LIMIT 1")
    fun observeQadhaForPrayer(prayerType: String): Flow<QadhaLedgerEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQadha(entity: QadhaLedgerEntity)

    @Transaction
    suspend fun incrementMissed(prayerType: String, delta: Int = 1) {
        val existing = getQadhaForPrayer(prayerType)
        if (existing != null) {
            upsertQadha(
                existing.copy(
                    totalMissed = existing.totalMissed + delta,
                    updatedTimestamp = System.currentTimeMillis()
                )
            )
        } else {
            upsertQadha(
                QadhaLedgerEntity(
                    prayerType = prayerType,
                    totalMissed = delta,
                    totalMadeUp = 0,
                    updatedTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    @Transaction
    suspend fun decrementMissed(prayerType: String, delta: Int = 1) {
        val existing = getQadhaForPrayer(prayerType)
        if (existing != null) {
            val newMissed = (existing.totalMissed - delta).coerceAtLeast(0)
            val newMadeUp = existing.totalMadeUp + delta
            upsertQadha(
                existing.copy(
                    totalMissed = newMissed,
                    totalMadeUp = newMadeUp,
                    updatedTimestamp = System.currentTimeMillis()
                )
            )
        }
    }
}

@Dao
interface HifzDao {
    @Query("SELECT * FROM hifz_mistakes WHERE surahNumber = :surahNumber AND ayahNumber = :ayahNumber ORDER BY wordPosition ASC")
    fun observeMistakesForAyah(surahNumber: Int, ayahNumber: Int): Flow<List<HifzMistakeEntity>>

    @Query("SELECT * FROM hifz_mistakes WHERE lastReviewedTimestamp <= :cutoffTimestamp ORDER BY lastReviewedTimestamp ASC")
    fun observeSpacedRepetitionCandidates(cutoffTimestamp: Long): Flow<List<HifzMistakeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMistake(entity: HifzMistakeEntity)

    @Query("DELETE FROM hifz_mistakes WHERE surahNumber = :surahNumber AND ayahNumber = :ayahNumber")
    suspend fun deleteMistakesForAyah(surahNumber: Int, ayahNumber: Int)
}

@Dao
interface SadaqahDao {
    @Query("SELECT SUM(amount) FROM sadaqah_logs WHERE timestamp BETWEEN :startTimestamp AND :endTimestamp")
    fun observeMonthlyTotal(startTimestamp: Long, endTimestamp: Long): Flow<Double?>

    @Query("SELECT * FROM sadaqah_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentLogs(limit: Int = 20): Flow<List<SadaqahLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SadaqahLogEntity)

    @Query("DELETE FROM sadaqah_logs WHERE id = :id")
    suspend fun deleteLog(id: String)
}
