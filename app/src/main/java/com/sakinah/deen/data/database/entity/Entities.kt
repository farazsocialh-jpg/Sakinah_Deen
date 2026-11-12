package com.sakinah.deen.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "salah_records",
    primaryKeys = ["dateEpochDay", "prayerType"]
)
data class SalahRecordEntity(
    val dateEpochDay: Long,
    val prayerType: String,
    val isCompleted: Boolean,
    val isJamaah: Boolean,
    val isQadha: Boolean,
    val completedTimestamp: Long? = null,
    val nawafilCompleted: List<String> = emptyList()
)

@Entity(tableName = "qadha_ledger")
data class QadhaLedgerEntity(
    @PrimaryKey
    val prayerType: String,
    val totalMissed: Int,
    val totalMadeUp: Int,
    val updatedTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "hifz_mistakes",
    primaryKeys = ["surahNumber", "ayahNumber", "wordPosition"]
)
data class HifzMistakeEntity(
    val surahNumber: Int,
    val ayahNumber: Int,
    val wordPosition: Int,
    val mistakeType: String, // RED_MEMORY, YELLOW_TAJWEED, GREEN_MASTERED
    val lastReviewedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "sadaqah_logs")
data class SadaqahLogEntity(
    @PrimaryKey
    val id: String,
    val amount: Double,
    val currency: String = "USD",
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Cached Quran text. Populated from the free Quran.com v4 API so the reader works
 * offline once a surah has been downloaded.
 */
@Entity(
    tableName = "quran_ayahs",
    primaryKeys = ["surahNumber", "ayahNumber"]
)
data class QuranAyahEntity(
    val surahNumber: Int,
    val ayahNumber: Int,
    val verseKey: String,
    val textUthmani: String,
    val translation: String? = null,
    val transliteration: String? = null,
    val audioUrl: String? = null,
    val surahNameEnglish: String? = null,
    val surahNameArabic: String? = null,
    val revelationPlace: String? = null,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "surah_metadata")
data class SurahMetadataEntity(
    @PrimaryKey
    val surahNumber: Int,
    val nameSimple: String,
    val nameArabic: String,
    val translatedName: String,
    val versesCount: Int,
    val revelationPlace: String? = null
)

/**
 * Hadith text ingested from the free fawazahmed0 CDN editions (Sahih al-Bukhari,
 * Sahih Muslim, 40 Hadith Nawawi, and the remaining Kutub al-Tis'ah sources).
 */
@Entity(
    tableName = "hadith_entries",
    primaryKeys = ["bookKey", "hadithNumber"]
)
data class HadithEntryEntity(
    val bookKey: String,
    val hadithNumber: Int,
    val bookTitle: String,
    val sectionNumber: Int = 0,
    val chapterTitle: String = "",
    val arabicText: String = "",
    val englishText: String = "",
    val grade: String = "",
    val gradingScholar: String = "",
    val downloadedAt: Long = System.currentTimeMillis()
)

/**
 * Bundled adhkar/du'a seeded from assets on first launch (Hisn al-Muslim essentials).
 */
@Entity(tableName = "adhkar_entries")
data class AdhkarEntity(
    @PrimaryKey
    val id: String,
    val categoryKey: String,
    val categoryTitle: String,
    val title: String,
    val arabic: String,
    val translation: String,
    val reference: String,
    val orderIndex: Int = 0
)

/**
 * One day of authoritative (online) prayer timings for a single calculation context.
 *
 * A monthly AlAdhan calendar is cached as ~30 rows sharing one [cacheKey], which encodes
 * the coordinates, calculation method, Asr school and month. Changing any of those (or
 * moving into a new month) therefore misses the cache and triggers a refresh instead of
 * silently serving timings computed for a different place.
 *
 * Times are stored as `HH:mm` wall-clock strings in [timeZone]. [source] is
 * [com.sakinah.deen.data.prayer.PrayerTimeSource.name]: `ONLINE` rows came from AlAdhan,
 * `OFFLINE_FALLBACK` rows are the local astronomical calculation written while offline.
 */
@Entity(
    tableName = "prayer_timetable",
    primaryKeys = ["cacheKey", "dateEpochDay"],
    indices = [Index("dateEpochDay")]
)
data class PrayerTimetableEntity(
    val cacheKey: String,
    val dateEpochDay: Long,
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val methodName: String,
    val methodId: Int,
    val schoolId: Int,
    val timeZone: String = "",
    val source: String = "ONLINE",
    val fetchedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "tafsir_entries",
    primaryKeys = ["sourceKey", "surahNumber", "ayahNumber"]
)
data class TafsirEntryEntity(
    val sourceKey: String,
    val sourceName: String,
    val surahNumber: Int,
    val ayahNumber: Int,
    val languageCode: String = "en",
    val text: String,
    val downloadedAt: Long = System.currentTimeMillis()
)
