package com.sakinah.deen.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for the free Quran.com v4 API. Field names mirror the wire format exactly;
 * the JSON parser is configured with ignoreUnknownKeys so additive API changes are safe.
 */
@Serializable
data class ChaptersResponse(
    val chapters: List<ChapterDto> = emptyList()
)

@Serializable
data class ChapterDto(
    val id: Int,
    @SerialName("name_simple") val nameSimple: String = "",
    @SerialName("name_arabic") val nameArabic: String = "",
    @SerialName("verses_count") val versesCount: Int = 0,
    @SerialName("revelation_place") val revelationPlace: String? = null,
    @SerialName("translated_name") val translatedName: TranslatedNameDto? = null
)

@Serializable
data class TranslatedNameDto(
    val name: String = ""
)

@Serializable
data class UthmaniVersesResponse(
    val verses: List<UthmaniVerseDto> = emptyList()
)

@Serializable
data class UthmaniVerseDto(
    val id: Int = 0,
    @SerialName("verse_key") val verseKey: String = "",
    @SerialName("text_uthmani") val textUthmani: String = ""
)

@Serializable
data class TranslationsResponse(
    val translations: List<TranslationDto> = emptyList()
)

@Serializable
data class TranslationDto(
    @SerialName("resource_id") val resourceId: Int = 0,
    val text: String = ""
)

@Serializable
data class ChapterAudioResponse(
    @SerialName("audio_file") val audioFile: AudioFileDto? = null
)

@Serializable
data class AudioFileDto(
    @SerialName("audio_url") val audioUrl: String = ""
)

@Serializable
data class TafsirResponse(
    val tafsir: TafsirDto? = null
)

@Serializable
data class TafsirDto(
    val text: String = "",
    @SerialName("resource_name") val resourceName: String = ""
)
