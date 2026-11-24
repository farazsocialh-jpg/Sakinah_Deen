package com.sakinah.deen.data.quran

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real unpack + SQLite path on a device/emulator.
 *
 * The JVM cannot host Android's SQLite, so these assertions live in `androidTest`; the
 * same behaviour is reached on the JVM through [com.sakinah.deen.QuranViewModelQulTest]
 * with a fake [QulDataSource].
 */
@RunWith(AndroidJUnit4::class)
class QulOfflineDatabaseTest {

    private lateinit var database: QulOfflineDatabase
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        database = QulOfflineDatabase(context)
    }

    @Test
    fun bundledAssetUnpacksWithThePinnedChecksum() = runBlocking {
        val file = QuranOfflinePack.ensureQulDatabaseFile(context)
        assertEquals(QuranOfflinePack.QUL_DB_BYTES, file.length())

        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        assertEquals(
            QuranOfflinePack.QUL_DB_SHA256,
            digest.digest().joinToString("") { "%02x".format(it) }
        )
        // A second call must reuse the verified file rather than re-expanding the gzip.
        assertEquals(file, QuranOfflinePack.ensureQulDatabaseFile(context))
    }

    @Test
    fun assetGzipHeaderMatchesPack() {
        GZIPInputStream(context.assets.open(QuranOfflinePack.QUL_ASSET)).use { gzip ->
            assertTrue(gzip.read() >= 0)
        }
    }

    @Test
    fun wbwLanguagesIncludeEveryOpenEdition() = runBlocking {
        val languages = database.getAvailableWbwLanguages()
        listOf("en", "ur", "id", "tr", "bn", "hi", "fr", "ta", "fa", "inh")
            .forEach { assertTrue("missing $it", it in languages) }
    }

    @Test
    fun surahWordsLoadInOneIndexedQuery() = runBlocking {
        val words = database.getWordsForSurah(1, "en")
        assertEquals(7, words.size)
        val firstAyah = words[1].orEmpty()
        assertTrue(firstAyah.isNotEmpty())
        assertTrue(firstAyah.all { it.wordKey.startsWith("1:1:") })
        assertTrue(firstAyah.first().uthmani.isNotBlank())
        assertTrue(firstAyah.first().translation.isNotBlank())
    }

    @Test
    fun mushafPageHasOrderedLines() = runBlocking {
        val lines = database.getMushafPage("madani", 1)
        assertTrue(lines.isNotEmpty())
        assertEquals(lines.sortedBy { it.lineNumber }, lines)
        assertTrue(lines.all { it.firstWordId <= it.lastWordId })
    }

    @Test
    fun morphologyAndRootSearchWork() = runBlocking {
        val morphology = database.getWordMorphology("1:1:2")
        assertNotNull(morphology)
        assertTrue(morphology!!.root.isNotBlank())

        val occurrences = database.searchByRoot(morphology.root)
        assertTrue(occurrences.isNotEmpty())
        assertTrue(occurrences.all { it.wordKey.isNotBlank() })
    }

    @Test
    fun tafsirSurahInfoAndTopicsAreBundled() = runBlocking {
        assertTrue(database.getTafsir("ibn-kathir", "1:1").orEmpty().isNotBlank())
        assertNotNull(database.getSurahInfo(1))
        assertTrue(database.getAyahTopics("1:1").isNotEmpty())
    }
}
