package com.sakinah.deen.content

import kotlinx.serialization.Serializable

@Serializable
data class SeedQuranDocument(val surahs: List<SeedSurah> = emptyList())

@Serializable
data class SeedSurah(
    val number: Int = 0,
    val nameEnglish: String = "",
    val nameArabic: String = "",
    val meaning: String = "",
    val revelationPlace: String = "",
    val versesCount: Int = 0,
    val ayahs: List<SeedAyah> = emptyList()
)

@Serializable
data class SeedAyah(
    val number: Int = 0,
    val arabic: String = "",
    val translation: String = ""
)

@Serializable
data class SeedHadithDocument(
    val bookKey: String = "nawawi",
    val title: String = "",
    val hadiths: List<SeedHadith> = emptyList()
)

@Serializable
data class SeedHadith(
    val number: Int? = null,
    val arabic: String = "",
    val english: String = "",
    val grade: String = "",
    val gradingScholar: String = ""
)

@Serializable
data class SeedHisnDocument(
    val version: Int = 1,
    /** How the text was obtained and cross-checked; shown in the Adhkar library footer. */
    val source: String = "",
    val note: String = "",
    /** e.g. "pending_scholarly_review" — surfaced so the UI never implies a fatwa. */
    val reviewStatus: String = "",
    val categories: List<SeedCategory> = emptyList()
)

@Serializable
data class SeedCategory(
    val key: String = "",
    val title: String = "",
    val items: List<SeedAdhkar> = emptyList()
)

@Serializable
data class SeedAdhkar(
    val id: String = "",
    val title: String = "",
    val arabic: String = "",
    /** Latin transliteration, so a non-Arabic reader can attempt the wording. */
    val transliteration: String = "",
    val translation: String = "",
    /** Book + number, e.g. "Sahih Muslim 597". Never left blank in the bundled set. */
    val reference: String = "",
    /** Sahih / Hasan / Sahih (al-Albani) as published in Hisn al-Muslim. */
    val grading: String = "",
    /** How many times the wording is repeated; at least 1. */
    val repeatCount: Int = 1,
    /** Differences between narrations, e.g. an added phrase on return from travel. */
    val variantNote: String = ""
)
