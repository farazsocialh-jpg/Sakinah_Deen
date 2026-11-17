package com.sakinah.deen.domain.qibla

import kotlin.math.abs
import kotlin.math.roundToInt

/** Alignment tolerance, in degrees, at which the user is told they face the Qibla. */
const val QIBLA_ALIGNMENT_TOLERANCE_DEG = 4f

/** Device tilt, in degrees from flat, above which a magnetic heading cannot be trusted. */
const val QIBLA_TILT_THRESHOLD_DEG = 18f

/** Raw runtime-permission state for the location the Qibla bearing depends on. */
enum class QiblaPermissionState { NOT_REQUESTED, GRANTED, DENIED, PERMANENTLY_DENIED }

/**
 * Sensor confidence, mapped from [android.hardware.SensorManager] accuracy constants by the
 * Android layer so this state machine stays framework-free and unit-testable.
 */
enum class QiblaSensorAccuracy(val label: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low"),
    UNRELIABLE("Unreliable")
}

/** CTA the Qibla card should offer for the current state. */
enum class QiblaAction { GRANT_PERMISSION, OPEN_SETTINGS, ENABLE_LOCATION, RETRY }

/**
 * A single, coherent compass reading. Heading, bearing, distance and accuracy are always
 * carried together so the UI can present exact numbers instead of a bare needle.
 */
data class QiblaReading(
    val headingDeg: Float,
    val qiblaBearingDeg: Float,
    val distanceKm: Int,
    val accuracy: QiblaSensorAccuracy,
    val tiltDeg: Float,
    val magneticInterference: Boolean = false,
    val isVehicularTrajectoryActive: Boolean = false
) {
    /** Shortest signed rotation from the current heading to the Qibla: +right, -left. */
    val signedTurnDegrees: Float
        get() {
            var delta = (qiblaBearingDeg - headingDeg) % 360f
            if (delta > 180f) delta -= 360f
            if (delta < -180f) delta += 360f
            return delta
        }

    val isAligned: Boolean
        get() = abs(signedTurnDegrees) <= QIBLA_ALIGNMENT_TOLERANCE_DEG

    /** Spoken/visual instruction, e.g. "Turn left 15 degrees". */
    val turnInstruction: String
        get() = when {
            isAligned -> "Facing the Qibla"
            signedTurnDegrees > 0f -> "Turn right ${abs(signedTurnDegrees).roundToInt()} degrees"
            else -> "Turn left ${abs(signedTurnDegrees).roundToInt()} degrees"
        }

    /** True when the heading itself cannot be trusted (interference or an unreliable sensor). */
    val isHeadingUnreliable: Boolean
        get() = magneticInterference ||
            accuracy == QiblaSensorAccuracy.LOW ||
            accuracy == QiblaSensorAccuracy.UNRELIABLE
}

/**
 * Explicit state machine for the Qibla experience (audit #47/#48): every failure mode the
 * compass can hit is a named state with its own message and, where useful, a CTA.
 */
sealed interface QiblaUiState {
    /** Human-readable explanation shown in the state banner. */
    val message: String

    /** Optional CTA rendered next to [message]. */
    val action: QiblaAction?
        get() = null

    /** True when the state is about the location every mode depends on, not the sensor. */
    val isLocationRelated: Boolean
        get() = false

    data object Idle : QiblaUiState {
        override val message = "Preparing the Qibla compass…"
    }

    data object RequestingPermission : QiblaUiState {
        override val message = "Waiting for location permission…"
        override val isLocationRelated = true
    }

    data object PermissionRequired : QiblaUiState {
        override val message = "Allow location to calculate the Qibla from where you are."
        override val action = QiblaAction.GRANT_PERMISSION
        override val isLocationRelated = true
    }

    data object PermissionDenied : QiblaUiState {
        override val message = "Location is denied, so the Qibla direction is unknown."
        override val action = QiblaAction.GRANT_PERMISSION
        override val isLocationRelated = true
    }

    data object PermissionPermanentlyDenied : QiblaUiState {
        override val message = "Location is blocked. Enable it for ImanCore in Android Settings."
        override val action = QiblaAction.OPEN_SETTINGS
        override val isLocationRelated = true
    }

    data object LocationDisabled : QiblaUiState {
        override val message = "Device location is switched off."
        override val action = QiblaAction.ENABLE_LOCATION
        override val isLocationRelated = true
    }

    data object Locating : QiblaUiState {
        override val message = "Locating your position to compute the Qibla bearing…"
        override val isLocationRelated = true
    }

    data object SensorUnavailable : QiblaUiState {
        override val message =
            "This device has no compass sensor. Use Map or Sun mode to find the Qibla."
    }

    data object CalibrationNeeded : QiblaUiState {
        override val message =
            "Compass accuracy is low. Move the phone in a figure-8, away from metal and magnets."
    }

    data object Tilted : QiblaUiState {
        override val message = "Hold the phone flat — a tilted compass reads the wrong direction."
    }

    data class Ready(val reading: QiblaReading) : QiblaUiState {
        override val message = reading.turnInstruction
    }

    data class NearAligned(val reading: QiblaReading) : QiblaUiState {
        override val message = "Aligned with the Qibla · ${reading.qiblaBearingDeg.roundToInt()}° from true north"
    }

    data class Error(val reason: String) : QiblaUiState {
        override val message = "Compass error: $reason"
        override val action = QiblaAction.RETRY
    }
}

/**
 * Everything the state machine needs, gathered by the Android layer. Kept as a plain data
 * class so the precedence rules below can be unit-tested without a device.
 */
data class QiblaStatus(
    val permission: QiblaPermissionState = QiblaPermissionState.NOT_REQUESTED,
    val permissionRequestInFlight: Boolean = false,
    val locationServicesEnabled: Boolean = false,
    val hasCoordinates: Boolean = false,
    val hasSensor: Boolean = true,
    val accuracy: QiblaSensorAccuracy = QiblaSensorAccuracy.HIGH,
    val tiltDeg: Float = 0f,
    val headingDeg: Float = 0f,
    val qiblaBearingDeg: Float = 0f,
    val distanceKm: Int = 0,
    val magneticInterference: Boolean = false,
    val isVehicularTrajectoryActive: Boolean = false,
    val error: String? = null
) {
    val reading: QiblaReading
        get() = QiblaReading(
            headingDeg = headingDeg,
            qiblaBearingDeg = qiblaBearingDeg,
            distanceKm = distanceKm,
            accuracy = accuracy,
            tiltDeg = tiltDeg,
            magneticInterference = magneticInterference,
            isVehicularTrajectoryActive = isVehicularTrajectoryActive
        )

    /**
     * Resolves the single state to render. Order matters: a hard error wins, then the
     * location dependency, then sensor availability/quality, then alignment.
     */
    fun toUiState(): QiblaUiState {
        error?.let { return QiblaUiState.Error(it) }
        if (permissionRequestInFlight) return QiblaUiState.RequestingPermission
        if (!hasCoordinates) {
            return when (permission) {
                QiblaPermissionState.GRANTED ->
                    if (locationServicesEnabled) QiblaUiState.Locating else QiblaUiState.LocationDisabled
                QiblaPermissionState.NOT_REQUESTED -> QiblaUiState.PermissionRequired
                QiblaPermissionState.DENIED -> QiblaUiState.PermissionDenied
                QiblaPermissionState.PERMANENTLY_DENIED -> QiblaUiState.PermissionPermanentlyDenied
            }
        }
        if (!hasSensor) return QiblaUiState.SensorUnavailable
        if (accuracy == QiblaSensorAccuracy.LOW || accuracy == QiblaSensorAccuracy.UNRELIABLE) {
            return QiblaUiState.CalibrationNeeded
        }
        if (tiltDeg > QIBLA_TILT_THRESHOLD_DEG) return QiblaUiState.Tilted
        return if (reading.isAligned) {
            QiblaUiState.NearAligned(reading)
        } else {
            QiblaUiState.Ready(reading)
        }
    }
}
