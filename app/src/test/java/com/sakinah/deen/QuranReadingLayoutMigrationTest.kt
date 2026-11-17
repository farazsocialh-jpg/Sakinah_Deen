package com.sakinah.deen

import com.sakinah.deen.data.prefs.QuranReadingLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The ten old reading layouts were consolidated into four; stored preferences must survive. */
class QuranReadingLayoutMigrationTest {

    @Test
    fun everyCurrentPresetParsesFromItsStoredName() {
        QuranReadingLayout.entries.forEach { layout ->
            assertEquals(layout, QuranReadingLayout.fromStoredName(layout.name))
        }
    }

    @Test
    fun retiredLayoutsMapToTheirClosestSuccessor() {
        assertEquals(QuranReadingLayout.DIVIDER_HAIRLINE, QuranReadingLayout.fromStoredName("ALTERNATING_ROWS"))
        assertEquals(QuranReadingLayout.DIVIDER_HAIRLINE, QuranReadingLayout.fromStoredName("COMPACT_CARDS"))
        assertEquals(QuranReadingLayout.DIVIDER_HAIRLINE, QuranReadingLayout.fromStoredName("LEFT_ACCENT_RIBBON"))
        assertEquals(QuranReadingLayout.DIVIDER_HAIRLINE, QuranReadingLayout.fromStoredName("FOCUS_SPOTLIGHT"))
        assertEquals(QuranReadingLayout.CONTINUOUS_MUSHAF, QuranReadingLayout.fromStoredName("IMMERSIVE_ZEN"))
        assertEquals(QuranReadingLayout.THUMB_BOTTOM_DOCK, QuranReadingLayout.fromStoredName("ARABIC_TAP_EXPAND"))
    }

    @Test
    fun unknownOrBlankNamesFallBackInsteadOfCrashing() {
        assertNull(QuranReadingLayout.fromStoredName(null))
        assertNull(QuranReadingLayout.fromStoredName("  "))
        assertNull(QuranReadingLayout.fromStoredName("NOT_A_LAYOUT"))
    }

    @Test
    fun onlyFourPresetsRemain() {
        assertEquals(4, QuranReadingLayout.entries.size)
    }
}
