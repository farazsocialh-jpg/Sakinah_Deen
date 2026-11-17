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
            QuranReadingLayout.DIVIDER_HAIRLINE,
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
}
