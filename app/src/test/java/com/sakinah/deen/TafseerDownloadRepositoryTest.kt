package com.sakinah.deen

import com.sakinah.deen.data.database.entity.QuranAyahEntity
import com.sakinah.deen.data.repository.TafseerDownloadRepository
import com.sakinah.deen.fakes.FakeHadithDao
import com.sakinah.deen.fakes.FakeQuranDao
import com.sakinah.deen.fakes.FakeTafsirDao
import com.sakinah.deen.network.IslamicDataRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Exercises the dynamic catalog, a non-English download/resume, and Room deletion. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TafseerDownloadRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private lateinit var dispatcher: TestDispatcher

    @Before fun setUp() {
        dispatcher = kotlinx.coroutines.test.StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private val catalogJson = """
        {"tafsirs":[
          {"id":169,"name":"Ibn Kathir (Abridged)","author_name":"Hafiz Ibn Kathir","slug":"en-tafisr-ibn-kathir","language_name":"english","translated_name":{"name":"Ibn Kathir (Abridged)","language_name":"english"}},
          {"id":160,"name":"Tafsir Ibn Kathir","author_name":"Hafiz Ibn Kathir","slug":"tafseer-ibn-e-kaseer-urdu","language_name":"urdu","translated_name":{"name":"Tafsir Ibn Kathir","language_name":"english"}},
          {"id":804,"name":"Rebar Kurdish Tafsir","author_name":"Rebar Kurdish Tafsir","slug":"kurd-tafsir-rebar","language_name":"Kurdish","translated_name":{"name":"Rebar Kurdish Tafsir","language_name":"english"}}
        ]}
    """.trimIndent()

    private fun chapterJson(surah: Int) = """
        {"tafsirs":[
          {"resource_id":160,"verse_key":"$surah:1","resource_name":"Tafsir Ibn Kathir","text":"<p>Urdu tafsir 1</p>"},
          {"resource_id":160,"verse_key":"$surah:2","resource_name":"Tafsir Ibn Kathir","text":"<p>Urdu tafsir 2</p>"},
          {"resource_id":160,"verse_key":"$surah:3","resource_name":"Tafsir Ibn Kathir","text":"<p>Urdu tafsir 3</p>"}
        ]}
    """.trimIndent()

    private fun fixture(
        chapterCalls: AtomicInteger = AtomicInteger(),
        catalogCalls: AtomicInteger = AtomicInteger(),
        chapterJsonOverride: String? = null
    ): Triple<TafseerDownloadRepository, FakeTafsirDao, FakeQuranDao> {
        val quranDao = FakeQuranDao()
        repeat(3) { index ->
            val ayah = index + 1
            quranDao.ayahs[1 to ayah] = QuranAyahEntity(
                surahNumber = 1,
                ayahNumber = ayah,
                verseKey = "1:$ayah",
                textUthmani = "آية"
            )
        }
        val tafsirDao = FakeTafsirDao()
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("/resources/tafsirs") -> {
                    catalogCalls.incrementAndGet()
                    respond(catalogJson, HttpStatusCode.OK)
                }
                url.contains("/tafsirs/160/by_chapter/") -> {
                    val surah = url.substringAfter("/by_chapter/").substringBefore('/').toIntOrNull() ?: 0
                    chapterCalls.incrementAndGet()
                    if (surah == 1) {
                        respond(chapterJsonOverride ?: chapterJson(surah), HttpStatusCode.OK)
                    } else {
                        respond("""{"tafsirs":[]}""", HttpStatusCode.OK)
                    }
                }
                else -> respond("""{"tafsirs":[]}""", HttpStatusCode.OK)
            }
        }
        val islamicRepository = IslamicDataRepository(
            quranDao = quranDao,
            hadithDao = FakeHadithDao(),
            tafsirDao = tafsirDao,
            client = HttpClient(engine),
            json = json,
            ioDispatcher = dispatcher
        )
        val repo = TafseerDownloadRepository(
            tafsirDao = tafsirDao,
            islamicRepository = islamicRepository,
            chaptersToDownload = 1..1,
            ioDispatcher = dispatcher,
            refreshCatalogOnStart = false
        )
        return Triple(repo, tafsirDao, quranDao)
    }

    @Test
    fun refreshCatalogMergesDynamicEditionsIntoOfflineFallback() = runTest(dispatcher) {
        val catalogCalls = AtomicInteger()
        val (repo, _, _) = fixture(catalogCalls = catalogCalls)
        val refreshed = repo.refreshCatalog()

        assertEquals(1, catalogCalls.get())
        assertTrue(refreshed.any { it.id == "tafsir_804" && it.languageCode == "ku" })
        assertTrue(refreshed.any { it.id == "ibn_kathir_en" && it.tafsirId == 169 })
        assertTrue(refreshed.any { it.id == "tafsir_160" && it.languageName == "Urdu" })
    }

    @Test
    fun downloadsResumesAndDeletesNonEnglishPackByTafsirId() = runTest(dispatcher) {
        val chapterCalls = AtomicInteger()
        val (repo, dao, _) = fixture(chapterCalls = chapterCalls)
        val progress = mutableListOf<Float>()

        val first = repo.downloadTafseerPackage(160) { progress += it }
        assertTrue(first.isSuccess)
        assertEquals(listOf("tafsir_160"), dao.entries.map { it.sourceKey }.distinct())
        assertEquals(3, dao.entries.size)
        assertEquals("ur", dao.entries.first().languageCode)
        assertTrue(dao.entries.all { it.text == "Urdu tafsir ${it.ayahNumber}" })
        assertEquals(1, chapterCalls.get())
        assertTrue(progress.isNotEmpty())

        // Every ayah of Surah 1 is cached, so the second run must not hit the API again.
        val resumed = repo.downloadTafseerPackage(160)
        assertTrue(resumed.isSuccess)
        assertEquals(1, chapterCalls.get())
        assertEquals(1, repo.packages.first().first { it.tafsirId == 160 }.downloadedChapters)

        repo.removeTafseerPackage(160)
        assertTrue(dao.entries.isEmpty())
        assertEquals(0, repo.packages.first().first { it.tafsirId == 160 }.downloadedChapters)
    }

    @Test
    fun groupedVersesExpandToOneRowPerAyah() = runTest(dispatcher) {
        val grouped = """
            {"tafsirs":[
              {"resource_id":160,"verse_key":"1:1","resource_name":"Tafsir Ibn Kathir",
               "text":"<p>passage</p>","verses":["1:1","1:2","1:3"]}
            ]}
        """.trimIndent()
        val (repo, dao, _) = fixture(chapterJsonOverride = grouped)

        val result = repo.downloadTafseerPackage(160)

        assertTrue(result.isSuccess)
        assertEquals(listOf(1, 2, 3), dao.entries.map { it.ayahNumber }.sorted())
        assertTrue(dao.entries.all { it.text == "passage" })
    }

    @Test
    fun groupStartsFillGapAyahsUpToExpectedCount() = runTest(dispatcher) {
        val sparse = """
            {"tafsirs":[
              {"resource_id":160,"verse_key":"1:1","resource_name":"Tafsir Ibn Kathir","text":"<p>first</p>"},
              {"resource_id":160,"verse_key":"1:5","resource_name":"Tafsir Ibn Kathir","text":"<p>fifth</p>"}
            ]}
        """.trimIndent()
        val (repo, dao, _) = fixture(chapterJsonOverride = sparse)

        val result = repo.downloadTafseerPackage(160)

        assertTrue(result.isSuccess)
        assertEquals(listOf(1, 2, 3), dao.entries.map { it.ayahNumber }.sorted())
        assertTrue(dao.entries.all { it.text == "first" })
    }

    @Test
    fun cachedAyahIsReadFromTafsirDaoSourceKey() = runTest(dispatcher) {
        val (repo, dao, _) = fixture()
        dao.upsertAll(
            listOf(
                com.sakinah.deen.data.database.entity.TafsirEntryEntity(
                    sourceKey = "tafsir_160",
                    sourceName = "Tafsir Ibn Kathir",
                    surahNumber = 1,
                    ayahNumber = 2,
                    languageCode = "ur",
                    text = "<p>Urdu cached ayah</p>"
                )
            )
        )

        val entry = repo.getCachedTafsir(160, 1, 2)
        assertEquals("tafsir_160", entry?.sourceKey)
        assertEquals("Urdu cached ayah", com.sakinah.deen.data.model.TafsirText.clean(entry!!.text))
    }
}
