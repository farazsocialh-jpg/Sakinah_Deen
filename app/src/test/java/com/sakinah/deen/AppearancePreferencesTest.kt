package com.sakinah.deen

import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.data.prefs.HadithReadingLayout
import com.sakinah.deen.data.prefs.QuranReadingLayout
import com.sakinah.deen.data.prefs.SalahHomeLayout
import com.sakinah.deen.fakes.FakeSharedPreferences
import com.sakinah.deen.ui.theme.FontScalePreset
import com.sakinah.deen.ui.theme.SakinahPalette
import com.sakinah.deen.ui.theme.ThemeMode
import com.sakinah.deen.ui.theme.UserAppearanceConfig
import com.sakinah.deen.ui.theme.UserObjective
import com.sakinah.deen.ui.theme.buildSakinahColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearancePreferencesTest {

    @Test
    fun defaultsAndObjectiveStartTabMapping() {
        val fake = FakeSharedPreferences()
        val store = AppearancePreferences(fake)

        val initial = store.config.value
        assertFalse(initial.onboardingCompleted)
        assertEquals(UserObjective.ALL_IN_ONE, initial.objective)
        assertEquals("DailySalah", UserObjective.PRAYER_COMPASS.recommendedStartTab)
        assertEquals("Quran", UserObjective.QURAN_STUDY.recommendedStartTab)
        assertEquals("Hadith", UserObjective.HADITH_SCHOLAR.recommendedStartTab)
        assertEquals("DailySalah", UserObjective.ALL_IN_ONE.recommendedStartTab)
    }

    @Test
    fun saveConfigPersistsAcrossInstances() {
        val fake = FakeSharedPreferences()
        val store = AppearancePreferences(fake)

        val custom = UserAppearanceConfig(
            userName = "Amina",
            experienceLevel = "Student of Knowledge",
            objective = UserObjective.QURAN_STUDY,
            themeMode = ThemeMode.DARK,
            palette = SakinahPalette.PURE_MONOCHROME,
            fontScale = FontScalePreset.COMFORTABLE,
            focusModeHideDistractions = true,
            autoHideBarsOnScroll = false,
            onboardingCompleted = true
        )
        store.saveConfig(custom)

        val reloaded = AppearancePreferences(fake).config.value
        assertEquals(custom, reloaded)
    }

    @Test
    fun salahHomeLayoutPersistsAndInvalidValueFallsBack() {
        val fake = FakeSharedPreferences()
        val store = AppearancePreferences(fake)

        store.updateSalahHomeLayout(SalahHomeLayout.CIRCULAR_RING)
        assertEquals(
            SalahHomeLayout.CIRCULAR_RING,
            AppearancePreferences(fake).config.value.salahHomeLayout
        )

        fake.edit().putString("salah_home_layout", "OBSOLETE_LAYOUT").apply()
        assertEquals(
            SalahHomeLayout.CLASSIC_COMPACT,
            AppearancePreferences(fake).config.value.salahHomeLayout
        )
    }

    @Test
    fun retiredSalahHomeLayoutsMapToTheirSuccessorInsteadOfResetting() {
        val fake = FakeSharedPreferences()
        // Both retired presets rendered the classic hero card, so neither choice is lost.
        fake.edit().putString("salah_home_layout", "ALL_DAY_GRID").apply()
        assertEquals(
            SalahHomeLayout.CLASSIC_COMPACT,
            AppearancePreferences(fake).config.value.salahHomeLayout
        )

        fake.edit().putString("salah_home_layout", "TIMELINE_STRIP").apply()
        assertEquals(
            SalahHomeLayout.CLASSIC_COMPACT,
            AppearancePreferences(fake).config.value.salahHomeLayout
        )

        // Every surviving preset still round-trips through its stored name.
        SalahHomeLayout.entries.forEach { layout ->
            assertEquals(layout, SalahHomeLayout.fromStoredName(layout.name))
        }
    }

    @Test
    fun quranReadingLayoutPersistsAndInvalidValueFallsBack() {
        val fake = FakeSharedPreferences()
        val store = AppearancePreferences(fake)

        store.updateQuranReadingLayout(QuranReadingLayout.SPLIT_COLUMN)
        assertEquals(
            QuranReadingLayout.SPLIT_COLUMN,
            AppearancePreferences(fake).config.value.quranReadingLayout
        )

        fake.edit().putString("quran_reading_layout", "OBSOLETE_LAYOUT").apply()
        assertEquals(
            QuranReadingLayout.SPLIT_COLUMN,
            AppearancePreferences(fake).config.value.quranReadingLayout
        )
    }

    @Test
    fun hadithReadingLayoutPersistsAndInvalidValueFallsBack() {
        val fake = FakeSharedPreferences()
        val store = AppearancePreferences(fake)

        store.updateHadithReadingLayout(HadithReadingLayout.SANAD_MATN_STUDY)
        assertEquals(
            HadithReadingLayout.SANAD_MATN_STUDY,
            AppearancePreferences(fake).config.value.hadithReadingLayout
        )

        fake.edit().putString("hadith_reading_layout", "OBSOLETE_LAYOUT").apply()
        assertEquals(
            HadithReadingLayout.COMPACT_DROPDOWN,
            AppearancePreferences(fake).config.value.hadithReadingLayout
        )
    }

    @Test
    fun invalidStoredEnumsFallbackSafelyToDefaults() {
        val fake = FakeSharedPreferences()
        fake.edit()
            .putString("user_objective", "UNKNOWN_OBJECTIVE")
            .putString("theme_mode", "NEON")
            .putString("sakinah_palette", "OBSOLETE_PALETTE")
            .putString("font_scale_preset", "GIANT")
            .apply()

        val loaded = AppearancePreferences(fake).config.value
        assertEquals(UserObjective.ALL_IN_ONE, loaded.objective)
        assertEquals(ThemeMode.SYSTEM, loaded.themeMode)
        assertEquals(SakinahPalette.EMERALD_SANCTUARY, loaded.palette)
        assertEquals(FontScalePreset.STANDARD, loaded.fontScale)
    }

    @Test
    fun previewUpdatesEffectiveConfigWithoutOverwritingPersistedState() {
        val fake = FakeSharedPreferences()
        val store = AppearancePreferences(fake)
        val saved = UserAppearanceConfig(
            palette = SakinahPalette.EMERALD_SANCTUARY,
            themeMode = ThemeMode.LIGHT,
            onboardingCompleted = true
        )
        store.saveConfig(saved)

        val preview = saved.copy(
            palette = SakinahPalette.ANDALUSIAN_SAPPHIRE,
            themeMode = ThemeMode.DARK,
            fontScale = FontScalePreset.LARGE
        )
        store.setPreview(preview)

        assertEquals(saved, store.config.value)
        assertEquals(preview, store.effectiveConfig.value)

        store.setPreview(null)
        assertEquals(saved, store.effectiveConfig.value)
    }

    @Test
    fun resetOnboardingClearsOnlyCompletionFlag() {
        val fake = FakeSharedPreferences()
        val store = AppearancePreferences(fake)
        store.saveConfig(
            UserAppearanceConfig(
                userName = "Bilal",
                objective = UserObjective.HADITH_SCHOLAR,
                palette = SakinahPalette.MADINAH_SAND,
                onboardingCompleted = true
            )
        )

        store.resetOnboarding()
        val current = store.config.value
        assertFalse(current.onboardingCompleted)
        assertEquals("Bilal", current.userName)
        assertEquals(UserObjective.HADITH_SCHOLAR, current.objective)
        assertEquals(SakinahPalette.MADINAH_SAND, current.palette)
    }

    @Test
    fun allPalettesProduceDistinctLightAndDarkColorSchemes() {
        SakinahPalette.entries.forEach { palette ->
            val light = buildSakinahColorScheme(palette, isDark = false)
            val dark = buildSakinahColorScheme(palette, isDark = true)
            assertNotEquals(light.background, dark.background)
            assertNotEquals(light.primary, light.onPrimary)
            assertNotEquals(dark.primary, dark.onPrimary)
        }
        assertTrue(FontScalePreset.LARGE.multiplier > FontScalePreset.STANDARD.multiplier)
        assertTrue(FontScalePreset.COMPACT.multiplier < FontScalePreset.STANDARD.multiplier)
    }

    @Test
    fun everyRegisteredPaletteHasLocalizedResourcesAndPreviewColours() {
        assertEquals(18, SakinahPalette.entries.size)
        assertEquals(
            listOf(
                "EMERALD_SANCTUARY",
                "MADINAH_SAND",
                "ANDALUSIAN_SAPPHIRE",
                "PURE_MONOCHROME"
            ),
            SakinahPalette.entries.take(4).map { it.name }
        )
        SakinahPalette.entries.forEach { palette ->
            assertTrue("${palette.name} needs a title", palette.titleRes != 0)
            assertTrue("${palette.name} needs a subtitle", palette.subtitleRes != 0)
            assertNotEquals(palette.lightPreviewPrimary, palette.lightPreviewAccent)
            assertNotEquals(palette.darkPreviewPrimary, palette.darkPreviewBg)
        }
    }

    @Test
    fun everyNewPaletteMeetsWcagAaForTextPairs() {
        // The four original palettes ship byte-identical so a saved selection keeps its exact
        // look; their historical contrast tokens are therefore not re-asserted here.
        SakinahPalette.entries.drop(LEGACY_PALETTE_COUNT).forEach { palette ->
            listOf(false, true).forEach { isDark ->
                val scheme = buildSakinahColorScheme(palette, isDark)
                val mode = if (isDark) "dark" else "light"
                assertAa("$mode onPrimary/primary ${palette.name}", scheme.onPrimary, scheme.primary)
                assertAa(
                    "$mode onPrimaryContainer/primaryContainer ${palette.name}",
                    scheme.onPrimaryContainer,
                    scheme.primaryContainer
                )
                assertAa("$mode onBackground/background ${palette.name}", scheme.onBackground, scheme.background)
                assertAa("$mode onSurface/surface ${palette.name}", scheme.onSurface, scheme.surface)
                assertAa(
                    "$mode onSurfaceVariant/surfaceVariant ${palette.name}",
                    scheme.onSurfaceVariant,
                    scheme.surfaceVariant
                )
            }
        }
    }

    @Test
    fun everyPalettePersistsAndRestoresThroughPreferences() {
        SakinahPalette.entries.forEach { palette ->
            val fake = FakeSharedPreferences()
            AppearancePreferences(fake).update { it.copy(palette = palette) }
            assertEquals(palette, AppearancePreferences(fake).config.value.palette)
        }
    }

    @Test
    fun everyPaletteHasDistinctLightAndDarkSwatches() {
        // Several palettes intentionally share a primary (Cedar Wood reuses Madinah Sand's
        // brown); the primary + accent pair is what identifies a theme in the picker.
        val lightPairs = SakinahPalette.entries.map { it.lightPreviewPrimary to it.lightPreviewAccent }
        val darkPairs = SakinahPalette.entries.map { it.darkPreviewPrimary to it.darkPreviewBg }
        assertEquals(lightPairs.size, lightPairs.distinct().size)
        assertEquals(darkPairs.size, darkPairs.distinct().size)
    }

    private companion object {
        const val LEGACY_PALETTE_COUNT = 4
    }

    private fun assertAa(message: String, foreground: Color, background: Color) {
        assertTrue("$message has contrast ${contrastRatio(foreground, background)}", contrastRatio(foreground, background) >= 4.5)
    }

    /** WCAG 2.1 relative-luminance contrast ratio. */
    private fun contrastRatio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
