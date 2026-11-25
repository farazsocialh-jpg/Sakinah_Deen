package com.sakinah.deen

import com.sakinah.deen.data.prefs.QuranReadingLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Retired reading layouts were consolidated into split and dock; stored preferences must survive. */
class QuranReadingLayoutMigrationTest {

    @Test
    fun everyCurrentPresetParsesFromItsStoredName() {
        QuranReadingLayout.entries.forEach { layout ->
            assertEquals(layout, QuranReadingLayout.fromStoredName(layout.name))
        }
    }

    @Test
    fun retiredLayoutsMapToTheirClosestSuccessor() {
        assertEquals(QuranReadingLayout.SPLIT_COLUMN, QuranReadingLayout.fromStoredName("DIVIDER_HAIRLINE"))
        assertEquals(QuranReadingLayout.SPLIT_COLUMN, QuranReadingLayout.fromStoredName("CONTINUOUS_MUSHAF"))
        assertEquals(QuranReadingLayout.SPLIT_COLUMN, QuranReadingLayout.fromStoredName("ALTERNATING_ROWS"))
        assertEquals(QuranReadingLayout.SPLIT_COLUMN, QuranReadingLayout.fromStoredName("COMPACT_CARDS"))
        assertEquals(QuranReadingLayout.SPLIT_COLUMN, QuranReadingLayout.fromStoredName("LEFT_ACCENT_RIBBON"))
        assertEquals(QuranReadingLayout.SPLIT_COLUMN, QuranReadingLayout.fromStoredName("FOCUS_SPOTLIGHT"))
        assertEquals(QuranReadingLayout.SPLIT_COLUMN, QuranReadingLayout.fromStoredName("IMMERSIVE_ZEN"))
        assertEquals(QuranReadingLayout.THUMB_BOTTOM_DOCK, QuranReadingLayout.fromStoredName("ARABIC_TAP_EXPAND"))
    }

    @Test
    fun unknownOrBlankNamesFallBackInsteadOfCrashing() {
        assertNull(QuranReadingLayout.fromStoredName(null))
        assertNull(QuranReadingLayout.fromStoredName("  "))
        assertNull(QuranReadingLayout.fromStoredName("NOT_A_LAYOUT"))
    }

    @Test
    fun onlyTwoPresetsRemain() {
        assertEquals(
            listOf(QuranReadingLayout.SPLIT_COLUMN, QuranReadingLayout.THUMB_BOTTOM_DOCK),
            QuranReadingLayout.entries.toList()
        )
    }
}
