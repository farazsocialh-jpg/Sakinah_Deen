package com.sakinah.deen.util

/**
 * Normalizes Arabic text for diacritic-insensitive search across Quran and Hadith corpora
 * and formats verse numbers with Eastern Arabic-Indic digits for Mus-haf rendering.
 */
object ArabicNormalizer {

    private val DIACRITICS_REGEX = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
    private val ARABIC_CHAR_REGEX = Regex("[\\u0600-\\u06FF]")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val ARABIC_INDIC_DIGITS = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val stripped = DIACRITICS_REGEX.replace(input, "")
        val sb = StringBuilder(stripped.length)
        for (ch in stripped) {
            sb.append(
                when (ch) {
                    'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                    'ى' -> 'ي'
                    'ة' -> 'ه'
                    'ؤ' -> 'و'
                    'ئ' -> 'ي'
                    else -> ch
                }
            )
        }
        return WHITESPACE_REGEX.replace(sb.toString().trim().lowercase(), " ")
    }

    fun containsArabic(text: String): Boolean = ARABIC_CHAR_REGEX.containsMatchIn(text)

    fun matches(text: String, query: String): Boolean {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return true
        if (text.contains(cleanQuery, ignoreCase = true)) return true
        if (containsArabic(cleanQuery) || containsArabic(text)) {
            val normQuery = normalize(cleanQuery)
            if (normQuery.isNotBlank() && normalize(text).contains(normQuery)) {
                return true
            }
        }
        return false
    }

    fun toArabicIndicNumerals(number: Int): String {
        if (number <= 0) return ARABIC_INDIC_DIGITS[0].toString()
        return number.toString().map { ch ->
            if (ch in '0'..'9') ARABIC_INDIC_DIGITS[ch - '0'] else ch
        }.joinToString("")
    }
}
