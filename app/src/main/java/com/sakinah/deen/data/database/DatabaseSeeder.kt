package com.sakinah.deen.data.database

import android.content.Context
import android.util.Log
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sakinah.deen.content.SeedAdhkar
import com.sakinah.deen.content.SeedAyah
import com.sakinah.deen.content.SeedCategory
import com.sakinah.deen.content.SeedHadith
import com.sakinah.deen.content.SeedHadithDocument
import com.sakinah.deen.content.SeedHisnDocument
import com.sakinah.deen.content.SeedQuranDocument
import com.sakinah.deen.content.SeedSurah
import com.sakinah.deen.data.database.entity.AdhkarEntity
import com.sakinah.deen.data.database.entity.HadithEntryEntity
import com.sakinah.deen.data.database.entity.QuranAyahEntity
import com.sakinah.deen.data.database.entity.SurahMetadataEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Loads the bundled offline seed datasets (Quran Juz Amma + Al-Fatihah, the 40
 * Nawawi hadith, and essential adhkar) into the encrypted database the first
 * time it is created, so no screen is empty before any download.
 *
 * Text provenance is recorded in `docs/resources.md`; nothing here is typed from
 * memory — the assets are generated from Quran.com and the hadith CDN.
 */
object DatabaseSeeder {

    private const val TAG = "SakinahSeeder"

    private const val QURAN_ASSET = "seed_quran_juz_amma.json"
    private const val HADITH_ASSET = "seed_hadith_nawawi.json"
    private const val HISN_ASSET = "seed_hisn_muslim.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

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
                    val result = runCatching { seed(appContext, database) }
                    if (result.isSuccess) {
                        Log.i(TAG, "Offline seed complete: ${result.getOrNull()}")
                        return@launch
                    }
                    delay(250L * (attempt + 1))
                }
                Log.w(TAG, "Offline seed did not complete; screens fall back to on-demand sync")
            }
        }
    }

    /** Inserts all bundled content. Safe to call again: every insert is a REPLACE upsert. */
    suspend fun seed(context: Context, database: SakinahEncryptedDatabase): String {
        val surahCount = seedQuran(context, database)
        val hadithCount = seedHadith(context, database)
        val adhkarCount = seedAdhkar(context, database)
        return "$surahCount surahs, $hadithCount hadith, $adhkarCount adhkar"
    }

    private suspend fun seedQuran(context: Context, database: SakinahEncryptedDatabase): Int {
        val document = readAsset(context, QURAN_ASSET)
            ?.let { json.decodeFromString<SeedQuranDocument>(it) }
            ?: return 0

        val ayahs = document.surahs.flatMap { surah ->
            surah.ayahs.map { ayah ->
                QuranAyahEntity(
                    surahNumber = surah.number,
                    ayahNumber = ayah.number,
                    verseKey = "${surah.number}:${ayah.number}",
                    textUthmani = ayah.arabic,
                    translation = ayah.translation,
                    surahNameEnglish = surah.nameEnglish,
                    surahNameArabic = surah.nameArabic,
                    revelationPlace = surah.revelationPlace
                )
            }
        }
        if (ayahs.isNotEmpty()) {
            database.quranDao().upsertAyahs(ayahs)
        }

        val surahs = document.surahs.map { surah ->
            SurahMetadataEntity(
                surahNumber = surah.number,
                nameSimple = surah.nameEnglish,
                nameArabic = surah.nameArabic,
                translatedName = surah.meaning,
                versesCount = surah.versesCount.takeIf { it > 0 } ?: surah.ayahs.size,
                revelationPlace = surah.revelationPlace
            )
        }
        if (surahs.isNotEmpty()) {
            database.quranDao().upsertSurahs(surahs)
        }
        return document.surahs.size
    }

    private suspend fun seedHadith(context: Context, database: SakinahEncryptedDatabase): Int {
        val document = readAsset(context, HADITH_ASSET)
            ?.let { json.decodeFromString<SeedHadithDocument>(it) }
            ?: return 0

        val entries = document.hadiths.mapNotNull { hadith ->
            val number = hadith.number ?: return@mapNotNull null
            HadithEntryEntity(
                bookKey = document.bookKey.ifBlank { "nawawi" },
                hadithNumber = number,
                bookTitle = document.title,
                sectionNumber = 0,
                chapterTitle = "",
                arabicText = hadith.arabic,
                englishText = hadith.english,
                grade = hadith.grade,
                gradingScholar = hadith.gradingScholar
            )
        }
        if (entries.isNotEmpty()) {
            database.hadithDao().upsertAll(entries)
        }
        return entries.size
    }

    private suspend fun seedAdhkar(context: Context, database: SakinahEncryptedDatabase): Int {
        val document = readAsset(context, HISN_ASSET)
            ?.let { json.decodeFromString<SeedHisnDocument>(it) }
            ?: return 0

        val entries = document.categories.flatMap { category ->
            category.items.mapIndexedNotNull { index, item ->
                if (item.arabic.isBlank()) return@mapIndexedNotNull null
                AdhkarEntity(
                    id = item.id.ifBlank { "${category.key}_$index" },
                    categoryKey = category.key,
                    categoryTitle = category.title,
                    title = item.title,
                    arabic = item.arabic,
                    translation = item.translation,
                    reference = item.reference,
                    orderIndex = index
                )
            }
        }
        if (entries.isNotEmpty()) {
            database.adhkarDao().upsertAll(entries)
        }
        return entries.size
    }

    private fun readAsset(context: Context, name: String): String? = runCatching {
        context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrElse {
        Log.w(TAG, "Missing bundled asset $name: ${it.message}")
        null
    }

}
