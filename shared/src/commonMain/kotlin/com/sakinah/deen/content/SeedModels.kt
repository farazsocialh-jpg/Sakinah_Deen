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
data class SeedHisnDocument(val categories: List<SeedCategory> = emptyList())

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
    val translation: String = "",
    val reference: String = ""
)
