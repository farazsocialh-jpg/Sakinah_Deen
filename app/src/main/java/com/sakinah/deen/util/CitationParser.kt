package com.sakinah.deen.util

/** A Quran reference found in prose: `2:255`, `2:255-257`, `Quran 2:255`, `2:255–257`. */
data class QuranRef(val surah: Int, val ayahStart: Int, val ayahEnd: Int = ayahStart) {
    val deepLink: String get() = "quran/$surah/$ayahStart"
    val label: String
        get() = if (ayahEnd > ayahStart) "$surah:$ayahStart-$ayahEnd" else "$surah:$ayahStart"
}

/** A hadith reference: a recognised collection plus a hadith number. */
data class HadithRef(
    val bookKey: String,
    val number: Int,
    val collectionLabel: String,
    val sectionHint: String? = null
) {
    val deepLink: String get() = "hadith/$bookKey/$number"
}

/** Half-open character span inside the analysed text, with what it points at. */
data class CitationMatch(
    val start: Int,
    val endExclusive: Int,
    val quran: QuranRef? = null,
    val hadith: HadithRef? = null
) {
    val range: IntRange get() = start until endExclusive
}

/**
 * Extracts scholarly references from model prose so they can be rendered tappable.
 *
 * Handles what scholars actually write: Quran ranges (`2:255-257`, en/em dash variants,
 * `Quran 2:255`), the ten recognised hadith collections under their common aliases
 * (`Sahih al-Bukhari`, `Bukhari`, `Sunan Abi Dawud`, `Muwatta Malik`, `Musnad Ahmad`,
 * `40 Hadith Nawawi`, …), optional `#`/`No.`/`Hadith` markers, Arabic-Indic digits, and
 * page/section hints such as "Book of Zakat". Anything malformed (out-of-range surah,
 * reversed range, ayah past the end of the surah) is skipped rather than linked.
 *
 * Pure JVM code so [CitationParserTest] can pin the behaviour without a device.
 */
object CitationParser {

    /** Number of ayat in each surah, indexed from surah 1 (Madani mushaf). */
    val AYAH_COUNTS = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111,
        110, 98, 135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83,
        182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96,
        29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31,
        50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5,
        8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6
    )

    private val QURAN_REFERENCE = Regex("""(\d{1,3})\s*:\s*(\d{1,3})(?:\s*[-–—]\s*(\d{1,3}))?""")

    /**
     * Collection aliases, longest first so "Sahih al-Bukhari" wins over "Bukhari" and the
     * matched span (and therefore the tappable label) stays complete.
     */
    private val HADITH_COLLECTIONS: List<Triple<String, String, String>> = listOf(
        Triple("sahih al-bukhari", "bukhari", "Sahih al-Bukhari"),
        Triple("sahih albukhari", "bukhari", "Sahih al-Bukhari"),
        Triple("al-bukhari", "bukhari", "Sahih al-Bukhari"),
        Triple("bukhari", "bukhari", "Sahih al-Bukhari"),
        Triple("sahih muslim", "muslim", "Sahih Muslim"),
        Triple("muslim", "muslim", "Sahih Muslim"),
        Triple("sunun abi dawud", "abudawud", "Sunan Abi Dawud"),
        Triple("sunan abi dawud", "abudawud", "Sunan Abi Dawud"),
        Triple("sunan abu dawud", "abudawud", "Sunan Abi Dawud"),
        Triple("abu dawud", "abudawud", "Sunan Abi Dawud"),
        Triple("abi dawud", "abudawud", "Sunan Abi Dawud"),
        Triple("abudawud", "abudawud", "Sunan Abi Dawud"),
        Triple("jami' at-tirmidhi", "tirmidhi", "Jami' at-Tirmidhi"),
        Triple("jami at-tirmidhi", "tirmidhi", "Jami' at-Tirmidhi"),
        Triple("jami' at tirmidhi", "tirmidhi", "Jami' at-Tirmidhi"),
        Triple("sunan at-tirmidhi", "tirmidhi", "Jami' at-Tirmidhi"),
        Triple("tirmidhi", "tirmidhi", "Jami' at-Tirmidhi"),
        Triple("sunan an-nasa'i", "nasai", "Sunan an-Nasa'i"),
        Triple("sunan an nasai", "nasai", "Sunan an-Nasa'i"),
        Triple("sunan an-nasai", "nasai", "Sunan an-Nasa'i"),
        Triple("nasa'i", "nasai", "Sunan an-Nasa'i"),
        Triple("nasai", "nasai", "Sunan an-Nasa'i"),
        Triple("sunan ibn majah", "ibnmajah", "Sunan Ibn Majah"),
        Triple("ibn majah", "ibnmajah", "Sunan Ibn Majah"),
        Triple("ibnmajah", "ibnmajah", "Sunan Ibn Majah"),
        Triple("muwatta malik", "malik", "Muwatta Malik"),
        Triple("muwatta", "malik", "Muwatta Malik"),
        Triple("musnad ahmad", "ahmad", "Musnad Ahmad"),
        Triple("sunan ad-darimi", "darimi", "Sunan ad-Darimi"),
        Triple("darimi", "darimi", "Sunan ad-Darimi"),
        Triple("riyad as-salihin", "nawawi", "Riyad as-Salihin"),
        Triple("40 hadith nawawi", "nawawi", "40 Hadith Nawawi"),
        Triple("forty hadith nawawi", "nawawi", "40 Hadith Nawawi"),
        Triple("nawawi", "nawawi", "40 Hadith Nawawi"),
        Triple("ahmad", "ahmad", "Musnad Ahmad"),
        Triple("malik", "malik", "Muwatta Malik")
    )

    private val HADITH_NUMBER = Regex(
        """\s*(?:no\.?|hadith|#|:)?\s*(\d{1,5})\b""",
        RegexOption.IGNORE_CASE
    )

    private val SECTION_HINT = Regex(
        """(?i)\b(?:book\s+of|kitab(?:\s+al-)?)\s+[A-Za-z'’ -]{2,60}"""
    )

    private const val MAX_GAP_CHARS = 40

    /**
     * All tappable references in [text], sorted by position and non-overlapping.
     * Quran ranges win over hadith matches that start at the same character.
     */
    fun find(text: String): List<CitationMatch> {
        if (text.isBlank()) return emptyList()
        val normalized = arabicIndicToAsciiDigits(text)
        val matches = mutableListOf<CitationMatch>()

        QURAN_REFERENCE.findAll(normalized).forEach { match ->
            val surah = match.groupValues[1].toIntOrNull() ?: return@forEach
            val ayah = match.groupValues[2].toIntOrNull() ?: return@forEach
            val endAyah = match.groupValues[3].toIntOrNull() ?: ayah
            val maxAyah = AYAH_COUNTS.getOrNull(surah - 1) ?: return@forEach
            if (ayah < 1 || endAyah < ayah || endAyah > maxAyah) return@forEach
            matches += CitationMatch(
                start = match.range.first,
                endExclusive = match.range.last + 1,
                quran = QuranRef(surah, ayah, endAyah)
            )
        }

        HADITH_COLLECTIONS.forEach { (alias, bookKey, label) ->
            findAllIgnoreCase(normalized, alias).forEach { aliasStart ->
                val numberStart = aliasStart + alias.length
                val tail = normalized.substring(
                    numberStart,
                    (numberStart + MAX_GAP_CHARS).coerceAtMost(normalized.length)
                )
                val numberMatch = HADITH_NUMBER.find(tail) ?: return@forEach
                // Never swallow a Quran reference as a hadith number.
                if (tail.substring(0, numberMatch.range.first).contains(':')) return@forEach
                val number = numberMatch.groupValues[1].toIntOrNull() ?: return@forEach
                if (number <= 0) return@forEach
                // "Sahih Muslim 2:255" is a Quran reference, not hadith #2.
                if (tail.getOrNull(numberMatch.range.last + 1) == ':') return@forEach
                val start = aliasStart
                val end = numberStart + numberMatch.range.last + 1
                matches += CitationMatch(
                    start = start,
                    endExclusive = end,
                    hadith = HadithRef(
                        bookKey = bookKey,
                        number = number,
                        collectionLabel = label,
                        sectionHint = extractSectionHint(normalized, start..(end - 1))
                    )
                )
            }
        }

        return matches
            .sortedWith(compareBy({ it.start }, { -(it.endExclusive - it.start) }))
            .fold(mutableListOf<CitationMatch>()) { accepted, candidate ->
                val overlaps = accepted.any {
                    candidate.start < it.endExclusive && it.start < candidate.endExclusive
                }
                if (!overlaps) accepted += candidate
                accepted
            }
    }

    /** Pulls a Kitab/Book phrase out of the citation context around a matched reference. */
    fun extractSectionHint(text: String, matchRange: IntRange? = null): String? {
        val context = if (matchRange == null) {
            text
        } else {
            val start = (matchRange.first - 80).coerceAtLeast(0)
            val end = (matchRange.last + 81).coerceAtMost(text.length)
            text.substring(start, end)
        }
        return SECTION_HINT.find(context)?.value
            ?.trim()
            ?.trimEnd('.', ',', ';', ':', ')')
            ?.takeIf { it.isNotBlank() }
    }

    /** Maps ٠١٢… to 0-1… without changing length, so match offsets stay valid. */
    private fun arabicIndicToAsciiDigits(text: String): String {
        var needsMapping = false
        for (ch in text) {
            if (ch in '\u0660'..'\u0669' || ch in '\u06F0'..'\u06F9') {
                needsMapping = true
                break
            }
        }
        if (!needsMapping) return text
        val out = StringBuilder(text.length)
        text.forEach { ch ->
            when (ch) {
                in '\u0660'..'\u0669' -> out.append('0' + (ch - '\u0660'))
                in '\u06F0'..'\u06F9' -> out.append('0' + (ch - '\u06F0'))
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    private fun findAllIgnoreCase(text: String, needle: String): List<Int> {
        val lower = text.lowercase()
        val target = needle.lowercase()
        val hits = mutableListOf<Int>()
        var index = lower.indexOf(target)
        while (index >= 0) {
            val before = lower.getOrNull(index - 1)
            val after = lower.getOrNull(index + target.length)
            val boundaryBefore = before == null || !before.isLetterOrDigit()
            val boundaryAfter = after == null || !after.isLetterOrDigit()
            if (boundaryBefore && boundaryAfter) hits += index
            index = lower.indexOf(target, index + 1)
        }
        return hits
    }
}
