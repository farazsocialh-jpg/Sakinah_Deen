package com.sakinah.deen

import com.sakinah.deen.data.prefs.QuranReaderPreferences
import com.sakinah.deen.data.prefs.QuranReadingMode
import com.sakinah.deen.fakes.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Storage rules behind the reader's mode, last-read position and ayah bookmarks. */
class QuranReaderPreferencesTest {

    private fun prefs() = QuranReaderPreferences(FakeSharedPreferences())

    @Test
    fun readingModeDefaultsToStudy_andRoundTrips() {
        val p = prefs()
        assertEquals(QuranReadingMode.STUDY, p.readingMode)

        p.readingMode = QuranReadingMode.MUSHAF
        assertEquals(QuranReadingMode.MUSHAF, p.readingMode)
    }

    @Test
    fun unknownReadingModeFallsBackToStudy() {
        val fake = FakeSharedPreferences()
        fake.edit().putString("quran_reading_mode", "BOOK_SCROLL").apply()

        assertEquals(QuranReadingMode.STUDY, QuranReaderPreferences(fake).readingMode)
        assertNull(QuranReadingMode.fromStoredName("BOOK_SCROLL"))
        assertEquals(QuranReadingMode.MUSHAF, QuranReadingMode.fromStoredName("  MUSHAF  "))
    }

    @Test
    fun noReadingPositionUntilOneIsSaved() {
        assertNull(prefs().lastReadPosition)
    }

    @Test
    fun readingPositionRoundTripsIncludingTheScrollIndex() {
        val fake = FakeSharedPreferences()
        QuranReaderPreferences(fake).saveReadingPosition(18, 10, 7)

        val restored = QuranReaderPreferences(fake).lastReadPosition
        assertEquals(18, restored?.surahNumber)
        assertEquals(10, restored?.ayahNumber)
        assertEquals(7, restored?.scrollIndex)
    }

    @Test
    fun invalidReadingPositionsAreRejectedOrClamped() {
        val fake = FakeSharedPreferences()
        val p = QuranReaderPreferences(fake)

        p.saveReadingPosition(0, 4, 2)
        assertNull(p.lastReadPosition)

        p.saveReadingPosition(115, 4, 2)
        assertNull(p.lastReadPosition)

        // An index written by a broken value reads back as the top of the page.
        fake.edit()
            .putInt("quran_last_read_surah", 3)
            .putInt("quran_last_read_ayah", 0)
            .putInt("quran_last_read_scroll_index", -5)
            .apply()
        val restored = QuranReaderPreferences(fake).lastReadPosition
        assertEquals(3, restored?.surahNumber)
        assertEquals(1, restored?.ayahNumber)
        assertEquals(0, restored?.scrollIndex)
    }

    @Test
    fun toggleBookmarkAddsAndRemoves() {
        val p = prefs()
        assertFalse(p.isBookmarked(2, 255))

        assertTrue(p.toggleBookmark(2, 255, savedAtMillis = 1_000L))
        assertTrue(p.isBookmarked(2, 255))
        assertEquals(setOf(255), p.ayahsBookmarkedIn(2))
        assertTrue(p.ayahsBookmarkedIn(3).isEmpty())

        assertFalse(p.toggleBookmark(2, 255, savedAtMillis = 2_000L))
        assertFalse(p.isBookmarked(2, 255))
        assertTrue(p.bookmarks.isEmpty())
    }

    @Test
    fun bookmarksAreNewestFirst_andSurviveReopen() {
        val fake = FakeSharedPreferences()
        val p = QuranReaderPreferences(fake)

        p.toggleBookmark(2, 255, savedAtMillis = 1_000L)
        p.toggleBookmark(18, 10, savedAtMillis = 5_000L)
        p.toggleBookmark(1, 1, savedAtMillis = 3_000L)

        val expected = listOf(18 to 10, 1 to 1, 2 to 255)
        assertEquals(expected, p.bookmarks.map { it.key })
        assertEquals(expected, QuranReaderPreferences(fake).bookmarks.map { it.key })
    }

    @Test
    fun malformedStoredBookmarksAreIgnored() {
        val fake = FakeSharedPreferences()
        fake.edit()
            .putString(
                "quran_ayah_bookmarks",
                "2:255:1000|broken|0:1:500|115:1:400|1:1|3:7:300"
            )
            .apply()

        val stored = QuranReaderPreferences(fake).bookmarks
        assertEquals(listOf(2 to 255, 3 to 7), stored.map { it.key })
    }

    @Test
    fun duplicatingAnAyahKeepsOneEntry() {
        val fake = FakeSharedPreferences()
        val p = QuranReaderPreferences(fake)
        p.toggleBookmark(2, 255, savedAtMillis = 1_000L)
        p.toggleBookmark(2, 255, savedAtMillis = 2_000L) // removes
        p.toggleBookmark(2, 255, savedAtMillis = 3_000L) // re-adds

        assertEquals(1, p.bookmarks.size)
        assertEquals(3_000L, p.bookmarks.first().savedAtMillis)
    }

    @Test
    fun removeBookmarkIsIdempotent() {
        val p = prefs()
        p.toggleBookmark(1, 1, savedAtMillis = 1_000L)

        p.removeBookmark(1, 1)
        p.removeBookmark(1, 1)

        assertTrue(p.bookmarks.isEmpty())
    }
}
