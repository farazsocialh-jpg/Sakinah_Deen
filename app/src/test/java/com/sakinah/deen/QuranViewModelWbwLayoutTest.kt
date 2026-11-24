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
 * Word-by-word layout selection, persistence and the vocabulary-quiz reveal state,
 * exercised against the [QulDataSource] contract without Android SQLite.
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

    private fun word(key: String, index: Int, translation: String) = QulWord(
        id = index,
        wordKey = key,
        surah = 1,
        ayah = 1,
        wordIndex = index,
        uthmani = "كلمة$index",
        indopak = "کلمہ$index",
        transliteration = "kalimah$index",
        translation = translation
    )

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
    fun everyModeIsSelectedPersistedAndExposedAsAStateFlow() = runTest(testDispatcher) {
        val prefs = QuranReaderPreferences(FakeSharedPreferences())
        val vm = QuranViewModel(quranReaderPreferences = prefs, qulDatabase = FakeQul())
        advanceUntilIdle()
        assertEquals(WbwLayoutMode.INTERLINEAR_RTL, vm.wbwLayoutMode.value)

        WbwLayoutMode.entries.forEach { mode ->
            vm.onIntent(QuranUiIntent.SelectWbwLayout(mode))
            advanceUntilIdle()

            assertEquals(mode, vm.uiState.value.wbwLayoutMode)
            assertEquals(mode, vm.wbwLayoutMode.value)
            assertEquals(mode, prefs.wbwLayoutMode)
        }
    }

    @Test
    fun theStoredModeIsRestoredAndItsMorphologyPreloaded() = runTest(testDispatcher) {
        val fake = FakeSharedPreferences()
        QuranReaderPreferences(fake).wbwLayoutMode = WbwLayoutMode.GLOSSARY_LIST
        val qul = FakeQul()

        val vm = QuranViewModel(
            quranReaderPreferences = QuranReaderPreferences(fake),
            qulDatabase = qul
        )
        advanceUntilIdle()

        assertEquals(WbwLayoutMode.GLOSSARY_LIST, vm.uiState.value.wbwLayoutMode)
        // The glossary prints a root per word, so the chapter's morphology is fetched once.
        assertTrue(qul.morphologyRequests.contains(1))
        assertEquals("أله", vm.uiState.value.wbwMorphology["1:1:2"]?.root)
    }

    @Test
    fun switchingToADockModeLoadsMorphologyOnce() = runTest(testDispatcher) {
        val qul = FakeQul()
        val vm = QuranViewModel(qulDatabase = qul)
        advanceUntilIdle()
        assertTrue(qul.morphologyRequests.isEmpty())

        vm.onIntent(QuranUiIntent.SelectWbwLayout(WbwLayoutMode.VERSE_BOTTOM_DOCK))
        advanceUntilIdle()
        assertEquals(listOf(1), qul.morphologyRequests)

        vm.onIntent(QuranUiIntent.SelectWbwLayout(WbwLayoutMode.GLOSSARY_LIST))
        advanceUntilIdle()
        // Same chapter, already loaded: no duplicate scan.
        assertEquals(listOf(1), qul.morphologyRequests)
    }

    @Test
    fun quizRevealTogglesPerWord() = runTest(testDispatcher) {
        val vm = QuranViewModel(
            quranReaderPreferences = QuranReaderPreferences(FakeSharedPreferences()),
            qulDatabase = FakeQul()
        )
        advanceUntilIdle()
        vm.onIntent(QuranUiIntent.SelectWbwLayout(WbwLayoutMode.VOCAB_QUIZ))
        assertTrue(vm.uiState.value.revealedWbwWordKeys.isEmpty())

        vm.onIntent(QuranUiIntent.ToggleWbwReveal("1:1:1"))
        assertEquals(setOf("1:1:1"), vm.uiState.value.revealedWbwWordKeys)

        vm.onIntent(QuranUiIntent.ToggleWbwReveal("1:1:3"))
        assertEquals(setOf("1:1:1", "1:1:3"), vm.uiState.value.revealedWbwWordKeys)

        vm.onIntent(QuranUiIntent.ToggleWbwReveal("1:1:1"))
        assertEquals(setOf("1:1:3"), vm.uiState.value.revealedWbwWordKeys)

        // A blank key is ignored rather than polluting the revealed set.
        vm.onIntent(QuranUiIntent.ToggleWbwReveal(""))
        assertEquals(setOf("1:1:3"), vm.uiState.value.revealedWbwWordKeys)
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
