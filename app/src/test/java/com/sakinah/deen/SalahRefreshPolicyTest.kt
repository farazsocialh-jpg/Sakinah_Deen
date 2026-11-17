package com.sakinah.deen

import com.sakinah.deen.domain.astronomy.SalahRefreshPolicy
import com.sakinah.deen.domain.astronomy.SalahRefreshReason
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The resume decision used to be implicit: granting location permission in system settings,
 * or landing in a new timezone, left the Salah table on the old values until a manual
 * pull-to-refresh. These cases pin the decision down without Android.
 */
class SalahRefreshPolicyTest {

    private fun decide(
        hadPermission: Boolean = true,
        hasPermission: Boolean = true,
        previousZone: String? = "Europe/London",
        currentZone: String? = "Europe/London",
        manual: Boolean = false
    ) = SalahRefreshPolicy.decide(
        hadPermission = hadPermission,
        hasPermission = hasPermission,
        previousSystemZone = previousZone,
        currentSystemZone = currentZone,
        isManualLocation = manual
    )

    @Test
    fun nothingChanged_doesNothing() {
        assertEquals(SalahRefreshReason.NONE, decide())
    }

    @Test
    fun permissionGrantedWhileBackgrounded_refreshes() {
        assertEquals(
            SalahRefreshReason.PERMISSION_GRANTED,
            decide(hadPermission = false, hasPermission = true)
        )
    }

    @Test
    fun permissionRevoked_stopsClaimingAFix() {
        assertEquals(
            SalahRefreshReason.PERMISSION_REVOKED,
            decide(hadPermission = true, hasPermission = false)
        )
    }

    @Test
    fun permissionChangesWinOverZoneChanges() {
        assertEquals(
            SalahRefreshReason.PERMISSION_GRANTED,
            decide(hadPermission = false, hasPermission = true, currentZone = "Asia/Qatar")
        )
        assertEquals(
            SalahRefreshReason.PERMISSION_REVOKED,
            decide(hadPermission = true, hasPermission = false, currentZone = "Asia/Qatar")
        )
    }

    @Test
    fun zoneChangeWithoutAManualCity_refreshes() {
        assertEquals(
            SalahRefreshReason.SYSTEM_ZONE_CHANGED,
            decide(currentZone = "Asia/Qatar")
        )
    }

    @Test
    fun zoneChangeWithAManualCity_keepsTheSavedLocationZone() {
        assertEquals(
            SalahRefreshReason.NONE,
            decide(currentZone = "Asia/Qatar", manual = true)
        )
    }

    @Test
    fun unknownZonesNeverTriggerARefresh() {
        assertEquals(SalahRefreshReason.NONE, decide(previousZone = null))
        assertEquals(SalahRefreshReason.NONE, decide(currentZone = null))
        assertEquals(SalahRefreshReason.NONE, decide(previousZone = "  "))
        assertEquals(SalahRefreshReason.NONE, decide(currentZone = ""))
    }
}
