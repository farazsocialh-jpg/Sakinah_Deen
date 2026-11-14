package com.sakinah.deen.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sakinah.deen.data.database.entity.AdhkarEntity
import com.sakinah.deen.data.database.entity.HadithEntryEntity
import com.sakinah.deen.data.database.entity.HifzMistakeEntity
import com.sakinah.deen.data.database.entity.PrayerTimetableEntity
import com.sakinah.deen.data.database.entity.QadhaLedgerEntity
import com.sakinah.deen.data.database.entity.QuranAyahEntity
import com.sakinah.deen.data.database.entity.QuranTranslationEntity
import com.sakinah.deen.data.database.entity.SadaqahLogEntity
import com.sakinah.deen.data.database.entity.SalahRecordEntity
import com.sakinah.deen.data.database.entity.SurahMetadataEntity
import com.sakinah.deen.data.database.entity.TafsirEntryEntity
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

    /** Rebuilds one day atomically (used when re-seeding or correcting a day). */
    @Transaction
    suspend fun replaceDay(epochDay: Long, records: List<SalahRecordEntity>) {
        deleteForDay(epochDay)
        insertAll(records)
    }
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

@Dao
interface QuranDao {
    @Query("SELECT * FROM quran_ayahs WHERE surahNumber = :surahNumber ORDER BY ayahNumber ASC")
    fun observeSurah(surahNumber: Int): Flow<List<QuranAyahEntity>>

    @Query("SELECT * FROM quran_ayahs ORDER BY surahNumber ASC, ayahNumber ASC")
    fun observeAllAyahs(): Flow<List<QuranAyahEntity>>

    @Query(
        "SELECT * FROM quran_ayahs WHERE textUthmani LIKE '%' || :query || '%' " +
            "OR translation LIKE '%' || :query || '%' " +
            "ORDER BY surahNumber ASC, ayahNumber ASC LIMIT :limit"
    )
    fun searchAyahs(query: String, limit: Int = 100): Flow<List<QuranAyahEntity>>

    @Query("SELECT * FROM quran_ayahs WHERE surahNumber = :surahNumber AND ayahNumber = :ayahNumber LIMIT 1")
    suspend fun getAyah(surahNumber: Int, ayahNumber: Int): QuranAyahEntity?

    @Query("SELECT COUNT(*) FROM quran_ayahs WHERE surahNumber = :surahNumber")
    suspend fun countAyahs(surahNumber: Int): Int

    @Query("SELECT * FROM quran_translations WHERE surahNumber = :surahNumber AND translationId = :translationId ORDER BY ayahNumber ASC")
    fun observeTranslations(surahNumber: Int, translationId: Int): kotlinx.coroutines.flow.Flow<List<QuranTranslationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTranslations(translations: List<QuranTranslationEntity>)

    @Query("DELETE FROM quran_translations WHERE surahNumber = :surahNumber AND translationId = :translationId")
    suspend fun deleteTranslations(surahNumber: Int, translationId: Int)

    @Query("SELECT * FROM surah_metadata ORDER BY surahNumber ASC")
    fun observeSurahs(): Flow<List<SurahMetadataEntity>>

    @Query("SELECT COUNT(*) FROM surah_metadata")
    suspend fun countSurahs(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAyahs(ayahs: List<QuranAyahEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSurahs(surahs: List<SurahMetadataEntity>)

    @Query("DELETE FROM quran_ayahs WHERE surahNumber = :surahNumber")
    suspend fun deleteSurah(surahNumber: Int)

    /** Replaces a downloaded surah in one transaction so readers never see a half-surah. */
    @Transaction
    suspend fun replaceSurah(surahNumber: Int, ayahs: List<QuranAyahEntity>) {
        deleteSurah(surahNumber)
        upsertAyahs(ayahs)
    }
}

@Dao
interface HadithDao {
    @Query(
        "SELECT * FROM hadith_entries WHERE bookKey = :bookKey AND languageCode = :languageCode " +
            "ORDER BY hadithNumber ASC LIMIT :limit OFFSET :offset"
    )
    fun observeHadiths(
        bookKey: String,
        languageCode: String = "eng",
        limit: Int = 100000,
        offset: Int = 0
    ): Flow<List<HadithEntryEntity>>

    @Query(
        "SELECT * FROM hadith_entries WHERE bookKey = :bookKey AND languageCode = :languageCode " +
            "AND sectionNumber = :sectionNumber ORDER BY hadithNumber ASC LIMIT :limit OFFSET :offset"
    )
    fun observeHadithsBySection(
        bookKey: String,
        languageCode: String = "eng",
        sectionNumber: Int,
        limit: Int = 100000,
        offset: Int = 0
    ): Flow<List<HadithEntryEntity>>

    @Query(
        "SELECT * FROM hadith_entries " +
            "ORDER BY bookKey ASC, languageCode ASC, hadithNumber ASC LIMIT :limit OFFSET :offset"
    )
    fun observeAllHadiths(limit: Int = 100000, offset: Int = 0): Flow<List<HadithEntryEntity>>

    @Query(
        "SELECT * FROM hadith_entries WHERE englishText LIKE '%' || :query || '%' " +
            "OR arabicText LIKE '%' || :query || '%' OR chapterTitle LIKE '%' || :query || '%' " +
            "ORDER BY bookKey ASC, languageCode ASC, hadithNumber ASC LIMIT :limit"
    )
    fun searchHadiths(query: String, limit: Int = 200): Flow<List<HadithEntryEntity>>

    @Query("SELECT * FROM hadith_entries WHERE bookKey = :bookKey AND languageCode = :languageCode AND hadithNumber = :hadithNumber LIMIT 1")
    suspend fun getHadith(bookKey: String, languageCode: String = "eng", hadithNumber: Int): HadithEntryEntity?

    @Query("SELECT COUNT(*) FROM hadith_entries WHERE bookKey = :bookKey AND languageCode = :languageCode")
    suspend fun countForBook(bookKey: String, languageCode: String = "eng"): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<HadithEntryEntity>)

    @Query("DELETE FROM hadith_entries WHERE bookKey = :bookKey AND languageCode = :languageCode")
    suspend fun clearBook(bookKey: String, languageCode: String = "eng")

    /** Replaces one language edition of a collection atomically after a re-download. */
    @Transaction
    suspend fun replaceBook(bookKey: String, languageCode: String = "eng", entries: List<HadithEntryEntity>) {
        clearBook(bookKey, languageCode)
        upsertAll(entries)
    }
}

@Dao
interface AdhkarDao {
    @Query("SELECT * FROM adhkar_entries WHERE categoryKey = :categoryKey ORDER BY orderIndex ASC")
    fun observeCategory(categoryKey: String): Flow<List<AdhkarEntity>>

    @Query("SELECT COUNT(*) FROM adhkar_entries")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<AdhkarEntity>)
}

/**
 * 30-day cache of authoritative prayer timings. Rows are addressed by [cacheKey] (place,
 * method, school and month) plus the day, so a stale context never leaks into a new one.
 */
@Dao
interface PrayerTimetableDao {
    @Query(
        "SELECT * FROM prayer_timetable WHERE cacheKey = :cacheKey AND dateEpochDay = :epochDay LIMIT 1"
    )
    suspend fun getForDay(cacheKey: String, epochDay: Long): PrayerTimetableEntity?

    @Query("SELECT * FROM prayer_timetable WHERE cacheKey = :cacheKey ORDER BY dateEpochDay ASC")
    suspend fun getForKey(cacheKey: String): List<PrayerTimetableEntity>

    @Query("SELECT COUNT(*) FROM prayer_timetable WHERE cacheKey = :cacheKey")
    suspend fun countForKey(cacheKey: String): Int

    @Query("SELECT MAX(fetchedAt) FROM prayer_timetable WHERE cacheKey = :cacheKey")
    suspend fun lastFetchedAt(cacheKey: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<PrayerTimetableEntity>)

    @Query("DELETE FROM prayer_timetable WHERE cacheKey = :cacheKey")
    suspend fun deleteForKey(cacheKey: String)

    /** Keeps one place/method/month only, so switching city or authority cannot accumulate. */
    @Query("DELETE FROM prayer_timetable WHERE cacheKey != :keepKey")
    suspend fun deleteAllExcept(keepKey: String)

    @Transaction
    suspend fun replaceMonth(cacheKey: String, rows: List<PrayerTimetableEntity>) {
        deleteForKey(cacheKey)
        upsertAll(rows)
    }
}

@Dao
interface TafsirDao {
    @Query(
        "SELECT * FROM tafsir_entries WHERE sourceKey = :sourceKey " +
            "AND surahNumber = :surahNumber AND ayahNumber = :ayahNumber LIMIT 1"
    )
    suspend fun getTafsir(sourceKey: String, surahNumber: Int, ayahNumber: Int): TafsirEntryEntity?

    @Query("SELECT COUNT(*) FROM tafsir_entries WHERE sourceKey = :sourceKey AND surahNumber = :surahNumber")
    suspend fun countForSurah(sourceKey: String, surahNumber: Int): Int

    @Query("SELECT COUNT(*) FROM tafsir_entries WHERE sourceKey = :sourceKey")
    suspend fun countForSource(sourceKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<TafsirEntryEntity>)

    @Query("DELETE FROM tafsir_entries")
    suspend fun deleteAll()
}
