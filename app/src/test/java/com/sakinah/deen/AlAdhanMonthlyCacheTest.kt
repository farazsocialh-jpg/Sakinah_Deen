package com.sakinah.deen

import com.sakinah.deen.data.database.dao.PrayerTimetableDao
import com.sakinah.deen.data.database.entity.PrayerTimetableEntity
import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prayer.AlAdhanApiClient
import com.sakinah.deen.data.prayer.PrayerMethodSettings
import com.sakinah.deen.data.prayer.SunniAuthority
import com.sakinah.deen.data.repository.PrayerTimeRepositoryImpl
import com.sakinah.deen.data.repository.PrayerTimeSource
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Monthly AlAdhan calendar parsing and the 30-day Room cache behaviour. */
class AlAdhanMonthlyCacheTest {

    private val doha = 25.2854 to 51.5310
    private val startOfNovember = LocalDate.of(2026, 11, 12)

    @Test
    fun resolveDay_parsesMonthlyJsonAndCachesAllThirtyDays() = runTest {
        var requests = 0
        var requestedPath = ""
        var requestedParams: Map<String, String> = emptyMap()
        val engine = MockEngine { request ->
            requests++
            // The request carries the mapped authority and Asr school.
            requestedPath = request.url.encodedPath
            requestedParams = request.url.parameters.entries().associate { it.key to it.value.first() }
            respondJson(monthlyJson())
        }
        val dao = FakePrayerTimetableDao()
        val repo = repository(dao, engine, now = FIXED_NOW)

        val resolved = repo.resolve(startOfNovember)

        assertEquals("/v1/calendar/2026/11", requestedPath)
        assertEquals("10", requestedParams["method"])
        assertEquals("0", requestedParams["school"])
        assertEquals("25.2854", requestedParams["latitude"])
        assertEquals("51.531", requestedParams["longitude"])

        assertEquals(PrayerTimeSource.ONLINE, resolved.source)
        assertEquals(SunniAuthority.QATAR, resolved.authority)
        assertEquals("Qatar Awqaf (Online)", resolved.label)
        // Day 12 of the fixture: "04:12 (+03)" stripped of its timezone annotation.
        assertEquals(LocalTime.of(4, 12), resolved.times[PrayerType.FAJR])
        assertEquals(LocalTime.of(5, 41), resolved.times[PrayerType.SUNRISE])
        assertEquals(LocalTime.of(11, 17), resolved.times[PrayerType.DHUHR])
        assertEquals(LocalTime.of(14, 30), resolved.times[PrayerType.ASR])
        assertEquals(LocalTime.of(16, 53), resolved.times[PrayerType.MAGHRIB])
        assertEquals(LocalTime.of(18, 23), resolved.times[PrayerType.ISHA])
        assertEquals(1, requests)

        // The whole month is stored, with the documented provenance fields.
        assertEquals(30, dao.count)
        val row = dao.getForDay(PrayerTimeRepositoryImpl.cacheKeyOf(startOfNovember, doha, 10, 0), startOfNovember.toEpochDay())
        assertNotNull(row)
        assertEquals("Qatar", row!!.methodName)
        assertEquals(10, row.methodId)
        assertEquals(0, row.schoolId)
        assertEquals("ONLINE", row.source)
        assertEquals("Asia/Qatar", row.timeZone)
        assertEquals(FIXED_NOW, row.fetchedAt)
        assertEquals("04:12", row.fajr)
    }

    @Test
    fun secondDayOfTheSameMonth_isServedFromRoomWithoutAnotherRequest() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests++
            respondJson(monthlyJson())
        }
        val dao = FakePrayerTimetableDao()
        val repo = repository(dao, engine, now = FIXED_NOW)

        repo.resolve(startOfNovember)
        val nextDay = repo.resolve(startOfNovember.plusDays(1))

        assertEquals(1, requests)
        assertEquals(PrayerTimeSource.CACHE, nextDay.source)
        assertEquals("Qatar Awqaf (Cached)", nextDay.label)
        assertEquals(LocalTime.of(4, 13), nextDay.times[PrayerType.FAJR])
    }

    @Test
    fun cacheOlderThanSevenDays_isRefreshedOnline() = runTest {
        var requests = 0
        var now = FIXED_NOW
        val engine = MockEngine {
            requests++
            respondJson(monthlyJson())
        }
        val dao = FakePrayerTimetableDao()
        val repo = repository(dao, engine, now = { now })

        repo.resolve(startOfNovember)
        assertEquals(1, requests)

        // Six days later the cache is still authoritative.
        now = FIXED_NOW + 6L * DAY_MS
        assertEquals(PrayerTimeSource.CACHE, repo.resolve(startOfNovember.plusDays(2)).source)
        assertEquals(1, requests)

        // Past the seven-day window the month is fetched again.
        now = FIXED_NOW + 8L * DAY_MS
        val refreshed = repo.resolve(startOfNovember.plusDays(3))
        assertEquals(2, requests)
        assertEquals(PrayerTimeSource.ONLINE, refreshed.source)
        assertEquals(30, dao.count)
    }

    @Test
    fun changingCoordinatesOrMethod_missesTheCacheAndFetchesAgain() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests++
            respondJson(monthlyJson())
        }
        val dao = FakePrayerTimetableDao()
        val settings = FakeMethodSettings()
        val repo = repository(dao, engine, now = FIXED_NOW, settings = settings)

        repo.resolve(startOfNovember)
        assertEquals(1, requests)

        // Moving to another city invalidates the (place-specific) cache.
        val makkah = 21.4225 to 39.8262
        val moved = repo.resolve(startOfNovember, coords = makkah, countryCode = "SA")
        assertEquals(2, requests)
        assertEquals(SunniAuthority.UMM_AL_QURA, moved.authority)
        assertEquals(30, dao.count) // previous month/place rows pruned

        // A manual calculation method is an override, so it also misses.
        settings.override = CalculationMethod.NORTH_AMERICA
        val overridden = repo.resolve(startOfNovember, coords = makkah)
        assertEquals(3, requests)
        assertEquals(SunniAuthority.ISNA, overridden.authority)

        // Hanafi Asr is a different school id, hence a different cache key.
        repo.resolve(startOfNovember, coords = makkah, madhhab = Madhhab.HANAFI)
        assertEquals(4, requests)
    }

    @Test
    fun newMonth_isFetchedEvenWhenThePreviousMonthIsCached() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests++
            respondJson(monthlyJson())
        }
        val dao = FakePrayerTimetableDao()
        val repo = repository(dao, engine, now = FIXED_NOW)

        repo.resolve(LocalDate.of(2026, 11, 30))
        assertEquals(1, requests)
        assertTrue(PrayerTimeRepositoryImpl.cacheKeyOf(startOfNovember, doha, 10, 0).endsWith("|202611"))

        repo.resolve(LocalDate.of(2026, 12, 1))
        assertEquals(2, requests)
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * Resolves for a Doha device (Qatar authority, Asia/Qatar clock), the context the fixture
     * calendar was fetched for.
     */
    private suspend fun PrayerTimeRepositoryImpl.resolve(
        date: LocalDate,
        coords: Pair<Double, Double> = doha,
        madhhab: Madhhab = Madhhab.SHAFI_HANBALI_MALIKI,
        countryCode: String? = "QA"
    ) = resolveDay(
        date = date,
        coordinates = coords,
        madhhab = madhhab,
        zoneId = ZoneId.of("Asia/Qatar"),
        countryCode = countryCode
    )

    private fun repository(
        dao: PrayerTimetableDao,
        engine: MockEngine,
        now: Long,
        settings: PrayerMethodSettings? = null
    ) = repository(dao, engine, now = { now }, settings = settings)

    private fun repository(
        dao: PrayerTimetableDao,
        engine: MockEngine,
        now: () -> Long,
        settings: PrayerMethodSettings? = null
    ) = PrayerTimeRepositoryImpl(
        dao = dao,
        apiClient = AlAdhanApiClient(HttpClient(engine), Json { ignoreUnknownKeys = true }),
        engine = PrayerCalculationEngineImpl(),
        settings = settings,
        clock = now
    )

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )

    /** A full month of the real AlAdhan payload shape; the Fajr minute tracks the day number. */
    private fun monthlyJson(year: Int = 2026, month: Int = 11, days: Int = 30): String {
        val mm = "%02d".format(month)
        val entries = (1..days).joinToString(",") { day ->
            val dd = "%02d".format(day)
            val fajr = "04:%02d".format(day)
            """{"timings":{"Fajr":"$fajr (+03)","Sunrise":"05:41 (+03)","Dhuhr":"11:17 (+03)","Asr":"14:30 (+03)","Sunset":"16:53 (+03)","Maghrib":"16:53 (+03)","Isha":"18:23 (+03)"},"date":{"readable":"$dd Nov $year","timestamp":"1793512861","gregorian":{"date":"$dd-$mm-$year"}},"meta":{"latitude":25.2854,"longitude":51.531,"timezone":"Asia/Qatar","method":{"id":10,"name":"Qatar","params":{"Fajr":18,"Isha":"90 min"}},"school":"STANDARD"}}"""
        }
        return """{"code":200,"status":"OK","data":[$entries]}"""
    }

    companion object {
        private const val FIXED_NOW = 1_794_500_000_000L
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

/** In-memory [PrayerTimetableDao]; the real Room DAO is exercised on device. */
class FakePrayerTimetableDao : PrayerTimetableDao {

    private val rows = linkedMapOf<Pair<String, Long>, PrayerTimetableEntity>()

    val count: Int get() = rows.size

    fun all(): List<PrayerTimetableEntity> = rows.values.toList()

    override suspend fun getForDay(cacheKey: String, epochDay: Long): PrayerTimetableEntity? =
        rows[cacheKey to epochDay]

    override suspend fun getForKey(cacheKey: String): List<PrayerTimetableEntity> =
        rows.values.filter { it.cacheKey == cacheKey }.sortedBy { it.dateEpochDay }

    override suspend fun countForKey(cacheKey: String): Int =
        rows.values.count { it.cacheKey == cacheKey }

    override suspend fun lastFetchedAt(cacheKey: String): Long? =
        rows.values.filter { it.cacheKey == cacheKey }.maxOfOrNull { it.fetchedAt }

    override suspend fun upsertAll(rows: List<PrayerTimetableEntity>) {
        rows.forEach { row -> this.rows[row.cacheKey to row.dateEpochDay] = row }
    }

    override suspend fun deleteForKey(cacheKey: String) {
        rows.keys.removeAll { it.first == cacheKey }
    }

    override suspend fun deleteAllExcept(keepKey: String) {
        rows.keys.removeAll { it.first != keepKey }
    }
}

/** Test double for the manual-override / country persistence. */
class FakeMethodSettings(
    var override: CalculationMethod? = null,
    var country: String? = null
) : PrayerMethodSettings {
    var overrideWrites = 0

    override fun countryCode(): String? = country

    override fun manualMethodOverride(): CalculationMethod? = override

    override fun setManualMethodOverride(method: CalculationMethod?) {
        override = method
        overrideWrites++
    }

    override fun setCountryCode(code: String?) {
        country = code
    }
}
