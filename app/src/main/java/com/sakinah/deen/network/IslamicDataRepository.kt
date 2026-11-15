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
import com.sakinah.deen.util.ArabicNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
        const val HADITH_CDN_RAW_BASE = "https://raw.githubusercontent.com/fawazahmed0/hadith-api/1"
        const val ALQURAN_CLOUD_BASE = "https://api.alquran.cloud/v1"
        const val QURAN_API_CDN_BASE = "https://cdn.jsdelivr.net/gh/fawazahmed0/quran-api@1"
        const val QURAN_API_RAW_BASE = "https://raw.githubusercontent.com/fawazahmed0/quran-api/1"

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

        private fun stableId(value: String): Int =
            value.hashCode().let { if (it == 0) 1 else it }

        /** Keeps persisted IDs stable while guaranteeing uniqueness inside a catalog. */
        private fun ensureUniqueTranslationIds(
            items: List<QuranTranslationResource>
        ): List<QuranTranslationResource> {
            val used = HashSet<Int>(items.size)
            return items.map { item ->
                var id = item.id
                while (!used.add(id)) {
                    id = if (id == Int.MAX_VALUE) 1 else id + 1
                }
                if (id == item.id) item else item.copy(id = id)
            }
        }

        /** Human-readable names for two- and three-letter language codes from free APIs. */
        fun languageDisplayName(code: String, fallback: String = ""): String {
            val normalized = code.trim().lowercase()
            val name = when (normalized) {
                "eng", "en" -> "English"
                "ara", "ar" -> "Arabic"
                "urd", "ur" -> "Urdu"
                "ind", "id" -> "Indonesian"
                "tur", "tr" -> "Turkish"
                "fra", "fre", "fr" -> "French"
                "ben", "bn" -> "Bengali"
                "rus", "ru" -> "Russian"
                "tam", "ta" -> "Tamil"
                "hin", "hi" -> "Hindi"
                "spa", "es" -> "Spanish"
                "deu", "ger", "de" -> "German"
                "msa", "may", "ms" -> "Malay"
                "swa", "sw" -> "Swahili"
                "fas", "per", "fa" -> "Persian"
                "zho", "chi", "zh" -> "Chinese"
                "jpn", "ja" -> "Japanese"
                "kor", "ko" -> "Korean"
                "por", "pt" -> "Portuguese"
                "ita", "it" -> "Italian"
                "nld", "dut", "nl" -> "Dutch"
                "aze", "az" -> "Azerbaijani"
                "bos", "bs" -> "Bosnian"
                "sqi", "alb", "sq" -> "Albanian"
                "som", "so" -> "Somali"
                "hau", "ha" -> "Hausa"
                "yor", "yo" -> "Yoruba"
                "amh", "am" -> "Amharic"
                "kaz", "kk" -> "Kazakh"
                "uzb", "uz" -> "Uzbek"
                "tgl", "tl" -> "Tagalog"
                "tha", "th" -> "Thai"
                "vie", "vi" -> "Vietnamese"
                else -> ""
            }
            return name.ifBlank { fallback.ifBlank { code.uppercase() } }
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
                    isoCode = (obj["iso_code"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    editionKey = id.toString(),
                    source = QuranTranslationResource.SOURCE_QURAN_COM
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
                val identifier = (obj["identifier"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                if (identifier.isBlank()) return@mapNotNull null
                val isoCode = (obj["language"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                QuranTranslationResource(
                    id = stableId("alquran_cloud:$identifier"),
                    name = (obj["englishName"] as? JsonPrimitive)?.contentOrNull
                        ?.ifBlank { (obj["name"] as? JsonPrimitive)?.contentOrNull.orEmpty() }
                        .orEmpty(),
                    authorName = (obj["name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    languageName = languageDisplayName(isoCode, isoCode),
                    isoCode = isoCode,
                    editionKey = identifier,
                    source = QuranTranslationResource.SOURCE_ALQURAN_CLOUD
                )
            }.distinctBy { it.editionKey }.let(::ensureUniqueTranslationIds)
        }

        fun parseQuranApiTranslations(
            body: String,
            json: Json = defaultJson
        ): List<QuranTranslationResource> {
            val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyList()
            return root.mapNotNull { (key, value) ->
                val obj = value as? JsonObject ?: return@mapNotNull null
                val editionKey = (obj["name"] as? JsonPrimitive)?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                    ?: key.replace('_', '-')
                val language = (obj["language"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val author = (obj["author"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val isoCode = editionKey.substringBefore('-').ifBlank { key.substringBefore('_') }
                QuranTranslationResource(
                    id = stableId("quran_api:$editionKey"),
                    name = author.ifBlank { editionKey },
                    authorName = editionKey,
                    languageName = language.ifBlank { languageDisplayName(isoCode, isoCode) },
                    isoCode = isoCode,
                    editionKey = editionKey,
                    source = QuranTranslationResource.SOURCE_QURAN_API
                )
            }.distinctBy { it.editionKey }.let(::ensureUniqueTranslationIds)
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
                        languageName = (edition["language"] as? JsonPrimitive)?.contentOrNull
                            ?.takeIf { it.isNotBlank() }
                            ?: languageDisplayName(editionName.substringBefore('-')),

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
        translationEditionKey: String = "",
        translationSource: String = QuranTranslationResource.SOURCE_QURAN_COM,
        withAudio: Boolean = true,
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = runCatching {
        require(surahNumber in 1..114) { "Surah number must be between 1 and 114" }
        onProgress(0.05f)

        val chapter = runCatching { fetchChapters() }
            .getOrDefault(emptyList())
            .firstOrNull { it.id == surahNumber }
        onProgress(0.15f)

        val usesQuranComTranslation =
            translationSource == QuranTranslationResource.SOURCE_QURAN_COM ||
                translationEditionKey.isBlank()
        val primaryVerses = runCatching {
            fetchQuranComVerses(
                surahNumber = surahNumber,
                translationId = if (usesQuranComTranslation) translationId else null
            )
        }.getOrDefault(emptyList()).ifEmpty {
            fetchAlQuranAyahs(surahNumber, "quran-uthmani").map { (ayah, text) ->
                com.sakinah.deen.network.model.VerseByChapterDto(
                    verseNumber = ayah,
                    verseKey = "$surahNumber:$ayah",
                    textUthmani = text
                )
            }
        }.ifEmpty {
            fetchQuranApiAyahs(surahNumber, "ara-quranuthmanihaf").map { (ayah, text) ->
                com.sakinah.deen.network.model.VerseByChapterDto(
                    verseNumber = ayah,
                    verseKey = "$surahNumber:$ayah",
                    textUthmani = text
                )
            }
        }
        val verses = primaryVerses
        if (verses.isEmpty()) error("No authentic Quran source returned surah $surahNumber")
        onProgress(0.45f)

        val translationByAyah: Map<Int, String> = if (usesQuranComTranslation) {
            verses.associate { verse ->
                val number = verse.verseNumber.takeIf { it > 0 }
                    ?: verse.verseKey.substringAfter(':').toIntOrNull() ?: 0
                number to (verse.translations.firstOrNull()?.text?.let(::cleanText).orEmpty())
            }.filterValues { it.isNotBlank() }
        } else {
            when (translationSource) {
                QuranTranslationResource.SOURCE_ALQURAN_CLOUD ->
                    fetchAlQuranAyahs(surahNumber, translationEditionKey)
                QuranTranslationResource.SOURCE_QURAN_API ->
                    fetchQuranApiAyahs(surahNumber, translationEditionKey)
                else -> emptyMap()
            }
        }
        if (translationByAyah.isEmpty()) {
            error("The selected $translationSource translation could not be downloaded for surah $surahNumber")
        }
        onProgress(0.70f)

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
            val ayahNumber = verse.verseNumber.takeIf { it > 0 }
                ?: verse.verseKey.substringAfter(':').toIntOrNull()
                ?: return@map null
            QuranAyahEntity(
                surahNumber = surahNumber,
                ayahNumber = ayahNumber,
                verseKey = verse.verseKey.ifBlank { "$surahNumber:$ayahNumber" },
                textUthmani = verse.textUthmani,
                translation = translationByAyah[ayahNumber],
                audioUrl = audioUrl,
                surahNameEnglish = chapter?.nameSimple,
                surahNameArabic = chapter?.nameArabic,
                revelationPlace = chapter?.revelationPlace
            )
        }.filterNotNull()

        val translations = ayahs.mapNotNull { ayah ->
            val text = translationByAyah[ayah.ayahNumber].orEmpty()
            if (text.isBlank()) null else QuranTranslationEntity(
                surahNumber = surahNumber,
                ayahNumber = ayah.ayahNumber,
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

    private suspend fun fetchQuranComVerses(
        surahNumber: Int,
        translationId: Int?
    ): List<com.sakinah.deen.network.model.VerseByChapterDto> {
        val translationQuery = translationId?.let { "&translations=$it" }.orEmpty()
        return json.decodeFromString<VerseByChapterResponse>(
            fetch(
                "$QURAN_API_BASE/verses/by_chapter/$surahNumber" +
                    "?fields=text_uthmani&per_page=300$translationQuery"
            )
        ).verses
    }

    /** AlQuran.cloud surah text keyed by ayah number. */
    private suspend fun fetchAlQuranAyahs(surahNumber: Int, edition: String): Map<Int, String> {
        if (edition.isBlank()) return emptyMap()
        val root = json.parseToJsonElement(
            fetch("$ALQURAN_CLOUD_BASE/surah/$surahNumber/$edition")
        ) as? JsonObject ?: return emptyMap()
        val data = root["data"] as? JsonObject ?: return emptyMap()
        val ayahs = data["ayahs"] as? JsonArray ?: return emptyMap()
        return ayahs.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val number = intOf(obj["numberInSurah"]) ?: return@mapNotNull null
            val text = (obj["text"] as? JsonPrimitive)?.contentOrNull?.let(::cleanText).orEmpty()
            if (text.isBlank()) null else number to text
        }.toMap()
    }

    /** fawazahmed0/quran-api chapter text keyed by ayah number. */
    private suspend fun fetchQuranApiAyahs(
        surahNumber: Int,
        edition: String
    ): Map<Int, String> {
        if (edition.isBlank()) return emptyMap()
        val body = runCatching {
            fetch("$QURAN_API_CDN_BASE/editions/$edition/$surahNumber.min.json")
        }.getOrElse {
            fetch("$QURAN_API_RAW_BASE/editions/$edition/$surahNumber.min.json")
        }
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyMap()
        val chapter = root["chapter"] as? JsonArray ?: return emptyMap()
        return chapter.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val number = intOf(obj["verse"]) ?: return@mapNotNull null
            val text = (obj["text"] as? JsonPrimitive)?.contentOrNull?.let(::cleanText).orEmpty()
            if (text.isBlank()) null else number to text
        }.toMap()
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
    fun searchAyahs(query: String, limit: Int = 100): Flow<List<QuranAyahEntity>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return flow { emit(emptyList()) }
        return afterSeeding(quranDao.observeAllAyahs()).map { rows ->
            rows.filter { row ->
                ArabicNormalizer.matches(row.textUthmani, trimmed) ||
                    ArabicNormalizer.matches(row.translation.orEmpty(), trimmed) ||
                    row.verseKey.equals(trimmed, ignoreCase = true)
            }.take(limit)
        }
    }

    suspend fun isSurahAvailable(surahNumber: Int): Boolean =
        quranDao.countAyahs(surahNumber) > 0

    // ------------------------------------------------------------------ Translation catalog

    suspend fun fetchTranslationCatalog(): List<QuranTranslationResource> {
        val sources: List<suspend () -> List<QuranTranslationResource>> = listOf(
            { parseQuranComTranslations(fetch("$QURAN_API_BASE/resources/translations")) },
            { parseAlQuranCloudTranslations(fetch("$ALQURAN_CLOUD_BASE/edition/type/translation")) },
            { parseQuranApiTranslations(fetch("$QURAN_API_CDN_BASE/editions.min.json")) }
        )
        for (source in sources) {
            val parsed = runCatching { source() }.getOrDefault(emptyList())
            if (parsed.isNotEmpty()) {
                cacheCatalog(parsed)
                return parsed
            }
        }
        return loadCachedCatalog()
    }

    fun parseQuranComTranslations(body: String): List<QuranTranslationResource> =
        Companion.parseQuranComTranslations(body, json)

    fun parseAlQuranCloudTranslations(body: String): List<QuranTranslationResource> =
        Companion.parseAlQuranCloudTranslations(body, json)

    fun parseQuranApiTranslations(body: String): List<QuranTranslationResource> =
        Companion.parseQuranApiTranslations(body, json)

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

        val primaryBody = withContext(Dispatchers.IO) {
            fetchHadithEdition(languageCode, bookKey)
        }
        onProgress(0.45f)

        val arabicEdition = if (languageCode == "ara") {
            ArabicEditionIndex()
        } else {
            runCatching {
                withContext(Dispatchers.IO) {
                    parseArabicTexts(fetchHadithEdition("ara", bookKey))
                }
            }.getOrDefault(ArabicEditionIndex())
        }
        onProgress(0.70f)

        // Parsing multi-megabyte Urdu/Bengali editions must never run on the main thread.
        val entries = withContext(Dispatchers.Default) {
            parseHadithEdition(primaryBody, bookKey, languageCode, arabicEdition)
        }
        if (entries.isEmpty()) error("No hadith parsed for $bookKey")

        withContext(Dispatchers.IO) {
            hadithDao.replaceBook(bookKey, languageCode, entries)
        }
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

    /** "All Books" restricted to one edition, so the ENG/ARA toggle changes every narration. */
    fun observeAllHadiths(
        languageCode: String,
        limit: Int = 100000,
        offset: Int = 0
    ): Flow<List<HadithEntryEntity>> =
        afterSeeding(hadithDao.observeAllHadithsByLanguage(languageCode, limit, offset))

    fun searchHadiths(query: String, limit: Int = 200): Flow<List<HadithEntryEntity>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return flow { emit(emptyList()) }
        return afterSeeding(hadithDao.observeAllHadiths()).map { rows ->
            rows.filter { row ->
                ArabicNormalizer.matches(row.arabicText, trimmed) ||
                    ArabicNormalizer.matches(row.englishText, trimmed) ||
                    ArabicNormalizer.matches(row.chapterTitle, trimmed) ||
                    ArabicNormalizer.matches(row.bookTitle, trimmed) ||
                    row.hadithNumber.toString() == trimmed
            }.take(limit)
        }
    }

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
            val editionsBody = fetchHadithJson("/editions.min.json")
            val infoBody = runCatching { fetchHadithJson("/info.min.json") }.getOrNull()
            val parsed = Companion.parseHadithCatalog(editionsBody, infoBody, json)
            if (parsed.isEmpty()) error("Hadith catalog was empty")
            cacheHadithCatalog(parsed)
            parsed
        }.getOrElse { loadCachedHadithCatalog() }
    }

    /** Try jsDelivr first, then the GitHub raw mirror for the same versioned edition. */
    private suspend fun fetchHadithJson(path: String): String = runCatching {
        fetch("$HADITH_CDN_BASE$path")
    }.getOrElse {
        fetch("$HADITH_CDN_RAW_BASE$path")
    }

    private suspend fun fetchHadithEdition(languageCode: String, bookKey: String): String =
        fetchHadithJson("/editions/$languageCode-$bookKey.min.json")

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
        var resolvedName = "Tafsir $tafsirId"
        var lastError: Throwable? = null

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
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                lastError = error
            }.getOrNull()?.let { collected.add(it) }

            onProgress((index + 1).toFloat() / range.count().toFloat())
            delay(120)
        }

        if (collected.isEmpty()) {
            // Offline/HTTP failures must reach the caller instead of looking like a successful
            // empty download. Partial success is still useful and is stored below.
            lastError?.let { throw it }
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

    /**
     * Arabic text keyed both by hadith number and by `reference.book:reference.hadith`, so an
     * English narration can never be paired with the Arabic of a different hadith (index-based
     * merging is what produced Bukhari #2's mismatched "trade" narration).
     */
    private data class ArabicEditionIndex(
        val byNumber: Map<Int, String> = emptyMap(),
        val byReference: Map<String, String> = emptyMap()
    ) {
        fun resolve(number: Int, referenceBook: Int?, referenceHadith: Int?): String? {
            byNumber[number]?.let { return it }
            if (referenceBook != null && referenceHadith != null) {
                byReference["$referenceBook:$referenceHadith"]?.let { return it }
            }
            return null
        }
    }

    private fun parseArabicTexts(body: String): ArabicEditionIndex {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return ArabicEditionIndex()
        val hadiths = root["hadiths"] as? JsonArray ?: return ArabicEditionIndex()
        val byNumber = mutableMapOf<Int, String>()
        val byReference = mutableMapOf<String, String>()
        hadiths.forEach { element ->
            val entry = element as? JsonObject ?: return@forEach
            val number = intOf(entry["hadithnumber"])
            val reference = entry["reference"] as? JsonObject
            val referenceBook = intOf(reference?.get("book"))
            val referenceHadith = intOf(reference?.get("hadith"))
            val text = (entry["text"] as? JsonPrimitive)?.contentOrNull?.let(::cleanText)
            if (text.isNullOrBlank()) return@forEach
            if (number != null) byNumber[number] = text
            if (referenceBook != null && referenceHadith != null) {
                byReference["$referenceBook:$referenceHadith"] = text
            }
        }
        return ArabicEditionIndex(byNumber = byNumber, byReference = byReference)
    }

    private fun parseHadithEdition(
        body: String,
        bookKey: String,
        languageCode: String,
        arabicEdition: ArabicEditionIndex
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
            val referenceBook = intOf(reference?.get("book"))
            val referenceHadith = intOf(reference?.get("hadith"))
            val number = intOf(entry["hadithnumber"])
                ?: referenceHadith
                ?: return@mapNotNull null
            val sectionNumber = referenceBook ?: 0

            val grade = (entry["grades"] as? JsonArray)
                ?.firstOrNull() as? JsonObject

            val rawText = cleanText((entry["text"] as? JsonPrimitive)?.contentOrNull.orEmpty())
            val isArabicEdition = languageCode.equals("ara", ignoreCase = true)
            val resolvedArabic = if (isArabicEdition) {
                rawText
            } else {
                arabicEdition.resolve(number, referenceBook, referenceHadith).orEmpty()
            }
            val resolvedTranslation = if (isArabicEdition) "" else rawText

            HadithEntryEntity(
                bookKey = bookKey,
                languageCode = languageCode,
                hadithNumber = number,
                arabicNumber = (entry["arabicnumber"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                bookTitle = cleanText(bookTitle),
                sectionNumber = sectionNumber,
                chapterTitle = sections[sectionNumber.toString()].orEmpty(),
                arabicText = resolvedArabic,
                englishText = resolvedTranslation,
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
