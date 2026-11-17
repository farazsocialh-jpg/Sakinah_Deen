package com.sakinah.deen

import com.sakinah.deen.content.SeedHisnDocument
import com.sakinah.deen.data.database.DatabaseSeeder
import com.sakinah.deen.fakes.FakeAdhkarDao
import com.sakinah.deen.fakes.FakeHadithDao
import com.sakinah.deen.fakes.FakeQuranDao
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bundled Hisn al-Muslim set is the only adhkar source the app ships, and it used to
 * hold two categories and five entries with no grading, repeat count or variant labels.
 * These tests fail if a future edit drops provenance or shrinks the library again.
 */
class AdhkarSeedTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun bundledAsset(name: String): String {
        val candidates = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Bundled asset $name not found from ${File(".").absolutePath}")
        return file.readText()
    }

    private fun document(): SeedHisnDocument =
        json.decodeFromString(SeedHisnDocument.serializer(), bundledAsset(DatabaseSeeder.HISN_ASSET))

    private fun entries() = document().categories.flatMap { it.items }

    @Test
    fun libraryCoversEveryDayOfTheWeekOfDhikr() {
        val doc = document()
        val keys = doc.categories.map { it.key }

        assertTrue("expected at least 8 categories, got $keys", doc.categories.size >= 8)
        assertTrue("expected at least 35 entries", entries().size >= 35)
        listOf(
            "morning_evening",
            "after_salah",
            "sleep",
            "waking",
            "travel",
            "home",
            "eating",
            "distress",
            "illness"
        ).forEach { required ->
            assertTrue("missing category $required", required in keys)
        }
        doc.categories.forEach { category ->
            assertTrue("category ${category.key} has no title", category.title.isNotBlank())
            assertTrue("category ${category.key} is empty", category.items.isNotEmpty())
        }
    }

    @Test
    fun everyEntryCarriesArabicAndAVerifiableSource() {
        entries().forEach { entry ->
            val where = "${entry.id} (${entry.title})"
            assertTrue("$where has no Arabic", entry.arabic.trim().isNotBlank())
            assertTrue("$where has no title", entry.title.isNotBlank())
            assertTrue("$where has no translation", entry.translation.isNotBlank())
            assertTrue("$where has no source reference", entry.reference.isNotBlank())
            assertTrue("$where has no grading", entry.grading.isNotBlank())
            assertTrue("$where repeats ${entry.repeatCount} times", entry.repeatCount >= 1)
            // A reference must name a source *and* a locator, e.g. "Sahih Muslim 597" or
            // "Quran 2:255". A bare book name is not a verifiable citation chain.
            assertTrue(
                "$where has an unverifiable reference: ${entry.reference}",
                Regex("(Quran \\d+:\\d+|\\d+)").containsMatchIn(entry.reference)
            )
        }
    }

    @Test
    fun entryIdsAreUniqueAndStable() {
        val ids = entries().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it.isBlank() })
        assertTrue("ayat_al_kursi id must stay stable for favourites", "ayat_al_kursi" in ids)
    }

    @Test
    fun repeatCountsMatchTheKnownWording() {
        val byId = entries().associateBy { it.id }
        assertEquals(3, byId.getValue("surah_ikhlas_falaq_nas").repeatCount)
        assertEquals(100, byId.getValue("subhanallahi_wa_bihamdihi_100").repeatCount)
        assertEquals(33, byId.getValue("tasbih_fatimah").repeatCount)
        assertEquals(7, byId.getValue("ruqyah_shifa").repeatCount)
        assertEquals(1, byId.getValue("bismika_amutu").repeatCount)
    }

    @Test
    fun seederWritesProvenanceAndKeepsFakeStoreConsistent() = runTest {
        val dao = FakeAdhkarDao()
        val written = DatabaseSeeder.applyAdhkarSeed(bundledAsset(DatabaseSeeder.HISN_ASSET), dao)

        assertEquals(entries().size, written)
        val stored = dao.entries.getValue("tasbih_fatimah")
        assertEquals("Sahih Muslim 597; al-Bukhari 5362", stored.reference)
        assertEquals("Sahih", stored.grading)
        assertEquals(33, stored.repeatCount)
        assertTrue(stored.transliteration.isNotBlank())
        assertTrue(stored.variantNote.isNotBlank())
    }

    @Test
    fun aNewSeedEditionReplacesTheOldOneInsteadOfSkipping() = runTest {
        val adhkarDao = FakeAdhkarDao()
        val content = DatabaseSeeder.SeedContent(
            FakeQuranDao(),
            FakeHadithDao(),
            adhkarDao
        )
        val hisn = bundledAsset(DatabaseSeeder.HISN_ASSET)
        val readOnlyHisn = { name: String -> if (name == DatabaseSeeder.HISN_ASSET) hisn else null }

        val first = DatabaseSeeder.seedOnce(readOnlyHisn, { content })
        assertTrue(first.adhkar > 0)
        val seededCount = adhkarDao.entries.size

        // Same edition: no adhkar is rewritten, so cold start stays as cheap as before.
        val writesAfterFirst = adhkarDao.writes
        val second = DatabaseSeeder.seedOnce(readOnlyHisn, { content })
        assertEquals("an unchanged edition must not re-seed", 0, second.adhkar)
        assertEquals(writesAfterFirst, adhkarDao.writes)

        // New edition (one more entry): the hash changes, the whole set is refreshed.
        val enlarged = hisn.replaceFirst("\"id\": \"ayat_al_kursi\"", "\"extra\": \"x\", \"id\": \"ayat_al_kursi\"")
        val third = DatabaseSeeder.seedOnce(
            { name -> if (name == DatabaseSeeder.HISN_ASSET) enlarged else null },
            { content }
        )
        assertEquals(seededCount, third.adhkar)
        assertEquals(seededCount, adhkarDao.entries.size)
        assertNotEquals(
            DatabaseSeeder.adhkarSeedHash(hisn),
            DatabaseSeeder.adhkarSeedHash(enlarged)
        )
    }
}
