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
    val isoCode: String = ""
)