package com.sakinah.deen

import com.sakinah.deen.data.database.DatabaseSeeder
import com.sakinah.deen.data.database.OfflineContentSeeder
import com.sakinah.deen.data.database.SeedStatus
import com.sakinah.deen.fakes.FakeAdhkarDao
import com.sakinah.deen.fakes.FakeHadithDao
import com.sakinah.deen.fakes.FakeQuranDao
import com.sakinah.deen.fakes.FakeTafsirDao
import com.sakinah.deen.network.IslamicDataRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Proves the bundled assets alone populate the database on a fresh install with no
 * network: the same asset JSON shipped in the APK is parsed and inserted here, so a
 * failure means an airplane-mode first launch would show an empty Quran/hadith screen.
 */
class OfflineSeedingTest {

    private fun bundledAsset(name: String): String {
        val candidates = listOf(
            File("src/main/assets/$name"),
            File("app/src/main/assets/$name")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Bundled asset $name not found from ${File(".").absolutePath}")
        return file.readText()
    }

    private fun noNetworkClient() = HttpClient(MockEngine { respond("", HttpStatusCode.OK) })

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun bundledQuranAsset_seedsAlFatihahAndJuzAmma() = runTest {
        val dao = FakeQuranDao()

        val (surahCount, ayahCount) =
            DatabaseSeeder.applyQuranSeed(bundledAsset(DatabaseSeeder.QURAN_ASSET), dao)

        assertEquals(38, surahCount)
        assertTrue("expected ayahs in the bundled seed, got $ayahCount", ayahCount > 0)
        assertEquals(7, dao.countAyahs(1))
        assertEquals("Al-Fatihah", dao.getAyah(1, 1)?.surahNameEnglish)
        assertTrue(dao.observeSurahs().first().any { it.surahNumber == 78 })
    }

    @Test
    fun bundledHadithAndAdhkarAssets_seedContent() = runTest {
        val hadithDao = FakeHadithDao()
        val adhkarDao = FakeAdhkarDao()

        val hadiths = DatabaseSeeder.applyHadithSeed(
            bundledAsset(DatabaseSeeder.HADITH_ASSET),
            hadithDao
        )
        val adhkar = DatabaseSeeder.applyAdhkarSeed(
            bundledAsset(DatabaseSeeder.HISN_ASSET),
            adhkarDao
        )

        assertTrue("Nawawi seed produced no hadith", hadiths > 0)
        assertTrue("Hisn al-Muslim seed produced no adhkar", adhkar > 0)
        assertEquals(
            hadiths,
            hadithDao.countForBook(DatabaseSeeder.NAWAWI_BOOK_KEY)
        )
        assertTrue(hadithDao.observeAllHadiths().first().isNotEmpty())
    }

    @Test
    fun repeatedSeeding_isIdempotent() = runTest {
        val quranDao = FakeQuranDao()
        val hadithDao = FakeHadithDao()
        val adhkarDao = FakeAdhkarDao()
        val content = DatabaseSeeder.SeedContent(quranDao, hadithDao, adhkarDao)
        val readAsset = { name: String -> bundledAsset(name) }

        val first = DatabaseSeeder.seedOnce(readAsset, { content })
        val writesAfterFirst = quranDao.ayahWrites + hadithDao.writes + adhkarDao.writes
        val second = DatabaseSeeder.seedOnce(readAsset, { content })

        assertFalse(first.skipped)
        assertTrue("second pass should be a no-op", second.skipped)
        assertEquals(writesAfterFirst, quranDao.ayahWrites + hadithDao.writes + adhkarDao.writes)
        assertEquals(38, quranDao.surahs.size)
    }

    @Test
    fun seeder_reachesReadyAndLeavesNoEmptyScreen() = runTest {
        val quranDao = FakeQuranDao()
        val hadithDao = FakeHadithDao()
        val adhkarDao = FakeAdhkarDao()
        val seeder = OfflineContentSeeder(
            readAsset = { name -> bundledAsset(name) },
            content = { DatabaseSeeder.SeedContent(quranDao, hadithDao, adhkarDao) },
            scope = CoroutineScope(Dispatchers.Unconfined)
        )

        val status = seeder.awaitReady()

        assertTrue("seeding should settle ready, was $status", status is SeedStatus.Ready)
        assertTrue(seeder.status.value.isSettled)
        assertTrue((status as SeedStatus.Ready).summary.rowsWritten > 0)
        assertTrue(quranDao.ayahs.isNotEmpty())
        assertTrue(hadithDao.entries.isNotEmpty())
        assertTrue(adhkarDao.entries.isNotEmpty())
    }

    @Test
    fun repository_waitsForSeederThenEmitsSeededRowsOffline() = runTest {
        val quranDao = FakeQuranDao()
        val hadithDao = FakeHadithDao()
        val adhkarDao = FakeAdhkarDao()
        val seeder = OfflineContentSeeder(
            readAsset = { name -> bundledAsset(name) },
            content = { DatabaseSeeder.SeedContent(quranDao, hadithDao, adhkarDao) },
            scope = CoroutineScope(Dispatchers.Unconfined)
        )
        // Never started explicitly: the read path itself must trigger and await seeding.
        val repo = IslamicDataRepository(
            quranDao = quranDao,
            hadithDao = hadithDao,
            tafsirDao = FakeTafsirDao(),
            client = noNetworkClient(),
            json = json,
            offlineContent = seeder
        )

        val surahs = repo.observeSurahs().first()
        val ayahs = repo.observeSurah(1).first()
        val hadithResults = repo.observeAllHadiths().first()
        // Seed translations transliterate the divine name as "Allāh", so search on a
        // plain-ASCII word that is present in the bundled translation.
        val searchResults = repo.searchAyahs("Merciful").first()

        assertEquals(38, surahs.size)
        assertEquals(7, ayahs.size)
        assertTrue(hadithResults.isNotEmpty())
        assertTrue("offline search should match seeded translations", searchResults.isNotEmpty())
        assertTrue(seeder.status.value.isSettled)
    }

    @Test
    fun missingAssets_leaveAppUsableWithPlaceholderContent() = runTest {
        val quranDao = FakeQuranDao()
        val hadithDao = FakeHadithDao()
        val adhkarDao = FakeAdhkarDao()
        val seeder = OfflineContentSeeder(
            readAsset = { null },
            content = { DatabaseSeeder.SeedContent(quranDao, hadithDao, adhkarDao) },
            scope = CoroutineScope(Dispatchers.Unconfined)
        )

        val status = seeder.awaitReady()

        assertTrue("a missing asset must settle, not hang", status.isSettled)
        assertTrue(status is SeedStatus.Ready)
        assertTrue(quranDao.ayahs.isEmpty())
    }
}