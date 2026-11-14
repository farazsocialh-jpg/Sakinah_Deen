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

    fun setPreview(preview: UserAppearanceConfig?) {
        _effectiveConfig.value = preview ?: _config.value
    }

    fun saveConfig(newConfig: UserAppearanceConfig) {
        persist(newConfig)
        _config.value = newConfig
        _effectiveConfig.value = newConfig
    }

    fun update(transform: (UserAppearanceConfig) -> UserAppearanceConfig) {
        saveConfig(transform(_config.value))
    }

    fun resetOnboarding() {
        update { it.copy(onboardingCompleted = false) }
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
            .putBoolean(KEY_ONBOARDING_COMPLETED, value.onboardingCompleted)
            .apply()
    }

    private inline fun <reified T : Enum<T>> safeEnum(key: String, default: T): T {
        val raw = prefs.getString(key, null) ?: return default
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }
}
