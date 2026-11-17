package com.sakinah.deen

import com.sakinah.deen.content.SeedQuranDocument
import com.sakinah.deen.data.quran.QuranOfflinePack
import com.sakinah.deen.data.quran.QuranOfflineStatus
import com.sakinah.deen.data.database.entity.QuranAyahEntity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Content integrity for the bundled Quran pack: the APK must ship all 114 surahs in order
 * with the canonical 6,236 ayahs, no duplicates, and an unchanged checksum. This is the
 * regression guard for the release build that shipped 114 surah metadata rows but only
 * 571 ayahs (38 surahs readable offline).
 */
class QuranOfflinePackTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun assetText(name: String = QuranOfflinePack.ASSET): String {
        val candidates = listOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Bundled asset $name not found from ${File(".").absolutePath}")
        return file.readText()
    }

    private fun document(): SeedQuranDocument =
        json.decodeFromString(assetText(QuranOfflinePack.ASSET))

    @Test
    fun bundledPack_matchesPinnedChecksumAndSize() {
        val text = assetText()
        val audit = QuranOfflinePack.audit(text)

        assertTrue("audit issues: ${audit.issues}", audit.isPristine)
        assertEquals(QuranOfflinePack.SHA256, audit.actualSha256)
        assertEquals(QuranOfflinePack.BYTES, audit.actualBytes)
        assertEquals(QuranOfflinePack.TOTAL_SURAHS, audit.surahCount)
        assertEquals(QuranOfflinePack.TOTAL_AYAHS, audit.ayahCount)
    }

    @Test
    fun bundledPack_holdsEverySurahInOrderWithoutDuplicates() {
        val surahs = document().surahs

        assertEquals(114, surahs.size)
        assertEquals((1..114).toList(), surahs.map { it.number })
        assertEquals(114, surahs.map { it.number }.distinct().size)
        assertTrue(surahs.all { it.nameArabic.isNotBlank() && it.nameEnglish.isNotBlank() })
        assertEquals(6236, surahs.sumOf { it.ayahs.size })
    }

    @Test
    fun bundledPack_ayahCountsMatchEachSurah() {
        val surahs = document().surahs.associateBy { it.number }

        // Canonical Madani mushaf verse counts for a spread of surahs.
        val expected = mapOf(
            1 to 7,
            2 to 286,
            9 to 129,
            18 to 110,
            36 to 83,
            55 to 78,
            78 to 40,
            112 to 4,
            113 to 5,
            114 to 6
        )
        expected.forEach { (surah, count) ->
            assertEquals("surah $surah", count, surahs.getValue(surah).ayahs.size)
            assertEquals("surah $surah declared count", count, surahs.getValue(surah).versesCount)
        }
        surahs.values.forEach { surah ->
            assertEquals(
                "surah ${surah.number} ayah numbering",
                (1..surah.ayahs.size).toList(),
                surah.ayahs.map { it.number }
            )
        }
    }

    @Test
    fun bundledPack_keepsArabicOrthographyAndVerseBoundaries() {
        val surahs = document().surahs.associateBy { it.number }

        val fatihah = surahs.getValue(1).ayahs
        assertEquals(7, fatihah.size)
        assertTrue(fatihah.first().arabic.contains("\u064E")) // fatha
        assertTrue(fatihah.first().arabic.startsWith("بِسْمِ"))

        // Verse boundaries: the last ayah of Al-Baqarah exists, the next one does not.
        val baqarah = surahs.getValue(2).ayahs
        assertNotNull(baqarah.firstOrNull { it.number == 286 })
        assertEquals(null, baqarah.firstOrNull { it.number == 287 })

        val nas = surahs.getValue(114).ayahs
        assertEquals(6, nas.size)
        assertTrue(nas.last().arabic.isNotBlank())

        // Every ayah carries Arabic letters, tashkeel and a translation.
        val arabicLetters = Regex("[\\u0621-\\u064A]")
        val tashkeel = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
        val unvocalized = surahs.values.flatMap { it.ayahs }
            .count { !tashkeel.containsMatchIn(it.arabic) }
        assertTrue("too many unvocalized ayahs: $unvocalized", unvocalized <= 3)
        surahs.values.flatMap { it.ayahs }.forEach { ayah ->
            assertTrue(arabicLetters.containsMatchIn(ayah.arabic))
            assertTrue(ayah.translation.isNotBlank())
        }
    }

    @Test
    fun audit_rejectsATruncatedPack() {
        val tampered = assetText().dropLast(64)
        val audit = QuranOfflinePack.audit(tampered)

        assertFalse(audit.checksumMatches)
        assertFalse(audit.isPristine)
        assertEquals(null, audit.document)
    }

    @Test
    fun audit_flagsStructuralDamageEvenWithAVerifiedChecksum() {
        val damaged = document().let { doc ->
            doc.copy(
                surahs = doc.surahs.mapIndexed { index, surah ->
                    if (index == 0) surah.copy(ayahs = surah.ayahs.dropLast(1)) else surah
                }
            )
        }
        val issues = QuranOfflinePack.audit(damaged)

        assertTrue(issues.any { it.contains("declares 7 ayahs") })
        assertTrue(issues.any { it.contains("6216") || it.contains("6235") || it.contains("6236") })
    }

    @Test
    fun offlineStatus_reportsPartialAndCompletePacksHonestly() {
        val partial = QuranOfflineStatus.fromCounts(surahCount = 38, ayahCount = 571)
        assertFalse(partial.isComplete)
        assertTrue(partial.headline.startsWith("Partial Offline (38/114"))

        val complete = QuranOfflineStatus.fromCounts(surahCount = 114, ayahCount = 6236)
        assertTrue(complete.isComplete)
        assertEquals("Offline Ready (114/114)", complete.headline)

        val empty = QuranOfflineStatus.from(emptyList<QuranAyahEntity>())
        assertFalse(empty.isComplete)
        assertTrue(empty.headline.contains("Missing"))
    }
}
