package com.sakinah.deen.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Which presentation the Quran reader renders: verse-by-verse study pages or the flowing
 * mushaf page. Stored separately from [QuranReadingLayout] because it is a reader state,
 * not an appearance preset, and must survive a restart exactly like the layout does.
 */
enum class QuranReadingMode {
    STUDY,
    MUSHAF;

    companion object {
        /** Resolves a stored name; null when the value is empty or unknown. */
        fun fromStoredName(raw: String?): QuranReadingMode? {
            val name = raw?.trim().orEmpty()
            if (name.isEmpty()) return null
            return entries.firstOrNull { it.name == name }
        }
    }
}

/**
 * Presentation of the Word-by-Word (WBW) row under each ayah.
 *
 * The reader now has a single presentation: verbatim QUL Arabic flowing right to left with
 * each word's meaning underneath. Tapping any word still opens the word-meaning/root sheet.
 * Previously saved modes are mapped to [INTERLINEAR_RTL] by [fromStoredName], so upgrading
 * never changes the Quranic text or loses a reader's choice.
 */
enum class WbwLayoutMode {
    INTERLINEAR_RTL;

    companion object {
        val DEFAULT = INTERLINEAR_RTL

        /** Retired presentations, kept so stored preferences migrate instead of resetting. */
        private val LEGACY_NAMES = setOf(
            "MUSHAF_TAP",
            "VERSE_BOTTOM_DOCK",
            "WORD_CARDS_RTL",
            "GLOSSARY_LIST",
            "VOCAB_QUIZ"
        )

        /** Resolves a stored name; null when the value is empty or unknown. */
        fun fromStoredName(raw: String?): WbwLayoutMode? {
            val name = raw?.trim().orEmpty()
            if (name.isEmpty()) return null
            if (name in LEGACY_NAMES) return INTERLINEAR_RTL
            return entries.firstOrNull { it.name == name }
        }
    }
}

/** One saved ayah. The timestamp orders the bookmarks sheet newest-first. */
data class AyahBookmark(
    val surahNumber: Int,
    val ayahNumber: Int,
    val savedAtMillis: Long
) {
    val key: Pair<Int, Int> get() = surahNumber to ayahNumber
}

/** Where the reader was left: chapter, verse and the list index it was scrolled to. */
data class QuranReadingPosition(
    val surahNumber: Int,
    val ayahNumber: Int,
    val scrollIndex: Int
)

/**
 * Persists the Quran reader's own state: reading mode, the last-read position and
 * per-ayah bookmarks. Kept in SharedPreferences (not Room) so a bookmark works in guest
 * mode and needs no schema migration; values are encoded defensively so a corrupted or
 * older entry is dropped instead of crashing the reader.
 */
class QuranReaderPreferences internal constructor(
    private val prefs: SharedPreferences
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    companion object {
        private const val PREFS_NAME = "sakinah_quran_reader"
        private const val KEY_READING_MODE = "quran_reading_mode"
        private const val KEY_LAST_SURAH = "quran_last_read_surah"
        private const val KEY_LAST_AYAH = "quran_last_read_ayah"
        private const val KEY_LAST_SCROLL_INDEX = "quran_last_read_scroll_index"
        private const val KEY_BOOKMARKS = "quran_ayah_bookmarks"
        private const val KEY_READING_DAYS = "quran_reading_days"
        private const val KEY_WBW_LANGUAGE = "quran_wbw_language"
        private const val KEY_WBW_SCRIPT = "quran_wbw_script"
        private const val KEY_MUSHAF_LAYOUT = "quran_mushaf_layout"
        private const val KEY_WBW_LAYOUT_MODE = "quran_wbw_layout_mode"
        private const val KEY_WBW_ENABLED = "quran_wbw_enabled"

        const val DEFAULT_WBW_LANGUAGE = "en"
        const val DEFAULT_WBW_SCRIPT = "uthmani"
        const val DEFAULT_MUSHAF_LAYOUT = "madani"
        val WBW_SCRIPTS = setOf("uthmani", "indopak")

        /** Consecutive reading days ending today, or yesterday before today's reading. */
        internal fun streakOf(days: Set<Long>, today: Long): Int {
            var cursor = if (days.contains(today)) today else today - 1
            var streak = 0
            while (days.contains(cursor)) {
                streak++
                cursor--
            }
            return streak
        }

        const val MIN_SURAH = 1
        const val MAX_SURAH = 114
    }

    /** Reader presentation; defaults to study pages for a brand-new install. */
    var readingMode: QuranReadingMode
        get() = QuranReadingMode.fromStoredName(prefs.getString(KEY_READING_MODE, null))
            ?: QuranReadingMode.STUDY
        set(value) = prefs.edit { putString(KEY_READING_MODE, value.name) }

    /** Null only on the first-ever launch, so the reader can fall back to 1:1. */
    val lastReadPosition: QuranReadingPosition?
        get() {
            if (!prefs.contains(KEY_LAST_SURAH)) return null
            val surah = prefs.getInt(KEY_LAST_SURAH, 1)
            if (surah !in MIN_SURAH..MAX_SURAH) return null
            return QuranReadingPosition(
                surahNumber = surah,
                ayahNumber = prefs.getInt(KEY_LAST_AYAH, 1).coerceAtLeast(1),
                scrollIndex = prefs.getInt(KEY_LAST_SCROLL_INDEX, 0).coerceAtLeast(0)
            )
        }

    fun saveReadingPosition(surahNumber: Int, ayahNumber: Int, scrollIndex: Int) {
        if (surahNumber !in MIN_SURAH..MAX_SURAH) return
        prefs.edit {
            putInt(KEY_LAST_SURAH, surahNumber)
            putInt(KEY_LAST_AYAH, ayahNumber.coerceAtLeast(1))
            putInt(KEY_LAST_SCROLL_INDEX, scrollIndex.coerceAtLeast(0))
        }
        recordReadingDay(java.time.LocalDate.now().toEpochDay())
    }

    /** QUL word-by-word language code (ISO-639-1/3), e.g. "en", "ur", "inh". */
    var wbwLanguage: String
        get() = prefs.getString(KEY_WBW_LANGUAGE, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_WBW_LANGUAGE
        set(value) = prefs.edit {
            putString(KEY_WBW_LANGUAGE, value.trim().ifEmpty { DEFAULT_WBW_LANGUAGE })
        }

    /** Which QUL Arabic script the word-by-word row renders: "uthmani" or "indopak". */
    var wbwScript: String
        get() = prefs.getString(KEY_WBW_SCRIPT, null)
            ?.trim()
            ?.takeIf { it in WBW_SCRIPTS }
            ?: DEFAULT_WBW_SCRIPT
        set(value) = prefs.edit {
            putString(KEY_WBW_SCRIPT, value.trim().takeIf { it in WBW_SCRIPTS } ?: DEFAULT_WBW_SCRIPT)
        }

    /** Word-by-word presentation; survives a restart like the rest of the reader state. */
    var wbwLayoutMode: WbwLayoutMode
        get() = WbwLayoutMode.fromStoredName(prefs.getString(KEY_WBW_LAYOUT_MODE, null))
            ?: WbwLayoutMode.DEFAULT
        set(value) = prefs.edit { putString(KEY_WBW_LAYOUT_MODE, value.name) }

    /** Whether the word-by-word strip is shown under each ayah at all. */
    var wbwEnabled: Boolean
        get() = prefs.getBoolean(KEY_WBW_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_WBW_ENABLED, value) }

    /** Mushaf page layout key bundled in the QUL database. */
    var mushafLayout: String
        get() = prefs.getString(KEY_MUSHAF_LAYOUT, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_MUSHAF_LAYOUT
        set(value) = prefs.edit {
            putString(KEY_MUSHAF_LAYOUT, value.trim().ifEmpty { DEFAULT_MUSHAF_LAYOUT })
        }

    /** Days on which the reader was used; drives the home-screen streak widget. */
    val readingDays: Set<Long>
        get() = prefs.getString(KEY_READING_DAYS, null)
            ?.split('|')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.toSet()
            .orEmpty()

    /** Records today as a reading day, keeping the last ~13 months only. */
    fun recordReadingDay(epochDay: Long) {
        val days = (readingDays + epochDay).sorted().takeLast(400).toSet()
        prefs.edit { putString(KEY_READING_DAYS, days.joinToString("|")) }
    }

    fun currentStreak(today: Long = java.time.LocalDate.now().toEpochDay()): Int =
        streakOf(readingDays, today)

    /** Saved ayahs, newest first. */
    var bookmarks: List<AyahBookmark>
        get() = decodeBookmarks(prefs.getString(KEY_BOOKMARKS, null))
        set(value) {
            val encoded = value
                .distinctBy { it.key }
                .sortedWith(
                    compareByDescending<AyahBookmark> { it.savedAtMillis }
                        .thenBy { it.surahNumber }
                        .thenBy { it.ayahNumber }
                )
                .joinToString("|") { "${it.surahNumber}:${it.ayahNumber}:${it.savedAtMillis}" }
            if (encoded.isBlank()) {
                prefs.edit { remove(KEY_BOOKMARKS) }
            } else {
                prefs.edit { putString(KEY_BOOKMARKS, encoded) }
            }
        }

    fun isBookmarked(surahNumber: Int, ayahNumber: Int): Boolean =
        bookmarks.any { it.surahNumber == surahNumber && it.ayahNumber == ayahNumber }

    /** Adds or removes one ayah and returns the new bookmarked state. */
    fun toggleBookmark(
        surahNumber: Int,
        ayahNumber: Int,
        savedAtMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (surahNumber !in MIN_SURAH..MAX_SURAH || ayahNumber < 1) return false
        val current = bookmarks
        val alreadySaved = current.any { it.surahNumber == surahNumber && it.ayahNumber == ayahNumber }
        bookmarks = if (alreadySaved) {
            current.filterNot { it.surahNumber == surahNumber && it.ayahNumber == ayahNumber }
        } else {
            current + AyahBookmark(surahNumber, ayahNumber, savedAtMillis)
        }
        return !alreadySaved
    }

    fun removeBookmark(surahNumber: Int, ayahNumber: Int) {
        bookmarks = bookmarks.filterNot {
            it.surahNumber == surahNumber && it.ayahNumber == ayahNumber
        }
    }

    fun ayahsBookmarkedIn(surahNumber: Int): Set<Int> =
        bookmarks.asSequence()
            .filter { it.surahNumber == surahNumber }
            .map { it.ayahNumber }
            .toSet()

    private fun decodeBookmarks(raw: String?): List<AyahBookmark> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split("|").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 3) return@mapNotNull null
            val surah = parts[0].trim().toIntOrNull() ?: return@mapNotNull null
            val ayah = parts[1].trim().toIntOrNull() ?: return@mapNotNull null
            val savedAt = parts[2].trim().toLongOrNull() ?: return@mapNotNull null
            if (surah !in MIN_SURAH..MAX_SURAH || ayah < 1 || savedAt <= 0L) {
                null
            } else {
                AyahBookmark(surah, ayah, savedAt)
            }
        }.distinctBy { it.key }
            .sortedWith(
                compareByDescending<AyahBookmark> { it.savedAtMillis }
                    .thenBy { it.surahNumber }
                    .thenBy { it.ayahNumber }
            )
    }
}
