package com.sakinah.deen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakinah.deen.data.database.dao.QadhaDao
import com.sakinah.deen.data.database.dao.SalahDao
import com.sakinah.deen.data.database.entity.SalahRecordEntity
import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.NaflType
import com.sakinah.deen.data.model.PrayerAdjustments
import com.sakinah.deen.data.model.PrayerModel
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngine
import com.sakinah.deen.domain.astronomy.PrayerCountdownInfo
import com.sakinah.deen.domain.astronomy.UserLocationProvider
import com.sakinah.deen.domain.travel.TravelStatusManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate as KLocalDate
import kotlinx.datetime.todayIn
import kotlinx.datetime.LocalTime as KLocalTime
import kotlinx.datetime.TimeZone as KTimeZone
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toLocalDateTime

data class SalahUiState(
    val hijriDate: String = "14 Jumada al-Ula 1448 AH",
    val gregorianDate: String = "Tuesday · 11 November 2026",
    val prayerTimes: Map<PrayerType, KLocalTime> = emptyMap(),
    val currentPrayer: PrayerType = PrayerType.DHUHR,
    val nextPrayer: PrayerType = PrayerType.ASR,
    val countdownText: String = "01h 24m 12s",
    val elapsedWindowRatio: Float = 0.65f,
    val prayersList: List<PrayerModel> = emptyList(),
    val qadhaDeficits: Map<PrayerType, Int> = emptyMap(),
    val isTraveler: Boolean = false,
    val calculationMethod: CalculationMethod = CalculationMethod.UMM_AL_QURA,
    val madhhab: Madhhab = Madhhab.SHAFI_HANBALI_MALIKI,
    val adjustments: PrayerAdjustments = PrayerAdjustments(),
    val locationName: String = "Detecting Location...",
    val isLocationPermissionGranted: Boolean = false,
    val showLocationWarningBanner: Boolean = false,
    val userCoordinates: Pair<Double, Double> = Pair(21.4225, 39.8262) // Default to Makkah
)

sealed class SalahUiIntent {
    data class ToggleSalahCompletion(val prayerType: PrayerType, val isJamaah: Boolean) : SalahUiIntent()
    data class ToggleNaflCompletion(val prayerType: PrayerType, val nafl: NaflType) : SalahUiIntent()
    data class IncrementQadha(val prayerType: PrayerType) : SalahUiIntent()
    data class DecrementQadha(val prayerType: PrayerType) : SalahUiIntent()
    data class ChangeCalculationMethod(val method: CalculationMethod) : SalahUiIntent()
    data class ChangeMadhhab(val madhhab: Madhhab) : SalahUiIntent()
    data class UpdatePrayerAdjustments(val adjustments: PrayerAdjustments) : SalahUiIntent()
    data class LocationPermissionResult(val isGranted: Boolean) : SalahUiIntent()
    data object RefreshLocation : SalahUiIntent()
    data class SetManualLocation(val lat: Double, val lon: Double, val name: String) : SalahUiIntent()
}

class SalahViewModel(
    private val salahDao: SalahDao,
    private val qadhaDao: QadhaDao,
    private val astronomyEngine: PrayerCalculationEngine,
    private val travelManager: TravelStatusManager,
    private val locationProvider: UserLocationProvider? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalahUiState())
    val uiState: StateFlow<SalahUiState> = _uiState.asStateFlow()

    init {
        checkAndRefreshLocation()
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
            is SalahUiIntent.UpdatePrayerAdjustments -> {
                _uiState.update { it.copy(adjustments = intent.adjustments) }
                loadDailySchedule()
            }
            is SalahUiIntent.LocationPermissionResult -> {
                if (intent.isGranted) {
                    _uiState.update { it.copy(isLocationPermissionGranted = true, showLocationWarningBanner = false) }
                    checkAndRefreshLocation()
                } else {
                    _uiState.update { it.copy(isLocationPermissionGranted = false, showLocationWarningBanner = true) }
                    loadDailySchedule()
                }
            }
            is SalahUiIntent.RefreshLocation -> {
                checkAndRefreshLocation()
            }
            is SalahUiIntent.SetManualLocation -> {
                _uiState.update {
                    it.copy(
                        userCoordinates = Pair(intent.lat, intent.lon),
                        locationName = intent.name,
                        showLocationWarningBanner = false
                    )
                }
                loadDailySchedule()
            }
        }
    }

    private fun checkAndRefreshLocation() {
        if (locationProvider == null) {
            loadDailySchedule()
            return
        }

        val hasPermission = locationProvider.hasLocationPermission()
        _uiState.update {
            it.copy(
                isLocationPermissionGranted = hasPermission,
                showLocationWarningBanner = !hasPermission
            )
        }

        viewModelScope.launch {
            val resolved = locationProvider.getFreshOrCachedLocation()
            _uiState.update {
                it.copy(
                    userCoordinates = Pair(resolved.latitude, resolved.longitude),
                    locationName = if (resolved.countryName.isNotBlank()) "${resolved.cityName}, ${resolved.countryName}" else resolved.cityName,
                    showLocationWarningBanner = !resolved.isFromGps && !hasPermission
                )
            }
            loadDailySchedule()
        }
    }

    private fun loadDailySchedule() {
        val today = Clock.System.todayIn(KTimeZone.currentSystemDefault())
        val userZone = KTimeZone.currentSystemDefault()
        val coords = _uiState.value.userCoordinates

        val times = astronomyEngine.getPrayerTimes(
            coordinates = coords,
            date = today,
            method = _uiState.value.calculationMethod,
            madhhab = _uiState.value.madhhab,
            timeZone = userZone,
            adjustments = _uiState.value.adjustments
        )

        val countdown: PrayerCountdownInfo = astronomyEngine.calculateCountdown(times, Clock.System.now().toLocalDateTime(userZone).time)
        val travel = travelManager.evaluateTravelState(coords)

        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

        val prayerModels = listOf(
            PrayerModel("fajr", "Fajr", "الفجر", times[PrayerType.FAJR]?.toJavaLocalTime()?.format(timeFormatter) ?: "05:14 AM", travel.shortenedRakats[PrayerType.FAJR] ?: 2, 2, 0),
            PrayerModel("sunrise", "Sunrise", "الشروق", times[PrayerType.SUNRISE]?.toJavaLocalTime()?.format(timeFormatter) ?: "06:38 AM", 0, 0, 0),
            PrayerModel("dhuhr", "Dhuhr", "الظهر", times[PrayerType.DHUHR]?.toJavaLocalTime()?.format(timeFormatter) ?: "12:45 PM", travel.shortenedRakats[PrayerType.DHUHR] ?: 4, 4, 2, nawafilName = "2 Rakat Nafl"),
            PrayerModel("asr", "Asr", "العصر", times[PrayerType.ASR]?.toJavaLocalTime()?.format(timeFormatter) ?: "03:52 PM", travel.shortenedRakats[PrayerType.ASR] ?: 4, 4, 0),
            PrayerModel("maghrib", "Maghrib", "المغرب", times[PrayerType.MAGHRIB]?.toJavaLocalTime()?.format(timeFormatter) ?: "06:18 PM", travel.shortenedRakats[PrayerType.MAGHRIB] ?: 3, 0, 2, nawafilName = "Awwabin (6 Rakat)"),
            PrayerModel("isha", "Isha", "العشاء", times[PrayerType.ISHA]?.toJavaLocalTime()?.format(timeFormatter) ?: "07:44 PM", travel.shortenedRakats[PrayerType.ISHA] ?: 4, 4, 2, nawafilName = "Tahajjud & Witr")
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
