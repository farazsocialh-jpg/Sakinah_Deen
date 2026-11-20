package com.sakinah.deen.widget

import android.content.Context
import com.sakinah.deen.data.database.DatabasePassphraseProvider
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.data.database.dao.QuranDao
import com.sakinah.deen.data.database.dao.SalahDao
import com.sakinah.deen.data.database.entity.SalahRecordEntity
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.repository.PrayerTimeRepository
import com.sakinah.deen.domain.qibla.QiblaMath
import com.sakinah.deen.service.AzaanScheduling
import com.sakinah.deen.ui.theme.UserAppearanceConfig
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import com.sakinah.deen.data.prayer.SunniMethodMapper
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl
import kotlinx.datetime.toJavaLocalTime
import org.koin.core.context.GlobalContext
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

/** The five obligatory prayers in display order. */
val OBLIGATORY_PRAYERS: List<Pair<PrayerType, String>> = listOf(
    PrayerType.FAJR to "Fajr",
    PrayerType.DHUHR to "Dhuhr",
    PrayerType.ASR to "Asr",
    PrayerType.MAGHRIB to "Maghrib",
    PrayerType.ISHA to "Isha"
)

/** Safe Koin-backed access to the app's existing repositories and DAOs. */
internal object WidgetRepositories {
    val salahDao: SalahDao?
        get() = runCatching { GlobalContext.getOrNull()?.getOrNull<SalahDao>() }.getOrNull()

    val prayerTimes: PrayerTimeRepository?
        get() = runCatching { GlobalContext.getOrNull()?.getOrNull<PrayerTimeRepository>() }.getOrNull()

    val quranDao: QuranDao?
        get() = runCatching { GlobalContext.getOrNull()?.getOrNull<QuranDao>() }.getOrNull()
}

private fun database(context: Context): SakinahEncryptedDatabase? = runCatching {
    val appContext = context.applicationContext
    SakinahEncryptedDatabase.getInstance(
        appContext,
        DatabasePassphraseProvider.getPassphrase(appContext)
    )
}.getOrNull()

internal fun salahDao(context: Context): SalahDao? =
    WidgetRepositories.salahDao ?: database(context)?.salahDao()

internal fun quranDao(context: Context): QuranDao? =
    WidgetRepositories.quranDao ?: database(context)?.quranDao()

/** The saved location's zone, so a manually chosen city keeps its own times. */
internal fun activeZone(context: Context): ZoneId =
    runCatching { AzaanScheduling.activeZoneId(SakinahPreferences(context)) }
        .getOrDefault(ZoneId.systemDefault())

internal fun appPreferences(context: Context): SakinahPreferences? =
    runCatching { SakinahPreferences(context) }.getOrNull()

/** The user's saved app palette/font settings, used to tint widget accents. */
internal fun appAppearance(context: Context): UserAppearanceConfig? =
    runCatching { AppearancePreferences(context).config.value }.getOrNull()

internal fun cityLabel(context: Context): String =
    appPreferences(context)?.locationLabel?.takeIf { it.isNotBlank() } ?: "Your location"

/** Today's timings plus the zone they belong to. */
data class WidgetTimes(
    val zone: ZoneId,
    val date: LocalDate,
    val times: Map<PrayerType, LocalTime>
) {
    val hasTimes: Boolean get() = times.isNotEmpty()

    fun label(type: PrayerType): String = times[type]?.format(TIME_FORMATTER) ?: "--:--"

    /** First obligatory prayer still ahead today; null means Fajr has passed for today. */
    fun nextToday(now: LocalTime): PrayerType? =
        OBLIGATORY_PRAYERS.map { it.first }.firstOrNull { type ->
            times[type]?.isAfter(now) == true
        }
}

/** Small process memo so 19 widgets painting together resolve one timetable, not nineteen. */
private object WidgetTimesCache {
    private var cacheKey: String? = null
    private var cached: WidgetTimes? = null
    private var cachedAt: Long = 0L
    private const val TTL_MS = 60_000L

    @Synchronized
    fun get(key: String): WidgetTimes? =
        cached?.takeIf { cacheKey == key && System.currentTimeMillis() - cachedAt < TTL_MS }

    @Synchronized
    fun put(key: String, value: WidgetTimes) {
        cacheKey = key
        cached = value
        cachedAt = System.currentTimeMillis()
    }
}

/**
 * Resolves today's timings through the existing offline-first repository
 * (Room timetable cache -> AlAdhan -> local astronomy).
 */
suspend fun loadWidgetTimes(context: Context): WidgetTimes {
    val zone = activeZone(context)
    val today = LocalDate.now(zone)
    val preferences = appPreferences(context)
        ?: return WidgetTimes(zone, today, emptyMap())
    val coordinates = preferences.locationCoordinates
        ?: return WidgetTimes(zone, today, emptyMap())

    val key = "${today.toEpochDay()}|${coordinates.first}|${coordinates.second}|${zone.id}"
    WidgetTimesCache.get(key)?.let { return it }

    val repository = WidgetRepositories.prayerTimes
    val repoTimes = if (repository != null) {
        try {
            // A home-screen refresh must never hang on the network; the Room cache answers instantly.
            withTimeoutOrNull(8_000) {
                repository.resolveDay(
                    date = today,
                    coordinates = coordinates,
                    madhhab = preferences.madhhab,
                    zoneId = zone,
                    highLatitudeRule = preferences.highLatitudeRule
                ).times
            }.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }
    } else {
        emptyMap()
    }

    val finalTimes = if (repoTimes.isNotEmpty()) {
        repoTimes
    } else {
        // Fallback: calculate using local astronomy engine so widget never shows blank/empty
        runCatching {
            val authority = SunniMethodMapper.resolve(
                countryCode = preferences.locationCountryCode,
                override = preferences.calculationMethodOverride
            )
            val method = preferences.calculationMethodOverride
                ?: SunniMethodMapper.localCalculationMethod(authority)
            val kZone = runCatching { kotlinx.datetime.TimeZone.of(zone.id) }
                .getOrElse { kotlinx.datetime.TimeZone.currentSystemDefault() }
            val kDate = kotlinx.datetime.LocalDate.parse(today.toString())
            PrayerCalculationEngineImpl().getPrayerTimes(
                coordinates = coordinates,
                date = kDate,
                method = method,
                madhhab = preferences.madhhab,
                timeZone = kZone,
                adjustments = preferences.prayerAdjustments,
                highLatitudeRule = preferences.highLatitudeRule
            ).mapValues { it.value.toJavaLocalTime() }
        }.getOrDefault(emptyMap())
    }

    val resolved = WidgetTimes(zone, today, finalTimes)
    if (finalTimes.isNotEmpty()) WidgetTimesCache.put(key, resolved)
    return resolved
}

/** Prayers already marked complete for [epochDay]. */
suspend fun loadCompletedPrayers(context: Context, epochDay: Long): Set<String> {
    val dao = salahDao(context) ?: return emptySet()
    return runCatching {
        dao.observeTodaySalah(epochDay).first()
            .filter { it.isCompleted }
            .map { it.prayerType }
            .toSet()
    }.getOrDefault(emptySet())
}

/** Pure toggle plan, so the Room write rules are unit-testable without a database. */
data class PrayerTogglePlan(
    val insert: Boolean,
    val nextCompleted: Boolean
)

fun planPrayerToggle(existingCompleted: Boolean?): PrayerTogglePlan =
    if (existingCompleted == null) {
        PrayerTogglePlan(insert = true, nextCompleted = true)
    } else {
        PrayerTogglePlan(insert = false, nextCompleted = !existingCompleted)
    }

/** Marks the prayer prayed, or clears it again when it already was. */
suspend fun togglePrayerForWidget(context: Context, prayerKey: String) {
    val dao = salahDao(context) ?: return
    val epochDay = LocalDate.now(activeZone(context)).toEpochDay()
    val existing = dao.getSalahRecord(epochDay, prayerKey)
    val plan = planPrayerToggle(existing?.isCompleted)

    if (plan.insert || existing == null) {
        dao.upsertSalahRecord(
            SalahRecordEntity(
                dateEpochDay = epochDay,
                prayerType = prayerKey,
                isCompleted = true,
                isJamaah = false,
                isQadha = false,
                completedTimestamp = System.currentTimeMillis()
            )
        )
    } else {
        dao.updateSalahRecord(
            existing.copy(
                isCompleted = plan.nextCompleted,
                completedTimestamp = if (plan.nextCompleted) System.currentTimeMillis() else null
            )
        )
    }
}

fun prayerLabel(type: PrayerType): String =
    OBLIGATORY_PRAYERS.firstOrNull { it.first == type }?.second
        ?: type.name.lowercase().replaceFirstChar { it.uppercase() }

internal fun PrayerType.toPrayerWindow(): PrayerWindow = when (this) {
    PrayerType.FAJR, PrayerType.SUNRISE -> PrayerWindow.FAJR
    PrayerType.DHUHR -> PrayerWindow.DHUHR
    PrayerType.ASR -> PrayerWindow.ASR
    PrayerType.MAGHRIB -> PrayerWindow.MAGHRIB
    PrayerType.ISHA -> PrayerWindow.ISHA
}

/** The prayer whose window is currently active at [now] (last prayer that has begun). */
fun currentPrayer(times: WidgetTimes, now: LocalTime): PrayerType? {
    if (!times.hasTimes) return null
    return when (currentPrayerWindow(times.times, now)) {
        PrayerWindow.FAJR -> PrayerType.FAJR
        PrayerWindow.DHUHR -> PrayerType.DHUHR
        PrayerWindow.ASR -> PrayerType.ASR
        PrayerWindow.MAGHRIB -> PrayerType.MAGHRIB
        PrayerWindow.ISHA -> PrayerType.ISHA
    }
}

/** The window the sky should currently show: the last prayer that has begun. */
fun currentPrayerWindow(times: Map<PrayerType, LocalTime>, now: LocalTime): PrayerWindow {
    var window = PrayerWindow.ISHA // before Fajr the sky is still the night sky
    OBLIGATORY_PRAYERS.forEach { (type, _) ->
        val start = times[type] ?: return@forEach
        if (!now.isBefore(start)) window = type.toPrayerWindow()
    }
    return window
}

/** Whole minutes until [target], wrapping to tomorrow when it has already passed. */
fun minutesUntil(now: LocalTime, target: LocalTime): Long {
    val seconds = (target.toSecondOfDay() - now.toSecondOfDay() + 86_400) % 86_400
    return (seconds / 60).toLong()
}

/**
 * How far the current prayer window has run, from the prayer that began last to the one
 * that comes next. Drives the progress bar on the compact Salah widget.
 */
fun prayerProgress(times: Map<PrayerType, LocalTime>, now: LocalTime): Float {
    val starts = OBLIGATORY_PRAYERS.mapNotNull { times[it.first] }.sorted()
    if (starts.size < 2) return 0f
    var start = starts.last()
    var end = starts.first()
    starts.forEachIndexed { index, candidate ->
        if (!now.isBefore(candidate)) {
            start = candidate
            end = starts.getOrElse(index + 1) { starts.first() }
        }
    }
    val total = ((end.toSecondOfDay() - start.toSecondOfDay()) + 86_400) % 86_400
    if (total == 0) return 0f
    val elapsed = ((now.toSecondOfDay() - start.toSecondOfDay()) + 86_400) % 86_400
    return (elapsed.toFloat() / total).coerceIn(0f, 1f)
}

fun countdownLabel(minutes: Long): String {
    val safe = minutes.coerceAtLeast(0)
    val hours = safe / 60
    val mins = safe % 60
    return when {
        hours > 0 -> "${hours}h ${mins}m left"
        mins > 0 -> "${mins}m left"
        else -> "now"
    }
}

fun durationLabel(minutes: Long): String {
    val safe = minutes.coerceAtLeast(0)
    val hours = safe / 60
    val mins = safe % 60
    return when {
        hours > 0 -> "${hours}h ${mins}m"
        else -> "${mins}m"
    }
}

// ---------------------------------------------------------------------------
// Hijri calendar, Sunnah fasts and Islamic milestones
// ---------------------------------------------------------------------------

private val HIJRI_MONTHS = listOf(
    "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
    "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha'ban",
    "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
)

private val HIJRI_SHORT_MONTHS = listOf(
    "Muharram", "Safar", "Rabi I", "Rabi II", "Jumada I", "Jumada II",
    "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
)

data class HijriDateInfo(
    val day: Int,
    val month: Int,
    val year: Int,
    val monthName: String
) {
    val label: String get() = "$day $monthName $year AH"

    /** "Jumada I" — the compact form the widgets set in caps. */
    val shortMonth: String get() = HIJRI_SHORT_MONTHS.getOrElse(month - 1) { monthName }

    /** "18 JUMADA I 1448" for the 2x1 tile. */
    val compactLabel: String get() = "$day ${shortMonth.uppercase(Locale.ENGLISH)} $year"

    /** "JUMADA I 1448" under the big day number. */
    val monthYearLabel: String get() = "${shortMonth.uppercase(Locale.ENGLISH)} $year"
}

/** Hijri date for a Gregorian day, shifted by the user's moon-sighting offset. */
fun hijriFor(date: LocalDate, offsetDays: Int = 0): HijriDateInfo {
    val hijri = runCatching {
        HijrahDate.from(date).plus(offsetDays.toLong(), ChronoUnit.DAYS)
    }.getOrElse { HijrahDate.from(date) }
    val day = hijri.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
    val month = hijri.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)
    val year = hijri.get(java.time.temporal.ChronoField.YEAR_OF_ERA)
    return HijriDateInfo(
        day = day,
        month = month,
        year = year,
        monthName = HIJRI_MONTHS.getOrElse(month - 1) { "Hijri" }
    )
}

private fun gregorianForHijri(year: Int, month: Int, day: Int, offsetDays: Int): LocalDate =
    LocalDate.from(HijrahDate.of(year, month, day)).minusDays(offsetDays.toLong())

data class SunnahFastInfo(
    val title: String,
    val hijriLabel: String,
    val daysAway: Int
)

/** Next Monday/Thursday or white day (13-15 Hijri), searching two weeks ahead. */
fun nextSunnahFast(today: LocalDate, offsetDays: Int = 0): SunnahFastInfo {
    for (offset in 0..14) {
        val date = today.plusDays(offset.toLong())
        val hijri = hijriFor(date, offsetDays)
        val whiteDay = hijri.day in 13..15
        val weekdayFast = date.dayOfWeek == DayOfWeek.MONDAY || date.dayOfWeek == DayOfWeek.THURSDAY
        if (whiteDay || weekdayFast) {
            val title = when {
                whiteDay && weekdayFast -> "White Day + ${weekdayName(date)}"
                whiteDay -> "White Day ${hijri.day}"
                else -> weekdayName(date)
            }
            return SunnahFastInfo(title = title, hijriLabel = hijri.label, daysAway = offset)
        }
    }
    return SunnahFastInfo(title = "Monday", hijriLabel = hijriFor(today, offsetDays).label, daysAway = 0)
}

data class IslamicEventInfo(
    val name: String,
    val hijriLabel: String,
    val daysAway: Int,
    val date: LocalDate
)

private val ISLAMIC_MILESTONES = listOf(
    Triple(9, 1, "Ramadan begins"),
    Triple(9, 27, "Laylatul Qadr"),
    Triple(10, 1, "Eid al-Fitr"),
    Triple(12, 9, "Day of Arafah"),
    Triple(12, 10, "Eid al-Adha"),
    Triple(1, 10, "Ashura")
)

/** Nearest upcoming milestone, never negative: today reports 0 days. */
fun nextIslamicEvent(today: LocalDate, offsetDays: Int = 0): IslamicEventInfo {
    val hijriYear = hijriFor(today, offsetDays).year
    var best: IslamicEventInfo? = null
    for (year in listOf(hijriYear, hijriYear + 1)) {
        ISLAMIC_MILESTONES.forEach { (month, day, name) ->
            val date = gregorianForHijri(year, month, day, offsetDays)
            val days = ChronoUnit.DAYS.between(today, date).toInt()
            val current = best
            if (days >= 0 && (current == null || days < current.daysAway)) {
                best = IslamicEventInfo(
                    name = name,
                    hijriLabel = "$day ${HIJRI_MONTHS[month - 1]} $year AH",
                    daysAway = days,
                    date = date
                )
            }
        }
    }
    return best ?: IslamicEventInfo(
        name = "Ramadan begins",
        hijriLabel = hijriFor(today, offsetDays).label,
        daysAway = 0,
        date = today
    )
}

/** The last third of the night: two thirds after Maghrib, ending at Fajr. */
fun lastThirdStart(maghrib: LocalTime, fajr: LocalTime): LocalTime {
    val nightSeconds = ((fajr.toSecondOfDay() - maghrib.toSecondOfDay()) + 86_400) % 86_400
    val start = (maghrib.toSecondOfDay() + (nightSeconds * 2) / 3) % 86_400
    return LocalTime.ofSecondOfDay(start.toLong())
}

data class DuhaStatus(val isOpen: Boolean, val label: String)

/** Duha runs from sunrise until Dhuhr. */
fun duhaStatus(now: LocalTime, sunrise: LocalTime, dhuhr: LocalTime): DuhaStatus = when {
    now.isBefore(sunrise) -> DuhaStatus(false, "Starts at ${sunrise.format(TIME_FORMATTER)}")
    now.isBefore(dhuhr) -> DuhaStatus(true, "Open until ${dhuhr.format(TIME_FORMATTER)}")
    else -> DuhaStatus(false, "Opens at sunrise tomorrow")
}

// ---------------------------------------------------------------------------
// Qur'an reading streak
// ---------------------------------------------------------------------------

/** Consecutive reading days ending today (or yesterday, before today's reading). */
fun streakFromDays(days: Set<Long>, today: Long): Int =
    com.sakinah.deen.data.prefs.QuranReaderPreferences.streakOf(days, today)

// ---------------------------------------------------------------------------
// Curated rotating Ayat
// ---------------------------------------------------------------------------

/**
 * A hand-picked set of short, uplifting verses. The list ships with the app so the widget
 * can rotate offline; the Arabic script is filled in from the bundled Qur'an database
 * whenever that row is available.
 */
data class DailyAyah(
    val reference: String,
    val surahName: String,
    val translation: String,
    val arabic: String = ""
) {
    /** "Al-Baqarah · 2:152" for the taller layouts. */
    fun surahLabel(): String = if (surahName.isBlank()) reference else "$surahName · $reference"

    /** A one-line teaser that never cuts a word in half. */
    fun snippet(maxChars: Int = 74): String {
        if (translation.length <= maxChars) return translation
        val cut = translation.take(maxChars).substringBeforeLast(' ')
        return "$cut…"
    }
}

private val CURATED_AYAHS: List<DailyAyah> = listOf(
    DailyAyah(
        reference = "2:152",
        surahName = "Al-Baqarah",
        translation = "So remember Me; I will remember you. And be grateful to Me and do not deny Me."
    ),
    DailyAyah(
        reference = "94:5",
        surahName = "Ash-Sharh",
        translation = "For indeed, with hardship comes ease."
    ),
    DailyAyah(
        reference = "65:3",
        surahName = "At-Talaq",
        translation = "And whoever relies upon Allah, then He is sufficient for him."
    ),
    DailyAyah(
        reference = "13:28",
        surahName = "Ar-Ra'd",
        translation = "Verily, in the remembrance of Allah do hearts find rest."
    ),
    DailyAyah(
        reference = "3:139",
        surahName = "Ali 'Imran",
        translation = "And do not weaken and do not grieve, and you will be superior if you are true believers."
    ),
    DailyAyah(
        reference = "39:53",
        surahName = "Az-Zumar",
        translation = "Do not despair of the mercy of Allah. Indeed, Allah forgives all sins."
    ),
    DailyAyah(
        reference = "2:186",
        surahName = "Al-Baqarah",
        translation = "And when My servants ask you concerning Me, indeed I am near."
    ),
    DailyAyah(
        reference = "55:13",
        surahName = "Ar-Rahman",
        translation = "So which of the favors of your Lord would you deny?"
    ),
    DailyAyah(
        reference = "2:286",
        surahName = "Al-Baqarah",
        translation = "Allah does not burden a soul beyond that it can bear."
    ),
    DailyAyah(
        reference = "67:2",
        surahName = "Al-Mulk",
        translation = "He who created death and life to test you as to which of you is best in deed."
    ),
    DailyAyah(
        reference = "49:13",
        surahName = "Al-Hujurat",
        translation = "Indeed, the most noble of you in the sight of Allah is the most righteous of you."
    ),
    DailyAyah(
        reference = "16:97",
        surahName = "An-Nahl",
        translation = "Whoever does righteousness, whether male or female, while a believer, We will surely give them a good life."
    ),
    DailyAyah(
        reference = "8:46",
        surahName = "Al-Anfal",
        translation = "And be patient. Indeed, Allah is with the patient."
    ),
    DailyAyah(
        reference = "29:69",
        surahName = "Al-'Ankabut",
        translation = "And those who strive for Us, We will surely guide them to Our ways."
    ),
    DailyAyah(
        reference = "40:60",
        surahName = "Ghafir",
        translation = "Call upon Me; I will respond to you."
    ),
    DailyAyah(
        reference = "25:63",
        surahName = "Al-Furqan",
        translation = "And the servants of the Most Merciful are those who walk upon the earth easily."
    ),
    DailyAyah(
        reference = "57:4",
        surahName = "Al-Hadid",
        translation = "And He is with you wherever you are. And Allah, of what you do, is Seeing."
    ),
    DailyAyah(
        reference = "6:162",
        surahName = "Al-An'am",
        translation = "Say, indeed my prayer, my rites of sacrifice, my living and my dying are for Allah."
    ),
)

/** How many verses the rotation cycles through. */
val curatedAyahCount: Int get() = CURATED_AYAHS.size

/** Pure rotation rule: the index wraps in both directions. */
fun curatedAyahFor(index: Int): DailyAyah =
    CURATED_AYAHS[Math.floorMod(index, CURATED_AYAHS.size)]

/**
 * The verse at [index] in the curated rotation, with its Uthmani text when the bundled
 * Qur'an database has been seeded. The translation always comes from the curated list.
 */
suspend fun loadDailyAyah(context: Context, index: Int): DailyAyah {
    val curated = curatedAyahFor(index)
    val parts = curated.reference.split(':')
    val surah = parts.firstOrNull()?.toIntOrNull() ?: return curated
    val ayah = parts.getOrNull(1)?.toIntOrNull() ?: return curated
    val dao = quranDao(context) ?: return curated
    val row = runCatching { dao.getAyah(surah, ayah) }.getOrNull() ?: return curated
    return curated.copy(arabic = row.textUthmani)
}

// ---------------------------------------------------------------------------
// Qibla
// ---------------------------------------------------------------------------

data class QiblaSummary(
    val bearing: Int,
    val cardinal: String,
    val city: String
)

fun qiblaSummary(
    latitude: Double,
    longitude: Double,
    city: String
): QiblaSummary {
    val bearing = QiblaMath.qiblaBearingDegrees(latitude, longitude)
    return QiblaSummary(
        bearing = bearing.toInt().coerceIn(0, 360),
        cardinal = QiblaMath.cardinalDirection(bearing),
        city = city
    )
}

/** Whole days between two local dates, clamped at zero. */
fun daysUntil(today: LocalDate, target: LocalDate): Int =
    Duration.between(today.atStartOfDay(), target.atStartOfDay()).toDays().toInt().coerceAtLeast(0)
