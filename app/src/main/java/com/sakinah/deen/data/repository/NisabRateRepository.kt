package com.sakinah.deen.data.repository

import androidx.core.content.edit
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
import java.util.Locale

/**
 * Metal prices per gram are meaningless without the currency they are quoted in, so the
 * currency travels with the numbers instead of being implied by the user's balance sheet.
 */
data class NisabRates(
    val goldPricePerGram: Float,
    val silverPricePerGram: Float,
    val lastUpdatedEpochMillis: Long,
    /** ISO-4217 currency of both per-gram prices. */
    val currencyCode: String = "USD",
    /** Where these numbers came from, e.g. "Offline estimate" or "gold-api.com". */
    val sourceLabel: String? = null
) {
    companion object {
        /** Conservative last-resort values used only before the first successful fetch. */
        val FALLBACK = NisabRates(
            goldPricePerGram = 88.40f,
            silverPricePerGram = 1.15f,
            lastUpdatedEpochMillis = 0L,
            currencyCode = "USD",
            sourceLabel = "Offline estimate"
        )
    }
}

interface NisabRateRepository {
    val rates: StateFlow<NisabRates>

    /** Fetches fresh metal prices. Never throws: on any failure the cached rate is kept. */
    suspend fun refresh(): Result<Unit>

    /**
     * Switches the currency the rates are quoted in. Currency-aware implementations
     * re-read their cache and convert on the next [refresh]. The default is a no-op so
     * simple/currency-agnostic implementations keep working.
     */
    fun setCurrency(code: String) = Unit
}

/** Last successful rate per currency so the threshold survives process death offline. */
interface NisabRateCache {
    fun read(currencyCode: String = "USD"): NisabRates?
    fun write(rates: NisabRates)
}

/**
 * Fetches current gold/silver prices per gram from a configurable endpoint
 * ([BuildConfig.NISAB_RATE_URL]) and caches the last successful response per currency.
 *
 * The documented endpoint contract is
 * `{"goldPerGram": 88.4, "silverPerGram": 1.15, "date": "2026-11-12", "currency": "USD"}`,
 * where `date` may also be an ISO-8601 instant (`2026-11-12T06:00:00Z`). Tolerant aliases
 * (`gold`, `goldPricePerGram`, numeric `updatedAt` seconds or millis, `currencyCode`) are
 * accepted so an existing price feed can be pointed at without reshaping it.
 *
 * When that endpoint is blank or returns an unsupported shape, the public
 * `api.gold-api.com/price/XAU` and `/XAG` quotes (USD per troy ounce, 31.1034768 g) are
 * used. If the quote currency differs from the user's, `open.er-api.com` converts it.
 * Every failure path falls back to the cached rate or the labelled offline estimate.
 */
class NisabRateRepositoryImpl(
    private val client: HttpClient,
    private val json: Json,
    private val endpointUrl: String = BuildConfig.NISAB_RATE_URL
        .takeIf { it.isNotBlank() && !it.startsWith("MY_") } ?: NISAB_AL_ZAKAT_URL,
    private val cache: NisabRateCache? = null,
    private val initial: NisabRates = NisabRates.FALLBACK,
    /** Lowercase madhhab key used by the Nisab Al Zakat feed (hanafi/maliki/shafii/hanbali). */
    private val madhhabKey: String = "hanafi"
) : NisabRateRepository {

    companion object {
        private const val USER_AGENT = "ImanCore-Android/1.0 (offline-first Islamic companion)"
        private const val NISAB_AL_ZAKAT_URL = "https://nisab.tahababa.com/nisab.json"
        private const val GOLD_API_XAU_URL = "https://api.gold-api.com/price/XAU"
        private const val GOLD_API_XAG_URL = "https://api.gold-api.com/price/XAG"
        private const val FX_RATES_URL = "https://open.er-api.com/v6/latest/USD"
        /** A troy ounce is exactly 31.1034768 grams; the Nisab is 85 g gold / 595 g silver. */
        private const val GRAMS_PER_TROY_OUNCE = 31.1034768

        private val GOLD_KEYS = listOf("gold", "goldPerGram", "goldPricePerGram")
        private val SILVER_KEYS = listOf("silver", "silverPerGram", "silverPricePerGram")
        private val TIMESTAMP_KEYS = listOf("updatedAt", "timestamp", "lastUpdated")
        private val DATE_KEYS = listOf("date", "updatedAt", "updatedAtDate", "lastUpdatedDate")
        private val CURRENCY_KEYS = listOf("currency", "currencyCode")
        private val PRICE_KEYS = listOf("price")
    }

    /** Currency the app wants prices in; updated when the user changes the wallet currency. */
    private var requestedCurrencyCode: String = initial.currencyCode.ifBlank { "USD" }.uppercase(Locale.ROOT)

    private val _rates = MutableStateFlow(cache?.read(requestedCurrencyCode) ?: initial)
    override val rates: StateFlow<NisabRates> = _rates.asStateFlow()

    override fun setCurrency(code: String) {
        val normalized = code.trim().uppercase(Locale.ROOT).ifBlank { "USD" }
        if (normalized == requestedCurrencyCode) return
        requestedCurrencyCode = normalized
        // Never leave a quote from the previous currency on screen: it would be read as
        // if those numbers were prices in the newly selected currency.
        _rates.value = cache?.read(normalized) ?: NisabRates.FALLBACK
    }

    override suspend fun refresh(): Result<Unit> {
        var fresh = runCatching {
            fetchRemote() ?: fetchGoldApi()
        }.getOrNull()
            ?: return Result.failure(IllegalStateException("Nisab rate endpoint unreachable"))
        if (fresh.goldPricePerGram <= 0f || fresh.silverPricePerGram <= 0f) {
            return Result.failure(IllegalStateException("Invalid nisab rates received"))
        }
        if (!fresh.currencyCode.equals(requestedCurrencyCode, ignoreCase = true)) {
            fresh = convertFromUsd(fresh, requestedCurrencyCode)
                ?: return Result.failure(IllegalStateException("Nisab rate currency is unavailable"))
        }
        cache?.write(fresh)
        _rates.value = fresh
        return Result.success(Unit)
    }

    /** Configured feed. `null` (blank URL, unsupported shape or failure) triggers gold-api. */
    private suspend fun fetchRemote(): NisabRates? {
        val url = endpointUrl.takeIf { it.isNotBlank() && !it.startsWith("MY_") }
            ?: NISAB_AL_ZAKAT_URL
        return runCatching {
            val response = client.get(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, "application/json")
            }
            if (!response.status.isSuccess()) return@runCatching null

            val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
                ?: return@runCatching null
            nisabAlZakatRates(root)
                ?: run {
                    val gold = numberOf(root, GOLD_KEYS)?.toFloat()
                    val silver = numberOf(root, SILVER_KEYS)?.toFloat()
                    if (gold == null && silver == null) return@runCatching null
                    NisabRates(
                        goldPricePerGram = gold ?: _rates.value.goldPricePerGram,
                        silverPricePerGram = silver ?: _rates.value.silverPricePerGram,
                        lastUpdatedEpochMillis = timestampOf(root) ?: System.currentTimeMillis(),
                        currencyCode = stringOf(root, CURRENCY_KEYS)?.uppercase(Locale.ROOT) ?: "USD",
                        sourceLabel = "Live API"
                    )
                }
        }.getOrNull()
    }

    private data class MetalQuote(
        val pricePerGram: Float,
        val currencyCode: String,
        val lastUpdatedEpochMillis: Long
    )

    /** Public key-free fallback: USD per troy ounce, normalised to USD per gram. */
    private suspend fun fetchGoldApi(): NisabRates? {
        val gold = fetchMetalQuote(GOLD_API_XAU_URL) ?: return null
        val silver = fetchMetalQuote(GOLD_API_XAG_URL) ?: return null
        return NisabRates(
            goldPricePerGram = gold.pricePerGram,
            silverPricePerGram = silver.pricePerGram,
            lastUpdatedEpochMillis = maxOf(gold.lastUpdatedEpochMillis, silver.lastUpdatedEpochMillis),
            currencyCode = gold.currencyCode,
            sourceLabel = "gold-api.com (USD per troy ounce)"
        )
    }

    private suspend fun fetchMetalQuote(url: String): MetalQuote? {
        val response = client.get(url) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "application/json")
        }
        if (!response.status.isSuccess()) return null

        val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return null
        val pricePerOunce = numberOf(root, PRICE_KEYS) ?: return null
        if (pricePerOunce <= 0.0) return null
        val currency = stringOf(root, CURRENCY_KEYS)?.uppercase(Locale.ROOT) ?: "USD"
        return MetalQuote(
            pricePerGram = (pricePerOunce / GRAMS_PER_TROY_OUNCE).toFloat(),
            currencyCode = currency,
            lastUpdatedEpochMillis = timestampOf(root) ?: System.currentTimeMillis()
        )
    }

    /** Converts USD quotes with the keyless public FX feed; never guesses a rate. */
    private suspend fun convertFromUsd(rates: NisabRates, targetCurrency: String): NisabRates? {
        if (!rates.currencyCode.equals("USD", ignoreCase = true)) return null
        if (targetCurrency == "USD") return null

        val response = client.get(FX_RATES_URL) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "application/json")
        }
        if (!response.status.isSuccess()) return null
        val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return null
        if (!stringOf(root, listOf("result")).equals("success", ignoreCase = true)) return null
        val factor = numberOf(root["rates"] as? JsonObject ?: return null, listOf(targetCurrency))
            ?: return null
        if (factor <= 0.0) return null

        val fxEpoch = keysFirstLong(root, listOf("time_last_update_unix"))
        return rates.copy(
            goldPricePerGram = (rates.goldPricePerGram * factor).toFloat(),
            silverPricePerGram = (rates.silverPricePerGram * factor).toFloat(),
            lastUpdatedEpochMillis = fxEpoch?.let(::normalizeEpoch) ?: rates.lastUpdatedEpochMillis,
            currencyCode = targetCurrency,
            sourceLabel = "${rates.sourceLabel} · USD via er-api"
        )
    }

    /** Parses the keyless `nisab.tahababa.com/nisab.json` schema into per-gram prices. */
    private fun nisabAlZakatRates(root: JsonObject): NisabRates? {
        val meta = root["meta"] as? JsonObject ?: return null
        val updated = stringOf(meta, listOf("updated_at"))?.let(::parseIsoDate)
            ?: timestampOf(meta)
            ?: return null
        val school = (root["nisab"] as? JsonObject ?: return null)
            .let { it[madhhabKey.lowercase(Locale.ROOT)] ?: it["hanafi"] } as? JsonObject
            ?: return null

        fun perGram(metal: String, defaultGrams: Double): Float? {
            val entry = school[metal] as? JsonObject ?: return null
            val values = entry["values"] as? JsonObject ?: return null
            val threshold = numberOf(values, listOf(requestedCurrencyCode)) ?: return null
            val grams = numberOf(entry, listOf("grams")) ?: defaultGrams
            return (threshold / grams).toFloat().takeIf { it > 0f }
        }

        val gold = perGram("gold", 85.0) ?: return null
        val silver = perGram("silver", 595.0) ?: return null
        val source = stringOf(meta, listOf("source")) ?: "Nisab Al Zakat"
        return NisabRates(
            goldPricePerGram = gold,
            silverPricePerGram = silver,
            lastUpdatedEpochMillis = updated,
            currencyCode = requestedCurrencyCode,
            sourceLabel = "Live · $source"
        )
    }

    private fun numberOf(root: JsonObject, keys: List<String>): Double? = keys.firstNotNullOfOrNull { key ->
        val element = root[key] as? JsonPrimitive ?: return@firstNotNullOfOrNull null
        element.doubleOrNull ?: element.contentOrNull?.trim()?.toDoubleOrNull()
    }

    private fun stringOf(root: JsonObject, keys: List<String>): String? = keys.firstNotNullOfOrNull { key ->
        (root[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
    }

    /** The endpoint may send seconds or milliseconds; normalise to epoch millis. */
    private fun timestampOf(root: JsonObject): Long? {
        val raw = keysFirstLong(root, TIMESTAMP_KEYS)
        if (raw != null) return normalizeEpoch(raw)
        return dateOf(root)
    }

    private fun normalizeEpoch(raw: Long): Long =
        if (raw in 1..9_999_999_999L) raw * 1000L else raw

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

    override fun read(currencyCode: String): NisabRates? {
        val requested = currencyCode.trim().uppercase(Locale.ROOT)
        val prefix = keyPrefix(requested)
        val storedCurrency = prefs.getString(prefix + KEY_CURRENCY, "").uppercase(Locale.ROOT)
        // Quoted numbers without their currency are unsafe: reject a stale entry from a
        // different currency instead of silently relabelling it as the selected one.
        if (storedCurrency != requested) return null
        val gold = prefs.getFloat(prefix + KEY_GOLD, -1f)
        val silver = prefs.getFloat(prefix + KEY_SILVER, -1f)
        if (gold <= 0f && silver <= 0f) return null
        return NisabRates(
            goldPricePerGram = if (gold > 0f) gold else NisabRates.FALLBACK.goldPricePerGram,
            silverPricePerGram = if (silver > 0f) silver else NisabRates.FALLBACK.silverPricePerGram,
            lastUpdatedEpochMillis = prefs.getLong(prefix + KEY_TIMESTAMP, 0L),
            currencyCode = storedCurrency,
            sourceLabel = prefs.getString(prefix + KEY_SOURCE, "").ifBlank { "Cached rate" }
        )
    }

    override fun write(rates: NisabRates) {
        val prefix = keyPrefix(rates.currencyCode)
        prefs.putFloat(prefix + KEY_GOLD, rates.goldPricePerGram)
        prefs.putFloat(prefix + KEY_SILVER, rates.silverPricePerGram)
        prefs.putLong(prefix + KEY_TIMESTAMP, rates.lastUpdatedEpochMillis)
        prefs.putString(prefix + KEY_CURRENCY, rates.currencyCode.trim().uppercase(Locale.ROOT))
        prefs.putString(prefix + KEY_SOURCE, rates.sourceLabel.orEmpty())
    }

    private fun keyPrefix(currencyCode: String): String =
        "nisab_${currencyCode.trim().uppercase(Locale.ROOT)}_"

    private companion object {
        const val KEY_GOLD = "gold_per_gram"
        const val KEY_SILVER = "silver_per_gram"
        const val KEY_TIMESTAMP = "rates_updated_at"
        const val KEY_CURRENCY = "currency"
        const val KEY_SOURCE = "source"
    }
}

/** Thin seam so the cache can be unit-tested without an Android context. */
interface NisabRateCacheStore {
    fun getFloat(key: String, default: Float): Float
    fun putFloat(key: String, value: Float)
    fun getLong(key: String, default: Long): Long
    fun putLong(key: String, value: Long)
    /** Defaults keep simple/test stores compiling while the currency tag is optional. */
    fun getString(key: String, default: String): String = default
    fun putString(key: String, value: String) = Unit
}

class SharedPreferencesNisabRateCacheStore(
    private val prefs: android.content.SharedPreferences
) : NisabRateCacheStore {
    override fun getFloat(key: String, default: Float): Float = prefs.getFloat(key, default)
    override fun putFloat(key: String, value: Float) = prefs.edit { this.putFloat(key, value) }
    override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    override fun putLong(key: String, value: Long) = prefs.edit { this.putLong(key, value) }
    override fun getString(key: String, default: String): String = prefs.getString(key, default) ?: default
    override fun putString(key: String, value: String) = prefs.edit { this.putString(key, value) }
}
