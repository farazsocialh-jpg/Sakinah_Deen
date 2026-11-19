package com.sakinah.deen.widget

import com.sakinah.deen.ui.theme.SakinahPalette
import com.sakinah.deen.ui.theme.ThemeMode
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
    fun `every style resolves a full palette for every app palette`() {
        SakinahPalette.entries.forEach { palette ->
            PrayerWindow.entries.forEach { window ->
                listOf(true, false).forEach { isDark ->
                    WidgetStyle.entries.forEach { style ->
                        val colors = resolveWidgetColors(style, isDark, window, palette)
                        assertNotEquals("text for $style", 0L, colors.primaryText)
                        assertNotEquals("accent for $style", 0L, colors.accent)
                        assertNotEquals("divider for $style", 0L, colors.divider)
                        assertNotEquals("onAccent for $style", 0L, colors.onAccent)
                    }
                }
            }
        }
    }

    @Test
    fun `frosted glass uses the active palette surface at 85 percent`() {
        val light = resolveWidgetColors(
            WidgetStyle.FROSTED_GLASS,
            isDark = false,
            window = PrayerWindow.DHUHR,
            palette = SakinahPalette.EMERALD_SANCTUARY
        )
        assertEquals(0xD8FFFFFF, light.background) // CardSurface at 85%
        assertEquals(0xFF0B3B2C, light.accent) // EmeraldDeep
        assertEquals(0xFF1E293B, light.primaryText) // onSurface
        assertEquals(0xFF526072, light.secondaryText) // onSurfaceVariant
        assertNotEquals(0L, light.border)

        val dark = resolveWidgetColors(
            WidgetStyle.FROSTED_GLASS,
            isDark = true,
            window = PrayerWindow.DHUHR,
            palette = SakinahPalette.EMERALD_SANCTUARY
        )
        assertEquals(0xD80E2920, dark.background) // NightSurface at 85%
        assertEquals(0xFF34D399, dark.accent) // NightMintAccent
        assertEquals(0xFFF1F5F9, dark.primaryText)
    }

    @Test
    fun `changing the palette changes the widget accent`() {
        val emerald = resolveWidgetColors(WidgetStyle.FROSTED_GLASS, false, PrayerWindow.DHUHR)
        val sand = resolveWidgetColors(
            WidgetStyle.FROSTED_GLASS, false, PrayerWindow.DHUHR, SakinahPalette.MADINAH_SAND
        )
        val sapphire = resolveWidgetColors(
            WidgetStyle.FROSTED_GLASS, true, PrayerWindow.DHUHR, SakinahPalette.ANDALUSIAN_SAPPHIRE
        )
        assertEquals(0xFF0B3B2C, emerald.accent)
        assertEquals(0xFF6B4423, sand.accent)
        assertEquals(0xFF93C5FD, sapphire.accent)
        assertNotEquals(emerald.accent, sand.accent)
    }

    @Test
    fun `monochrome is matte black and white with the palette accent`() {
        val light = resolveWidgetColors(WidgetStyle.MONOCHROME, false, PrayerWindow.ISHA)
        val dark = resolveWidgetColors(WidgetStyle.MONOCHROME, true, PrayerWindow.ISHA)
        assertEquals(0xFFFFFFFF, light.background)
        assertEquals(0xFF0C0C0C, light.primaryText)
        assertEquals(0xFF0C0C0C, dark.background)
        assertEquals(0xFFFFFFFF, dark.primaryText)
        // The single accent comes from the active palette, not a hard-coded emerald.
        assertEquals(0xFF0B3B2C, light.accent)
        assertEquals(0xFF34D399, dark.accent)
    }

    @Test
    fun `living sky shifts with every prayer window`() {
        val backgrounds = PrayerWindow.entries.map {
            resolveWidgetColors(WidgetStyle.LIVING_SKY, false, it).background
        }
        assertEquals(5, backgrounds.distinct().size)
        assertEquals(0xFF2B4C7E, skyBaseColor(PrayerWindow.FAJR))
        assertEquals(0xFF1D6FA5, skyBaseColor(PrayerWindow.DHUHR))
        assertEquals(0xFF9A5B25, skyBaseColor(PrayerWindow.ASR))
        assertEquals(0xFF4C2A6A, skyBaseColor(PrayerWindow.MAGHRIB))
        assertEquals(0xFF131B3A, skyBaseColor(PrayerWindow.ISHA))
        // Sky text stays white and the pill accent is the palette's bright variant.
        val fajr = resolveWidgetColors(WidgetStyle.LIVING_SKY, false, PrayerWindow.FAJR)
        assertEquals(0xFFFFFFFF, fajr.primaryText)
        assertEquals(0xFF34D399, fajr.accent)
    }

    @Test
    fun `transparent keeps no surface but keeps contrast`() {
        val light = resolveWidgetColors(WidgetStyle.TRANSPARENT, false, PrayerWindow.DHUHR)
        val dark = resolveWidgetColors(WidgetStyle.TRANSPARENT, true, PrayerWindow.DHUHR)
        assertEquals(0L, light.background)
        assertEquals(0L, dark.background)
        assertNotEquals(light.primaryText, dark.primaryText)
        assertNotEquals(0L, light.divider)
        assertEquals(0xFF0B3B2C, light.accent)
    }

    @Test
    fun `theme mode follows the app mode unless the widget overrides it`() {
        assertTrue(resolveDarkMode(WidgetThemeMode.SYSTEM, ThemeMode.SYSTEM, systemDark = true))
        assertFalse(resolveDarkMode(WidgetThemeMode.SYSTEM, ThemeMode.SYSTEM, systemDark = false))
        // The widget's System setting honours the app's forced theme, not only the phone's.
        assertTrue(resolveDarkMode(WidgetThemeMode.SYSTEM, ThemeMode.DARK, systemDark = false))
        assertFalse(resolveDarkMode(WidgetThemeMode.SYSTEM, ThemeMode.LIGHT, systemDark = true))
        assertFalse(resolveDarkMode(WidgetThemeMode.LIGHT, ThemeMode.DARK, systemDark = true))
        assertTrue(resolveDarkMode(WidgetThemeMode.DARK, ThemeMode.LIGHT, systemDark = false))
    }

    @Test
    fun `prayer window progress runs from the last prayer to the next`() {
        val times = mapOf(
            com.sakinah.deen.data.model.PrayerType.FAJR to LocalTime.of(5, 0),
            com.sakinah.deen.data.model.PrayerType.DHUHR to LocalTime.of(12, 30),
            com.sakinah.deen.data.model.PrayerType.ASR to LocalTime.of(16, 0),
            com.sakinah.deen.data.model.PrayerType.MAGHRIB to LocalTime.of(18, 30),
            com.sakinah.deen.data.model.PrayerType.ISHA to LocalTime.of(20, 0)
        )
        assertEquals(0f, prayerProgress(times, LocalTime.of(5, 0)), 0.0001f)
        // 60 of the 450 minutes between Fajr and Dhuhr.
        assertEquals(60f / 450f, prayerProgress(times, LocalTime.of(6, 0)), 0.0001f)
        assertEquals(1f, prayerProgress(times, LocalTime.of(12, 29)), 0.01f)
        // After Isha the window wraps into tomorrow's Fajr.
        assertTrue(prayerProgress(times, LocalTime.of(23, 0)) in 0.3f..0.6f)
    }

    @Test
    fun `prayer progress is safe without a timetable`() {
        assertEquals(0f, prayerProgress(emptyMap(), LocalTime.NOON), 0.0001f)
    }

    @Test
    fun `hijri compact label is upper-case day month year`() {
        val hijri = hijriFor(LocalDate.of(2026, 11, 19))
        assertTrue(hijri.compactLabel.startsWith("${hijri.day} "))
        assertTrue(hijri.compactLabel.endsWith(" ${hijri.year}"))
        assertEquals(hijri.shortMonth.uppercase(), hijri.monthYearLabel.removeSuffix(" ${hijri.year}"))
        assertEquals("${hijri.shortMonth.uppercase()} ${hijri.year}", hijri.monthYearLabel)
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
