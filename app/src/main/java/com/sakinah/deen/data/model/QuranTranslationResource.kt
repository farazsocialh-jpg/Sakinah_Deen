package com.sakinah.deen.data.model

import kotlinx.serialization.Serializable

/**
 * One authentic translation available from Quran.com (or a fallback provider).
 */
@Serializable
data class QuranTranslationResource(
    val id: Int,
    val name: String,
    val authorName: String = "",
    val languageName: String = "",
    val isoCode: String = "",
    /** Provider-specific edition identifier used to download surah text. */
    val editionKey: String = "",
    /** quran_com, alquran_cloud, or quran_api. */
    val source: String = SOURCE_QURAN_COM
) {
    companion object {
        const val SOURCE_QURAN_COM = "quran_com"
        const val SOURCE_ALQURAN_CLOUD = "alquran_cloud"
        const val SOURCE_QURAN_API = "quran_api"
    }
}