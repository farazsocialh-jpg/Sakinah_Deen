package com.sakinah.deen.data.model

import kotlinx.serialization.Serializable

/**
 * One tafsir edition advertised by Quran.com API v4 `/resources/tafsirs`.
 *
 * The catalog is cached for offline display; the commentary itself is cached per chapter in
 * Room's `tafsir_entries` table under `tafsir_<id>`.
 */
@Serializable
data class TafsirResource(
    val id: Int,
    val name: String = "",
    val authorName: String = "",
    val slug: String = "",
    val languageName: String = "",
    /** Localized name from `translated_name.name`, used when [name] is empty. */
    val translatedName: String = "",
    /** Normalized ISO 639-1 code derived from [languageName] (en, ar, ur, bn, ru, ku, …). */
    val languageCode: String = ""
)

/** Shared presentation helpers for commentary text coming from any provider. */
object TafsirText {
    private val SUP_TAG = Regex("<sup[^>]*>.*?</sup>", RegexOption.DOT_MATCHES_ALL)
    private val HTML_TAG = Regex("<[^>]+>")
    private val WHITESPACE = Regex("\\s+")

    /** Removes live-API HTML without touching the Arabic/Quranic characters inside it. */
    fun clean(raw: String): String = raw
        .replace(SUP_TAG, "")
        .replace(HTML_TAG, " ")
        .replace("&nbsp;", " ")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace(WHITESPACE, " ")
        .trim()

    /** Scripts that must render right-to-left even when the app locale is English. */
    fun isRtl(languageCode: String): Boolean = when (languageCode.trim().lowercase()) {
        "ar", "ur", "fa", "ku", "ckb", "he", "ps", "sd", "yi" -> true
        else -> false
    }
}
