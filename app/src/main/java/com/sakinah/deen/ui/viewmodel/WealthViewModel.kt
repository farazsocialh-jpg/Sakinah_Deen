package com.sakinah.deen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakinah.deen.data.database.dao.SadaqahDao
import com.sakinah.deen.data.database.entity.SadaqahLogEntity
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.repository.NisabRateRepository
import com.sakinah.deen.data.repository.NisabRates
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class WealthUiState(
    val isGoldStandard: Boolean = true,
    val currencyCode: String = "USD",
    val goldPricePerGram: Float = 88.40f,
    val silverPricePerGram: Float = 1.15f,
    val nisabLastUpdatedEpochMillis: Long = 0L,
    val rateSource: String = "Offline Fallback Estimate",
    /** Currency the gold/silver rates are quoted in; never implied by a bare number. */
    val nisabRateCurrencyCode: String = "USD",
    val isManualNisabOverride: Boolean = false,
    /** True while the built-in benchmark (not a fetched quote) prices the Nisab. */
    val isNisabRateFallback: Boolean = true,
    /** Explains why balances were reset after a currency switch. */
    val currencyChangeNotice: String? = null,
    val cashInBank: Float = 0f,
    val goldHeld: Float = 0f,
    val silverHeld: Float = 0f,
    val tradeInventory: Float = 0f,
    val stocksValue: Float = 0f,
    val shortTermDebts: Float = 0f,
    val sadaqahJarCollected: Float = 0f,
    val sadaqahGoal: Float = 100f,
    val sadaqahMonthlyTotalsByCurrency: Map<String, Double> = emptyMap(),
    val nisabThreshold: Float = 85f * 88.40f, // 7514.0f
    val grossAssets: Float = 0f,
    val netEligible: Float = 0f,
    val isEligible: Boolean = false,
    val zakatPayable: Float = 0f,
    val recentSadaqahLogs: List<SadaqahLogEntity> = emptyList(),
    val isRefreshingRates: Boolean = false,
    /** Surfaced failure for nisab/sadaqah work, retried through [WealthUiIntent.RefreshNisabRates]. */
    val errorMessage: String? = null
)

sealed class WealthUiIntent {
    data class SetGoldStandard(val isGold: Boolean) : WealthUiIntent()
    data class SetCurrency(val code: String) : WealthUiIntent()
    data class SetManualGoldPrice(val price: Float) : WealthUiIntent()
    data class SetManualSilverPrice(val price: Float) : WealthUiIntent()
    data class ToggleManualNisabOverride(val enabled: Boolean) : WealthUiIntent()
    data class UpdateCash(val amount: Float) : WealthUiIntent()
    data class UpdateGoldHeld(val amount: Float) : WealthUiIntent()
    data class UpdateSilverHeld(val amount: Float) : WealthUiIntent()
    data class UpdateTradeInventory(val amount: Float) : WealthUiIntent()
    data class UpdateStocksValue(val amount: Float) : WealthUiIntent()
    data class UpdateDebts(val amount: Float) : WealthUiIntent()
    data class AddMicroSadaqah(val amount: Float, val note: String? = null) : WealthUiIntent()
    data object RefreshNisabRates : WealthUiIntent()
    data object ResetBalances : WealthUiIntent()

    /** Dismisses the currency-change explanation after the user has read it. */
    data object DismissCurrencyChangeNotice : WealthUiIntent()
}

class WealthViewModel(
    private val sadaqahDao: SadaqahDao,
    private val nisabRateRepository: NisabRateRepository? = null,
    private val preferences: SakinahPreferences? = null,
    /** Production enables the midnight rollover; unit tests keep it off to stay finite. */
    private val observeMonthRollover: Boolean = false
) : ViewModel() {

    companion object {
        /** Start (inclusive) and end (inclusive) of the calendar month containing [today]. */
        internal fun monthBounds(today: LocalDate, zone: ZoneId): Pair<Long, Long> {
            val firstDay = today.withDayOfMonth(1)
            val start = firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
            val nextMonth = firstDay.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return start to (nextMonth - 1L)
        }
    }

    private val _uiState = MutableStateFlow(WealthUiState())
    val uiState: StateFlow<WealthUiState> = _uiState.asStateFlow()

    init {
        // Restore the saved balance sheet before the first calculation (guest mode included).
        preferences?.let { prefs ->
            _uiState.update {
                it.copy(
                    currencyCode = prefs.zakatCurrency,
                    cashInBank = prefs.zakatCashInBank,
                    goldHeld = prefs.zakatGoldHeld,
                    silverHeld = prefs.zakatSilverHeld,
                    tradeInventory = prefs.zakatTradeInventory,
                    stocksValue = prefs.zakatStocksValue,
                    shortTermDebts = prefs.zakatShortTermDebts
                )
            }
        }
        recalculateZakat()
        observeSadaqah()
        observeNisabRates()
    }

    /** Writes cash, gold, silver, stocks and debts to disk after every edit. */
    private fun persistBalances() {
        val prefs = preferences ?: return
        val s = _uiState.value
        prefs.zakatCurrency = s.currencyCode
        prefs.zakatCashInBank = s.cashInBank
        prefs.zakatGoldHeld = s.goldHeld
        prefs.zakatSilverHeld = s.silverHeld
        prefs.zakatTradeInventory = s.tradeInventory
        prefs.zakatStocksValue = s.stocksValue
        prefs.zakatShortTermDebts = s.shortTermDebts
    }

    private fun isOfflineRate(rates: NisabRates): Boolean =
        rates.sourceLabel?.let { label ->
            label.contains("offline", ignoreCase = true) ||
                label.contains("fallback", ignoreCase = true) ||
                label.contains("estimate", ignoreCase = true)
        } ?: false

    private fun rateSourceFor(rates: NisabRates, walletCurrency: String): String =
        if (rates.lastUpdatedEpochMillis > 0L &&
            rates.currencyCode.equals(walletCurrency, ignoreCase = true) &&
            !isOfflineRate(rates)
        ) "Live API" else "Offline Fallback Estimate"

    private fun observeNisabRates() {
        val repo = nisabRateRepository ?: return
        // Restore the persisted wallet currency into the rate source before the first read.
        repo.setCurrency(_uiState.value.currencyCode)
        viewModelScope.launch {
            repo.rates.collectLatest { rates: NisabRates ->
                if (_uiState.value.isManualNisabOverride) return@collectLatest
                // A rate is only "Live API" when it has a real timestamp AND is quoted in
                // the currency the user is entering balances in. A stale/foreign-currency
                // quote must keep the honest offline label and its own currency code.
                val source = rateSourceFor(rates, _uiState.value.currencyCode)
                _uiState.update {
                    it.copy(
                        goldPricePerGram = rates.goldPricePerGram,
                        silverPricePerGram = rates.silverPricePerGram,
                        nisabLastUpdatedEpochMillis = rates.lastUpdatedEpochMillis,
                        nisabRateCurrencyCode = rates.currencyCode,
                        rateSource = source,
                        isNisabRateFallback = source != "Live API"
                    )
                }
                recalculateZakat()
                persistBalances()
            }
        }
        refreshNisabRates(repo)
    }

    private fun refreshNisabRates(repo: NisabRateRepository? = nisabRateRepository) {
        val target = repo ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingRates = true) }
            try {
                val result = target.refresh()
                if (result.isSuccess) {
                    // The rates collector owns source metadata: a successful fetch whose
                    // quote currency does not match the wallet must not be called live.
                    _uiState.update { it.copy(errorMessage = null) }
                } else {
                    _uiState.update {
                        it.copy(
                            errorMessage = "Using the built-in Nisab benchmark; live market sync is unavailable.",
                            rateSource = "Offline Fallback Estimate",
                            isNisabRateFallback = true
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = "Using the built-in Nisab benchmark; live market sync is unavailable."
                    )
                }
            } finally {
                _uiState.update { it.copy(isRefreshingRates = false) }
            }
        }
    }

    fun onIntent(intent: WealthUiIntent) {
        when (intent) {
            is WealthUiIntent.SetGoldStandard -> {
                _uiState.update { it.copy(isGoldStandard = intent.isGold) }
                recalculateZakat()
                persistBalances()
            }
            is WealthUiIntent.SetCurrency -> {
                val code = intent.code.trim().uppercase()
                val current = _uiState.value
                if (code.isBlank() || code == current.currencyCode) return
                val hasBalances = listOf(
                    current.cashInBank, current.goldHeld, current.silverHeld,
                    current.tradeInventory, current.stocksValue, current.shortTermDebts
                ).any { it > 0f }
                // No verified FX rate is available offline, so existing amounts must not be
                // silently relabelled as a different currency. Reset them and say why.
                _uiState.update {
                    if (hasBalances) {
                        it.copy(
                            currencyCode = code,
                            cashInBank = 0f,
                            goldHeld = 0f,
                            silverHeld = 0f,
                            tradeInventory = 0f,
                            stocksValue = 0f,
                            shortTermDebts = 0f,
                            sadaqahJarCollected = (it.sadaqahMonthlyTotalsByCurrency[code] ?: 0.0).toFloat(),
                            currencyChangeNotice =
                                "Currency changed to $code. Existing balances were cleared " +
                                    "instead of being reinterpreted without a verified exchange rate."
                        )
                    } else {
                        it.copy(
                            currencyCode = code,
                            sadaqahJarCollected = (it.sadaqahMonthlyTotalsByCurrency[code] ?: 0.0).toFloat()
                        )
                    }
                }
                // StateFlow may not re-emit an unchanged rate object, so re-evaluate the
                // source label for the new wallet currency immediately.
                nisabRateRepository?.rates?.value?.let { currentRates ->
                    val source = rateSourceFor(currentRates, code)
                    _uiState.update {
                        it.copy(
                            nisabRateCurrencyCode = currentRates.currencyCode,
                            rateSource = source,
                            isNisabRateFallback = source != "Live API"
                        )
                    }
                }
                recalculateZakat()
                persistBalances()
                // The old quote is in the previous currency: re-read/refetch nisab for the
                // new one. Balances above stay cleared; no FX guess is applied to them.
                nisabRateRepository?.setCurrency(code)
                refreshNisabRates()
            }
            is WealthUiIntent.DismissCurrencyChangeNotice -> {
                _uiState.update { it.copy(currencyChangeNotice = null) }
            }
            is WealthUiIntent.ToggleManualNisabOverride -> {
                _uiState.update {
                    it.copy(
                        isManualNisabOverride = intent.enabled,
                        rateSource = if (intent.enabled) "Manual Override" else it.rateSource
                    )
                }
                recalculateZakat()
            }
            is WealthUiIntent.SetManualGoldPrice -> {
                _uiState.update { it.copy(goldPricePerGram = intent.price.coerceAtLeast(0f)) }
                recalculateZakat()
            }
            is WealthUiIntent.SetManualSilverPrice -> {
                _uiState.update { it.copy(silverPricePerGram = intent.price.coerceAtLeast(0f)) }
                recalculateZakat()
            }
            is WealthUiIntent.UpdateCash -> {
                _uiState.update { it.copy(cashInBank = intent.amount.coerceAtLeast(0f)) }
                recalculateZakat()
                persistBalances()
            }
            is WealthUiIntent.UpdateGoldHeld -> {
                _uiState.update { it.copy(goldHeld = intent.amount.coerceAtLeast(0f)) }
                recalculateZakat()
                persistBalances()
            }
            is WealthUiIntent.UpdateSilverHeld -> {
                _uiState.update { it.copy(silverHeld = intent.amount.coerceAtLeast(0f)) }
                recalculateZakat()
                persistBalances()
            }
            is WealthUiIntent.UpdateTradeInventory -> {
                _uiState.update { it.copy(tradeInventory = intent.amount.coerceAtLeast(0f)) }
                recalculateZakat()
                persistBalances()
            }
            is WealthUiIntent.UpdateStocksValue -> {
                _uiState.update { it.copy(stocksValue = intent.amount.coerceAtLeast(0f)) }
                recalculateZakat()
                persistBalances()
            }
            is WealthUiIntent.UpdateDebts -> {
                _uiState.update { it.copy(shortTermDebts = intent.amount.coerceAtLeast(0f)) }
                recalculateZakat()
                persistBalances()
            }
            is WealthUiIntent.AddMicroSadaqah -> {
                addMicroSadaqah(intent.amount, intent.note)
            }
            is WealthUiIntent.RefreshNisabRates -> refreshNisabRates()
            is WealthUiIntent.ResetBalances -> {
                _uiState.update {
                    it.copy(
                        cashInBank = 0f,
                        goldHeld = 0f,
                        silverHeld = 0f,
                        tradeInventory = 0f,
                        stocksValue = 0f,
                        shortTermDebts = 0f
                    )
                }
                recalculateZakat()
                persistBalances()
            }
        }
    }

    private fun recalculateZakat() {
        val s = _uiState.value
        val threshold = if (s.isGoldStandard) 85f * s.goldPricePerGram else 595f * s.silverPricePerGram
        val gross = s.cashInBank + s.goldHeld + s.silverHeld + s.tradeInventory + s.stocksValue
        val net = (gross - s.shortTermDebts).coerceAtLeast(0f)
        val eligible = net >= threshold
        val payable = if (eligible) net * 0.025f else 0f

        _uiState.update {
            it.copy(
                nisabThreshold = threshold,
                grossAssets = gross,
                netEligible = net,
                isEligible = eligible,
                zakatPayable = payable
            )
        }
    }

    private fun observeSadaqah() {
        viewModelScope.launch {
            sadaqahDao.observeRecentLogs(limit = 10).collect { logs ->
                _uiState.update { it.copy(recentSadaqahLogs = logs) }
            }
        }

        // Month boundaries are recomputed on every rollover instead of being captured once,
        // so a long-lived process does not keep showing last month's jar total.
        viewModelScope.launch {
            while (isActive) {
                val zone = ZoneId.systemDefault()
                val (startOfMonth, endOfMonth) = monthBounds(LocalDate.now(zone), zone)
                val nextMonthStart = endOfMonth + 1L

                val collector = launch {
                    // Grouped by currency: QAR + USD must never be summed into one number.
                    sadaqahDao.observeMonthlyTotalsByCurrency(startOfMonth, endOfMonth)
                        .collect { totals ->
                            val byCurrency = totals.associate {
                                it.currency.trim().uppercase() to it.total
                            }
                            _uiState.update { state ->
                                state.copy(
                                    sadaqahMonthlyTotalsByCurrency = byCurrency,
                                    sadaqahJarCollected =
                                        (byCurrency[state.currencyCode.uppercase()] ?: 0.0).toFloat()
                                )
                            }
                        }
                }
                if (!observeMonthRollover) {
                    collector.join()
                    return@launch
                }
                val waitMillis = (nextMonthStart - System.currentTimeMillis()).coerceAtLeast(1_000L)
                delay(waitMillis)
                collector.cancel()
            }
        }
    }

    private fun addMicroSadaqah(amount: Float, note: String?) {
        viewModelScope.launch {
            try {
                sadaqahDao.insertLog(
                    SadaqahLogEntity(
                        id = UUID.randomUUID().toString(),
                        amount = amount.toDouble(),
                        currency = _uiState.value.currencyCode.uppercase(),
                        note = note ?: "Micro-Sadaqah Daily Jar",
                        timestamp = System.currentTimeMillis()
                    )
                )
                _uiState.update { it.copy(errorMessage = null) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Could not record sadaqah: ${e.message ?: "database error"}")
                }
            }
        }
    }
}
