package com.sakinah.deen.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.sakinah.deen.data.model.AzaanSettings
import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.Muezzin
import com.sakinah.deen.data.model.NaflType
import com.sakinah.deen.data.model.PrayerAlertMode
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.model.QuranArabicScript
import com.sakinah.deen.data.model.QuranTranslationResource
import com.sakinah.deen.network.IslamicDataRepository
import com.sakinah.deen.data.model.PrayerAdjustments
import com.sakinah.deen.domain.astronomy.LocationValidator
import com.sakinah.deen.domain.travel.HomeAnchorStore
import com.sakinah.deen.domain.travel.SafarPolicy
import com.sakinah.deen.settings.HighLatitudeRule

/**
 * Single source of truth for values that background components (alarms, receivers,
 * services) need while no Activity or ViewModel exists.
 *
 * The [SharedPreferences] constructor is internal so the preference rules ("a saved Home
 * clears Travel Mode", "(0,0) is not a Home") can be unit tested without Android.
 */
class SakinahPreferences internal constructor(
    private val prefs: SharedPreferences,
    /** Encrypted store for coordinates and Zakat balances; falls back to [prefs] in tests. */
    private val securePrefs: SharedPreferences = prefs
) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        SensitivePreferences.create(context.applicationContext)
            ?: context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    init {
        migrateSensitiveValuesIfNeeded()
        // Existing installs may have inherited a London default Home; drop it once.
        migrateLondonHomeIfNeeded()
    }

    /** Non-null when encrypted storage could not be opened; surfaced in Settings. */
    val storageSecurityWarning: String?
        get() = SensitivePreferences.error.value

    private fun secureEdit(): SharedPreferences.Editor = securePrefs.edit()

    companion object {
        /**
         * Legacy pre-onboarding defaults. A new install has **no** location: the Salah
         * screen asks for one (GPS or a city) instead of quietly running London times for
         * everyone in the world. These values survive only so the one-time cleanup below can
         * recognise and drop them.
         */
        private const val LEGACY_DEFAULT_LATITUDE = 51.5074
        private const val LEGACY_DEFAULT_LONGITUDE = -0.1278
        private const val LEGACY_DEFAULT_LOCATION_LABEL = "London, UK"

        private const val PREFS_NAME = "sakinah_preferences"
        private const val KEY_LATITUDE = "location_latitude"
        private const val KEY_LONGITUDE = "location_longitude"
        private const val KEY_LOCATION_LABEL = "location_label"
        private const val KEY_LOCATION_COUNTRY_CODE = "location_country_code"
        private const val KEY_LOCATION_TIMEZONE_ID = "location_timezone_id"
        private const val KEY_IS_MANUAL_LOCATION = "is_manual_location"
        private const val KEY_CALCULATION_METHOD_OVERRIDE = "calculation_method_override"
        private const val KEY_HOME_LATITUDE = "home_latitude"
        private const val KEY_HOME_LONGITUDE = "home_longitude"
        private const val KEY_HOME_CITY = "home_city"
        private const val KEY_HOME_EXPLICITLY_SET = "home_explicitly_set"
        private const val KEY_CALCULATION_METHOD = "calculation_method"
        private const val KEY_MADHHAB = "madhhab"
        private const val KEY_CAR_ADDRESS = "car_bluetooth_address"
        private const val KEY_CAR_NAME = "car_bluetooth_name"
        private const val KEY_TRAVEL_MODE = "travel_mode_active"
        private const val KEY_QASR_ACTIVE = "qasr_active"
        private const val KEY_LAST_DISTANCE_KM = "last_distance_from_home_km"
        private const val KEY_ADHAN_SOUND_URI = "adhan_sound_uri"
        private const val KEY_DUA_SAFAR_SOUND_URI = "dua_safar_sound_uri"
        private const val KEY_QURAN_TRANSLATION_ID = "quran_translation_id"
        private const val KEY_QURAN_TRANSLATION_LANGUAGE = "quran_translation_language"
        private const val KEY_QURAN_TRANSLATION_EDITION_KEY = "quran_translation_edition_key"
        private const val KEY_QURAN_TRANSLATION_SOURCE = "quran_translation_source"
        private const val KEY_QURAN_ARABIC_SCRIPT = "quran_arabic_script"
        private const val KEY_HADITH_LANGUAGE = "hadith_language_code"
        private const val KEY_BOOKMARKED_HADITH_IDS = "bookmarked_hadith_ids"
        private const val KEY_HADITH_BOOKMARKS_INITIALIZED = "hadith_bookmarks_initialized"
        private const val KEY_ALARMS_ENABLED = "alarms_enabled"
        private const val KEY_LAST_QADHA_ROLLOVER = "last_qadha_rollover_epoch_day"
        private const val KEY_FIRST_LAUNCH_PERMISSIONS_REQUESTED = "first_launch_permissions_requested"
                 private const val KEY_ZAKAT_CURRENCY = "zakat_currency"
         private const val KEY_ZAKAT_CASH = "zakat_cash_in_bank"
        private const val KEY_ZAKAT_GOLD = "zakat_gold_held"
        private const val KEY_ZAKAT_SILVER = "zakat_silver_held"
        private const val KEY_ZAKAT_INVENTORY = "zakat_trade_inventory"
        private const val KEY_ZAKAT_STOCKS = "zakat_stocks_value"
        private const val KEY_ZAKAT_DEBTS = "zakat_short_term_debts"
        private const val KEY_HIFZ_ANNOTATIONS = "hifz_verse_annotations"
        private const val KEY_MUEZZIN = "azaan_muezzin"
        private const val KEY_FAJR_SPECIAL_ADHAN = "azaan_fajr_special"
        private const val KEY_ALERT_MODE_PREFIX = "azaan_mode_"
        private const val KEY_ALERT_OFFSET_PREFIX = "azaan_offset_"
        private const val KEY_CRITICAL_FAJR_CHANNEL = "azaan_critical_fajr_channel"
        private const val KEY_NEXT_ALARM_AT = "azaan_next_alarm_at"
        private const val KEY_NEXT_ALARM_PRAYER = "azaan_next_alarm_prayer"
        private const val KEY_LAST_ALARM_RESULT = "azaan_last_schedule_result"
        private const val KEY_ADHKAR_SEED_HASH = "adhkar_seed_hash"
        private const val KEY_ADJUST_FAJR = "prayer_adjust_fajr"
        private const val KEY_ADJUST_SUNRISE = "prayer_adjust_sunrise"
        private const val KEY_ADJUST_DHUHR = "prayer_adjust_dhuhr"
        private const val KEY_ADJUST_ASR = "prayer_adjust_asr"
        private const val KEY_ADJUST_MAGHRIB = "prayer_adjust_maghrib"
        private const val KEY_ADJUST_ISHA = "prayer_adjust_isha"
        private const val KEY_HIGH_LATITUDE_RULE = "prayer_high_latitude_rule"
        private const val KEY_LOCATION_TIMESTAMP = "location_timestamp_millis"
        private const val KEY_LOCATION_SOURCE = "location_source"
        private const val KEY_NAFL_COMPLETIONS = "nafl_completions"
        private const val KEY_SAFAR_METHODOLOGY = "safar_methodology"
        private const val KEY_SAFAR_MAX_STAY_DAYS = "safar_max_stay_days"
        private const val KEY_SAFAR_JAM_PERMITTED = "safar_jam_permitted"
        private const val KEY_SAFAR_TRAVEL_START = "safar_travel_start_epoch_day"

        /** A saved location older than this is reported as stale in the UI. */
        const val CACHED_LOCATION_MAX_AGE_MS: Long = 6L * 60 * 60 * 1000

        const val LOCATION_SOURCE_GPS = "gps"
        const val LOCATION_SOURCE_MANUAL = "manual"

        /** Values that are written to the encrypted store instead of plain SharedPreferences. */
        private val SENSITIVE_KEYS = listOf(
            KEY_LATITUDE,
            KEY_LONGITUDE,
            KEY_LOCATION_LABEL,
            KEY_LOCATION_COUNTRY_CODE,
            KEY_LOCATION_TIMEZONE_ID,
            KEY_IS_MANUAL_LOCATION,
            KEY_HOME_LATITUDE,
            KEY_HOME_LONGITUDE,
            KEY_HOME_CITY,
            KEY_HOME_EXPLICITLY_SET,
            KEY_ZAKAT_CURRENCY,
            KEY_ZAKAT_CASH,
            KEY_ZAKAT_GOLD,
            KEY_ZAKAT_SILVER,
            KEY_ZAKAT_INVENTORY,
            KEY_ZAKAT_STOCKS,
            KEY_ZAKAT_DEBTS
        )
    }

    /** Double is stored as raw bits so stored coordinates do not lose precision. */
    var locationLatitude: Double?
        get() = if (securePrefs.contains(KEY_LATITUDE)) Double.fromBits(securePrefs.getLong(KEY_LATITUDE, 0L)) else null
        set(value) = secureEdit().apply {
            if (value == null) remove(KEY_LATITUDE) else putLong(KEY_LATITUDE, value.toBits())
        }.apply()

    var locationLongitude: Double?
        get() = if (securePrefs.contains(KEY_LONGITUDE)) Double.fromBits(securePrefs.getLong(KEY_LONGITUDE, 0L)) else null
        set(value) = secureEdit().apply {
            if (value == null) remove(KEY_LONGITUDE) else putLong(KEY_LONGITUDE, value.toBits())
        }.apply()

    var locationLabel: String
        get() = securePrefs.getString(KEY_LOCATION_LABEL, "") ?: ""
        set(value) = secureEdit().putString(KEY_LOCATION_LABEL, value).apply()

    /**
     * The saved fix, or null when it cannot be trusted. Range-checking here (not only on
     * write) means a coordinate persisted by an older build — `(0,0)`, a `NaN`, an
     * out-of-range value restored from a backup — degrades to "no location yet" instead of
     * producing a wrong timetable.
     */
    val locationCoordinates: Pair<Double, Double>?
        get() {
            val lat = locationLatitude ?: return null
            val lon = locationLongitude ?: return null
            return LocationValidator.sanitize(lat, lon)
        }

    /** ISO 3166-1 alpha-2 code of the last resolved location (e.g. "QA"). */
    var locationCountryCode: String?
        get() = securePrefs.getString(KEY_LOCATION_COUNTRY_CODE, null)?.takeIf { it.isNotBlank() }
        set(value) = secureEdit().apply {
            if (value.isNullOrBlank()) remove(KEY_LOCATION_COUNTRY_CODE) else putString(KEY_LOCATION_COUNTRY_CODE, value.uppercase())
        }.apply()

    /** IANA timezone ID for the active prayer location (e.g. "America/Los_Angeles"). */
    var locationTimeZoneId: String?
        get() = securePrefs.getString(KEY_LOCATION_TIMEZONE_ID, null)?.takeIf { it.isNotBlank() }
        set(value) = secureEdit().apply {
            if (value.isNullOrBlank()) remove(KEY_LOCATION_TIMEZONE_ID) else putString(KEY_LOCATION_TIMEZONE_ID, value)
        }.apply()

    /** True when the user explicitly chose a city manually (prevents GPS overwrite on restart). */
    var isManualLocation: Boolean
        get() = securePrefs.getBoolean(KEY_IS_MANUAL_LOCATION, false)
        set(value) = secureEdit().putBoolean(KEY_IS_MANUAL_LOCATION, value).apply()

    /** Epoch millis at which the saved location was last written, or 0 when unknown. */
    var locationTimestampMillis: Long
        get() = securePrefs.getLong(KEY_LOCATION_TIMESTAMP, 0L)
        set(value) = secureEdit().putLong(KEY_LOCATION_TIMESTAMP, value).apply()

    /** "gps" or "manual" — which path produced the saved coordinates. */
    var locationSource: String
        get() = securePrefs.getString(KEY_LOCATION_SOURCE, "") ?: ""
        set(value) = secureEdit().putString(KEY_LOCATION_SOURCE, value).apply()

    /** Age of the saved location in millis, or null when nothing (or no timestamp) is saved. */
    fun locationAgeMillis(nowMillis: Long = System.currentTimeMillis()): Long? {
        if (locationCoordinates == null) return null
        val savedAt = locationTimestampMillis
        if (savedAt <= 0L) return null
        return (nowMillis - savedAt).coerceAtLeast(0L)
    }

    /** True when the saved fix is older than [maxAgeMillis] and must not read as current. */
    fun isLocationStale(
        maxAgeMillis: Long = CACHED_LOCATION_MAX_AGE_MS,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val age = locationAgeMillis(nowMillis) ?: return false
        return age > maxAgeMillis
    }

    /**
     * A calculation method the user picked by hand. `null` means "follow the country's
     * Sunni authority automatically".
     */
    var calculationMethodOverride: CalculationMethod?
        get() = prefs.getString(KEY_CALCULATION_METHOD_OVERRIDE, null)
            ?.let { name -> runCatching { CalculationMethod.valueOf(name) }.getOrNull() }
        set(value) = prefs.edit {
            if (value == null) remove(KEY_CALCULATION_METHOD_OVERRIDE) else putString(KEY_CALCULATION_METHOD_OVERRIDE, value.name)
        }

    var homeLatitude: Double?
        get() = if (securePrefs.contains(KEY_HOME_LATITUDE)) Double.fromBits(securePrefs.getLong(KEY_HOME_LATITUDE, 0L)) else null
        set(value) = secureEdit().apply {
            if (value == null) remove(KEY_HOME_LATITUDE) else putLong(KEY_HOME_LATITUDE, value.toBits())
        }.apply()

    var homeLongitude: Double?
        get() = if (securePrefs.contains(KEY_HOME_LONGITUDE)) Double.fromBits(securePrefs.getLong(KEY_HOME_LONGITUDE, 0L)) else null
        set(value) = secureEdit().apply {
            if (value == null) remove(KEY_HOME_LONGITUDE) else putLong(KEY_HOME_LONGITUDE, value.toBits())
        }.apply()

    var homeCityName: String?
        get() = securePrefs.getString(KEY_HOME_CITY, null)?.takeIf { it.isNotBlank() }
        set(value) = secureEdit().apply {
            if (value.isNullOrBlank()) remove(KEY_HOME_CITY) else putString(KEY_HOME_CITY, value)
        }.apply()

    /** `null` when unsaved or when the stored pair cannot be a real Home (e.g. `(0.0, 0.0)`). */
    val homeCoordinates: Pair<Double, Double>?
        get() {
            val lat = homeLatitude ?: return null
            val lon = homeLongitude ?: return null
            return SafarPolicy.sanitize(Pair(lat, lon))
        }

    /** True only when the user explicitly chose a Home Location. */
    var homeExplicitlySet: Boolean
        get() = securePrefs.getBoolean(KEY_HOME_EXPLICITLY_SET, false)
        set(value) = secureEdit().putBoolean(KEY_HOME_EXPLICITLY_SET, value).apply()

    var calculationMethod: CalculationMethod
        get() = runCatching {
            CalculationMethod.valueOf(
                prefs.getString(KEY_CALCULATION_METHOD, null) ?: return@runCatching CalculationMethod.MUSLIM_WORLD_LEAGUE
            )
        }.getOrDefault(CalculationMethod.MUSLIM_WORLD_LEAGUE)
        set(value) = prefs.edit { putString(KEY_CALCULATION_METHOD, value.name) }

    var madhhab: Madhhab
        get() = runCatching {
            Madhhab.valueOf(
                prefs.getString(KEY_MADHHAB, null) ?: return@runCatching Madhhab.HANAFI
            )
        }.getOrDefault(Madhhab.HANAFI)
        set(value) = prefs.edit { putString(KEY_MADHHAB, value.name) }

    /** MAC address of the user's saved car head unit, if any. */
    var carBluetoothAddress: String?
        get() = prefs.getString(KEY_CAR_ADDRESS, null)
        set(value) = prefs.edit {
            if (value.isNullOrBlank()) remove(KEY_CAR_ADDRESS) else putString(KEY_CAR_ADDRESS, value)
        }

    var carBluetoothName: String?
        get() = prefs.getString(KEY_CAR_NAME, null)
        set(value) = prefs.edit {
            if (value.isNullOrBlank()) remove(KEY_CAR_NAME) else putString(KEY_CAR_NAME, value)
        }

    var travelModeActive: Boolean
        get() = prefs.getBoolean(KEY_TRAVEL_MODE, false)
        set(value) = prefs.edit { putBoolean(KEY_TRAVEL_MODE, value) }

    var qasrActive: Boolean
        get() = prefs.getBoolean(KEY_QASR_ACTIVE, false)
        set(value) = prefs.edit { putBoolean(KEY_QASR_ACTIVE, value) }

    var lastDistanceFromHomeKm: Float
        get() = prefs.getFloat(KEY_LAST_DISTANCE_KM, 0f)
        set(value) = prefs.edit { putFloat(KEY_LAST_DISTANCE_KM, value) }

    /** Optional override when the user supplies their own Adhan recording. */
    var adhanSoundUri: String?
        get() = prefs.getString(KEY_ADHAN_SOUND_URI, null)
        set(value) = prefs.edit {
            if (value.isNullOrBlank()) remove(KEY_ADHAN_SOUND_URI) else putString(KEY_ADHAN_SOUND_URI, value)
        }

    var duaSafarSoundUri: String?
        get() = prefs.getString(KEY_DUA_SAFAR_SOUND_URI, null)
        set(value) = prefs.edit {
            if (value.isNullOrBlank()) remove(KEY_DUA_SAFAR_SOUND_URI) else putString(KEY_DUA_SAFAR_SOUND_URI, value)
        }

    var quranTranslationId: Int
        get() = prefs.getInt(KEY_QURAN_TRANSLATION_ID, IslamicDataRepository.DEFAULT_TRANSLATION_ID)
        set(value) = prefs.edit { putInt(KEY_QURAN_TRANSLATION_ID, value) }

    var quranTranslationLanguage: String
        get() = prefs.getString(KEY_QURAN_TRANSLATION_LANGUAGE, "English") ?: "English"
        set(value) = prefs.edit { putString(KEY_QURAN_TRANSLATION_LANGUAGE, value) }

    var quranTranslationEditionKey: String
        get() = prefs.getString(KEY_QURAN_TRANSLATION_EDITION_KEY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_QURAN_TRANSLATION_EDITION_KEY, value) }

    var quranTranslationSource: String
        get() = prefs.getString(
            KEY_QURAN_TRANSLATION_SOURCE,
            QuranTranslationResource.SOURCE_QURAN_COM
        ) ?: QuranTranslationResource.SOURCE_QURAN_COM
        set(value) = prefs.edit { putString(KEY_QURAN_TRANSLATION_SOURCE, value) }

    /** Selected Arabic script edition for the Quran reader. */
    var quranArabicScript: QuranArabicScript
        get() = QuranArabicScript.fromKey(prefs.getString(KEY_QURAN_ARABIC_SCRIPT, null))
        set(value) = prefs.edit { putString(KEY_QURAN_ARABIC_SCRIPT, value.name) }

    /**
     * Background Azaan scheduling is on after onboarding (or whenever a location has already
     * been saved by an older install). The settings screen can turn it off explicitly.
     */
    var alarmsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALARMS_ENABLED, locationCoordinates != null)
        set(value) = prefs.edit { putBoolean(KEY_ALARMS_ENABLED, value) }

    /**
     * Last day (epoch day) whose missed prayers were rolled into the Qadha ledger. Keeps the
     * daily reset idempotent when WorkManager retries a partially completed run.
     */
    var lastQadhaRolloverEpochDay: Long
        get() = prefs.getLong(KEY_LAST_QADHA_ROLLOVER, Long.MIN_VALUE)
        set(value) = prefs.edit { putLong(KEY_LAST_QADHA_ROLLOVER, value) }

    /** True once the first-launch permission request has been shown, so it is not repeated. */
    var firstLaunchPermissionsRequested: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH_PERMISSIONS_REQUESTED, false)
        set(value) = prefs.edit { putBoolean(KEY_FIRST_LAUNCH_PERMISSIONS_REQUESTED, value) }

    var azaanSettings: AzaanSettings
        get() {
            val modes = AzaanSettings.ALERT_PRAYERS.associateWith { prayer ->
                prefs.getString(KEY_ALERT_MODE_PREFIX + prayer.name.lowercase(), null)
                    ?.let { name -> runCatching { PrayerAlertMode.valueOf(name) }.getOrNull() }
                    ?: PrayerAlertMode.FULL_AZAAN
            }
            val offsets = AzaanSettings.ALERT_PRAYERS.associateWith { prayer ->
                prefs.getInt(KEY_ALERT_OFFSET_PREFIX + prayer.name.lowercase(), 0)
            }
            return AzaanSettings(
                enabled = alarmsEnabled,
                muezzin = Muezzin.fromId(prefs.getString(KEY_MUEZZIN, null)),
                fajrSpecialAdhan = prefs.getBoolean(KEY_FAJR_SPECIAL_ADHAN, true),
                criticalFajrChannel = prefs.getBoolean(KEY_CRITICAL_FAJR_CHANNEL, false),
                modes = modes,
                offsetsMinutes = offsets
            )
        }
        set(value) {
            prefs.edit {
                putBoolean(KEY_ALARMS_ENABLED, value.enabled)
                putString(KEY_MUEZZIN, value.muezzin.id)
                putBoolean(KEY_FAJR_SPECIAL_ADHAN, value.fajrSpecialAdhan)
                putBoolean(KEY_CRITICAL_FAJR_CHANNEL, value.criticalFajrChannel)
                AzaanSettings.ALERT_PRAYERS.forEach { prayer ->
                    val suffix = prayer.name.lowercase()
                    putString(
                        KEY_ALERT_MODE_PREFIX + suffix,
                        (value.modes[prayer] ?: PrayerAlertMode.FULL_AZAAN).name
                    )
                    putInt(
                        KEY_ALERT_OFFSET_PREFIX + suffix,
                        value.offsetsMinutes[prayer] ?: 0
                    )
                }
            }
        }

    fun alertModeFor(prayer: PrayerType): PrayerAlertMode = azaanSettings.modeFor(prayer)

    fun alertOffsetMinutesFor(prayer: PrayerType): Int = azaanSettings.offsetFor(prayer)

    /**
     * Next prayer alarm actually armed with AlarmManager (0 when none). Written by
     * [com.sakinah.deen.service.PrayerAlarmScheduler], read by the in-app self-test so the next
     * prayer stays visible even while notifications are denied or delayed.
     */
    var nextAlarmAtMillis: Long
        get() = prefs.getLong(KEY_NEXT_ALARM_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_NEXT_ALARM_AT, value) }

    var nextAlarmPrayer: String
        get() = prefs.getString(KEY_NEXT_ALARM_PRAYER, "") ?: ""
        set(value) = prefs.edit { putString(KEY_NEXT_ALARM_PRAYER, value) }

    /** [com.sakinah.deen.service.AlarmScheduleResult.code] of the last scheduling attempt. */
    var lastAlarmScheduleResult: String
        get() = prefs.getString(KEY_LAST_ALARM_RESULT, "") ?: ""
        set(value) = prefs.edit { putString(KEY_LAST_ALARM_RESULT, value) }

    /**
     * FNV-1a hash of the bundled Hisn al-Muslim asset edition that was last written to
     * Room. A mismatch means the APK ships a newer/larger set and seeding should refresh it.
     */
    var adhkarSeedHash: String?
        get() = prefs.getString(KEY_ADHKAR_SEED_HASH, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit {
            if (value.isNullOrBlank()) remove(KEY_ADHKAR_SEED_HASH) else putString(KEY_ADHKAR_SEED_HASH, value)
        }

    var hadithLanguageCode: String
        get() = prefs.getString(KEY_HADITH_LANGUAGE, "eng") ?: "eng"
        set(value) = prefs.edit { putString(KEY_HADITH_LANGUAGE, value) }

    var hasInitializedHadithBookmarks: Boolean
        get() = prefs.getBoolean(KEY_HADITH_BOOKMARKS_INITIALIZED, false)
        set(value) = prefs.edit { putBoolean(KEY_HADITH_BOOKMARKS_INITIALIZED, value) }

    var bookmarkedHadithIds: Set<String>
        get() {
            val raw = prefs.getString(KEY_BOOKMARKED_HADITH_IDS, "") ?: ""
            if (raw.isBlank()) return emptySet()
            return raw.split("|").mapNotNull { it.takeIf(String::isNotBlank) }.toSet()
        }
        set(value) {
            prefs.edit { putString(KEY_BOOKMARKED_HADITH_IDS, value.joinToString("|")) }
        }

    /**
     * Zakat balance-sheet values, persisted on every edit (guest mode included) so
     * closing the app never loses an entered amount.
     */
    var zakatCurrency: String
        get() = securePrefs.getString(KEY_ZAKAT_CURRENCY, "USD") ?: "USD"
        set(value) = secureEdit().putString(KEY_ZAKAT_CURRENCY, value).apply()

    var zakatCashInBank: Float
        get() = securePrefs.getFloat(KEY_ZAKAT_CASH, 0f)
        set(value) = secureEdit().putFloat(KEY_ZAKAT_CASH, value).apply()

    var zakatGoldHeld: Float
        get() = securePrefs.getFloat(KEY_ZAKAT_GOLD, 0f)
        set(value) = secureEdit().putFloat(KEY_ZAKAT_GOLD, value).apply()

    var zakatSilverHeld: Float
        get() = securePrefs.getFloat(KEY_ZAKAT_SILVER, 0f)
        set(value) = secureEdit().putFloat(KEY_ZAKAT_SILVER, value).apply()

    var zakatTradeInventory: Float
        get() = securePrefs.getFloat(KEY_ZAKAT_INVENTORY, 0f)
        set(value) = secureEdit().putFloat(KEY_ZAKAT_INVENTORY, value).apply()

    var zakatStocksValue: Float
        get() = securePrefs.getFloat(KEY_ZAKAT_STOCKS, 0f)
        set(value) = secureEdit().putFloat(KEY_ZAKAT_STOCKS, value).apply()

    var zakatShortTermDebts: Float
        get() = securePrefs.getFloat(KEY_ZAKAT_DEBTS, 0f)
        set(value) = secureEdit().putFloat(KEY_ZAKAT_DEBTS, value).apply()

    /**
     * Hifz verse annotations keyed by "surahNumber:ayahNumber" (e.g. "2:255" to "Mutashabihat").
     * Stored locally so memorisation tags survive a restart in guest mode too.
     */
    var hifzAnnotations: Map<String, String>
        get() {
            val raw = prefs.getString(KEY_HIFZ_ANNOTATIONS, null) ?: return emptyMap()
            if (raw.isBlank()) return emptyMap()
            return raw.split("|").mapNotNull { entry ->
                val separator = entry.indexOf('=')
                if (separator <= 0 || separator == entry.lastIndex) return@mapNotNull null
                val key = entry.substring(0, separator).trim()
                val tag = entry.substring(separator + 1).trim()
                if (key.isBlank() || tag.isBlank()) null else key to tag
            }.toMap()
        }
        set(value) {
            val encoded = value
                .filterKeys { it.isNotBlank() }
                .filterValues { it.isNotBlank() }
                .entries
                .joinToString("|") { "${it.key}=${it.value}" }
            if (encoded.isBlank()) {
                prefs.edit { remove(KEY_HIFZ_ANNOTATIONS) }
            } else {
                prefs.edit { putString(KEY_HIFZ_ANNOTATIONS, encoded) }
            }
        }

    /** Returns false (and writes nothing) when the coordinates are not a real location. */
    fun saveLocation(latitude: Double, longitude: Double, label: String): Boolean {
        val sane = LocationValidator.sanitize(latitude, longitude) ?: return false
        locationLatitude = sane.first
        locationLongitude = sane.second
        locationLabel = label
        locationTimestampMillis = System.currentTimeMillis()
        locationSource = LOCATION_SOURCE_MANUAL
        return true
    }

    /**
     * Saves a hand-picked city. Invalid coordinates are rejected outright; an invalid or
     * missing zone id is dropped (the resolver derives one from the city/country/longitude)
     * rather than being stored as a zone the platform cannot resolve.
     */
    fun saveManualLocation(
        latitude: Double,
        longitude: Double,
        label: String,
        countryCode: String?,
        timeZoneId: String?,
    ): Boolean {
        val sane = LocationValidator.sanitize(latitude, longitude) ?: return false
        locationLatitude = sane.first
        locationLongitude = sane.second
        locationLabel = label
        locationCountryCode = countryCode
        locationTimeZoneId = timeZoneId?.takeIf(LocationValidator::isValidZoneId)
        isManualLocation = true
        locationTimestampMillis = System.currentTimeMillis()
        locationSource = LOCATION_SOURCE_MANUAL
        return true
    }

    fun saveGpsLocation(
        latitude: Double,
        longitude: Double,
        label: String,
        countryCode: String?,
        timeZoneId: String?,
    ): Boolean {
        val sane = LocationValidator.sanitize(latitude, longitude) ?: return false
        locationLatitude = sane.first
        locationLongitude = sane.second
        locationLabel = label
        locationCountryCode = countryCode
        locationTimeZoneId = timeZoneId?.takeIf(LocationValidator::isValidZoneId)
        isManualLocation = false
        locationTimestampMillis = System.currentTimeMillis()
        locationSource = LOCATION_SOURCE_GPS
        return true
    }

    // --- Nafl completion (date-aware so a new day starts unchecked) -------------------

    private fun naflKey(epochDay: Long, prayerType: PrayerType, nafl: NaflType): String =
        "$epochDay:${prayerType.name}:${nafl.name}"

    /** Nafl entries recorded for [epochDay], as `epochDay:PRAYER:NAFL` keys. */
    fun naflCompletions(epochDay: Long): Set<String> =
        prefs.getStringSet(KEY_NAFL_COMPLETIONS, emptySet()).orEmpty()
            .filterTo(mutableSetOf()) { it.startsWith("$epochDay:") }

    fun isNaflCompleted(epochDay: Long, prayerType: PrayerType, nafl: NaflType): Boolean =
        prefs.getStringSet(KEY_NAFL_COMPLETIONS, emptySet()).orEmpty()
            .contains(naflKey(epochDay, prayerType, nafl))

    /**
     * Toggles one date/prayer/nafl entry and drops every other day's entries, so Nafl
     * completion resets on a new date and survives process death within the same day.
     * Returns the new completed state.
     */
    fun toggleNaflCompletion(epochDay: Long, prayerType: PrayerType, nafl: NaflType): Boolean {
        val key = naflKey(epochDay, prayerType, nafl)
        val current = prefs.getStringSet(KEY_NAFL_COMPLETIONS, emptySet()).orEmpty()
        val sameDay = current.filterTo(mutableSetOf()) { it.startsWith("$epochDay:") }
        val completed = if (key in sameDay) {
            sameDay.remove(key)
            false
        } else {
            sameDay.add(key)
            true
        }
        prefs.edit { putStringSet(KEY_NAFL_COMPLETIONS, sameDay) }
        return completed
    }

    // --- Safar methodology ------------------------------------------------------------

    /** Distance-only Qasr or distance plus a stay-duration limit. */
    var safarMethodology: SafarPolicy.SafarMethodology
        get() = runCatching {
            SafarPolicy.SafarMethodology.valueOf(
                prefs.getString(KEY_SAFAR_METHODOLOGY, null)
                    ?: return@runCatching SafarPolicy.SafarMethodology.DISTANCE_AND_STAY_DURATION
            )
        }.getOrDefault(SafarPolicy.SafarMethodology.DISTANCE_AND_STAY_DURATION)
        set(value) = prefs.edit { putString(KEY_SAFAR_METHODOLOGY, value.name) }

    /** Nights after which a traveller following the stay-limit methodology becomes Muqim. */
    var safarMaxStayDays: Int
        get() = prefs.getInt(KEY_SAFAR_MAX_STAY_DAYS, SafarPolicy.DEFAULT_MAX_STAY_DAYS)
        set(value) = prefs.edit { putInt(KEY_SAFAR_MAX_STAY_DAYS, value.coerceAtLeast(1)) }

    /** Explicit opt-in: Jam is never enabled merely by crossing the distance threshold. */
    var safarJamPermitted: Boolean
        get() = prefs.getBoolean(KEY_SAFAR_JAM_PERMITTED, false)
        set(value) = prefs.edit { putBoolean(KEY_SAFAR_JAM_PERMITTED, value) }

    var safarTravelStartEpochDay: Long?
        get() = prefs.getLong(KEY_SAFAR_TRAVEL_START, 0L).takeIf { it > 0L }
        set(value) = prefs.edit {
            if (value == null) remove(KEY_SAFAR_TRAVEL_START) else putLong(KEY_SAFAR_TRAVEL_START, value)
        }

    /** Current Safar rules, shared by the dashboard, the travel card and background code. */
    val safarSettings: SafarPolicy.SafarSettings
        get() = SafarPolicy.SafarSettings(
            methodology = safarMethodology,
            maxStayDays = safarMaxStayDays,
            jamPermittedByUser = safarJamPermitted,
            travelStartEpochDay = safarTravelStartEpochDay
        )

    /**
     * Saves a Home Location. Invalid coordinates (e.g. an unresolved `(0.0, 0.0)` fix) are
     * ignored so an existing Home is never overwritten by junk. Saving a Home also drops any
     * stale Safar override, so rakat counts immediately return to normal.
     */
    fun saveHomeAnchor(latitude: Double, longitude: Double, cityName: String? = null) {
        val home = SafarPolicy.sanitize(Pair(latitude, longitude)) ?: return
        homeLatitude = home.first
        homeLongitude = home.second
        if (cityName != null) homeCityName = cityName
        travelModeActive = false
        qasrActive = false
        lastDistanceFromHomeKm = 0f
        homeExplicitlySet = true
        HomeAnchorStore.publish(home)
    }

    /** Removes the Home Location and every Safar flag derived from it. */
    fun clearHomeAnchor() {
        homeLatitude = null
        homeLongitude = null
        homeCityName = null
        travelModeActive = false
        qasrActive = false
        lastDistanceFromHomeKm = 0f
        homeExplicitlySet = false
        HomeAnchorStore.publish(null)
    }

    /**
     * Moves coordinates and Zakat balances into the encrypted store, then removes the
     * plaintext copies only after the encrypted write is verified.
     */
    private fun migrateSensitiveValuesIfNeeded() {
        if (securePrefs === prefs) return
        val editor = securePrefs.edit()
        val copied = mutableListOf<String>()
        SENSITIVE_KEYS.forEach { key ->
            if (!prefs.contains(key) || securePrefs.contains(key)) return@forEach
            when (val value = prefs.all[key]) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                else -> return@forEach
            }
            copied += key
        }
        if (copied.isEmpty()) return
        editor.apply()
        val verified = copied.filter { securePrefs.all[it] == prefs.all[it] }
        if (verified.size == copied.size) {
            prefs.edit { copied.forEach { key -> remove(key) } }
        }
    }

    /**
     * One-time cleanup for installs that inherited a fabricated London Home. Also drops a
     * London Home that was never explicitly chosen, so nobody is told they are 5,225 km from
     * a city they never picked.
     */
    private fun migrateLondonHomeIfNeeded() {
        if (homeExplicitlySet) return
        val lat = homeLatitude ?: return
        val lon = homeLongitude ?: return
        val label = homeCityName
        if (lat == LEGACY_DEFAULT_LATITUDE && lon == LEGACY_DEFAULT_LONGITUDE &&
            (label == null || label == LEGACY_DEFAULT_LOCATION_LABEL)
        ) {
            clearHomeAnchor()
        }
    }

    /** Minute offsets applied to the computed/authoritative timings (neighbourhood mosque). */
    var prayerAdjustments: PrayerAdjustments
        get() = PrayerAdjustments(
            fajrMinutes = prefs.getInt(KEY_ADJUST_FAJR, 0),
            sunriseMinutes = prefs.getInt(KEY_ADJUST_SUNRISE, 0),
            dhuhrMinutes = prefs.getInt(KEY_ADJUST_DHUHR, 0),
            asrMinutes = prefs.getInt(KEY_ADJUST_ASR, 0),
            maghribMinutes = prefs.getInt(KEY_ADJUST_MAGHRIB, 0),
            ishaMinutes = prefs.getInt(KEY_ADJUST_ISHA, 0)
        )
        set(value) = prefs.edit {
            putInt(KEY_ADJUST_FAJR, value.fajrMinutes)
            putInt(KEY_ADJUST_SUNRISE, value.sunriseMinutes)
            putInt(KEY_ADJUST_DHUHR, value.dhuhrMinutes)
            putInt(KEY_ADJUST_ASR, value.asrMinutes)
            putInt(KEY_ADJUST_MAGHRIB, value.maghribMinutes)
            putInt(KEY_ADJUST_ISHA, value.ishaMinutes)
        }

    /**
     * Mirrors the high-latitude rule kept in the shared settings store so background
     * scheduling (no repository, no Compose) recomputes the same Fajr/Isha.
     */
    var highLatitudeRule: HighLatitudeRule
        get() = runCatching {
            HighLatitudeRule.valueOf(
                prefs.getString(KEY_HIGH_LATITUDE_RULE, null)
                    ?: return@runCatching HighLatitudeRule.MIDDLE_OF_NIGHT
            )
        }.getOrDefault(HighLatitudeRule.MIDDLE_OF_NIGHT)
        set(value) = prefs.edit { putString(KEY_HIGH_LATITUDE_RULE, value.name) }

    fun saveCarDevice(address: String, name: String?) {
        carBluetoothAddress = address
        carBluetoothName = name
    }
}
