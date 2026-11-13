package com.sakinah.deen.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.network.IslamicDataRepository
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.domain.travel.HomeAnchorStore
import com.sakinah.deen.domain.travel.SafarPolicy

/**
 * Single source of truth for values that background components (alarms, receivers,
 * services) need while no Activity or ViewModel exists.
 *
 * The [SharedPreferences] constructor is internal so the preference rules ("a saved Home
 * clears Travel Mode", "(0,0) is not a Home") can be unit tested without Android.
 */
class SakinahPreferences internal constructor(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    init {
        // Existing installs may have inherited a London default Home; drop it once.
        migrateLondonHomeIfNeeded()
    }

    companion object {
        const val DEFAULT_LATITUDE = 51.5074
        const val DEFAULT_LONGITUDE = -0.1278
        const val DEFAULT_LOCATION_LABEL = "London, UK"

        private const val PREFS_NAME = "sakinah_preferences"
        private const val KEY_LATITUDE = "location_latitude"
        private const val KEY_LONGITUDE = "location_longitude"
        private const val KEY_LOCATION_LABEL = "location_label"
        private const val KEY_LOCATION_COUNTRY_CODE = "location_country_code"
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
        private const val KEY_HADITH_LANGUAGE = "hadith_language_code"
    }

    /** Double is stored as raw bits so stored coordinates do not lose precision. */
    var locationLatitude: Double?
        get() = if (prefs.contains(KEY_LATITUDE)) Double.fromBits(prefs.getLong(KEY_LATITUDE, 0L)) else null
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_LATITUDE) else putLong(KEY_LATITUDE, value.toBits())
        }.apply()

    var locationLongitude: Double?
        get() = if (prefs.contains(KEY_LONGITUDE)) Double.fromBits(prefs.getLong(KEY_LONGITUDE, 0L)) else null
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_LONGITUDE) else putLong(KEY_LONGITUDE, value.toBits())
        }.apply()

    var locationLabel: String
        get() = prefs.getString(KEY_LOCATION_LABEL, DEFAULT_LOCATION_LABEL) ?: DEFAULT_LOCATION_LABEL
        set(value) = prefs.edit().putString(KEY_LOCATION_LABEL, value).apply()

    val locationCoordinates: Pair<Double, Double>?
        get() {
            val lat = locationLatitude ?: return null
            val lon = locationLongitude ?: return null
            return Pair(lat, lon)
        }

    /** ISO 3166-1 alpha-2 code of the last resolved location (e.g. "QA"). */
    var locationCountryCode: String?
        get() = prefs.getString(KEY_LOCATION_COUNTRY_CODE, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_LOCATION_COUNTRY_CODE) else putString(KEY_LOCATION_COUNTRY_CODE, value.uppercase())
        }.apply()

    /**
     * A calculation method the user picked by hand. `null` means "follow the country's
     * Sunni authority automatically".
     */
    var calculationMethodOverride: CalculationMethod?
        get() = prefs.getString(KEY_CALCULATION_METHOD_OVERRIDE, null)
            ?.let { name -> runCatching { CalculationMethod.valueOf(name) }.getOrNull() }
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_CALCULATION_METHOD_OVERRIDE) else putString(KEY_CALCULATION_METHOD_OVERRIDE, value.name)
        }.apply()

    var homeLatitude: Double?
        get() = if (prefs.contains(KEY_HOME_LATITUDE)) Double.fromBits(prefs.getLong(KEY_HOME_LATITUDE, 0L)) else null
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_HOME_LATITUDE) else putLong(KEY_HOME_LATITUDE, value.toBits())
        }.apply()

    var homeLongitude: Double?
        get() = if (prefs.contains(KEY_HOME_LONGITUDE)) Double.fromBits(prefs.getLong(KEY_HOME_LONGITUDE, 0L)) else null
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_HOME_LONGITUDE) else putLong(KEY_HOME_LONGITUDE, value.toBits())
        }.apply()

    var homeCityName: String?
        get() = prefs.getString(KEY_HOME_CITY, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().apply {
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
        get() = prefs.getBoolean(KEY_HOME_EXPLICITLY_SET, false)
        set(value) = prefs.edit().putBoolean(KEY_HOME_EXPLICITLY_SET, value).apply()

    var calculationMethod: CalculationMethod
        get() = runCatching {
            CalculationMethod.valueOf(
                prefs.getString(KEY_CALCULATION_METHOD, null) ?: return@runCatching CalculationMethod.MUSLIM_WORLD_LEAGUE
            )
        }.getOrDefault(CalculationMethod.MUSLIM_WORLD_LEAGUE)
        set(value) = prefs.edit().putString(KEY_CALCULATION_METHOD, value.name).apply()

    var madhhab: Madhhab
        get() = runCatching {
            Madhhab.valueOf(
                prefs.getString(KEY_MADHHAB, null) ?: return@runCatching Madhhab.HANAFI
            )
        }.getOrDefault(Madhhab.HANAFI)
        set(value) = prefs.edit().putString(KEY_MADHHAB, value.name).apply()

    /** MAC address of the user's saved car head unit, if any. */
    var carBluetoothAddress: String?
        get() = prefs.getString(KEY_CAR_ADDRESS, null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_CAR_ADDRESS) else putString(KEY_CAR_ADDRESS, value)
        }.apply()

    var carBluetoothName: String?
        get() = prefs.getString(KEY_CAR_NAME, null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_CAR_NAME) else putString(KEY_CAR_NAME, value)
        }.apply()

    var travelModeActive: Boolean
        get() = prefs.getBoolean(KEY_TRAVEL_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_TRAVEL_MODE, value).apply()

    var qasrActive: Boolean
        get() = prefs.getBoolean(KEY_QASR_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_QASR_ACTIVE, value).apply()

    var lastDistanceFromHomeKm: Float
        get() = prefs.getFloat(KEY_LAST_DISTANCE_KM, 0f)
        set(value) = prefs.edit().putFloat(KEY_LAST_DISTANCE_KM, value).apply()

    /** Optional override when the user supplies their own Adhan recording. */
    var adhanSoundUri: String?
        get() = prefs.getString(KEY_ADHAN_SOUND_URI, null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_ADHAN_SOUND_URI) else putString(KEY_ADHAN_SOUND_URI, value)
        }.apply()

    var duaSafarSoundUri: String?
        get() = prefs.getString(KEY_DUA_SAFAR_SOUND_URI, null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_DUA_SAFAR_SOUND_URI) else putString(KEY_DUA_SAFAR_SOUND_URI, value)
        }.apply()

    var quranTranslationId: Int
        get() = prefs.getInt(KEY_QURAN_TRANSLATION_ID, IslamicDataRepository.DEFAULT_TRANSLATION_ID)
        set(value) = prefs.edit().putInt(KEY_QURAN_TRANSLATION_ID, value).apply()

    var quranTranslationLanguage: String
        get() = prefs.getString(KEY_QURAN_TRANSLATION_LANGUAGE, "English") ?: "English"
        set(value) = prefs.edit().putString(KEY_QURAN_TRANSLATION_LANGUAGE, value).apply()

    var hadithLanguageCode: String
        get() = prefs.getString(KEY_HADITH_LANGUAGE, "eng") ?: "eng"
        set(value) = prefs.edit().putString(KEY_HADITH_LANGUAGE, value).apply()

    fun saveLocation(latitude: Double, longitude: Double, label: String) {
        locationLatitude = latitude
        locationLongitude = longitude
        locationLabel = label
    }

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

    private fun migrateLondonHomeIfNeeded() {
        if (homeExplicitlySet) return
        val lat = homeLatitude ?: return
        val lon = homeLongitude ?: return
        val label = homeCityName
        if (lat == DEFAULT_LATITUDE && lon == DEFAULT_LONGITUDE &&
            (label == null || label == DEFAULT_LOCATION_LABEL || label == "London, UK")
        ) {
            // Drop the inherited London default so existing installs stop showing 5225 km.
            clearHomeAnchor()
        }
    }

    fun saveCarDevice(address: String, name: String?) {
        carBluetoothAddress = address
        carBluetoothName = name
    }
}
