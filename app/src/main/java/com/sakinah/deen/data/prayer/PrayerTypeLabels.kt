package com.sakinah.deen.data.prayer

import com.sakinah.deen.data.model.PrayerType

/**
 * Canonical prayer labels. The English names double as the keys AlAdhan returns in its
 * `timings` object, so API parsing, notifications and the UI all read the same mapping
 * instead of each keeping a private copy of it.
 */
val PrayerType.englishName: String
    get() = when (this) {
        PrayerType.FAJR -> "Fajr"
        PrayerType.SUNRISE -> "Sunrise"
        PrayerType.DHUHR -> "Dhuhr"
        PrayerType.ASR -> "Asr"
        PrayerType.MAGHRIB -> "Maghrib"
        PrayerType.ISHA -> "Isha"
    }

/** Arabic label shown next to the localized countdown on the Salah screen. */
val PrayerType.arabicName: String
    get() = when (this) {
        PrayerType.FAJR -> "الفجر"
        PrayerType.SUNRISE -> "الشروق"
        PrayerType.DHUHR -> "الظهر"
        PrayerType.ASR -> "العصر"
        PrayerType.MAGHRIB -> "المغرب"
        PrayerType.ISHA -> "العشاء"
    }

/**
 * Jumu'ah replaces Dhuhr on Fridays. Apps that show a calculated Dhuhr time on Friday relabel
 * that same window as Jummah; the exact congregation/khutbah time remains mosque-specific.
 */
fun PrayerType.displayEnglishName(isFriday: Boolean = false): String =
    if (isFriday && this == PrayerType.DHUHR) "Jummah" else englishName

fun PrayerType.displayArabicName(isFriday: Boolean = false): String =
    if (isFriday && this == PrayerType.DHUHR) "الجمعة" else arabicName
