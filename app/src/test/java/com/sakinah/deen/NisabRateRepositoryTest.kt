package com.sakinah.deen

import com.sakinah.deen.data.repository.NisabRateCache
import com.sakinah.deen.data.repository.NisabRateRepositoryImpl
import com.sakinah.deen.data.repository.NisabRates
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class NisabRateRepositoryTest {

    private class RecordingCache(initial: NisabRates? = null) : NisabRateCache {
        var stored: NisabRates? = initial
        var writeCount = 0
        override fun read(): NisabRates? = stored
        override fun write(rates: NisabRates) {
            stored = rates
            writeCount++
        }
    }

    private fun repository(
        cache: NisabRateCache? = null,
        endpoint: String = "https://rates.example.test/nisab",
        engine: MockEngine
    ) = NisabRateRepositoryImpl(
        client = HttpClient(engine),
        json = Json { ignoreUnknownKeys = true },
        endpointUrl = endpoint,
        cache = cache
    )

    @Test
    fun refresh_parsesRatesAndCachesThem() = runTest {
        val cache = RecordingCache()
        val engine = MockEngine { _ ->
            respond(
                content = """{"gold": 91.5, "silver": 1.21, "updatedAt": 1763000000}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val repo = repository(cache = cache, engine = engine)

        repo.refresh()

        assertEquals(91.5f, repo.rates.value.goldPricePerGram, 0.001f)
        assertEquals(1.21f, repo.rates.value.silverPricePerGram, 0.001f)
        // Seconds from the endpoint are normalised to epoch millis.
        assertEquals(1_763_000_000_000L, repo.rates.value.lastUpdatedEpochMillis)
        assertEquals(1, cache.writeCount)
        assertNotNull(cache.stored)
    }

    @Test
    fun refresh_parsesDocumentedSchemaWithDateString() = runTest {
        val cache = RecordingCache()
        val engine = MockEngine { _ ->
            respond(
                content = """{"goldPerGram": 92.75, "silverPerGram": 1.28, "date": "2026-11-12"}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val repo = repository(cache = cache, engine = engine)

        repo.refresh()

        assertEquals(92.75f, repo.rates.value.goldPricePerGram, 0.001f)
        assertEquals(1.28f, repo.rates.value.silverPricePerGram, 0.001f)
        // "2026-11-12" is anchored to UTC midnight, not "now".
        assertEquals(1_794_441_600_000L, repo.rates.value.lastUpdatedEpochMillis)
        assertEquals(1, cache.writeCount)
    }

    @Test
    fun refresh_parsesIsoInstantDate() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = """{"goldPerGram": 90.0, "silverPerGram": 1.2, "date": "2026-11-12T06:30:00Z"}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val repo = repository(engine = engine)

        repo.refresh()

        assertEquals(1_794_465_000_000L, repo.rates.value.lastUpdatedEpochMillis)
    }

    @Test
    fun refresh_onHttpError_keepsCachedRate() = runTest {
        val cached = NisabRates(86.0f, 1.05f, 1_700_000_000_000L)
        val cache = RecordingCache(cached)
        val engine = MockEngine { _ -> respondError(HttpStatusCode.ServiceUnavailable) }
        val repo = repository(cache = cache, engine = engine)

        // Starts from cache, and the failing refresh must not overwrite it.
        assertEquals(86.0f, repo.rates.value.goldPricePerGram, 0.001f)
        repo.refresh()

        assertEquals(86.0f, repo.rates.value.goldPricePerGram, 0.001f)
        assertEquals(1_700_000_000_000L, repo.rates.value.lastUpdatedEpochMillis)
        assertEquals(0, cache.writeCount)
    }

    @Test
    fun refresh_onNetworkFailure_keepsCachedRate() = runTest {
        val cached = NisabRates(84.5f, 1.02f, 1_690_000_000_000L)
        val engine = MockEngine { _ -> throw IOException("offline") }
        val repo = repository(cache = RecordingCache(cached), engine = engine)

        repo.refresh()

        assertEquals(84.5f, repo.rates.value.goldPricePerGram, 0.001f)
    }

    @Test
    fun refresh_skipsRequestWhenEndpointNotConfigured() = runTest {
        var called = false
        val engine = MockEngine { _ ->
            called = true
            respond("{}", HttpStatusCode.OK)
        }
        val repo = repository(endpoint = "", engine = engine)

        repo.refresh()

        assertEquals(false, called)
        assertEquals(NisabRates.FALLBACK.goldPricePerGram, repo.rates.value.goldPricePerGram, 0.001f)
    }

    @Test
    fun unreadableResponse_doesNotOverwriteCache() = runTest {
        val cache = RecordingCache(NisabRates(88.0f, 1.1f, 1_680_000_000_000L))
        val engine = MockEngine { _ ->
            respond(
                content = """{"unexpected": true}""",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val repo = repository(cache = cache, engine = engine)

        repo.refresh()

        assertEquals(88.0f, repo.rates.value.goldPricePerGram, 0.001f)
        assertEquals(0, cache.writeCount)
    }

    @Test
    fun cacheRoundTrip_persistsAllFields() {
        val store = object : com.sakinah.deen.data.repository.NisabRateCacheStore {
            private val floats = mutableMapOf<String, Float>()
            private val longs = mutableMapOf<String, Long>()
            override fun getFloat(key: String, default: Float): Float = floats[key] ?: default
            override fun putFloat(key: String, value: Float) {
                floats[key] = value
            }
            override fun getLong(key: String, default: Long): Long = longs[key] ?: default
            override fun putLong(key: String, value: Long) {
                longs[key] = value
            }
        }
        val cache = com.sakinah.deen.data.repository.PreferencesNisabRateCache(store)
        assertNull(cache.read())

        cache.write(NisabRates(92.0f, 1.3f, 1_763_000_000_000L))
        val restored = cache.read()

        assertEquals(92.0f, restored!!.goldPricePerGram, 0.001f)
        assertEquals(1.3f, restored.silverPricePerGram, 0.001f)
        assertEquals(1_763_000_000_000L, restored.lastUpdatedEpochMillis)
    }
}