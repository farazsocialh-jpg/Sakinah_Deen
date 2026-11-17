package com.sakinah.deen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakinah.deen.data.database.dao.SadaqahDao
import com.sakinah.deen.data.database.entity.SadaqahLogEntity
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.repository.NisabRateRepository
import com.sakinah.deen.data.repository.NisabRates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
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
    val isManualNisabOverride: Boolean = false,
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
}

class WealthViewModel(
    private val sadaqahDao: SadaqahDao,
    private val nisabRateRepository: NisabRateRepository? = null,
    private val preferences: SakinahPreferences? = null
) : ViewModel() {

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

    private fun observeNisabRates() {
        val repo = nisabRateRepository ?: return
        viewModelScope.launch {
            repo.rates.collectLatest { rates: NisabRates ->
                if (_uiState.value.isManualNisabOverride) return@collectLatest
                _uiState.update {
                    it.copy(
                        goldPricePerGram = rates.goldPricePerGram,
                        silverPricePerGram = rates.silverPricePerGram,
                        nisabLastUpdatedEpochMillis = rates.lastUpdatedEpochMillis,
                        rateSource = "Live API"
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
                    _uiState.update { it.copy(errorMessage = null, rateSource = "Live API") }
                } else {
                    _uiState.update {
                        it.copy(
                            errorMessage = "Using the built-in Nisab benchmark; live market sync is unavailable.",
                            rateSource = "Offline Fallback Estimate"
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
                _uiState.update { it.copy(currencyCode = intent.code) }
                persistBalances()
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

        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val firstDay = LocalDate.now(zone).withDayOfMonth(1)
            val startOfMonth = firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
            val endOfMonth = firstDay.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
            sadaqahDao.observeMonthlyTotal(startOfMonth, endOfMonth).collect { monthlySum ->
                _uiState.update { it.copy(sadaqahJarCollected = (monthlySum ?: 0.0).toFloat()) }
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
                        currency = "USD",
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
