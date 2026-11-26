package com.sakinah.deen

import com.sakinah.deen.data.database.entity.TafsirEntryEntity
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.repository.TafseerDownloadRepository
import com.sakinah.deen.fakes.FakeHadithDao
import com.sakinah.deen.fakes.FakeQuranDao
import com.sakinah.deen.fakes.FakeSharedPreferences
import com.sakinah.deen.fakes.FakeTafsirDao
import com.sakinah.deen.network.IslamicDataRepository
import com.sakinah.deen.ui.viewmodel.QuranUiIntent
import com.sakinah.deen.ui.viewmodel.QuranViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Selection persistence, legacy scholar chips, Room cache and live ayah fallback. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class QuranViewModelTafseerTest {
    private lateinit var dispatcher: TestDispatcher

    @Before fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun repo(dao: FakeTafsirDao) = TafseerDownloadRepository(
        tafsirDao = dao,
        ioDispatcher = dispatcher,
        refreshCatalogOnStart = false
    )

    @Test
    fun selectedTafsirAndLanguageSurviveRestart() = runTest(dispatcher) {
        val store = FakeSharedPreferences()
        val prefs = SakinahPreferences(store)
        val dao = FakeTafsirDao()
        val vm = QuranViewModel(tafseerRepository = repo(dao), sakinahPreferences = prefs)
        advanceUntilIdle()

        vm.onIntent(QuranUiIntent.SelectTafsir(160, "ur", "Tafsir Ibn Kathir"))
        advanceUntilIdle()

        assertEquals(160, vm.uiState.value.selectedTafsirId)
        assertEquals("ur", vm.uiState.value.selectedTafseerLanguage)
        assertEquals(160, prefs.selectedTafsirId)
        assertEquals("ur", prefs.selectedTafseerLanguage)

        val reopened = QuranViewModel(tafseerRepository = repo(dao), sakinahPreferences = prefs)
        advanceUntilIdle()
        assertEquals(160, reopened.uiState.value.selectedTafsirId)
        assertEquals("ur", reopened.uiState.value.selectedTafseerLanguage)
        assertEquals("Tafsir Ibn Kathir", reopened.uiState.value.selectedTafseerTitle)
    }

    @Test
    fun legacyScholarChipMapsToTafsirIdAndCachedTextIsCleaned() = runTest(dispatcher) {
        val dao = FakeTafsirDao()
        dao.upsertAll(
            listOf(
                TafsirEntryEntity(
                    sourceKey = "tafsir_15",
                    sourceName = "Tafsir al-Tabari",
                    surahNumber = 1,
                    ayahNumber = 2,
                    languageCode = "ar",
                    text = "<p><span>Tabari</span> commentary</p>"
                )
            )
        )
        val vm = QuranViewModel(tafseerRepository = repo(dao))
        advanceUntilIdle()

        vm.onIntent(QuranUiIntent.SelectScholar("Al-Tabari"))
        advanceUntilIdle()
        assertEquals(15, vm.uiState.value.selectedTafsirId)
        assertEquals("ar", vm.uiState.value.selectedTafseerLanguage)

        vm.onIntent(QuranUiIntent.SelectAyahForTafseer(2))
        advanceUntilIdle()
        assertEquals("Tabari commentary", vm.uiState.value.selectedTafseerContent)
    }

    @Test
    fun singleAyahLiveFetchReturnsCleanTextWithoutMarkingPackOffline() = runTest(dispatcher) {
        val dao = FakeTafsirDao()
        val engine = MockEngine { request ->
            if (request.url.toString().contains("/tafsirs/804/by_ayah/")) {
                respond(
                    """{"tafsir":{"text":"<p>Live Kurdish commentary</p>","resource_name":"Rebar Kurdish Tafsir"}}""",
                    HttpStatusCode.OK
                )
            } else {
                respond("""{"tafsirs":[]}""", HttpStatusCode.OK)
            }
        }
        val islamicRepository = IslamicDataRepository(
            quranDao = FakeQuranDao(),
            hadithDao = FakeHadithDao(),
            tafsirDao = dao,
            client = HttpClient(engine),
            json = Json { ignoreUnknownKeys = true },
            ioDispatcher = dispatcher
        )

        val entry = islamicRepository.fetchTafsirForAyah(1, 1, 804, "ku")
        assertEquals("Live Kurdish commentary", entry?.text)
        assertEquals("tafsir_804", entry?.sourceKey)
        assertEquals("ku", entry?.languageCode)
        assertTrue(dao.entries.none { it.sourceKey == "tafsir_804" })
    }
}
