package com.sakinah.deen.domain.astronomy

import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.PrayerAdjustments
import com.sakinah.deen.data.model.PrayerType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
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
        val lat1 = coordinates.first * PI / 180.0
        val lon1 = coordinates.second * PI / 180.0
        val lat2 = KAABA_LATITUDE * PI / 180.0
        val lon2 = KAABA_LONGITUDE * PI / 180.0

        val deltaLon = lon2 - lon1
        val y = sin(deltaLon)
        val x = cos(lat1) * tan(lat2) - sin(lat1) * cos(deltaLon)
        val qiblaRad = atan2(y, x)
        val qiblaDeg = qiblaRad * 180.0 / PI
        return (qiblaDeg + 360.0) % 360.0
    }

    override fun getPrayerTimes(
        coordinates: Pair<Double, Double>,
        date: LocalDate,
        method: CalculationMethod,
        madhhab: Madhhab,
        timeZone: TimeZone,
        adjustments: PrayerAdjustments
    ): Map<PrayerType, LocalTime> {
        val lat = coordinates.first
        val lon = coordinates.second
        val dayOfYear = date.dayOfYear

        // Astronomical Solar Declination & Equation of Time (Spencer 1971 / NOAA)
        val gamma = 2.0 * PI / 365.0 * (dayOfYear - 1)
        val eqtime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma)) // in minutes

        val declRad = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
                0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
                0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)

        val latRad = lat * PI / 180.0

        // 1. Calculate Exact Solar Noon in UTC Minutes
        // Solar noon occurs when Sun transits the local meridian: 12:00 UTC - (lon * 4 min/deg) - EoT
        val solarNoonUtcMinutes = 720.0 - (4.0 * lon) - eqtime

        // 2. Dynamic Local TimeZone Resolution: Use the exact device ZoneId offset for this date
        // Never estimate timezone using (lon / 15.0) which causes multi-hour errors in regions with non-solar political timezones!
        val noonUtcInstant = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 12, 0).toInstant(TimeZone.UTC)
        val localNoon = noonUtcInstant.toLocalDateTime(timeZone)
        val localAsUtc = LocalDateTime(localNoon.year, localNoon.monthNumber, localNoon.dayOfMonth, localNoon.hour, localNoon.minute, localNoon.second)
        val offsetSeconds = (noonUtcInstant.epochSeconds - localAsUtc.toInstant(TimeZone.UTC).epochSeconds).toInt()
        val zoneOffsetHours = offsetSeconds / 3600.0

        val solarNoonLocalHours = (solarNoonUtcMinutes / 60.0) + zoneOffsetHours

        // Dhuhr: Solar noon + 2 min safety buffer (Zawal passed)
        val dhuhrHours = solarNoonLocalHours + (2.0 / 60.0)

        // 3. Sunrise & Sunset Angle: -0.8333 degrees (atmospheric refraction + solar disc semi-diameter)
        val sunAltRad = -0.8333 * PI / 180.0
        val cosHourAngleSun = (sin(sunAltRad) - sin(latRad) * sin(declRad)) / (cos(latRad) * cos(declRad))
        val hourAngleSunDeg = if (cosHourAngleSun in -1.0..1.0) acos(cosHourAngleSun) * 180.0 / PI else 90.0
        val halfDayHours = hourAngleSunDeg / 15.0

        val sunriseHours = solarNoonLocalHours - halfDayHours
        val maghribHours = solarNoonLocalHours + halfDayHours

        // 4. Fajr Angle by Juristic / Regional Authority
        val fajrAngle = when (method) {
            CalculationMethod.MUSLIM_WORLD_LEAGUE -> 18.0
            CalculationMethod.EGYPTIAN -> 19.5
            CalculationMethod.KARACHI -> 18.0
            CalculationMethod.UMM_AL_QURA -> 18.5
            CalculationMethod.DUBAI -> 18.2
            CalculationMethod.QATAR -> 18.0
            CalculationMethod.NORTH_AMERICA -> 15.0
        }
        val fajrAltRad = -fajrAngle * PI / 180.0
        val cosHourAngleFajr = (sin(fajrAltRad) - sin(latRad) * sin(declRad)) / (cos(latRad) * cos(declRad))
        val hourAngleFajrDeg = if (cosHourAngleFajr in -1.0..1.0) acos(cosHourAngleFajr) * 180.0 / PI else 108.0
        val fajrHours = solarNoonLocalHours - (hourAngleFajrDeg / 15.0)

        // 5. Asr Juristic Shadow Calculation: Standard Shafi'i/Maliki/Hanbali (1:1) vs Hanafi (2:1)
        val shadowRatio = if (madhhab == Madhhab.HANAFI) 2.0 else 1.0
        val zenithDiff = abs(latRad - declRad)
        val asrAltRad = atan(1.0 / (shadowRatio + tan(zenithDiff)))
        val cosHourAngleAsr = (sin(asrAltRad) - sin(latRad) * sin(declRad)) / (cos(latRad) * cos(declRad))
        val hourAngleAsrDeg = if (cosHourAngleAsr in -1.0..1.0) acos(cosHourAngleAsr) * 180.0 / PI else 60.0
        val asrHours = solarNoonLocalHours + (hourAngleAsrDeg / 15.0)

        // 6. Isha Angle / Interval Calculation
        val ishaHours = when (method) {
            CalculationMethod.UMM_AL_QURA, CalculationMethod.QATAR -> {
                // Fixed 90-minute interval after Maghrib (120 minutes during Ramadan)
                maghribHours + (90.0 / 60.0)
            }
            CalculationMethod.DUBAI -> {
                maghribHours + (90.0 / 60.0)
            }
            else -> {
                val ishaAngle = when (method) {
                    CalculationMethod.MUSLIM_WORLD_LEAGUE -> 17.0
                    CalculationMethod.EGYPTIAN -> 17.5
                    CalculationMethod.KARACHI -> 18.0
                    CalculationMethod.NORTH_AMERICA -> 15.0
                    else -> 17.0
                }
                val ishaAltRad = -ishaAngle * PI / 180.0
                val cosHourAngleIsha = (sin(ishaAltRad) - sin(latRad) * sin(declRad)) / (cos(latRad) * cos(declRad))
                val hourAngleIshaDeg = if (cosHourAngleIsha in -1.0..1.0) acos(cosHourAngleIsha) * 180.0 / PI else 105.0
                solarNoonLocalHours + (hourAngleIshaDeg / 15.0)
            }
        }

        // Apply manual neighborhood mosque minute adjustments
        val rawFajr = hoursToLocalTime(fajrHours + adjustments.fajrMinutes / 60.0)
        val rawSunrise = hoursToLocalTime(sunriseHours + adjustments.sunriseMinutes / 60.0)
        val rawDhuhr = hoursToLocalTime(dhuhrHours + adjustments.dhuhrMinutes / 60.0)
        val rawAsr = hoursToLocalTime(asrHours + adjustments.asrMinutes / 60.0)
        val rawMaghrib = hoursToLocalTime(maghribHours + adjustments.maghribMinutes / 60.0)
        val rawIsha = hoursToLocalTime(ishaHours + adjustments.ishaMinutes / 60.0)

        return mapOf(
            PrayerType.FAJR to rawFajr,
            PrayerType.SUNRISE to rawSunrise,
            PrayerType.DHUHR to rawDhuhr,
            PrayerType.ASR to rawAsr,
            PrayerType.MAGHRIB to rawMaghrib,
            PrayerType.ISHA to rawIsha
        )
    }

    override fun calculateCountdown(
        prayerTimes: Map<PrayerType, LocalTime>,
        currentTime: LocalTime
    ): PrayerCountdownInfo {
        val fajr = prayerTimes[PrayerType.FAJR] ?: LocalTime(5, 15)
        val sunrise = prayerTimes[PrayerType.SUNRISE] ?: LocalTime(6, 40)
        val dhuhr = prayerTimes[PrayerType.DHUHR] ?: LocalTime(12, 45)
        val asr = prayerTimes[PrayerType.ASR] ?: LocalTime(15, 50)
        val maghrib = prayerTimes[PrayerType.MAGHRIB] ?: LocalTime(18, 20)
        val isha = prayerTimes[PrayerType.ISHA] ?: LocalTime(19, 45)

        val (current, next, windowStart, windowEnd) = when {
            currentTime < fajr -> {
                Quadruple(PrayerType.ISHA, PrayerType.FAJR, isha, fajr)
            }
            currentTime < sunrise -> {
                Quadruple(PrayerType.FAJR, PrayerType.SUNRISE, fajr, sunrise)
            }
            currentTime < dhuhr -> {
                Quadruple(PrayerType.SUNRISE, PrayerType.DHUHR, sunrise, dhuhr)
            }
            currentTime < asr -> {
                Quadruple(PrayerType.DHUHR, PrayerType.ASR, dhuhr, asr)
            }
            currentTime < maghrib -> {
                Quadruple(PrayerType.ASR, PrayerType.MAGHRIB, asr, maghrib)
            }
            currentTime < isha -> {
                Quadruple(PrayerType.MAGHRIB, PrayerType.ISHA, maghrib, isha)
            }
            else -> {
                Quadruple(PrayerType.ISHA, PrayerType.FAJR, isha, fajr)
            }
        }

        val remainingSecs = if (currentTime < windowEnd) {
            (windowEnd.toSecondOfDay() - currentTime.toSecondOfDay()).toLong()
        } else {
            (86400 - currentTime.toSecondOfDay() + windowEnd.toSecondOfDay()).toLong()
        }

        val totalWindowSecs = if (windowStart < windowEnd) {
            (windowEnd.toSecondOfDay() - windowStart.toSecondOfDay()).toLong().coerceAtLeast(1)
        } else {
            (86400 - windowStart.toSecondOfDay() + windowEnd.toSecondOfDay()).toLong().coerceAtLeast(1)
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
        return LocalTime(hours, minutes, seconds)
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}
