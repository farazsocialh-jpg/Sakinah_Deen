package com.sakinah.deen.data.prayer

import com.sakinah.deen.data.model.PrayerType
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide memo of the timings that were already resolved from Room or AlAdhan.
 *
 * It exists so a component that cannot suspend — e.g. a `BroadcastReceiver` re-registering
 * the alarm table after a prayer fires — can schedule the *same* authoritative timings the
 * screen shows, instead of silently recomputing a slightly different astronomical table.
 * It is only a read-through cache: the durable copy lives in the `prayer_timetable` table,
 * and a miss always degrades to the local calculation.
 */
object PrayerTimeSnapshot {

    private val days = ConcurrentHashMap<String, Map<PrayerType, LocalTime>>()

    fun record(key: String, times: Map<PrayerType, LocalTime>) {
        if (times.isEmpty()) return
        days[key] = times.toMap()
    }

    fun timesFor(key: String): Map<PrayerType, LocalTime>? = days[key]

    fun clear() = days.clear()

    /**
     * Identity of a monthly timetable: rounded coordinates (~110 m), calculation method,
     * Asr school and month. Shared with the Room `cacheKey`, so any change to place or
     * juristic settings misses both the database and this memo instead of reusing another
     * city's timetable.
     */
    fun cacheKeyOf(
        month: LocalDate,
        coordinates: Pair<Double, Double>,
        methodId: Int,
        schoolId: Int
    ): String = "%.3f,%.3f|%d|%d|%d".format(
        coordinates.first,
        coordinates.second,
        methodId,
        schoolId,
        month.year * 100 + month.monthValue
    )

    /** Key of a single resolved day. */
    fun keyOf(
        date: LocalDate,
        coordinates: Pair<Double, Double>,
        methodId: Int,
        schoolId: Int
    ): String = "${cacheKeyOf(date, coordinates, methodId, schoolId)}#${date.toEpochDay()}"
}
