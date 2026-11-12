package com.sakinah.deen.data.repository

import com.sakinah.deen.data.database.dao.PrayerTimetableDao
import com.sakinah.deen.data.database.entity.PrayerTimetableEntity
import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.PrayerAdjustments
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prayer.AlAdhanApiClient
import com.sakinah.deen.data.prayer.PrayerMethodSettings
import com.sakinah.deen.data.prayer.PrayerTimeSnapshot
import com.sakinah.deen.data.prayer.SunniAuthority
import com.sakinah.deen.data.prayer.SunniMethodMapper
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone as KTimeZone
import kotlinx.datetime.toJavaLocalTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.datetime.LocalDate as KLocalDate

/** Where the timings on screen came from. */
enum class PrayerTimeSource {
    /** Freshly fetched from the AlAdhan calendar endpoint during this resolution. */
    ONLINE,

    /** Served from the encrypted 30-day Room cache. */
    CACHE,

    /** Computed on device because neither the cache nor the network could answer. */
    OFFLINE_FALLBACK
}

data class ResolvedPrayerTimes(
    val times: Map<PrayerType, LocalTime>,
    val source: PrayerTimeSource,
    val authority: SunniAuthority,
    val fetchedAtEpochMillis: Long? = null
) {
    /** Concise label for the Salah screen, e.g. "Qatar Awqaf (Online)". */
    val label: String
        get() = when (source) {
            PrayerTimeSource.ONLINE -> "${authority.displayName} (Online)"
            PrayerTimeSource.CACHE -> "${authority.displayName} (Cached)"
            PrayerTimeSource.OFFLINE_FALLBACK -> "Offline Calculation"
        }
}

interface PrayerTimeRepository {
    /** The authority that applies right now: a manual choice, else the country default. */
    fun effectiveAuthority(countryCode: String? = null): SunniAuthority

    /** `null` when the user follows the country's authority automatically. */
    fun currentOverride(): CalculationMethod?

    fun updateOverride(method: CalculationMethod?)

    /**
     * Resolves one day in the documented order: Room cache, then AlAdhan, then the local
     * astronomical calculation. Never throws.
     */
    suspend fun resolveDay(
        date: LocalDate,
        coordinates: Pair<Double, Double>,
        madhhab: Madhhab,
        zoneId: ZoneId = ZoneId.systemDefault(),
        adjustments: PrayerAdjustments = PrayerAdjustments(),
        countryCode: String? = null
    ): ResolvedPrayerTimes
}

/**
 * Offline-first prayer-time resolver backed by the free AlAdhan calendar API.
 *
 * Caching rules:
 *  - The cache key covers rounded coordinates, calculation method, Asr school and month, so
 *    moving city, changing method or crossing into a new month misses the cache and the
 *    monthly calendar is fetched again. Rows for the previous key are pruned.
 *  - Rows fetched more than [FRESH_WINDOW_MS] (7 days) ago are still shown immediately but
 *    are refreshed in the same call when the network answers.
 *  - Failed fetches are throttled for [RETRY_THROTTLE_MS] so an offline device does not
 *    retry the API on every recomposition.
 *  - When nothing online is available the local [PrayerCalculationEngine] answers, and the
 *    result is labelled "Offline Calculation".
 */
class PrayerTimeRepositoryImpl(
    private val dao: PrayerTimetableDao?,
    private val apiClient: AlAdhanApiClient?,
    private val engine: PrayerCalculationEngine,
    private val settings: PrayerMethodSettings? = null,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : PrayerTimeRepository {

    companion object {
        /** Max age of cached authoritative timings before a background refresh is attempted. */
        const val FRESH_WINDOW_MS: Long = 7L * 24 * 60 * 60 * 1000

        /** Minimum gap between failed network attempts for the same context. */
        const val RETRY_THROTTLE_MS: Long = 15L * 60 * 1000

        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /** Cache identity of a monthly timetable; also the Room `cacheKey`. */
        fun cacheKeyOf(
            month: LocalDate,
            coordinates: Pair<Double, Double>,
            methodId: Int,
            schoolId: Int
        ): String = PrayerTimeSnapshot.cacheKeyOf(month, coordinates, methodId, schoolId)

        internal fun formatTime(time: LocalTime): String = time.format(TIME_FORMAT)

        internal fun parseTime(raw: String): LocalTime? =
            runCatching { LocalTime.parse(raw, TIME_FORMAT) }.getOrNull()
    }

    private val mutex = Mutex()
    private val lastAttemptAt = mutableMapOf<String, Long>()

    override fun effectiveAuthority(countryCode: String?): SunniAuthority = SunniMethodMapper.resolve(
        countryCode = countryCode ?: settings?.countryCode(),
        override = settings?.manualMethodOverride()
    )

    override fun currentOverride(): CalculationMethod? = settings?.manualMethodOverride()

    override fun updateOverride(method: CalculationMethod?) {
        settings?.setManualMethodOverride(method)
    }

    override suspend fun resolveDay(
        date: LocalDate,
        coordinates: Pair<Double, Double>,
        madhhab: Madhhab,
        zoneId: ZoneId,
        adjustments: PrayerAdjustments,
        countryCode: String?
    ): ResolvedPrayerTimes = withContext(Dispatchers.IO) {
        if (countryCode != null) settings?.setCountryCode(countryCode)

        val authority = effectiveAuthority(countryCode)
        val schoolId = SunniMethodMapper.schoolId(madhhab)
        val cacheKey = cacheKeyOf(date, coordinates, authority.methodId, schoolId)
        val epochDay = date.toEpochDay()
        val deviceZone = zoneId.id

        // A timetable computed for another timezone would be shown at the wrong hour, so
        // treat it as a miss and let the local engine (which uses the device zone) answer.
        val cached = dao?.getForDay(cacheKey, epochDay)?.takeIf { it.matchesZone(deviceZone) }

        // 1. Cache: fresh authoritative rows work instantly and offline.
        if (cached != null && cached.isFreshOnline(clock())) {
            return@withContext publish(cached, PrayerTimeSource.CACHE, authority)
        }

        // 2. Network: month missing, context changed, or rows older than the fresh window.
        if (refreshMonth(date, coordinates, authority, schoolId, cacheKey)) {
            val fetched = dao?.getForDay(cacheKey, epochDay)?.takeIf { it.matchesZone(deviceZone) }
            if (fetched != null && fetched.isFreshOnline(clock())) {
                return@withContext publish(fetched, PrayerTimeSource.ONLINE, authority)
            }
        }

        // 3. Offline: keep whatever authoritative row survived, else calculate locally.
        if (cached != null) {
            return@withContext publish(cached, PrayerTimeSource.CACHE, authority)
        }

        val local = localCalculation(date, coordinates, authority, madhhab, zoneId, adjustments)
        persistFallback(date, coordinates, authority, schoolId, cacheKey, local, deviceZone)
        publish(local, PrayerTimeSource.OFFLINE_FALLBACK, authority, clock())
    }

    /** Fetches and stores the whole month for [cacheKey]. Returns whether a fetch happened. */
    private suspend fun refreshMonth(
        date: LocalDate,
        coordinates: Pair<Double, Double>,
        authority: SunniAuthority,
        schoolId: Int,
        cacheKey: String
    ): Boolean = mutex.withLock {
        val now = clock()
        val existing = dao?.getForKey(cacheKey).orEmpty()
        if (existing.any { it.isFreshOnline(now) }) return@withLock false
        if (now - (lastAttemptAt[cacheKey] ?: 0L) < RETRY_THROTTLE_MS) return@withLock false

        val client = apiClient ?: return@withLock false
        lastAttemptAt[cacheKey] = now

        val timetable = client.fetchMonth(
            year = date.year,
            month = date.monthValue,
            latitude = coordinates.first,
            longitude = coordinates.second,
            methodId = authority.methodId,
            schoolId = schoolId
        ) ?: return@withLock false

        val rows = timetable.days.map { day ->
            PrayerTimetableEntity(
                cacheKey = cacheKey,
                dateEpochDay = day.date.toEpochDay(),
                fajr = formatTime(day.times[PrayerType.FAJR]!!),
                sunrise = formatTime(day.times[PrayerType.SUNRISE]!!),
                dhuhr = formatTime(day.times[PrayerType.DHUHR]!!),
                asr = formatTime(day.times[PrayerType.ASR]!!),
                maghrib = formatTime(day.times[PrayerType.MAGHRIB]!!),
                isha = formatTime(day.times[PrayerType.ISHA]!!),
                methodName = timetable.methodName.ifBlank { authority.displayName },
                methodId = authority.methodId,
                schoolId = schoolId,
                timeZone = timetable.timeZone.orEmpty(),
                source = PrayerTimeSource.ONLINE.name,
                fetchedAt = now
            )
        }
        if (rows.isEmpty()) return@withLock false

        dao?.replaceMonth(cacheKey, rows)
        dao?.deleteAllExcept(cacheKey)
        lastAttemptAt.keys.retainAll(setOf(cacheKey))
        true
    }

    /**
     * Persists the offline calculation for one day so a restart while still offline can
     * serve it from Room (and the source stays visibly `OFFLINE_FALLBACK` in the cache).
     */
    private suspend fun persistFallback(
        date: LocalDate,
        coordinates: Pair<Double, Double>,
        authority: SunniAuthority,
        schoolId: Int,
        cacheKey: String,
        times: Map<PrayerType, LocalTime>,
        deviceZone: String
    ) {
        val row = times.toEntityOrNull(cacheKey, date, authority, schoolId, deviceZone) ?: return
        runCatching { dao?.upsertAll(listOf(row)) }
    }

    private fun localCalculation(
        date: LocalDate,
        coordinates: Pair<Double, Double>,
        authority: SunniAuthority,
        madhhab: Madhhab,
        zoneId: ZoneId,
        adjustments: PrayerAdjustments
    ): Map<PrayerType, LocalTime> {
        val zone = runCatching { KTimeZone.of(zoneId.id) }.getOrElse { KTimeZone.currentSystemDefault() }
        val kDate = KLocalDate(date.year, date.monthValue, date.dayOfMonth)
        return engine.getPrayerTimes(
            coordinates = coordinates,
            date = kDate,
            method = SunniMethodMapper.localCalculationMethod(authority),
            madhhab = madhhab,
            timeZone = zone,
            adjustments = adjustments
        ).mapValues { it.value.toJavaLocalTime() }
    }

    private fun publish(
        row: PrayerTimetableEntity,
        source: PrayerTimeSource,
        authority: SunniAuthority
    ): ResolvedPrayerTimes {
        val times = row.toTimesOrNull()
            ?: return publishEmptyFallback(authority)
        remember(row.cacheKey, row.dateEpochDay, times)
        return ResolvedPrayerTimes(
            times = times,
            source = if (row.source == PrayerTimeSource.OFFLINE_FALLBACK.name) {
                PrayerTimeSource.OFFLINE_FALLBACK
            } else {
                source
            },
            authority = authority,
            fetchedAtEpochMillis = row.fetchedAt
        )
    }

    private fun publish(
        times: Map<PrayerType, LocalTime>,
        source: PrayerTimeSource,
        authority: SunniAuthority,
        fetchedAt: Long
    ): ResolvedPrayerTimes = ResolvedPrayerTimes(
        times = times,
        source = source,
        authority = authority,
        fetchedAtEpochMillis = fetchedAt
    )

    private fun publishEmptyFallback(authority: SunniAuthority) = ResolvedPrayerTimes(
        times = emptyMap(),
        source = PrayerTimeSource.OFFLINE_FALLBACK,
        authority = authority
    )

    private fun remember(cacheKey: String, epochDay: Long, times: Map<PrayerType, LocalTime>) {
        PrayerTimeSnapshot.record(snapshotKey(cacheKey, epochDay), times)
    }

    private fun snapshotKey(cacheKey: String, epochDay: Long): String = "$cacheKey#$epochDay"

    private fun PrayerTimetableEntity.matchesZone(deviceZone: String): Boolean =
        timeZone.isBlank() || timeZone == deviceZone

    private fun PrayerTimetableEntity.isFreshOnline(now: Long): Boolean =
        source == PrayerTimeSource.ONLINE.name && now - fetchedAt <= FRESH_WINDOW_MS

    private fun PrayerTimetableEntity.toTimesOrNull(): Map<PrayerType, LocalTime>? {
        val parsed = mapOf(
            PrayerType.FAJR to parseTime(fajr),
            PrayerType.SUNRISE to parseTime(sunrise),
            PrayerType.DHUHR to parseTime(dhuhr),
            PrayerType.ASR to parseTime(asr),
            PrayerType.MAGHRIB to parseTime(maghrib),
            PrayerType.ISHA to parseTime(isha)
        )
        if (parsed.values.any { it == null }) return null
        return parsed.mapValues { it.value!! }
    }

    private fun Map<PrayerType, LocalTime>.toEntityOrNull(
        cacheKey: String,
        date: LocalDate,
        authority: SunniAuthority,
        schoolId: Int,
        deviceZone: String
    ): PrayerTimetableEntity? {
        val fajr = this[PrayerType.FAJR] ?: return null
        val sunrise = this[PrayerType.SUNRISE] ?: return null
        val dhuhr = this[PrayerType.DHUHR] ?: return null
        val asr = this[PrayerType.ASR] ?: return null
        val maghrib = this[PrayerType.MAGHRIB] ?: return null
        val isha = this[PrayerType.ISHA] ?: return null
        return PrayerTimetableEntity(
            cacheKey = cacheKey,
            dateEpochDay = date.toEpochDay(),
            fajr = formatTime(fajr),
            sunrise = formatTime(sunrise),
            dhuhr = formatTime(dhuhr),
            asr = formatTime(asr),
            maghrib = formatTime(maghrib),
            isha = formatTime(isha),
            methodName = authority.displayName,
            methodId = authority.methodId,
            schoolId = schoolId,
            timeZone = deviceZone,
            source = PrayerTimeSource.OFFLINE_FALLBACK.name,
            fetchedAt = clock()
        )
    }
}
