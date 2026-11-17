package com.sakinah.deen

import com.sakinah.deen.domain.qibla.QiblaPermissionState
import com.sakinah.deen.domain.qibla.QiblaSensorAccuracy
import com.sakinah.deen.domain.qibla.QiblaStatus
import com.sakinah.deen.domain.qibla.QiblaUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Qibla state machine must resolve every failure mode deterministically: the compass
 * silently showing a wrong-bearing needle is the bug these states exist to prevent.
 */
class QiblaUiStateTest {

    private val located = QiblaStatus(
        permission = QiblaPermissionState.GRANTED,
        locationServicesEnabled = true,
        hasCoordinates = true,
        hasSensor = true,
        accuracy = QiblaSensorAccuracy.HIGH,
        tiltDeg = 2f,
        headingDeg = 100f,
        qiblaBearingDeg = 250f,
        distanceKm = 1200
    )

    @Test
    fun `error outranks every other condition`() {
        val state = located.copy(
            error = "sensor failure",
            hasCoordinates = false,
            hasSensor = false
        ).toUiState()
        assertTrue(state is QiblaUiState.Error)
        assertEquals("sensor failure", (state as QiblaUiState.Error).reason)
    }

    @Test
    fun `permission states are resolved when coordinates are missing`() {
        val base = located.copy(hasCoordinates = false, permissionRequestInFlight = false)
        assertEquals(
            QiblaUiState.RequestingPermission,
            base.copy(permissionRequestInFlight = true).toUiState()
        )
        assertEquals(
            QiblaUiState.PermissionRequired,
            base.copy(permission = QiblaPermissionState.NOT_REQUESTED).toUiState()
        )
        assertEquals(
            QiblaUiState.PermissionDenied,
            base.copy(permission = QiblaPermissionState.DENIED).toUiState()
        )
        assertEquals(
            QiblaUiState.PermissionPermanentlyDenied,
            base.copy(permission = QiblaPermissionState.PERMANENTLY_DENIED).toUiState()
        )
        assertEquals(
            QiblaUiState.LocationDisabled,
            base.copy(locationServicesEnabled = false).toUiState()
        )
        assertEquals(
            QiblaUiState.Locating,
            base.copy(locationServicesEnabled = true).toUiState()
        )
    }

    @Test
    fun `sensor failures outrank alignment`() {
        assertEquals(
            QiblaUiState.SensorUnavailable,
            located.copy(hasSensor = false).toUiState()
        )
        assertEquals(
            QiblaUiState.CalibrationNeeded,
            located.copy(accuracy = QiblaSensorAccuracy.LOW).toUiState()
        )
        assertEquals(
            QiblaUiState.CalibrationNeeded,
            located.copy(accuracy = QiblaSensorAccuracy.UNRELIABLE).toUiState()
        )
        assertEquals(
            QiblaUiState.Tilted,
            located.copy(tiltDeg = 40f).toUiState()
        )
    }

    @Test
    fun `aligned reading reports near-aligned with exact numbers`() {
        val state = located.copy(headingDeg = 248f).toUiState()
        assertTrue(state is QiblaUiState.NearAligned)
        val reading = (state as QiblaUiState.NearAligned).reading
        assertEquals(250f, reading.qiblaBearingDeg, 0.01f)
        assertEquals(248f, reading.headingDeg, 0.01f)
        assertEquals(1200, reading.distanceKm)
        assertTrue(reading.isAligned)
    }

    @Test
    fun `un-aligned reading states the shortest turn direction`() {
        // 350° Qibla from a 5° heading is a 15° left turn, never a 345° right turn.
        val left = (located.copy(headingDeg = 5f, qiblaBearingDeg = 350f).toUiState() as QiblaUiState.Ready).reading
        assertEquals(-15f, left.signedTurnDegrees, 0.01f)
        assertEquals("Turn left 15 degrees", left.turnInstruction)

        // And the mirror case turns right.
        val clockwise = (located.copy(headingDeg = 350f, qiblaBearingDeg = 5f).toUiState() as QiblaUiState.Ready).reading
        assertEquals(15f, clockwise.signedTurnDegrees, 0.01f)
        assertEquals("Turn right 15 degrees", clockwise.turnInstruction)
    }

    @Test
    fun `location states are flagged for the card level banner`() {
        assertTrue(QiblaUiState.PermissionRequired.isLocationRelated)
        assertTrue(QiblaUiState.Locating.isLocationRelated)
        assertFalse(QiblaUiState.CalibrationNeeded.isLocationRelated)
        assertFalse(QiblaUiState.Tilted.isLocationRelated)
    }

    @Test
    fun `magnetic interference marks the heading unreliable`() {
        val reading = located.copy(magneticInterference = true).reading
        assertTrue(reading.isHeadingUnreliable)
        assertFalse(located.reading.isHeadingUnreliable)
    }
}
