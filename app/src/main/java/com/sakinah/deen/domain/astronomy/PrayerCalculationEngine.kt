package com.sakinah.deen.domain.astronomy

import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.PrayerAdjustments
import com.sakinah.deen.data.model.PrayerType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

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
        zoneId: ZoneId = ZoneId.systemDefault(),
        adjustments: PrayerAdjustments = PrayerAdjustments()
    ): Map<PrayerType, LocalTime>

    fun getQiblaAzimuth(coordinates: Pair<Double, Double>): Double

    fun calculateCountdown(
        prayerTimes: Map<PrayerType, LocalTime>,
        currentTime: LocalTime
    ): PrayerCountdownInfo
}
