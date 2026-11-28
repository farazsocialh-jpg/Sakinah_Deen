package com.sakinah.deen.data.prayer

import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.settings.CalculationMethod as SharedCalculationMethod

/**
 * A Sunni calculation authority as published by the free AlAdhan API. [methodId] is the
 * value sent as `method=` on `api.aladhan.com`; [displayName] is deliberately short so it
 * can be shown as an on-screen source label ("Qatar Awqaf (Online)").
 */
enum class SunniAuthority(val methodId: Int, val displayName: String) {
    UMM_AL_QURA(4, "Umm Al-Qura (Makkah & Saudi Arabia)"),
    QATAR(10, "Qatar Awqaf"),
    DUBAI(16, "Dubai / UAE Awqaf"),
    KUWAIT(9, "Kuwait Awqaf"),
    GULF_REGION(8, "Gulf Region"),
    EGYPTIAN(5, "Egyptian General Survey"),
    MUSLIM_WORLD_LEAGUE(3, "Muslim World League (MWL)"),
    ISNA(2, "North America (ISNA)"),
    KARACHI(1, "Karachi (South Asia)"),
    DIYANET(13, "Diyanet (Turkey)"),
    JAKIM(17, "JAKIM (Malaysia)"),
    MUIS(11, "MUIS (Singapore)"),
    KEMENAG(20, "KEMENAG (Indonesia)"),
    MOONSIGHTING_COMMITTEE(15, "Moonsighting Committee Worldwide"),
    UOIF(12, "UOIF (France)"),
    SAMR(14, "Spiritual Administration of Muslims of Russia"),
    JORDAN(23, "Jordan Awqaf"),
    TUNISIA(18, "Tunisia / Algeria / Morocco"),
    TEHRAN(7, "Tehran (Institute of Geophysics)");

    companion object {
        fun fromMethodId(methodId: Int): SunniAuthority? = entries.firstOrNull { it.methodId == methodId }
    }
}

/**
 * Maps the user's country to the Sunni authority that governs local prayer times, and
 * translates the app's juristic settings into AlAdhan request parameters.
 *
 * The auto-mapping is only a default: an explicit selection in settings always wins, see
 * [resolve].
 */
object SunniMethodMapper {

    /** ISO 3166-1 alpha-2 country code to AlAdhan `method` id. */
    fun fromCountryCode(countryCode: String?): SunniAuthority =
        when (countryCode?.trim()?.uppercase()) {
            "SA" -> SunniAuthority.UMM_AL_QURA
            "QA" -> SunniAuthority.QATAR
            "AE" -> SunniAuthority.DUBAI
            "KW" -> SunniAuthority.KUWAIT
            "EG" -> SunniAuthority.EGYPTIAN
            "TR" -> SunniAuthority.DIYANET
            "MY" -> SunniAuthority.JAKIM
            "SG" -> SunniAuthority.MUIS
            "PK", "IN", "BD" -> SunniAuthority.KARACHI
            "US", "CA" -> SunniAuthority.ISNA
            else -> SunniAuthority.MUSLIM_WORLD_LEAGUE
        }

    /** A calculation method chosen by hand (Salah screen or app settings). */
    fun fromOverride(method: CalculationMethod): SunniAuthority = when (method) {
        CalculationMethod.UMM_AL_QURA -> SunniAuthority.UMM_AL_QURA
        CalculationMethod.QATAR -> SunniAuthority.QATAR
        CalculationMethod.DUBAI -> SunniAuthority.DUBAI
        CalculationMethod.KARACHI -> SunniAuthority.KARACHI
        CalculationMethod.EGYPTIAN -> SunniAuthority.EGYPTIAN
        CalculationMethod.NORTH_AMERICA -> SunniAuthority.ISNA
        CalculationMethod.MUSLIM_WORLD_LEAGUE -> SunniAuthority.MUSLIM_WORLD_LEAGUE
        CalculationMethod.KUWAIT -> SunniAuthority.KUWAIT
        CalculationMethod.GULF_REGION -> SunniAuthority.GULF_REGION
        CalculationMethod.DIYANET -> SunniAuthority.DIYANET
        CalculationMethod.JAKIM -> SunniAuthority.JAKIM
        CalculationMethod.MUIS -> SunniAuthority.MUIS
        CalculationMethod.KEMENAG -> SunniAuthority.KEMENAG
        CalculationMethod.MOONSIGHTING_COMMITTEE -> SunniAuthority.MOONSIGHTING_COMMITTEE
        CalculationMethod.UOIF -> SunniAuthority.UOIF
        CalculationMethod.SAMR -> SunniAuthority.SAMR
        CalculationMethod.JORDAN -> SunniAuthority.JORDAN
        CalculationMethod.TUNISIA -> SunniAuthority.TUNISIA
        CalculationMethod.TEHRAN -> SunniAuthority.TEHRAN
    }

    /** Manual selection wins; otherwise the country's authority is used. */
    fun resolve(countryCode: String?, override: CalculationMethod?): SunniAuthority =
        override?.let(::fromOverride) ?: fromCountryCode(countryCode)

    /**
     * Legacy (shared module) method -> app calculation method. The shared enum is a small
     * subset of the app's methods, so this is the only place that knows the old ids.
     */
    fun fromSharedCalculation(method: SharedCalculationMethod): CalculationMethod = when (method) {
        SharedCalculationMethod.MWL -> CalculationMethod.MUSLIM_WORLD_LEAGUE
        SharedCalculationMethod.ISNA -> CalculationMethod.NORTH_AMERICA
        SharedCalculationMethod.UMM_AL_QURA -> CalculationMethod.UMM_AL_QURA
        SharedCalculationMethod.EGYPTIAN -> CalculationMethod.EGYPTIAN
        SharedCalculationMethod.KARACHI -> CalculationMethod.KARACHI
        SharedCalculationMethod.TEHRAN -> CalculationMethod.TEHRAN
        SharedCalculationMethod.MAKKAH -> CalculationMethod.UMM_AL_QURA
    }

    /**
     * Reverse of [fromSharedCalculation] for the settings screen: the closest legacy method
     * for an app method, or null when the legacy enum has no equivalent. Built on the
     * authoritative [fromOverride] mapping so new app methods need no restatement here.
     */
    fun sharedCalculationFor(method: CalculationMethod): SharedCalculationMethod? =
        when (fromOverride(method)) {
            SunniAuthority.MUSLIM_WORLD_LEAGUE -> SharedCalculationMethod.MWL
            SunniAuthority.ISNA -> SharedCalculationMethod.ISNA
            SunniAuthority.UMM_AL_QURA -> SharedCalculationMethod.UMM_AL_QURA
            SunniAuthority.EGYPTIAN -> SharedCalculationMethod.EGYPTIAN
            SunniAuthority.KARACHI -> SharedCalculationMethod.KARACHI
            // Legacy settings had no Qatar/Dubai ids; both were stored as Umm Al-Qura.
            SunniAuthority.QATAR, SunniAuthority.DUBAI -> SharedCalculationMethod.UMM_AL_QURA
            else -> null
        }

    /** AlAdhan `school`: 0 = Shafi'i/Maliki/Hanbali (1:1 shadow), 1 = Hanafi (2:1). */
    fun schoolId(madhhab: Madhhab): Int =
        if (madhhab == Madhhab.HANAFI) 1 else 0

    /**
     * The closest local (offline) engine method for an authority, used to compute the
     * astronomical fallback with matching twilight angles when no cache or network is
     * available.
     */
    fun localCalculationMethod(authority: SunniAuthority): CalculationMethod =
        overridesByAuthority[authority] ?: CalculationMethod.MUSLIM_WORLD_LEAGUE

    /**
     * Reverse of [fromOverride], built from it rather than restated, so the two directions can
     * never drift apart when a calculation method is added.
     */
    private val overridesByAuthority: Map<SunniAuthority, CalculationMethod> by lazy {
        CalculationMethod.entries.associateBy(::fromOverride)
    }
}

/**
 * Small persistence seam so the prayer-time repository and the ViewModel can read the
 * user's manual method choice and last known country without depending on Android
 * SharedPreferences. A `null` override means "follow the country".
 */
interface PrayerMethodSettings {
    fun countryCode(): String?
    fun manualMethodOverride(): CalculationMethod?
    fun setManualMethodOverride(method: CalculationMethod?)

    /** Remembers the detected country so background alarm scheduling maps the same authority. */
    fun setCountryCode(code: String?)
}
