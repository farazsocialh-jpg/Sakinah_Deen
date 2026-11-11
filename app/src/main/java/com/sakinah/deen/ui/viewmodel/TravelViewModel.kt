package com.sakinah.deen.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngine
import com.sakinah.deen.domain.travel.TravelState
import com.sakinah.deen.domain.travel.TravelStatusManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TravelUiState(
    val homeAnchorCity: String = "London, UK",
    val homeCoordinates: Pair<Double, Double> = Pair(51.5074, -0.1278),
    val currentLocationName: String = "M40 Motorway, Milepost 46",
    val currentCoordinates: Pair<Double, Double> = Pair(52.1245, -1.2435),
    val distanceKm: Float = 74f,
    val thresholdKm: Float = 88f,
    val isSafar: Boolean = false,
    val vehicleHeading: Float = 42f,
    val kaabaAzimuth: Float = 118f,
    val relativeAngle: Float = 76f,
    val isBluetoothConnected: Boolean = true,
    val isPlayingDua: Boolean = false,
    val prayerRakats: Map<PrayerType, Int> = mapOf(
        PrayerType.FAJR to 2,
        PrayerType.DHUHR to 4,
        PrayerType.ASR to 4,
        PrayerType.MAGHRIB to 3,
        PrayerType.ISHA to 4
    )
)

sealed class TravelUiIntent {
    data class UpdateCurrentCoordinates(val lat: Double, val lon: Double, val locationName: String? = null) : TravelUiIntent()
    data class UpdateHeading(val headingDeg: Float) : TravelUiIntent()
    data class SetHomeAnchor(val lat: Double, val lon: Double, val cityName: String) : TravelUiIntent()
    data class SetBluetoothConnected(val isConnected: Boolean) : TravelUiIntent()
    data class ToggleDuaPlayback(val play: Boolean) : TravelUiIntent()
    data class SimulateDistance(val targetKm: Float) : TravelUiIntent()
}

class TravelViewModel(
    private val travelManager: TravelStatusManager,
    private val astronomyEngine: PrayerCalculationEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(TravelUiState())
    val uiState: StateFlow<TravelUiState> = _uiState.asStateFlow()

    init {
        recomputeTravelState()
    }

    fun onIntent(intent: TravelUiIntent) {
        when (intent) {
            is TravelUiIntent.UpdateCurrentCoordinates -> {
                _uiState.update {
                    it.copy(
                        currentCoordinates = Pair(intent.lat, intent.lon),
                        currentLocationName = intent.locationName ?: it.currentLocationName
                    )
                }
                recomputeTravelState()
            }
            is TravelUiIntent.UpdateHeading -> {
                val clampedHeading = (intent.headingDeg % 360f + 360f) % 360f
                val relative = (_uiState.value.kaabaAzimuth - clampedHeading + 360f) % 360f
                _uiState.update {
                    it.copy(
                        vehicleHeading = clampedHeading,
                        relativeAngle = relative
                    )
                }
            }
            is TravelUiIntent.SetHomeAnchor -> {
                travelManager.setHomeAnchor(Pair(intent.lat, intent.lon))
                _uiState.update {
                    it.copy(
                        homeCoordinates = Pair(intent.lat, intent.lon),
                        homeAnchorCity = intent.cityName
                    )
                }
                recomputeTravelState()
            }
            is TravelUiIntent.SetBluetoothConnected -> {
                _uiState.update { it.copy(isBluetoothConnected = intent.isConnected) }
            }
            is TravelUiIntent.ToggleDuaPlayback -> {
                _uiState.update { it.copy(isPlayingDua = intent.play) }
            }
            is TravelUiIntent.SimulateDistance -> {
                val isSafarActive = intent.targetKm >= _uiState.value.thresholdKm
                val rakats = if (isSafarActive) {
                    mapOf(
                        PrayerType.FAJR to 2,
                        PrayerType.DHUHR to 2,
                        PrayerType.ASR to 2,
                        PrayerType.MAGHRIB to 3,
                        PrayerType.ISHA to 2
                    )
                } else {
                    mapOf(
                        PrayerType.FAJR to 2,
                        PrayerType.DHUHR to 4,
                        PrayerType.ASR to 4,
                        PrayerType.MAGHRIB to 3,
                        PrayerType.ISHA to 4
                    )
                }
                _uiState.update {
                    it.copy(
                        distanceKm = intent.targetKm,
                        isSafar = isSafarActive,
                        prayerRakats = rakats
                    )
                }
            }
        }
    }

    private fun recomputeTravelState() {
        val currentCoords = _uiState.value.currentCoordinates
        val travelState: TravelState = travelManager.evaluateTravelState(currentCoords)
        val qiblaBearing = astronomyEngine.getQiblaAzimuth(currentCoords).toFloat()
        val heading = _uiState.value.vehicleHeading
        val relative = (qiblaBearing - heading + 360f) % 360f

        _uiState.update {
            it.copy(
                distanceKm = travelState.distanceKm.toFloat(),
                isSafar = travelState.isTraveler,
                prayerRakats = travelState.shortenedRakats,
                kaabaAzimuth = qiblaBearing,
                relativeAngle = relative
            )
        }
    }
}
