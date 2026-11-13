package com.sakinah.deen.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CalculationMethod { MWL, ISNA, UMM_AL_QURA, EGYPTIAN, KARACHI, TEHRAN, MAKKAH }
enum class AsrMethod { SHAFI, HANAFI }
enum class HighLatitudeRule { MIDDLE_OF_NIGHT, SEVENTH_OF_NIGHT, TWILIGHT_ANGLE }
enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class AdhanChoice { FAJR, MAKKAH, VIBRATE, SILENT }

data class PrayerSettings(
    val calculationMethod: CalculationMethod = CalculationMethod.MWL,
    val asrMethod: AsrMethod = AsrMethod.SHAFI,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.MIDDLE_OF_NIGHT,
    val hijriOffset: Int = 0,
)

data class AdhanSettings(
    val fajrEnabled: Boolean = true,
    val dhuhrEnabled: Boolean = true,
    val asrEnabled: Boolean = true,
    val maghribEnabled: Boolean = true,
    val ishaEnabled: Boolean = true,
    val adhanChoice: AdhanChoice = AdhanChoice.MAKKAH,
    val carBluetoothAutoPlay: Boolean = false,
)

data class AppearanceSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val arabicFontSize: Int = 24,
    val translationLanguage: String = "en",
    val translationId: Int = 20,
    val translationName: String = "English",
    val hadithLanguageCode: String = "eng",
)

data class AppSettings(
    val prayer: PrayerSettings = PrayerSettings(),
    val adhan: AdhanSettings = AdhanSettings(),
    val appearance: AppearanceSettings = AppearanceSettings(),
)

interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    suspend fun updatePrayer(settings: PrayerSettings)
    suspend fun updateAdhan(settings: AdhanSettings)
    suspend fun updateAppearance(settings: AppearanceSettings)
    fun cacheSizeBytes(): Long
    suspend fun clearCache()
}

class SettingsRepositoryImpl(
    private val prefs: Settings = Settings(),
    private val cacheBytesProvider: () -> Long = { 0L },
    private val onClearCache: suspend () -> Unit = {},
) : SettingsRepository {

    private val _settings = MutableStateFlow(load())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private inline fun <reified T : Enum<T>> safeEnum(key: String, default: T): T {
        val raw = prefs.getString(key, default.name)
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }

    private fun load(): AppSettings = AppSettings(
        prayer = PrayerSettings(
            calculationMethod = safeEnum("calc", CalculationMethod.MWL),
            asrMethod = safeEnum("asr", AsrMethod.SHAFI),
            highLatitudeRule = safeEnum("highLat", HighLatitudeRule.MIDDLE_OF_NIGHT),
            hijriOffset = prefs.getInt("hijriOffset", 0),
        ),
        adhan = AdhanSettings(
            fajrEnabled = prefs.getBoolean("fajrEnabled", true),
            dhuhrEnabled = prefs.getBoolean("dhuhrEnabled", true),
            asrEnabled = prefs.getBoolean("asrEnabled", true),
            maghribEnabled = prefs.getBoolean("maghribEnabled", true),
            ishaEnabled = prefs.getBoolean("ishaEnabled", true),
            adhanChoice = safeEnum("adhanChoice", AdhanChoice.MAKKAH),
            carBluetoothAutoPlay = prefs.getBoolean("carBt", false),
        ),
        appearance = AppearanceSettings(
            theme = safeEnum("theme", ThemeMode.SYSTEM),
            arabicFontSize = prefs.getInt("arabicFontSize", 24),
            translationLanguage = prefs.getString("translationLang", "en"),
            translationId = prefs.getInt("quranTranslationId", 20),
            translationName = prefs.getString("quranTranslationName", "English") ?: "English",
            hadithLanguageCode = prefs.getString("hadithLanguageCode", "eng") ?: "eng",
        ),
    )

    override suspend fun updatePrayer(settings: PrayerSettings) {
        prefs.putString("calc", settings.calculationMethod.name)
        prefs.putString("asr", settings.asrMethod.name)
        prefs.putString("highLat", settings.highLatitudeRule.name)
        prefs.putInt("hijriOffset", settings.hijriOffset)
        _settings.value = _settings.value.copy(prayer = settings)
    }

    override suspend fun updateAdhan(settings: AdhanSettings) {
        prefs.putBoolean("fajrEnabled", settings.fajrEnabled)
        prefs.putBoolean("dhuhrEnabled", settings.dhuhrEnabled)
        prefs.putBoolean("asrEnabled", settings.asrEnabled)
        prefs.putBoolean("maghribEnabled", settings.maghribEnabled)
        prefs.putBoolean("ishaEnabled", settings.ishaEnabled)
        prefs.putString("adhanChoice", settings.adhanChoice.name)
        prefs.putBoolean("carBt", settings.carBluetoothAutoPlay)
        _settings.value = _settings.value.copy(adhan = settings)
    }

    override suspend fun updateAppearance(settings: AppearanceSettings) {
        prefs.putString("theme", settings.theme.name)
        prefs.putInt("arabicFontSize", settings.arabicFontSize)
        prefs.putString("translationLang", settings.translationLanguage)
        prefs.putInt("quranTranslationId", settings.translationId)
        prefs.putString("quranTranslationName", settings.translationName)
        prefs.putString("hadithLanguageCode", settings.hadithLanguageCode)
        _settings.value = _settings.value.copy(appearance = settings)
    }

    override fun cacheSizeBytes(): Long = cacheBytesProvider()

    override suspend fun clearCache() {
        onClearCache()
    }
}