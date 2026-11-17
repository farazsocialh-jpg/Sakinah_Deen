package com.sakinah.deen.data.quran

import com.sakinah.deen.data.database.entity.QuranAyahEntity

/**
 * What the reader can actually open with no network, derived from the rows in Room —
 * never from a preference or a "download finished" flag. The Quran and Settings screens
 * both render [headline] so the claim "offline" is always backed by real content.
 */
data class QuranOfflineStatus(
    val surahsAvailable: Int = 0,
    val ayahsAvailable: Int = 0,
    val totalSurahs: Int = QuranOfflinePack.TOTAL_SURAHS,
    val totalAyahs: Int = QuranOfflinePack.TOTAL_AYAHS
) {
    val isComplete: Boolean
        get() = QuranOfflinePack.isComplete(surahsAvailable, ayahsAvailable)

    /** "Offline Ready (114/114)" or "Partial Offline (38/114 — Download Remaining)". */
    val headline: String
        get() = when {
            isComplete -> "Offline Ready ($totalSurahs/$totalSurahs)"
            surahsAvailable <= 0 -> "Offline Pack Missing (0/$totalSurahs)"
            else -> "Partial Offline ($surahsAvailable/$totalSurahs — Download Remaining)"
        }

    val detail: String
        get() = buildString {
            append(ayahsAvailable)
            append(" of ")
            append(totalAyahs)
            append(" ayahs cached · ")
            append(QuranOfflinePack.ARABIC_EDITION)
            append(" + ")
            append(QuranOfflinePack.TRANSLATION_EDITION)
        }

    companion object {
        fun from(rows: List<QuranAyahEntity>): QuranOfflineStatus = QuranOfflineStatus(
            surahsAvailable = rows.asSequence()
                .map { it.surahNumber }
                .distinct()
                .count(),
            ayahsAvailable = rows.size
        )

        fun fromCounts(surahCount: Int, ayahCount: Int): QuranOfflineStatus =
            QuranOfflineStatus(surahsAvailable = surahCount, ayahsAvailable = ayahCount)
    }
}
