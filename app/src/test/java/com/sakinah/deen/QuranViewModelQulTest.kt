package com.sakinah.deen

import com.sakinah.deen.data.prefs.QuranReaderPreferences
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Word-by-word state updates against the [QulDataSource] contract, without Android SQLite. */
class QuranViewModelQulTest {

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
        uthmani = "كلمة",
        indopak = "كلمه",
        transliteration = "kalimah",
        translation = translation
    )

    private class FakeQul : QulDataSource {
        val requested = mutableListOf<Pair<Int, String>>()
        val words = mapOf(
            "en" to mapOf(1 to listOf(word("1:1:1", 1, "word"))),
            "ur" to mapOf(1 to listOf(word("1:1:1", 1, "لفظ")))
        )

        override suspend fun getAvailableWbwLanguages(): List<String> = listOf("en", "ur")

        override suspend fun getWordsForSurah(surah: Int, lang: String): Map<Int, List<QulWord>> {
            requested += surah to lang
            return words[lang].orEmpty()
        }

        override suspend fun getMushafPage(layout: String, pageNumber: Int): List<QulMushafLine> =
            emptyList()

        override suspend fun getWordMorphology(wordKey: String): QulMorphology =
            QulMorphology(wordKey, root = "كلم", lemma = "كَلِمَة", stem = "كَلِمَة", pos = "N")

        override suspend fun getMorphologyForSurah(surah: Int): Map<String, QulMorphology> =
            mapOf("1:1:1" to getWordMorphology("1:1:1"))

        override suspend fun searchByRoot(root: String): List<QulWord> = words["en"]?.get(1).orEmpty()

        override suspend fun getTafsir(slug: String, ayahKey: String): String? = null

        override suspend fun getSurahInfo(surah: Int): QulSurahInfo =
            QulSurahInfo(surah, "en", "makkah", "A summary")

        override suspend fun getAyahTopics(ayahKey: String): List<Pair<String, String?>> =
            listOf("Mercy" to null)

        override suspend fun isAvailable(): Boolean = true

        private fun word(key: String, index: Int, translation: String) =
            QulWord(index, key, 1, 1, index, "كلمة", "كلمه", "kalimah", translation)
    }

    @Test
    fun initPreloadsLanguagesWordsAndSurahInfo() = runTest(testDispatcher) {
        val qul = FakeQul()
        val vm = QuranViewModel(qulDatabase = qul)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf("en", "ur"), state.availableWbwLanguages)
        assertEquals("en", state.wbwLanguage)
        assertEquals("word", state.qulWordsByAyah[1]?.first()?.translation)
        assertNotNull(state.surahInfo)
        assertTrue(qul.requested.contains(1 to "en"))
    }

    @Test
    fun selectingLanguageReloadsWordsAndPersists() = runTest(testDispatcher) {
        val prefs = QuranReaderPreferences(FakeSharedPreferences())
        val qul = FakeQul()
        val vm = QuranViewModel(quranReaderPreferences = prefs, qulDatabase = qul)
        advanceUntilIdle()

        vm.onIntent(QuranUiIntent.SelectWbwLanguage("ur"))
        advanceUntilIdle()

        assertEquals("ur", vm.uiState.value.wbwLanguage)
        assertEquals("لفظ", vm.uiState.value.qulWordsByAyah[1]?.first()?.translation)
        assertEquals("ur", prefs.wbwLanguage)
        assertTrue(qul.requested.contains(1 to "ur"))
    }

    @Test
    fun selectingScriptPersistsAndIsValidated() = runTest(testDispatcher) {
        val prefs = QuranReaderPreferences(FakeSharedPreferences())
        val vm = QuranViewModel(quranReaderPreferences = prefs)
        advanceUntilIdle()

        vm.onIntent(QuranUiIntent.SelectQulScript(QulWord.QUL_SCRIPT_INDOPAK))
        assertEquals(QulWord.QUL_SCRIPT_INDOPAK, vm.uiState.value.qulScript)
        assertEquals(QulWord.QUL_SCRIPT_INDOPAK, prefs.wbwScript)

        vm.onIntent(QuranUiIntent.SelectQulScript("not-a-script"))
        assertEquals(QulWord.QUL_SCRIPT_UTHMANI, vm.uiState.value.qulScript)
    }

    @Test
    fun selectingWordOpensMorphologyAndRootOccurrences() = runTest(testDispatcher) {
        val qul = FakeQul()
        val vm = QuranViewModel(qulDatabase = qul)
        advanceUntilIdle()

        vm.onIntent(QuranUiIntent.SelectQulWord("1:1:1"))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("1:1:1", state.selectedQulWord?.wordKey)
        assertEquals("كلم", state.selectedQulMorphology?.root)
        assertTrue(state.selectedQulWordOccurrences.isNotEmpty())

        vm.onIntent(QuranUiIntent.ClearQulWord)
        assertEquals(null, vm.uiState.value.selectedQulWord)
    }
}
