package com.sakinah.deen.data.model

import kotlinx.serialization.Serializable

@Serializable
data class HadithEditionResource(
    val editionName: String,
    val bookKey: String,
    val bookTitle: String = "",
    val languageCode: String = "",
    val languageName: String = "",
    val hasSections: Boolean = false,
    val direction: String = "ltr",
    val link: String = ""
)

@Serializable
data class HadithBookCatalog(
    val bookKey: String,
    val bookTitle: String = "",
    val editions: List<HadithEditionResource> = emptyList(),
    val sections: Map<Int, String> = emptyMap()
)