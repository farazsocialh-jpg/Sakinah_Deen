package com.sakinah.deen.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.sp

private val INLINE_QURAN_REGEX = Regex("""\b(\d{1,3})\s*:\s*(\d{1,3})(?:\s*[-–]\s*(\d{1,3}))?\b""")

/** Number of ayat in each surah, indexed from surah 1. */
private val AYAH_COUNTS = intArrayOf(
    7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111,
    110, 98, 135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83,
    182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96,
    29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31,
    50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5,
    8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6
)

private fun isIdentifierChar(c: Char): Boolean = c.isLetterOrDigit()
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

private data class MarkdownSpan(val range: IntRange, val style: SpanStyle)

private data class ParsedMarkdown(
    val text: String,
    val spans: List<MarkdownSpan>
)

private val HEADING_PREFIX_REGEX = Regex("^\\s{0,3}#{1,6}\\s+")

/**
 * Turns the AI reply's Markdown into plain text plus styled spans: **bold**, *italic*,
 * ### headings and > blockquotes stop appearing as raw symbols in the chat bubble.
 */
private fun parseMarkdown(raw: String, blockquoteColor: Color): ParsedMarkdown {
    val out = StringBuilder()
    val spans = mutableListOf<MarkdownSpan>()
    val lines = raw.replace("\r\n", "\n").split('\n')

    fun appendInline(line: String) {
        var i = 0
        while (i < line.length) {
            when {
                line.startsWith("***", i) || line.startsWith("___", i) -> {
                    val marker = line.substring(i, i + 3)
                    val close = line.indexOf(marker, i + 3)
                    if (close > i + 3) {
                        val start = out.length
                        out.append(line.substring(i + 3, close))
                        spans += MarkdownSpan(start until out.length, SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                        i = close + 3
                    } else {
                        out.append(line[i]); i++
                    }
                }
                line.startsWith("**", i) || line.startsWith("__", i) -> {
                    val marker = line.substring(i, i + 2)
                    val close = line.indexOf(marker, i + 2)
                    if (close > i + 2) {
                        val start = out.length
                        out.append(line.substring(i + 2, close))
                        spans += MarkdownSpan(start until out.length, SpanStyle(fontWeight = FontWeight.Bold))
                        i = close + 2
                    } else {
                        out.append(line[i]); i++
                    }
                }
                line[i] == '*' -> {
                    val close = line.indexOf('*', i + 1)
                    if (close > i + 1) {
                        val start = out.length
                        out.append(line.substring(i + 1, close))
                        spans += MarkdownSpan(start until out.length, SpanStyle(fontStyle = FontStyle.Italic))
                        i = close + 1
                    } else {
                        out.append(line[i]); i++
                    }
                }
                line[i] == '_' -> {
                    val boundaryBefore = i == 0 || !isIdentifierChar(line[i - 1])
                    val close = if (boundaryBefore) line.indexOf('_', i + 1) else -1
                    val boundaryAfter = close > 0 &&
                        (close == line.lastIndex || !isIdentifierChar(line[close + 1]))
                    if (close > i + 1 && boundaryAfter) {
                        val start = out.length
                        out.append(line.substring(i + 1, close))
                        spans += MarkdownSpan(start until out.length, SpanStyle(fontStyle = FontStyle.Italic))
                        i = close + 1
                    } else {
                        out.append(line[i]); i++
                    }
                }
                else -> {
                    out.append(line[i]); i++
                }
            }
        }
    }

    lines.forEachIndexed { index, original ->
        var line = original
        val isBlockquote = line.trimStart().startsWith(">")
        if (isBlockquote) {
            line = line.trimStart().trimStart('>').trimStart()
        }
        val heading = HEADING_PREFIX_REGEX.find(line)
        if (heading != null) {
            line = line.substring(heading.range.last + 1)
        }
        val trimmed = line.trimStart()
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            line = "• " + trimmed.substring(2)
        }
        if (isBlockquote) {
            line = "▍ $line"
        }

        val lineStart = out.length
        appendInline(line)
        val lineEnd = out.length
        if (lineEnd > lineStart) {
            if (heading != null) {
                spans += MarkdownSpan(lineStart until lineEnd, SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp))
            }
            if (isBlockquote) {
                spans += MarkdownSpan(lineStart until lineEnd, SpanStyle(color = blockquoteColor, fontStyle = FontStyle.Italic))
            }
        }
        if (index != lines.lastIndex) out.append('\n')
    }

    return ParsedMarkdown(text = out.toString(), spans = spans)
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

    val blockquoteColor = MaterialTheme.colorScheme.onSurfaceVariant
    val annotated = remember(text, linkStyle, blockquoteColor) {
        val parsed = parseMarkdown(text, blockquoteColor)
        val plain = parsed.text
        val matches = buildList {
            INLINE_HADITH_REGEX.findAll(plain).forEach { match ->
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
                    rawBook.contains("muwatta") || rawBook.contains("malik") -> "malik"
                    else -> return@forEach
                }
                val sectionHint = extractSectionHint(plain, match.range)
                add(CitationHit(range = match.range, hadith = Triple(bookKey, number, sectionHint)))
            }
            INLINE_QURAN_REGEX.findAll(plain).forEach { match ->
                val surah = match.groupValues[1].toIntOrNull() ?: return@forEach
                val ayah = match.groupValues[2].toIntOrNull() ?: return@forEach
                val endAyah = match.groupValues.getOrNull(3)?.toIntOrNull() ?: ayah
                val maxAyah = AYAH_COUNTS.getOrNull(surah - 1) ?: return@forEach
                if (ayah >= 1 && endAyah >= ayah && endAyah <= maxAyah) {
                    add(CitationHit(range = match.range, quran = surah to ayah))
                }
            }
        }.sortedBy { it.range.first }

        buildAnnotatedString {
            var cursor = 0
            for (hit in matches) {
                if (hit.range.first < cursor) continue
                append(plain.substring(cursor, hit.range.first))
                val label = plain.substring(hit.range.first, hit.range.last + 1)
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
            if (cursor < plain.length) append(plain.substring(cursor))

            // Markdown styling is applied last so it can overlay the citation links.
            parsed.spans.forEach { span ->
                if (span.range.first >= 0 && span.range.last + 1 <= length) {
                    addStyle(span.style, span.range.first, span.range.last + 1)
                }
            }
        }
    }

    Text(text = annotated, style = style, modifier = modifier)
}
