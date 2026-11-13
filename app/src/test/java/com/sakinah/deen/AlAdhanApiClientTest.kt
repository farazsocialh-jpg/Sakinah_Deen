package com.sakinah.deen

import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prayer.AlAdhanApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Time-zone and `HH:mm` parsing edge cases for the AlAdhan calendar client. */
class AlAdhanApiClientTest {

    private val client = AlAdhanApiClient(
        client = HttpClient(MockEngine { respond("") })
    )

    @Test
    fun timestampFallback_usesTheLocationZoneNotUtc() {
        // 23:30 UTC is already the next calendar day in UTC+14.
        val timestamp = Instant.parse("2026-11-12T23:30:00Z").epochSecond
        val timetable = client.parse(monthlyJson(timeZone = "Pacific/Kiritimati", timestamp = timestamp))

        assertNotNull(timetable)
        assertEquals(LocalDate.of(2026, 11, 13), timetable!!.days.single().date)
    }

    @Test
    fun timestampFallback_missingTimeZoneFallsBackWithoutCrashing() {
        val timestamp = Instant.parse("2026-11-12T12:00:00Z").epochSecond
        val timetable = client.parse(monthlyJson(timeZone = null, timestamp = timestamp))

        assertNotNull(timetable)
        assertEquals(1, timetable!!.days.size)
    }

    @Test
    fun timestampFallback_invalidTimeZoneFallsBackWithoutCrashing() {
        val timestamp = Instant.parse("2026-11-12T12:00:00Z").epochSecond
        val timetable = client.parse(monthlyJson(timeZone = "Not/AZone", timestamp = timestamp))

        assertNotNull(timetable)
        assertEquals(1, timetable!!.days.size)
    }

    @Test
    fun timingsWithoutSpaceBeforeAnnotation_parseAsWallClockTimes() {
        val timetable = client.parse(
            monthlyJson(
                timeZone = "Asia/Riyadh",
                timestamp = Instant.parse("2026-11-12T12:00:00Z").epochSecond,
                fajr = "05:12(AST)"
            )
        )

        assertNotNull(timetable)
        assertEquals(LocalTime.of(5, 12), timetable!!.days.single().times[PrayerType.FAJR])
        assertEquals(LocalTime.of(6, 20), timetable.days.single().times[PrayerType.SUNRISE])
    }

    private fun monthlyJson(timeZone: String?, timestamp: Long, fajr: String = "04:24 (+03)"): String {
        val zoneField = timeZone?.let { """"timezone":"$it",""" }.orEmpty()
        val day = """
            {
              "timings":{
                "Fajr":"$fajr",
                "Sunrise":"06:20 (AST)",
                "Dhuhr":"11:45 (AST)",
                "Asr":"15:00 (AST)",
                "Maghrib":"18:10 (AST)",
                "Isha":"19:25 (AST)"
              },
              "date":{"timestamp":"$timestamp"},
              "meta":{$zoneField"method":{"id":4,"name":"Umm Al-Qura"}}
            }
        """.trimIndent()
        return """{"code":200,"status":"OK","data":[$day]}"""
    }
}
