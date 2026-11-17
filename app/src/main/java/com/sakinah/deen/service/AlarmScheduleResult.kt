package com.sakinah.deen.service

/**
 * Outcome of asking the platform to arm a prayer alarm.
 *
 * The UI must never claim "scheduled" unless one of the armed states was returned:
 *
 *  - [Scheduled]: an exact `setExactAndAllowWhileIdle` alarm is registered for [triggerTimeMillis].
 *  - [ExactAlarmDenied]: an alarm is registered, but only as an inexact idle-safe alarm because
 *    the user has not granted `SCHEDULE_EXACT_ALARM`. Delivery can therefore be late.
 *  - [PermissionMissing]: `POST_NOTIFICATIONS` is blocked, so nothing was armed (a reminder that
 *    can never be shown must not be reported as pending).
 *  - [Failed]: nothing was armed; [reason] explains why in plain language.
 *  - [Unsupported]: the device exposes no [android.app.AlarmManager] for this context.
 */
sealed interface AlarmScheduleResult {

    /** True only when an alarm is actually registered with AlarmManager. */
    val isArmed: Boolean
        get() = this is Scheduled || this is ExactAlarmDenied

    /** Stable, loggable identifier (also persisted for the in-app self-test). */
    val code: String
        get() = when (this) {
            is Scheduled -> CODE_SCHEDULED
            ExactAlarmDenied -> CODE_EXACT_DENIED
            PermissionMissing -> CODE_PERMISSION_MISSING
            is Failed -> CODE_FAILED
            Unsupported -> CODE_UNSUPPORTED
        }

    data class Scheduled(val triggerTimeMillis: Long) : AlarmScheduleResult

    data object PermissionMissing : AlarmScheduleResult

    /**
     * Armed, but without exact-alarm permission. The alarm still fires (idle-safe), just not on
     * the minute, so the caller can surface an actionable warning.
     */
    data object ExactAlarmDenied : AlarmScheduleResult

    data class Failed(val reason: String) : AlarmScheduleResult

    data object Unsupported : AlarmScheduleResult

    companion object {
        const val CODE_SCHEDULED = "SCHEDULED"
        const val CODE_EXACT_DENIED = "EXACT_ALARM_DENIED"
        const val CODE_PERMISSION_MISSING = "PERMISSION_MISSING"
        const val CODE_FAILED = "FAILED"
        const val CODE_UNSUPPORTED = "UNSUPPORTED"

        /** Parses a persisted [code] back into a coarse result for the self-test card. */
        fun fromCode(code: String?, triggerTimeMillis: Long = 0L): AlarmScheduleResult? =
            when (code) {
                CODE_SCHEDULED -> Scheduled(triggerTimeMillis)
                CODE_EXACT_DENIED -> ExactAlarmDenied
                CODE_PERMISSION_MISSING -> PermissionMissing
                CODE_UNSUPPORTED -> Unsupported
                CODE_FAILED -> Failed("The last scheduling attempt failed")
                else -> null
            }
    }
}

/**
 * Pure decision table shared by every alarm entry point.
 *
 * Returns `null` when the alarm may be armed, otherwise the honest reason it must not be (or
 * the warning that it can only be inexact). Keeping this framework-free makes every branch
 * unit-testable without an Android runtime.
 */
object AlarmSchedulePreflight {

    fun evaluate(
        alarmManagerAvailable: Boolean,
        alarmsEnabled: Boolean,
        notificationsGranted: Boolean,
        exactAlarmsAllowed: Boolean,
        triggerAtMillis: Long,
        nowMillis: Long
    ): AlarmScheduleResult? = when {
        !alarmManagerAvailable -> AlarmScheduleResult.Unsupported
        !alarmsEnabled -> AlarmScheduleResult.Failed("Azaan is turned off")
        triggerAtMillis <= nowMillis -> AlarmScheduleResult.Failed("That time has already passed")
        !notificationsGranted -> AlarmScheduleResult.PermissionMissing
        // Still arm the reminder, but report the degraded precision truthfully.
        !exactAlarmsAllowed -> AlarmScheduleResult.ExactAlarmDenied
        else -> null
    }
}
