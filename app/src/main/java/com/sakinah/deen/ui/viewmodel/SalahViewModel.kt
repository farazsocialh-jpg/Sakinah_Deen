package com.sakinah.deen.ui.viewmodel

import androidx.lifecycle.ViewModel
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
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
import com.sakinah.deen.data.prayer.arabicName
import com.sakinah.deen.data.prayer.displayArabicName
import com.sakinah.deen.data.prayer.displayEnglishName
import com.sakinah.deen.data.prayer.englishName
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.prayer.SunniMethodMapper
import com.sakinah.deen.data.repository.PrayerTimeRepository
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngine
import com.sakinah.deen.domain.astronomy.SalahRefreshPolicy
import com.sakinah.deen.domain.astronomy.SalahRefreshReason
import com.sakinah.deen.domain.astronomy.PrayerCountdownInfo
import com.sakinah.deen.domain.astronomy.UserLocationProvider
import com.sakinah.deen.domain.travel.HomeAnchorStore
import com.sakinah.deen.domain.travel.SafarPolicy
import com.sakinah.deen.domain.travel.TravelStatusManager
import com.sakinah.deen.service.PrayerAlarmScheduler
import com.sakinah.deen.settings.AsrMethod
import com.sakinah.deen.settings.HighLatitudeRule
import com.sakinah.deen.settings.SettingsRepository
import com.sakinah.deen.util.TimeZoneResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate as KLocalDate
import kotlinx.datetime.LocalTime as KLocalTime
import kotlinx.datetime.TimeZone as KTimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toLocalDateTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Live sky summary from Open-Meteo, with an offline-neutral fallback. */
enum class SkyCondition(val label: String) {
    CLEAR("Sunny"),
    SCATTERED_CLOUDS("Scattered clouds"),
    CLOUDY("Cloudy"),
    RAINY("Rainy"),
    FOG("Fog"),
    NIGHT("Clear night")
}

data class SalahUiState(
    val hijriDate: String = "14 Jumada al-Ula 1448 AH",
    val gregorianDate: String = "",
    val prayerTimes: Map<PrayerType, KLocalTime> = emptyMap(),
    val currentPrayer: PrayerType = PrayerType.DHUHR,
    val nextPrayer: PrayerType = PrayerType.ASR,
    val countdownText: String = "00m 00s",
    val elapsedWindowRatio: Float = 0.0f,
    val currentPrayerName: String = "Dhuhr",
    val currentPrayerArabic: String = "الظهر",
    val isCurrentPrayerCompleted: Boolean = false,
    val dailyCompletionByPrayer: Map<String, Boolean> = emptyMap(),
    val nextPrayerName: String = "Asr",
    val nextPrayerStartTimeFormatted: String = "",
    val remainingMillis: Long = 0L,
    val totalWindowMillis: Long = 1L,
    val prayersList: List<PrayerModel> = emptyList(),
    val qadhaDeficits: Map<PrayerType, Int> = emptyMap(),
    val calculationMethod: CalculationMethod = CalculationMethod.UMM_AL_QURA,
    val madhhab: Madhhab = Madhhab.SHAFI_HANBALI_MALIKI,
    val adjustments: PrayerAdjustments = PrayerAdjustments(),
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.MIDDLE_OF_NIGHT,
    val locationName: String = "Detecting Location...",
    val isLocationPermissionGranted: Boolean = false,
    val showLocationWarningBanner: Boolean = false,
    val showExactAlarmWarningBanner: Boolean = false,
    val userCoordinates: Pair<Double, Double> = Pair(21.4225, 39.8262),
    val timeZoneId: String = ZoneId.systemDefault().id,
    val timeZoneAbbr: String = TimeZoneResolver.shortAbbreviation(null),
    val isManualLocation: Boolean = false,
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
    val hasHomeLocation: Boolean = false,
    /** Drives the pull-to-refresh indicator; cleared when a refresh attempt finishes. */
    val isRefreshing: Boolean = false,
    /**
     * True until a saved or freshly resolved location exists. Prevents showing Makkah's
     * solar times against the device timezone on a cold start.
     */
    val isLocationPending: Boolean = false,
    /** Start of the last third of the night (Tahajjud); empty when not computed yet. */
    val tahajjudStartTimeFormatted: String = "",
    /** Tahajjud lasts until the next Fajr. */
    val tahajjudEndTimeFormatted: String = "",
    /** Length of the Tahajjud window, e.g. "2h 45m". */
    val tahajjudDurationText: String = "",
    /** True during the Duha/Ishraq window (sunrise+15m .. Dhuhr): hide the top NEXT pill. */
    val isDuhaWindow: Boolean = false,
    /** True during Tahajjud (last third of night .. Fajr). */
    val isTahajjudWindow: Boolean = false,
    /** Optional override for the hero title (e.g. "Next: Dhuhr · 11:19 am"). */
    val heroTitleOverride: String? = null,
    val skyCondition: SkyCondition? = null,
    val temperatureCelsius: Double? = null,
    val isDaylight: Boolean = true,
    /** Start of Tahajjud window in seconds-of-day (-1 when unknown). */
    val tahajjudStartSecondOfDay: Int = -1,
    /** How old the saved/current fix is, in minutes, when a timestamp exists. */
    val locationAgeMinutes: Long = 0L,
    /** True when the saved fix is too old to read as a current position. */
    val isLocationStale: Boolean = false,
    /** Qadha rows skipped because their stored prayer type was corrupted. */
    val invalidQadhaRecords: Int = 0,
    /** Jam is an explicit choice, never implied by crossing the travel threshold. */
    val isJamPermitted: Boolean = false,
    /** Short user-facing explanation of the current Safar ruling. */
    val safarExplanation: String = ""
)

sealed class SalahUiIntent {
    data class ToggleSalahCompletion(val prayerType: PrayerType, val isJamaah: Boolean) : SalahUiIntent()
    data object ToggleCurrentPrayerCompleted : SalahUiIntent()
    data class ToggleNaflCompletion(val prayerType: PrayerType, val nafl: NaflType) : SalahUiIntent()
    data class IncrementQadha(val prayerType: PrayerType) : SalahUiIntent()
    data class DecrementQadha(val prayerType: PrayerType) : SalahUiIntent()
    data class ChangeCalculationMethod(val method: CalculationMethod) : SalahUiIntent()
    data class ChangeMadhhab(val madhhab: Madhhab) : SalahUiIntent()
    data class UpdatePrayerAdjustments(val adjustments: PrayerAdjustments) : SalahUiIntent()
    data class LocationPermissionResult(val isGranted: Boolean) : SalahUiIntent()
    data object RefreshLocation : SalahUiIntent()
    data object RecalculateToday : SalahUiIntent()
    data object SystemStateChanged : SalahUiIntent()
    data class ChangeHighLatitudeRule(val rule: com.sakinah.deen.settings.HighLatitudeRule) : SalahUiIntent()
    data object UseGpsLocation : SalahUiIntent()
    data class SetManualLocation(
        val lat: Double,
        val lon: Double,
        val name: String,
        val countryCode: String? = null,
        val timeZoneId: String? = null
    ) : SalahUiIntent()

    /** Clears a manual calculation-method choice and follows the country's authority again. */
    data object UseAutomaticMethod : SalahUiIntent()

    /** Saves the current location as the user's Home location. */
    data object SetHomeLocationFromCurrent : SalahUiIntent()
}

class SalahViewModel(
    private val salahDao: SalahDao,
    private val qadhaDao: QadhaDao,
    private val astronomyEngine: PrayerCalculationEngine,
    private val travelManager: TravelStatusManager,
    private val locationProvider: UserLocationProvider? = null,
    private val prayerTimes: PrayerTimeRepository? = null,
    private val preferences: SakinahPreferences? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val alarmScheduler: PrayerAlarmScheduler? = null,
    private val httpClient: HttpClient? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalahUiState())
    val uiState: StateFlow<SalahUiState> = _uiState.asStateFlow()

    /** ISO code of the last resolved location, driving the country → authority mapping. */
    private var countryCode: String? = preferences?.locationCountryCode

    /** Guards against a slow online resolution overwriting a newer one. */
    private var scheduleRequest = 0

    /** True once the saved location or a GPS/network fix gives real coordinates. */
    private var hasUsableCoordinates = preferences?.locationCoordinates != null

    /** For [SalahRefreshPolicy]: permission/zone state captured when backgrounded. */
    private var lastKnownPermission: Boolean = false
    private var lastKnownSystemZone: String? = ZoneId.systemDefault().id

    private var salahObserverJob: Job? = null
    private var observedEpochDay: Long? = null
    private var completedRecordsByPrayer: Map<PrayerType, SalahRecordEntity> = emptyMap()
    /** Nafl completion for the current date, persisted by date+prayer+nafl in preferences. */
    private var completedNawafilPrayers: Set<PrayerType> = emptySet()

    init {
        preferences?.homeCoordinates?.let { travelManager.setHomeAnchor(it) }
        val initialMadhhab = settingsRepository?.settings?.value?.prayer?.asrMethod?.let(::fromSettingsAsr)
            ?: preferences?.madhhab
            ?: Madhhab.SHAFI_HANBALI_MALIKI

        val savedCoords = preferences?.locationCoordinates
        val savedManual = preferences?.isManualLocation == true && savedCoords != null
        val savedZone = if (savedManual) {
            TimeZoneResolver.resolveZoneIdString(
                explicitZoneId = preferences.locationTimeZoneId,
                cityLabel = preferences.locationLabel,
                countryCode = preferences.locationCountryCode,
                longitude = savedCoords.second
            )
        } else {
            preferences?.locationTimeZoneId ?: ZoneId.systemDefault().id
        }

        _uiState.update {
            it.copy(
                madhhab = initialMadhhab,
                adjustments = preferences?.prayerAdjustments ?: PrayerAdjustments(),
                highLatitudeRule = settingsRepository?.settings?.value?.prayer?.highLatitudeRule
                    ?: preferences?.highLatitudeRule ?: HighLatitudeRule.MIDDLE_OF_NIGHT,
                hasHomeLocation = travelManager.hasHomeAnchor,
                isManualLocation = savedManual,
                locationAgeMinutes = preferences?.locationAgeMillis()?.let { age -> age / 60_000L } ?: 0L,
                isLocationStale = preferences?.isLocationStale() == true,
                userCoordinates = savedCoords ?: it.userCoordinates,
                locationName = if (savedCoords != null) preferences.locationLabel else it.locationName,
                timeZoneId = savedZone,
                timeZoneAbbr = TimeZoneResolver.shortAbbreviation(savedZone),
                isLocationPending = !hasUsableCoordinates
            )
        }

        lastKnownPermission = locationProvider?.hasLocationPermission() ?: (preferences?.locationCoordinates != null)
        syncAuthority()
        syncExactAlarmWarning()
        observeSharedSettings()
        if (savedManual) {
            updateDateLabels(savedZone)
            observeTodaySalahRecords(currentLocationLocalDate(savedZone).toEpochDay())
            loadDailySchedule()
        } else {
            checkAndRefreshLocation()
        }
        observeQadhaLedger()
        observeHomeAnchorChanges()
        startCountdownTicker()
    }

    private fun observeSharedSettings() {
        val repo = settingsRepository ?: return
        viewModelScope.launch {
            repo.settings.drop(1).collect { settings ->
                var needsReload = false
                val targetMadhhab = fromSettingsAsr(settings.prayer.asrMethod)
                if (_uiState.value.madhhab != targetMadhhab) {
                    preferences?.madhhab = targetMadhhab
                    _uiState.update { it.copy(madhhab = targetMadhhab) }
                    needsReload = true
                }

                val targetRule = settings.prayer.highLatitudeRule
                if (_uiState.value.highLatitudeRule != targetRule) {
                    preferences?.highLatitudeRule = targetRule
                    _uiState.update { it.copy(highLatitudeRule = targetRule) }
                    needsReload = true
                }

                val manualOverride = preferences?.calculationMethodOverride
                if (manualOverride == null) {
                    if (!_uiState.value.isAutomaticMethod) {
                        _uiState.update { it.copy(isAutomaticMethod = true) }
                        needsReload = true
                    }
                } else {
                    val targetMethod = SunniMethodMapper.fromSharedCalculation(settings.prayer.calculationMethod)
                    if (_uiState.value.calculationMethod != targetMethod || _uiState.value.isAutomaticMethod) {
                        prayerTimes?.updateOverride(targetMethod)
                        _uiState.update {
                            it.copy(
                                calculationMethod = targetMethod,
                                authorityName = SunniMethodMapper.fromOverride(targetMethod).displayName,
                                isAutomaticMethod = false
                            )
                        }
                        needsReload = true
                    }
                }

                if (needsReload) loadDailySchedule() else updateDateLabels(_uiState.value.timeZoneId)
            }
        }
    }

    private fun observeHomeAnchorChanges() {
        viewModelScope.launch {
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
            is SalahUiIntent.ToggleCurrentPrayerCompleted -> toggleCurrentPrayerCompleted()
            is SalahUiIntent.ToggleNaflCompletion -> toggleNaflCompletion(intent.prayerType, intent.nafl)
            is SalahUiIntent.IncrementQadha -> incrementQadha(intent.prayerType)
            is SalahUiIntent.DecrementQadha -> decrementQadha(intent.prayerType)
            is SalahUiIntent.ChangeCalculationMethod -> {
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
                preferences?.madhhab = intent.madhhab
                settingsRepository?.let { repo ->
                    viewModelScope.launch {
                        repo.updatePrayer(
                            repo.settings.value.prayer.copy(asrMethod = toSettingsAsr(intent.madhhab))
                        )
                    }
                }
                _uiState.update { it.copy(madhhab = intent.madhhab) }
                loadDailySchedule()
            }
            is SalahUiIntent.UpdatePrayerAdjustments -> {
                preferences?.prayerAdjustments = intent.adjustments
                _uiState.update { it.copy(adjustments = intent.adjustments) }
                loadDailySchedule()
            }
            is SalahUiIntent.LocationPermissionResult -> {
                if (intent.isGranted) {
                    _uiState.update { it.copy(isLocationPermissionGranted = true, showLocationWarningBanner = false) }
                    if (!_uiState.value.isManualLocation) {
                        checkAndRefreshLocation()
                    } else {
                        loadDailySchedule()
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLocationPermissionGranted = false,
                            showLocationWarningBanner = !_uiState.value.isManualLocation
                        )
                    }
                    loadDailySchedule()
                }
            }
            is SalahUiIntent.RefreshLocation -> {
                // The indicator is owned here so a failed or empty refresh cannot leave it
                // spinning forever in the UI.
                _uiState.update { it.copy(isRefreshing = true) }
                if (_uiState.value.isManualLocation) {
                    loadDailySchedule()
                } else {
                    checkAndRefreshLocation()
                }
            }
            is SalahUiIntent.RecalculateToday -> {
                _uiState.update { it.copy(isRefreshing = true) }
                loadDailySchedule()
            }
            is SalahUiIntent.SystemStateChanged -> {
                // Offsets are edited on the Settings tab, which writes straight to prefs: pick
                // them up on resume so the two screens can never disagree.
                val persistedAdjustments = preferences?.prayerAdjustments
                if (persistedAdjustments != null && persistedAdjustments != _uiState.value.adjustments) {
                    _uiState.update { it.copy(adjustments = persistedAdjustments) }
                    loadDailySchedule()
                    return
                }
                val hasPermission = locationProvider?.hasLocationPermission() ?: _uiState.value.isLocationPermissionGranted
                val currentZone = ZoneId.systemDefault().id
                val reason = SalahRefreshPolicy.decide(
                    hadPermission = lastKnownPermission,
                    hasPermission = hasPermission,
                    previousSystemZone = lastKnownSystemZone,
                    currentSystemZone = currentZone,
                    isManualLocation = _uiState.value.isManualLocation
                )
                lastKnownPermission = hasPermission
                lastKnownSystemZone = currentZone
                when (reason) {
                    SalahRefreshReason.PERMISSION_GRANTED -> {
                        _uiState.update { it.copy(isLocationPermissionGranted = true, showLocationWarningBanner = false) }
                        if (!_uiState.value.isManualLocation) checkAndRefreshLocation() else loadDailySchedule()
                    }
                    SalahRefreshReason.PERMISSION_REVOKED -> {
                        _uiState.update { it.copy(isLocationPermissionGranted = false, showLocationWarningBanner = !_uiState.value.isManualLocation) }
                        loadDailySchedule()
                    }
                    SalahRefreshReason.SYSTEM_ZONE_CHANGED -> {
                        if (!_uiState.value.isManualLocation) {
                            _uiState.update { it.copy(timeZoneId = currentZone, timeZoneAbbr = TimeZoneResolver.shortAbbreviation(currentZone)) }
                        }
                        loadDailySchedule()
                    }
                    SalahRefreshReason.NONE -> Unit
                }
                syncExactAlarmWarning()
            }
            is SalahUiIntent.ChangeHighLatitudeRule -> {
                preferences?.highLatitudeRule = intent.rule
                _uiState.update { it.copy(highLatitudeRule = intent.rule) }
                settingsRepository?.let { repo ->
                    viewModelScope.launch {
                        repo.updatePrayer(repo.settings.value.prayer.copy(highLatitudeRule = intent.rule))
                    }
                }
                loadDailySchedule()
            }
            is SalahUiIntent.UseGpsLocation -> {
                preferences?.isManualLocation = false
                _uiState.update { it.copy(isManualLocation = false) }
                checkAndRefreshLocation()
            }
            is SalahUiIntent.SetHomeLocationFromCurrent -> {
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
                val resolvedCountry = intent.countryCode?.takeIf { it.isNotBlank() } ?: countryCode
                countryCode = resolvedCountry
                val resolvedZone = TimeZoneResolver.resolveZoneIdString(
                    explicitZoneId = intent.timeZoneId,
                    cityLabel = intent.name,
                    countryCode = resolvedCountry,
                    longitude = intent.lon
                )
                val stored = preferences?.saveManualLocation(
                    latitude = intent.lat,
                    longitude = intent.lon,
                    label = intent.name,
                    countryCode = resolvedCountry,
                    timeZoneId = resolvedZone
                ) ?: true
                if (!stored) {
                    // Invalid city coordinates (e.g. a stalled picker) are not accepted.
                    _uiState.update { it.copy(isRefreshing = false) }
                    return
                }
                hasUsableCoordinates = true
                _uiState.update {
                    it.copy(
                        userCoordinates = Pair(intent.lat, intent.lon),
                        locationName = intent.name,
                        timeZoneId = resolvedZone,
                        timeZoneAbbr = TimeZoneResolver.shortAbbreviation(resolvedZone),
                        isManualLocation = true,
                        isLocationPending = false,
                        showLocationWarningBanner = false
                    )
                }
                updateDateLabels(resolvedZone)
                observeTodaySalahRecords(currentLocationLocalDate(resolvedZone).toEpochDay())
                syncAuthority()
                loadDailySchedule()
            }
        }
    }

    private fun checkAndRefreshLocation() {
        val zoneNow = _uiState.value.timeZoneId
        updateDateLabels(zoneNow)
        observeTodaySalahRecords(currentLocationLocalDate(zoneNow).toEpochDay())

        if (locationProvider == null) {
            hasUsableCoordinates = true
            _uiState.update { it.copy(isLocationPending = false) }
            loadDailySchedule()
            return
        }

        val hasPermission = locationProvider.hasLocationPermission()
        _uiState.update {
            it.copy(
                isLocationPermissionGranted = hasPermission,
                showLocationWarningBanner = !hasPermission && !it.isManualLocation
            )
        }

        viewModelScope.launch {
            val resolved = runCatching { locationProvider.getFreshOrCachedLocation() }.getOrNull()
            if (resolved == null) {
                // Resolution failed outright. Without any real coordinates, stay in the
                // pending state instead of rendering Makkah times in the device timezone.
                _uiState.update { it.copy(isRefreshing = false) }
                if (hasUsableCoordinates) loadDailySchedule()
                return@launch
            }
            // Guard against overwriting if user switched to manual city while GPS resolved.
            if (_uiState.value.isManualLocation) {
                loadDailySchedule()
                return@launch
            }
            countryCode = resolved.countryCode.takeIf { it.isNotBlank() } ?: countryCode
            val resolvedLabel = if (resolved.countryName.isNotBlank()) {
                "${resolved.cityName}, ${resolved.countryName}"
            } else {
                resolved.cityName
            }
            val resolvedZone = TimeZoneResolver.resolveZoneIdString(
                cityLabel = resolvedLabel,
                countryCode = countryCode,
                longitude = resolved.longitude
            )
            // A fix the validator rejects must not be persisted or rendered: better to keep
            // asking for a location than to show a timetable for a made-up position.
            val stored = preferences?.saveGpsLocation(
                latitude = resolved.latitude,
                longitude = resolved.longitude,
                label = resolvedLabel,
                countryCode = countryCode,
                timeZoneId = resolvedZone
            ) ?: true
            if (!stored) {
                _uiState.update { it.copy(isRefreshing = false) }
                if (hasUsableCoordinates) loadDailySchedule()
                return@launch
            }
            hasUsableCoordinates = true
            val ageMinutes = resolved.ageMillis()?.let { it / 60_000L } ?: 0L
            _uiState.update {
                it.copy(
                    userCoordinates = Pair(resolved.latitude, resolved.longitude),
                    isLocationPending = false,
                    locationName = resolvedLabel,
                    locationAgeMinutes = ageMinutes,
                    isLocationStale = resolved.isStale(),
                    timeZoneId = resolvedZone,
                    timeZoneAbbr = TimeZoneResolver.shortAbbreviation(resolvedZone),
                    isManualLocation = false,
                    showLocationWarningBanner = !resolved.isFromGps && !hasPermission,
                    hasHomeLocation = travelManager.hasHomeAnchor
                )
            }
            updateDateLabels(resolvedZone)
            observeTodaySalahRecords(currentLocationLocalDate(resolvedZone).toEpochDay())
            syncAuthority()
            loadDailySchedule()
        }
    }

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

    private var countdownJob: Job? = null

    private fun startCountdownTicker() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value
                val times = state.prayerTimes
                if (times.isNotEmpty()) {
                    val zone = locationKTimeZone(state.timeZoneId)
                    val now = Clock.System.now().toLocalDateTime(zone)
                    val isFriday = now.dayOfWeek == DayOfWeek.FRIDAY
                    val countdown = astronomyEngine.calculateCountdown(times, now.time)
                    val currentStart = times[countdown.currentPrayer]
                    val nextStart = times[countdown.nextPrayer]
                    val remainingMillis = (countdown.remainingMinutes * 60L + countdown.remainingSeconds) * 1000L
                    val totalWindowMillis = windowMillis(currentStart, nextStart)
                    // Evaluate each window once per tick; all labels below mirror the same flags.
                    val tahajjudStart = state.tahajjudStartSecondOfDay.let { second ->
                        if (second >= 0) KLocalTime(second / 3600, (second % 3600) / 60, second % 60) else null
                    }
                    val isTahajjudWindow = isTahajjudWindowNow(countdown, times, tahajjudStart)
                    val isDuhaWindow = countdown.currentPrayer == PrayerType.SUNRISE &&
                        now.time.toSecondOfDay() >= (times[PrayerType.SUNRISE]?.toSecondOfDay() ?: 0) + 15 * 60
                    _uiState.update {
                        it.copy(
                            currentPrayer = countdown.currentPrayer,
                            nextPrayer = countdown.nextPrayer,
                            countdownText = formatCountdown(countdown),
                            elapsedWindowRatio = countdown.elapsedRatio,
                            currentPrayerName = if (isDuhaWindow) "Next: ${PrayerType.DHUHR.displayEnglishName(isFriday)}" else if (isTahajjudWindow) "Tahajjud" else countdown.currentPrayer.displayEnglishName(isFriday),
                            currentPrayerArabic = if (isDuhaWindow) (times[countdown.nextPrayer]?.let { formatPrayerTime(it) } ?: "") else if (isTahajjudWindow) "التهجد" else countdown.currentPrayer.displayArabicName(isFriday),
                            isDuhaWindow = isDuhaWindow,
                            isTahajjudWindow = isTahajjudWindow,
                            heroTitleOverride = if (isDuhaWindow) "Next: ${PrayerType.DHUHR.displayEnglishName(isFriday)} · ${formatPrayerTime(times[PrayerType.DHUHR])}" else null,
                            isCurrentPrayerCompleted = completedRecordsByPrayer[countdown.currentPrayer]?.isCompleted == true,
                            dailyCompletionByPrayer = dailyCompletionMap(),
                            nextPrayerName = countdown.nextPrayer.displayEnglishName(isFriday),
                            nextPrayerStartTimeFormatted = formatPrayerTime(nextStart),
                            remainingMillis = remainingMillis,
                            totalWindowMillis = totalWindowMillis
                        )
                    }
                }
                delay(1000L)
            }
        }
    }

    private fun loadDailySchedule() {
        val state = _uiState.value
        val zoneId = TimeZoneResolver.resolveZoneId(state.timeZoneId)
        val kZone = locationKTimeZone(state.timeZoneId)
        val today = currentLocationLocalDate(state.timeZoneId)
        val kToday = KLocalDate(today.year, today.monthValue, today.dayOfMonth)
        val request = ++scheduleRequest

        updateDateLabels(state.timeZoneId)
        observeTodaySalahRecords(today.toEpochDay())
        refreshNaflForCurrentDate()

        renderTimes(
            times = astronomyEngine.getPrayerTimes(
                coordinates = state.userCoordinates,
                date = kToday,
                method = state.calculationMethod,
                madhhab = state.madhhab,
                timeZone = kZone,
                adjustments = state.adjustments,
                highLatitudeRule = state.highLatitudeRule
            ),
            sourceLabel = "Offline Calculation"
        )
        refreshWeather(state.userCoordinates.first, state.userCoordinates.second)

        // City, GPS, madhhab, calculation-method or offset changes all pass through here.
        // Re-arm immediately so background alarms never use stale times.
        alarmScheduler?.scheduleAllDailyAlarms(
            coordinates = state.userCoordinates,
            method = preferences?.calculationMethodOverride,
            madhhab = state.madhhab
        )
        syncExactAlarmWarning()

        val repository = prayerTimes ?: return
        viewModelScope.launch {
            val resolved = runCatching {
                repository.resolveDay(
                    date = today,
                    coordinates = state.userCoordinates,
                    madhhab = state.madhhab,
                    zoneId = zoneId,
                    adjustments = state.adjustments,
                    countryCode = countryCode,
                    highLatitudeRule = state.highLatitudeRule
                )
            }.getOrNull()
            if (request != scheduleRequest) return@launch

            if (resolved == null || resolved.times.isEmpty()) {
                // Offline or failed lookup: the local table already rendered, so finish here.
                _uiState.update {
                    it.copy(sourceLabel = resolved?.label ?: it.sourceLabel, isRefreshing = false)
                }
                return@launch
            }
            renderTimes(
                times = resolved.times.mapValues { (_, time) ->
                    KLocalTime(time.hour, time.minute, time.second)
                },
                sourceLabel = resolved.label,
                authorityName = resolved.authority.displayName
            )
            refreshWeather(state.userCoordinates.first, state.userCoordinates.second)
        }
    }

    private fun syncExactAlarmWarning() {
        val scheduler = alarmScheduler ?: return
        val alarmsActive = preferences?.alarmsEnabled != false
        val denied = alarmsActive && hasUsableCoordinates && !scheduler.canScheduleExactAlarms()
        if (_uiState.value.showExactAlarmWarningBanner != denied) {
            _uiState.update { it.copy(showExactAlarmWarningBanner = denied) }
        }
    }

    /** Free, keyless Open-Meteo current conditions; failures keep the last cached values. */
    private fun refreshWeather(latitude: Double, longitude: Double) {
        val client = httpClient ?: return
        viewModelScope.launch {
            runCatching {
                val response = client.get(
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=$latitude&longitude=$longitude" +
                        "&current=weather_code,is_day,temperature_2m"
                )
                if (!response.status.isSuccess()) return@launch
                val root = weatherJson.parseToJsonElement(response.bodyAsText()) as? JsonObject
                    ?: return@launch
                val current = root["current"] as? JsonObject ?: return@launch
                val code = (current["weather_code"] as? JsonPrimitive)?.intOrNull ?: 0
                val isDay = (current["is_day"] as? JsonPrimitive)?.intOrNull != 0
                val temp = (current["temperature_2m"] as? JsonPrimitive)?.doubleOrNull
                val condition = when {
                    !isDay -> SkyCondition.NIGHT
                    code == 0 || code == 1 -> SkyCondition.CLEAR
                    code == 2 -> SkyCondition.SCATTERED_CLOUDS
                    code == 3 -> SkyCondition.CLOUDY
                    code in 45..48 -> SkyCondition.FOG
                    code in 51..99 -> SkyCondition.RAINY
                    else -> SkyCondition.CLEAR
                }
                _uiState.update {
                    it.copy(
                        skyCondition = condition,
                        temperatureCelsius = temp,
                        isDaylight = isDay
                    )
                }
            }
        }
    }

    private fun renderTimes(
        times: Map<PrayerType, KLocalTime>,
        sourceLabel: String,
        authorityName: String? = null
    ) {
        val state = _uiState.value
        val userZone = locationKTimeZone(state.timeZoneId)
        val coords = state.userCoordinates

        val now = Clock.System.now().toLocalDateTime(userZone)
        val nowTime = now.time
        val isFriday = now.dayOfWeek == DayOfWeek.FRIDAY
        // Fajr ends at Shuruq: the engine reports Sunrise between sunrise and Dhuhr, which is
        // accurate — rewriting it to Fajr->Dhuhr claimed Fajr was still active after sunrise.
        val countdown: PrayerCountdownInfo = astronomyEngine.calculateCountdown(times, nowTime)
        val tahajjud = tahajjudWindow(times, state)
        val travel = travelManager.evaluateTravelState(coords)

        val manualTravelMode = preferences?.travelModeActive == true
        val clearManualTravelMode = manualTravelMode &&
            SafarPolicy.shouldClearManualTravelMode(travelManager.getHomeAnchor(), coords)
        if (clearManualTravelMode) preferences.travelModeActive = false
        val isSafar = travel.isTraveler || (manualTravelMode && !clearManualTravelMode)
        val rakats = SafarPolicy.rakats(isSafar)

        val prayerModels = buildPrayerModels(times, rakats, tahajjud?.second, isFriday)
        // Same window checks used by the ticker; computed once so labels cannot disagree.
        val isTahajjudWindow = isTahajjudWindowNow(countdown, times, tahajjud?.first)
        val isDuhaWindow = countdown.currentPrayer == PrayerType.SUNRISE &&
            nowTime.toSecondOfDay() >= (times[PrayerType.SUNRISE]?.toSecondOfDay() ?: 0) + 15 * 60

        _uiState.update {
            it.copy(
                prayerTimes = times,
                currentPrayer = countdown.currentPrayer,
                nextPrayer = countdown.nextPrayer,
                countdownText = formatCountdown(countdown),
                elapsedWindowRatio = countdown.elapsedRatio,
                currentPrayerName = if (isDuhaWindow) "Next: ${PrayerType.DHUHR.displayEnglishName(isFriday)}" else if (isTahajjudWindow) "Tahajjud" else countdown.currentPrayer.displayEnglishName(isFriday),
                currentPrayerArabic = if (isDuhaWindow) (times[countdown.nextPrayer]?.let { formatPrayerTime(it) } ?: "") else if (isTahajjudWindow) "التهجد" else countdown.currentPrayer.displayArabicName(isFriday),
                isDuhaWindow = isDuhaWindow,
                isTahajjudWindow = isTahajjudWindow,
                heroTitleOverride = if (isDuhaWindow) "Next: ${PrayerType.DHUHR.displayEnglishName(isFriday)} · ${formatPrayerTime(times[PrayerType.DHUHR])}" else null,
                isCurrentPrayerCompleted = completedRecordsByPrayer[countdown.currentPrayer]?.isCompleted == true,
                dailyCompletionByPrayer = dailyCompletionMap(),
                nextPrayerName = countdown.nextPrayer.displayEnglishName(isFriday),
                nextPrayerStartTimeFormatted = formatPrayerTime(times[countdown.nextPrayer]),
                remainingMillis = (countdown.remainingMinutes * 60L + countdown.remainingSeconds) * 1000L,
                totalWindowMillis = windowMillis(times[countdown.currentPrayer], times[countdown.nextPrayer]),
                prayersList = prayerModels,
                isTraveler = isSafar,
                distanceFromHomeKm = travel.distanceKm,
                hasHomeLocation = travelManager.hasHomeAnchor,
                isJamPermitted = travel.isJamPermitted,
                safarExplanation = travel.explanation,
                sourceLabel = sourceLabel,
                authorityName = authorityName ?: it.authorityName,
                timeZoneAbbr = TimeZoneResolver.shortAbbreviation(it.timeZoneId),
                isRefreshing = false,
                tahajjudStartTimeFormatted = formatPrayerTime(tahajjud?.first),
                tahajjudStartSecondOfDay = tahajjud?.first?.toSecondOfDay() ?: -1,
                tahajjudEndTimeFormatted = formatPrayerTime(tahajjud?.second),
                tahajjudDurationText = tahajjud?.let { (start, end) ->
                    formatNightDuration(start, end)
                }.orEmpty()
            )
        }
    }

    /**
     * Tahajjud window for today: last third of the night after Maghrib, ending at the next
     * day's Fajr. Returns null until both are known.
     */
    private fun tahajjudWindow(
        times: Map<PrayerType, KLocalTime>,
        state: SalahUiState
    ): Pair<KLocalTime, KLocalTime>? {
        val maghrib = times[PrayerType.MAGHRIB] ?: return null
        val nextFajr = runCatching {
            val tomorrow = currentLocationLocalDate(state.timeZoneId).plusDays(1)
            astronomyEngine.getPrayerTimes(
                coordinates = state.userCoordinates,
                date = KLocalDate.parse(tomorrow.toString()),
                method = state.calculationMethod,
                madhhab = state.madhhab,
                timeZone = locationKTimeZone(state.timeZoneId),
                adjustments = state.adjustments,
                highLatitudeRule = state.highLatitudeRule
            )[PrayerType.FAJR]
        }.getOrNull() ?: return null
        return astronomyEngine.lastThirdOfNight(maghrib, nextFajr) to nextFajr
    }

    /** "2h 45m" for the length of the night window between two local times. */
    private fun formatNightDuration(start: KLocalTime, end: KLocalTime): String {
        var seconds = end.toSecondOfDay().toLong() - start.toSecondOfDay().toLong()
        if (seconds <= 0L) seconds += SECONDS_PER_DAY
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun buildPrayerModels(
        times: Map<PrayerType, KLocalTime>,
        rakats: Map<PrayerType, Int>,
        nextFajr: KLocalTime? = null,
        isFriday: Boolean = false
    ): List<PrayerModel> {
        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")
        // Authentic end of each prayer's window (Sahih Muslim 612a / fiqh):
        // Fajr ends at Sunrise, Dhuhr at Asr, Asr at Maghrib, Maghrib at Isha, Isha at
        // Islamic midnight (Maghrib + 1/2 of the night). Isha's end needs the next day's Fajr.
        val endTimes = mutableMapOf<PrayerType, KLocalTime>()
        times[PrayerType.SUNRISE]?.let { sunrise ->
            endTimes[PrayerType.FAJR] = sunrise
            // Sunrise is a prohibited prayer window: sunrise .. sunrise + 15m.
            val plus15Secs = (sunrise.toSecondOfDay() + 15 * 60) % 86400
            endTimes[PrayerType.SUNRISE] = KLocalTime(
                plus15Secs / 3600,
                (plus15Secs % 3600) / 60,
                plus15Secs % 60
            )
        }
        times[PrayerType.ASR]?.let { endTimes[PrayerType.DHUHR] = it }
        times[PrayerType.MAGHRIB]?.let { endTimes[PrayerType.ASR] = it }
        times[PrayerType.ISHA]?.let { endTimes[PrayerType.MAGHRIB] = it }
        val maghrib = times[PrayerType.MAGHRIB]
        if (maghrib != null && nextFajr != null) {
            endTimes[PrayerType.ISHA] = astronomyEngine.islamicMidnight(maghrib, nextFajr)
        }
        fun model(
            id: String,
            type: PrayerType,
            nameEn: String,
            nameAr: String,
            defaultFard: Int,
            sunnahBefore: Int,
            sunnahAfter: Int,
            nawafilName: String? = null
        ): PrayerModel {
            val record = completedRecordsByPrayer[type]
            return PrayerModel(
                id = id,
                nameEnglish = nameEn,
                nameArabic = nameAr,
                // A missing time is shown as unavailable. Never substitute a static clock
                // time: that claimed a resolved timetable when there was none.
                time = prayerTimeLabel(times[type]),
                endTime = endTimes[type]?.toJavaLocalTime()?.format(timeFormatter).orEmpty(),
                fardRakats = rakats[type] ?: defaultFard,
                sunnahBefore = sunnahBefore,
                sunnahAfter = sunnahAfter,
                isFardCompleted = record?.isCompleted == true,
                isJamaah = record?.isJamaah == true,
                isNawafilCompleted = type in completedNawafilPrayers,
                nawafilName = nawafilName
            )
        }

        return listOf(
            model("fajr", PrayerType.FAJR, "Fajr", "الفجر", 2, 2, 0),
            model(
                "dhuhr",
                PrayerType.DHUHR,
                PrayerType.DHUHR.displayEnglishName(isFriday),
                PrayerType.DHUHR.displayArabicName(isFriday),
                4,
                4,
                2,
                "2 Rakat Nafl"
            ),
            model("asr", PrayerType.ASR, "Asr", "العصر", 4, 4, 0),
            model("maghrib", PrayerType.MAGHRIB, "Maghrib", "المغرب", 3, 0, 2, "Awwabin (6 Rakat)"),
            model("isha", PrayerType.ISHA, "Isha", "العشاء", 4, 4, 2, "Tahajjud & Witr")
        )
    }

    /** Friday's Dhuhr is displayed as Jummah, so resolve completion by stable id first. */
    private fun prayerTypeForPrayer(prayer: PrayerModel): PrayerType? =
        runCatching { PrayerType.valueOf(prayer.id.uppercase()) }.getOrNull()
            ?: runCatching { PrayerType.valueOf(prayer.nameEnglish.uppercase()) }.getOrNull()
            ?: PrayerType.DHUHR.takeIf { prayer.nameEnglish.equals("Jummah", ignoreCase = true) }

    private fun observeTodaySalahRecords(epochDay: Long) {
        if (observedEpochDay == epochDay && salahObserverJob?.isActive == true) return
        observedEpochDay = epochDay
        salahObserverJob?.cancel()
        salahObserverJob = viewModelScope.launch {
            salahDao.observeTodaySalah(epochDay).collect { entities ->
                completedRecordsByPrayer = entities.mapNotNull { entity ->
                    runCatching { PrayerType.valueOf(entity.prayerType) to entity }.getOrNull()
                }.toMap()
                refreshNaflForCurrentDate()
                _uiState.update { state ->
                    if (state.prayersList.isEmpty()) state
                    else {
                        val updatedList = state.prayersList.map { prayer ->
                            val type = prayerTypeForPrayer(prayer)
                            val record = type?.let { completedRecordsByPrayer[it] }
                            prayer.copy(
                                isFardCompleted = record?.isCompleted == true,
                                isJamaah = record?.isJamaah == true,
                                isNawafilCompleted = type != null && type in completedNawafilPrayers
                            )
                        }
                        state.copy(
                            prayersList = updatedList,
                            isCurrentPrayerCompleted = completedRecordsByPrayer[state.currentPrayer]?.isCompleted == true,
                            dailyCompletionByPrayer = dailyCompletionMap()
                        )
                    }
                }
            }
        }
    }

    private fun observeQadhaLedger() {
        viewModelScope.launch {
            qadhaDao.observeQadhaLedger().collect { entities ->
                var invalid = 0
                val map = entities.mapNotNull { entity ->
                    val type = parsePrayerType(entity.prayerType)
                    if (type == null) {
                        // Never fold a corrupted row into Fajr: that inflated a real deficit.
                        invalid++
                        android.util.Log.w(
                            "SalahViewModel",
                            "Skipping Qadha row with unknown prayer type '${entity.prayerType}'"
                        )
                        null
                    } else {
                        type to entity.totalMissed
                    }
                }.toMap()
                _uiState.update { it.copy(qadhaDeficits = map, invalidQadhaRecords = invalid) }
            }
        }
    }

    /** Friday relabels Dhuhr as Jummah, so match on the stable id, never the display text. */
    private fun PrayerModel.matchesType(type: PrayerType): Boolean =
        id.equals(type.name, ignoreCase = true) ||
            nameEnglish.equals(type.name, ignoreCase = true) ||
            (type == PrayerType.DHUHR && nameEnglish.equals("Jummah", ignoreCase = true))

    private fun toggleSalahCompletion(prayerType: PrayerType, isJamaah: Boolean) {
        viewModelScope.launch {
            val currentlyCompleted = _uiState.value.prayersList
                .firstOrNull { it.matchesType(prayerType) }
                ?.isFardCompleted == true
            val newCompleted = !currentlyCompleted
            val newJamaah = if (newCompleted) isJamaah else false

            val epochDay = currentLocationLocalDate(_uiState.value.timeZoneId).toEpochDay()
            val existing = salahDao.getSalahRecord(epochDay, prayerType.name)
            val entity = (existing ?: SalahRecordEntity(
                dateEpochDay = epochDay,
                prayerType = prayerType.name,
                isCompleted = false,
                isJamaah = false,
                isQadha = false
            )).copy(
                isCompleted = newCompleted,
                isJamaah = newJamaah,
                completedTimestamp = if (newCompleted) System.currentTimeMillis() else null
            )
            completedRecordsByPrayer = completedRecordsByPrayer.toMutableMap().apply {
                put(prayerType, entity)
            }

            val updated = _uiState.value.prayersList.map { p ->
                if (p.matchesType(prayerType)) {
                    p.copy(isFardCompleted = newCompleted, isJamaah = newJamaah)
                } else p
            }
            _uiState.update {
                it.copy(
                    prayersList = updated,
                    isCurrentPrayerCompleted = if (prayerType == it.currentPrayer) newCompleted else it.isCurrentPrayerCompleted,
                    dailyCompletionByPrayer = dailyCompletionMap()
                )
            }

            // The table is keyed by (dateEpochDay, prayerType). A plain INSERT IGNORE
            // silently dropped the unchecked state when a row already existed.
            if (existing != null) {
                salahDao.updateSalahRecord(entity)
            } else {
                salahDao.upsertSalahRecord(entity)
            }
        }
    }

    private fun toggleCurrentPrayerCompleted() {
        val current = _uiState.value.currentPrayer
        if (current == PrayerType.SUNRISE) return
        toggleSalahCompletion(current, false)
    }

    private fun toggleNaflCompletion(prayerType: PrayerType, nafl: NaflType) {
        val epochDay = currentLocationLocalDate(_uiState.value.timeZoneId).toEpochDay()
        val prefs = preferences
        if (prefs != null) {
            prefs.toggleNaflCompletion(epochDay, prayerType, nafl)
            completedNawafilPrayers = prefs.naflCompletions(epochDay).mapNotNull { entry ->
                entry.split(':').getOrNull(1)?.let { name ->
                    runCatching { PrayerType.valueOf(name) }.getOrNull()
                }
            }.toSet()
        } else {
            // No preference store (preview/tests): keep the in-memory toggle consistent.
            completedNawafilPrayers = if (prayerType in completedNawafilPrayers) {
                completedNawafilPrayers - prayerType
            } else {
                completedNawafilPrayers + prayerType
            }
        }
        val updated = _uiState.value.prayersList.map { p ->
            if (p.nameEnglish.equals(prayerType.name, ignoreCase = true)) {
                p.copy(isNawafilCompleted = prayerType in completedNawafilPrayers)
            } else p
        }
        _uiState.update { it.copy(prayersList = updated) }
    }

    /** Reloads date-scoped Nafl completion; a new day starts with nothing checked. */
    private fun refreshNaflForCurrentDate() {
        val prefs = preferences ?: return
        val epochDay = currentLocationLocalDate(_uiState.value.timeZoneId).toEpochDay()
        completedNawafilPrayers = prefs.naflCompletions(epochDay).mapNotNull { entry ->
            entry.split(':').getOrNull(1)?.let { name ->
                runCatching { PrayerType.valueOf(name) }.getOrNull()
            }
        }.toSet()
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

    private fun updateDateLabels(timeZoneId: String) {
        val localDate = currentLocationLocalDate(timeZoneId)
        val gregorian = localDate.format(DateTimeFormatter.ofPattern("EEEE · d MMMM yyyy", Locale.ENGLISH))
        val offsetDays = settingsRepository?.settings?.value?.prayer?.hijriOffset ?: 0
        val hijri = runCatching {
            val h = HijrahDate.from(localDate).plus(offsetDays.toLong(), ChronoUnit.DAYS)
            val day = h.get(ChronoField.DAY_OF_MONTH)
            val month = HIJRI_MONTHS.getOrElse(h.get(ChronoField.MONTH_OF_YEAR) - 1) { "Hijri" }
            val year = h.get(ChronoField.YEAR_OF_ERA)
            "$day $month $year AH"
        }.getOrDefault(_uiState.value.hijriDate)

        _uiState.update { it.copy(gregorianDate = gregorian, hijriDate = hijri) }
    }

    private fun currentLocationLocalDate(timeZoneId: String): LocalDate =
        LocalDate.now(TimeZoneResolver.resolveZoneId(timeZoneId))

    private fun locationKTimeZone(timeZoneId: String): KTimeZone =
        runCatching { KTimeZone.of(timeZoneId) }.getOrElse { KTimeZone.currentSystemDefault() }

    private fun formatCountdown(info: PrayerCountdownInfo): String {
        val hrs = info.remainingMinutes / 60
        val mins = info.remainingMinutes % 60
        return if (hrs > 0) "${hrs}h ${mins}m ${info.remainingSeconds}s"
        else "${mins}m ${info.remainingSeconds}s"
    }

    private fun dailyCompletionMap(): Map<String, Boolean> =
        listOf(PrayerType.FAJR, PrayerType.DHUHR, PrayerType.ASR, PrayerType.MAGHRIB, PrayerType.ISHA)
            .associate { it.englishName to (completedRecordsByPrayer[it]?.isCompleted == true) }


    private fun isTahajjudWindowNow(
        countdown: PrayerCountdownInfo,
        times: Map<PrayerType, KLocalTime>,
        tahajjudStart: KLocalTime?
    ): Boolean {
        if (countdown.currentPrayer != PrayerType.ISHA || countdown.nextPrayer != PrayerType.FAJR) return false
        val start = tahajjudStart ?: return false
        val fajrSecond = times[PrayerType.FAJR]?.toSecondOfDay() ?: return false
        val nowSecond = Clock.System.now().toLocalDateTime(locationKTimeZone(_uiState.value.timeZoneId)).time.toSecondOfDay()
        val startSecond = start.toSecondOfDay()
        return if (startSecond <= fajrSecond) {
            nowSecond in startSecond..fajrSecond
        } else {
            nowSecond >= startSecond || nowSecond <= fajrSecond
        }
    }


    private fun formatPrayerTime(time: KLocalTime?): String =
        time?.toJavaLocalTime()?.format(DateTimeFormatter.ofPattern("hh:mm a")) ?: ""

    private fun windowMillis(start: KLocalTime?, end: KLocalTime?): Long {
        if (start == null || end == null) return 1L
        val s = start.toSecondOfDay().toLong()
        val e = end.toSecondOfDay().toLong()
        val diff = if (e > s) e - s else if (e < s) e + 86400L - s else 86400L
        return diff.coerceAtLeast(1L) * 1000L
    }

    private fun fromSettingsAsr(asr: AsrMethod): Madhhab =
        if (asr == AsrMethod.HANAFI) Madhhab.HANAFI else Madhhab.SHAFI_HANBALI_MALIKI

    private fun toSettingsAsr(madhhab: Madhhab): AsrMethod =
        if (madhhab == Madhhab.HANAFI) AsrMethod.HANAFI else AsrMethod.SHAFI

    companion object {
        private const val SECONDS_PER_DAY = 86_400L
        private val weatherJson = Json { ignoreUnknownKeys = true }

        /** Shown when a prayer time could not be resolved, instead of a fake clock time. */
        internal const val UNAVAILABLE_TIME = "--:--"

        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

        /** Resolved time, or the explicit unavailable marker. Never a placeholder clock time. */
        internal fun prayerTimeLabel(time: KLocalTime?): String =
            time?.toJavaLocalTime()?.format(TIME_FORMATTER) ?: UNAVAILABLE_TIME

        /** Parses a stored prayer type without ever falling back to Fajr. */
        internal fun parsePrayerType(raw: String?): PrayerType? =
            raw?.let { runCatching { PrayerType.valueOf(it) }.getOrNull() }
        private val HIJRI_MONTHS = listOf(
            "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
            "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha'ban",
            "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
        )
    }
}
