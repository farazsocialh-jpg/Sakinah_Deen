package com.sakinah.deen.util

/**
 * Normalises HTML fragments occasionally returned by the Scholar model.
 *
 * The backend usually answers in Markdown, but Kalimat/Gemini sometimes wraps Arabic
 * quotations in `<p dir="rtl" lang="ar">…</p>`. Rendering that literally leaked tags into
 * chat bubbles. This converts block boundaries to newlines and drops the remaining tags.
 */
fun stripHtmlForDisplay(raw: String): String {
    if ('<' !in raw && '&' !in raw) return raw
    return raw
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</(p|div|li|h[1-6])\\s*>"), "\n\n")
        .replace(Regex("(?i)<li[^>]*>"), "\n• ")
        .replace(Regex("(?is)<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("[ \\t]+\n"), "\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
