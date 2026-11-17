package com.sakinah.deen.domain.astronomy

import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.PrayerType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fajr must hand over to Sunrise (Shuruq), not run until Dhuhr, and the Tahajjud window must
 * be the last third of the night between Maghrib and the next Fajr.
 */
class PrayerWindowAndTahajjudTest {

    private val engine = PrayerCalculationEngineImpl()

    private val times = mapOf(
        PrayerType.FAJR to LocalTime(5, 0),
        PrayerType.SUNRISE to LocalTime(6, 20),
        PrayerType.DHUHR to LocalTime(12, 15),
        PrayerType.ASR to LocalTime(15, 40),
        PrayerType.MAGHRIB to LocalTime(18, 10),
        PrayerType.ISHA to LocalTime(19, 40)
    )

    @Test
    fun countdown_handsFajrOverToSunriseThenDhuhr() {
        // Before Fajr the night's Isha is still current.
        engine.calculateCountdown(times, LocalTime(4, 0)).let { info ->
            assertEquals(PrayerType.ISHA, info.currentPrayer)
            assertEquals(PrayerType.FAJR, info.nextPrayer)
        }

        // During Fajr the next event is Sunrise: the Fajr window ends at Shuruq.
        engine.calculateCountdown(times, LocalTime(5, 30)).let { info ->
            assertEquals(PrayerType.FAJR, info.currentPrayer)
            assertEquals(PrayerType.SUNRISE, info.nextPrayer)
        }

        // At and after sunrise Fajr is over; the next fard prayer is Dhuhr.
        engine.calculateCountdown(times, LocalTime(6, 20)).let { info ->
            assertEquals(PrayerType.SUNRISE, info.currentPrayer)
            assertEquals(PrayerType.DHUHR, info.nextPrayer)
        }
        engine.calculateCountdown(times, LocalTime(7, 0)).let { info ->
            assertEquals(PrayerType.SUNRISE, info.currentPrayer)
            assertEquals(PrayerType.DHUHR, info.nextPrayer)
        }
        engine.calculateCountdown(times, LocalTime(12, 15)).let { info ->
            assertEquals(PrayerType.DHUHR, info.currentPrayer)
            assertEquals(PrayerType.ASR, info.nextPrayer)
        }
    }

    @Test
    fun lastThirdOfNight_isTwoThirdsAfterMaghrib() {
        // Night = 18:10 -> 05:00 = 10h50m; two thirds = 7h13m20s, so Tahajjud starts 01:23.
        assertEquals(
            LocalTime(1, 23, 20),
            engine.lastThirdOfNight(LocalTime(18, 10), LocalTime(5, 0))
        )
        // Night = 20:00 -> 04:30 = 8h30m; two thirds = 5h40m, so Tahajjud starts 01:40.
        assertEquals(
            LocalTime(1, 40),
            engine.lastThirdOfNight(LocalTime(20, 0), LocalTime(4, 30))
        )
        // Night = 22:00 -> 02:00 = 4h; two thirds = 2h40m, so Tahajjud starts 00:40.
        assertEquals(
            LocalTime(0, 40),
            engine.lastThirdOfNight(LocalTime(22, 0), LocalTime(2, 0))
        )
    }

    @Test
    fun prayerTimes_stayInAstronomicalOrder() {
        val table = engine.getPrayerTimes(
            coordinates = 25.2854 to 51.5310,
            date = LocalDate(2026, 3, 20),
            method = CalculationMethod.QATAR,
            madhhab = Madhhab.SHAFI_HANBALI_MALIKI,
            timeZone = kotlinx.datetime.TimeZone.of("Asia/Qatar")
        )

        val ordered = listOf(
            PrayerType.FAJR,
            PrayerType.SUNRISE,
            PrayerType.DHUHR,
            PrayerType.ASR,
            PrayerType.MAGHRIB,
            PrayerType.ISHA
        ).map { table.getValue(it) }

        assertTrue(
            ordered.zipWithNext().all { (earlier, later) -> earlier < later },
            "prayer times must increase through the day, was $ordered"
        )
    }

    @Test
    fun islamicMidnight_isHalfwayBetweenMaghribAndNextFajr() {
        // Night = 18:10 -> 05:00 = 10h50m; half = 5h25m, so midnight = 23:35.
        assertEquals(
            LocalTime(23, 35),
            engine.islamicMidnight(LocalTime(18, 10), LocalTime(5, 0))
        )
        // Night = 20:00 -> 04:30 = 8h30m; half = 4h15m, so midnight = 00:15.
        assertEquals(
            LocalTime(0, 15),
            engine.islamicMidnight(LocalTime(20, 0), LocalTime(4, 30))
        )
    }

    @Test
    fun sunrise_windowEndsAtSunrisePlus15Minutes() {
        // Sunrise 06:40 -> prohibited window ends 06:55.
        val times = mapOf(
            PrayerType.FAJR to LocalTime(5, 15),
            PrayerType.SUNRISE to LocalTime(6, 40),
            PrayerType.DHUHR to LocalTime(12, 45),
            PrayerType.ASR to LocalTime(15, 50),
            PrayerType.MAGHRIB to LocalTime(18, 20),
            PrayerType.ISHA to LocalTime(19, 45)
        )
        // During prohibited window, next prayer is Dhuhr.
        val info = engine.calculateCountdown(times, LocalTime(6, 45))
        assertEquals(PrayerType.SUNRISE, info.currentPrayer)
        assertEquals(PrayerType.DHUHR, info.nextPrayer)
        // After sunrise + 15m, next prayer still targets Dhuhr.
        val after = engine.calculateCountdown(times, LocalTime(7, 0))
        assertEquals(PrayerType.DHUHR, after.nextPrayer)
    }

    @Test
    fun worldwideMethods_produceOrderedPrayerTimes() {
        CalculationMethod.entries.forEach { method ->
            val table = engine.getPrayerTimes(
                coordinates = 25.2854 to 51.5310,
                date = LocalDate(2026, 3, 20),
                method = method,
                madhhab = Madhhab.SHAFI_HANBALI_MALIKI,
                timeZone = kotlinx.datetime.TimeZone.of("Asia/Qatar")
            )
            val ordered = listOf(
                PrayerType.FAJR,
                PrayerType.SUNRISE,
                PrayerType.DHUHR,
                PrayerType.ASR,
                PrayerType.MAGHRIB,
                PrayerType.ISHA
            ).map { table.getValue(it) }
            assertTrue(
                ordered.zipWithNext().all { (earlier, later) -> earlier < later },
                "method $method produced out-of-order times: $ordered"
            )
        }
    }
}
