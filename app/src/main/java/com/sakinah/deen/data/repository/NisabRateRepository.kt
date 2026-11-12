package com.sakinah.deen.data.repository

import com.sakinah.deen.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class NisabRates(
    val goldPricePerGram: Float,
    val silverPricePerGram: Float,
    val lastUpdatedEpochMillis: Long
) {
    companion object {
        /** Conservative last-resort values used only before the first successful fetch. */
        val FALLBACK = NisabRates(
            goldPricePerGram = 88.40f,
            silverPricePerGram = 1.15f,
            lastUpdatedEpochMillis = 0L
        )
    }
}

interface NisabRateRepository {
    val rates: StateFlow<NisabRates>

    /** Fetches fresh metal prices. Never throws: on any failure the cached rate is kept. */
    suspend fun refresh()
}

/** Last successful rate persisted so the nisab threshold survives process death offline. */
interface NisabRateCache {
    fun read(): NisabRates?
    fun write(rates: NisabRates)
}

/**
 * Fetches current gold/silver prices per gram from a configurable endpoint
 * ([BuildConfig.NISAB_RATE_URL]) and caches the last successful response.
 *
 * The documented endpoint contract is
 * `{"goldPerGram": 88.4, "silverPerGram": 1.15, "date": "2026-11-12"}`, where `date`
 * may also be an ISO-8601 instant (`2026-11-12T06:00:00Z`). Tolerant aliases
 * (`gold`, `goldPricePerGram`, numeric `updatedAt` seconds or millis) are accepted so an
 * existing price feed can be pointed at without reshaping it.
 */
class NisabRateRepositoryImpl(
    private val client: HttpClient,
    private val json: Json,
    private val endpointUrl: String = BuildConfig.NISAB_RATE_URL,
    private val cache: NisabRateCache? = null,
    private val initial: NisabRates = NisabRates.FALLBACK
) : NisabRateRepository {

    companion object {
        private const val USER_AGENT = "Sakinah-Android/1.0 (offline-first Islamic companion)"
        private val GOLD_KEYS = listOf("gold", "goldPerGram", "goldPricePerGram")
        private val SILVER_KEYS = listOf("silver", "silverPerGram", "silverPricePerGram")
        private val TIMESTAMP_KEYS = listOf("updatedAt", "timestamp", "lastUpdated")
        private val DATE_KEYS = listOf("date", "updatedAtDate", "lastUpdatedDate")
    }

    private val _rates = MutableStateFlow(cache?.read() ?: initial)
    override val rates: StateFlow<NisabRates> = _rates.asStateFlow()

    override suspend fun refresh() {
        val fresh = runCatching { fetchRemote() }.getOrNull() ?: return
        if (fresh.goldPricePerGram <= 0f && fresh.silverPricePerGram <= 0f) return
        cache?.write(fresh)
        _rates.value = fresh
    }

    private suspend fun fetchRemote(): NisabRates? {
        if (endpointUrl.isBlank() || endpointUrl.startsWith("MY_")) return null

        val response = client.get(endpointUrl) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "application/json")
        }
        if (!response.status.isSuccess()) return null

        val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return null
        val gold = numberOf(root, GOLD_KEYS)?.toFloat()
        val silver = numberOf(root, SILVER_KEYS)?.toFloat()
        if (gold == null && silver == null) return null

        return NisabRates(
            goldPricePerGram = gold ?: _rates.value.goldPricePerGram,
            silverPricePerGram = silver ?: _rates.value.silverPricePerGram,
            lastUpdatedEpochMillis = timestampOf(root) ?: System.currentTimeMillis()
        )
    }

    private fun numberOf(root: JsonObject, keys: List<String>): Double? = keys.firstNotNullOfOrNull { key ->
        val element = root[key] as? JsonPrimitive ?: return@firstNotNullOfOrNull null
        element.doubleOrNull ?: element.contentOrNull?.trim()?.toDoubleOrNull()
    }

    /** The endpoint may send seconds or milliseconds; normalise to epoch millis. */
    private fun timestampOf(root: JsonObject): Long? {
        val raw = keysFirstLong(root, TIMESTAMP_KEYS)
        if (raw != null) return if (raw in 1..9_999_999_999L) raw * 1000L else raw
        return dateOf(root)
    }

    /**
     * Parses the documented `date` field. A bare calendar date is anchored to UTC midnight
     * so the freshness label is stable regardless of the device time zone.
     */
    private fun dateOf(root: JsonObject): Long? = DATE_KEYS.firstNotNullOfOrNull { key ->
        val raw = (root[key] as? JsonPrimitive)?.contentOrNull?.trim()
        if (raw.isNullOrBlank()) null else parseIsoDate(raw)
    }

    internal fun parseIsoDate(raw: String): Long? = runCatching {
        runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
                .getOrNull()
    }.getOrNull()

    private fun keysFirstLong(root: JsonObject, keys: List<String>): Long? = keys.firstNotNullOfOrNull { key ->
        val element = root[key] as? JsonPrimitive ?: return@firstNotNullOfOrNull null
        element.doubleOrNull?.toLong() ?: element.contentOrNull?.trim()?.toLongOrNull()
    }
}

/** SharedPreferences-backed cache: the app already treats this store as its offline ledger. */
class PreferencesNisabRateCache(private val prefs: NisabRateCacheStore) : NisabRateCache {

    override fun read(): NisabRates? {
        val gold = prefs.getFloat(KEY_GOLD, -1f)
        val silver = prefs.getFloat(KEY_SILVER, -1f)
        if (gold <= 0f && silver <= 0f) return null
        return NisabRates(
            goldPricePerGram = if (gold > 0f) gold else NisabRates.FALLBACK.goldPricePerGram,
            silverPricePerGram = if (silver > 0f) silver else NisabRates.FALLBACK.silverPricePerGram,
            lastUpdatedEpochMillis = prefs.getLong(KEY_TIMESTAMP, 0L)
        )
    }

    override fun write(rates: NisabRates) {
        prefs.putFloat(KEY_GOLD, rates.goldPricePerGram)
        prefs.putFloat(KEY_SILVER, rates.silverPricePerGram)
        prefs.putLong(KEY_TIMESTAMP, rates.lastUpdatedEpochMillis)
    }

    private companion object {
        const val KEY_GOLD = "nisab_gold_per_gram"
        const val KEY_SILVER = "nisab_silver_per_gram"
        const val KEY_TIMESTAMP = "nisab_rates_updated_at"
    }
}

/** Thin seam so the cache can be unit-tested without an Android context. */
interface NisabRateCacheStore {
    fun getFloat(key: String, default: Float): Float
    fun putFloat(key: String, value: Float)
    fun getLong(key: String, default: Long): Long
    fun putLong(key: String, value: Long)
}

class SharedPreferencesNisabRateCacheStore(
    private val prefs: android.content.SharedPreferences
) : NisabRateCacheStore {
    override fun getFloat(key: String, default: Float): Float = prefs.getFloat(key, default)
    override fun putFloat(key: String, value: Float) = prefs.edit().putFloat(key, value).apply()
    override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
}
