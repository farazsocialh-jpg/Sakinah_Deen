package com.sakinah.deen.widget

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPaletteTest {

    @Test
    fun `every style resolves a full palette in light and dark`() {
        PrayerWindow.entries.forEach { window ->
            listOf(true, false).forEach { isDark ->
                WidgetStyle.entries.forEach { style ->
                    val colors = resolveWidgetColors(style, isDark, window)
                    assertNotEquals("background for $style", 0L, colors.primaryText)
                    assertNotEquals("accent for $style", 0L, colors.accent)
                    assertNotEquals("divider for $style", 0L, colors.divider)
                }
            }
        }
    }

    @Test
    fun `frosted glass uses the documented translucent surfaces`() {
        assertEquals(0xD9F7F9F8, resolveWidgetColors(WidgetStyle.FROSTED_GLASS, false, PrayerWindow.DHUHR).background)
        assertEquals(0xCC0F1715, resolveWidgetColors(WidgetStyle.FROSTED_GLASS, true, PrayerWindow.DHUHR).background)
        assertEquals(0xFF10B981, resolveWidgetColors(WidgetStyle.FROSTED_GLASS, false, PrayerWindow.DHUHR).accent)
        assertEquals(0xFF34D399, resolveWidgetColors(WidgetStyle.FROSTED_GLASS, true, PrayerWindow.DHUHR).accent)
    }

    @Test
    fun `monochrome is matte black and white with one emerald accent`() {
        val light = resolveWidgetColors(WidgetStyle.MONOCHROME, false, PrayerWindow.ISHA)
        val dark = resolveWidgetColors(WidgetStyle.MONOCHROME, true, PrayerWindow.ISHA)
        assertEquals(0xFFFFFFFF, light.background)
        assertEquals(0xFF0C0C0C, light.primaryText)
        assertEquals(0xFF0C0C0C, dark.background)
        assertEquals(0xFFFFFFFF, dark.primaryText)
        assertEquals(0xFF10B981, light.accent)
        assertEquals(0xFF10B981, dark.accent)
    }

    @Test
    fun `living sky shifts with every prayer window`() {
        val backgrounds = PrayerWindow.entries.map {
            resolveWidgetColors(WidgetStyle.LIVING_SKY, false, it).background
        }
        assertEquals(5, backgrounds.distinct().size)
        assertEquals(0xFF2B4C7E, resolveWidgetColors(WidgetStyle.LIVING_SKY, false, PrayerWindow.FAJR).background)
        assertEquals(0xFF1D6FA5, resolveWidgetColors(WidgetStyle.LIVING_SKY, false, PrayerWindow.DHUHR).background)
        assertEquals(0xFF9A5B25, resolveWidgetColors(WidgetStyle.LIVING_SKY, false, PrayerWindow.ASR).background)
        assertEquals(0xFF4C2A6A, resolveWidgetColors(WidgetStyle.LIVING_SKY, false, PrayerWindow.MAGHRIB).background)
        assertEquals(0xFF131B3A, resolveWidgetColors(WidgetStyle.LIVING_SKY, false, PrayerWindow.ISHA).background)
    }

    @Test
    fun `transparent keeps no surface but keeps contrast`() {
        val light = resolveWidgetColors(WidgetStyle.TRANSPARENT, false, PrayerWindow.DHUHR)
        val dark = resolveWidgetColors(WidgetStyle.TRANSPARENT, true, PrayerWindow.DHUHR)
        assertEquals(0L, light.background)
        assertEquals(0L, dark.background)
        assertNotEquals(light.primaryText, dark.primaryText)
        assertNotEquals(0L, light.divider)
    }

    @Test
    fun `theme mode overrides only when it is not system`() {
        assertTrue(resolveDarkMode(WidgetThemeMode.SYSTEM, true))
        assertFalse(resolveDarkMode(WidgetThemeMode.SYSTEM, false))
        assertFalse(resolveDarkMode(WidgetThemeMode.LIGHT, true))
        assertTrue(resolveDarkMode(WidgetThemeMode.DARK, false))
    }

    @Test
    fun `living sky follows the prayer whose window has begun`() {
        val times = mapOf(
            com.sakinah.deen.data.model.PrayerType.FAJR to LocalTime.of(5, 0),
            com.sakinah.deen.data.model.PrayerType.DHUHR to LocalTime.of(12, 30),
            com.sakinah.deen.data.model.PrayerType.ASR to LocalTime.of(16, 0),
            com.sakinah.deen.data.model.PrayerType.MAGHRIB to LocalTime.of(18, 30),
            com.sakinah.deen.data.model.PrayerType.ISHA to LocalTime.of(20, 0)
        )
        assertEquals(PrayerWindow.ISHA, currentPrayerWindow(times, LocalTime.of(4, 0)))
        assertEquals(PrayerWindow.FAJR, currentPrayerWindow(times, LocalTime.of(5, 1)))
        assertEquals(PrayerWindow.DHUHR, currentPrayerWindow(times, LocalTime.of(13, 0)))
        assertEquals(PrayerWindow.ASR, currentPrayerWindow(times, LocalTime.of(17, 0)))
        assertEquals(PrayerWindow.MAGHRIB, currentPrayerWindow(times, LocalTime.of(19, 0)))
        assertEquals(PrayerWindow.ISHA, currentPrayerWindow(times, LocalTime.of(22, 0)))
    }
}

class WidgetCalendarTest {

    @Test
    fun `hijri conversion yields a valid day month year`() {
        val hijri = hijriFor(LocalDate.of(2026, 11, 19))
        assertTrue(hijri.day in 1..30)
        assertTrue(hijri.month in 1..12)
        assertTrue(hijri.year > 1400)
        assertTrue(hijri.label.endsWith("AH"))
        assertTrue(hijri.monthName.isNotBlank())
    }

    @Test
    fun `hijri offset shifts the day forward`() {
        val base = hijriFor(LocalDate.of(2026, 11, 19), 0)
        val shifted = hijriFor(LocalDate.of(2026, 11, 19), 1)
        val expected = hijriFor(LocalDate.of(2026, 11, 20), 0)
        assertEquals(expected.label, shifted.label)
        assertNotEquals(base.label, shifted.label)
    }

    @Test
    fun `next islamic event is inside one hijri year`() {
        val today = LocalDate.of(2026, 11, 19)
        val event = nextIslamicEvent(today)
        assertTrue(event.daysAway in 0..354)
        assertTrue(event.name in setOf(
            "Ramadan begins", "Laylatul Qadr", "Eid al-Fitr",
            "Day of Arafah", "Eid al-Adha", "Ashura"
        ))
        assertFalse(event.date.isBefore(today))
        assertTrue(event.hijriLabel.endsWith("AH"))
    }

    @Test
    fun `islamic countdown never lands in the past`() {
        var day = LocalDate.of(2026, 1, 1)
        repeat(60) {
            val event = nextIslamicEvent(day)
            assertTrue(event.date >= day)
            assertTrue(event.daysAway >= 0)
            day = day.plusDays(7)
        }
    }

    @Test
    fun `sunnah fast finds monday or thursday and white days`() {
        // 2026-11-19 is a Thursday.
        val thursday = LocalDate.of(2026, 11, 19)
        val fast = nextSunnahFast(thursday)
        assertEquals(0, fast.daysAway)
        assertTrue(fast.title.contains("Thursday"))

        // A white day always resolves within two weeks.
        val other = LocalDate.of(2026, 11, 21)
        val next = nextSunnahFast(other)
        assertTrue(next.daysAway in 0..14)
    }

    @Test
    fun `last third of the night is two thirds after maghrib`() {
        assertEquals(
            LocalTime.of(2, 40),
            lastThirdStart(LocalTime.of(22, 0), LocalTime.of(5, 0))
        )
        assertEquals(
            LocalTime.of(3, 0),
            lastThirdStart(LocalTime.of(21, 0), LocalTime.of(6, 0))
        )
    }

    @Test
    fun `duha window opens after sunrise and closes at dhuhr`() {
        val sunrise = LocalTime.of(6, 30)
        val dhuhr = LocalTime.of(12, 30)
        assertFalse(duhaStatus(LocalTime.of(5, 0), sunrise, dhuhr).isOpen)
        assertTrue(duhaStatus(LocalTime.of(9, 0), sunrise, dhuhr).isOpen)
        assertFalse(duhaStatus(LocalTime.of(14, 0), sunrise, dhuhr).isOpen)
    }

    @Test
    fun `countdown wraps past midnight and formats`() {
        assertEquals(20L, minutesUntil(LocalTime.of(23, 50), LocalTime.of(0, 10)))
        assertEquals("1h 42m left", countdownLabel(102))
        assertEquals("7m left", countdownLabel(7))
        assertEquals("now", countdownLabel(0))
    }

    @Test
    fun `streak counts consecutive days ending today`() {
        val today = 20_000L
        assertEquals(0, streakFromDays(emptySet(), today))
        assertEquals(3, streakFromDays(setOf(today, today - 1, today - 2), today))
        assertEquals(2, streakFromDays(setOf(today - 1, today - 2), today))
        assertEquals(1, streakFromDays(setOf(today, today - 2), today))
    }

    @Test
    fun `salah toggle plan inserts once then flips completion`() {
        val first = planPrayerToggle(null)
        assertTrue(first.insert)
        assertTrue(first.nextCompleted)

        val clear = planPrayerToggle(true)
        assertFalse(clear.insert)
        assertFalse(clear.nextCompleted)

        val restore = planPrayerToggle(false)
        assertFalse(restore.insert)
        assertTrue(restore.nextCompleted)
    }

    @Test
    fun `catalog ships exactly the six streamlined widgets`() {
        val receivers = WidgetCatalog.entries.map { it.receiverClassName }
        assertEquals(6, receivers.size)
        assertEquals(receivers.size, receivers.distinct().size)
        assertEquals(
            setOf(
                "SalahTrackerWidgetReceiver",
                "TasbihWidgetReceiver",
                "DailyAyahWidgetReceiver",
                "HijriDateWidgetReceiver",
                "QiblaWidgetReceiver",
                "AiScholarWidgetReceiver"
            ),
            receivers.toSet()
        )
        WidgetCatalog.entries.forEach { entry ->
            assertNotNull(
                "missing receiver mapping for ${entry.receiverClassName}",
                WidgetCatalog.widgetForReceiver("com.sakinah.deen.widget.${entry.receiverClassName}")
            )
        }
        assertNull(
            WidgetCatalog.widgetForReceiver("com.sakinah.deen.widget.NextPrayerWidgetReceiver")
        )
    }

    @Test
    fun `every widget declares the four responsive buckets`() {
        assertEquals(4, WidgetSizes.ALL.size)
        assertEquals(WidgetShape.COMPACT, shapeOf(WidgetSizes.COMPACT))
        assertEquals(WidgetShape.WIDE, shapeOf(WidgetSizes.WIDE))
        assertEquals(WidgetShape.SQUARE, shapeOf(WidgetSizes.SQUARE))
        assertEquals(WidgetShape.EXPANDED, shapeOf(WidgetSizes.EXPANDED))
    }

    @Test
    fun `frosted and transparent accents follow the active app palette`() {
        val override = 0xFF123456
        assertEquals(
            override,
            resolveWidgetColors(WidgetStyle.FROSTED_GLASS, false, PrayerWindow.DHUHR, override).accent
        )
        assertEquals(
            override,
            resolveWidgetColors(WidgetStyle.TRANSPARENT, true, PrayerWindow.DHUHR, override).accent
        )
        // Living Sky keeps its own per-window accent so the sky identity survives.
        assertEquals(
            0xFFFDE68A,
            resolveWidgetColors(WidgetStyle.LIVING_SKY, false, PrayerWindow.FAJR, override).accent
        )
    }

    @Test
    fun `ayah rotation walks the curated list and wraps both ways`() {
        assertTrue(curatedAyahCount >= 12)
        val seen = (0 until curatedAyahCount).map { curatedAyahFor(it) }
        assertEquals(curatedAyahCount, seen.distinct().size)
        assertTrue(seen.all { it.reference.contains(":") })
        assertTrue(seen.all { it.surahName.isNotBlank() })
        assertNotEquals(curatedAyahFor(0), curatedAyahFor(1))
        assertEquals(curatedAyahFor(0), curatedAyahFor(curatedAyahCount))
        assertEquals(curatedAyahFor(curatedAyahCount - 1), curatedAyahFor(-1))
    }

    @Test
    fun `compact ayah teaser cuts on a word boundary`() {
        val long = curatedAyahFor(4)
        val snippet = long.snippet(maxChars = 30)
        assertTrue(snippet.endsWith("…"))
        val body = snippet.removeSuffix("…")
        assertTrue(long.translation.startsWith(body))
        assertEquals(' ', long.translation[body.length])

        val short = DailyAyah("1:1", "Al-Fatihah", "In the name of Allah.")
        assertEquals("In the name of Allah.", short.snippet(maxChars = 40))
        assertEquals("Al-Fatihah · 1:1", short.surahLabel())
    }
}
