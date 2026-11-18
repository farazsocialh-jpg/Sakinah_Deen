package com.sakinah.deen

import com.sakinah.deen.data.prefs.QuranReaderPreferences
import com.sakinah.deen.data.prefs.QuranReadingMode
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranViewModelTest {
    private lateinit var testDispatcher: TestDispatcher
    @Before fun setUp() { testDispatcher = StandardTestDispatcher(); Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun initialState_hasSampleSurah() = runTest(testDispatcher) {
        val vm = QuranViewModel()
        val state = vm.uiState.value
        assertEquals("Al-Fatihah", state.currentSurah.nameEnglish)
        assertTrue(state.surahs.isNotEmpty())
    }

    @Test
    fun selectSurah_updatesCurrentSurah() = runTest(testDispatcher) {
        val vm = QuranViewModel()
        vm.onIntent(QuranUiIntent.SelectSurah(1))
        assertEquals(1, vm.uiState.value.currentSurah.number)
    }

    @Test
    fun searchWithoutLibrary_storesQueryAndStopsSpinner() = runTest(testDispatcher) {
        val vm = QuranViewModel()
        vm.onIntent(QuranUiIntent.SearchAyahs("mercy"))
        advanceUntilIdle()

        assertEquals("mercy", vm.uiState.value.searchQuery)
        assertFalse(vm.uiState.value.isSearching)
        assertTrue(vm.uiState.value.searchResults.isEmpty())
    }

    @Test
    fun clearSearch_resetsResults() = runTest(testDispatcher) {
        val vm = QuranViewModel()
        vm.onIntent(QuranUiIntent.SearchAyahs("mercy"))
        vm.onIntent(QuranUiIntent.ClearSearch)

        assertEquals("", vm.uiState.value.searchQuery)
        assertTrue(vm.uiState.value.searchResults.isEmpty())
    }

    @Test
    fun unknownSurahWithoutLibrary_fallsBackToBundledSurah() = runTest(testDispatcher) {
        val vm = QuranViewModel()
        vm.onIntent(QuranUiIntent.SelectSurah(99))

        // No Room metadata yet: the bundled Al-Fatihah keeps the reader usable.
        assertEquals("Al-Fatihah", vm.uiState.value.currentSurah.nameEnglish)
        assertEquals(7, vm.uiState.value.currentSurah.ayahs.size)
    }

    @Test
    fun selectAyah_setsHighlightedAyah() = runTest(testDispatcher) {
        val vm = QuranViewModel()
        vm.onIntent(QuranUiIntent.SelectAyahForTafseer(3))
        assertEquals(3, vm.uiState.value.activeHighlightedAyah)
    }

    @Test
    fun readingMode_togglesUpdatesTheReaderAndPersists() = runTest(testDispatcher) {
        val store = FakeSharedPreferences()
        val vm = QuranViewModel(quranReaderPreferences = QuranReaderPreferences(store))

        assertEquals(QuranReadingMode.STUDY, vm.uiState.value.readingMode)
        assertTrue(vm.uiState.value.isStudyMode)

        vm.onIntent(QuranUiIntent.ToggleReadingMode)
        assertEquals(QuranReadingMode.MUSHAF, vm.uiState.value.readingMode)
        assertFalse(vm.uiState.value.isStudyMode)

        // The mode the reader is rendering and the mode on disk stay in step.
        assertEquals(QuranReadingMode.MUSHAF, QuranReaderPreferences(store).readingMode)
        assertEquals(
            QuranReadingMode.MUSHAF,
            QuranViewModel(quranReaderPreferences = QuranReaderPreferences(store))
                .uiState.value.readingMode
        )

        // And it can be switched back, not only once.
        vm.onIntent(QuranUiIntent.SetReadingMode(QuranReadingMode.STUDY))
        assertEquals(QuranReadingMode.STUDY, vm.uiState.value.readingMode)
        assertEquals(QuranReadingMode.STUDY, QuranReaderPreferences(store).readingMode)
    }

    @Test
    fun firstLaunch_hasNoStoredReadingPosition() = runTest(testDispatcher) {
        val vm = QuranViewModel(quranReaderPreferences = QuranReaderPreferences(FakeSharedPreferences()))

        assertEquals(1, vm.uiState.value.activeHighlightedAyah)
        assertNull(vm.uiState.value.restoreScrollIndex)
    }

    @Test
    fun lastReadPosition_isRestoredOnReopen() = runTest(testDispatcher) {
        val store = FakeSharedPreferences()
        QuranReaderPreferences(store).saveReadingPosition(
            surahNumber = 2,
            ayahNumber = 255,
            scrollIndex = 42
        )

        val vm = QuranViewModel(quranReaderPreferences = QuranReaderPreferences(store))

        assertEquals(255, vm.uiState.value.activeHighlightedAyah)
        assertEquals(42, vm.uiState.value.restoreScrollIndex)
        assertEquals(2, vm.uiState.value.restoreSurahNumber)

        // The reader applies the index once, so it must be cleared afterwards.
        vm.onIntent(QuranUiIntent.ClearRestoreScrollIndex)
        assertNull(vm.uiState.value.restoreScrollIndex)
        assertNull(vm.uiState.value.restoreSurahNumber)
    }

    @Test
    fun selectingASurahPersistsTheReadingPosition() = runTest(testDispatcher) {
        val reader = QuranReaderPreferences(FakeSharedPreferences())
        val vm = QuranViewModel(quranReaderPreferences = reader)

        vm.onIntent(QuranUiIntent.SelectSurah(2))

        assertEquals(2, reader.lastReadPosition?.surahNumber)
    }

    @Test
    fun bookmarkToggle_addsRemovesAndSurvivesANewViewModel() = runTest(testDispatcher) {
        val store = FakeSharedPreferences()
        val reader = QuranReaderPreferences(store)
        val vm = QuranViewModel(quranReaderPreferences = reader)

        assertTrue(vm.uiState.value.bookmarks.isEmpty())

        vm.onIntent(QuranUiIntent.ToggleBookmark(1, 5))
        assertEquals(listOf(1 to 5), vm.uiState.value.bookmarks.map { it.key })
        // The reader highlights bookmarks of the chapter it is showing.
        assertTrue(5 in vm.uiState.value.bookmarkedAyahNumbers)
        assertTrue(reader.isBookmarked(1, 5))

        // Reopening the reader keeps the bookmark.
        val reopened = QuranViewModel(quranReaderPreferences = QuranReaderPreferences(store))
        assertEquals(listOf(1 to 5), reopened.uiState.value.bookmarks.map { it.key })

        vm.onIntent(QuranUiIntent.ToggleBookmark(1, 5))
        assertTrue(vm.uiState.value.bookmarks.isEmpty())
        assertFalse(reader.isBookmarked(1, 5))
    }

    @Test
    fun bookmarkedAyahNumbers_onlyCoversTheOpenSurah() = runTest(testDispatcher) {
        val reader = QuranReaderPreferences(FakeSharedPreferences())
        val vm = QuranViewModel(quranReaderPreferences = reader)

        vm.onIntent(QuranUiIntent.ToggleBookmark(1, 3))
        vm.onIntent(QuranUiIntent.ToggleBookmark(5, 1))

        assertEquals(setOf(3), vm.uiState.value.bookmarkedAyahNumbers)
    }

    @Test
    fun jumpingFromABookmark_targetsThatAyahForScrolling() = runTest(testDispatcher) {
        val vm = QuranViewModel(quranReaderPreferences = QuranReaderPreferences(FakeSharedPreferences()))

        vm.onIntent(QuranUiIntent.SelectSurahAtAyah(1, 3))

        assertEquals(3, vm.uiState.value.scrollTargetAyah)
        assertEquals(3, vm.uiState.value.activeHighlightedAyah)
    }
}
