package com.sakinah.deen.domain.astronomy

import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.PrayerAdjustments
import com.sakinah.deen.data.model.PrayerType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

data class PrayerCountdownInfo(
    val currentPrayer: PrayerType,
    val nextPrayer: PrayerType,
    val remainingMinutes: Long,
    val remainingSeconds: Long,
    val elapsedRatio: Float
)

interface PrayerCalculationEngine {
    fun getPrayerTimes(
        coordinates: Pair<Double, Double>,
        date: LocalDate,
        method: CalculationMethod,
        madhhab: Madhhab,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        adjustments: PrayerAdjustments = PrayerAdjustments()
    ): Map<PrayerType, LocalTime>

    fun getQiblaAzimuth(coordinates: Pair<Double, Double>): Double

    fun calculateCountdown(
        prayerTimes: Map<PrayerType, LocalTime>,
        currentTime: LocalTime
    ): PrayerCountdownInfo

    /**
     * Start of the last third of the night — the Tahajjud window — between this evening's
     * Maghrib and the following day's Fajr: `maghrib + 2/3 * (nextFajr - maghrib)`.
     *
     * Handles the midnight wrap, so an Isha/Maghrib near 20:00 with Fajr near 04:30
     * resolves to roughly 01:50 rather than a negative time.
     */
    fun lastThirdOfNight(maghrib: LocalTime, nextFajr: LocalTime): LocalTime
}
