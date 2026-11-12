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
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.prayer.SunniMethodMapper
import com.sakinah.deen.data.repository.PrayerTimeRepository
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngine
import com.sakinah.deen.domain.astronomy.PrayerCountdownInfo
import com.sakinah.deen.domain.astronomy.UserLocationProvider
import com.sakinah.deen.domain.travel.HomeAnchorStore
import com.sakinah.deen.domain.travel.SafarPolicy
import com.sakinah.deen.domain.travel.TravelStatusManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
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
import kotlinx.datetime.toJavaLocalDate
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
    val calculationMethod: CalculationMethod = CalculationMethod.UMM_AL_QURA,
    val madhhab: Madhhab = Madhhab.SHAFI_HANBALI_MALIKI,
    val adjustments: PrayerAdjustments = PrayerAdjustments(),
    val locationName: String = "Detecting Location...",
    val isLocationPermissionGranted: Boolean = false,
    val showLocationWarningBanner: Boolean = false,
    val userCoordinates: Pair<Double, Double> = Pair(21.4225, 39.8262), // Default to Makkah
    /** Concise provenance of the timings, e.g. "Qatar Awqaf (Online)" / "Offline Calculation". */
    val sourceLabel: String = "Offline Calculation",
    /** Short name of the authority the timings follow (country-mapped or manual). */
    val authorityName: String = "Muslim World League",
    /** True when no method was picked by hand, so the country's authority is followed. */
    val isAutomaticMethod: Boolean = true,
    /** Whether Qasr (shortened) rakats are currently active. */
    val isTraveler: Boolean = false,
    /** Great-circle distance from the saved Home location, in kilometres. */
    val distanceFromHomeKm: Double = 0.0,
    /** Whether a Home location has been saved (drives the "Set Home Location" prompt). */
    val hasHomeLocation: Boolean = false
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
    data class SetManualLocation(
        val lat: Double,
        val lon: Double,
        val name: String,
        val countryCode: String? = null
    ) : SalahUiIntent()

    /** Clears a manual calculation-method choice and follows the country's authority again. */
    data object UseAutomaticMethod : SalahUiIntent()

    /** Saves the current GPS location as the user's Home location. */
    data object SetHomeLocationFromCurrent : SalahUiIntent()
}

class SalahViewModel(
    private val salahDao: SalahDao,
    private val qadhaDao: QadhaDao,
    private val astronomyEngine: PrayerCalculationEngine,
    private val travelManager: TravelStatusManager,
    private val locationProvider: UserLocationProvider? = null,
    private val prayerTimes: PrayerTimeRepository? = null,
    private val preferences: SakinahPreferences? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalahUiState())
    val uiState: StateFlow<SalahUiState> = _uiState.asStateFlow()

    /** ISO code of the last resolved location, driving the country → authority mapping. */
    private var countryCode: String? = null

    /** Guards against a slow online resolution overwriting a newer one. */
    private var scheduleRequest = 0

    init {
        // Restore the saved Home location so Qasr only activates for real travel.
        preferences?.homeCoordinates?.let { travelManager.setHomeAnchor(it) }
        syncAuthority()
        checkAndRefreshLocation()
        observeQadhaLedger()
        observeHomeAnchorChanges()
        startCountdownTicker()
    }

    /**
     * The dashboard ViewModel outlives the Settings/Travel screens, so a Home saved
     * (or cleared) elsewhere has to reach it without a restart.
     */
    private fun observeHomeAnchorChanges() {
        viewModelScope.launch {
            // Skip the initial `null`; `init` has already restored the saved anchor.
            HomeAnchorStore.state.drop(1).collect { home ->
                val anchor = SafarPolicy.sanitize(home)
                if (anchor == null) travelManager.clearHomeAnchor() else travelManager.setHomeAnchor(anchor)
                _uiState.update {
                    it.copy(
                        hasHomeLocation = travelManager.hasHomeAnchor,
                        isTraveler = false,
                        distanceFromHomeKm = 0.0
                    )
                }
                loadDailySchedule()
            }
        }
    }

    fun onIntent(intent: SalahUiIntent) {
        when (intent) {
            is SalahUiIntent.ToggleSalahCompletion -> toggleSalahCompletion(intent.prayerType, intent.isJamaah)
            is SalahUiIntent.ToggleNaflCompletion -> toggleNaflCompletion(intent.prayerType, intent.nafl)
            is SalahUiIntent.IncrementQadha -> incrementQadha(intent.prayerType)
            is SalahUiIntent.DecrementQadha -> decrementQadha(intent.prayerType)
            is SalahUiIntent.ChangeCalculationMethod -> {
                // A manual choice is persisted so it also wins for background alarm scheduling.
                prayerTimes?.updateOverride(intent.method)
                _uiState.update {
                    it.copy(
                        calculationMethod = intent.method,
                        authorityName = SunniMethodMapper.fromOverride(intent.method).displayName,
                        isAutomaticMethod = false
                    )
                }
                loadDailySchedule()
            }
            is SalahUiIntent.UseAutomaticMethod -> {
                prayerTimes?.updateOverride(null)
                syncAuthority()
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
            is SalahUiIntent.SetHomeLocationFromCurrent -> {
                // An unresolved fix (0.0, 0.0) must never become the Home anchor.
                val coords = SafarPolicy.sanitize(_uiState.value.userCoordinates)
                if (coords == null) {
                    loadDailySchedule()
                } else {
                    travelManager.setHomeAnchor(coords)
                    preferences?.saveHomeAnchor(coords.first, coords.second, _uiState.value.locationName)
                    _uiState.update {
                        it.copy(
                            hasHomeLocation = true,
                            isTraveler = false,
                            distanceFromHomeKm = 0.0
                        )
                    }
                    loadDailySchedule()
                }
            }
            is SalahUiIntent.SetManualLocation -> {
                countryCode = intent.countryCode?.takeIf { it.isNotBlank() } ?: countryCode
                _uiState.update {
                    it.copy(
                        userCoordinates = Pair(intent.lat, intent.lon),
                        locationName = intent.name,
                        showLocationWarningBanner = false
                    )
                }
                syncAuthority()
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
            countryCode = resolved.countryCode.takeIf { it.isNotBlank() } ?: countryCode
            _uiState.update {
                it.copy(
                    userCoordinates = Pair(resolved.latitude, resolved.longitude),
                    locationName = if (resolved.countryName.isNotBlank()) "${resolved.cityName}, ${resolved.countryName}" else resolved.cityName,
                    showLocationWarningBanner = !resolved.isFromGps && !hasPermission,
                    hasHomeLocation = travelManager.hasHomeAnchor
                )
            }
            syncAuthority()
            loadDailySchedule()
        }
    }

    /**
     * Applies the effective Sunni authority: a manual settings choice if one exists, otherwise
     * the authority of the detected country. The local engine uses the matching twilight
     * angles so the offline fallback stays consistent with the online timetable.
     */
    private fun syncAuthority() {
        val authority = prayerTimes?.effectiveAuthority(countryCode)
            ?: SunniMethodMapper.resolve(countryCode, null)
        val override = prayerTimes?.currentOverride()
        _uiState.update {
            it.copy(
                calculationMethod = SunniMethodMapper.localCalculationMethod(authority),
                authorityName = authority.displayName,
                isAutomaticMethod = override == null
            )
        }
    }

    /** Live 1-second countdown to the next prayer, so the UI ticks down without recomputation. */
    private var countdownJob: Job? = null

    private fun startCountdownTicker() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive) {
                val times = _uiState.value.prayerTimes
                if (times.isNotEmpty()) {
                    val now = Clock.System.now().toLocalDateTime(KTimeZone.currentSystemDefault())
                    val countdown = astronomyEngine.calculateCountdown(times, now.time)
                    _uiState.update {
                        it.copy(
                            currentPrayer = countdown.currentPrayer,
                            nextPrayer = countdown.nextPrayer,
                            countdownText = "${countdown.remainingMinutes}m ${countdown.remainingSeconds}s",
                            elapsedWindowRatio = countdown.elapsedRatio
                        )
                    }
                }
                delay(1000L)
            }
        }
    }

    private fun loadDailySchedule() {
        val today = Clock.System.todayIn(KTimeZone.currentSystemDefault())
        val state = _uiState.value
        val request = ++scheduleRequest

        // Always render an instant answer first: the on-device astronomical calculation.
        renderTimes(
            times = astronomyEngine.getPrayerTimes(
                coordinates = state.userCoordinates,
                date = today,
                method = state.calculationMethod,
                madhhab = state.madhhab,
                timeZone = KTimeZone.currentSystemDefault(),
                adjustments = state.adjustments
            ),
            sourceLabel = "Offline Calculation"
        )

        val repository = prayerTimes ?: return
        viewModelScope.launch {
            // Cache first (instant, offline), then AlAdhan, then local calculation internally.
            val resolved = repository.resolveDay(
                date = today.toJavaLocalDate(),
                coordinates = state.userCoordinates,
                madhhab = state.madhhab,
                zoneId = ZoneId.systemDefault(),
                adjustments = state.adjustments,
                countryCode = countryCode
            )
            if (request != scheduleRequest) return@launch

            if (resolved.times.isEmpty()) {
                _uiState.update { it.copy(sourceLabel = resolved.label) }
                return@launch
            }
            renderTimes(
                times = resolved.times.mapValues { (_, time) ->
                    KLocalTime(time.hour, time.minute, time.second)
                },
                sourceLabel = resolved.label,
                authorityName = resolved.authority.displayName
            )
        }
    }

    private fun renderTimes(
        times: Map<PrayerType, KLocalTime>,
        sourceLabel: String,
        authorityName: String? = null
    ) {
        val userZone = KTimeZone.currentSystemDefault()
        val coords = _uiState.value.userCoordinates

        val countdown: PrayerCountdownInfo = astronomyEngine.calculateCountdown(times, Clock.System.now().toLocalDateTime(userZone).time)
        val travel = travelManager.evaluateTravelState(coords)

        // A manual Travel Mode override only survives while the user is away from Home.
        // Back within 80 km of a saved Home it is cleared, so four rakat return immediately.
        val manualTravelMode = preferences?.travelModeActive == true
        val clearManualTravelMode = manualTravelMode &&
            SafarPolicy.shouldClearManualTravelMode(travelManager.getHomeAnchor(), coords)
        if (clearManualTravelMode) preferences?.travelModeActive = false
        val isSafar = travel.isTraveler || (manualTravelMode && !clearManualTravelMode)
        val rakats = SafarPolicy.rakats(isSafar)

        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

        val prayerModels = listOf(
            PrayerModel("fajr", "Fajr", "الفجر", times[PrayerType.FAJR]?.toJavaLocalTime()?.format(timeFormatter) ?: "05:14 AM", rakats[PrayerType.FAJR] ?: 2, 2, 0),
            PrayerModel("sunrise", "Sunrise", "الشروق", times[PrayerType.SUNRISE]?.toJavaLocalTime()?.format(timeFormatter) ?: "06:38 AM", 0, 0, 0),
            PrayerModel("dhuhr", "Dhuhr", "الظهر", times[PrayerType.DHUHR]?.toJavaLocalTime()?.format(timeFormatter) ?: "12:45 PM", rakats[PrayerType.DHUHR] ?: 4, 4, 2, nawafilName = "2 Rakat Nafl"),
            PrayerModel("asr", "Asr", "العصر", times[PrayerType.ASR]?.toJavaLocalTime()?.format(timeFormatter) ?: "03:52 PM", rakats[PrayerType.ASR] ?: 4, 4, 0),
            PrayerModel("maghrib", "Maghrib", "المغرب", times[PrayerType.MAGHRIB]?.toJavaLocalTime()?.format(timeFormatter) ?: "06:18 PM", rakats[PrayerType.MAGHRIB] ?: 3, 0, 2, nawafilName = "Awwabin (6 Rakat)"),
            PrayerModel("isha", "Isha", "العشاء", times[PrayerType.ISHA]?.toJavaLocalTime()?.format(timeFormatter) ?: "07:44 PM", rakats[PrayerType.ISHA] ?: 4, 4, 2, nawafilName = "Tahajjud & Witr")
        )

        _uiState.update {
            it.copy(
                prayerTimes = times,
                currentPrayer = countdown.currentPrayer,
                nextPrayer = countdown.nextPrayer,
                countdownText = "${countdown.remainingMinutes}m ${countdown.remainingSeconds}s",
                elapsedWindowRatio = countdown.elapsedRatio,
                prayersList = prayerModels,
                isTraveler = isSafar,
                distanceFromHomeKm = travel.distanceKm,
                hasHomeLocation = travelManager.hasHomeAnchor,
                sourceLabel = sourceLabel,
                authorityName = authorityName ?: it.authorityName
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
            if (e is kotlinx.coroutines.CancellationException) throw e
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
