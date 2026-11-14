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
import com.sakinah.deen.settings.AsrMethod
import com.sakinah.deen.settings.CalculationMethod as SharedCalculationMethod
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

data class SalahUiState(
    val hijriDate: String = "14 Jumada al-Ula 1448 AH",
    val gregorianDate: String = "",
    val prayerTimes: Map<PrayerType, KLocalTime> = emptyMap(),
    val currentPrayer: PrayerType = PrayerType.DHUHR,
    val nextPrayer: PrayerType = PrayerType.ASR,
    val countdownText: String = "00m 00s",
    val elapsedWindowRatio: Float = 0.0f,
    val prayersList: List<PrayerModel> = emptyList(),
    val qadhaDeficits: Map<PrayerType, Int> = emptyMap(),
    val calculationMethod: CalculationMethod = CalculationMethod.UMM_AL_QURA,
    val madhhab: Madhhab = Madhhab.SHAFI_HANBALI_MALIKI,
    val adjustments: PrayerAdjustments = PrayerAdjustments(),
    val locationName: String = "Detecting Location...",
    val isLocationPermissionGranted: Boolean = false,
    val showLocationWarningBanner: Boolean = false,
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
    private val settingsRepository: SettingsRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalahUiState())
    val uiState: StateFlow<SalahUiState> = _uiState.asStateFlow()

    /** ISO code of the last resolved location, driving the country → authority mapping. */
    private var countryCode: String? = preferences?.locationCountryCode

    /** Guards against a slow online resolution overwriting a newer one. */
    private var scheduleRequest = 0

    private var salahObserverJob: Job? = null
    private var observedEpochDay: Long? = null
    private var completedRecordsByPrayer: Map<PrayerType, SalahRecordEntity> = emptyMap()
    private val completedNawafilPrayers = mutableSetOf<PrayerType>()

    init {
        preferences?.homeCoordinates?.let { travelManager.setHomeAnchor(it) }
        val initialMadhhab = settingsRepository?.settings?.value?.prayer?.asrMethod?.let(::fromSettingsAsr)
            ?: preferences?.madhhab
            ?: Madhhab.SHAFI_HANBALI_MALIKI

        val savedCoords = preferences?.locationCoordinates
        val savedManual = preferences?.isManualLocation == true && savedCoords != null
        val savedZone = if (savedManual) {
            TimeZoneResolver.resolveZoneIdString(
                explicitZoneId = preferences?.locationTimeZoneId,
                cityLabel = preferences?.locationLabel,
                countryCode = preferences?.locationCountryCode,
                longitude = savedCoords?.second
            )
        } else {
            preferences?.locationTimeZoneId ?: ZoneId.systemDefault().id
        }

        _uiState.update {
            it.copy(
                madhhab = initialMadhhab,
                hasHomeLocation = travelManager.hasHomeAnchor,
                isManualLocation = savedManual,
                userCoordinates = savedCoords ?: it.userCoordinates,
                locationName = if (savedCoords != null) preferences?.locationLabel ?: it.locationName else it.locationName,
                timeZoneId = savedZone,
                timeZoneAbbr = TimeZoneResolver.shortAbbreviation(savedZone)
            )
        }

        syncAuthority()
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

                val manualOverride = preferences?.calculationMethodOverride
                if (manualOverride == null) {
                    if (!_uiState.value.isAutomaticMethod) {
                        _uiState.update { it.copy(isAutomaticMethod = true) }
                        needsReload = true
                    }
                } else {
                    val targetMethod = fromSharedCalculation(settings.prayer.calculationMethod)
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

    private fun fromSharedCalculation(method: SharedCalculationMethod): CalculationMethod = when (method) {
        SharedCalculationMethod.MWL -> CalculationMethod.MUSLIM_WORLD_LEAGUE
        SharedCalculationMethod.ISNA -> CalculationMethod.NORTH_AMERICA
        SharedCalculationMethod.UMM_AL_QURA -> CalculationMethod.UMM_AL_QURA
        SharedCalculationMethod.EGYPTIAN -> CalculationMethod.EGYPTIAN
        SharedCalculationMethod.KARACHI -> CalculationMethod.KARACHI
        SharedCalculationMethod.TEHRAN -> CalculationMethod.KARACHI
        SharedCalculationMethod.MAKKAH -> CalculationMethod.UMM_AL_QURA
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
                if (_uiState.value.isManualLocation) {
                    loadDailySchedule()
                } else {
                    checkAndRefreshLocation()
                }
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
                preferences?.saveManualLocation(
                    latitude = intent.lat,
                    longitude = intent.lon,
                    label = intent.name,
                    countryCode = resolvedCountry,
                    timeZoneId = resolvedZone
                )
                _uiState.update {
                    it.copy(
                        userCoordinates = Pair(intent.lat, intent.lon),
                        locationName = intent.name,
                        timeZoneId = resolvedZone,
                        timeZoneAbbr = TimeZoneResolver.shortAbbreviation(resolvedZone),
                        isManualLocation = true,
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
            val resolved = locationProvider.getFreshOrCachedLocation()
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
            preferences?.saveGpsLocation(
                latitude = resolved.latitude,
                longitude = resolved.longitude,
                label = resolvedLabel,
                countryCode = countryCode,
                timeZoneId = resolvedZone
            )
            _uiState.update {
                it.copy(
                    userCoordinates = Pair(resolved.latitude, resolved.longitude),
                    locationName = resolvedLabel,
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
                    val countdown = astronomyEngine.calculateCountdown(times, now.time)
                    _uiState.update {
                        it.copy(
                            currentPrayer = countdown.currentPrayer,
                            nextPrayer = countdown.nextPrayer,
                            countdownText = formatCountdown(countdown),
                            elapsedWindowRatio = countdown.elapsedRatio
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

        renderTimes(
            times = astronomyEngine.getPrayerTimes(
                coordinates = state.userCoordinates,
                date = kToday,
                method = state.calculationMethod,
                madhhab = state.madhhab,
                timeZone = kZone,
                adjustments = state.adjustments
            ),
            sourceLabel = "Offline Calculation"
        )

        val repository = prayerTimes ?: return
        viewModelScope.launch {
            val resolved = repository.resolveDay(
                date = today,
                coordinates = state.userCoordinates,
                madhhab = state.madhhab,
                zoneId = zoneId,
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
        val state = _uiState.value
        val userZone = locationKTimeZone(state.timeZoneId)
        val coords = state.userCoordinates

        val countdown: PrayerCountdownInfo = astronomyEngine.calculateCountdown(
            times,
            Clock.System.now().toLocalDateTime(userZone).time
        )
        val travel = travelManager.evaluateTravelState(coords)

        val manualTravelMode = preferences?.travelModeActive == true
        val clearManualTravelMode = manualTravelMode &&
            SafarPolicy.shouldClearManualTravelMode(travelManager.getHomeAnchor(), coords)
        if (clearManualTravelMode) preferences?.travelModeActive = false
        val isSafar = travel.isTraveler || (manualTravelMode && !clearManualTravelMode)
        val rakats = SafarPolicy.rakats(isSafar)

        val prayerModels = buildPrayerModels(times, rakats)

        _uiState.update {
            it.copy(
                prayerTimes = times,
                currentPrayer = countdown.currentPrayer,
                nextPrayer = countdown.nextPrayer,
                countdownText = formatCountdown(countdown),
                elapsedWindowRatio = countdown.elapsedRatio,
                prayersList = prayerModels,
                isTraveler = isSafar,
                distanceFromHomeKm = travel.distanceKm,
                hasHomeLocation = travelManager.hasHomeAnchor,
                sourceLabel = sourceLabel,
                authorityName = authorityName ?: it.authorityName,
                timeZoneAbbr = TimeZoneResolver.shortAbbreviation(it.timeZoneId)
            )
        }
    }

    private fun buildPrayerModels(
        times: Map<PrayerType, KLocalTime>,
        rakats: Map<PrayerType, Int>
    ): List<PrayerModel> {
        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")
        fun model(
            id: String,
            type: PrayerType,
            nameEn: String,
            nameAr: String,
            fallbackTime: String,
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
                time = times[type]?.toJavaLocalTime()?.format(timeFormatter) ?: fallbackTime,
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
            model("fajr", PrayerType.FAJR, "Fajr", "الفجر", "05:14 AM", 2, 2, 0),
            model("sunrise", PrayerType.SUNRISE, "Sunrise", "الشروق", "06:38 AM", 0, 0, 0),
            model("dhuhr", PrayerType.DHUHR, "Dhuhr", "الظهر", "12:45 PM", 4, 4, 2, "2 Rakat Nafl"),
            model("asr", PrayerType.ASR, "Asr", "العصر", "03:52 PM", 4, 4, 0),
            model("maghrib", PrayerType.MAGHRIB, "Maghrib", "المغرب", "06:18 PM", 3, 0, 2, "Awwabin (6 Rakat)"),
            model("isha", PrayerType.ISHA, "Isha", "العشاء", "07:44 PM", 4, 4, 2, "Tahajjud & Witr")
        )
    }

    private fun observeTodaySalahRecords(epochDay: Long) {
        if (observedEpochDay == epochDay && salahObserverJob?.isActive == true) return
        observedEpochDay = epochDay
        salahObserverJob?.cancel()
        salahObserverJob = viewModelScope.launch {
            salahDao.observeTodaySalah(epochDay).collect { entities ->
                completedRecordsByPrayer = entities.mapNotNull { entity ->
                    runCatching { PrayerType.valueOf(entity.prayerType) to entity }.getOrNull()
                }.toMap()
                _uiState.update { state ->
                    if (state.prayersList.isEmpty()) state
                    else {
                        val updatedList = state.prayersList.map { prayer ->
                            val type = runCatching { PrayerType.valueOf(prayer.nameEnglish.uppercase()) }.getOrNull()
                            val record = type?.let { completedRecordsByPrayer[it] }
                            prayer.copy(
                                isFardCompleted = record?.isCompleted == true,
                                isJamaah = record?.isJamaah == true,
                                isNawafilCompleted = type != null && type in completedNawafilPrayers
                            )
                        }
                        state.copy(prayersList = updatedList)
                    }
                }
            }
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
            val currentlyCompleted = _uiState.value.prayersList
                .firstOrNull { it.nameEnglish.equals(prayerType.name, ignoreCase = true) }
                ?.isFardCompleted == true
            val newCompleted = !currentlyCompleted
            val newJamaah = if (newCompleted) isJamaah else false

            val epochDay = currentLocationLocalDate(_uiState.value.timeZoneId).toEpochDay()
            val entity = SalahRecordEntity(
                dateEpochDay = epochDay,
                prayerType = prayerType.name,
                isCompleted = newCompleted,
                isJamaah = newJamaah,
                isQadha = false,
                completedTimestamp = if (newCompleted) System.currentTimeMillis() else null
            )
            completedRecordsByPrayer = completedRecordsByPrayer.toMutableMap().apply {
                put(prayerType, entity)
            }

            val updated = _uiState.value.prayersList.map { p ->
                if (p.nameEnglish.equals(prayerType.name, ignoreCase = true)) {
                    p.copy(isFardCompleted = newCompleted, isJamaah = newJamaah)
                } else p
            }
            _uiState.update { it.copy(prayersList = updated) }

            salahDao.upsertSalahRecord(entity)
        }
    }

    private fun toggleNaflCompletion(prayerType: PrayerType, nafl: NaflType) {
        if (prayerType in completedNawafilPrayers) {
            completedNawafilPrayers.remove(prayerType)
        } else {
            completedNawafilPrayers.add(prayerType)
        }
        val updated = _uiState.value.prayersList.map { p ->
            if (p.nameEnglish.equals(prayerType.name, ignoreCase = true)) {
                p.copy(isNawafilCompleted = prayerType in completedNawafilPrayers)
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

    private fun fromSettingsAsr(asr: AsrMethod): Madhhab =
        if (asr == AsrMethod.HANAFI) Madhhab.HANAFI else Madhhab.SHAFI_HANBALI_MALIKI

    private fun toSettingsAsr(madhhab: Madhhab): AsrMethod =
        if (madhhab == Madhhab.HANAFI) AsrMethod.HANAFI else AsrMethod.SHAFI

    companion object {
        private val HIJRI_MONTHS = listOf(
            "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
            "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha'ban",
            "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
        )
    }
}
