package com.sakinah.deen.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

private val INLINE_QURAN_REGEX = Regex("""\b(\d{1,3})\s*:\s*(\d{1,3})\b""")
private val INLINE_HADITH_REGEX = Regex(
    """(?i)\b(Sahih\s+al-?Bukhari|Bukhari|Sahih\s+Muslim|Muslim|Abu\s+Dawud|Tirmidhi|Nasa'?i|Ibn\s+Majah|Muwatta|Malik|Nawawi)\b[^.\n]{0,40}?\b(?:No\.?|Hadith|#|:)?\s*(\d{1,5})\b"""
)
private val SECTION_HINT_REGEX = Regex(
    """(?i)\b(?:Book\s+of|Kitab(?:\s+al-)?)\s+[A-Za-z'’ -]{2,60}"""
)

/** Pulls a Kitab/Book phrase out of the citation context around a matched reference. */
internal fun extractSectionHint(text: String, matchRange: IntRange? = null): String? {
    val context = if (matchRange == null) {
        text
    } else {
        val start = (matchRange.first - 80).coerceAtLeast(0)
        val end = (matchRange.last + 81).coerceAtMost(text.length)
        text.substring(start, end)
    }
    return SECTION_HINT_REGEX.find(context)?.value
        ?.trim()
        ?.trimEnd('.', ',', ';', ':', ')')
        ?.takeIf { it.isNotBlank() }
}

private data class CitationHit(
    val range: IntRange,
    val quran: Pair<Int, Int>? = null,
    val hadith: Triple<String, Int, String?>? = null
)

@Composable
fun ClickableCitationText(
    text: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    onQuranClick: (surah: Int, ayah: Int) -> Unit,
    onHadithClick: (bookKey: String, number: Int, sectionHint: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val currentOnQuranClick by rememberUpdatedState(onQuranClick)
    val currentOnHadithClick by rememberUpdatedState(onHadithClick)
    val linkStyle = remember(linkColor) {
        TextLinkStyles(
            style = SpanStyle(
                color = linkColor,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline
            )
        )
    }

    val annotated = remember(text, linkStyle) {
        val matches = buildList {
            INLINE_HADITH_REGEX.findAll(text).forEach { match ->
                val number = match.groupValues[2].toIntOrNull() ?: return@forEach
                val rawBook = match.groupValues[1].lowercase()
                val bookKey = when {
                    rawBook.contains("muslim") -> "muslim"
                    rawBook.contains("bukhari") -> "bukhari"
                    rawBook.contains("dawud") -> "abudawud"
                    rawBook.contains("tirmidhi") -> "tirmidhi"
                    rawBook.contains("nasai") || rawBook.contains("nasa'i") -> "nasai"
                    rawBook.contains("majah") -> "ibnmajah"
                    rawBook.contains("nawawi") -> "nawawi"
                    else -> "malik"
                }
                val sectionHint = extractSectionHint(text, match.range)
                add(CitationHit(range = match.range, hadith = Triple(bookKey, number, sectionHint)))
            }
            INLINE_QURAN_REGEX.findAll(text).forEach { match ->
                val surah = match.groupValues[1].toIntOrNull() ?: return@forEach
                val ayah = match.groupValues[2].toIntOrNull() ?: return@forEach
                if (surah in 1..114 && ayah >= 1) {
                    add(CitationHit(range = match.range, quran = surah to ayah))
                }
            }
        }.sortedBy { it.range.first }

        buildAnnotatedString {
            var cursor = 0
            for (hit in matches) {
                if (hit.range.first < cursor) continue
                append(text.substring(cursor, hit.range.first))
                val label = text.substring(hit.range.first, hit.range.last + 1)
                val quran = hit.quran
                val hadith = hit.hadith
                val link = when {
                    quran != null -> LinkAnnotation.Clickable(
                        tag = "quran:${quran.first}:${quran.second}",
                        styles = linkStyle,
                        linkInteractionListener = { currentOnQuranClick(quran.first, quran.second) }
                    )
                    hadith != null -> LinkAnnotation.Clickable(
                        tag = "hadith:${hadith.first}:${hadith.second}",
                        styles = linkStyle,
                        linkInteractionListener = {
                            currentOnHadithClick(hadith.first, hadith.second, hadith.third)
                        }
                    )
                    else -> null
                }
                if (link == null) {
                    append(label)
                } else {
                    withLink(link) { append(label) }
                }
                cursor = hit.range.last + 1
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }

    Text(text = annotated, style = style, modifier = modifier)
}
