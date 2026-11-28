package com.sakinah.deen

import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.service.AdhanPlaybackService
import com.sakinah.deen.service.PrayerReminderContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract tests for the stop-intent handling added to silence Adhan on volume/app-open. */
class AdhanStopIntentTest {

    @Test
    fun stopActionConstant_isStable() {
        assertEquals("com.sakinah.deen.ACTION_STOP_ADHAN", AdhanPlaybackService.ACTION_STOP_ADHAN)
    }

    @Test
    fun notificationTitle_formatIncludesPrayerName() {
        // Exercise the exact pure helper PrayerNotificationFactory.build() uses for its
        // expanded notification title, instead of asserting a locally built string.
        val title = PrayerReminderContent.expandedTitle(PrayerType.MAGHRIB, "Maghrib")
        assertEquals("Maghrib · 3 Rakat Fard", title)
        assertTrue(title.startsWith("Maghrib"))
    }
}
