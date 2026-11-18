package com.sakinah.deen.data.model

import androidx.compose.ui.graphics.Color

/**
 * Presentation metadata for a hadith collection's hardcover card.
 *
 * Every supported collection key maps to a complete entry so the Books tab never
 * renders a placeholder or an unstyled cover. Compiler names are proper nouns and
 * intentionally kept out of string resources; the surrounding labels live in
 * values/strings.xml for translation.
 */
data class HadithCollectionCover(
    val bookKey: String,
    val englishTitle: String,
    val arabicTitle: String,
    val compiler: String,
    val coverColor: Color,
    val spineColor: Color,
    val foilColor: Color
)

object HadithCollectionCovers {
    val all: List<HadithCollectionCover> = listOf(
        HadithCollectionCover(
            bookKey = "bukhari",
            englishTitle = "Sahih al-Bukhari",
            arabicTitle = "صحيح البخاري",
            compiler = "Imam al-Bukhari",
            coverColor = Color(0xFF0B4F3C),
            spineColor = Color(0xFF07382A),
            foilColor = Color(0xFFD8B45A)
        ),
        HadithCollectionCover(
            bookKey = "muslim",
            englishTitle = "Sahih Muslim",
            arabicTitle = "صحيح مسلم",
            compiler = "Imam Muslim",
            coverColor = Color(0xFF14284D),
            spineColor = Color(0xFF0C1B36),
            foilColor = Color(0xFFC9D6F2)
        ),
        HadithCollectionCover(
            bookKey = "abudawud",
            englishTitle = "Sunan Abi Dawud",
            arabicTitle = "سنن أبي داود",
            compiler = "Imam Abu Dawud",
            coverColor = Color(0xFF5E1F2A),
            spineColor = Color(0xFF43141D),
            foilColor = Color(0xFFE2C27A)
        ),
        HadithCollectionCover(
            bookKey = "tirmidhi",
            englishTitle = "Jami‘ at-Tirmidhi",
            arabicTitle = "جامع الترمذي",
            compiler = "Imam at-Tirmidhi",
            coverColor = Color(0xFF155E5E),
            spineColor = Color(0xFF0D4343),
            foilColor = Color(0xFFD9BE7C)
        ),
        HadithCollectionCover(
            bookKey = "nasai",
            englishTitle = "Sunan an-Nasa'i",
            arabicTitle = "سنن النسائي",
            compiler = "Imam an-Nasa'i",
            coverColor = Color(0xFF6B4A22),
            spineColor = Color(0xFF4C3316),
            foilColor = Color(0xFFE7C77B)
        ),
        HadithCollectionCover(
            bookKey = "ibnmajah",
            englishTitle = "Sunan Ibn Majah",
            arabicTitle = "سنن ابن ماجه",
            compiler = "Imam Ibn Majah",
            coverColor = Color(0xFF2F2C63),
            spineColor = Color(0xFF201E49),
            foilColor = Color(0xFFD8C88C)
        ),
        HadithCollectionCover(
            bookKey = "malik",
            englishTitle = "Muwatta Malik",
            arabicTitle = "موطأ مالك",
            compiler = "Imam Malik",
            coverColor = Color(0xFF3F4A1F),
            spineColor = Color(0xFF2C3415),
            foilColor = Color(0xFFDCC77B)
        ),
        HadithCollectionCover(
            bookKey = "nawawi",
            englishTitle = "40 Hadith Nawawi",
            arabicTitle = "الأربعون النووية",
            compiler = "Imam an-Nawawi",
            coverColor = Color(0xFF4F4A1C),
            spineColor = Color(0xFF38340F),
            foilColor = Color(0xFFE5CF83)
        )
    )

    private val byKey: Map<String, HadithCollectionCover> = all.associateBy { it.bookKey }

    /** Returns the cover metadata for [bookKey], falling back to a stable neutral cover. */
    fun forBook(bookKey: String): HadithCollectionCover {
        val normalized = bookKey.trim().lowercase()
        return byKey[normalized] ?: HadithCollectionCover(
            bookKey = normalized,
            englishTitle = bookKey.replaceFirstChar { it.uppercase() },
            arabicTitle = "",
            compiler = "",
            coverColor = Color(0xFF3A4A44),
            spineColor = Color(0xFF28332F),
            foilColor = Color(0xFFD6C17A)
        )
    }
}
