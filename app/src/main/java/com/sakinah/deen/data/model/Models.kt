package com.sakinah.deen.data.model

data class PrayerModel(
    val id: String,
    val nameEnglish: String,
    val nameArabic: String,
    val time: String,
    val fardRakats: Int,
    val sunnahBefore: Int,
    val sunnahAfter: Int,
    val isFardCompleted: Boolean = false,
    val isJamaah: Boolean = false,
    val isSunnahCompleted: Boolean = false,
    val isNawafilCompleted: Boolean = false,
    val nawafilName: String? = null
)

data class QadhaLedgerModel(
    val fajr: Int = 12,
    val dhuhr: Int = 8,
    val asr: Int = 14,
    val maghrib: Int = 3,
    val isha: Int = 9,
    val witr: Int = 12,
    val dailyGoalPace: Int = 2
)

data class PostureStepModel(
    val id: Int,
    val name: String,
    val arabicTitle: String,
    val postureKey: String,
    val postureAngleText: String,
    val footPositioning: String,
    val handPlacement: String,
    val arabicRecitation: String,
    val phoneticTransliteration: String,
    val englishTranslation: String,
    val sunnahNotes: String,
    val durationSec: Int = 4
)

data class AyahModel(
    val numberInSurah: Int,
    val arabicText: String,
    val transliteration: String,
    val translation: String,
    val tafseerIbnKathir: String,
    val tafseerTabari: String,
    val tafseerQurtubi: String,
    val tafseerSadi: String,
    val tafseerMaarifulQuran: String,
    val hadithProofQuote: String? = null,
    val memorizationMistake: String? = null
)

data class SurahModel(
    val number: Int,
    val nameEnglish: String,
    val nameArabic: String,
    val englishMeaning: String,
    val revelationType: String,
    val ayahCount: Int,
    val juzNumber: Int,
    val ayahs: List<AyahModel>
)

data class HadithModel(
    val id: String,
    val bookKey: String,
    val bookTitle: String,
    val bookTotalCount: Int,
    val hadithNumber: Int,
    val chapterTitle: String,
    val grade: String,
    val gradingScholar: String,
    val arabicText: String,
    val sanad: String,
    val matnTranslation: String,
    val bookmarked: Boolean = false,
    val sectionNumber: Int = 0
)

data class TravelConcessionModel(
    val distanceFromHomeKm: Int = 74,
    val thresholdKm: Int = 88,
    val isConcessionActive: Boolean = false,
    val homeCity: String = "London, UK",
    val currentLocationName: String = "M40 Motorway, Milepost 46",
    val vehicleHeadingDeg: Int = 42,
    val kaabaBearingDeg: Int = 118,
    val speedKmh: Int = 68,
    val bluetoothConnected: Boolean = true
)

data class WealthZakatModel(
    val nisabStandard: String = "gold",
    val goldSpotPricePerGram: Float = 88.40f,
    val silverSpotPricePerGram: Float = 1.15f,
    val cashInHandAndBank: Float = 14200f,
    val goldValueHeld: Float = 4500f,
    val silverValueHeld: Float = 320f,
    val businessInventory: Float = 0f,
    val stocksInvestments: Float = 6800f,
    val immediateDebts: Float = 1800f,
    val sadaqahJarCollected: Float = 76f,
    val sadaqahMonthlyGoal: Float = 100f
)

@kotlinx.serialization.Serializable
data class Citation(
    val title: String,
    val deepLink: String? = null,
    val referenceType: String = ""
)

data class ChatMessageModel(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: String,
    val sourceTitle: String? = null,
    val hanafiStance: String? = null,
    val malikiStance: String? = null,
    val shafiiStance: String? = null,
    val hanbaliStance: String? = null,
    val sourceDeepLink: String? = null,
    val citations: List<Citation> = emptyList()
)
