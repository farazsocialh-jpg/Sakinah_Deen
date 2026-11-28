package com.sakinah.deen

import com.sakinah.deen.data.repository.NisabRateCache
import com.sakinah.deen.data.repository.NisabRateCacheStore
import com.sakinah.deen.data.repository.NisabRateRepositoryImpl
import com.sakinah.deen.data.repository.NisabRates
import com.sakinah.deen.data.repository.PreferencesNisabRateCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

class NisabRateRepositoryTest {

    private class RecordingCache(initial: NisabRates? = null) : NisabRateCache {
        var stored: NisabRates? = initial
        var writeCount = 0
        override fun read(currencyCode: String): NisabRates? =
            stored?.takeIf { it.currencyCode.equals(currencyCode, ignoreCase = true) }
        override fun write(rates: NisabRates) {
            stored = rates
            writeCount++
        }
    }

    private class FakeStore : NisabRateCacheStore {
        val floats = mutableMapOf<String, Float>()
        val longs = mutableMapOf<String, Long>()
        val strings = mutableMapOf<String, String>()
        override fun getFloat(key: String, default: Float): Float = floats[key] ?: default
        override fun putFloat(key: String, value: Float) { floats[key] = value }
        override fun getLong(key: String, default: Long): Long = longs[key] ?: default
        override fun putLong(key: String, value: Long) { longs[key] = value }
        override fun getString(key: String, default: String): String = strings[key] ?: default
        override fun putString(key: String, value: String) { strings[key] = value }
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

    private fun MockRequestHandleScope.jsonResponse(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf("Content-Type", "application/json")
    )

    private val goldApiStamp = Instant.parse("2026-11-28T05:53:21Z").toEpochMilli()

    @Test
    fun refresh_parsesRatesAndCachesThem() = runTest {
        val cache = RecordingCache()
        val engine = MockEngine { _ ->
            jsonResponse("""{"gold": 91.5, "silver": 1.21, "currency": "USD", "updatedAt": 1763000000}""")
        }
        val repo = repository(cache = cache, engine = engine)

        repo.refresh()

        assertEquals(91.5f, repo.rates.value.goldPricePerGram, 0.001f)
        assertEquals(1.21f, repo.rates.value.silverPricePerGram, 0.001f)
        // Seconds from the endpoint are normalised to epoch millis.
        assertEquals(1_763_000_000_000L, repo.rates.value.lastUpdatedEpochMillis)
        assertEquals("USD", repo.rates.value.currencyCode)
        assertEquals("Live API", repo.rates.value.sourceLabel)
        assertEquals(1, cache.writeCount)
        assertNotNull(cache.stored)
    }

    @Test
    fun refresh_parsesDocumentedSchemaWithDateString() = runTest {
        val cache = RecordingCache()
        val engine = MockEngine { _ ->
            jsonResponse("""{"goldPerGram": 92.75, "silverPerGram": 1.28, "date": "2026-11-12"}""")
        }
        val repo = repository(cache = cache, engine = engine)

        repo.refresh()

        assertEquals(92.75f, repo.rates.value.goldPricePerGram, 0.001f)
        assertEquals(1.28f, repo.rates.value.silverPricePerGram, 0.001f)
        // "2026-11-12" is anchored to UTC midnight, not "now".
        assertEquals(1_794_441_600_000L, repo.rates.value.lastUpdatedEpochMillis)
        // Absent currency is USD, never inferred from the device locale.
        assertEquals("USD", repo.rates.value.currencyCode)
        assertEquals(1, cache.writeCount)
    }

    @Test
    fun refresh_parsesIsoInstantDate() = runTest {
        val engine = MockEngine { _ ->
            jsonResponse("""{"goldPerGram": 90.0, "silverPerGram": 1.2, "date": "2026-11-12T06:30:00Z"}""")
        }
        val repo = repository(engine = engine)

        repo.refresh()

        assertEquals(1_794_465_000_000L, repo.rates.value.lastUpdatedEpochMillis)
    }

    @Test
    fun refresh_parsesCurrencyCodeAlias() = runTest {
        val engine = MockEngine { _ ->
            jsonResponse(
                """{"goldPerGram": 90.0, "silverPerGram": 1.2, "currencyCode": "usd", "date": "2026-11-12"}"""
            )
        }
        val repo = repository(engine = engine)

        repo.refresh()

        assertEquals("USD", repo.rates.value.currencyCode)
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
    fun blankEndpoint_goldApiFallbackConvertsTroyOuncesToGrams() = runTest {
        val cache = RecordingCache()
        val engine = MockEngine { request ->
            when {
                request.url.toString().endsWith("/XAU") ->
                    jsonResponse("""{"currency":"USD","price":3650.0,"updatedAt":"2026-11-28T05:53:21Z"}""")
                request.url.toString().endsWith("/XAG") ->
                    jsonResponse("""{"currency":"USD","price":45.0,"updatedAt":"2026-11-28T05:53:21Z"}""")
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val repo = repository(cache = cache, endpoint = "", engine = engine)

        val result = repo.refresh()

        assertTrue(result.isSuccess)
        assertEquals(3650.0 / 31.1034768, repo.rates.value.goldPricePerGram.toDouble(), 0.001)
        assertEquals(45.0 / 31.1034768, repo.rates.value.silverPricePerGram.toDouble(), 0.001)
        assertEquals("USD", repo.rates.value.currencyCode)
        assertEquals(goldApiStamp, repo.rates.value.lastUpdatedEpochMillis)
        assertTrue(repo.rates.value.sourceLabel!!.contains("gold-api.com"))
        assertTrue(
            "Unexpected hosts: ${engine.requestHistory.map { it.url.host }}",
            engine.requestHistory.all {
                it.url.host in setOf("nisab.tahababa.com", "api.gold-api.com")
            }
        )
        assertEquals(1, cache.writeCount)
    }

    @Test
    fun unreachableConfiguredEndpoint_fallsBackToGoldApi() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.host == "rates.example.test" -> throw IOException("offline")
                request.url.toString().endsWith("/XAU") ->
                    jsonResponse("""{"currency":"USD","price":3650.0,"updatedAt":"2026-11-28T05:53:21Z"}""")
                request.url.toString().endsWith("/XAG") ->
                    jsonResponse("""{"currency":"USD","price":45.0,"updatedAt":"2026-11-28T05:53:21Z"}""")
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val repo = repository(engine = engine)

        assertTrue(repo.refresh().isSuccess)
        assertEquals(3650.0 / 31.1034768, repo.rates.value.goldPricePerGram.toDouble(), 0.001)
        assertEquals("USD", repo.rates.value.currencyCode)
    }

    @Test
    fun blankEndpoint_unsupportedGoldApiShapeKeepsOfflineFallback() = runTest {
        var called = false
        val engine = MockEngine { request ->
            called = true
            jsonResponse("""{"unexpected": true}""")
        }
        val repo = repository(endpoint = "", engine = engine)

        val result = repo.refresh()

        assertTrue(result.isFailure)
        assertTrue(called)
        assertTrue(engine.requestHistory.all {
            it.url.host in setOf("nisab.tahababa.com", "api.gold-api.com")
        })
        assertEquals(NisabRates.FALLBACK.goldPricePerGram, repo.rates.value.goldPricePerGram, 0.001f)
        assertEquals("USD", repo.rates.value.currencyCode)
        assertTrue(repo.rates.value.sourceLabel!!.contains("Offline", ignoreCase = true))
    }

    @Test
    fun refresh_convertsUsdRatesToSelectedCurrencyViaPublicFx() = runTest {
        val cache = RecordingCache()
        val engine = MockEngine { request ->
            when {
                request.url.host == "open.er-api.com" -> jsonResponse(
                    """{"result":"success","time_last_update_unix":1764200000,"rates":{"QAR":3.64,"EUR":0.91}}"""
                )
                else -> jsonResponse(
                    """{"gold": 91.5, "silver": 1.21, "currency": "USD", "updatedAt": 1763000000}"""
                )
            }
        }
        val repo = repository(cache = cache, engine = engine)
        repo.setCurrency("QAR")

        val result = repo.refresh()

        assertTrue(result.isSuccess)
        assertEquals(91.5f * 3.64f, repo.rates.value.goldPricePerGram, 0.001f)
        assertEquals(1.21f * 3.64f, repo.rates.value.silverPricePerGram, 0.001f)
        assertEquals("QAR", repo.rates.value.currencyCode)
        assertEquals(1_764_200_000_000L, repo.rates.value.lastUpdatedEpochMillis)
        assertTrue(repo.rates.value.sourceLabel!!.contains("er-api"))
        assertEquals("QAR", cache.stored!!.currencyCode)
    }

    @Test
    fun refresh_currencyMismatchWithoutFxKeepsCachedRate() = runTest {
        val engine = MockEngine { _ ->
            jsonResponse("""{"gold": 91.5, "silver": 1.21, "currency": "EUR"}""")
        }
        val repo = repository(engine = engine)

        // EUR prices cannot be relabelled as the requested USD quote without a rate.
        assertTrue(repo.refresh().isFailure)
        assertEquals("USD", repo.rates.value.currencyCode)
    }

    @Test
    fun refresh_fxFailureKeepsOfflineFallback() = runTest {
        val cache = RecordingCache(NisabRates(88.0f, 1.1f, 1_680_000_000_000L))
        val engine = MockEngine { request ->
            if (request.url.host == "open.er-api.com") {
                respondError(HttpStatusCode.ServiceUnavailable)
            } else {
                jsonResponse("""{"gold": 91.5, "silver": 1.21, "currency": "USD"}""")
            }
        }
        val repo = repository(cache = cache, engine = engine)
        repo.setCurrency("QAR")

        assertTrue(repo.refresh().isFailure)

        assertEquals(NisabRates.FALLBACK.goldPricePerGram, repo.rates.value.goldPricePerGram, 0.001f)
        assertEquals("USD", repo.rates.value.currencyCode)
        assertEquals(0, cache.writeCount)
    }

    @Test
    fun unreadableResponse_doesNotOverwriteCache() = runTest {
        val cache = RecordingCache(NisabRates(88.0f, 1.1f, 1_680_000_000_000L))
        val engine = MockEngine { _ -> jsonResponse("""{"unexpected": true}""") }
        val repo = repository(cache = cache, engine = engine)

        repo.refresh()

        assertEquals(88.0f, repo.rates.value.goldPricePerGram, 0.001f)
        assertEquals(0, cache.writeCount)
    }

    @Test
    fun cacheRoundTrip_persistsAllFieldsIncludingCurrency() {
        val store = FakeStore()
        val cache = PreferencesNisabRateCache(store)
        assertNull(cache.read())
        assertNull(cache.read("QAR"))

        cache.write(
            NisabRates(
                goldPricePerGram = 92.0f,
                silverPricePerGram = 1.3f,
                lastUpdatedEpochMillis = 1_763_000_000_000L,
                currencyCode = "USD",
                sourceLabel = "Live API"
            )
        )
        val restored = cache.read("USD")

        assertEquals(92.0f, restored!!.goldPricePerGram, 0.001f)
        assertEquals(1.3f, restored.silverPricePerGram, 0.001f)
        assertEquals(1_763_000_000_000L, restored.lastUpdatedEpochMillis)
        assertEquals("USD", restored.currencyCode)
        assertEquals("Live API", restored.sourceLabel)
        // A USD quote must never be served as a QAR quote.
        assertNull(cache.read("QAR"))
    }

    @Test
    fun nisabAlZakatFeed_parsesMadhabThresholdsForSelectedCurrency() = runTest {
        val body = """
            {
              "meta": {"updated_at": "2026-11-28T06:08:14.030Z", "source": "Nisab Al Zakat"},
              "prices": {"gold": {"per_gram": 117.48726}, "silver": {"per_gram": 1.410131}},
              "nisab": {
                "hanafi": {
                  "gold": {"grams": 85, "values": {"QAR": 36350.56}},
                  "silver": {"grams": 595, "values": {"QAR": 3054.06}}
                }
              }
            }
        """.trimIndent()
        val repo = repository(endpoint = "", engine = MockEngine { jsonResponse(body) })
        repo.setCurrency("QAR")

        assertTrue(repo.refresh().isSuccess)
        val rates = repo.rates.value
        assertEquals(36350.56f / 85f, rates.goldPricePerGram, 0.01f)
        assertEquals(3054.06f / 595f, rates.silverPricePerGram, 0.01f)
        assertEquals("QAR", rates.currencyCode)
        assertEquals(
            Instant.parse("2026-11-28T06:08:14.030Z").toEpochMilli(),
            rates.lastUpdatedEpochMillis
        )
        assertTrue(rates.sourceLabel!!.contains("Nisab Al Zakat"))
    }
}
