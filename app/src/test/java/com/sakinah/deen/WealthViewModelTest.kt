package com.sakinah.deen

import com.sakinah.deen.data.repository.NisabRates
import com.sakinah.deen.fakes.FakeNisabRateRepository
import com.sakinah.deen.fakes.FakeSadaqahDao
import com.sakinah.deen.ui.viewmodel.WealthUiIntent
import com.sakinah.deen.ui.viewmodel.WealthUiState
import com.sakinah.deen.ui.viewmodel.WealthViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WealthViewModelTest {

    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun nisabMath_goldStandard_usesEightyFiveGrams() = runTest(testDispatcher) {
        val vm = WealthViewModel(sadaqahDao = FakeSadaqahDao(), nisabRateRepository = null)
        vm.onIntent(WealthUiIntent.SetGoldStandard(true))
        assertEquals(85f * 88.40f, vm.uiState.value.nisabThreshold, 0.01f)
    }

    @Test
    fun nisabMath_silverStandard_usesFiveHundredNinetyFiveGrams() = runTest(testDispatcher) {
        val vm = WealthViewModel(sadaqahDao = FakeSadaqahDao(), nisabRateRepository = null)
        vm.onIntent(WealthUiIntent.SetGoldStandard(false))
        assertEquals(595f * 1.15f, vm.uiState.value.nisabThreshold, 0.01f)
    }

    @Test
    fun liveRates_replaceHardcodedPricesAndTimestamp() = runTest(testDispatcher) {
        val repo = FakeNisabRateRepository()
        val vm = WealthViewModel(sadaqahDao = FakeSadaqahDao(), nisabRateRepository = repo)
        advanceUntilIdle()

        assertTrue(repo.refreshCalled)
        assertEquals(90.0f, vm.uiState.value.goldPricePerGram, 0.001f)
        assertEquals(1.20f, vm.uiState.value.silverPricePerGram, 0.001f)
        assertEquals(1_760_000_000_000L, vm.uiState.value.nisabLastUpdatedEpochMillis)
        // Threshold recomputed from the live rate, not the hardcoded 88.40 constant.
        assertEquals(85f * 90.0f, vm.uiState.value.nisabThreshold, 0.01f)
    }

    @Test
    fun offlineFallback_keepsCachedRateWhenRefreshFails() = runTest(testDispatcher) {
        val cached = NisabRates(goldPricePerGram = 87.25f, silverPricePerGram = 1.10f, lastUpdatedEpochMillis = 1_700_000_000_000L)
        val repo = FakeNisabRateRepository(initial = cached).apply { shouldFail = true }

        val vm = WealthViewModel(sadaqahDao = FakeSadaqahDao(), nisabRateRepository = repo)
        advanceUntilIdle()

        assertTrue(repo.refreshCalled)
        assertEquals(87.25f, vm.uiState.value.goldPricePerGram, 0.001f)
        assertEquals(1_700_000_000_000L, vm.uiState.value.nisabLastUpdatedEpochMillis)
        assertEquals(85f * 87.25f, vm.uiState.value.nisabThreshold, 0.01f)
    }

    @Test
    fun zakatPayable_isTwoPointFivePercentAboveNisab() = runTest(testDispatcher) {
        val vm = WealthViewModel(sadaqahDao = FakeSadaqahDao(), nisabRateRepository = null)
        vm.onIntent(WealthUiIntent.UpdateCash(20_000f))
        vm.onIntent(WealthUiIntent.UpdateGoldHeld(0f))
        vm.onIntent(WealthUiIntent.UpdateSilverHeld(0f))
        vm.onIntent(WealthUiIntent.UpdateStocksValue(0f))
        vm.onIntent(WealthUiIntent.UpdateDebts(0f))

        val state = vm.uiState.value
        assertTrue(state.isEligible)
        assertEquals(20_000f * 0.025f, state.zakatPayable, 0.5f)
    }

    @Test
    fun belowNisab_isNotEligible() = runTest(testDispatcher) {
        val vm = WealthViewModel(sadaqahDao = FakeSadaqahDao(), nisabRateRepository = null)
        vm.onIntent(WealthUiIntent.ResetBalances)
        vm.onIntent(WealthUiIntent.UpdateCash(100f))

        val state = vm.uiState.value
        assertFalse(state.isEligible)
        assertEquals(0f, state.zakatPayable, 0.001f)
    }

    @Test
    fun sadaqahTotalsAreGroupedByCurrency_neverSummedTogether() = runTest(testDispatcher) {
        val dao = FakeSadaqahDao()
        val vm = WealthViewModel(sadaqahDao = dao, nisabRateRepository = null)
        advanceUntilIdle()

        vm.onIntent(WealthUiIntent.SetCurrency("QAR"))
        vm.onIntent(WealthUiIntent.AddMicroSadaqah(10f))
        // Let the insert run while QAR is selected before switching currency.
        advanceUntilIdle()
        vm.onIntent(WealthUiIntent.SetCurrency("USD"))
        vm.onIntent(WealthUiIntent.AddMicroSadaqah(5f))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(10.0, state.sadaqahMonthlyTotalsByCurrency["QAR"] ?: 0.0, 0.001)
        assertEquals(5.0, state.sadaqahMonthlyTotalsByCurrency["USD"] ?: 0.0, 0.001)
        // The jar shows only the selected currency's total, not 15.
        assertEquals(5f, state.sadaqahJarCollected, 0.001f)
        assertEquals(setOf("QAR", "USD"), dao.getAllLogs().map { it.currency }.toSet())
    }

    @Test
    fun quickSadaqahUsesTheSelectedCurrency() = runTest(testDispatcher) {
        val dao = FakeSadaqahDao()
        val vm = WealthViewModel(sadaqahDao = dao, nisabRateRepository = null)
        advanceUntilIdle()

        vm.onIntent(WealthUiIntent.SetCurrency("QAR"))
        vm.onIntent(WealthUiIntent.AddMicroSadaqah(7f))
        advanceUntilIdle()

        assertEquals("QAR", dao.getAllLogs().single().currency)
    }

    @Test
    fun changingCurrencyNeverSilentlyRelabelsExistingBalances() = runTest(testDispatcher) {
        val vm = WealthViewModel(sadaqahDao = FakeSadaqahDao(), nisabRateRepository = null)
        vm.onIntent(WealthUiIntent.UpdateCash(1_000f))

        vm.onIntent(WealthUiIntent.SetCurrency("QAR"))

        val state = vm.uiState.value
        assertEquals("QAR", state.currencyCode)
        assertEquals(0f, state.cashInBank, 0.001f)
        assertFalse(state.isEligible)
        assertTrue(state.currencyChangeNotice?.contains("cleared") == true)

        vm.onIntent(WealthUiIntent.DismissCurrencyChangeNotice)
        assertEquals(null, vm.uiState.value.currencyChangeNotice)
    }

    @Test
    fun currencyChangeKeepsBalancesWhenTheSheetIsEmpty() = runTest(testDispatcher) {
        val vm = WealthViewModel(sadaqahDao = FakeSadaqahDao(), nisabRateRepository = null)
        vm.onIntent(WealthUiIntent.SetCurrency("QAR"))

        assertEquals("QAR", vm.uiState.value.currencyCode)
        assertEquals(null, vm.uiState.value.currencyChangeNotice)
    }

    @Test
    fun monthBoundariesRecomputePerCalendarMonth() {
        val zone = java.time.ZoneId.of("UTC")
        val january = WealthViewModel.monthBounds(java.time.LocalDate.of(2026, 1, 15), zone)
        val february = WealthViewModel.monthBounds(java.time.LocalDate.of(2026, 2, 1), zone)

        assertEquals(java.time.Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(), january.first)
        assertEquals(java.time.Instant.parse("2026-01-31T23:59:59.999Z").toEpochMilli(), january.second)
        assertEquals(java.time.Instant.parse("2026-02-01T00:00:00Z").toEpochMilli(), february.first)
        assertTrue(february.first > january.second)
    }

    @Test
    fun nisabFallbackKeepsExplicitCurrencyAndFreshnessMetadata() {
        val state = WealthUiState()
        assertEquals("USD", state.nisabRateCurrencyCode)
        assertTrue(state.isNisabRateFallback)
        assertEquals(0L, state.nisabLastUpdatedEpochMillis)
    }

    @Test
    fun nisabTimestampLabel_reportsEstimateWhenNeverUpdated() {
        assertEquals(
            "Nisab rates: built-in estimate · update when online",
            com.sakinah.deen.ui.screens.formatNisabTimestamp(0L)
        )
    }
}