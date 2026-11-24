package com.sakinah.deen.data.quran

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.LruCache
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One Quran word as published by QUL, joined with the selected WBW language. */
@Immutable
data class QulWord(
    val id: Int,
    val wordKey: String,
    val surah: Int,
    val ayah: Int,
    val wordIndex: Int,
    val uthmani: String,
    val indopak: String,
    val transliteration: String = "",
    /** Word-by-word meaning in the language the reader selected. */
    val translation: String = ""
) {
    /** Arabic text for the selected QUL script; IndoPak falls back to Uthmani. */
    fun arabicFor(script: String): String =
        if (script == QUL_SCRIPT_INDOPAK && indopak.isNotBlank()) indopak else uthmani

    companion object {
        const val QUL_SCRIPT_UTHMANI = "uthmani"
        const val QUL_SCRIPT_INDOPAK = "indopak"
    }
}

/** One line of a Mushaf page: the word span it holds and its surah. */
@Immutable
data class QulMushafLine(
    val layout: String,
    val pageNumber: Int,
    val lineNumber: Int,
    val lineType: String,
    val isCentered: Boolean,
    val firstWordId: Int,
    val lastWordId: Int,
    val surahNumber: Int
)

/** Word-level Arabic morphology: root, dictionary form, stem and part of speech. */
@Immutable
data class QulMorphology(
    val wordKey: String,
    val root: String,
    val lemma: String,
    val stem: String,
    val pos: String
)

/** Offline surah introduction and revelation place. */
@Immutable
data class QulSurahInfo(
    val surah: Int,
    val lang: String,
    val revelationPlace: String,
    val summary: String
)

/**
 * Read-only access to the bundled QUL datasets.
 *
 * The interface exists so [com.sakinah.deen.ui.viewmodel.QuranViewModel] can be exercised
 * on the JVM with a fake, without an Android SQLite runtime.
 */
interface QulDataSource {
    suspend fun getAvailableWbwLanguages(): List<String>
    suspend fun getWordsForSurah(surah: Int, lang: String): Map<Int, List<QulWord>>
    suspend fun getMushafPage(layout: String, pageNumber: Int): List<QulMushafLine>
    suspend fun getWordMorphology(wordKey: String): QulMorphology?
    suspend fun searchByRoot(root: String): List<QulWord>
    suspend fun getTafsir(slug: String, ayahKey: String): String?
    suspend fun getSurahInfo(surah: Int): QulSurahInfo?
    suspend fun getAyahTopics(ayahKey: String): List<Pair<String, String?>>
    suspend fun isAvailable(): Boolean
}

/**
 * The public Quran text is read-only and unencrypted: paying SQLCipher's per-page cost for
 * data nobody edits would slow every scroll for no privacy gain. User-owned Quran state
 * (bookmarks, Hifz mistakes, notes) stays in [com.sakinah.deen.data.database.SakinahEncryptedDatabase].
 *
 * Every query runs on [Dispatchers.IO]. Hot paths are memoised in a small LRU: a surah's
 * words are one indexed query (never one query per ayah), and returning to a page or
 * chapter after a scroll or a language switch hits RAM instead of SQLite.
 */
class QulOfflineDatabase(context: Context) : QulDataSource {

    private val appContext = context.applicationContext

    private val openMutex = Mutex()

    @Volatile
    private var database: SQLiteDatabase? = null

    private val languagesCache = LruCache<String, List<String>>(1)
    private val surahCache = LruCache<String, Map<Int, List<QulWord>>>(CACHE_ENTRIES)
    private val pageCache = LruCache<String, List<QulMushafLine>>(CACHE_ENTRIES)
    private val morphologyCache = LruCache<String, QulMorphology>(CACHE_ENTRIES)
    private val tafsirCache = LruCache<String, String>(CACHE_ENTRIES)
    private val surahInfoCache = LruCache<Int, QulSurahInfo>(CACHE_ENTRIES)
    private val topicsCache = LruCache<String, List<Pair<String, String?>>>(CACHE_ENTRIES)

    private suspend fun database(): SQLiteDatabase {
        database?.let { return it }
        return openMutex.withLock {
            database ?: run {
                // Unpacks + verifies the gzip on IO the first time a reader query needs it.
                val file = QuranOfflinePack.ensureQulDatabaseFile(appContext)
                // No PRAGMA tuning on this connection: execSQL() rejects a PRAGMA that
                // returns a row (`PRAGMA mmap_size = …` does) with "Queries can be
                // performed using SQLiteDatabase query or rawQuery methods only", and an
                // OPEN_READONLY connection additionally refuses statements SQLite does
                // not consider read-only. Both surfaced as a silently empty database, so
                // the connection is opened with SQLite's defaults instead.
                SQLiteDatabase.openDatabase(
                    file.path,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                ).also { opened -> database = opened }
            }
        }
    }

    override suspend fun isAvailable(): Boolean = runCatching { database() }.isSuccess

    override suspend fun getAvailableWbwLanguages(): List<String> {
        languagesCache.get(LANGUAGES_KEY)?.let { return it }
        return withContext(Dispatchers.IO) {
            val values = mutableListOf<String>()
            database().rawQuery(
                "SELECT lang FROM qul_wbw_translations " +
                    "GROUP BY lang ORDER BY COUNT(*) DESC, lang",
                emptyArray()
            ).use { cursor ->
                while (cursor.moveToNext()) values += cursor.getString(0)
            }
            languagesCache.put(LANGUAGES_KEY, values)
            values
        }
    }

    override suspend fun getWordsForSurah(surah: Int, lang: String): Map<Int, List<QulWord>> {
        val cacheKey = "$surah:$lang"
        surahCache.get(cacheKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            val grouped = LinkedHashMap<Int, MutableList<QulWord>>()
            // One indexed query for the whole surah: 286 rows maximum, not N+1 per ayah.
            database().rawQuery(WORDS_FOR_SURAH_SQL, arrayOf(lang, surah.toString()))
                .use { cursor ->
                    while (cursor.moveToNext()) {
                        val word = QulWord(
                            id = cursor.getInt(0),
                            wordKey = cursor.getString(1),
                            surah = cursor.getInt(2),
                            ayah = cursor.getInt(3),
                            wordIndex = cursor.getInt(4),
                            uthmani = cursor.getString(5).orEmpty(),
                            indopak = cursor.getString(6).orEmpty(),
                            transliteration = cursor.getString(7).orEmpty(),
                            translation = cursor.getString(8).orEmpty()
                        )
                        grouped.getOrPut(word.ayah) { mutableListOf() } += word
                    }
                }
            val result: Map<Int, List<QulWord>> = grouped.mapValues { it.value.toList() }
            surahCache.put(cacheKey, result)
            result
        }
    }

    override suspend fun getMushafPage(layout: String, pageNumber: Int): List<QulMushafLine> {
        val cacheKey = "$layout:$pageNumber"
        pageCache.get(cacheKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            val lines = mutableListOf<QulMushafLine>()
            database().rawQuery(
                "SELECT layout, page_number, line_number, line_type, is_centered, " +
                    "first_word_id, last_word_id, surah_number FROM qul_mushaf_pages " +
                    "WHERE layout = ? AND page_number = ? ORDER BY line_number",
                arrayOf(layout, pageNumber.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    lines += QulMushafLine(
                        layout = cursor.getString(0).orEmpty(),
                        pageNumber = cursor.getInt(1),
                        lineNumber = cursor.getInt(2),
                        lineType = cursor.getString(3).orEmpty(),
                        isCentered = cursor.getInt(4) != 0,
                        firstWordId = cursor.getInt(5),
                        lastWordId = cursor.getInt(6),
                        surahNumber = cursor.getInt(7)
                    )
                }
            }
            val result = lines.toList()
            pageCache.put(cacheKey, result)
            result
        }
    }

    override suspend fun getWordMorphology(wordKey: String): QulMorphology? {
        morphologyCache.get(wordKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            var morphology: QulMorphology? = null
            database().rawQuery(
                "SELECT word_key, root, lemma, stem, pos FROM qul_morphology WHERE word_key = ?",
                arrayOf(wordKey)
            ).use { cursor ->
                if (cursor.moveToNext()) {
                    morphology = QulMorphology(
                        wordKey = cursor.getString(0).orEmpty(),
                        root = cursor.getString(1).orEmpty(),
                        lemma = cursor.getString(2).orEmpty(),
                        stem = cursor.getString(3).orEmpty(),
                        pos = cursor.getString(4).orEmpty()
                    )
                }
            }
            morphology?.let { morphologyCache.put(wordKey, it) }
            morphology
        }
    }

    override suspend fun searchByRoot(root: String): List<QulWord> {
        if (root.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val words = mutableListOf<QulWord>()
            database().rawQuery(ROOT_SEARCH_SQL, arrayOf(root)).use { cursor ->
                while (cursor.moveToNext()) {
                    words += QulWord(
                        id = cursor.getInt(0),
                        wordKey = cursor.getString(1),
                        surah = cursor.getInt(2),
                        ayah = cursor.getInt(3),
                        wordIndex = cursor.getInt(4),
                        uthmani = cursor.getString(5).orEmpty(),
                        indopak = cursor.getString(6).orEmpty(),
                        transliteration = cursor.getString(7).orEmpty()
                    )
                }
            }
            words.toList()
        }
    }

    override suspend fun getTafsir(slug: String, ayahKey: String): String? {
        val cacheKey = "$slug:$ayahKey"
        tafsirCache.get(cacheKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            var text = ""
            database().rawQuery(
                "SELECT text FROM qul_tafsir WHERE slug = ? AND ayah_key = ?",
                arrayOf(slug, ayahKey)
            ).use { cursor -> if (cursor.moveToNext()) text = cursor.getString(0).orEmpty() }
            text.takeIf { it.isNotBlank() }?.also { tafsirCache.put(cacheKey, it) }
        }
    }

    override suspend fun getSurahInfo(surah: Int): QulSurahInfo? {
        surahInfoCache.get(surah)?.let { return it }
        return withContext(Dispatchers.IO) {
            var info: QulSurahInfo? = null
            database().rawQuery(
                "SELECT surah, lang, revelation_place, summary FROM qul_surah_info WHERE surah = ?",
                arrayOf(surah.toString())
            ).use { cursor ->
                if (cursor.moveToNext()) {
                    info = QulSurahInfo(
                        surah = cursor.getInt(0),
                        lang = cursor.getString(1).orEmpty(),
                        revelationPlace = cursor.getString(2).orEmpty(),
                        summary = cursor.getString(3).orEmpty()
                    )
                }
            }
            info?.let { surahInfoCache.put(surah, it) }
            info
        }
    }

    override suspend fun getAyahTopics(ayahKey: String): List<Pair<String, String?>> {
        topicsCache.get(ayahKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            val topics = mutableListOf<Pair<String, String?>>()
            database().rawQuery(
                "SELECT topic, theme FROM qul_ayah_topics WHERE ayah_key = ? ORDER BY topic",
                arrayOf(ayahKey)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    topics += cursor.getString(0).orEmpty() to cursor.getString(1)
                }
            }
            val result = topics.toList()
            topicsCache.put(ayahKey, result)
            result
        }
    }

    private companion object {
        const val CACHE_ENTRIES = 64
        const val LANGUAGES_KEY = "wbw_languages"

        const val WORDS_FOR_SURAH_SQL =
            "SELECT w.id, w.word_key, w.surah, w.ayah, w.word_index, w.uthmani, w.indopak, " +
                "w.transliteration, COALESCE(t.text, '') AS translation " +
                "FROM qul_words w " +
                "LEFT JOIN qul_wbw_translations t " +
                "ON t.word_key = w.word_key AND t.lang = ? " +
                "WHERE w.surah = ? ORDER BY w.ayah, w.word_index"

        const val ROOT_SEARCH_SQL =
            "SELECT w.id, w.word_key, w.surah, w.ayah, w.word_index, w.uthmani, w.indopak, " +
                "w.transliteration FROM qul_words w " +
                "JOIN qul_morphology m ON m.word_key = w.word_key " +
                "WHERE m.root = ? " +
                "ORDER BY w.surah, w.ayah, w.word_index LIMIT 200"
    }
}
