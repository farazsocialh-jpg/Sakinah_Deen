package com.sakinah.deen.data.database

import android.content.Context
import android.util.Log
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sakinah.deen.content.SeedAyah
import com.sakinah.deen.content.SeedCategory
import com.sakinah.deen.content.SeedHadith
import com.sakinah.deen.content.SeedHadithDocument
import com.sakinah.deen.content.SeedHisnDocument
import com.sakinah.deen.content.SeedQuranDocument
import com.sakinah.deen.content.SeedSurah
import com.sakinah.deen.data.database.dao.AdhkarDao
import com.sakinah.deen.data.database.dao.HadithDao
import com.sakinah.deen.data.database.dao.QuranDao
import com.sakinah.deen.data.database.entity.AdhkarEntity
import com.sakinah.deen.data.database.entity.HadithEntryEntity
import com.sakinah.deen.data.database.entity.QuranAyahEntity
import com.sakinah.deen.data.database.entity.SurahMetadataEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Loads the bundled offline seed datasets (Quran Juz Amma + Al-Fatihah, the 40
 * Nawawi hadith, and essential adhkar) into the encrypted database so no screen is
 * empty before any download — including on a fresh install with no connectivity.
 *
 * Text provenance is recorded in `docs/resources.md`; nothing here is typed from
 * memory — the assets are generated from Quran.com and the hadith CDN.
 */
object DatabaseSeeder {

    private const val TAG = "SakinahSeeder"

    const val QURAN_ASSET = "seed_quran_juz_amma.json"
    const val SURAH_METADATA_ASSET = "seed_quran_surahs.json"
    const val HADITH_ASSET = "seed_hadith_nawawi.json"
    const val HISN_ASSET = "seed_hisn_muslim.json"

    /** Book key the bundled hadith asset is written under. */
    const val NAWAWI_BOOK_KEY = "nawawi"

    /** Proxy of exactly the DAOs seeding touches, so the logic is testable off-device. */
    data class SeedContent(
        val quranDao: QuranDao,
        val hadithDao: HadithDao,
        val adhkarDao: AdhkarDao
    )

    /** What a seeding pass actually did. `skipped` is true when content was already present. */
    data class SeedSummary(
        val surahs: Int = 0,
        val ayahs: Int = 0,
        val hadiths: Int = 0,
        val adhkar: Int = 0,
        val skipped: Boolean = false
    ) {
        val rowsWritten: Int get() = ayahs + hadiths + adhkar
        override fun toString(): String = if (skipped) {
            "already seeded"
        } else {
            "$surahs surahs / $ayahs ayahs, $hadiths hadith, $adhkar adhkar"
        }
    }

    /** Serialises the Room `onCreate` callback and the eager startup pass. */
    private val seedMutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * Seeds whatever is missing and returns what was written. Idempotent: a second call
     * after a successful pass writes nothing, and a partially seeded database only fills
     * the empty datasets. Never throws — failures are returned as an empty summary.
     */
    suspend fun seedOnce(
        readAsset: (String) -> String?,
        content: () -> SeedContent?
    ): SeedSummary = seedMutex.withLock {
        // Resolving the database can fail (encryption factory, corrupt file); never let
        // that escape into the Room open callback or the startup coroutine.
        val target = runCatching { content() }.getOrNull() ?: return@withLock SeedSummary()
        try {
            val needsQuran = target.quranDao.countAyahs(1) <= 0
            val needsSurahMetadata = target.quranDao.countSurahs() < 114
            val needsHadith = target.hadithDao.countForBook(NAWAWI_BOOK_KEY) <= 0
            val needsAdhkar = target.adhkarDao.count() <= 0

            if (!needsQuran && !needsSurahMetadata && !needsHadith && !needsAdhkar) {
                return SeedSummary(skipped = true)
            }

            var surahs = 0
            var ayahs = 0
            if (needsQuran) {
                readAsset(QURAN_ASSET)?.let { text ->
                    applyQuranSeed(text, target.quranDao).let { (surahCount, ayahCount) ->
                        surahs = surahCount
                        ayahs = ayahCount
                    }
                }
            }
            if (needsSurahMetadata) {
                readAsset(SURAH_METADATA_ASSET)?.let { text ->
                    applyQuranSeed(text, target.quranDao).let { (surahCount, _) ->
                        if (surahs == 0) surahs = surahCount
                    }
                }
            }

            val hadiths = if (needsHadith) {
                readAsset(HADITH_ASSET)?.let { applyHadithSeed(it, target.hadithDao) } ?: 0
            } else {
                0
            }

            val adhkar = if (needsAdhkar) {
                readAsset(HISN_ASSET)?.let { applyAdhkarSeed(it, target.adhkarDao) } ?: 0
            } else {
                0
            }

            SeedSummary(surahs = surahs, ayahs = ayahs, hadiths = hadiths, adhkar = adhkar)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Offline seed failed: ${e.message}")
            SeedSummary()
        }
    }

    /** Kept for callers that want the whole bundle in one go. */
    suspend fun seed(context: Context, database: SakinahEncryptedDatabase): String =
        seedOnce(
            readAsset = assetReader(context),
            content = { seedContentOf(database) }
        ).toString()

    suspend fun applyQuranSeed(jsonText: String, quranDao: QuranDao): Pair<Int, Int> {
        val document = runCatching { json.decodeFromString<SeedQuranDocument>(jsonText) }
            .getOrElse {
                Log.w(TAG, "Unreadable Quran seed: ${it.message}")
                return 0 to 0
            }

        val ayahs = document.surahs.flatMap { surah -> surah.toAyahEntities() }
        if (ayahs.isNotEmpty()) {
            quranDao.upsertAyahs(ayahs)
        }

        val surahs = document.surahs.map { surah -> surah.toMetadataEntity() }
        if (surahs.isNotEmpty()) {
            quranDao.upsertSurahs(surahs)
        }
        return document.surahs.size to ayahs.size
    }

    suspend fun applyHadithSeed(jsonText: String, hadithDao: HadithDao): Int {
        val document = runCatching { json.decodeFromString<SeedHadithDocument>(jsonText) }
            .getOrElse {
                Log.w(TAG, "Unreadable hadith seed: ${it.message}")
                return 0
            }

        val entries = document.hadiths.mapNotNull { hadith -> hadith.toEntity(document) }
        if (entries.isNotEmpty()) {
            hadithDao.upsertAll(entries)
        }
        return entries.size
    }

    suspend fun applyAdhkarSeed(jsonText: String, adhkarDao: AdhkarDao): Int {
        val document = runCatching { json.decodeFromString<SeedHisnDocument>(jsonText) }
            .getOrElse {
                Log.w(TAG, "Unreadable adhkar seed: ${it.message}")
                return 0
            }

        val entries = document.categories.flatMap { category -> category.toEntities() }
        if (entries.isNotEmpty()) {
            adhkarDao.upsertAll(entries)
        }
        return entries.size
    }

    /**
     * Room's `onCreate` hook. The eager startup pass ([OfflineContentSeeder]) covers the
     * same ground and is what the UI waits on; this callback just avoids leaving a brand
     * new database empty if some other entry point opens it first.
     */
    fun createCallback(
        context: Context,
        databaseProvider: () -> SakinahEncryptedDatabase?
    ): RoomDatabase.Callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            val appContext = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                // onCreate fires while the connection is still opening; retry briefly
                // rather than touching DAOs mid-open.
                repeat(10) { attempt ->
                    val database = databaseProvider() ?: return@launch
                    val summary = seedOnce(
                        readAsset = assetReader(appContext),
                        content = { seedContentOf(database) }
                    )
                    if (summary.skipped || summary.rowsWritten > 0) {
                        Log.i(TAG, "Offline seed complete: $summary")
                        return@launch
                    }
                    delay(250L * (attempt + 1))
                }
                Log.w(TAG, "Offline seed did not complete; screens fall back to on-demand sync")
            }
        }
    }

    /** The DAOs seeding writes through. */
    fun seedContentOf(database: SakinahEncryptedDatabase): SeedContent =
        SeedContent(database.quranDao(), database.hadithDao(), database.adhkarDao())

    /** Reads a bundled asset by name, or null when it is missing/unreadable. */
    fun assetReader(context: Context): (String) -> String? {
        val appContext = context.applicationContext
        return { name -> readAsset(appContext, name) }
    }

    private fun SeedSurah.toAyahEntities(): List<QuranAyahEntity> = ayahs.map { ayah: SeedAyah ->
        QuranAyahEntity(
            surahNumber = number,
            ayahNumber = ayah.number,
            verseKey = "$number:${ayah.number}",
            textUthmani = ayah.arabic,
            translation = ayah.translation,
            surahNameEnglish = nameEnglish,
            surahNameArabic = nameArabic,
            revelationPlace = revelationPlace
        )
    }

    private fun SeedSurah.toMetadataEntity(): SurahMetadataEntity = SurahMetadataEntity(
        surahNumber = number,
        nameSimple = nameEnglish,
        nameArabic = nameArabic,
        translatedName = meaning,
        versesCount = versesCount.takeIf { it > 0 } ?: ayahs.size,
        revelationPlace = revelationPlace
    )

    private fun SeedHadith.toEntity(document: SeedHadithDocument): HadithEntryEntity? {
        val hadithNumber = number ?: return null
        return HadithEntryEntity(
            bookKey = document.bookKey.ifBlank { NAWAWI_BOOK_KEY },
            hadithNumber = hadithNumber,
            bookTitle = document.title,
            sectionNumber = 0,
            chapterTitle = "",
            arabicText = arabic,
            englishText = english,
            grade = grade,
            gradingScholar = gradingScholar
        )
    }

    private fun SeedCategory.toEntities(): List<AdhkarEntity> = items.mapIndexedNotNull { index, item ->
        if (item.arabic.isBlank()) return@mapIndexedNotNull null
        AdhkarEntity(
            id = item.id.ifBlank { "${key}_$index" },
            categoryKey = key,
            categoryTitle = title,
            title = item.title,
            arabic = item.arabic,
            translation = item.translation,
            reference = item.reference,
            orderIndex = index
        )
    }

    private fun readAsset(context: Context, name: String): String? = runCatching {
        context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrElse {
        Log.w(TAG, "Missing bundled asset $name: ${it.message}")
        null
    }
}
