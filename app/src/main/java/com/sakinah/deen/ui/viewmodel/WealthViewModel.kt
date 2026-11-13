package com.sakinah.deen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakinah.deen.data.database.dao.SadaqahDao
import com.sakinah.deen.data.database.entity.SadaqahLogEntity
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
    val goldPricePerGram: Float = 88.40f,
    val silverPricePerGram: Float = 1.15f,
    val nisabLastUpdatedEpochMillis: Long = 0L,
    val cashInBank: Float = 14200f,
    val goldHeld: Float = 4500f,
    val silverHeld: Float = 0f,
    val tradeInventory: Float = 0f,
    val stocksValue: Float = 6800f,
    val shortTermDebts: Float = 1800f,
    val sadaqahJarCollected: Float = 0f,
    val sadaqahGoal: Float = 100f,
    val nisabThreshold: Float = 85f * 88.40f, // 7514.0f
    val grossAssets: Float = 14200f + 4500f + 6800f,
    val netEligible: Float = (14200f + 4500f + 6800f) - 1800f,
    val isEligible: Boolean = true,
    val zakatPayable: Float = ((14200f + 4500f + 6800f) - 1800f) * 0.025f,
    val recentSadaqahLogs: List<SadaqahLogEntity> = emptyList(),
    /** Surfaced failure for nisab/sadaqah work, retried through [WealthUiIntent.RefreshNisabRates]. */
    val errorMessage: String? = null
)

sealed class WealthUiIntent {
    data class SetGoldStandard(val isGold: Boolean) : WealthUiIntent()
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
    private val nisabRateRepository: NisabRateRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(WealthUiState())
    val uiState: StateFlow<WealthUiState> = _uiState.asStateFlow()

    init {
        recalculateZakat()
        observeSadaqah()
        observeNisabRates()
    }

    private fun observeNisabRates() {
        val repo = nisabRateRepository ?: return
        viewModelScope.launch {
            repo.rates.collectLatest { rates: NisabRates ->
                _uiState.update {
                    it.copy(
                        goldPricePerGram = rates.goldPricePerGram,
                        silverPricePerGram = rates.silverPricePerGram,
                        nisabLastUpdatedEpochMillis = rates.lastUpdatedEpochMillis
                    )
                }
                recalculateZakat()
            }
        }
        refreshNisabRates(repo)
    }

    private fun refreshNisabRates(repo: NisabRateRepository? = nisabRateRepository) {
        val target = repo ?: return
        viewModelScope.launch {
            try {
                target.refresh()
                _uiState.update { it.copy(errorMessage = null) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Could not refresh nisab rates: ${e.message ?: "network error"}")
                }
            }
        }
    }

    fun onIntent(intent: WealthUiIntent) {
        when (intent) {
            is WealthUiIntent.SetGoldStandard -> {
                _uiState.update { it.copy(isGoldStandard = intent.isGold) }
                recalculateZakat()
            }
            is WealthUiIntent.UpdateCash -> {
                _uiState.update { it.copy(cashInBank = intent.amount.coerceAtLeast(0f)) }
                recalculateZakat()
            }
            is WealthUiIntent.UpdateGoldHeld -> {
                _uiState.update { it.copy(goldHeld = intent.amount.coerceAtLeast(0f)) }
                recalculateZakat()
            }
            is WealthUiIntent.UpdateSilverHeld -> {
                _uiState.update { it.copy(silverHeld = intent.amount.coerceAtLeast(0f)) }
                recalculateZakat()
            }
            is WealthUiIntent.UpdateTradeInventory -> {
                _uiState.update { it.copy(tradeInventory = intent.amount.coerceAtLeast(0f)) }
                recalculateZakat()
            }
            is WealthUiIntent.UpdateStocksValue -> {
                _uiState.update { it.copy(stocksValue = intent.amount.coerceAtLeast(0f)) }
                recalculateZakat()
            }
            is WealthUiIntent.UpdateDebts -> {
                _uiState.update { it.copy(shortTermDebts = intent.amount.coerceAtLeast(0f)) }
                recalculateZakat()
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
