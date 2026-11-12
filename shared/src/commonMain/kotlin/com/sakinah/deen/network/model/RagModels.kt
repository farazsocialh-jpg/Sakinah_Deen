package com.sakinah.deen.network.model

import kotlinx.serialization.Serializable

@Serializable
data class RagQueryRequest(
    val query: String,
    val conversationHistory: List<ChatMessageDto> = emptyList(),
    val preferredLanguage: String = "en",
    val schoolOfThoughtContext: String? = null // HANAFI, SHAFI_I, MALIKI, HANBALI, or ALL
)

@Serializable
data class ChatMessageDto(
    val role: String, // "user" or "assistant"
    val content: String
)

@Serializable
data class ProofSourceDto(
    val title: String,         // e.g. "Sahih al-Bukhari #5063" or "Surah Al-Baqarah 2:183"
    val referenceType: String, // "QURAN", "HADITH", "TAFSEER", "FIQH_MANUAL"
    val primaryTextArabic: String? = null,
    val translation: String,
    val navigationDeepLink: String // Links internally to Screen.Quran or Screen.Hadith
)

sealed class RagStreamChunk {
    data class TextDelta(val delta: String) : RagStreamChunk()
    data class SourceCitation(val source: ProofSourceDto) : RagStreamChunk()
    data class MadhhabBreakdown(val school: String, val summary: String) : RagStreamChunk()
    data class GuardrailDisclaimer(val notice: String) : RagStreamChunk()
    data object Complete : RagStreamChunk()
}
