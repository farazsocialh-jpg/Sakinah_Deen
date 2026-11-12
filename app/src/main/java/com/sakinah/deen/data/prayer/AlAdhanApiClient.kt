package com.sakinah.deen.data.prayer

import com.sakinah.deen.data.model.PrayerType
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One cached day of an AlAdhan monthly calendar. */
data class AlAdhanDay(
    val date: LocalDate,
    val times: Map<PrayerType, LocalTime>
)

/**
 * A full monthly calendar (~29-31 days) returned by AlAdhan's
 * `/v1/calendar/{year}/{month}` endpoint. [methodName] is the authority name AlAdhan
 * reports back (`meta.method.name`), [timeZone] the IANA zone the timings refer to.
 */
data class AlAdhanMonthlyTimetable(
    val days: List<AlAdhanDay>,
    val methodName: String = "",
    val timeZone: String? = null
)

/**
 * Client for the free AlAdhan prayer-time API (no key required).
 *
 * Request: `GET /v1/calendar/{year}/{month}?latitude=..&longitude=..&method=..&school=..`
 * where `school` is 0 for Shafi'i/Maliki/Hanbali and 1 for Hanafi.
 *
 * Timings come back as location wall-clock strings such as `"04:24 (+03)"`; the timezone
 * annotation is dropped and the zone is taken from `meta.timezone` instead.
 *
 * Every failure mode (network, HTTP status, malformed payload) returns `null` so callers
 * can fall back to the cache or a local calculation instead of crashing.
 */
class AlAdhanApiClient(
    private val client: HttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    },
    private val baseUrl: String = DEFAULT_BASE_URL
) {

    companion object {
        const val DEFAULT_BASE_URL = "https://api.aladhan.com"
        private const val USER_AGENT = "Sakinah-Android/1.0 (offline-first Islamic companion)"
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        private val TIME_FORMATS = listOf("HH:mm", "H:mm")

        // Sunrise is mandatory too: the Salah screen and the cache rows both carry it.
        private val REQUIRED_PRAYERS = listOf(
            PrayerType.FAJR,
            PrayerType.SUNRISE,
            PrayerType.DHUHR,
            PrayerType.ASR,
            PrayerType.MAGHRIB,
            PrayerType.ISHA
        )

        private fun keyOf(type: PrayerType): String = when (type) {
            PrayerType.FAJR -> "Fajr"
            PrayerType.SUNRISE -> "Sunrise"
            PrayerType.DHUHR -> "Dhuhr"
            PrayerType.ASR -> "Asr"
            PrayerType.MAGHRIB -> "Maghrib"
            PrayerType.ISHA -> "Isha"
        }
    }

    suspend fun fetchMonth(
        year: Int,
        month: Int,
        latitude: Double,
        longitude: Double,
        methodId: Int,
        schoolId: Int
    ): AlAdhanMonthlyTimetable? {
        val response = runCatching {
            client.get("$baseUrl/v1/calendar/$year/$month") {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("method", methodId)
                parameter("school", schoolId)
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, USER_AGENT)
            }
        }.getOrNull() ?: return null

        if (!response.status.isSuccess()) return null

        val body = runCatching { response.bodyAsText() }.getOrNull() ?: return null
        return runCatching { parse(body) }.getOrNull()
    }

    internal fun parse(body: String): AlAdhanMonthlyTimetable? {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return null
        val data = root["data"] as? JsonArray ?: return null

        val days = data.mapNotNull { parseDay(it) }
        if (days.isEmpty()) return null

        val meta = (data.firstOrNull() as? JsonObject)?.get("meta") as? JsonObject
        val methodName = ((meta?.get("method") as? JsonObject)?.get("name") as? JsonPrimitive)
            ?.contentOrNull
            .orEmpty()
        val timeZone = (meta?.get("timezone") as? JsonPrimitive)?.contentOrNull

        return AlAdhanMonthlyTimetable(
            days = days.sortedBy { it.date },
            methodName = methodName,
            timeZone = timeZone
        )
    }

    private fun parseDay(element: JsonElement): AlAdhanDay? {
        val entry = element as? JsonObject ?: return null
        val timings = entry["timings"] as? JsonObject ?: return null
        val date = parseDate(entry) ?: return null

        val times = buildMap {
            PrayerType.entries.forEach { type ->
                val raw = (timings[keyOf(type)] as? JsonPrimitive)?.contentOrNull
                parseTime(raw)?.let { put(type, it) }
            }
        }
        if (REQUIRED_PRAYERS.any { it !in times }) return null

        return AlAdhanDay(date = date, times = times)
    }

    /** `date.gregorian.date` is `DD-MM-YYYY`; `date.timestamp` is a UTC fallback. */
    private fun parseDate(entry: JsonObject): LocalDate? {
        val gregorian = (entry["date"] as? JsonObject)?.get("gregorian") as? JsonObject
        val raw = (gregorian?.get("date") as? JsonPrimitive)?.contentOrNull?.trim()
        if (!raw.isNullOrBlank()) {
            runCatching { LocalDate.parse(raw, DATE_FORMAT) }.getOrNull()?.let { return it }
        }
        val timestamp = (entry["date"] as? JsonObject)
            ?.let { (it["timestamp"] as? JsonPrimitive)?.contentOrNull }
            ?.toLongOrNull()
            ?: return null
        return Instant.ofEpochSecond(timestamp).atZone(ZoneOffset.UTC).toLocalDate()
    }

    private fun parseTime(raw: String?): LocalTime? {
        val cleaned = raw?.trim()?.substringBefore(' ')?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        TIME_FORMATS.forEach { format ->
            runCatching { LocalTime.parse(cleaned, DateTimeFormatter.ofPattern(format)) }
                .getOrNull()
                ?.let { return it }
        }
        return runCatching { LocalTime.parse(cleaned) }.getOrNull()
    }
}
