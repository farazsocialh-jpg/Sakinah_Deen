package com.sakinah.deen.service

import java.util.concurrent.atomic.AtomicLong

/**
 * Deterministic alarm identities.
 *
 * A [android.app.PendingIntent] is matched on (request code, Intent action/data/component).
 * Previously the app used fixed integer request codes, so a test alarm and a snooze could
 * collide and overwrite each other's extras, and a later code change could orphan an armed
 * alarm that cancel() could no longer find.
 *
 * The identity is now the data URI, following the audit's cleaner suggestion:
 *
 *  - Daily prayers: `sakinah://alarm/prayer/FAJR` — stable, so scheduling and cancellation
 *    always rebuild byte-identical intents (idempotent re-arm and idempotent cancel).
 *  - Test / snooze alarms: `sakinah://alarm/test/DHUHR/<trigger>-<sequence>` — a monotonically
 *    increasing sequence plus the trigger instant, so two alarms armed in the same millisecond
 *    can never share an identity.
 *
 * No Android types are used here so the whole scheme is unit-testable on the JVM.
 */
object AlarmIdentity {

    const val URI_PREFIX = "sakinah://alarm/"

    const val KIND_PRAYER = "prayer"
    const val KIND_SNOOZE = "snooze"
    const val KIND_TEST = "test"

    /** Process-wide monotonic counter; only ever increases within a run. */
    private val sequence = AtomicLong(0L)

    /** Stable identity for a prayer's daily alarm (one armed slot per prayer). */
    fun prayerKey(prayerName: String): String = "$KIND_PRAYER/${normalize(prayerName)}"

    /** Fresh identity for a snooze re-fire; unique even for back-to-back requests. */
    fun snoozeKey(prayerName: String, triggerAtMillis: Long): String =
        uniqueKey(KIND_SNOOZE, prayerName, triggerAtMillis)

    /** Fresh identity for a self-test alert; unique even for back-to-back requests. */
    fun testKey(prayerName: String, triggerAtMillis: Long): String =
        uniqueKey(KIND_TEST, prayerName, triggerAtMillis)

    private fun uniqueKey(kind: String, prayerName: String, triggerAtMillis: Long): String =
        "$kind/${normalize(prayerName)}/$triggerAtMillis-${sequence.incrementAndGet()}"

    /** Uppercase, space-free token: enum names, plus defensive handling of free-form input. */
    fun normalize(prayerName: String): String =
        prayerName.trim().uppercase().replace(' ', '_').ifBlank { "SALAH" }

    /** The data URI that carries the identity into the Intent. */
    fun uri(key: String): String = URI_PREFIX + key

    /**
     * Deterministic request code derived from the same key. A hash collision is harmless: the
     * data URI still differs, so the two PendingIntents remain distinct.
     */
    fun requestCode(key: String): Int = key.hashCode() and 0x7FFF_FFFF

    /**
     * Trigger instant encoded in a key, when it has one (test/snooze). Prayer keys are stable
     * and never expire, so they return null and are always retained.
     */
    fun triggerTimestamp(key: String): Long? =
        // The encoded form is "<instant>-<sequence>"; split on the *last* dash so a negative
        // instant or an unexpected separator cannot silently disable pruning.
        key.substringAfterLast('/').substringBeforeLast('-').toLongOrNull()

    /**
     * Drops registry entries whose alarm already fired (plus [graceMillis]) so a long-lived
     * install never accumulates unbounded cancellation work.
     */
    fun pruneExpired(
        keys: Collection<String>,
        nowMillis: Long,
        graceMillis: Long
    ): List<String> = keys.filter { key ->
        val triggerAt = triggerTimestamp(key)
        triggerAt == null || triggerAt + graceMillis >= nowMillis
    }

    /** Serialises the set of outstanding one-off alarm keys for persistence. */
    fun encode(keys: Collection<String>): String =
        keys.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .sorted()
            .joinToString("\n")

    fun decode(raw: String?): List<String> =
        raw?.split('\n')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.distinct()
            ?.sorted()
            ?: emptyList()
}
