package com.sakinah.deen

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
    fun toggleLayoutMode_flipsFlag() = runTest(testDispatcher) {
        val vm = QuranViewModel()
        val before = vm.uiState.value.isStudyMode
        vm.onIntent(QuranUiIntent.ToggleLayoutMode)
        assertEquals(!before, vm.uiState.value.isStudyMode)
    }
}