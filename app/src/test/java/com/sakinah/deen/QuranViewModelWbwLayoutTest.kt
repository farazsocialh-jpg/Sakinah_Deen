package com.sakinah.deen

import com.sakinah.deen.data.prefs.QuranReaderPreferences
import com.sakinah.deen.data.prefs.WbwLayoutMode
import com.sakinah.deen.data.quran.QulDataSource
import com.sakinah.deen.data.quran.QulMorphology
import com.sakinah.deen.data.quran.QulMushafLine
import com.sakinah.deen.data.quran.QulSurahInfo
import com.sakinah.deen.data.quran.QulWord
import com.sakinah.deen.fakes.FakeSharedPreferences
import com.sakinah.deen.ui.viewmodel.QuranUiIntent
import com.sakinah.deen.ui.viewmodel.QuranViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Word-by-word state, persistence and the single interlinear presentation, exercised
 * against the [QulDataSource] contract without Android SQLite.
 */
class QuranViewModelWbwLayoutTest {

    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeQul : QulDataSource {
        val morphologyRequests = mutableListOf<Int>()

        private val words = listOf(
            QulWord(1, "1:1:1", 1, 1, 1, "بِسْمِ", "بسم", "bismi", "In (the) name"),
            QulWord(2, "1:1:2", 1, 1, 2, "اللَّهِ", "اللہ", "allahi", "(of) Allah"),
            QulWord(3, "1:1:3", 1, 1, 3, "الرَّحْمَٰنِ", "الرحمٰن", "ar-rahmani", "the Most Gracious")
        )

        override suspend fun getAvailableWbwLanguages(): List<String> = listOf("en")

        override suspend fun getWordsForSurah(surah: Int, lang: String): Map<Int, List<QulWord>> =
            mapOf(1 to words)

        override suspend fun getMushafPage(layout: String, pageNumber: Int): List<QulMushafLine> =
            emptyList()

        override suspend fun getWordMorphology(wordKey: String): QulMorphology =
            QulMorphology(wordKey, "أله", "اللَّه", "اللَّهِ", "N")

        override suspend fun getMorphologyForSurah(surah: Int): Map<String, QulMorphology> {
            morphologyRequests += surah
            return words.associate { it.wordKey to QulMorphology(it.wordKey, "أله", "اللَّه", "اللَّهِ", "N") }
        }

        override suspend fun searchByRoot(root: String): List<QulWord> = emptyList()

        override suspend fun getTafsir(slug: String, ayahKey: String): String? = null

        override suspend fun getSurahInfo(surah: Int): QulSurahInfo =
            QulSurahInfo(surah, "en", "makkah", "A summary")

        override suspend fun getAyahTopics(ayahKey: String): List<Pair<String, String?>> = emptyList()

        override suspend fun isAvailable(): Boolean = true
    }

    @Test
    fun theSingleModeIsExposedAsAStateFlowAndPersisted() = runTest(testDispatcher) {
        val prefs = QuranReaderPreferences(FakeSharedPreferences())
        val vm = QuranViewModel(quranReaderPreferences = prefs, qulDatabase = FakeQul())
        advanceUntilIdle()

        assertEquals(WbwLayoutMode.INTERLINEAR_RTL, vm.wbwLayoutMode.value)
        assertEquals(WbwLayoutMode.INTERLINEAR_RTL, vm.uiState.value.wbwLayoutMode)
        assertEquals(WbwLayoutMode.INTERLINEAR_RTL, prefs.wbwLayoutMode)
    }

    @Test
    fun aStoredRetiredModeMigratesToInterlinearWithoutAMorphologyScan() = runTest(testDispatcher) {
        val fake = FakeSharedPreferences()
        QuranReaderPreferences(fake).wbwLayoutMode = WbwLayoutMode.INTERLINEAR_RTL
        // Simulate a pre-consolidation install writing its own enum name directly.
        fake.edit().putString("quran_wbw_layout_mode", "GLOSSARY_LIST").apply()
        val qul = FakeQul()

        val vm = QuranViewModel(
            quranReaderPreferences = QuranReaderPreferences(fake),
            qulDatabase = qul
        )
        advanceUntilIdle()

        assertEquals(WbwLayoutMode.INTERLINEAR_RTL, vm.uiState.value.wbwLayoutMode)
        assertEquals(WbwLayoutMode.INTERLINEAR_RTL, vm.wbwLayoutMode.value)
        assertTrue(qul.morphologyRequests.isEmpty())
    }

    @Test
    fun wordsKeepTheirQulOrderSoTheRtlStripStartsOnTheRight() = runTest(testDispatcher) {
        val vm = QuranViewModel(qulDatabase = FakeQul())
        advanceUntilIdle()

        // The strip reverses nothing itself: it renders this list inside LayoutDirection.Rtl,
        // so word 1 must still be first here and therefore right-most on screen.
        val ayah = vm.uiState.value.qulWordsByAyah[1].orEmpty()
        assertEquals(listOf("1:1:1", "1:1:2", "1:1:3"), ayah.map { it.wordKey })
        assertEquals(listOf(1, 2, 3), ayah.map { it.wordIndex })
    }
}
