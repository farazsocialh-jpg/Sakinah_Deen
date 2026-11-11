package com.sakinah.deen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakinah.deen.data.database.dao.QadhaDao
import com.sakinah.deen.data.database.dao.SalahDao
import com.sakinah.deen.data.database.entity.SalahRecordEntity
import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.NaflType
import com.sakinah.deen.data.model.PrayerModel
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngine
import com.sakinah.deen.domain.astronomy.PrayerCountdownInfo
import com.sakinah.deen.domain.travel.TravelStatusManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class SalahUiState(
    val hijriDate: String = "14 Jumada al-Ula 1448 AH",
    val gregorianDate: String = "Tuesday · 11 November 2026",
    val prayerTimes: Map<PrayerType, LocalTime> = emptyMap(),
    val currentPrayer: PrayerType = PrayerType.DHUHR,
    val nextPrayer: PrayerType = PrayerType.ASR,
    val countdownText: String = "01h 24m 12s",
    val elapsedWindowRatio: Float = 0.65f,
    val prayersList: List<PrayerModel> = emptyList(),
    val qadhaDeficits: Map<PrayerType, Int> = emptyMap(),
    val isTraveler: Boolean = false,
    val calculationMethod: CalculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,
    val madhhab: Madhhab = Madhhab.HANAFI
)

sealed class SalahUiIntent {
    data class ToggleSalahCompletion(val prayerType: PrayerType, val isJamaah: Boolean) : SalahUiIntent()
    data class ToggleNaflCompletion(val prayerType: PrayerType, val nafl: NaflType) : SalahUiIntent()
    data class IncrementQadha(val prayerType: PrayerType) : SalahUiIntent()
    data class DecrementQadha(val prayerType: PrayerType) : SalahUiIntent()
    data class ChangeCalculationMethod(val method: CalculationMethod) : SalahUiIntent()
    data class ChangeMadhhab(val madhhab: Madhhab) : SalahUiIntent()
}

class SalahViewModel(
    private val salahDao: SalahDao,
    private val qadhaDao: QadhaDao,
    private val astronomyEngine: PrayerCalculationEngine,
    private val travelManager: TravelStatusManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalahUiState())
    val uiState: StateFlow<SalahUiState> = _uiState.asStateFlow()

    private var userCoordinates: Pair<Double, Double> = Pair(51.5074, -0.1278) // London default

    init {
        loadDailySchedule()
        observeQadhaLedger()
    }

    fun onIntent(intent: SalahUiIntent) {
        when (intent) {
            is SalahUiIntent.ToggleSalahCompletion -> toggleSalahCompletion(intent.prayerType, intent.isJamaah)
            is SalahUiIntent.ToggleNaflCompletion -> toggleNaflCompletion(intent.prayerType, intent.nafl)
            is SalahUiIntent.IncrementQadha -> incrementQadha(intent.prayerType)
            is SalahUiIntent.DecrementQadha -> decrementQadha(intent.prayerType)
            is SalahUiIntent.ChangeCalculationMethod -> {
                _uiState.update { it.copy(calculationMethod = intent.method) }
                loadDailySchedule()
            }
            is SalahUiIntent.ChangeMadhhab -> {
                _uiState.update { it.copy(madhhab = intent.madhhab) }
                loadDailySchedule()
            }
        }
    }

    private fun loadDailySchedule() {
        val today = LocalDate.now()
        val times = astronomyEngine.getPrayerTimes(
            userCoordinates,
            today,
            _uiState.value.calculationMethod,
            _uiState.value.madhhab
        )

        val countdown: PrayerCountdownInfo = astronomyEngine.calculateCountdown(times, LocalTime.now())
        val travel = travelManager.evaluateTravelState(userCoordinates)

        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

        val prayerModels = listOf(
            PrayerModel("fajr", "Fajr", "الفجر", times[PrayerType.FAJR]?.format(timeFormatter) ?: "05:14 AM", travel.shortenedRakats[PrayerType.FAJR] ?: 2, 2, 0),
            PrayerModel("sunrise", "Sunrise", "الشروق", times[PrayerType.SUNRISE]?.format(timeFormatter) ?: "06:38 AM", 0, 0, 0),
            PrayerModel("dhuhr", "Dhuhr", "الظهر", times[PrayerType.DHUHR]?.format(timeFormatter) ?: "12:45 PM", travel.shortenedRakats[PrayerType.DHUHR] ?: 4, 4, 2, nawafilName = "2 Rakat Nafl"),
            PrayerModel("asr", "Asr", "العصر", times[PrayerType.ASR]?.format(timeFormatter) ?: "03:52 PM", travel.shortenedRakats[PrayerType.ASR] ?: 4, 4, 0),
            PrayerModel("maghrib", "Maghrib", "المغرب", times[PrayerType.MAGHRIB]?.format(timeFormatter) ?: "06:18 PM", travel.shortenedRakats[PrayerType.MAGHRIB] ?: 3, 0, 2, nawafilName = "Awwabin (6 Rakat)"),
            PrayerModel("isha", "Isha", "العشاء", times[PrayerType.ISHA]?.format(timeFormatter) ?: "07:44 PM", travel.shortenedRakats[PrayerType.ISHA] ?: 4, 4, 2, nawafilName = "Tahajjud & Witr")
        )

        _uiState.update {
            it.copy(
                prayerTimes = times,
                currentPrayer = countdown.currentPrayer,
                nextPrayer = countdown.nextPrayer,
                countdownText = "${countdown.remainingMinutes}m ${countdown.remainingSeconds}s",
                elapsedWindowRatio = countdown.elapsedRatio,
                prayersList = prayerModels,
                isTraveler = travel.isTraveler
            )
        }
    }

    private fun observeQadhaLedger() {
        viewModelScope.launch {
            qadhaDao.observeQadhaLedger().collect { entities ->
                val map = entities.associate {
                    try {
                        PrayerType.valueOf(it.prayerType) to it.totalMissed
                    } catch (e: Exception) {
                        PrayerType.FAJR to it.totalMissed
                    }
                }
                _uiState.update { it.copy(qadhaDeficits = map) }
            }
        }
    }

    private fun toggleSalahCompletion(prayerType: PrayerType, isJamaah: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.prayersList.map { p ->
                if (p.nameEnglish.uppercase() == prayerType.name) {
                    p.copy(isFardCompleted = !p.isFardCompleted, isJamaah = isJamaah)
                } else p
            }
            _uiState.update { it.copy(prayersList = updated) }

            salahDao.upsertSalahRecord(
                SalahRecordEntity(
                    dateEpochDay = LocalDate.now().toEpochDay(),
                    prayerType = prayerType.name,
                    isCompleted = true,
                    isJamaah = isJamaah,
                    isQadha = false,
                    completedTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    private fun toggleNaflCompletion(prayerType: PrayerType, nafl: NaflType) {
        val updated = _uiState.value.prayersList.map { p ->
            if (p.nameEnglish.uppercase() == prayerType.name) {
                p.copy(isNawafilCompleted = !p.isNawafilCompleted)
            } else p
        }
        _uiState.update { it.copy(prayersList = updated) }
    }

    private fun incrementQadha(prayerType: PrayerType) {
        viewModelScope.launch {
            qadhaDao.incrementMissed(prayerType.name)
        }
    }

    private fun decrementQadha(prayerType: PrayerType) {
        viewModelScope.launch {
            qadhaDao.decrementMissed(prayerType.name)
        }
    }
}
