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
import com.sakinah.deen.util.CitationParser
import com.sakinah.deen.util.stripHtmlForDisplay

private fun isIdentifierChar(c: Char): Boolean = c.isLetterOrDigit()

/** Delegates to the pure parser so citation behaviour is unit-tested off-device. */
internal fun extractSectionHint(text: String, matchRange: IntRange? = null): String? =
    CitationParser.extractSectionHint(text, matchRange)

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

@Composable
fun ClickableCitationText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    onQuranClick: (surah: Int, ayah: Int) -> Unit,
    onHadithClick: (bookKey: String, number: Int, sectionHint: String?) -> Unit
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
        // Model output occasionally contains raw HTML wrappers around Arabic quotes.
        val parsed = parseMarkdown(stripHtmlForDisplay(text), blockquoteColor)
        val plain = parsed.text
        // Ranges, aliases and malformed references are handled by the pure parser.
        val matches = CitationParser.find(plain)

        buildAnnotatedString {
            var cursor = 0
            for (hit in matches) {
                if (hit.start < cursor) continue
                append(plain.substring(cursor, hit.start))
                val label = plain.substring(hit.start, hit.endExclusive)
                val quran = hit.quran
                val hadith = hit.hadith
                val link = when {
                    quran != null -> LinkAnnotation.Clickable(
                        tag = "quran:${quran.surah}:${quran.ayahStart}",
                        styles = linkStyle,
                        linkInteractionListener = {
                            currentOnQuranClick(quran.surah, quran.ayahStart)
                        }
                    )
                    hadith != null -> LinkAnnotation.Clickable(
                        tag = "hadith:${hadith.bookKey}:${hadith.number}",
                        styles = linkStyle,
                        linkInteractionListener = {
                            currentOnHadithClick(hadith.bookKey, hadith.number, hadith.sectionHint)
                        }
                    )
                    else -> null
                }
                if (link == null) {
                    append(label)
                } else {
                    withLink(link) { append(label) }
                }
                cursor = hit.endExclusive
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
