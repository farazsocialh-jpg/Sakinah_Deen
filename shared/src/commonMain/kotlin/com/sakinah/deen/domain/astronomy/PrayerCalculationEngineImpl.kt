package com.sakinah.deen.domain.astronomy

import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.PrayerAdjustments
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.settings.HighLatitudeRule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

class PrayerCalculationEngineImpl : PrayerCalculationEngine {

    companion object {
        private const val KAABA_LATITUDE = 21.422487
        private const val KAABA_LONGITUDE = 39.826206
        private const val SECONDS_PER_DAY = 24 * 60 * 60
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
        adjustments: PrayerAdjustments,
        highLatitudeRule: HighLatitudeRule
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
        // Local-minus-UTC for this date, taken via the zone's own civil time so DST is
        // respected. Subtracting in the other order shifted every timing by twice the zone
        // offset (~6h in Asia/Qatar).
        val localNoon = noonUtcInstant.toLocalDateTime(timeZone)
        val localNoonAsUtc = LocalDateTime(
            localNoon.year,
            localNoon.monthNumber,
            localNoon.dayOfMonth,
            localNoon.hour,
            localNoon.minute,
            localNoon.second
        ).toInstant(TimeZone.UTC)
        val zoneOffsetHours = (localNoonAsUtc.epochSeconds - noonUtcInstant.epochSeconds) / 3600.0

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

        // 3b. High-latitude fallback. Above roughly 48° in summer the sun never dips to the
        // Fajr/Isha angle, so an angle-based time does not exist for that day. The three
        // classical rules split the night between sunset and sunrise instead:
        //   middle of the night  -> half the night on either side of midnight
        //   one seventh          -> 1/7 of the night (the stricter Hanafi-era practice)
        //   angle based          -> the twilight angle expressed as a fraction of 60°
        // Night length is taken from this day's own sunset/sunrise, so it follows the season.
        val nightHours = (24.0 - 2.0 * halfDayHours).coerceIn(1.0, 23.0)
        fun portionOfNightFor(angle: Double): Double = when (highLatitudeRule) {
            HighLatitudeRule.MIDDLE_OF_NIGHT -> 0.5
            HighLatitudeRule.SEVENTH_OF_NIGHT -> 1.0 / 7.0
            HighLatitudeRule.TWILIGHT_ANGLE -> (angle / 60.0).coerceIn(1.0 / 12.0, 0.5)
        }

        // 4. Fajr Angle by Juristic / Regional Authority
        val fajrAngle = when (method) {
            CalculationMethod.MUSLIM_WORLD_LEAGUE -> 18.0
            CalculationMethod.EGYPTIAN -> 19.5
            CalculationMethod.KARACHI -> 18.0
            CalculationMethod.UMM_AL_QURA -> 18.5
            CalculationMethod.DUBAI -> 18.2
            CalculationMethod.QATAR -> 18.0
            CalculationMethod.NORTH_AMERICA -> 15.0
            CalculationMethod.KUWAIT -> 18.0
            CalculationMethod.GULF_REGION -> 18.2
            CalculationMethod.DIYANET -> 18.0
            CalculationMethod.JAKIM -> 20.0
            CalculationMethod.MUIS -> 20.0
            CalculationMethod.KEMENAG -> 20.0
            CalculationMethod.MOONSIGHTING_COMMITTEE -> 18.0
            CalculationMethod.UOIF -> 12.0
            CalculationMethod.SAMR -> 16.0
            CalculationMethod.JORDAN -> 18.0
            CalculationMethod.TUNISIA -> 18.0
            CalculationMethod.TEHRAN -> 17.7
        }
        val fajrAltRad = -fajrAngle * PI / 180.0
        val cosHourAngleFajr = (sin(fajrAltRad) - sin(latRad) * sin(declRad)) / (cos(latRad) * cos(declRad))
        val fajrHours = if (cosHourAngleFajr in -1.0..1.0) {
            solarNoonLocalHours - (acos(cosHourAngleFajr) * 180.0 / PI / 15.0)
        } else {
            // No astronomical Fajr today: fall back to the configured high-latitude rule.
            sunriseHours - portionOfNightFor(fajrAngle) * nightHours
        }

        // 5. Asr Juristic Shadow Calculation: Standard Shafi'i/Maliki/Hanbali (1:1) vs Hanafi (2:1)
        val shadowRatio = if (madhhab == Madhhab.HANAFI) 2.0 else 1.0
        val zenithDiff = abs(latRad - declRad)
        val asrAltRad = atan(1.0 / (shadowRatio + tan(zenithDiff)))
        val cosHourAngleAsr = (sin(asrAltRad) - sin(latRad) * sin(declRad)) / (cos(latRad) * cos(declRad))
        val hourAngleAsrDeg = if (cosHourAngleAsr in -1.0..1.0) acos(cosHourAngleAsr) * 180.0 / PI else 60.0
        val asrHours = solarNoonLocalHours + (hourAngleAsrDeg / 15.0)

        // 6. Isha Angle / Interval Calculation
        val ishaHours = when (method) {
            CalculationMethod.UMM_AL_QURA,
            CalculationMethod.QATAR,
            CalculationMethod.KUWAIT,
            CalculationMethod.GULF_REGION,
            CalculationMethod.JAKIM,
            CalculationMethod.MUIS,
            CalculationMethod.KEMENAG,
            CalculationMethod.JORDAN -> {
                // Fixed 90-minute interval after Maghrib
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
                    CalculationMethod.DIYANET -> 17.0
                    CalculationMethod.MOONSIGHTING_COMMITTEE -> 18.0
                    CalculationMethod.UOIF -> 12.0
                    CalculationMethod.SAMR -> 15.0
                    CalculationMethod.TUNISIA -> 18.0
                    CalculationMethod.TEHRAN -> 14.0
                }
                val ishaAltRad = -ishaAngle * PI / 180.0
                val cosHourAngleIsha = (sin(ishaAltRad) - sin(latRad) * sin(declRad)) / (cos(latRad) * cos(declRad))
                if (cosHourAngleIsha in -1.0..1.0) {
                    solarNoonLocalHours + (acos(cosHourAngleIsha) * 180.0 / PI / 15.0)
                } else {
                    // No astronomical Isha today: use the same rule as Fajr, applied after sunset.
                    maghribHours + portionOfNightFor(ishaAngle) * nightHours
                }
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
        val sunrisePlus15Secs = (sunrise.toSecondOfDay() + 15 * 60) % SECONDS_PER_DAY
        val sunrisePlus15 = LocalTime(
            sunrisePlus15Secs / 3600,
            (sunrisePlus15Secs % 3600) / 60,
            sunrisePlus15Secs % 60
        )
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
            currentTime < sunrisePlus15 -> {
                // Prohibited sunrise window: sunrise .. sunrise+15m.
                Quadruple(PrayerType.SUNRISE, PrayerType.DHUHR, sunrise, sunrisePlus15)
            }
            currentTime < dhuhr -> {
                // Duha/Ishraq window: sunrise no longer active; target Dhuhr.
                Quadruple(PrayerType.SUNRISE, PrayerType.DHUHR, sunrisePlus15, dhuhr)
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

    /** 2/3 of the way from Maghrib to the next Fajr, i.e. the start of the last third. */
    override fun lastThirdOfNight(maghrib: LocalTime, nextFajr: LocalTime): LocalTime {
        var nightSeconds = nextFajr.toSecondOfDay() - maghrib.toSecondOfDay()
        if (nightSeconds <= 0) nightSeconds += SECONDS_PER_DAY
        val startSeconds = (maghrib.toSecondOfDay() + (nightSeconds * 2) / 3) % SECONDS_PER_DAY
        return LocalTime(startSeconds / 3600, (startSeconds % 3600) / 60, startSeconds % 60)
    }

    /** Midpoint of the night between Maghrib and the next Fajr: Islamic midnight. */
    override fun islamicMidnight(maghrib: LocalTime, nextFajr: LocalTime): LocalTime {
        var nightSeconds = nextFajr.toSecondOfDay() - maghrib.toSecondOfDay()
        if (nightSeconds <= 0) nightSeconds += SECONDS_PER_DAY
        val midSeconds = (maghrib.toSecondOfDay() + nightSeconds / 2) % SECONDS_PER_DAY
        return LocalTime(midSeconds / 3600, (midSeconds % 3600) / 60, midSeconds % 60)
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
