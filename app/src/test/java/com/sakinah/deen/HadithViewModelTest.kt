package com.sakinah.deen

import com.sakinah.deen.ui.viewmodel.HadithUiIntent
import com.sakinah.deen.ui.viewmodel.HadithViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class HadithViewModelTest {
    private lateinit var testDispatcher: TestDispatcher
    @Before fun setUp() { testDispatcher = StandardTestDispatcher(); Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }
    @Test
    fun initialState_hasHadiths() = runTest(testDispatcher) {
        val vm = HadithViewModel(computeDispatcher = testDispatcher)
        assertTrue(vm.uiState.value.hadiths.isNotEmpty())
        assertTrue(vm.uiState.value.filteredHadiths.isNotEmpty())
    }

    @Test
    fun searchQuery_filtersResults() = runTest(testDispatcher) {
        val vm = HadithViewModel(computeDispatcher = testDispatcher)
        vm.onIntent(HadithUiIntent.SearchQueryChanged("intentions"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.filteredHadiths.any { it.matnTranslation.contains("intentions", ignoreCase = true) })
    }

    @Test
    fun selectBook_filtersToSingleCollection() = runTest(testDispatcher) {
        val vm = HadithViewModel(computeDispatcher = testDispatcher)
        vm.onIntent(HadithUiIntent.SelectBook("muslim"))

        val results = vm.uiState.value.filteredHadiths
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.bookKey == "muslim" })
    }

    @Test
    fun toggleBookmark_updatesIdsAndModel() = runTest(testDispatcher) {
        val vm = HadithViewModel(computeDispatcher = testDispatcher)
        val target = vm.uiState.value.hadiths.first { !it.bookmarked }

        vm.onIntent(HadithUiIntent.ToggleBookmark(target.id))

        assertTrue(target.id in vm.uiState.value.bookmarkedIds)
        assertTrue(vm.uiState.value.hadiths.first { it.id == target.id }.bookmarked)

        vm.onIntent(HadithUiIntent.ToggleBookmark(target.id))
        assertTrue(target.id !in vm.uiState.value.bookmarkedIds)
    }

    @Test
    fun clearSearch_restoresFullList() = runTest(testDispatcher) {
        val vm = HadithViewModel(computeDispatcher = testDispatcher)
        val full = vm.uiState.value.filteredHadiths.size
        vm.onIntent(HadithUiIntent.SearchQueryChanged("intentions"))
        advanceUntilIdle()
        vm.onIntent(HadithUiIntent.ClearSearch)

        assertEquals(full, vm.uiState.value.filteredHadiths.size)
    }

    @Test
    fun noMatchSearch_returnsEmptyWithoutCrashing() = runTest(testDispatcher) {
        val vm = HadithViewModel(computeDispatcher = testDispatcher)
        vm.onIntent(HadithUiIntent.SearchQueryChanged("zzzz-not-present"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.filteredHadiths.isEmpty())
    }

    @Test
    fun selectBook_allKeepsAll() = runTest(testDispatcher) {
        val vm = HadithViewModel(computeDispatcher = testDispatcher)
        val before = vm.uiState.value.filteredHadiths.size
        vm.onIntent(com.sakinah.deen.ui.viewmodel.HadithUiIntent.SelectBook("all"))
        assertEquals(before, vm.uiState.value.filteredHadiths.size)
    }
}