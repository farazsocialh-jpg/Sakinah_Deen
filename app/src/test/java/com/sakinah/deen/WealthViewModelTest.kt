package com.sakinah.deen

import com.sakinah.deen.data.repository.NisabRateRepository
import com.sakinah.deen.data.repository.NisabRates
import com.sakinah.deen.fakes.FakeNisabRateRepository
import com.sakinah.deen.fakes.FakeSadaqahDao
import com.sakinah.deen.ui.viewmodel.WealthUiIntent
import com.sakinah.deen.ui.viewmodel.WealthUiState
import com.sakinah.deen.ui.viewmodel.WealthViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    /** Records currency switches and refreshes, which the simple fake cannot do. */
    private class CountingNisabRateRepository(
        private val fresh: NisabRates
    ) : NisabRateRepository {
        private val _rates = MutableStateFlow(NisabRates.FALLBACK)
        override val rates: StateFlow<NisabRates> = _rates
        var refreshCount = 0
        val currencyRequests = mutableListOf<String>()

        override fun setCurrency(code: String) {
            currencyRequests += code
        }

        override suspend fun refresh(): Result<Unit> {
            refreshCount++
            _rates.value = fresh
            return Result.success(Unit)
        }
    }

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
        assertEquals("USD", vm.uiState.value.nisabRateCurrencyCode)
        assertEquals("Live API", vm.uiState.value.rateSource)
        assertFalse(vm.uiState.value.isNisabRateFallback)
    }

    @Test
    fun offlineFallback_keepsCachedRateWhenRefreshFails() = runTest(testDispatcher) {
        val cached = NisabRates(
            goldPricePerGram = 87.25f,
            silverPricePerGram = 1.10f,
            lastUpdatedEpochMillis = 1_700_000_000_000L,
            sourceLabel = "Offline estimate"
        )
        val repo = FakeNisabRateRepository(initial = cached).apply { shouldFail = true }

        val vm = WealthViewModel(sadaqahDao = FakeSadaqahDao(), nisabRateRepository = repo)
        advanceUntilIdle()

        assertTrue(repo.refreshCalled)
        assertEquals(87.25f, vm.uiState.value.goldPricePerGram, 0.001f)
        assertEquals(1_700_000_000_000L, vm.uiState.value.nisabLastUpdatedEpochMillis)
        assertEquals(85f * 87.25f, vm.uiState.value.nisabThreshold, 0.01f)
        assertEquals("USD", vm.uiState.value.nisabRateCurrencyCode)
        assertEquals("Offline Fallback Estimate", vm.uiState.value.rateSource)
        assertTrue(vm.uiState.value.isNisabRateFallback)
    }

    @Test
    fun rateInAnotherCurrencyIsNeverMarkedLive() = runTest(testDispatcher) {
        val foreign = NisabRates(
            goldPricePerGram = 90.0f,
            silverPricePerGram = 1.20f,
            lastUpdatedEpochMillis = 1_760_000_000_000L,
            currencyCode = "EUR",
            sourceLabel = "Live API"
        )
        val vm = WealthViewModel(
            sadaqahDao = FakeSadaqahDao(),
            nisabRateRepository = FakeNisabRateRepository(fresh = foreign)
        )
        advanceUntilIdle()

        val state = vm.uiState.value
        // A fresh timestamp alone is not enough: the quote must be in the wallet currency.
        assertEquals("EUR", state.nisabRateCurrencyCode)
        assertEquals("Offline Fallback Estimate", state.rateSource)
        assertTrue(state.isNisabRateFallback)
    }

    @Test
    fun untimestampedRateIsNeverMarkedLive() = runTest(testDispatcher) {
        val unstamped = NisabRates(
            goldPricePerGram = 90.0f,
            silverPricePerGram = 1.20f,
            lastUpdatedEpochMillis = 0L,
            currencyCode = "USD"
        )
        val vm = WealthViewModel(
            sadaqahDao = FakeSadaqahDao(),
            nisabRateRepository = FakeNisabRateRepository(fresh = unstamped)
        )
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isNisabRateFallback)
        assertEquals("Offline Fallback Estimate", vm.uiState.value.rateSource)
    }

    @Test
    fun changingCurrencyRefetchesNisabAndKeepsBalancesCleared() = runTest(testDispatcher) {
        val qarRate = NisabRates(
            goldPricePerGram = 333.0f,
            silverPricePerGram = 4.2f,
            lastUpdatedEpochMillis = 1_760_000_000_000L,
            currencyCode = "QAR",
            sourceLabel = "Live API"
        )
        val repo = CountingNisabRateRepository(qarRate)
        val vm = WealthViewModel(sadaqahDao = FakeSadaqahDao(), nisabRateRepository = repo)
        advanceUntilIdle()
        assertEquals(1, repo.refreshCount)

        vm.onIntent(WealthUiIntent.SetCurrency("QAR"))
        advanceUntilIdle()

        assertEquals(2, repo.refreshCount)
        assertEquals("QAR", repo.currencyRequests.last())
        val state = vm.uiState.value
        assertEquals("QAR", state.nisabRateCurrencyCode)
        assertEquals("Live API", state.rateSource)
        assertFalse(state.isNisabRateFallback)
        assertEquals(85f * 333.0f, state.nisabThreshold, 0.01f)
        // The conservative balance-clearing behaviour is unchanged.
        assertEquals(0f, state.cashInBank, 0.001f)
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
        assertEquals("Offline estimate", NisabRates.FALLBACK.sourceLabel)
    }

    @Test
    fun nisabTimestampLabel_reportsEstimateWhenNeverUpdated() {
        assertEquals(
            "Nisab rates: built-in estimate · update when online",
            com.sakinah.deen.ui.screens.formatNisabTimestamp(0L)
        )
    }
}