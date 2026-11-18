package com.sakinah.deen

import com.sakinah.deen.domain.sensor.QiblaSensorManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QiblaSensorManagerTest {

    @Test
    fun vincentyQiblaBearing_doha() {
        val bearing = QiblaSensorManager.calculateQiblaBearing(25.2854, 51.5310)
        assertEquals(252.7256, bearing.toDouble(), 0.5)
    }

    @Test
    fun vincentyQiblaBearing_alWukair() {
        val bearing = QiblaSensorManager.calculateQiblaBearing(25.1497, 51.4990)
        assertEquals(253.3117, bearing.toDouble(), 0.5)
    }

    @Test
    fun vincentyQiblaBearing_london() {
        val bearing = QiblaSensorManager.calculateQiblaBearing(51.5074, -0.1278)
        assertEquals(118.8684, bearing.toDouble(), 0.5)
    }

    @Test
    fun vincentyQiblaBearing_newYork() {
        val bearing = QiblaSensorManager.calculateQiblaBearing(40.7128, -74.0060)
        assertEquals(58.3960, bearing.toDouble(), 0.5)
    }

    @Test
    fun vincentyQiblaBearing_jakarta() {
        val bearing = QiblaSensorManager.calculateQiblaBearing(-6.2088, 106.8456)
        assertEquals(295.0247, bearing.toDouble(), 0.5)
    }

    @Test
    fun vincentyQiblaBearing_makkahItself() {
        val bearing = QiblaSensorManager.calculateQiblaBearing(21.422487, 39.826206)
        assertEquals(0.0, bearing.toDouble(), 0.0001)
    }

    @Test
    fun distanceToKaabaIsZeroAtTheKaabaAndPlausibleElsewhere() {
        assertEquals(
            QiblaSensorManager.calculateDistanceToKaabaKm(
                QiblaSensorManager.KAABA_LATITUDE,
                QiblaSensorManager.KAABA_LONGITUDE
            ),
            0.0,
            0.001
        )
        val fromDoha = QiblaSensorManager.calculateDistanceToKaabaKm(25.2854, 51.5310)
        assertTrue("Doha to Makkah should be roughly 1,000-1,500 km but was $fromDoha", fromDoha in 1_000.0..1_500.0)
    }

    @Test
    fun vincentyQiblaBearing_aliasesAgree() {
        assertEquals(
            QiblaSensorManager.calculateQiblaBearing(40.7128, -74.0060),
            QiblaSensorManager.vincentyBearing(40.7128, -74.0060)
        )
        assertEquals(
            QiblaSensorManager.calculateKaabaBearing(40.7128, -74.0060),
            QiblaSensorManager.qiblaBearing(40.7128, -74.0060)
        )
    }
}
