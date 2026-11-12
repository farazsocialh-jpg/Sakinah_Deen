package com.sakinah.deen.data.prefs

import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.prayer.PrayerMethodSettings

/**
 * Android-backed [PrayerMethodSettings]: the manual calculation-method override and the last
 * detected ISO country code live in [SakinahPreferences] so background components (alarm
 * scheduling, boot receiver) resolve the same Sunni authority as the foreground screen.
 */
class PreferencesPrayerMethodSettings(
    private val preferences: SakinahPreferences
) : PrayerMethodSettings {

    override fun countryCode(): String? = preferences.locationCountryCode

    override fun manualMethodOverride(): CalculationMethod? = preferences.calculationMethodOverride

    override fun setManualMethodOverride(method: CalculationMethod?) {
        preferences.calculationMethodOverride = method
    }

    override fun setCountryCode(code: String?) {
        preferences.locationCountryCode = code
    }
}
