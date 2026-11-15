package com.sakinah.deen.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.sakinah.deen.ui.theme.FontScalePreset
import com.sakinah.deen.ui.theme.PrayerGuideGender
import com.sakinah.deen.ui.theme.SakinahPalette
import com.sakinah.deen.ui.theme.ThemeMode
import com.sakinah.deen.ui.theme.UserAppearanceConfig
import com.sakinah.deen.ui.theme.UserObjective
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class QuranReadingLayout(val displayName: String, val subtitle: String) {
    DIVIDER_HAIRLINE("Divider Hairline", "Zero boxes, thin horizontal dividers, 6+ ayahs"),
    CONTINUOUS_MUSHAF("Continuous Mushaf", "Book-like flowing page with inline verse markers"),
    ALTERNATING_ROWS("Alternating Rows", "Edge-to-edge subtle alternating row tints"),
    COMPACT_CARDS("Compact Cards", "Tight-padded cards with inline Tafseer chip"),
    SPLIT_COLUMN("Split-Column Study", "Translation left, Arabic right side-by-side"),
    IMMERSIVE_ZEN("Immersive Zen", "Single floating top pill, 90%+ reading area"),
    FOCUS_SPOTLIGHT("Focus Spotlight", "Active ayah highlighted; others compact"),
    THUMB_BOTTOM_DOCK("Thumb-Zone Dock", "Floating bottom utility pill for one-handed use"),
    ARABIC_TAP_EXPAND("Arabic + Tap Expand", "Arabic-only flow; tap verse for English & Tafseer"),
    LEFT_ACCENT_RIBBON("Surah Ribbon", "Surah info ribbon + 2dp left accent bar per verse")
}

enum class SalahHomeLayout(val displayName: String, val subtitle: String) {
    CLASSIC_COMPACT("Classic Compact", "Single hero card with draining bar & prayer list"),
    BENTO_SPLIT("Bento Split", "Side-by-side current & next prayer cards"),
    CIRCULAR_RING("Circular Ring", "Circular draining ring beside current prayer"),
    ALL_DAY_GRID("All-Day Grid", "Hero card with 2x3 prayer grid (no scrolling)"),
    TIMELINE_STRIP("Timeline Strip", "5-prayer horizontal pill bar above hero card"),
    SEAMLESS_EDITORIAL("Seamless Hero", "Borderless gradient top flowing into prayer list"),
    DAILY_STREAK_DOTS("Daily Status Dots", "Hero card with F-D-A-M-I completion dots")
}

/**
 * Persists personalized onboarding choices, theme/palette/font presets,
 * Focus Mode, and auto-hiding chrome preferences as reactive [StateFlow]s.
 */
class AppearancePreferences internal constructor(
    private val prefs: SharedPreferences
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    companion object {
        private const val PREFS_NAME = "sakinah_appearance_preferences"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_EXPERIENCE_LEVEL = "experience_level"
        private const val KEY_OBJECTIVE = "user_objective"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PALETTE = "sakinah_palette"
        private const val KEY_FONT_SCALE = "font_scale_preset"
        private const val KEY_FOCUS_MODE = "focus_mode_hide_distractions"
        private const val KEY_AUTO_HIDE_BARS = "auto_hide_bars_on_scroll"
        private const val KEY_PRAYER_GUIDE_GENDER = "prayer_guide_gender"
        private const val KEY_SALAH_HOME_LAYOUT = "salah_home_layout"
        private const val KEY_QURAN_READING_LAYOUT = "quran_reading_layout"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }

    private val _config = MutableStateFlow(load())
    val config: StateFlow<UserAppearanceConfig> = _config.asStateFlow()

    /**
     * Reflects either the live in-progress onboarding preview or the persisted [config].
     * Collected at the application root so theme, palette, and font scale update immediately.
     */
    private val _effectiveConfig = MutableStateFlow(_config.value)
    val effectiveConfig: StateFlow<UserAppearanceConfig> = _effectiveConfig.asStateFlow()

    private var previewActive = false

    @Synchronized
    fun setPreview(preview: UserAppearanceConfig?) {
        previewActive = preview != null
        _effectiveConfig.value = preview ?: _config.value
    }

    @Synchronized
    fun saveConfig(newConfig: UserAppearanceConfig) {
        persist(newConfig)
        _config.value = newConfig
        // A live onboarding preview keeps control of the effective theme until it ends.
        if (!previewActive) {
            _effectiveConfig.value = newConfig
        }
    }

    @Synchronized
    fun update(transform: (UserAppearanceConfig) -> UserAppearanceConfig) {
        _config.update(transform)
        persist(_config.value)
        if (!previewActive) {
            _effectiveConfig.value = _config.value
        }
    }

    fun resetOnboarding() {
        update { it.copy(onboardingCompleted = false) }
    }

    fun updateSalahHomeLayout(layout: SalahHomeLayout) {
        update { it.copy(salahHomeLayout = layout) }
    }

    fun updateQuranReadingLayout(layout: QuranReadingLayout) {
        update { it.copy(quranReadingLayout = layout) }
    }

    private fun load(): UserAppearanceConfig {
        val defaults = UserAppearanceConfig()
        return UserAppearanceConfig(
            userName = prefs.getString(KEY_USER_NAME, defaults.userName).orEmpty(),
            experienceLevel = prefs.getString(KEY_EXPERIENCE_LEVEL, defaults.experienceLevel)
                ?.takeIf { it.isNotBlank() } ?: defaults.experienceLevel,
            objective = safeEnum(KEY_OBJECTIVE, defaults.objective),
            themeMode = safeEnum(KEY_THEME_MODE, defaults.themeMode),
            palette = safeEnum(KEY_PALETTE, defaults.palette),
            fontScale = safeEnum(KEY_FONT_SCALE, defaults.fontScale),
            focusModeHideDistractions = prefs.getBoolean(KEY_FOCUS_MODE, defaults.focusModeHideDistractions),
            autoHideBarsOnScroll = prefs.getBoolean(KEY_AUTO_HIDE_BARS, defaults.autoHideBarsOnScroll),
            prayerGuideGender = safeEnum(KEY_PRAYER_GUIDE_GENDER, defaults.prayerGuideGender),
            salahHomeLayout = safeEnum(KEY_SALAH_HOME_LAYOUT, defaults.salahHomeLayout),
            quranReadingLayout = safeEnum(KEY_QURAN_READING_LAYOUT, defaults.quranReadingLayout),
            onboardingCompleted = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, defaults.onboardingCompleted)
        )
    }

    private fun persist(value: UserAppearanceConfig) {
        prefs.edit()
            .putString(KEY_USER_NAME, value.userName.trim())
            .putString(KEY_EXPERIENCE_LEVEL, value.experienceLevel)
            .putString(KEY_OBJECTIVE, value.objective.name)
            .putString(KEY_THEME_MODE, value.themeMode.name)
            .putString(KEY_PALETTE, value.palette.name)
            .putString(KEY_FONT_SCALE, value.fontScale.name)
            .putBoolean(KEY_FOCUS_MODE, value.focusModeHideDistractions)
            .putBoolean(KEY_AUTO_HIDE_BARS, value.autoHideBarsOnScroll)
            .putString(KEY_PRAYER_GUIDE_GENDER, value.prayerGuideGender.name)
            .putString(KEY_SALAH_HOME_LAYOUT, value.salahHomeLayout.name)
            .putString(KEY_QURAN_READING_LAYOUT, value.quranReadingLayout.name)
            .putBoolean(KEY_ONBOARDING_COMPLETED, value.onboardingCompleted)
            .apply()
    }

    private inline fun <reified T : Enum<T>> safeEnum(key: String, default: T): T {
        val raw = prefs.getString(key, null) ?: return default
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }
}
