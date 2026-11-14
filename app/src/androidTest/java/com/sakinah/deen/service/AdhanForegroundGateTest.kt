package com.sakinah.deen.service

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sakinah.deen.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for the Azaan foreground-service fallback.
 *
 * The service promotes itself with `startForeground`; Android 12+ can refuse that with
 * [ForegroundServiceStartNotAllowedException] and Android 14+ adds media-FGS requirements.
 * [AdhanForegroundGate] must turn every refusal into a posted reminder notification instead of
 * crashing the process, so these tests drive the gate against the real NotificationManager.
 */
@RunWith(AndroidJUnit4::class)
class AdhanForegroundGateTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private lateinit var notificationManager: NotificationManager
    private lateinit var gate: AdhanForegroundGate

    @Before
    fun setUp() {
        grantNotificationPermission()
        notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Azaan test", NotificationManager.IMPORTANCE_HIGH)
        )
        clearNotifications()
        gate = AdhanForegroundGate(context)
    }

    @After
    fun tearDown() {
        clearNotifications()
    }

    @Test
    fun successfulPromotionDoesNotPostASecondReminder() {
        var attempts = 0

        val promoted = gate.enter(reminderNotification()) { attempts++ }

        assertTrue("the gate must report a promoted service", promoted)
        assertEquals(1, attempts)
        assertFalse(
            "no fallback notification may be posted when the service reached the foreground",
            isReminderPosted()
        )
    }

    @Test
    fun refusedForegroundStartPostsReminderInsteadOfCrashing() {
        assumeTrue(
            "ForegroundServiceStartNotAllowedException requires Android 12+",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        )
        val promoted = gate.enter(reminderNotification()) {
            throw ForegroundServiceStartNotAllowedException("FGS start blocked (test)")
        }

        assertFalse("a refused start must stop the service", promoted)
        assertTrue(
            "the prayer reminder must still be posted when the FGS start is refused",
            awaitReminder()
        )
    }

    @Test
    fun unexpectedStartFailureAlsoFallsBackToANotification() {
        val promoted = gate.enter(reminderNotification()) {
            throw SecurityException("Missing foreground service permission (test)")
        }

        assertFalse(promoted)
        assertTrue(awaitReminder())
    }

    @Test
    fun blockedNotificationsDoNotCrashTheFallback() {
        assumeTrue(
            "POST_NOTIFICATIONS exists from Android 13",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        )
        instrumentation.uiAutomation.revokeRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS
        )
        try {
            assertFalse(
                "the gate must report that nothing was posted while notifications are blocked",
                gate.postReminderNotification(reminderNotification())
            )
            val promoted = gate.enter(reminderNotification()) {
                throw IllegalStateException("FGS start blocked (test)")
            }
            assertFalse(promoted)
            assertFalse(isReminderPosted())
        } finally {
            grantNotificationPermission()
        }
    }

    @Test
    fun reminderUsesThePrayerNotificationId() {
        gate.postReminderNotification(reminderNotification())

        assertTrue(awaitReminder())
        assertTrue(
            "the reminder must reuse the Azaan notification id so it replaces the foreground one",
            isReminderPosted()
        )
    }

    private fun reminderNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Test prayer time started")
            .setContentText("Instrumented Azaan fallback")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

    private fun isReminderPosted(): Boolean = runCatching {
        notificationManager.activeNotifications.any {
            it.id == AdhanPlaybackService.NOTIFICATION_ID
        }
    }.getOrDefault(false)

    private fun awaitReminder(timeoutMs: Long = 3_000L): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isReminderPosted()) return true
            Thread.sleep(50)
        }
        return isReminderPosted()
    }

    private fun clearNotifications() {
        runCatching { NotificationManagerCompat.from(context).cancelAll() }
    }

    private fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "sakinah_azaan_test"
    }
}
