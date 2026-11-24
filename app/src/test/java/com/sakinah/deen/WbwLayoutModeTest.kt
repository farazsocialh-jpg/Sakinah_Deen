package com.sakinah.deen

import com.sakinah.deen.data.prefs.QuranReaderPreferences
import com.sakinah.deen.data.prefs.WbwLayoutMode
import com.sakinah.deen.fakes.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The six word-by-word presentations and the storage rules behind the chosen one. */
class WbwLayoutModeTest {

    private fun prefs() = QuranReaderPreferences(FakeSharedPreferences())

    @Test
    fun interlinearRtlIsTheDefault() {
        assertEquals(WbwLayoutMode.INTERLINEAR_RTL, WbwLayoutMode.DEFAULT)
        assertEquals(WbwLayoutMode.INTERLINEAR_RTL, prefs().wbwLayoutMode)
    }

    @Test
    fun everyModeRoundTripsThroughStorage() {
        WbwLayoutMode.entries.forEach { mode ->
            val fake = FakeSharedPreferences()
            QuranReaderPreferences(fake).wbwLayoutMode = mode

            // A second instance reads it back: the choice survives a restart.
            assertEquals(mode, QuranReaderPreferences(fake).wbwLayoutMode)
        }
    }

    @Test
    fun theSixModesAreOfferedInPickerOrder() {
        assertEquals(
            listOf(
                WbwLayoutMode.INTERLINEAR_RTL,
                WbwLayoutMode.MUSHAF_TAP,
                WbwLayoutMode.VERSE_BOTTOM_DOCK,
                WbwLayoutMode.WORD_CARDS_RTL,
                WbwLayoutMode.GLOSSARY_LIST,
                WbwLayoutMode.VOCAB_QUIZ
            ),
            WbwLayoutMode.entries.toList()
        )
    }

    @Test
    fun unknownOrBlankStoredModeFallsBackToTheDefault() {
        val fake = FakeSharedPreferences()
        fake.edit().putString("quran_wbw_layout_mode", "SIDE_BY_SIDE").apply()
        assertEquals(WbwLayoutMode.DEFAULT, QuranReaderPreferences(fake).wbwLayoutMode)

        assertNull(WbwLayoutMode.fromStoredName("SIDE_BY_SIDE"))
        assertNull(WbwLayoutMode.fromStoredName("   "))
        assertNull(WbwLayoutMode.fromStoredName(null))
        assertEquals(WbwLayoutMode.VOCAB_QUIZ, WbwLayoutMode.fromStoredName("  VOCAB_QUIZ  "))
    }

    @Test
    fun onlyTheRootShowingModesNeedMorphology() {
        assertFalse(WbwLayoutMode.INTERLINEAR_RTL.showsMorphology)
        assertFalse(WbwLayoutMode.MUSHAF_TAP.showsMorphology)
        assertFalse(WbwLayoutMode.WORD_CARDS_RTL.showsMorphology)
        assertFalse(WbwLayoutMode.VOCAB_QUIZ.showsMorphology)
        assertTrue(WbwLayoutMode.VERSE_BOTTOM_DOCK.showsMorphology)
        assertTrue(WbwLayoutMode.GLOSSARY_LIST.showsMorphology)
    }
}
