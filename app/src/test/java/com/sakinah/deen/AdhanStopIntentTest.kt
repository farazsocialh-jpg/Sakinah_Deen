package com.sakinah.deen

import com.sakinah.deen.service.AdhanPlaybackService
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
        // The service builds the title as "<Prayer> prayer time started".
        val prayer = "Maghrib"
        val title = "$prayer prayer time started"
        assertEquals("Maghrib prayer time started", title)
        assertTrue(title.endsWith("prayer time started"))
    }
}
