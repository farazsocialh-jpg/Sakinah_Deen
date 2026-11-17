package com.sakinah.deen

import com.sakinah.deen.util.CitationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Citation parsing used to miss most scholarly references: only a single `surah:ayah`
 * pattern and a handful of collection names were recognised. These tests pin the range,
 * alias and malformed-input behaviour the reader depends on.
 */
class CitationParserTest {

    @Test
    fun findsPlainAndPrefixedQuranReferences() {
        val plain = CitationParser.find("The verse 2:255 is Ayat al-Kursi.").single()
        assertEquals(2, plain.quran?.surah)
        assertEquals(255, plain.quran?.ayahStart)
        assertEquals("quran/2/255", plain.quran?.deepLink)

        val prefixed = CitationParser.find("As Allah says in Quran 4:59, obey the Messenger.")
        assertTrue(prefixed.any { it.quran?.surah == 4 && it.quran?.ayahStart == 59 })
    }

    @Test
    fun parsesAyahRangesWithHyphenAndEnDash() {
        val hyphen = CitationParser.find("Read 2:255-257 carefully.").single()
        assertEquals(255, hyphen.quran?.ayahStart)
        assertEquals(257, hyphen.quran?.ayahEnd)
        assertEquals("2:255-257", hyphen.quran?.label)
        assertEquals("quran/2/255", hyphen.quran?.deepLink)

        val enDash = CitationParser.find("Read 2:255–257 carefully.").single()
        assertEquals(257, enDash.quran?.ayahEnd)
    }

    @Test
    fun rejectsOutOfRangeAndReversedQuranReferences() {
        assertTrue(CitationParser.find("surah 115:1 does not exist").isEmpty())
        assertTrue(CitationParser.find("2:999 is past the end of Al-Baqarah").isEmpty())
        assertTrue(CitationParser.find("2:257-255 is reversed").isEmpty())
        assertTrue(CitationParser.find("0:0 is invalid").isEmpty())
    }

    @Test
    fun recognisesCollectionAliasesAndNumberMarkers() {
        CitationParser.find("Sahih al-Bukhari #5063").single().hadith.let { hadith ->
            assertNotNull(hadith)
            assertEquals("bukhari", hadith?.bookKey)
            assertEquals(5063, hadith?.number)
            assertEquals("hadith/bukhari/5063", hadith?.deepLink)
        }
        CitationParser.find("Bukhari 97").single().hadith.let {
            assertEquals("bukhari", it?.bookKey)
            assertEquals(97, it?.number)
        }
        CitationParser.find("Sunan Abi Dawud, Hadith 1420").single().hadith.let {
            assertEquals("abudawud", it?.bookKey)
            assertEquals(1420, it?.number)
        }
        CitationParser.find("Muwatta Malik 5").single().hadith.let {
            assertEquals("malik", it?.bookKey)
            assertEquals(5, it?.number)
        }
        CitationParser.find("40 Hadith Nawawi 13").single().hadith.let {
            assertEquals("nawawi", it?.bookKey)
        }
        CitationParser.find("Musnad Ahmad 1234").single().hadith.let {
            assertEquals("ahmad", it?.bookKey)
        }
    }

    @Test
    fun quranReferenceWinsOverALookalikeHadithNumber() {
        val matches = CitationParser.find("Sahih Muslim 2:255 was revealed about the Throne")
        assertTrue(matches.any { it.quran?.surah == 2 })
        assertTrue("a hadith number must not swallow 2:255", matches.none { it.hadith != null })
    }

    @Test
    fun readsArabicIndicDigitsWithoutShiftedOffsets() {
        val text = "قال تعالى: ٤:١٠١"
        val match = CitationParser.find(text).single()
        assertEquals(4, match.quran?.surah)
        assertEquals(101, match.quran?.ayahStart)
        // Offsets point at the original Arabic-Indic digits.
        assertEquals("٤:١٠١", text.substring(match.start, match.endExclusive))
    }

    @Test
    fun malformedAndPlainProseYieldNoMatches() {
        assertTrue(CitationParser.find("").isEmpty())
        assertTrue(CitationParser.find("No references in this sentence at all.").isEmpty())
        assertTrue(CitationParser.find("Ratio 300:1 in a chemistry summary").isEmpty())
        assertTrue(CitationParser.find("Sahih Muslim without a number").isEmpty())
        assertTrue(CitationParser.find("(2:255").none { it.quran == null })
    }

    @Test
    fun matchesAreSortedAndNeverOverlap() {
        val text = "See 2:255 and Sahih al-Bukhari #5063, then 4:59."
        val matches = CitationParser.find(text)
        assertEquals(3, matches.size)
        assertEquals(matches.sortedBy { it.start }, matches)
        matches.zipWithNext().forEach { (a, b) ->
            assertTrue(a.endExclusive <= b.start)
        }
    }

    @Test
    fun extractsBookHintFromContext() {
        val hint = CitationParser.extractSectionHint("Sahih al-Bukhari #97 · Book of Faith")
        assertNotNull(hint)
        assertTrue(hint!!.contains("Book of Faith"))
    }

    @Test
    fun ayahCountTableCoversAll114Surahs() {
        assertEquals(114, CitationParser.AYAH_COUNTS.size)
        assertEquals(6236, CitationParser.AYAH_COUNTS.sum())
        assertNull(CitationParser.AYAH_COUNTS.getOrNull(114))
    }
}
