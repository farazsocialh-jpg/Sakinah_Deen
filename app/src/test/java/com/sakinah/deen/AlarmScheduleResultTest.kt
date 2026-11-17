package com.sakinah.deen

import com.sakinah.deen.service.AlarmSchedulePreflight
import com.sakinah.deen.service.AlarmScheduleResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings screen used to print "scheduled" unconditionally (#28–#30). Every branch of the
 * preflight table is pinned here so the UI can only claim what really happened.
 */
class AlarmScheduleResultTest {

    private val now = 1_700_000_000_000L
    private val later = now + 5_000L

    private fun evaluate(
        alarmManagerAvailable: Boolean = true,
        alarmsEnabled: Boolean = true,
        notificationsGranted: Boolean = true,
        exactAlarmsAllowed: Boolean = true,
        triggerAtMillis: Long = later
    ) = AlarmSchedulePreflight.evaluate(
        alarmManagerAvailable = alarmManagerAvailable,
        alarmsEnabled = alarmsEnabled,
        notificationsGranted = notificationsGranted,
        exactAlarmsAllowed = exactAlarmsAllowed,
        triggerAtMillis = triggerAtMillis,
        nowMillis = now
    )

    @Test
    fun healthyDevice_isClearToSchedule() {
        assertNull(evaluate())
    }

    @Test
    fun missingAlarmManager_isUnsupported() {
        assertEquals(AlarmScheduleResult.Unsupported, evaluate(alarmManagerAvailable = false))
    }

    @Test
    fun azaanTurnedOff_failsWithAReason() {
        val result = evaluate(alarmsEnabled = false)
        assertTrue(result is AlarmScheduleResult.Failed)
        assertTrue((result as AlarmScheduleResult.Failed).reason.contains("turned off"))
    }

    @Test
    fun elapsedTriggerTime_isRejectedInsteadOfArmed() {
        val result = evaluate(triggerAtMillis = now)
        assertTrue(result is AlarmScheduleResult.Failed)
        assertTrue((result as AlarmScheduleResult.Failed).reason.contains("passed"))
    }

    @Test
    fun blockedNotifications_reportPermissionMissing() {
        assertEquals(
            AlarmScheduleResult.PermissionMissing,
            evaluate(notificationsGranted = false)
        )
    }

    @Test
    fun deniedExactAlarms_reportTheDegradedResult() {
        assertEquals(
            AlarmScheduleResult.ExactAlarmDenied,
            evaluate(exactAlarmsAllowed = false)
        )
    }

    @Test
    fun isArmed_onlyForRealSchedulingOutcomes() {
        assertTrue(AlarmScheduleResult.Scheduled(later).isArmed)
        assertTrue(AlarmScheduleResult.ExactAlarmDenied.isArmed)
        assertFalse(AlarmScheduleResult.PermissionMissing.isArmed)
        assertFalse(AlarmScheduleResult.Unsupported.isArmed)
        assertFalse(AlarmScheduleResult.Failed("nope").isArmed)
    }

    @Test
    fun codesAreStableAndRoundTrip() {
        assertEquals(AlarmScheduleResult.CODE_SCHEDULED, AlarmScheduleResult.Scheduled(later).code)
        assertEquals(AlarmScheduleResult.CODE_EXACT_DENIED, AlarmScheduleResult.ExactAlarmDenied.code)
        assertEquals(
            AlarmScheduleResult.CODE_PERMISSION_MISSING,
            AlarmScheduleResult.PermissionMissing.code
        )
        assertEquals(AlarmScheduleResult.CODE_UNSUPPORTED, AlarmScheduleResult.Unsupported.code)
        assertEquals(AlarmScheduleResult.CODE_FAILED, AlarmScheduleResult.Failed("x").code)

        assertEquals(
            AlarmScheduleResult.Scheduled(later),
            AlarmScheduleResult.fromCode(AlarmScheduleResult.CODE_SCHEDULED, later)
        )
        assertEquals(
            AlarmScheduleResult.ExactAlarmDenied,
            AlarmScheduleResult.fromCode(AlarmScheduleResult.CODE_EXACT_DENIED)
        )
        assertNull(AlarmScheduleResult.fromCode(null))
        assertNull(AlarmScheduleResult.fromCode("something-else"))
    }
}
