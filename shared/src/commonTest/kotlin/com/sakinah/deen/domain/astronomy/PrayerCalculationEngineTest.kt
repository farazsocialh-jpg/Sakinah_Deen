package com.sakinah.deen.domain.astronomy

import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.PrayerAdjustments
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertTrue

class PrayerCalculationEngineTest {

    private val engine = PrayerCalculationEngineImpl()

    @Test
    fun qiblaIsFiniteAndNormalized() {
        val azimuth = engine.getQiblaAzimuth(Pair(21.422487, 39.826206))
        assertTrue(azimuth.isFinite(), "expected finite bearing, got $azimuth")
        assertTrue(azimuth in 0.0..360.0, "expected normalized bearing, got $azimuth")
    }

    @Test
    fun qiblaFromLondonIsSouthEast() {
        val azimuth = engine.getQiblaAzimuth(Pair(51.5074, -0.1278))
        assertTrue(azimuth in 110.0..135.0, "expected SE bearing, got $azimuth")
    }

    @Test
    fun qiblaFromSydneyIsNorthWest() {
        val azimuth = engine.getQiblaAzimuth(Pair(-33.8688, 151.2093))
        assertTrue(azimuth in 250.0..290.0, "expected NW-ish bearing, got $azimuth")
    }

    @Test
    fun prayerTimesAtEquatorDoNotCrash() {
        val times = engine.getPrayerTimes(
            coordinates = Pair(0.0, 0.0),
            date = LocalDate(2026, 6, 15),
            method = CalculationMethod.MUSLIM_WORLD_LEAGUE,
            madhhab = Madhhab.SHAFI_HANBALI_MALIKI,
            timeZone = TimeZone.UTC,
            adjustments = PrayerAdjustments()
        )
        assertTrue(times.size == 6)
        assertTrue(times.values.all { it.hour in 0..23 })
    }

    @Test
    fun prayerTimesAtHighLatitudeDoNotCrash() {
        val times = engine.getPrayerTimes(
            coordinates = Pair(64.0, 25.0),
            date = LocalDate(2026, 12, 21),
            method = CalculationMethod.MUSLIM_WORLD_LEAGUE,
            madhhab = Madhhab.HANAFI,
            timeZone = TimeZone.UTC,
            adjustments = PrayerAdjustments()
        )
        assertTrue(times.size == 6)
    }

    @Test
    fun countdownReturnsValidRatio() {
        val times = engine.getPrayerTimes(
            coordinates = Pair(21.422487, 39.826206),
            date = LocalDate(2026, 11, 12),
            method = CalculationMethod.UMM_AL_QURA,
            madhhab = Madhhab.SHAFI_HANBALI_MALIKI,
            timeZone = TimeZone.UTC,
            adjustments = PrayerAdjustments()
        )
        val info = engine.calculateCountdown(times, kotlinx.datetime.LocalTime(12, 0))
        assertTrue(info.elapsedRatio in 0f..1f)
    }
}
