package com.sakinah.deen.ui.screens

data class PrayerReminder(val source: String, val text: String)

val uncheckedPrayerReminders = listOf(
    PrayerReminder("Quran 4:103", "Indeed, prayer has been decreed upon the believers a decree of specified times."),
    PrayerReminder("Quran 2:238", "Guard strictly your prayers, especially the middle prayer, and stand before Allah with devotion."),
    PrayerReminder("Quran 29:45", "Establish prayer. Indeed, prayer prohibits immorality and wrongdoing, and the remembrance of Allah is greater."),
    PrayerReminder("Sahih al-Bukhari 527", "The most beloved deed to Allah is prayer at its proper time."),
    PrayerReminder("Jami' at-Tirmidhi 413", "The first matter that the servant will be brought to account for on the Day of Judgment is the prayer."),
    PrayerReminder("Sahih Muslim 82", "Between a man and disbelief is abandoning the prayer."),
    PrayerReminder("Sahih al-Bukhari 552", "Whoever misses the Asr prayer, it is as if he lost his family and property."),
    PrayerReminder("Quran 107:4-5", "Woe to those who pray, yet are heedless of their prayer."),
    PrayerReminder("Quran 19:59", "There came after them successors who neglected prayer and pursued desires; so they are going to meet evil."),
    PrayerReminder("Quran 74:42-43", "What put you into Hell? They will say: We were not of those who prayed.")
)

val checkedPrayerReminders = listOf(
    PrayerReminder("Sahih Muslim 667", "The five daily prayers erase the sins committed between them, so long as major sins are avoided."),
    PrayerReminder("Sahih al-Bukhari 528", "Five daily prayers wash away sins just like bathing in a pure river five times a day."),
    PrayerReminder("Sahih Muslim 223", "Prayer is a shining light for the believer."),
    PrayerReminder("Sahih al-Bukhari 662", "Whoever walks to the mosque in the darkness will be given complete light on the Day of Resurrection."),
    PrayerReminder("Quran 23:1-2, 9-11", "Successful are the believers who are humble in their prayers and guard them—they are the inheritors of Paradise."),
    PrayerReminder("Sahih Muslim 482", "The closest a servant comes to his Lord is while prostrating, so increase your supplication."),
    PrayerReminder("Sunan Abi Dawud 861", "Allah has promised Paradise to whoever guards the five daily prayers with their wudu, bowing, and prostration."),
    PrayerReminder("Sahih al-Bukhari 574", "Whoever prays Fajr and Asr will enter Paradise."),
    PrayerReminder("Quran 20:130", "Glorify your Lord before sunrise and before sunset, and in the hours of the night, that you may be well pleased."),
    PrayerReminder("Sahih Muslim 669", "When a believer stands to pray, sins are placed on his head and shoulders, and every bow and prostration shakes them off.")
)