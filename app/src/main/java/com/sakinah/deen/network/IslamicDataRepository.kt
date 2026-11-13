package com.sakinah.deen.network

import com.sakinah.deen.data.database.OfflineContentSeeder
import com.sakinah.deen.data.database.dao.HadithDao
import com.sakinah.deen.data.database.dao.QuranDao
import com.sakinah.deen.data.database.dao.TafsirDao
import com.sakinah.deen.data.database.entity.HadithEntryEntity
import com.sakinah.deen.data.database.entity.QuranAyahEntity
import com.sakinah.deen.data.database.entity.QuranTranslationEntity
import com.sakinah.deen.data.model.HadithBookCatalog
import com.sakinah.deen.data.model.HadithEditionResource
import com.sakinah.deen.data.model.QuranTranslationResource
import com.sakinah.deen.data.database.entity.SurahMetadataEntity
import com.sakinah.deen.data.database.entity.TafsirEntryEntity
import com.sakinah.deen.network.model.ChapterAudioResponse
import com.sakinah.deen.network.model.ChaptersResponse
import com.sakinah.deen.network.model.TafsirResponse
import com.sakinah.deen.network.model.TranslationsResponse
import com.sakinah.deen.network.model.UthmaniVersesResponse
import com.sakinah.deen.network.model.VerseByChapterResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
    private val json: Json,
    /**
     * First-launch seeding gate. When present, reads wait for the bundled Quran/hadith
     * content to be inserted, so a fresh install offline already has data (bounded wait).
     */
    private val offlineContent: OfflineContentSeeder? = null,
    /** Optional context used to cache the translation catalog for offline use. */
    private val appContext: android.content.Context? = null
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
        private val HTML_TAG = Regex("<[^>]+>")
        private val WHITESPACE = Regex("\\s+")

        private fun cleanText(raw: String): String =
            raw.replace(HTML_TAG, " ")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace(WHITESPACE, " ")
                .trim()

        private val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }

        fun parseQuranComTranslations(
            body: String,
            json: Json = defaultJson
        ): List<QuranTranslationResource> {
            val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyList()
            val translations = root["translations"] as? JsonArray ?: return emptyList()
            return translations.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val id = (obj["id"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                QuranTranslationResource(
                    id = id,
                    name = (obj["name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    authorName = (obj["author_name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    languageName = (obj["language_name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    isoCode = (obj["iso_code"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                )
            }
        }

        fun parseAlQuranCloudTranslations(
            body: String,
            json: Json = defaultJson
        ): List<QuranTranslationResource> {
            val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyList()
            val editions = root["data"] as? JsonArray ?: return emptyList()
            return editions.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val format = (obj["format"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val type = (obj["type"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                if (format != "text" || type != "translation") return@mapNotNull null
                QuranTranslationResource(
                    id = 0,
                    name = (obj["englishName"] as? JsonPrimitive)?.contentOrNull
                        ?.ifBlank { (obj["name"] as? JsonPrimitive)?.contentOrNull.orEmpty() }
                        .orEmpty(),
                    authorName = (obj["name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    languageName = (obj["language"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    isoCode = (obj["language"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                )
            }
        }
        fun parseHadithCatalog(
            editionsBody: String,
            infoBody: String? = null,
            json: Json = defaultJson
        ): List<HadithBookCatalog> {
            val editionsRoot = runCatching { json.parseToJsonElement(editionsBody) as? JsonObject }.getOrNull() ?: return emptyList()
            val infoRoot = infoBody?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            return editionsRoot.mapNotNull { (bookKey, value) ->
                val obj = value as? JsonObject ?: return@mapNotNull null
                val bookTitle = (obj["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val collection = obj["collection"] as? JsonArray ?: JsonArray(emptyList())
                val editions = collection.mapNotNull { element ->
                    val edition = element as? JsonObject ?: return@mapNotNull null
                    val editionName = (edition["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    if (editionName.isBlank()) return@mapNotNull null
                    HadithEditionResource(
                        editionName = editionName,
                        bookKey = (edition["book"] as? JsonPrimitive)?.contentOrNull.orEmpty().ifBlank { bookKey },
                        bookTitle = bookTitle,
                        languageCode = editionName.substringBefore('-'),
                        languageName = (edition["language"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                        hasSections = (edition["has_sections"] as? JsonPrimitive)?.contentOrNull == "true",
                        direction = (edition["direction"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                        link = (edition["linkmin"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    )
                }
                val sectionsObject = ((infoRoot?.get(bookKey) as? JsonObject)
                    ?.get("metadata") as? JsonObject)
                    ?.get("sections") as? JsonObject
                val sections = sectionsObject?.mapNotNull { (key, value) ->
                    val name = (value as? JsonPrimitive)?.contentOrNull?.let(::cleanText).orEmpty()
                    val number = key.toIntOrNull() ?: return@mapNotNull null
                    if (name.isBlank()) null else number to name
                }?.toMap().orEmpty()
                HadithBookCatalog(
                    bookKey = bookKey,
                    bookTitle = bookTitle,
                    editions = editions,
                    sections = sections
                )
            }
        }

    }
    // ------------------------------------------------------------------ Quran

    /** Downloads one surah (Uthmani text + selected translation + optional audio) into Room. */
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

        val response = json.decodeFromString<VerseByChapterResponse>(
            fetch(
                "$QURAN_API_BASE/verses/by_chapter/$surahNumber" +
                    "?translations=$translationId&fields=text_uthmani&per_page=300"
            )
        )
        val verses = response.verses
        if (verses.isEmpty()) error("Quran.com returned no verses for surah $surahNumber")
        onProgress(0.55f)

        val audioUrl = if (withAudio) {
            runCatching {
                json.decodeFromString<ChapterAudioResponse>(
                    fetch("$QURAN_API_BASE/chapter_recitations/$DEFAULT_RECITER_ID/$surahNumber")
                ).audioFile?.audioUrl
            }.getOrNull()
        } else {
            null
        }

        val ayahs = verses.map { verse ->
            QuranAyahEntity(
                surahNumber = surahNumber,
                ayahNumber = verse.verseNumber,
                verseKey = verse.verseKey.ifBlank { "$surahNumber:${verse.verseNumber}" },
                textUthmani = verse.textUthmani,
                translation = verse.translations.firstOrNull()?.text?.let(::cleanText),
                audioUrl = audioUrl,
                surahNameEnglish = chapter?.nameSimple,
                surahNameArabic = chapter?.nameArabic,
                revelationPlace = chapter?.revelationPlace
            )
        }

        val translations = verses.mapNotNull { verse ->
            val text = verse.translations.firstOrNull()?.text?.let(::cleanText).orEmpty()
            if (text.isBlank()) null else QuranTranslationEntity(
                surahNumber = surahNumber,
                ayahNumber = verse.verseNumber,
                translationId = translationId,
                translation = text
            )
        }

        quranDao.replaceSurah(surahNumber, ayahs)
        if (translations.isNotEmpty()) quranDao.upsertTranslations(translations)
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
        observeSurah(surahNumber, DEFAULT_TRANSLATION_ID)

    fun observeSurah(surahNumber: Int, translationId: Int): Flow<List<QuranAyahEntity>> =
        combine(
            afterSeeding(quranDao.observeSurah(surahNumber)),
            afterSeeding(quranDao.observeTranslations(surahNumber, translationId))
        ) { ayahs, translations ->
            val byAyah = translations.associateBy { it.ayahNumber }
            ayahs.map { ayah ->
                ayah.copy(translation = byAyah[ayah.ayahNumber]?.translation ?: ayah.translation)
            }
        }

    fun observeSurahs(): Flow<List<SurahMetadataEntity>> = afterSeeding(quranDao.observeSurahs())

    /** Full-text search over the locally cached Uthmani text and its translation. */
    fun searchAyahs(query: String, limit: Int = 100): Flow<List<QuranAyahEntity>> =
        afterSeeding(quranDao.searchAyahs(query, limit))

    suspend fun isSurahAvailable(surahNumber: Int): Boolean =
        quranDao.countAyahs(surahNumber) > 0

    // ------------------------------------------------------------------ Translation catalog

    suspend fun fetchTranslationCatalog(): List<QuranTranslationResource> {
        return runCatching {
            val parsed = parseQuranComTranslations(
                fetch("$QURAN_API_BASE/resources/translations"),
                json
            )
            cacheCatalog(parsed)
            parsed
        }.getOrElse {
            runCatching {
                val parsed = parseAlQuranCloudTranslations(
                    fetch("https://api.alquran.cloud/v1/edition/type/translation"),
                    json
                )
                cacheCatalog(parsed)
                parsed
            }.getOrElse {
                loadCachedCatalog()
            }
        }
    }

    fun parseQuranComTranslations(body: String): List<QuranTranslationResource> =
        Companion.parseQuranComTranslations(body, json)

    fun parseAlQuranCloudTranslations(body: String): List<QuranTranslationResource> =
        Companion.parseAlQuranCloudTranslations(body, json)

    private fun cacheCatalog(catalog: List<QuranTranslationResource>) {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences("sakinah_quran_translations", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("catalog_json", json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(QuranTranslationResource.serializer()),
            catalog
        )).apply()
    }

    private fun loadCachedCatalog(): List<QuranTranslationResource> {
        val context = appContext ?: return emptyList()
        val prefs = context.getSharedPreferences("sakinah_quran_translations", android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString("catalog_json", null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(QuranTranslationResource.serializer()),
                raw
            )
        }.getOrDefault(emptyList())
    }

    // ------------------------------------------------------------------ Hadith

    /**
     * Downloads a full hadith collection. Arabic is fetched from the matching
     * `ara-` edition and joined by hadith number; missing Arabic degrades gracefully.
     */
    suspend fun syncHadithBook(
        bookKey: String,
        languageCode: String = "eng",
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = runCatching {
        onProgress(0.05f)

        val primaryBody = fetch("$HADITH_CDN_BASE/editions/$languageCode-$bookKey.min.json")
        onProgress(0.45f)

        val arabicByNumber = if (languageCode == "ara") {
            emptyMap()
        } else {
            runCatching {
                parseArabicTexts(fetch("$HADITH_CDN_BASE/editions/ara-$bookKey.min.json"))
            }.getOrDefault(emptyMap())
        }
        onProgress(0.70f)

        val entries = parseHadithEdition(primaryBody, bookKey, languageCode, arabicByNumber)
        if (entries.isEmpty()) error("No hadith parsed for $bookKey")

        hadithDao.replaceBook(bookKey, languageCode, entries)
        onProgress(1f)
        entries.size
    }

    fun observeHadiths(
        bookKey: String,
        languageCode: String = "eng",
        limit: Int = 100000,
        offset: Int = 0
    ): Flow<List<HadithEntryEntity>> =
        afterSeeding(hadithDao.observeHadiths(bookKey, languageCode, limit, offset))

    fun observeHadithsBySection(
        bookKey: String,
        languageCode: String = "eng",
        sectionNumber: Int,
        limit: Int = 100000,
        offset: Int = 0
    ): Flow<List<HadithEntryEntity>> =
        afterSeeding(hadithDao.observeHadithsBySection(bookKey, languageCode, sectionNumber, limit, offset))

    /** Every locally seeded/ downloaded collection, used for the "All Books" filter. */
    fun observeAllHadiths(limit: Int = 100000, offset: Int = 0): Flow<List<HadithEntryEntity>> =
        afterSeeding(hadithDao.observeAllHadiths(limit, offset))

    fun searchHadiths(query: String, limit: Int = 200): Flow<List<HadithEntryEntity>> =
        afterSeeding(hadithDao.searchHadiths(query, limit))

    /**
     * Waits for bundled offline content before the first Room emission. Without a seeder
     * (unit tests, tools) the source is returned untouched.
     */
    private fun <T> afterSeeding(source: Flow<T>): Flow<T> {
        val seeder = offlineContent ?: return source
        return flow {
            seeder.awaitReady()
            emitAll(source)
        }
    }

    suspend fun getHadith(bookKey: String, hadithNumber: Int, languageCode: String = "eng"): HadithEntryEntity? =
        hadithDao.getHadith(bookKey, languageCode, hadithNumber)

    suspend fun isBookAvailable(bookKey: String, languageCode: String = "eng"): Boolean =
        hadithDao.countForBook(bookKey, languageCode) > 0

    // ------------------------------------------------------------------ Hadith catalog

    suspend fun fetchHadithCatalog(): List<HadithBookCatalog> {
        return runCatching {
            val editionsBody = fetch("$HADITH_CDN_BASE/editions.min.json")
            val infoBody = runCatching { fetch("$HADITH_CDN_BASE/info.min.json") }.getOrNull()
            val parsed = Companion.parseHadithCatalog(editionsBody, infoBody, json)
            cacheHadithCatalog(parsed)
            parsed
        }.getOrElse { loadCachedHadithCatalog() }
    }

    private fun cacheHadithCatalog(catalog: List<HadithBookCatalog>) {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences("sakinah_hadith_catalog", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("catalog_json", json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(HadithBookCatalog.serializer()),
            catalog
        )).apply()
    }

    private fun loadCachedHadithCatalog(): List<HadithBookCatalog> {
        val context = appContext ?: return emptyList()
        val prefs = context.getSharedPreferences("sakinah_hadith_catalog", android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString("catalog_json", null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(HadithBookCatalog.serializer()),
                raw
            )
        }.getOrDefault(emptyList())
    }

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
        languageCode: String,
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
                languageCode = languageCode,
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
