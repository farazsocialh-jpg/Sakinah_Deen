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

    // Use targeted column-preserving upsert instead of REPLACE to avoid overwriting
    // user state (isBookmarked, lastReadAt, isCompleted, isJamaah, isQadha, completedAt, notes).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsertSalahRecord(record: SalahRecordEntity)

    @Query("SELECT * FROM salah_records WHERE dateEpochDay = :epochDay AND prayerType = :prayerType LIMIT 1")
    suspend fun getSalahRecord(epochDay: Long, prayerType: String): SalahRecordEntity?

    @Update
    suspend fun updateSalahRecord(record: SalahRecordEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(records: List<SalahRecordEntity>)

    @Query("DELETE FROM salah_records WHERE dateEpochDay = :epochDay")
    suspend fun deleteForDay(epochDay: Long)

    @Query(
        "SELECT * FROM salah_records WHERE dateEpochDay = :epochDay AND isCompleted = 0 " +
            "AND prayerType IN ('FAJR','DHUHR','ASR','MAGHRIB','ISHA')"
    )
    suspend fun getUncompletedFard(epochDay: Long): List<SalahRecordEntity>

    @Query("SELECT COUNT(*) FROM salah_records WHERE dateEpochDay = :epochDay")
    suspend fun slotCountForDay(epochDay: Long): Int

    /** Fills in missing slots for one day without touching existing user progress. */
    @Transaction
    suspend fun replaceDay(epochDay: Long, records: List<SalahRecordEntity>) {
        insertAll(records)
    }
}

@Dao
interface QadhaDao {
    @Query("SELECT * FROM qadha_ledger ORDER BY prayerType ASC")
    fun observeQadhaLedger(): Flow<List<QadhaLedgerEntity>>

    @Query("SELECT * FROM qadha_ledger ORDER BY prayerType ASC")
    suspend fun getAllQadha(): List<QadhaLedgerEntity>

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
        if (delta <= 0) return
        val existing = getQadhaForPrayer(prayerType)
        if (existing != null) {
            val credited = minOf(delta, existing.totalMissed)
            val newMissed = (existing.totalMissed - credited).coerceAtLeast(0)
            val newMadeUp = credited + existing.totalMadeUp
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

    @Query("SELECT * FROM hifz_mistakes")
    suspend fun getAllMistakes(): List<HifzMistakeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMistake(entity: HifzMistakeEntity)

    @Query("DELETE FROM hifz_mistakes WHERE surahNumber = :surahNumber AND ayahNumber = :ayahNumber")
    suspend fun deleteMistakesForAyah(surahNumber: Int, ayahNumber: Int)
}

data class SadaqahCurrencyTotal(
    val currency: String,
    val total: Double
)

@Dao
interface SadaqahDao {
    @Query("SELECT SUM(amount) FROM sadaqah_logs WHERE timestamp BETWEEN :startTimestamp AND :endTimestamp")
    fun observeMonthlyTotal(startTimestamp: Long, endTimestamp: Long): Flow<Double?>

    @Query("SELECT currency, SUM(amount) AS total FROM sadaqah_logs WHERE timestamp BETWEEN :startTimestamp AND :endTimestamp GROUP BY currency")
    fun observeMonthlyTotalsByCurrency(startTimestamp: Long, endTimestamp: Long): Flow<List<SadaqahCurrencyTotal>>

    @Query("SELECT * FROM sadaqah_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentLogs(limit: Int = 20): Flow<List<SadaqahLogEntity>>

    @Query("SELECT * FROM sadaqah_logs")
    suspend fun getAllLogs(): List<SadaqahLogEntity>

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

    @Query(
        "SELECT * FROM quran_ayahs WHERE textUthmani LIKE '%' || :query ESCAPE '\\' " +
            "OR translation LIKE '%' || :query ESCAPE '\\' " +
            "ORDER BY surahNumber ASC, ayahNumber ASC LIMIT :limit"
    )
    fun searchAyahsEscaped(query: String, limit: Int = 100): Flow<List<QuranAyahEntity>>

    @Query("SELECT * FROM quran_ayahs WHERE surahNumber = :surahNumber AND ayahNumber = :ayahNumber LIMIT 1")
    suspend fun getAyah(surahNumber: Int, ayahNumber: Int): QuranAyahEntity?

    @Query("SELECT COUNT(*) FROM quran_ayahs WHERE surahNumber = :surahNumber")
    suspend fun countAyahs(surahNumber: Int): Int

    /** Total cached ayahs; the offline-coverage banner compares this with 6,236. */
    @Query("SELECT COUNT(*) FROM quran_ayahs")
    suspend fun countAllAyahs(): Int

    /** Surahs with at least one cached ayah; the banner compares this with 114. */
    @Query("SELECT COUNT(DISTINCT surahNumber) FROM quran_ayahs")
    suspend fun countSurahsWithAyahs(): Int

    @Query("SELECT * FROM quran_translations WHERE surahNumber = :surahNumber AND translationId = :translationId ORDER BY ayahNumber ASC")
    fun observeTranslations(surahNumber: Int, translationId: Int): kotlinx.coroutines.flow.Flow<List<QuranTranslationEntity>>

    @Query("SELECT COUNT(*) FROM quran_translations WHERE surahNumber = :surahNumber AND translationId = :translationId")
    suspend fun countTranslations(surahNumber: Int, translationId: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTranslations(translations: List<QuranTranslationEntity>)

    @Query("DELETE FROM quran_translations WHERE surahNumber = :surahNumber AND translationId = :translationId")
    suspend fun deleteTranslations(surahNumber: Int, translationId: Int)

    @Query("SELECT * FROM surah_metadata ORDER BY surahNumber ASC")
    fun observeSurahs(): Flow<List<SurahMetadataEntity>>

    @Query("SELECT COUNT(*) FROM surah_metadata")
    suspend fun countSurahs(): Int

    @Query("SELECT surahNumber, ayahNumber FROM quran_ayahs WHERE isBookmarked = 1")
    suspend fun getBookmarkedKeys(): List<BookmarkKey>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAyahsRaw(ayahs: List<QuranAyahEntity>)

    /**
     * Refreshing Quran text must not wipe the user's bookmark/reading position. REPLACE
     * deletes and reinserts the row, so carry those columns over explicitly.
     */
    @Transaction
    suspend fun upsertAyahs(ayahs: List<QuranAyahEntity>) {
        ayahs.forEach { incoming ->
            val existing = getAyah(incoming.surahNumber, incoming.ayahNumber)
            insertAyahsRaw(
                listOf(
                    if (existing == null) incoming else incoming.copy(
                        isBookmarked = existing.isBookmarked,
                        lastReadAt = existing.lastReadAt
                    )
                )
            )
        }
    }

    @Query("SELECT * FROM quran_ayahs WHERE surahNumber = :surahNumber")
    suspend fun getAyahsForSurah(surahNumber: Int): List<QuranAyahEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSurahs(surahs: List<SurahMetadataEntity>)

    @Query("DELETE FROM quran_ayahs WHERE surahNumber = :surahNumber")
    suspend fun deleteSurah(surahNumber: Int)

    /** Replaces a downloaded surah in one transaction so readers never see a half-surah. */
    @Transaction
    suspend fun replaceSurah(surahNumber: Int, ayahs: List<QuranAyahEntity>) {
        val preserved = getAyahsForSurah(surahNumber)
            .associateBy { it.ayahNumber }
        deleteSurah(surahNumber)
        insertAyahsRaw(
            ayahs.map { incoming ->
                val existing = preserved[incoming.ayahNumber]
                if (existing == null) incoming else incoming.copy(
                    isBookmarked = existing.isBookmarked,
                    lastReadAt = existing.lastReadAt
                )
            }
        )
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
    fun observeAllHadiths(limit: Int = 1000, offset: Int = 0): Flow<List<HadithEntryEntity>>

    /** Lightweight catalog summary for download/status UI (no full text rows). */
    @Query("SELECT bookKey, languageCode, COUNT(*) AS count FROM hadith_entries GROUP BY bookKey, languageCode")
    fun observeHadithCatalogSummary(): Flow<List<HadithCatalogSummary>>

    /** Distinct sections for one book/edition (no full text rows). */
    @Query("SELECT DISTINCT sectionNumber, chapterTitle FROM hadith_entries WHERE bookKey = :bookKey AND languageCode = :languageCode ORDER BY sectionNumber ASC")
    fun observeHadithSections(bookKey: String, languageCode: String): Flow<List<HadithSectionSummary>>

    /** "All Books" view restricted to one language edition, so ENG <-> ARA switches every row. */
    @Query(
        "SELECT * FROM hadith_entries WHERE languageCode = :languageCode " +
            "ORDER BY bookKey ASC, hadithNumber ASC LIMIT :limit OFFSET :offset"
    )
    fun observeAllHadithsByLanguage(
        languageCode: String,
        limit: Int = 1000,
        offset: Int = 0
    ): Flow<List<HadithEntryEntity>>

    @Query(
        "SELECT * FROM hadith_entries WHERE englishText LIKE '%' || :query ESCAPE '\\' " +
            "OR arabicText LIKE '%' || :query ESCAPE '\\' " +
            "OR chapterTitle LIKE '%' || :query ESCAPE '\\' " +
            "ORDER BY bookKey ASC, languageCode ASC, hadithNumber ASC LIMIT :limit"
    )
    fun searchHadithsEscaped(query: String, limit: Int = 200): Flow<List<HadithEntryEntity>>

    @Query("SELECT * FROM hadith_entries WHERE bookKey = :bookKey AND languageCode = :languageCode AND hadithNumber = :hadithNumber LIMIT 1")
    suspend fun getHadith(bookKey: String, languageCode: String = "eng", hadithNumber: Int): HadithEntryEntity?

    @Query("SELECT COUNT(*) FROM hadith_entries WHERE bookKey = :bookKey AND languageCode = :languageCode")
    suspend fun countForBook(bookKey: String, languageCode: String = "eng"): Int

    /** Rows of one edition, used to reuse cached Arabic instead of re-downloading it. */
    @Query("SELECT * FROM hadith_entries WHERE bookKey = :bookKey AND languageCode = :languageCode")
    suspend fun getHadithByLanguage(bookKey: String, languageCode: String = "ara"): List<HadithEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<HadithEntryEntity>)

    @Query("DELETE FROM hadith_entries WHERE bookKey = :bookKey AND languageCode = :languageCode")
    suspend fun clearBook(bookKey: String, languageCode: String = "eng")

    /** Replaces one language edition of a collection atomically after a re-download. */
    @Transaction
    suspend fun replaceBook(bookKey: String, languageCode: String = "eng", entries: List<HadithEntryEntity>) {
        clearBook(bookKey, languageCode)
        // Large Urdu/Bengali editions can exceed a single bind batch. Chunking keeps the
        // transaction bounded while retaining the atomic clear-and-replace behaviour.
        entries.chunked(500).forEach { upsertAll(it) }
    }
}

@Dao
interface AdhkarDao {
    @Query("SELECT * FROM adhkar_entries WHERE categoryKey = :categoryKey ORDER BY orderIndex ASC")
    fun observeCategory(categoryKey: String): Flow<List<AdhkarEntity>>

    @Query("SELECT * FROM adhkar_entries ORDER BY categoryKey ASC, orderIndex ASC")
    fun observeAll(): Flow<List<AdhkarEntity>>

    @Query("SELECT COUNT(*) FROM adhkar_entries")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM adhkar_entries WHERE categoryKey = :categoryKey")
    suspend fun countForCategory(categoryKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<AdhkarEntity>)

    @Query("DELETE FROM adhkar_entries")
    suspend fun clearAll()

    /**
     * Replaces the whole bundled set atomically. An upsert alone would leave entries that a
     * newer seed edition dropped, so a version bump always clears first.
     */
    @Transaction
    suspend fun replaceAll(entries: List<AdhkarEntity>) {
        clearAll()
        upsertAll(entries)
    }
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
        // When single-cache-key retention is intended, delete all older entries first.
        deleteAllExcept(cacheKey)
        upsertAll(rows)
    }
}

/** Per-surah cached ayah counts used to verify an offline Tafsir pack. */
data class SurahAyahCount(
    val surahNumber: Int,
    val ayahCount: Int
)

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

    /** Grouped row counts, so a surah with only a few ayahs is never mistaken for complete. */
    @Query("SELECT surahNumber, COUNT(*) AS ayahCount FROM tafsir_entries WHERE sourceKey = :sourceKey GROUP BY surahNumber")
    suspend fun getAyahCountsBySurah(sourceKey: String): List<SurahAyahCount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<TafsirEntryEntity>)

    @Query("DELETE FROM tafsir_entries")
    suspend fun deleteAll()

    @Query("DELETE FROM tafsir_entries WHERE sourceKey = :sourceKey")
    suspend fun deleteBySource(sourceKey: String)

    /**
     * Drops one chapter so a partial or corrupt cache is replaced, never merged with
     * the retry that follows it.
     */
    @Query("DELETE FROM tafsir_entries WHERE sourceKey = :sourceKey AND surahNumber = :surahNumber")
    suspend fun deleteForSurah(sourceKey: String, surahNumber: Int)
}

data class HadithCatalogSummary(
    val bookKey: String,
    val languageCode: String,
    val count: Int
)

data class HadithSectionSummary(
    val sectionNumber: Int,
    val chapterTitle: String
)

data class BookmarkKey(
    val surahNumber: Int,
    val ayahNumber: Int
)
