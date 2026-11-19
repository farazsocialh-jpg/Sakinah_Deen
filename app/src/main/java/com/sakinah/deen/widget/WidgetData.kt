package com.sakinah.deen.widget

import android.content.Context
import com.sakinah.deen.data.database.DatabasePassphraseProvider
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.data.database.dao.AdhkarDao
import com.sakinah.deen.data.database.dao.HadithDao
import com.sakinah.deen.data.database.dao.QuranDao
import com.sakinah.deen.data.database.dao.SalahDao
import com.sakinah.deen.data.database.entity.SalahRecordEntity
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.repository.NisabRateRepository
import com.sakinah.deen.data.repository.NisabRates
import com.sakinah.deen.data.repository.PrayerTimeRepository
import com.sakinah.deen.domain.qibla.QiblaMath
import com.sakinah.deen.service.AzaanScheduling
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

/** The five obligatory prayers in display order. */
val OBLIGATORY_PRAYERS: List<Pair<PrayerType, String>> = listOf(
    PrayerType.FAJR to "Fajr",
    PrayerType.DHUHR to "Dhuhr",
    PrayerType.ASR to "Asr",
    PrayerType.MAGHRIB to "Maghrib",
    PrayerType.ISHA to "Isha"
)

/** Koin-backed access to the app's existing repositories and DAOs. */
internal object WidgetRepositories : KoinComponent {
    val salahDao: SalahDao by inject()
    val prayerTimes: PrayerTimeRepository by inject()
    val quranDao: QuranDao by inject()
    val hadithDao: HadithDao by inject()
    val adhkarDao: AdhkarDao by inject()
    val nisabRates: NisabRateRepository by inject()
}

private fun database(context: Context): SakinahEncryptedDatabase? = runCatching {
    val appContext = context.applicationContext
    SakinahEncryptedDatabase.getInstance(
        appContext,
        DatabasePassphraseProvider.getPassphrase(appContext)
    )
}.getOrNull()

internal fun salahDao(context: Context): SalahDao? =
    runCatching { WidgetRepositories.salahDao }.getOrNull() ?: database(context)?.salahDao()

internal fun quranDao(context: Context): QuranDao? =
    runCatching { WidgetRepositories.quranDao }.getOrNull() ?: database(context)?.quranDao()

internal fun hadithDao(context: Context): HadithDao? =
    runCatching { WidgetRepositories.hadithDao }.getOrNull() ?: database(context)?.hadithDao()

internal fun adhkarDao(context: Context): AdhkarDao? =
    runCatching { WidgetRepositories.adhkarDao }.getOrNull() ?: database(context)?.adhkarDao()

internal fun nisabRepository(): NisabRateRepository? =
    runCatching { WidgetRepositories.nisabRates }.getOrNull()

/** The saved location's zone, so a manually chosen city keeps its own times. */
internal fun activeZone(context: Context): ZoneId =
    runCatching { AzaanScheduling.activeZoneId(SakinahPreferences(context)) }
        .getOrDefault(ZoneId.systemDefault())

internal fun appPreferences(context: Context): SakinahPreferences? =
    runCatching { SakinahPreferences(context) }.getOrNull()

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

    val repository = runCatching { WidgetRepositories.prayerTimes }.getOrNull()
        ?: return WidgetTimes(zone, today, emptyMap())

    val times = try {
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

    val resolved = WidgetTimes(zone, today, times)
    if (times.isNotEmpty()) WidgetTimesCache.put(key, resolved)
    return resolved
}

/** Adds tomorrow's Fajr so the night-time windows (Qiyam, Isha) stay correct. */
suspend fun loadWidgetTimesWithTomorrowFajr(context: Context): WidgetTimes {
    val today = loadWidgetTimes(context)
    if (!today.hasTimes) return today
    val tomorrow = try {
        val preferences = appPreferences(context) ?: return today
        val coordinates = preferences.locationCoordinates ?: return today
        val repository = runCatching { WidgetRepositories.prayerTimes }.getOrNull() ?: return today
        withTimeoutOrNull(5_000) {
            repository.resolveDay(
                date = today.date.plusDays(1),
                coordinates = coordinates,
                madhhab = preferences.madhhab,
                zoneId = today.zone,
                highLatitudeRule = preferences.highLatitudeRule
            ).times
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    } ?: return today

    val fajr = tomorrow[PrayerType.FAJR] ?: return today
    return today.copy(times = today.times + (PrayerType.FAJR to fajr))
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

data class HijriDateInfo(
    val day: Int,
    val month: Int,
    val year: Int,
    val monthName: String
) {
    val label: String get() = "$day $monthName $year AH"
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
// Nisab
// ---------------------------------------------------------------------------

data class NisabStatus(
    val threshold: Double,
    val assets: Double,
    val currency: String,
    val aboveNisab: Boolean,
    val zakatDue: Double
) {
    val shortfall: Double get() = (threshold - assets).coerceAtLeast(0.0)
}

/** Silver nisab (595 g) is the lower, safer threshold for most households. */
fun nisabStatus(preferences: SakinahPreferences, rates: NisabRates): NisabStatus {
    val silverThreshold = rates.silverPricePerGram.toDouble() * 595.0
    val goldThreshold = rates.goldPricePerGram.toDouble() * 85.0
    val threshold = if (silverThreshold > 0 && goldThreshold > 0) {
        minOf(silverThreshold, goldThreshold)
    } else {
        maxOf(silverThreshold, goldThreshold)
    }
    val assets = (
        preferences.zakatCashInBank +
            preferences.zakatGoldHeld +
            preferences.zakatSilverHeld +
            preferences.zakatTradeInventory +
            preferences.zakatStocksValue -
            preferences.zakatShortTermDebts
        ).toDouble().coerceAtLeast(0.0)
    return NisabStatus(
        threshold = threshold,
        assets = assets,
        currency = preferences.zakatCurrency,
        aboveNisab = threshold > 0 && assets >= threshold,
        zakatDue = if (threshold > 0 && assets >= threshold) assets * 0.025 else 0.0
    )
}

// ---------------------------------------------------------------------------
// Daily content (Qur'an, Hadith, Du'a)
// ---------------------------------------------------------------------------

data class DailyAyah(
    val reference: String,
    val arabic: String,
    val translation: String
)

private val DAILY_AYAH_PICKS = listOf(
    2 to 152, 94 to 5, 65 to 3, 13 to 28, 3 to 139, 39 to 53, 2 to 286, 55 to 13
)

private val FALLBACK_AYAH = DailyAyah(
    reference = "2:152",
    arabic = "فَاذْكُرُونِي أَذْكُرْكُمْ وَاشْكُرُوا لِي وَلَا تَكْفُرُونِ",
    translation = "So remember Me; I will remember you. And be grateful to Me and do not deny Me."
)

suspend fun loadDailyAyah(context: Context): DailyAyah {
    val (surah, ayah) = DAILY_AYAH_PICKS[
        (LocalDate.now(activeZone(context)).toEpochDay() % DAILY_AYAH_PICKS.size).toInt()
    ]
    val dao = quranDao(context) ?: return FALLBACK_AYAH
    val row = runCatching { dao.getAyah(surah, ayah) }.getOrNull() ?: return FALLBACK_AYAH
    return DailyAyah(
        reference = "$surah:$ayah",
        arabic = row.textUthmani,
        translation = row.translation ?: FALLBACK_AYAH.translation
    )
}

data class DailyHadith(
    val text: String,
    val source: String
)

private val FALLBACK_HADITH = DailyHadith(
    text = "Actions are judged by intentions, and every person will have what they intended.",
    source = "Sahih al-Bukhari 1"
)

private val HADITH_SOURCES = listOf("bukhari", "muslim", "nawawi")

suspend fun loadDailyHadith(context: Context): DailyHadith {
    val dao = hadithDao(context) ?: return FALLBACK_HADITH
    val day = LocalDate.now(activeZone(context)).toEpochDay()
    val book = HADITH_SOURCES[(day % HADITH_SOURCES.size).toInt()]
    val number = (day % 40).toInt() + 1
    val row = runCatching { dao.getHadith(bookKey = book, languageCode = "eng", hadithNumber = number) }
        .getOrNull()
        ?: runCatching { dao.getHadith(bookKey = "nawawi", languageCode = "eng", hadithNumber = number) }
            .getOrNull()
        ?: return FALLBACK_HADITH
    return DailyHadith(
        text = row.englishText.ifBlank { FALLBACK_HADITH.text },
        source = "${row.bookTitle.ifBlank { book }} ${row.hadithNumber}"
    )
}

data class DailyDua(
    val title: String,
    val translation: String,
    val reference: String
)

private val FALLBACK_DUA = DailyDua(
    title = "Ayat al-Kursi",
    translation = "Whoever recites it in the morning is protected until evening.",
    reference = "Sahih Muslim 810"
)

suspend fun loadDailyDua(context: Context): DailyDua {
    val dao = adhkarDao(context) ?: return FALLBACK_DUA
    val all = runCatching { dao.observeAll().first() }.getOrDefault(emptyList())
    if (all.isEmpty()) return FALLBACK_DUA
    val day = LocalDate.now(activeZone(context)).toEpochDay()
    val row = all[(day % all.size).toInt()]
    return DailyDua(
        title = row.title,
        translation = row.translation,
        reference = row.reference
    )
}

/** Number of bundled adhkar in the morning/evening collection. */
suspend fun adhkarItemCount(context: Context): Int {
    val dao = adhkarDao(context) ?: return 12
    return runCatching { dao.countForCategory(ADHKAR_CATEGORY) }.getOrDefault(0)
        .takeIf { it > 0 } ?: 12
}

internal const val ADHKAR_CATEGORY = "morning_evening"

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
