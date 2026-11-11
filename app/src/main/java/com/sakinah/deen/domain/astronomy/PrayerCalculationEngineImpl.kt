package com.sakinah.deen.domain.astronomy

import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.PrayerType
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

class PrayerCalculationEngineImpl : PrayerCalculationEngine {

    companion object {
        private const val KAABA_LATITUDE = 21.422487
        private const val KAABA_LONGITUDE = 39.826206
    }

    override fun getQiblaAzimuth(coordinates: Pair<Double, Double>): Double {
        val lat1 = Math.toRadians(coordinates.first)
        val lon1 = Math.toRadians(coordinates.second)
        val lat2 = Math.toRadians(KAABA_LATITUDE)
        val lon2 = Math.toRadians(KAABA_LONGITUDE)

        val deltaLon = lon2 - lon1
        val y = sin(deltaLon)
        val x = cos(lat1) * tan(lat2) - sin(lat1) * cos(deltaLon)
        val qiblaRad = atan2(y, x)
        val qiblaDeg = Math.toDegrees(qiblaRad)
        return (qiblaDeg + 360.0) % 360.0
    }

    override fun getPrayerTimes(
        coordinates: Pair<Double, Double>,
        date: LocalDate,
        method: CalculationMethod,
        madhhab: Madhhab
    ): Map<PrayerType, LocalTime> {
        val lat = coordinates.first
        val lon = coordinates.second
        val dayOfYear = date.dayOfYear

        // Astronomical Solar Declination & Equation of Time
        val gamma = 2.0 * PI / 365.0 * (dayOfYear - 1)
        val eqtime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma)) // minutes
        val declRad = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
                0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
                0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)

        val latRad = Math.toRadians(lat)

        // Solar Noon in UTC hours
        val timeZoneOffset = Math.round(lon / 15.0).toDouble()
        val solarNoonUtcMinutes = 720.0 - (4.0 * lon) - eqtime
        val solarNoonHours = (solarNoonUtcMinutes / 60.0) + timeZoneOffset

        // Dhuhr (Solar noon + 2 min safety buffer)
        val dhuhrHours = solarNoonHours + (2.0 / 60.0)

        // Sunrise & Sunset angle: -0.8333 degrees
        val sunAltRad = Math.toRadians(-0.8333)
        val cosHourAngleSun = (sin(sunAltRad) - sin(latRad) * sin(declRad)) / (cos(latRad) * cos(declRad))
        val hourAngleSunDeg = if (cosHourAngleSun in -1.0..1.0) Math.toDegrees(acos(cosHourAngleSun)) else 90.0
        val halfDayHours = hourAngleSunDeg / 15.0

        val sunriseHours = solarNoonHours - halfDayHours
        val maghribHours = solarNoonHours + halfDayHours

        // Fajr angle depending on calculation authority
        val fajrAngle = when (method) {
            CalculationMethod.MUSLIM_WORLD_LEAGUE -> 18.0
            CalculationMethod.EGYPTIAN -> 19.5
            CalculationMethod.KARACHI -> 18.0
            CalculationMethod.UMM_AL_QURA -> 18.5
            CalculationMethod.NORTH_AMERICA -> 15.0
        }
        val fajrAltRad = Math.toRadians(-fajrAngle)
        val cosHourAngleFajr = (sin(fajrAltRad) - sin(latRad) * sin(declRad)) / (cos(latRad) * cos(declRad))
        val hourAngleFajrDeg = if (cosHourAngleFajr in -1.0..1.0) Math.toDegrees(acos(cosHourAngleFajr)) else 108.0
        val fajrHours = solarNoonHours - (hourAngleFajrDeg / 15.0)

        // Asr juristic shadow calculation: Standard (1:1) vs Hanafi (2:1)
        val shadowRatio = if (madhhab == Madhhab.HANAFI) 2.0 else 1.0
        val zenithDiff = Math.abs(latRad - declRad)
        val asrAltRad = atan(1.0 / (shadowRatio + tan(zenithDiff)))
        val cosHourAngleAsr = (sin(asrAltRad) - sin(latRad) * sin(declRad)) / (cos(latRad) * cos(declRad))
        val hourAngleAsrDeg = if (cosHourAngleAsr in -1.0..1.0) Math.toDegrees(acos(cosHourAngleAsr)) else 60.0
        val asrHours = solarNoonHours + (hourAngleAsrDeg / 15.0)

        // Isha calculation
        val ishaHours = if (method == CalculationMethod.UMM_AL_QURA) {
            maghribHours + 1.5 // 90 minutes after Maghrib
        } else {
            val ishaAngle = when (method) {
                CalculationMethod.MUSLIM_WORLD_LEAGUE -> 17.0
                CalculationMethod.EGYPTIAN -> 17.5
                CalculationMethod.KARACHI -> 18.0
                CalculationMethod.NORTH_AMERICA -> 15.0
                else -> 17.0
            }
            val ishaAltRad = Math.toRadians(-ishaAngle)
            val cosHourAngleIsha = (sin(ishaAltRad) - sin(latRad) * sin(declRad)) / (cos(latRad) * cos(declRad))
            val hourAngleIshaDeg = if (cosHourAngleIsha in -1.0..1.0) Math.toDegrees(acos(cosHourAngleIsha)) else 105.0
            solarNoonHours + (hourAngleIshaDeg / 15.0)
        }

        return mapOf(
            PrayerType.FAJR to hoursToLocalTime(fajrHours),
            PrayerType.SUNRISE to hoursToLocalTime(sunriseHours),
            PrayerType.DHUHR to hoursToLocalTime(dhuhrHours),
            PrayerType.ASR to hoursToLocalTime(asrHours),
            PrayerType.MAGHRIB to hoursToLocalTime(maghribHours),
            PrayerType.ISHA to hoursToLocalTime(ishaHours)
        )
    }

    override fun calculateCountdown(
        prayerTimes: Map<PrayerType, LocalTime>,
        currentTime: LocalTime
    ): PrayerCountdownInfo {
        val fajr = prayerTimes[PrayerType.FAJR] ?: LocalTime.of(5, 15)
        val sunrise = prayerTimes[PrayerType.SUNRISE] ?: LocalTime.of(6, 40)
        val dhuhr = prayerTimes[PrayerType.DHUHR] ?: LocalTime.of(12, 45)
        val asr = prayerTimes[PrayerType.ASR] ?: LocalTime.of(15, 50)
        val maghrib = prayerTimes[PrayerType.MAGHRIB] ?: LocalTime.of(18, 20)
        val isha = prayerTimes[PrayerType.ISHA] ?: LocalTime.of(19, 45)

        val (current, next, windowStart, windowEnd) = when {
            currentTime.isBefore(fajr) -> {
                Quadruple(PrayerType.ISHA, PrayerType.FAJR, isha, fajr)
            }
            currentTime.isBefore(sunrise) -> {
                Quadruple(PrayerType.FAJR, PrayerType.SUNRISE, fajr, sunrise)
            }
            currentTime.isBefore(dhuhr) -> {
                Quadruple(PrayerType.SUNRISE, PrayerType.DHUHR, sunrise, dhuhr)
            }
            currentTime.isBefore(asr) -> {
                Quadruple(PrayerType.DHUHR, PrayerType.ASR, dhuhr, asr)
            }
            currentTime.isBefore(maghrib) -> {
                Quadruple(PrayerType.ASR, PrayerType.MAGHRIB, asr, maghrib)
            }
            currentTime.isBefore(isha) -> {
                Quadruple(PrayerType.MAGHRIB, PrayerType.ISHA, maghrib, isha)
            }
            else -> {
                Quadruple(PrayerType.ISHA, PrayerType.FAJR, isha, fajr)
            }
        }

        val remainingSecs = if (currentTime.isBefore(windowEnd)) {
            Duration.between(currentTime, windowEnd).seconds
        } else {
            val toMidnight = Duration.between(currentTime, LocalTime.MAX).seconds
            val fromMidnight = Duration.between(LocalTime.MIN, windowEnd).seconds
            toMidnight + fromMidnight
        }

        val totalWindowSecs = if (windowStart.isBefore(windowEnd)) {
            Duration.between(windowStart, windowEnd).seconds.coerceAtLeast(1)
        } else {
            val toMidnight = Duration.between(windowStart, LocalTime.MAX).seconds
            val fromMidnight = Duration.between(LocalTime.MIN, windowEnd).seconds
            (toMidnight + fromMidnight).coerceAtLeast(1)
        }

        val elapsedSecs = totalWindowSecs - remainingSecs
        val ratio = (elapsedSecs.toFloat() / totalWindowSecs.toFloat()).coerceIn(0f, 1f)

        return PrayerCountdownInfo(
            currentPrayer = current,
            nextPrayer = next,
            remainingMinutes = remainingSecs / 60,
            remainingSeconds = remainingSecs % 60,
            elapsedRatio = ratio
        )
    }

    private fun hoursToLocalTime(hoursDouble: Double): LocalTime {
        var h = (hoursDouble % 24.0)
        if (h < 0) h += 24.0
        val hours = h.toInt().coerceIn(0, 23)
        val minutesDouble = (h - hours) * 60.0
        val minutes = minutesDouble.toInt().coerceIn(0, 59)
        val seconds = ((minutesDouble - minutes) * 60.0).toInt().coerceIn(0, 59)
        return LocalTime.of(hours, minutes, seconds)
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}
