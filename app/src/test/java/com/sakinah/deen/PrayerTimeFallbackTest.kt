package com.sakinah.deen

import com.sakinah.deen.data.database.dao.PrayerTimetableDao
import com.sakinah.deen.data.database.entity.PrayerTimetableEntity
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.PrayerAdjustments
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prayer.AlAdhanApiClient
import com.sakinah.deen.data.prayer.SunniMethodMapper
import com.sakinah.deen.data.repository.PrayerTimeRepositoryImpl
import com.sakinah.deen.data.repository.PrayerTimeSource
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngine
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl
import com.sakinah.deen.domain.astronomy.PrayerCountdownInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.datetime.LocalDate as KLocalDate
import kotlinx.datetime.LocalTime as KLocalTime
import kotlinx.datetime.TimeZone as KTimeZone
import kotlinx.datetime.toJavaLocalTime

/**
 * Offline behaviour: when AlAdhan cannot be reached the resolver must fall back seamlessly —
 * first to whatever the encrypted cache already holds, and only then to the local
 * astronomical calculation ("Offline Calculation").
 */
class PrayerTimeFallbackTest {

    private val doha = 25.2854 to 51.5310
    private val day = LocalDate.of(2026, 11, 12)
    private val cacheKey = PrayerTimeRepositoryImpl.cacheKeyOf(day, doha, 10, 0)

    @Test
    fun offlineWithEmptyCache_calculatesLocallyAndLabelsItOffline() = runTest {
        val dao = FakePrayerTimetableDao()
        val repo = repository(dao, engine = PrayerCalculationEngineImpl(), http = offline())

        val resolved = repo.resolveDay(day, doha, Madhhab.SHAFI_HANBALI_MALIKI)

        assertEquals(PrayerTimeSource.OFFLINE_FALLBACK, resolved.source)
        assertEquals("Offline Calculation", resolved.label)

        val expected = localEngineTimes()
        assertEquals(expected[PrayerType.FAJR], resolved.times[PrayerType.FAJR])
        assertEquals(expected[PrayerType.DHUHR], resolved.times[PrayerType.DHUHR])
        assertEquals(expected[PrayerType.MAGHRIB], resolved.times[PrayerType.MAGHRIB])
        assertEquals(expected[PrayerType.ISHA], resolved.times[PrayerType.ISHA])

        // Sanity check that the values are a real timetable, not placeholders.
        assertTrue(resolved.times.getValue(PrayerType.FAJR) < resolved.times.getValue(PrayerType.DHUHR))
        assertTrue(resolved.times.getValue(PrayerType.DHUHR) < resolved.times.getValue(PrayerType.MAGHRIB))
        assertTrue(resolved.times.getValue(PrayerType.MAGHRIB) < resolved.times.getValue(PrayerType.ISHA))

        // Persisted so a restart while still offline can serve it from Room.
        val row = dao.all().single()
        assertEquals("OFFLINE_FALLBACK", row.source)
        assertEquals("Offline Calculation", resolved.label)
    }

    @Test
    fun httpError_fallsBackToLocalCalculation() = runTest {
        val dao = FakePrayerTimetableDao()
        val repo = repository(
            dao,
            engine = PrayerCalculationEngineImpl(),
            http = MockEngine {
                respond("upstream unavailable", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.ContentType, "text/plain"))
            }
        )

        val resolved = repo.resolveDay(day, doha, Madhhab.SHAFI_HANBALI_MALIKI)

        assertEquals(PrayerTimeSource.OFFLINE_FALLBACK, resolved.source)
        assertEquals(localEngineTimes()[PrayerType.ASR], resolved.times[PrayerType.ASR])
    }

    @Test
    fun malformedPayload_fallsBackToLocalCalculation() = runTest {
        val repo = repository(
            FakePrayerTimetableDao(),
            engine = PrayerCalculationEngineImpl(),
            http = MockEngine {
                respond("""{"code":200,"status":"OK","data":[]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        )

        val resolved = repo.resolveDay(day, doha, Madhhab.SHAFI_HANBALI_MALIKI)

        assertEquals(PrayerTimeSource.OFFLINE_FALLBACK, resolved.source)
    }

    @Test
    fun staleCache_isServedOfflineWithoutRecalculating() = runTest {
        val dao = FakePrayerTimetableDao()
        dao.upsertAll(listOf(onlineRow(fetchedAt = FIXED_NOW - 10L * DAY_MS)))

        // If the resolver recalculated instead of using the cache, this engine would throw.
        val exploding = ExplodingEngine()
        val repo = repository(dao, engine = exploding, http = offline())

        val resolved = repo.resolveDay(day, doha, Madhhab.SHAFI_HANBALI_MALIKI)

        assertEquals(0, exploding.calls)
        assertEquals(PrayerTimeSource.CACHE, resolved.source)
        assertEquals("Qatar Awqaf (Cached)", resolved.label)
        assertEquals(LocalTime.of(4, 24), resolved.times[PrayerType.FAJR])
    }

    @Test
    fun restartWhileStillOffline_servesThePersistedFallbackWithoutRecalculating() = runTest {
        val dao = FakePrayerTimetableDao()

        // First run, offline: computes and stores the fallback day.
        repository(dao, engine = PrayerCalculationEngineImpl(), http = offline())
            .resolveDay(day, doha, Madhhab.SHAFI_HANBALI_MALIKI)

        // "Restart": a new resolver with an empty retry throttle and an engine that must not run.
        val exploding = ExplodingEngine()
        val resolved = repository(dao, engine = exploding, http = offline())
            .resolveDay(day, doha, Madhhab.SHAFI_HANBALI_MALIKI)

        assertEquals(0, exploding.calls)
        assertEquals(PrayerTimeSource.OFFLINE_FALLBACK, resolved.source)
        assertEquals("Offline Calculation", resolved.label)
    }

    @Test
    fun hanafiAsr_isCachedUnderItsOwnSchool() = runTest {
        val dao = FakePrayerTimetableDao()
        val repo = repository(dao, engine = PrayerCalculationEngineImpl(), http = offline())

        repo.resolveDay(day, doha, Madhhab.SHAFI_HANBALI_MALIKI)
        repo.resolveDay(day, doha, Madhhab.HANAFI)

        assertEquals(2, dao.all().size)
        assertEquals(setOf(0, 1), dao.all().map { it.schoolId }.toSet())
    }

    // ------------------------------------------------------------------ fixtures

    private fun repository(
        dao: PrayerTimetableDao,
        engine: PrayerCalculationEngine,
        http: MockEngine
    ) = PrayerTimeRepositoryImpl(
        dao = dao,
        apiClient = AlAdhanApiClient(HttpClient(http), Json { ignoreUnknownKeys = true }),
        engine = engine,
        settings = FakeMethodSettings(country = "QA"),
        clock = { FIXED_NOW }
    )

    private fun offline(): MockEngine = MockEngine { throw IOException("airplane mode") }

    private fun localEngineTimes(): Map<PrayerType, LocalTime> =
        PrayerCalculationEngineImpl().getPrayerTimes(
            coordinates = doha,
            date = KLocalDate(2026, 11, 12),
            method = SunniMethodMapper.localCalculationMethod(com.sakinah.deen.data.prayer.SunniAuthority.QATAR),
            madhhab = Madhhab.SHAFI_HANBALI_MALIKI,
            timeZone = KTimeZone.currentSystemDefault(),
            adjustments = PrayerAdjustments()
        ).mapValues { (_, time) -> time.toJavaLocalTime() }

    private fun onlineRow(fetchedAt: Long) = PrayerTimetableEntity(
        cacheKey = cacheKey,
        dateEpochDay = day.toEpochDay(),
        fajr = "04:24",
        sunrise = "05:41",
        dhuhr = "11:17",
        asr = "14:30",
        maghrib = "16:53",
        isha = "18:23",
        methodName = "Qatar",
        methodId = 10,
        schoolId = 0,
        timeZone = ZoneId.systemDefault().id,
        source = PrayerTimeSource.ONLINE.name,
        fetchedAt = fetchedAt
    )

    private companion object {
        const val FIXED_NOW = 1_794_500_000_000L
        const val DAY_MS = 24L * 60 * 60 * 1000
    }

    /** Fails loudly if the resolver falls back to a calculation it should not need. */
    private class ExplodingEngine : PrayerCalculationEngine {
        var calls = 0

        override fun getPrayerTimes(
            coordinates: Pair<Double, Double>,
            date: KLocalDate,
            method: com.sakinah.deen.data.model.CalculationMethod,
            madhhab: Madhhab,
            timeZone: KTimeZone,
            adjustments: PrayerAdjustments
        ): Map<PrayerType, KLocalTime> {
            calls++
            error("local calculation must not run while cached timings exist")
        }

        override fun getQiblaAzimuth(coordinates: Pair<Double, Double>): Double = 0.0

        override fun calculateCountdown(
            prayerTimes: Map<PrayerType, KLocalTime>,
            currentTime: KLocalTime
        ): PrayerCountdownInfo = PrayerCountdownInfo(
            currentPrayer = PrayerType.FAJR,
            nextPrayer = PrayerType.DHUHR,
            remainingMinutes = 0,
            remainingSeconds = 0,
            elapsedRatio = 0f
        )
    }
}
