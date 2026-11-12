package com.sakinah.deen.data.model

// Prayer & Habit Enums
enum class PrayerType {
    FAJR,
    SUNRISE,
    DHUHR,
    ASR,
    MAGHRIB,
    ISHA
}

enum class NaflType {
    TAHAJJUD,
    DUHA,
    AWWABIN,
    WITR,
    TAHIYYAT_AL_MASJID,
    SUNNAH_RAWATIB
}

enum class UserTrack {
    NEW_MUSLIM,
    PRACTICING
}

enum class Madhhab {
    SHAFI_HANBALI_MALIKI,
    HANAFI
}

enum class CalculationMethod {
    MUSLIM_WORLD_LEAGUE,
    EGYPTIAN,
    KARACHI,
    UMM_AL_QURA,
    DUBAI,
    QATAR,
    NORTH_AMERICA
}

data class PrayerAdjustments(
    val fajrMinutes: Int = 0,
    val sunriseMinutes: Int = 0,
    val dhuhrMinutes: Int = 0,
    val asrMinutes: Int = 0,
    val maghribMinutes: Int = 0,
    val ishaMinutes: Int = 0
)

// Quran & Tafseer Models
data class Ayah(
    val surahNumber: Int,
    val ayahNumber: Int,
    val textUthmani: String,
    val translation: String,
    val audioUrl: String? = null
)

data class TafseerEntry(
    val tafseerId: String,
    val surahNumber: Int,
    val ayahNumber: Int,
    val author: String,
    val languageCode: String,
    val contentMarkdown: String
)

// Hadith Models
enum class HadithGrade {
    SAHIH,
    HASAN,
    DA_IF
}

data class Hadith(
    val id: String,
    val bookSlug: String,
    val chapterId: Int,
    val hadithNumber: Int,
    val arabicText: String,
    val translation: String,
    val grade: HadithGrade,
    val grader: String
)

// Wealth & Zakat Calculation
data class ZakatCalculation(
    val cashOnHand: Double,
    val goldGrams: Double,
    val silverGrams: Double,
    val tradeGoods: Double,
    val investmentEquities: Double,
    val deductibleDebts: Double,
    val goldPricePerGram: Double,
    val silverPricePerGram: Double,
    val useSilverStandard: Boolean = false
) {
    val nisabThreshold: Double
        get() = if (useSilverStandard) silverPricePerGram * 595.0 else goldPricePerGram * 85.0

    val netZakatableWealth: Double
        get() = (cashOnHand + (goldGrams * goldPricePerGram) + (silverGrams * silverPricePerGram) + tradeGoods + investmentEquities - deductibleDebts).coerceAtLeast(0.0)

    val isZakatDue: Boolean
        get() = netZakatableWealth >= nisabThreshold

    val zakatPayable: Double
        get() = if (isZakatDue) netZakatableWealth * 0.025 else 0.0
}
