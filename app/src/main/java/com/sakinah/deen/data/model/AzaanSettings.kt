package com.sakinah.deen.data.model

/** How a prayer-time alarm should behave when it fires. */
enum class PrayerAlertMode(val label: String) {
    FULL_AZAAN("Full Azaan"),
    SHORT_TONE("Short Tone"),
    SILENT("Silent"),
    OFF("Off")
}

/** Bundled Muezzin recordings. Missing raw files fall back to the Makkah recording. */
enum class Muezzin(
    val id: String,
    val displayName: String,
    val rawName: String
) {
    MAKKAH("makkah", "Makkah (Masjid al-Haram)", "adhan_makkah"),
    MADINAH("madinah", "Madinah (Masjid an-Nabawi)", "azan_madinah"),
    AQSA("aqsa", "Al-Aqsa", "azan_aqsa"),
    ALAFASY("alafasy", "Mishary Rashid Alafasy", "azan_alafasy"),
    CAIRO("cairo", "Cairo (Classic)", "azan_cairo"),
    TAKBIR_AQSA("takbir_aqsa", "Allahu Akbar ×2 (Al-Aqsa)", "takbir_aqsa"),
    TAKBIR_CAIRO("takbir_cairo", "Allahu Akbar ×2 (Cairo)", "takbir_cairo");

    companion object {
        fun fromId(id: String?): Muezzin =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MAKKAH
    }
}

/** Persisted Azaan preferences shared by the UI, alarm scheduler and broadcast receivers. */
data class AzaanSettings(
    val enabled: Boolean = true,
    val muezzin: Muezzin = Muezzin.MAKKAH,
    val fajrSpecialAdhan: Boolean = true,
    val modes: Map<PrayerType, PrayerAlertMode> = DEFAULT_MODES,
    val offsetsMinutes: Map<PrayerType, Int> = DEFAULT_OFFSETS
) {
    fun modeFor(prayer: PrayerType): PrayerAlertMode =
        modes[prayer] ?: PrayerAlertMode.FULL_AZAAN

    fun offsetFor(prayer: PrayerType): Int =
        offsetsMinutes[prayer] ?: 0

    companion object {
        val ALERT_PRAYERS = listOf(
            PrayerType.FAJR,
            PrayerType.DHUHR,
            PrayerType.ASR,
            PrayerType.MAGHRIB,
            PrayerType.ISHA
        )

        val OFFSET_OPTIONS = listOf(-15, -10, -5, 0, 5)

        val DEFAULT_MODES: Map<PrayerType, PrayerAlertMode> =
            ALERT_PRAYERS.associateWith { PrayerAlertMode.FULL_AZAAN }

        val DEFAULT_OFFSETS: Map<PrayerType, Int> =
            ALERT_PRAYERS.associateWith { 0 }
    }
}
