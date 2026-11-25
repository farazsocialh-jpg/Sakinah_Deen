package com.sakinah.deen

import com.sakinah.deen.data.prefs.QuranReaderPreferences
import com.sakinah.deen.data.prefs.WbwLayoutMode
import com.sakinah.deen.fakes.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The reader offers one word-by-word presentation; stored retired modes migrate to it. */
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
    fun onlyOnePresentationIsOffered() {
        assertEquals(listOf(WbwLayoutMode.INTERLINEAR_RTL), WbwLayoutMode.entries.toList())
    }

    @Test
    fun retiredStoredModesMigrateToInterlinearRtl() {
        listOf(
            "MUSHAF_TAP",
            "VERSE_BOTTOM_DOCK",
            "WORD_CARDS_RTL",
            "GLOSSARY_LIST",
            "VOCAB_QUIZ"
        ).forEach { stored ->
            val fake = FakeSharedPreferences()
            fake.edit().putString("quran_wbw_layout_mode", stored).apply()
            assertEquals(WbwLayoutMode.INTERLINEAR_RTL, QuranReaderPreferences(fake).wbwLayoutMode)
            assertEquals(WbwLayoutMode.INTERLINEAR_RTL, WbwLayoutMode.fromStoredName("  $stored  "))
        }
    }

    @Test
    fun unknownOrBlankStoredModeFallsBackToTheDefault() {
        val fake = FakeSharedPreferences()
        fake.edit().putString("quran_wbw_layout_mode", "SIDE_BY_SIDE").apply()
        assertEquals(WbwLayoutMode.DEFAULT, QuranReaderPreferences(fake).wbwLayoutMode)

        assertNull(WbwLayoutMode.fromStoredName("SIDE_BY_SIDE"))
        assertNull(WbwLayoutMode.fromStoredName("   "))
        assertNull(WbwLayoutMode.fromStoredName(null))
    }
}
