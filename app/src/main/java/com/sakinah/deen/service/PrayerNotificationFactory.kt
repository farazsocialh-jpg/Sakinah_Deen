package com.sakinah.deen.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sakinah.deen.MainActivity
import com.sakinah.deen.R
import com.sakinah.deen.data.model.PrayerAlertMode
import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prefs.SakinahPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds the rich prayer reminder card.
 *
 * Collapsed: prayer + window with a native [android.widget.Chronometer] counting down to the
 * end of the window (zero wake-ups, the platform animates it).
 * Expanded: fard rak'ah count, a progress bar for the remaining share of the window, and a
 * rotating Qur'an/Hadith reminder tailored to the prayer.
 *
 * Both states share the active app palette and a monochrome status-bar icon; Mute and
 * Mark as Prayed act through [PrayerNotificationActionReceiver].
 */
object PrayerNotificationFactory {

    data class Spec(
        val prayerType: PrayerType,
        val prayerName: String,
        val startMillis: Long,
        val endMillis: Long,
        val alertMode: PrayerAlertMode,
        val isTest: Boolean = false,
        /** Routes Fajr to the optional highest-priority channel (see [NotificationChannels]). */
        val criticalFajrChannel: Boolean = false
    )

    private const val MUTE_REQUEST_CODE = 4100
    private const val MARK_PRAYED_REQUEST_CODE = 4200
    private const val DISMISS_REQUEST_CODE = 4300
    private const val OPEN_APP_REQUEST_CODE = 4400

    private const val FALLBACK_WINDOW_MS = 10 * 60_000L

    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    fun build(context: Context, spec: Spec): Notification {
        val theme = NotificationAppearance.resolve(context)
        val zone = runCatching {
            AzaanScheduling.activeZoneId(SakinahPreferences(context))
        }.getOrDefault(ZoneId.systemDefault())

        val startMillis = spec.startMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        val endMillis = spec.endMillis.takeIf { it > startMillis } ?: (startMillis + FALLBACK_WINDOW_MS)
        val now = System.currentTimeMillis()

        val startText = formatTime(startMillis, zone)
        val endText = formatTime(endMillis, zone)
        val windowText = context.getString(R.string.notif_prayer_window, startText, endText)
        val collapsedTitle = if (spec.prayerType == PrayerType.SUNRISE) {
            "${spec.prayerName} · $startText"
        } else {
            "${spec.prayerName} · $windowText"
        }
        val expandedTitle = PrayerReminderContent.expandedTitle(spec.prayerType, spec.prayerName)
        val reminder = PrayerReminderContent.reminderFor(
            prayer = spec.prayerType,
            dayOfYear = LocalDate.now(zone).dayOfYear
        )

        val remainingFraction = ((endMillis - now).toDouble() / (endMillis - startMillis).toDouble())
            .coerceIn(0.0, 1.0)
        val percentRemaining = (remainingFraction * 100.0).toInt().coerceIn(0, 100)

        val contentIntent = PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val muteIntent = broadcastIntent(
            context = context,
            action = PrayerNotificationActionReceiver.ACTION_MUTE_ADHAN,
            prayerType = spec.prayerType,
            requestCode = MUTE_REQUEST_CODE + spec.prayerType.ordinal,
            kind = "mute"
        )
        val markPrayedIntent = broadcastIntent(
            context = context,
            action = PrayerNotificationActionReceiver.ACTION_MARK_PRAYED,
            prayerType = spec.prayerType,
            requestCode = MARK_PRAYED_REQUEST_CODE + spec.prayerType.ordinal,
            kind = "mark",
            includeDate = true
        )
        val dismissIntent = broadcastIntent(
            context = context,
            action = PrayerNotificationActionReceiver.ACTION_DISMISS_PRAYER,
            prayerType = spec.prayerType,
            requestCode = DISMISS_REQUEST_CODE + spec.prayerType.ordinal,
            kind = "dismiss"
        )

        val collapsed = collapsedViews(
            context = context,
            theme = theme,
            title = collapsedTitle,
            subtitle = context.getString(R.string.adhan_body),
            contentIntent = contentIntent
        )
        val expanded = expandedViews(
            context = context,
            theme = theme,
            title = expandedTitle,
            window = windowText,
            percentRemaining = percentRemaining,
            reminder = reminder,
            contentIntent = contentIntent
        )

        val channelId = NotificationChannels.channelFor(
            mode = spec.alertMode,
            prayerType = spec.prayerType,
            criticalFajrEnabled = spec.criticalFajrChannel
        )

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_app)
            .setContentTitle(collapsedTitle)
            .setContentText(windowText)
            .setContentIntent(contentIntent)
            .setDeleteIntent(dismissIntent)
            .setAutoCancel(false)
            .setOngoing(false)
            .setOnlyAlertOnce(spec.alertMode == PrayerAlertMode.SILENT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(
                if (spec.alertMode == PrayerAlertMode.SILENT) {
                    NotificationCompat.PRIORITY_DEFAULT
                } else {
                    NotificationCompat.PRIORITY_HIGH
                }
            )
            .setColor(theme.accent)
            .setColorized(false)
            .setWhen(endMillis)
            .setShowWhen(true)
            // Native countdown in the decorated header: no services, no wake-ups.
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .addAction(R.drawable.ic_stat_app, context.getString(R.string.adhan_action_mute), muteIntent)
            .addAction(R.drawable.ic_stat_app, context.getString(R.string.notif_action_mark_prayed), markPrayedIntent)
            .build()
    }

    private fun collapsedViews(
        context: Context,
        theme: PrayerNotificationTheme,
        title: String,
        subtitle: String,
        contentIntent: PendingIntent
    ): RemoteViews = RemoteViews(context.packageName, R.layout.notification_prayer_collapsed).apply {
        setTextViewText(R.id.notif_collapsed_title, title)
        setTextViewText(R.id.notif_collapsed_window, subtitle)
        setTextColor(R.id.notif_collapsed_title, theme.onSurface)
        setTextColor(R.id.notif_collapsed_window, theme.secondaryText)
        setOnClickPendingIntent(R.id.notif_root, contentIntent)
    }

    private fun expandedViews(
        context: Context,
        theme: PrayerNotificationTheme,
        title: String,
        window: String,
        percentRemaining: Int,
        reminder: PrayerReminderContent.Reminder,
        contentIntent: PendingIntent
    ): RemoteViews = RemoteViews(context.packageName, R.layout.notification_prayer_expanded).apply {
        setTextViewText(R.id.notif_expanded_title, title)
        setTextViewText(R.id.notif_expanded_window, window)
        setTextViewText(R.id.notif_reminder_arabic, reminder.arabic)
        setTextViewText(R.id.notif_reminder_text, reminder.english)
        setTextViewText(R.id.notif_reminder_source, reminder.source)
        setTextColor(R.id.notif_expanded_title, theme.onSurface)
        setTextColor(R.id.notif_expanded_window, theme.secondaryText)
        setTextColor(R.id.notif_reminder_arabic, theme.onSurface)
        setTextColor(R.id.notif_reminder_text, theme.secondaryText)
        setTextColor(R.id.notif_reminder_source, theme.secondaryText)
        setProgressBar(R.id.notif_progress, 100, percentRemaining, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setColorStateList(
                R.id.notif_progress,
                "setProgressTintList",
                ColorStateList.valueOf(theme.accent)
            )
            setColorStateList(
                R.id.notif_progress,
                "setProgressBackgroundTintList",
                ColorStateList.valueOf(theme.track)
            )
        }
        setOnClickPendingIntent(R.id.notif_root, contentIntent)
    }

    private fun broadcastIntent(
        context: Context,
        action: String,
        prayerType: PrayerType,
        requestCode: Int,
        kind: String,
        includeDate: Boolean = false
    ): PendingIntent {
        val epochDay = LocalDate.now(
            runCatching { AzaanScheduling.activeZoneId(SakinahPreferences(context)) }
                .getOrDefault(ZoneId.systemDefault())
        ).toEpochDay()
        val intent = Intent(context, PrayerNotificationActionReceiver::class.java).apply {
            this.action = action
            // The data URI keeps PendingIntents distinct per prayer and per day, so a stale
            // "Mark as Prayed" can never complete a different prayer or a previous date.
            data = Uri.parse(
                "sakinah://prayer_action/$kind/${prayerType.name}/${if (includeDate) epochDay else "current"}"
            )
            putExtra(PrayerNotificationActionReceiver.EXTRA_PRAYER_TYPE, prayerType.name)
            putExtra(PrayerNotificationActionReceiver.EXTRA_PRAYER_DATE, epochDay)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Chronometer counts against elapsedRealtime, so convert the wall-clock end time. */
    private fun chronometerBase(endMillis: Long, nowMillis: Long): Long =
        SystemClock.elapsedRealtime() + (endMillis - nowMillis).coerceAtLeast(0L)

    private fun formatTime(epochMillis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime().format(TIME_FORMAT)

    /** Full-colour launcher icon rasterised for the notification card's large icon slot. */
    private fun appIconBitmap(context: Context): Bitmap? = runCatching {
        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) ?: return null
        val size = context.resources.getDimensionPixelSize(
            android.R.dimen.notification_large_icon_width
        ).coerceAtLeast(192)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        bitmap
    }.getOrNull()
}
