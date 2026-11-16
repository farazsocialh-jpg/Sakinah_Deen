package com.sakinah.deen.service

import com.sakinah.deen.data.model.PrayerType

/**
 * Curated, authentic Qur'an and Hadith reminders shown in the expanded prayer notification.
 *
 * One reminder per prayer per day: the index rotates with the day of the year and the
 * prayer's ordinal, so the same prayer shows a different reminder tomorrow and every
 * prayer of the day shows a different one. Content is static — no network, no database.
 */
object PrayerReminderContent {

    data class Reminder(
        val arabic: String,
        val english: String,
        val source: String
    )

    private val FAJR = listOf(
        Reminder(
            arabic = "أَقِمِ الصَّلَاةَ لِدُلُوكِ الشَّمْسِ إِلَىٰ غَسَقِ اللَّيْلِ وَقُرْآنَ الْفَجْرِ ۖ إِنَّ قُرْآنَ الْفَجْرِ كَانَ مَشْهُودًا",
            english = "Establish prayer at the decline of the sun until the darkness of the night, and the Qur'an of dawn. Indeed, the recitation of dawn is ever witnessed.",
            source = "Qur'an 17:78"
        ),
        Reminder(
            arabic = "مَنْ صَلَّى الصُّبْحَ فَهُوَ فِي ذِمَّةِ اللَّهِ",
            english = "Whoever prays the dawn prayer is under the protection of Allah.",
            source = "Sahih Muslim 657"
        ),
        Reminder(
            arabic = "رَكْعَتَا الْفَجْرِ خَيْرٌ مِنَ الدُّنْيَا وَمَا فِيهَا",
            english = "The two rak'ahs before Fajr are better than the world and all it contains.",
            source = "Sahih Muslim 725"
        ),
        Reminder(
            arabic = "وَسَبِّحْ بِحَمْدِ رَبِّكَ قَبْلَ طُلُوعِ الشَّمْسِ وَقَبْلَ الْغُرُوبِ",
            english = "And exalt Him with praise of your Lord before the rising of the sun and before its setting.",
            source = "Qur'an 50:39"
        )
    )

    private val DHUHR = listOf(
        Reminder(
            arabic = "إِنَّنِي أَنَا اللَّهُ لَا إِلَٰهَ إِلَّا أَنَا فَاعْبُدْنِي وَأَقِمِ الصَّلَاةَ لِذِكْرِي",
            english = "Indeed, I am Allah. There is no deity except Me, so worship Me and establish prayer for My remembrance.",
            source = "Qur'an 20:14"
        ),
        Reminder(
            arabic = "قُرَّةُ عَيْنِي فِي الصَّلَاةِ",
            english = "The coolness of my eyes lies in prayer.",
            source = "Sunan an-Nasa'i 3939 (hasan)"
        ),
        Reminder(
            arabic = "إِنَّ أَوَّلَ مَا يُحَاسَبُ بِهِ الْعَبْدُ يَوْمَ الْقِيَامَةِ الصَّلَاةُ",
            english = "The first thing a servant will be asked about on the Day of Resurrection is the prayer.",
            source = "Sunan Abi Dawud 864 · Jami' at-Tirmidhi 413"
        ),
        Reminder(
            arabic = "حَافِظُوا عَلَى الصَّلَوَاتِ وَالصَّلَاةِ الْوُسْطَىٰ وَقُومُوا لِلَّهِ قَانِتِينَ",
            english = "Guard strictly the prayers, especially the middle prayer, and stand before Allah devoutly obedient.",
            source = "Qur'an 2:238"
        )
    )

    private val ASR = listOf(
        Reminder(
            arabic = "مَنْ تَرَكَ صَلَاةَ الْعَصْرِ فَقَدْ حَبِطَ عَمَلُهُ",
            english = "Whoever misses the Asr prayer, his deeds are rendered void.",
            source = "Sahih al-Bukhari 552"
        ),
        Reminder(
            arabic = "مَنْ صَلَّى الْبَرْدَيْنِ دَخَلَ الْجَنَّةَ",
            english = "Whoever prays the two cool prayers — Fajr and Asr — will enter Paradise.",
            source = "Sahih al-Bukhari 574"
        ),
        Reminder(
            arabic = "وَالْعَصْرِ ۙ إِنَّ الْإِنسَانَ لَفِي خُسْرٍ إِلَّا الَّذِينَ آمَنُوا وَعَمِلُوا الصَّالِحَاتِ",
            english = "By time, indeed mankind is in loss — except those who believe and do righteous deeds.",
            source = "Qur'an 103:1-3"
        ),
        Reminder(
            arabic = "ذَلِكَ وَمَن يُعَظِّمْ شَعَائِرَ اللَّهِ فَإِنَّهَا مِن تَقْوَى الْقُلُوبِ",
            english = "Whoever honours the symbols of Allah — indeed, it is from the piety of hearts.",
            source = "Qur'an 22:32"
        )
    )

    private val MAGHRIB = listOf(
        Reminder(
            arabic = "فَسُبْحَانَ اللَّهِ حِينَ تُمْسُونَ وَحِينَ تُصْبِحُونَ",
            english = "So exalted is Allah when you reach the evening and when you reach the morning.",
            source = "Qur'an 30:17"
        ),
        Reminder(
            arabic = "الْحَمْدُ لِلَّهِ الَّذِي أَذْهَبَ عَنَّا الْحَزَنَ",
            english = "Maghrib arrives with the close of the day: praise Him who removes all grief from you.",
            source = "Qur'an 35:34 (reflection)"
        ),
        Reminder(
            arabic = "وَأَقِمِ الصَّلَاةَ طَرَفَيِ النَّهَارِ وَزُلَفًا مِّنَ اللَّيْلِ ۚ إِنَّ الْحَسَنَاتِ يُذْهِبْنَ السَّيِّئَاتِ",
            english = "Establish prayer at the two ends of the day and at the approach of the night. Indeed, good deeds erase bad deeds.",
            source = "Qur'an 11:114"
        ),
        Reminder(
            arabic = "أَحَبُّ الْأَعْمَالِ إِلَى اللَّهِ الصَّلَاةُ عَلَى وَقْتِهَا",
            english = "The deed most beloved to Allah is prayer offered at its proper time.",
            source = "Sahih al-Bukhari 527"
        )
    )

    private val ISHA = listOf(
        Reminder(
            arabic = "مَنْ صَلَّى الْعِشَاءَ فِي جَمَاعَةٍ فَكَأَنَّمَا صَلَّى نِصْفَ اللَّيْلِ",
            english = "Whoever prays Isha in congregation is as though he prayed half the night.",
            source = "Sahih Muslim 656"
        ),
        Reminder(
            arabic = "وَمِنَ اللَّيْلِ فَتَهَجَّدْ بِهِ نَافِلَةً لَّكَ عَسَىٰ أَن يَبْعَثَكَ رَبُّكَ مَقَامًا مَّحْمُودًا",
            english = "And in the night, arise from sleep for prayer as an extra duty for you; perhaps your Lord will raise you to a praiseworthy station.",
            source = "Qur'an 17:79"
        ),
        Reminder(
            arabic = "إِنَّ أَثْقَلَ الصَّلَاةِ عَلَى الْمُنَافِقِينَ الْعِشَاءُ وَالْفَجْرُ",
            english = "The most burdensome prayers for the hypocrites are Isha and Fajr.",
            source = "Sahih al-Bukhari 657"
        ),
        Reminder(
            arabic = "قُمِ اللَّيْلَ إِلَّا قَلِيلًا ۝ نِّصْفَهُ أَوِ انقُصْ مِنْهُ قَلِيلًا",
            english = "Stand the night, except a little — half of it, or a little less.",
            source = "Qur'an 73:2-3"
        )
    )

    /** Reminders for a prayer; sunrise is a window marker and has no prayer reminder pool. */
    fun remindersFor(prayer: PrayerType): List<Reminder> = when (prayer) {
        PrayerType.FAJR -> FAJR
        PrayerType.DHUHR -> DHUHR
        PrayerType.ASR -> ASR
        PrayerType.MAGHRIB -> MAGHRIB
        PrayerType.ISHA -> ISHA
        PrayerType.SUNRISE -> FAJR
    }

    /**
     * Rotating reminder for [dayOfYear] so any given prayer changes its reminder daily,
     * and every prayer of the same day shows a different one.
     */
    fun reminderFor(prayer: PrayerType, dayOfYear: Int): Reminder {
        val pool = remindersFor(prayer)
        val index = Math.floorMod(dayOfYear + prayer.ordinal, pool.size)
        return pool[index]
    }

    /** Obligatory rak'ah count shown in the expanded title. */
    fun fardRakat(prayer: PrayerType): Int = when (prayer) {
        PrayerType.FAJR -> 2
        PrayerType.DHUHR -> 4
        PrayerType.ASR -> 4
        PrayerType.MAGHRIB -> 3
        PrayerType.ISHA -> 4
        PrayerType.SUNRISE -> 0
    }

    /** "Maghrib · 3 Rakat Fard" for the five daily prayers, plain name for markers. */
    fun expandedTitle(prayer: PrayerType, fallbackName: String): String {
        val rakats = fardRakat(prayer)
        return if (rakats > 0) "$fallbackName · $rakats Rakat Fard" else fallbackName
    }
}
