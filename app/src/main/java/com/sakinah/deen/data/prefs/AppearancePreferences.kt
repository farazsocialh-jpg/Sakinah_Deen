package com.sakinah.deen.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
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

/**
 * The four supported Quran reader presets. Ten near-duplicate layouts previously existed;
 * they multiplied QA and accessibility work without adding a distinct reading mode.
 * Legacy preference values are mapped by [fromStoredName], so an upgrade never silently
 * resets a reader's chosen layout.
 */
enum class QuranReadingLayout(val displayName: String, val subtitle: String) {
    DIVIDER_HAIRLINE("Divider Hairline", "Zero boxes, thin dividers, Arabic + translation"),
    CONTINUOUS_MUSHAF("Continuous Mushaf", "Book-like flowing page with inline verse markers"),
    SPLIT_COLUMN("Split-Column Study", "Translation left, Arabic right side-by-side"),
    THUMB_BOTTOM_DOCK("Thumb-Zone Dock", "Floating bottom utility pill for one-handed use");

    companion object {
        /** Presets retired in the layout consolidation, mapped to their closest successor. */
        private val LEGACY_NAMES = mapOf(
            "ALTERNATING_ROWS" to DIVIDER_HAIRLINE,
            "COMPACT_CARDS" to DIVIDER_HAIRLINE,
            "LEFT_ACCENT_RIBBON" to DIVIDER_HAIRLINE,
            "FOCUS_SPOTLIGHT" to DIVIDER_HAIRLINE,
            "IMMERSIVE_ZEN" to CONTINUOUS_MUSHAF,
            "ARABIC_TAP_EXPAND" to THUMB_BOTTOM_DOCK
        )

        /** Resolves a stored enum name (current or legacy); null when it is unknown. */
        fun fromStoredName(raw: String?): QuranReadingLayout? {
            val name = raw?.trim().orEmpty()
            if (name.isEmpty()) return null
            return entries.firstOrNull { it.name == name } ?: LEGACY_NAMES[name]
        }
    }
}

enum class HadithReadingLayout(val displayName: String, val subtitle: String) {
    COMPACT_DROPDOWN("Compact Hairline", "Single filter bar with borderless hairline feed"),
    ENGLISH_FIRST_TAP("English-First", "Compact English cards with inline Arabic toggle"),
    BOOK_LIBRARY_GRID("Book Library Grid", "2-column Kutub al-Tis'ah grid into clean reader"),
    LANGUAGE_TABBED("Language Split", "English | Arabic | Both segmented reader"),
    SINGLE_BAR_CARDS("Compact Cards", "One filter row with tight-padded cards"),
    ALTERNATING_ROWS("Alternating Rows", "Edge-to-edge tinted rows with left accent bar"),
    FEATURED_AND_LIST("Featured + List", "Active hadith card on top + expandable previews"),
    THUMB_BOTTOM_DOCK("Thumb-Zone Dock", "Floating bottom filter pill for one-handed use"),
    SANAD_MATN_STUDY("Sanad / Matn Study", "Collapsible narrator chain + highlighted Matn"),
    MINIMALIST_ZEN("Minimalist Zen", "Single floating top pill with pure typography")
}

enum class SalahHomeLayout(val displayName: String, val subtitle: String) {
    CLASSIC_COMPACT("Classic Compact", "Single hero card with draining bar & prayer list"),
    BENTO_SPLIT("Bento Split", "Side-by-side current & next prayer cards"),
    CIRCULAR_RING("Circular Ring", "Circular draining ring beside current prayer"),
    SEAMLESS_EDITORIAL("Seamless Hero", "Borderless gradient top flowing into prayer list"),
    DAILY_STREAK_DOTS("Daily Status Dots", "Hero card with F-D-A-M-I completion dots");

    companion object {
        private val LEGACY_NAMES = mapOf(
            "ALL_DAY_GRID" to CLASSIC_COMPACT,
            "TIMELINE_STRIP" to CLASSIC_COMPACT
        )

        fun fromStoredName(raw: String?): SalahHomeLayout? {
            val name = raw?.trim().orEmpty()
            if (name.isEmpty()) return null
            return entries.firstOrNull { it.name == name } ?: LEGACY_NAMES[name]
        }
    }
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
        private const val KEY_HADITH_READING_LAYOUT = "hadith_reading_layout"
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

    fun updateHadithReadingLayout(layout: HadithReadingLayout) {
        update { it.copy(hadithReadingLayout = layout) }
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
            salahHomeLayout = SalahHomeLayout.fromStoredName(
                prefs.getString(KEY_SALAH_HOME_LAYOUT, null)
            ) ?: defaults.salahHomeLayout,
            quranReadingLayout = QuranReadingLayout.fromStoredName(
                prefs.getString(KEY_QURAN_READING_LAYOUT, null)
            ) ?: defaults.quranReadingLayout,
            hadithReadingLayout = safeEnum(KEY_HADITH_READING_LAYOUT, defaults.hadithReadingLayout),
            onboardingCompleted = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, defaults.onboardingCompleted)
        )
    }

    private fun persist(value: UserAppearanceConfig) {
        prefs.edit {
            putString(KEY_USER_NAME, value.userName.trim())
            putString(KEY_EXPERIENCE_LEVEL, value.experienceLevel)
            putString(KEY_OBJECTIVE, value.objective.name)
            putString(KEY_THEME_MODE, value.themeMode.name)
            putString(KEY_PALETTE, value.palette.name)
            putString(KEY_FONT_SCALE, value.fontScale.name)
            putBoolean(KEY_FOCUS_MODE, value.focusModeHideDistractions)
            putBoolean(KEY_AUTO_HIDE_BARS, value.autoHideBarsOnScroll)
            putString(KEY_PRAYER_GUIDE_GENDER, value.prayerGuideGender.name)
            putString(KEY_SALAH_HOME_LAYOUT, value.salahHomeLayout.name)
            putString(KEY_QURAN_READING_LAYOUT, value.quranReadingLayout.name)
            putString(KEY_HADITH_READING_LAYOUT, value.hadithReadingLayout.name)
            putBoolean(KEY_ONBOARDING_COMPLETED, value.onboardingCompleted)
        }
    }

    private inline fun <reified T : Enum<T>> safeEnum(key: String, default: T): T {
        val raw = prefs.getString(key, null) ?: return default
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }
}
