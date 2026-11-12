package com.sakinah.deen.network

import com.sakinah.deen.data.database.dao.HadithDao
import com.sakinah.deen.data.database.dao.QuranDao
import com.sakinah.deen.data.database.dao.TafsirDao
import com.sakinah.deen.data.database.entity.HadithEntryEntity
import com.sakinah.deen.data.database.entity.QuranAyahEntity
import com.sakinah.deen.data.database.entity.SurahMetadataEntity
import com.sakinah.deen.data.database.entity.TafsirEntryEntity
import com.sakinah.deen.network.model.ChapterAudioResponse
import com.sakinah.deen.network.model.ChaptersResponse
import com.sakinah.deen.network.model.TafsirResponse
import com.sakinah.deen.network.model.TranslationsResponse
import com.sakinah.deen.network.model.UthmaniVersesResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Zero-cost Sunni content ingestion.
 *
 * Sources (all free, no self-hosted server):
 *  - Quran.com API v4: Uthmani text, translations, chapter recitation audio, tafsir.
 *  - fawazahmed0 hadith-api on jsDelivr: pre-structured Kutub al-Tis'ah editions.
 *
 * UmmahAPI (api.ummahapi.com) was evaluated and is currently unreachable (HTTP 526),
 * so it is intentionally not a runtime dependency.
 */
class IslamicDataRepository(
    private val quranDao: QuranDao,
    private val hadithDao: HadithDao,
    private val tafsirDao: TafsirDao,
    private val client: HttpClient,
    private val json: Json
) {

    companion object {
        const val QURAN_API_BASE = "https://api.quran.com/api/v4"
        const val HADITH_CDN_BASE = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1"

        /** Quran.com retired translation id 131; 20 = Saheeh International (verified live). */
        const val DEFAULT_TRANSLATION_ID = 20
        const val DEFAULT_RECITER_ID = 7 // Mishari Rashid al-`Afasy
        const val IBN_KATHIR_TAFSIR_ID = 169

        val SUPPORTED_HADITH_BOOKS = listOf(
            "bukhari", "muslim", "nawawi", "abudawud",
            "tirmidhi", "nasai", "ibnmajah", "malik"
        )

        private const val USER_AGENT = "Sakinah-Android/1.0 (offline-first Islamic companion)"
        private const val INSERT_CHUNK_SIZE = 400
        private val HTML_TAG = Regex("<[^>]+>")
        private val WHITESPACE = Regex("\\s+")
    }

    // ------------------------------------------------------------------ Quran

    /** Downloads one surah (Uthmani text + translation + optional audio) into Room. */
    suspend fun syncSurah(
        surahNumber: Int,
        translationId: Int = DEFAULT_TRANSLATION_ID,
        withAudio: Boolean = true,
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = runCatching {
        require(surahNumber in 1..114) { "Surah number must be between 1 and 114" }
        onProgress(0.05f)

        val chapter = runCatching { fetchChapters() }
            .getOrDefault(emptyList())
            .firstOrNull { it.id == surahNumber }
        onProgress(0.20f)

        val verses = json.decodeFromString<UthmaniVersesResponse>(
            fetch("$QURAN_API_BASE/quran/verses/uthmani?chapter_number=$surahNumber")
        ).verses
        if (verses.isEmpty()) error("Quran.com returned no verses for surah $surahNumber")
        onProgress(0.55f)

        // The translations endpoint returns the chapter in order, without verse keys,
        // so it is index-aligned with the Uthmani verse list above.
        val translations = runCatching {
            json.decodeFromString<TranslationsResponse>(
                fetch("$QURAN_API_BASE/quran/translations/$translationId?chapter_number=$surahNumber")
            ).translations
        }.getOrDefault(emptyList())
        onProgress(0.80f)

        val audioUrl = if (withAudio) {
            runCatching {
                json.decodeFromString<ChapterAudioResponse>(
                    fetch("$QURAN_API_BASE/chapter_recitations/$DEFAULT_RECITER_ID/$surahNumber")
                ).audioFile?.audioUrl
            }.getOrNull()
        } else {
            null
        }

        val ayahs = verses.mapIndexed { index, verse ->
            QuranAyahEntity(
                surahNumber = surahNumber,
                ayahNumber = index + 1,
                verseKey = verse.verseKey.ifBlank { "$surahNumber:${index + 1}" },
                textUthmani = verse.textUthmani,
                translation = translations.getOrNull(index)?.text?.let(::cleanText),
                audioUrl = audioUrl,
                surahNameEnglish = chapter?.nameSimple,
                surahNameArabic = chapter?.nameArabic,
                revelationPlace = chapter?.revelationPlace
            )
        }

        quranDao.upsertAyahs(ayahs)
        chapter?.let { quranDao.upsertSurahs(listOf(it.toEntity())) }
        onProgress(1f)
        ayahs.size
    }

    suspend fun syncChapters(): Result<Int> = runCatching {
        val chapters = fetchChapters()
        quranDao.upsertSurahs(chapters.map { it.toEntity() })
        chapters.size
    }

    fun observeSurah(surahNumber: Int): Flow<List<QuranAyahEntity>> =
        quranDao.observeSurah(surahNumber)

    fun observeSurahs(): Flow<List<SurahMetadataEntity>> = quranDao.observeSurahs()

    /** Full-text search over the locally cached Uthmani text and its translation. */
    fun searchAyahs(query: String, limit: Int = 100): Flow<List<QuranAyahEntity>> =
        quranDao.searchAyahs(query, limit)

    suspend fun isSurahAvailable(surahNumber: Int): Boolean =
        quranDao.countAyahs(surahNumber) > 0

    // ------------------------------------------------------------------ Hadith

    /**
     * Downloads a full hadith collection. Arabic is fetched from the matching
     * `ara-` edition and joined by hadith number; missing Arabic degrades gracefully.
     */
    suspend fun syncHadithBook(
        bookKey: String,
        languagePrefix: String = "eng",
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = runCatching {
        require(bookKey in SUPPORTED_HADITH_BOOKS) { "Unsupported hadith book: $bookKey" }
        onProgress(0.05f)

        val primaryBody = runCatching {
            fetch("$HADITH_CDN_BASE/editions/$languagePrefix-$bookKey.json")
        }.getOrElse {
            fetch("$HADITH_CDN_BASE/editions/ara-$bookKey.json")
        }
        onProgress(0.45f)

        val arabicByNumber = if (languagePrefix == "ara") {
            emptyMap()
        } else {
            runCatching {
                parseArabicTexts(fetch("$HADITH_CDN_BASE/editions/ara-$bookKey.json"))
            }.getOrDefault(emptyMap())
        }
        onProgress(0.70f)

        val entries = parseHadithEdition(primaryBody, bookKey, arabicByNumber)
        if (entries.isEmpty()) error("No hadith parsed for $bookKey")

        entries.chunked(INSERT_CHUNK_SIZE).forEach { hadithDao.upsertAll(it) }
        onProgress(1f)
        entries.size
    }

    fun observeHadiths(
        bookKey: String,
        limit: Int = 200,
        offset: Int = 0
    ): Flow<List<HadithEntryEntity>> = hadithDao.observeHadiths(bookKey, limit, offset)

    /** Every locally seeded/ downloaded collection, used for the "All Books" filter. */
    fun observeAllHadiths(limit: Int = 500, offset: Int = 0): Flow<List<HadithEntryEntity>> =
        hadithDao.observeAllHadiths(limit, offset)

    fun searchHadiths(query: String, limit: Int = 200): Flow<List<HadithEntryEntity>> =
        hadithDao.searchHadiths(query, limit)

    suspend fun getHadith(bookKey: String, hadithNumber: Int): HadithEntryEntity? =
        hadithDao.getHadith(bookKey, hadithNumber)

    suspend fun isBookAvailable(bookKey: String): Boolean =
        hadithDao.countForBook(bookKey) > 0

    // ------------------------------------------------------------------ Tafsir

    /**
     * Downloads Ibn Kathir (abridged) tafsir for a surah range. Requests are serial
     * with a small delay to stay a polite consumer of the free API.
     */
    suspend fun syncTafsirForSurah(
        surahNumber: Int,
        ayahRange: IntRange? = null,
        tafsirId: Int = IBN_KATHIR_TAFSIR_ID,
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = runCatching {
        val cachedAyahCount = quranDao.countAyahs(surahNumber)
        val range = ayahRange
            ?: 1..(if (cachedAyahCount > 0) cachedAyahCount else 7)

        val sourceKey = "tafsir_$tafsirId"
        val collected = mutableListOf<TafsirEntryEntity>()
        var resolvedName = "Ibn Kathir (Abridged)"

        range.forEachIndexed { index, ayahNumber ->
            runCatching {
                val dto = json.decodeFromString<TafsirResponse>(
                    fetch("$QURAN_API_BASE/tafsirs/$tafsirId/by_ayah/$surahNumber:$ayahNumber")
                ).tafsir ?: return@runCatching null

                if (dto.resourceName.isNotBlank()) resolvedName = dto.resourceName
                val text = cleanText(dto.text)
                if (text.isBlank()) {
                    null
                } else {
                    TafsirEntryEntity(
                        sourceKey = sourceKey,
                        sourceName = resolvedName,
                        surahNumber = surahNumber,
                        ayahNumber = ayahNumber,
                        text = text
                    )
                }
            }.getOrNull()?.let { collected.add(it) }

            onProgress((index + 1).toFloat() / range.count().toFloat())
            delay(120)
        }

        if (collected.isNotEmpty()) {
            tafsirDao.upsertAll(collected)
        }
        collected.size
    }

    suspend fun getTafsir(
        surahNumber: Int,
        ayahNumber: Int,
        tafsirId: Int = IBN_KATHIR_TAFSIR_ID
    ): TafsirEntryEntity? = tafsirDao.getTafsir("tafsir_$tafsirId", surahNumber, ayahNumber)

    // ------------------------------------------------------------------ Parsing

    private suspend fun fetchChapters(): List<com.sakinah.deen.network.model.ChapterDto> =
        json.decodeFromString<ChaptersResponse>(
            fetch("$QURAN_API_BASE/chapters?language=en")
        ).chapters

    private suspend fun fetch(url: String): String {
        val response = client.get(url) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "application/json")
        }
        if (!response.status.isSuccess()) {
            error("HTTP ${response.status.value} from $url")
        }
        return response.bodyAsText()
    }

    private fun parseArabicTexts(body: String): Map<Int, String> {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyMap()
        val hadiths = root["hadiths"] as? JsonArray ?: return emptyMap()
        return hadiths.mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            val number = intOf(entry["hadithnumber"]) ?: return@mapNotNull null
            val text = (entry["text"] as? JsonPrimitive)?.contentOrNull?.let(::cleanText)
            if (text.isNullOrBlank()) null else number to text
        }.toMap()
    }

    private fun parseHadithEdition(
        body: String,
        bookKey: String,
        arabicByNumber: Map<Int, String>
    ): List<HadithEntryEntity> {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        val metadata = root["metadata"] as? JsonObject
        val bookTitle = (metadata?.get("name") as? JsonPrimitive)?.contentOrNull
            ?: bookKey.replaceFirstChar { it.uppercase() }

        val sections = (metadata?.get("sections") as? JsonObject)
            ?.mapNotNull { (key, value) ->
                val name = (value as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                key to cleanText(name)
            }
            ?.toMap()
            .orEmpty()

        val hadiths = root["hadiths"] as? JsonArray ?: return emptyList()

        return hadiths.mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            val reference = entry["reference"] as? JsonObject
            val number = intOf(entry["hadithnumber"])
                ?: intOf(reference?.get("hadith"))
                ?: return@mapNotNull null
            val sectionNumber = intOf(reference?.get("book")) ?: 0

            val grade = (entry["grades"] as? JsonArray)
                ?.firstOrNull() as? JsonObject

            HadithEntryEntity(
                bookKey = bookKey,
                hadithNumber = number,
                bookTitle = cleanText(bookTitle),
                sectionNumber = sectionNumber,
                chapterTitle = sections[sectionNumber.toString()].orEmpty(),
                arabicText = arabicByNumber[number].orEmpty(),
                englishText = cleanText((entry["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()),
                grade = (grade?.get("grade") as? JsonPrimitive)?.contentOrNull.orEmpty(),
                gradingScholar = (grade?.get("name") as? JsonPrimitive)?.contentOrNull.orEmpty()
            )
        }
    }

    /** Hadith numbers appear as JSON numbers in most editions and strings in a few. */
    private fun intOf(element: kotlinx.serialization.json.JsonElement?): Int? {
        val primitive = element as? JsonPrimitive ?: return null
        return primitive.intOrNull ?: primitive.contentOrNull?.trim()?.toIntOrNull()
    }

    private fun cleanText(raw: String): String =
        raw.replace(HTML_TAG, " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace(WHITESPACE, " ")
            .trim()

    private fun com.sakinah.deen.network.model.ChapterDto.toEntity(): SurahMetadataEntity =
        SurahMetadataEntity(
            surahNumber = id,
            nameSimple = nameSimple,
            nameArabic = nameArabic,
            translatedName = translatedName?.name.orEmpty(),
            versesCount = versesCount,
            revelationPlace = revelationPlace
        )
}
