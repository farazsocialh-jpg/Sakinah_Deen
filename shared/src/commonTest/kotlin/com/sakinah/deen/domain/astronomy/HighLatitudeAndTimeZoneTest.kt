package com.sakinah.deen.domain.astronomy

import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.PrayerAdjustments
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.settings.HighLatitudeRule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The two clock bugs this suite pins down:
 *
 *  - Above ~48° in summer the sun never reaches the Fajr/Isha twilight angle, so an
 *    angle-based time does not exist. The configured high-latitude rule must decide the
 *    split of the night instead of the old arbitrary 108°/105° guess.
 *  - The zone offset must come from the zone's own civil time for that date, so a DST
 *    rollover moves every wall-clock time by an hour exactly once.
 */
class HighLatitudeAndTimeZoneTest {

    private val engine = PrayerCalculationEngineImpl()

    private fun times(
        coordinates: Pair<Double, Double>,
        date: LocalDate,
        zone: TimeZone = TimeZone.UTC,
        rule: HighLatitudeRule = HighLatitudeRule.MIDDLE_OF_NIGHT,
        method: CalculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,
        madhhab: Madhhab = Madhhab.SHAFI_HANBALI_MALIKI
    ) = engine.getPrayerTimes(
        coordinates = coordinates,
        date = date,
        method = method,
        madhhab = madhhab,
        timeZone = zone,
        adjustments = PrayerAdjustments(),
        highLatitudeRule = rule
    )

    private fun minutesBetween(from: LocalTime, to: LocalTime): Int {
        var diff = to.toSecondOfDay() - from.toSecondOfDay()
        if (diff < 0) diff += 24 * 60 * 60
        return diff / 60
    }

    private fun LocalTime.asMinutes(): Int = hour * 60 + minute

    @Test
    fun midnightSunStillProducesEveryPrayer() {
        // Tromsø, midsummer: the sun never sets, so no twilight angle is reachable.
        val table = times(Pair(69.6492, 18.9553), LocalDate(2026, 6, 21))
        assertEquals(6, table.size)

        val fajr = table.getValue(PrayerType.FAJR)
        val sunrise = table.getValue(PrayerType.SUNRISE)
        val maghrib = table.getValue(PrayerType.MAGHRIB)
        val isha = table.getValue(PrayerType.ISHA)

        // Fajr precedes sunrise and Isha follows sunset.
        assertTrue(minutesBetween(fajr, sunrise) in 1..24 * 60, "Fajr $fajr must precede sunrise $sunrise")
        assertTrue(minutesBetween(maghrib, isha) in 1..24 * 60, "Isha $isha must follow maghrib $maghrib")
    }

    @Test
    fun seventhOfNightIsStricterThanMiddleOfNight() {
        val coords = Pair(64.1466, -21.9426) // Reykjavík, near-midnight-sun summer
        val date = LocalDate(2026, 6, 21)

        val middle = times(coords, date, rule = HighLatitudeRule.MIDDLE_OF_NIGHT)
        val seventh = times(coords, date, rule = HighLatitudeRule.SEVENTH_OF_NIGHT)
        val angle = times(coords, date, rule = HighLatitudeRule.TWILIGHT_ANGLE)

        // One seventh of the night is a smaller portion than a half, so Fajr lands later
        // and Isha earlier when the night is split that way.
        assertTrue(
            seventh.getValue(PrayerType.FAJR).asMinutes() > middle.getValue(PrayerType.FAJR).asMinutes(),
            "seventh-of-night Fajr should be later than middle-of-night"
        )
        assertTrue(
            seventh.getValue(PrayerType.ISHA).asMinutes() < middle.getValue(PrayerType.ISHA).asMinutes(),
            "seventh-of-night Isha should be earlier than middle-of-night"
        )

        // The angle rule is a distinct split (18/60 of the night for the MWL Fajr angle).
        assertNotEquals(angle.getValue(PrayerType.FAJR), middle.getValue(PrayerType.FAJR))
        assertNotEquals(angle.getValue(PrayerType.FAJR), seventh.getValue(PrayerType.FAJR))
    }

    @Test
    fun highLatitudeRuleIsIgnoredWhereTheSunDoesReachTheAngle() {
        // Doha in November: every angle is reachable, so the rule must not perturb the table.
        val coords = Pair(25.2854, 51.5310)
        val date = LocalDate(2026, 11, 12)
        val expected = times(coords, date, zone = TimeZone.of("Asia/Qatar"))

        HighLatitudeRule.entries.forEach { rule ->
            assertEquals(
                expected,
                times(coords, date, zone = TimeZone.of("Asia/Qatar"), rule = rule),
                "rule $rule changed an ordinary timetable"
            )
        }
    }

    @Test
    fun zoneOffsetFollowsDstForTheDate() {
        // London is on GMT in January and BST in July; the same solar noon is ~12:00 local
        // on both dates. A zone offset taken from the wrong instant jumps an hour.
        val coords = Pair(51.5074, -0.1278)
        val january = times(coords, LocalDate(2026, 1, 15), zone = TimeZone.of("Europe/London"))
        val july = times(coords, LocalDate(2026, 7, 15), zone = TimeZone.of("Europe/London"))

        val januaryNoon = january.getValue(PrayerType.DHUHR).asMinutes()
        val julyNoon = july.getValue(PrayerType.DHUHR).asMinutes()
        assertTrue(januaryNoon in 11 * 60 + 50..12 * 60 + 20, "January Dhuhr was $januaryNoon min")
        assertTrue(julyNoon in 12 * 60 + 50..13 * 60 + 20, "July Dhuhr was $julyNoon min")
        assertTrue(
            julyNoon - januaryNoon in 50..70,
            "the DST shift should be about an hour, was ${julyNoon - januaryNoon} min"
        )
    }

    @Test
    fun sameInstantInTwoZonesDiffersByTheZoneOffset() {
        val coords = Pair(25.2854, 51.5310)
        val date = LocalDate(2026, 11, 12)
        val utc = times(coords, date, zone = TimeZone.UTC)
        val qatar = times(coords, date, zone = TimeZone.of("Asia/Qatar"))

        // Qatar is a fixed UTC+3 zone: every wall-clock prayer is exactly three hours later.
        PrayerType.entries.forEach { prayer ->
            val shift = minutesBetween(utc.getValue(prayer), qatar.getValue(prayer))
            assertTrue(shift in 179..181, "$prayer shifted $shift min instead of 180")
        }
    }

    @Test
    fun dstRolloverDayKeepsTheSequenceInOrder() {
        // The night of the European DST change: 2026-03-29 01:00 GMT becomes 02:00 BST.
        val coords = Pair(51.5074, -0.1278)
        val zone = TimeZone.of("Europe/London")
        val before = times(coords, LocalDate(2026, 3, 28), zone = zone)
        val after = times(coords, LocalDate(2026, 3, 29), zone = zone)

        listOf(before, after).forEach { table ->
            assertTrue(table.getValue(PrayerType.FAJR) < table.getValue(PrayerType.SUNRISE))
            assertTrue(table.getValue(PrayerType.SUNRISE) < table.getValue(PrayerType.DHUHR))
            assertTrue(table.getValue(PrayerType.DHUHR) < table.getValue(PrayerType.ASR))
            assertTrue(table.getValue(PrayerType.ASR) < table.getValue(PrayerType.MAGHRIB))
            assertTrue(table.getValue(PrayerType.MAGHRIB) < table.getValue(PrayerType.ISHA))
        }
        // Consecutive days differ by minutes, never by the duplicated/missing DST hour.
        val fajrGap = minutesBetween(before.getValue(PrayerType.FAJR), after.getValue(PrayerType.FAJR))
        assertTrue(fajrGap < 60, "Fajr jumped $fajrGap min across the DST change")
    }
}
