package com.sakinah.deen.network

import androidx.core.content.edit
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
import com.sakinah.deen.data.model.TafsirResource
import com.sakinah.deen.data.model.TafsirText
import com.sakinah.deen.data.quran.QuranOfflineStatus
import com.sakinah.deen.data.database.entity.SurahMetadataEntity
import com.sakinah.deen.data.database.entity.TafsirEntryEntity
import com.sakinah.deen.network.model.ChapterAudioResponse
import com.sakinah.deen.network.model.ChaptersResponse
import com.sakinah.deen.network.model.TafsirChapterResponse
import com.sakinah.deen.network.model.TafsirResponse
import com.sakinah.deen.network.model.TranslationsResponse
import com.sakinah.deen.network.model.UthmaniVersesResponse
import com.sakinah.deen.network.model.VerseByChapterResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import com.sakinah.deen.util.ArabicNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import android.util.JsonReader
import java.io.InputStreamReader
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
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
    private val appContext: android.content.Context? = null,
    /** Injectable so unit tests can drain HTTP continuations with their test dispatcher. */
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
) {

    companion object {
        const val QURAN_API_BASE = "https://api.quran.com/api/v4"
        const val HADITH_CDN_BASE = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1"
        const val HADITH_CDN_RAW_BASE = "https://raw.githubusercontent.com/fawazahmed0/hadith-api/1"
        const val ALQURAN_CLOUD_BASE = "https://api.alquran.cloud/v1"
        const val QURAN_API_CDN_BASE = "https://cdn.jsdelivr.net/gh/fawazahmed0/quran-api@1"

        /** First retry waits 350 ms, then 700 ms (exponential). */
        private const val RETRY_BASE_DELAY_MILLIS = 350L
        const val QURAN_API_RAW_BASE = "https://raw.githubusercontent.com/fawazahmed0/quran-api/1"

        /** Quran.com retired translation id 131; 20 = Saheeh International (verified live). */
        const val DEFAULT_TRANSLATION_ID = 20
        const val DEFAULT_RECITER_ID = 7 // Mishari Rashid al-`Afasy
        const val IBN_KATHIR_TAFSIR_ID = 169
        private const val TAFSIR_UPSERT_CHUNK = 50

        /** Preferences files and key for the two catalogs cached for offline use. */
        private const val QURAN_CATALOG_PREFS = "sakinah_quran_translations"
        private const val HADITH_CATALOG_PREFS = "sakinah_hadith_catalog"
        private const val TAFSIR_CATALOG_PREFS = "sakinah_tafsir_catalog"
        private const val CATALOG_KEY = "catalog_json"

        val SUPPORTED_HADITH_BOOKS = listOf(
            "bukhari", "muslim", "nawawi", "abudawud",
            "tirmidhi", "nasai", "ibnmajah", "malik"
        )

        private const val USER_AGENT = "ImanCore-Android/1.0 (offline-first Islamic companion)"

        private fun cleanText(raw: String): String = TafsirText.clean(raw)

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
                "kur", "ku", "ckb" -> "Kurdish"
                "uzb", "uz" -> "Uzbek"
                "tgl", "tl" -> "Tagalog"
                "tha", "th" -> "Thai"
                "vie", "vi" -> "Vietnamese"
                else -> ""
            }
            return name.ifBlank { fallback.ifBlank { code.uppercase() } }
        }

        /**
         * Normalizes Quran.com's free-form `language_name` ("english", "Kurdish", "urdu") to an
         * ISO code. Also accepts codes already supplied by callers, so persisted selections stay
         * stable when the catalog refreshes.
         */
        fun tafsirLanguageCode(languageNameOrCode: String): String {
            val value = languageNameOrCode.trim().lowercase()
                .replace('_', ' ').replace('-', ' ')
            val mapped = when (value) {
                "en", "eng", "english" -> "en"
                "ar", "ara", "arabic" -> "ar"
                "ur", "urd", "urdu" -> "ur"
                "bn", "ben", "bengali", "bangla" -> "bn"
                "ru", "rus", "russian" -> "ru"
                "ku", "kur", "ckb", "kurdish", "sorani", "central kurdish" -> "ku"
                "fa", "fas", "per", "persian", "farsi" -> "fa"
                "id", "ind", "indonesian" -> "id"
                "tr", "tur", "turkish" -> "tr"
                "fr", "fra", "fre", "french" -> "fr"
                "ta", "tam", "tamil" -> "ta"
                "hi", "hin", "hindi" -> "hi"
                "es", "spa", "spanish" -> "es"
                "de", "deu", "ger", "german" -> "de"
                "ms", "msa", "may", "malay" -> "ms"
                "sw", "swa", "swahili" -> "sw"
                "zh", "zho", "chi", "chinese" -> "zh"
                "ja", "jpn", "japanese" -> "ja"
                "ko", "kor", "korean" -> "ko"
                "pt", "por", "portuguese" -> "pt"
                "it", "ita", "italian" -> "it"
                "nl", "nld", "dut", "dutch" -> "nl"
                "az", "aze", "azerbaijani" -> "az"
                "bs", "bos", "bosnian" -> "bs"
                "sq", "sqi", "alb", "albanian" -> "sq"
                "so", "som", "somali" -> "so"
                "ha", "hau", "hausa" -> "ha"
                "yo", "yor", "yoruba" -> "yo"
                "am", "amh", "amharic" -> "am"
                "kk", "kaz", "kazakh" -> "kk"
                "uz", "uzb", "uzbek" -> "uz"
                "tl", "tgl", "tagalog" -> "tl"
                "th", "tha", "thai" -> "th"
                "vi", "vie", "vietnamese" -> "vi"
                "ps", "pus", "pashto" -> "ps"
                "sd", "snd", "sindhi" -> "sd"
                else -> ""
            }
            return mapped.ifBlank { value.takeIf { it.length == 2 } ?: value }
        }

        /** Parses the full Quran.com v4 tafsir catalog, including its translated names. */
        fun parseTafseerCatalog(
            body: String,
            json: Json = defaultJson
        ): List<TafsirResource> {
            val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                ?: return emptyList()
            val editions = root["tafsirs"] as? JsonArray ?: return emptyList()
            return editions.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val id = (obj["id"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                val translated = obj["translated_name"] as? JsonObject
                val languageName = (obj["language_name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    .ifBlank {
                        (translated?.get("language_name") as? JsonPrimitive)?.contentOrNull.orEmpty()
                    }
                TafsirResource(
                    id = id,
                    name = (obj["name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    authorName = (obj["author_name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    slug = (obj["slug"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    languageName = languageName,
                    translatedName = (translated?.get("name") as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    languageCode = tafsirLanguageCode(languageName)
                )
            }.distinctBy { it.id }
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
            // A dropped connection mid-surah used to leave the reader on a stale copy;
            // retry with backoff before giving up on the network.
            fetchWithRetry {
                fetchQuranComVerses(
                    surahNumber = surahNumber,
                    translationId = if (usesQuranComTranslation) translationId else null
                )
            }
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
                textIndopak = verse.textIndopak,
                textImlaei = verse.textImlaei,
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

        // Version/completeness check: a truncated response must never replace the
        // verified offline copy with a short surah.
        val expectedAyahs = chapter?.versesCount ?: 0
        if (expectedAyahs > 0 && ayahs.size != expectedAyahs) {
            error(
                "Downloaded ${ayahs.size} of $expectedAyahs ayahs for surah $surahNumber; " +
                    "keeping the existing offline copy"
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
                    "?fields=text_uthmani,text_indopak,text_imlaei&per_page=300$translationQuery"
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

    /** Cached ayah count for one surah, used to validate downloads and tafsir chapters. */
    suspend fun ayahCountForSurah(surahNumber: Int): Int = quranDao.countAyahs(surahNumber)

    /**
     * Live offline coverage of the bundled pack, derived from the cached rows rather than
     * from a download flag, so the banner can never claim text the reader does not hold.
     */
    fun observeOfflineStatus(): Flow<QuranOfflineStatus> =
        afterSeeding(quranDao.observeAllAyahs()).map { rows -> QuranOfflineStatus.from(rows) }

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
                cacheTranslationCatalog(parsed)
                return parsed
            }
        }
        return loadCachedTranslationCatalog()
    }

    fun parseQuranComTranslations(body: String): List<QuranTranslationResource> =
        Companion.parseQuranComTranslations(body, json)

    fun parseAlQuranCloudTranslations(body: String): List<QuranTranslationResource> =
        Companion.parseAlQuranCloudTranslations(body, json)

    fun parseQuranApiTranslations(body: String): List<QuranTranslationResource> =
        Companion.parseQuranApiTranslations(body, json)

    // ------------------------------------------------------------------ Tafsir catalog

    /** Refreshes the edition catalog from Quran.com; falls back to the last cached copy. */
    suspend fun fetchTafseerCatalog(): List<TafsirResource> {
        val remote = runCatching {
            parseTafseerCatalog(fetch("$QURAN_API_BASE/resources/tafsirs?language=en"))
        }.getOrDefault(emptyList())
        if (remote.isNotEmpty()) {
            cacheTafseerCatalog(remote)
            return remote
        }
        return loadCachedTafseerCatalog()
    }

    fun parseTafseerCatalog(body: String): List<TafsirResource> =
        Companion.parseTafseerCatalog(body, json)

    private fun cacheTafseerCatalog(catalog: List<TafsirResource>) =
        cacheCatalog(TAFSIR_CATALOG_PREFS, catalog, ListSerializer(TafsirResource.serializer()))

    private fun loadCachedTafseerCatalog(): List<TafsirResource> =
        loadCachedCatalog(TAFSIR_CATALOG_PREFS, ListSerializer(TafsirResource.serializer()))

    private fun cacheTranslationCatalog(catalog: List<QuranTranslationResource>) =
        cacheCatalog(QURAN_CATALOG_PREFS, catalog, ListSerializer(QuranTranslationResource.serializer()))

    private fun loadCachedTranslationCatalog(): List<QuranTranslationResource> =
        loadCachedCatalog(QURAN_CATALOG_PREFS, ListSerializer(QuranTranslationResource.serializer()))

    private fun cacheHadithCatalog(catalog: List<HadithBookCatalog>) =
        cacheCatalog(HADITH_CATALOG_PREFS, catalog, ListSerializer(HadithBookCatalog.serializer()))

    private fun loadCachedHadithCatalog(): List<HadithBookCatalog> =
        loadCachedCatalog(HADITH_CATALOG_PREFS, ListSerializer(HadithBookCatalog.serializer()))

    /** Both catalogs are small JSON lists, each cached in its own preferences file. */
    private fun <T> cacheCatalog(
        prefsName: String,
        catalog: List<T>,
        serializer: KSerializer<List<T>>
    ) {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE)
        prefs.edit { putString(CATALOG_KEY, json.encodeToString(serializer, catalog)) }
    }

    private fun <T> loadCachedCatalog(prefsName: String, serializer: KSerializer<List<T>>): List<T> {
        val context = appContext ?: return emptyList()
        val prefs = context.getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString(CATALOG_KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
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
    ): Result<Int> = try {
        onProgress(0.05f)

        val arabicEdition = if (languageCode == "ara") {
            ArabicEditionIndex()
        } else {
            cachedArabicIndex(bookKey) ?: downloadArabicIndex(bookKey)
        }
        onProgress(0.10f)

        val count = withContext(Dispatchers.IO) {
            try {
                streamEdition(bookKey, languageCode, arabicEdition, onProgress)
            } catch (e: RuntimeException) {
                // android.util.JsonReader is not available in plain JVM unit tests (it is
                // stubbed by the Android SDK). Fall back to the in-memory parser there so
                // tests still exercise syncHadithBook; production uses the streaming path.
                if (!e.message.orEmpty().contains("not mocked", ignoreCase = true) &&
                    !e.message.orEmpty().contains("JsonReader", ignoreCase = true)
                ) throw e
                val body = fetchHadithEdition(languageCode, bookKey)
                val entries = parseHadithEdition(body, bookKey, languageCode, arabicEdition)
                hadithDao.clearBook(bookKey, languageCode)
                hadithDao.replaceBook(bookKey, languageCode, entries)
                entries.size
            }
        }
        if (count == 0) error("No hadith parsed for $bookKey")

        onProgress(1f)
        Result.success(count)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (t: Throwable) {
        Result.failure(t)
    }

    private suspend fun downloadArabicIndex(bookKey: String): ArabicEditionIndex {
        val urls = listOf(
            "$HADITH_CDN_BASE/editions/ara-$bookKey.min.json",
            "$HADITH_CDN_RAW_BASE/editions/ara-$bookKey.min.json"
        )
        for (url in urls) {
            try {
                return parseArabicStream(url)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (t is RuntimeException &&
                    (t.message.orEmpty().contains("not mocked", ignoreCase = true) ||
                        t.message.orEmpty().contains("JsonReader", ignoreCase = true))
                ) {
                    // JVM unit-test fallback.
                    return try {
                        val body = fetchHadithEdition("ara", bookKey)
                        parseArabicTexts(body)
                    } catch (e: Throwable) {
                        ArabicEditionIndex()
                    }
                }
                // try next mirror
            }
        }
        return ArabicEditionIndex()
    }

    private suspend fun streamEdition(
        bookKey: String,
        languageCode: String,
        arabicEdition: ArabicEditionIndex,
        onProgress: (Float) -> Unit
    ): Int {
        hadithDao.clearBook(bookKey, languageCode)
        val urls = listOf(
            "$HADITH_CDN_BASE/editions/$languageCode-$bookKey.min.json",
            "$HADITH_CDN_RAW_BASE/editions/$languageCode-$bookKey.min.json"
        )
        var lastError: Throwable? = null
        for (url in urls) {
            try {
                return parseEditionStream(url, bookKey, languageCode, arabicEdition, onProgress)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                lastError = t
                hadithDao.clearBook(bookKey, languageCode)
            }
        }
        throw lastError ?: IllegalStateException("Failed to download $bookKey")
    }

    private suspend fun parseArabicStream(url: String): ArabicEditionIndex {
        val byNumber = mutableMapOf<Int, String>()
        val byReference = mutableMapOf<String, String>()
        val response = client.prepareGet(url) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "application/json")
        }
        response.execute { resp ->
            if (!resp.status.isSuccess()) error("HTTP ${resp.status.value} from $url")
            val reader = JsonReader(InputStreamReader(resp.bodyAsChannel().toInputStream(), Charsets.UTF_8))
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "hadiths" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            var number: Int? = null
                            var referenceBook: Int? = null
                            var referenceHadith: Int? = null
                            var text = ""
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "hadithnumber" -> number = readIntOrString(reader)
                                    "reference" -> {
                                        reader.beginObject()
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "book" -> referenceBook = readIntOrString(reader)
                                                "hadith" -> referenceHadith = readIntOrString(reader)
                                                else -> reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                    }
                                    "text" -> text = reader.nextString()
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                            val cleaned = cleanText(text)
                            if (cleaned.isBlank()) continue
                            if (number != null) byNumber[number] = cleaned
                            if (referenceBook != null && referenceHadith != null) {
                                byReference["$referenceBook:$referenceHadith"] = cleaned
                            }
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            reader.close()
        }
        return ArabicEditionIndex(byNumber = byNumber, byReference = byReference)
    }

    private suspend fun parseEditionStream(
        url: String,
        bookKey: String,
        languageCode: String,
        arabicEdition: ArabicEditionIndex,
        onProgress: (Float) -> Unit
    ): Int {
        val response = client.prepareGet(url) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "application/json")
        }
        var count = 0
        val batch = mutableListOf<HadithEntryEntity>()
        suspend fun flush() {
            if (batch.isNotEmpty()) {
                hadithDao.upsertAll(ArrayList(batch))
                batch.clear()
            }
        }
        response.execute { resp ->
            if (!resp.status.isSuccess()) error("HTTP ${resp.status.value} from $url")
            val reader = JsonReader(InputStreamReader(resp.bodyAsChannel().toInputStream(), Charsets.UTF_8))
            reader.beginObject()
            var bookTitle = bookKey.replaceFirstChar { it.uppercase() }
            val sections = mutableMapOf<String, String>()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "metadata" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "name" -> bookTitle = cleanText(reader.nextString())
                                "sections" -> {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        val key = reader.nextName()
                                        sections[key] = cleanText(reader.nextString())
                                    }
                                    reader.endObject()
                                }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                    "hadiths" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            var number: Int? = null
                            var arabicNumber = ""
                            var referenceBook: Int? = null
                            var referenceHadith: Int? = null
                            var rawText = ""
                            var grade = ""
                            var gradingScholar = ""
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "hadithnumber" -> number = readIntOrString(reader)
                                    "arabicnumber" -> arabicNumber = reader.nextString()
                                    "reference" -> {
                                        reader.beginObject()
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "book" -> referenceBook = readIntOrString(reader)
                                                "hadith" -> referenceHadith = readIntOrString(reader)
                                                else -> reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                    }
                                    "text" -> rawText = reader.nextString()
                                    "grades" -> {
                                        if (reader.peek() == android.util.JsonToken.BEGIN_ARRAY) {
                                            reader.beginArray()
                                            if (reader.hasNext() && reader.peek() == android.util.JsonToken.BEGIN_OBJECT) {
                                                reader.beginObject()
                                                while (reader.hasNext()) {
                                                    when (reader.nextName()) {
                                                        "grade" -> grade = reader.nextString()
                                                        "name" -> gradingScholar = reader.nextString()
                                                        else -> reader.skipValue()
                                                    }
                                                }
                                                reader.endObject()
                                            }
                                            while (reader.hasNext()) reader.skipValue()
                                            reader.endArray()
                                        } else {
                                            reader.skipValue()
                                        }
                                    }
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()

                            val resolvedNumber = number ?: referenceHadith ?: continue
                            val sectionNumber = referenceBook ?: 0
                            val rawClean = cleanText(rawText)
                            val isArabicEdition = languageCode.equals("ara", ignoreCase = true)
                            val resolvedArabic = if (isArabicEdition) rawClean else arabicEdition.resolve(resolvedNumber, referenceBook, referenceHadith).orEmpty()
                            val resolvedTranslation = if (isArabicEdition) "" else rawClean

                            val entity = HadithEntryEntity(
                                bookKey = bookKey,
                                languageCode = languageCode,
                                hadithNumber = resolvedNumber,
                                arabicNumber = arabicNumber,
                                bookTitle = cleanText(bookTitle),
                                sectionNumber = sectionNumber,
                                chapterTitle = sections[sectionNumber.toString()].orEmpty(),
                                arabicText = resolvedArabic,
                                englishText = resolvedTranslation,
                                grade = grade.ifBlank { "UNGRADED" }.let { if (it == "UNGRADED") "" else it },
                                gradingScholar = gradingScholar
                            )
                            batch += entity
                            count++
                            if (batch.size >= 250) flush()
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            reader.close()
        }
        flush()
        return count
    }

    private fun readIntOrString(reader: JsonReader): Int? {
        return try {
            val s = reader.nextString()
            s.trim().toIntOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reuses Arabic text this collection already stores instead of re-downloading the
     * `ara` edition. Only the number index is needed: the parser falls back to it.
     */
    private suspend fun cachedArabicIndex(bookKey: String): ArabicEditionIndex? {
        val rows = runCatching { hadithDao.getHadithByLanguage(bookKey, "ara") }.getOrNull()
            ?: return null
        if (rows.isEmpty()) return null
        val byNumber = HashMap<Int, String>(rows.size)
        rows.forEach { row ->
            if (row.arabicText.isNotBlank()) byNumber[row.hadithNumber] = row.arabicText
        }
        return if (byNumber.isEmpty()) null else ArabicEditionIndex(byNumber = byNumber)
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
    fun observeAllHadiths(limit: Int = 1000, offset: Int = 0): Flow<List<HadithEntryEntity>> =
        afterSeeding(hadithDao.observeAllHadiths(limit, offset))

    /** "All Books" restricted to one edition, so the ENG/ARA toggle changes every narration. */
    fun observeAllHadiths(
        languageCode: String,
        limit: Int = 1000,
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

    /**
     * Downloads one whole chapter of commentary in a single Quran.com v4 request. Large
     * chapters are inserted in chunks so the parsed list and the Room rows are never all
     * held at once beyond a small window.
     */
    suspend fun syncTafsirForChapter(
        tafsirId: Int,
        surahNumber: Int,
        languageCode: String = "en"
    ): Result<Int> = runCatching {
        require(surahNumber in 1..114) { "Surah number must be between 1 and 114" }
        val response = json.decodeFromString<TafsirChapterResponse>(
            fetchWithRetry { fetch("$QURAN_API_BASE/tafsirs/$tafsirId/by_chapter/$surahNumber") }
        )
        val sourceName = response.tafsirs.firstOrNull { it.resourceName.isNotBlank() }
            ?.resourceName
            ?: "Tafsir $tafsirId"
        val sourceKey = "tafsir_$tafsirId"
        var saved = 0

        response.tafsirs.chunked(TAFSIR_UPSERT_CHUNK).forEach { chunk ->
            val entities = chunk.mapNotNull { dto ->
                val ayahNumber = dto.verseKey.substringAfter(':', "").toIntOrNull()
                    ?: return@mapNotNull null
                val text = cleanText(dto.text)
                if (text.isBlank()) return@mapNotNull null
                TafsirEntryEntity(
                    sourceKey = sourceKey,
                    sourceName = sourceName,
                    surahNumber = surahNumber,
                    ayahNumber = ayahNumber,
                    languageCode = languageCode,
                    text = text
                )
            }
            if (entities.isNotEmpty()) {
                tafsirDao.upsertAll(entities)
                saved += entities.size
            }
        }

        if (saved == 0) error("Quran.com returned no tafsir for Surah $surahNumber")
        saved
    }

    suspend fun getTafsir(
        surahNumber: Int,
        ayahNumber: Int,
        tafsirId: Int = IBN_KATHIR_TAFSIR_ID
    ): TafsirEntryEntity? = tafsirDao.getTafsir("tafsir_$tafsirId", surahNumber, ayahNumber)

    /**
     * Reads one ayah's commentary live with `by_ayah` without claiming the pack is offline.
     * HTML is stripped here so callers never have to render raw `<p>`/`<span>` markup.
     */
    suspend fun fetchTafsirForAyah(
        surahNumber: Int,
        ayahNumber: Int,
        tafsirId: Int,
        languageCode: String = "en"
    ): TafsirEntryEntity? {
        val dto = json.decodeFromString<TafsirResponse>(
            fetchWithRetry {
                fetch("$QURAN_API_BASE/tafsirs/$tafsirId/by_ayah/$surahNumber:$ayahNumber")
            }
        ).tafsir ?: return null
        val text = cleanText(dto.text)
        if (text.isBlank()) return null
        return TafsirEntryEntity(
            sourceKey = "tafsir_$tafsirId",
            sourceName = dto.resourceName.ifBlank { "Tafsir $tafsirId" },
            surahNumber = surahNumber,
            ayahNumber = ayahNumber,
            languageCode = languageCode,
            text = text
        )
    }

    // ------------------------------------------------------------------ Parsing

    private suspend fun fetchChapters(): List<com.sakinah.deen.network.model.ChapterDto> =
        json.decodeFromString<ChaptersResponse>(
            fetch("$QURAN_API_BASE/chapters?language=en")
        ).chapters

    /**
     * Runs [block] up to [attempts] times with exponential backoff (350ms, 700ms, …).
     * Cancellation is never retried.
     */
    private suspend fun <T> fetchWithRetry(
        attempts: Int = 3,
        block: suspend () -> T
    ): T {
        var lastError: Throwable? = null
        repeat(attempts) { attempt ->
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                if (attempt < attempts - 1) {
                    delay(RETRY_BASE_DELAY_MILLIS * (1L shl attempt))
                }
            }
        }
        throw lastError ?: IllegalStateException("Request failed")
    }

    private suspend fun fetch(url: String): String = kotlinx.coroutines.withContext(ioDispatcher) {
        val response = client.get(url) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "application/json")
        }
        if (!response.status.isSuccess()) {
            error("HTTP ${response.status.value} from $url")
        }
        response.bodyAsText()
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

    private fun cleanText(raw: String): String = TafsirText.clean(raw)

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
