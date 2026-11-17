package com.sakinah.deen.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakinah.deen.data.model.PrayerModel
import com.sakinah.deen.data.prefs.SalahHomeLayout
import com.sakinah.deen.ui.components.SegmentedPillBar
import com.sakinah.deen.ui.viewmodel.SalahUiState
import kotlin.math.roundToInt

internal val SalahEmerald = Color(0xFF22C55E)
internal val SalahAmber = Color(0xFFF59E0B)
internal val SalahCoral = Color(0xFFEF4444)

internal data class SalahHeroMetrics(
    val remainingFraction: Float,
    val remainingPercent: Int,
    val bucketIndex: Int,
    val animatedProgress: Float
)

@Composable
internal fun rememberSalahHeroMetrics(state: SalahUiState): SalahHeroMetrics {
    val remainingFraction = (state.remainingMillis.toFloat() / state.totalWindowMillis.coerceAtLeast(1L))
        .coerceIn(0f, 1f)
    val remainingPercent = (remainingFraction * 100f).roundToInt().coerceIn(0, 100)
    val bucketIndex = ((100 - remainingPercent).coerceIn(0, 99)) / 10
    val animatedProgress by animateFloatAsState(
        targetValue = remainingFraction,
        label = "salahHeroProgress"
    )
    return SalahHeroMetrics(
        remainingFraction = remainingFraction,
        remainingPercent = remainingPercent,
        bucketIndex = bucketIndex,
        animatedProgress = animatedProgress
    )
}

@Composable
private fun progressBrush(completed: Boolean, remainingPercent: Int): Brush = when {
    completed -> Brush.horizontalGradient(listOf(SalahEmerald, Color(0xFF4ADE80)))
    remainingPercent <= 20 -> Brush.horizontalGradient(listOf(SalahAmber, SalahCoral))
    else -> Brush.horizontalGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
    )
}

@Composable
private fun accentColor(completed: Boolean, remainingPercent: Int): Color = when {
    completed -> SalahEmerald
    remainingPercent <= 20 -> SalahAmber
    else -> MaterialTheme.colorScheme.primary
}

@Composable
internal fun SalahTopHeader(
    state: SalahUiState,
    focusMode: Boolean,
    onPickLocation: () -> Unit,
    onOpenNotifications: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    onOpenQadha: () -> Unit,
    onRecalculate: (() -> Unit)? = null
) {
    val shortDate = state.gregorianDate.substringAfter("·", state.gregorianDate).trim()
        .ifBlank { state.gregorianDate }
    val cityShort = state.locationName.substringBefore(",").trim().ifBlank { "Location" }
    val methodShort = state.authorityName.ifBlank { state.sourceLabel }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onPickLocation)
        ) {
            Text(
                text = state.hijriDate,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "$shortDate · $cityShort ▾ · $methodShort",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!focusMode) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onOpenQadha)
                ) {
                    Text(
                        text = "Qadha",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            if (onOpenNotifications != null) {
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(36.dp)
                        .clickable(onClick = onOpenNotifications)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Azaan & Notifications",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            if (onRecalculate != null) {
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(36.dp)
                        .clickable(enabled = !state.isRefreshing, onClick = onRecalculate)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Recalculate today's times",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            if (!focusMode) {
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(36.dp)
                        .clickable(onClick = onOpenSettings)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "Prayer Settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SalahTabBar(
    selectedSegment: Int,
    onSelect: (Int) -> Unit,
    underline: Boolean = false
) {
    val tabs = listOf("Times", "Qibla", "Guide")
    if (!underline) {
        SegmentedPillBar(
            options = tabs,
            selectedIndex = selectedSegment,
            onSelect = onSelect
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = index == selectedSegment
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                )
            }
        }
    }
}

@Composable
internal fun PrayerCheckCircle(
    completed: Boolean,
    onToggle: () -> Unit,
    size: Dp = 44.dp
) {
    Surface(
        shape = CircleShape,
        color = if (completed) SalahEmerald else MaterialTheme.colorScheme.surfaceVariant,
        border = if (completed) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .size(size)
            .clickable(onClick = onToggle)
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = if (completed) "Prayer completed" else "Mark prayer complete",
                tint = if (completed) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.48f)
            )
        }
    }
}

@Composable
internal fun DrainingBar(
    progress: Float,
    completed: Boolean,
    remainingPercent: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .clip(RoundedCornerShape(50))
                .background(progressBrush(completed, remainingPercent))
        )
    }
}

@Composable
internal fun ReminderCallout(
    completed: Boolean,
    bucketIndex: Int,
    remainingPercent: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (completed) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accentColor(completed, remainingPercent))
            )
            AnimatedContent(
                targetState = Pair(completed, bucketIndex),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "salahReminder"
            ) { (checked, bucket) ->
                val reminders = if (checked) checkedPrayerReminders else uncheckedPrayerReminders
                val reminder = reminders[bucket.coerceIn(0, reminders.lastIndex)]
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = reminder.source.uppercase(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        text = "\"${reminder.text}\"",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun NextMetaRow(
    state: SalahUiState,
    completed: Boolean,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "NEXT: ${state.nextPrayerName.uppercase()}",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = if (completed) SalahEmerald else MaterialTheme.colorScheme.primary
        )
        trailing()
    }
}

@Composable
private fun ClockPill(time: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = time.ifBlank { "--:--" },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CountdownRow(
    state: SalahUiState,
    metrics: SalahHeroMetrics,
    completed: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${state.countdownText} left",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "${metrics.remainingPercent}%",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (completed) SalahEmerald else MaterialTheme.colorScheme.primary
        )
    }
}

private fun heroTitle(state: SalahUiState): String {
    val override = state.heroTitleOverride
    if (!override.isNullOrBlank()) return override
    return listOfNotNull(
        state.currentPrayerName.takeIf { it.isNotBlank() },
        state.currentPrayerArabic.takeIf { it.isNotBlank() }
    ).joinToString(" · ").uppercase()
}

@Composable
internal fun ClassicHeroCard(
    state: SalahUiState,
    onToggleCurrent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = rememberSalahHeroMetrics(state)
    val completed = state.isCurrentPrayerCompleted
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (completed) SalahEmerald.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!state.isDuhaWindow) {
                NextMetaRow(state, completed) {
                    ClockPill(state.nextPrayerStartTimeFormatted)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = heroTitle(state),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                PrayerCheckCircle(completed, onToggleCurrent)
            }
            DrainingBar(metrics.animatedProgress, completed, metrics.remainingPercent)
            CountdownRow(state, metrics, completed)
            ReminderCallout(completed, metrics.bucketIndex, metrics.remainingPercent)
        }
    }
}

@Composable
internal fun BentoHero(
    state: SalahUiState,
    onToggleCurrent: () -> Unit
) {
    val metrics = rememberSalahHeroMetrics(state)
    val completed = state.isCurrentPrayerCompleted

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(0.64f),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NOW",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (completed) SalahEmerald else MaterialTheme.colorScheme.primary
                        )
                        PrayerCheckCircle(completed, onToggleCurrent, size = 40.dp)
                    }
                    Text(
                        text = heroTitle(state),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    DrainingBar(metrics.animatedProgress, completed, metrics.remainingPercent)
                    Text(
                        text = "${state.countdownText} left",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Surface(
                modifier = Modifier.weight(0.36f),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "NEXT PRAYER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                    Text(
                        text = state.nextPrayerName.uppercase(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.nextPrayerStartTimeFormatted.ifBlank { "--:--" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = state.hijriDate,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        ReminderCallout(completed, metrics.bucketIndex, metrics.remainingPercent)
    }
}

@Composable
internal fun CircularHero(
    state: SalahUiState,
    onToggleCurrent: () -> Unit
) {
    val metrics = rememberSalahHeroMetrics(state)
    val completed = state.isCurrentPrayerCompleted
    val arcColor = if (completed) SalahEmerald
    else if (metrics.remainingPercent <= 20) SalahCoral
    else MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!state.isDuhaWindow) {
                    Text(
                        text = "NEXT: ${state.nextPrayerName.uppercase()} · ${state.nextPrayerStartTimeFormatted.ifBlank { "--:--" }}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (completed) SalahEmerald else MaterialTheme.colorScheme.primary
                    )
                }
                PrayerCheckCircle(completed, onToggleCurrent, size = 40.dp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                        val strokeWidth = 6.dp.toPx()
                        drawCircle(
                            color = trackColor,
                            style = Stroke(width = strokeWidth)
                        )
                        drawArc(
                            color = arcColor,
                            startAngle = -90f,
                            sweepAngle = 360f * metrics.animatedProgress,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = "${metrics.remainingPercent}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = heroTitle(state),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${state.countdownText} left",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ReminderCallout(completed, metrics.bucketIndex, metrics.remainingPercent)
        }
    }
}

@Composable
internal fun SeamlessHero(
    state: SalahUiState,
    onToggleCurrent: () -> Unit
) {
    val metrics = rememberSalahHeroMetrics(state)
    val completed = state.isCurrentPrayerCompleted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!state.isDuhaWindow) {
                Text(
                    text = "NEXT: ${state.nextPrayerName.uppercase()} · ${state.nextPrayerStartTimeFormatted.ifBlank { "--:--" }}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (completed) SalahEmerald else MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = state.hijriDate,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = heroTitle(state),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            PrayerCheckCircle(completed, onToggleCurrent)
        }
        DrainingBar(metrics.animatedProgress, completed, metrics.remainingPercent)
        CountdownRow(state, metrics, completed)
        ReminderCallout(completed, metrics.bucketIndex, metrics.remainingPercent)
    }
}

@Composable
internal fun DailyDotsHero(
    state: SalahUiState,
    onToggleCurrent: () -> Unit
) {
    val metrics = rememberSalahHeroMetrics(state)
    val completed = state.isCurrentPrayerCompleted
    val dotPrayers = listOf(
        "F" to "Fajr",
        "D" to "Dhuhr",
        "A" to "Asr",
        "M" to "Maghrib",
        "I" to "Isha"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!state.isDuhaWindow) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "NEXT: ${state.nextPrayerName.uppercase()} · ${state.nextPrayerStartTimeFormatted.ifBlank { "--:--" }}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (completed) SalahEmerald else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dotPrayers.forEach { (letter, name) ->
                        val done = state.dailyCompletionByPrayer[name] == true
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = CircleShape,
                                color = if (done) SalahEmerald else Color.Transparent,
                                border = if (done) null else BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier.size(9.dp)
                            ) {}
                            Text(
                                text = letter,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = heroTitle(state),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                PrayerCheckCircle(completed, onToggleCurrent)
            }
            DrainingBar(metrics.animatedProgress, completed, metrics.remainingPercent)
            CountdownRow(state, metrics, completed)
            ReminderCallout(completed, metrics.bucketIndex, metrics.remainingPercent)
        }
    }
}

@Composable
internal fun TimelineStrip(
    prayers: List<PrayerModel>,
    currentPrayerName: String
) {
    val fivePrayers = prayers.filter { it.id != "sunrise" }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        fivePrayers.forEach { prayer ->
            val selected = prayer.nameEnglish.equals(currentPrayerName, ignoreCase = true)
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                border = if (selected) BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary
                ) else null
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = prayer.nameEnglish,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = prayer.time,
                        fontSize = 8.sp,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun PrayerGrid(
    prayers: List<PrayerModel>,
    currentPrayerName: String,
    onTogglePrayer: (PrayerModel) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        prayers.chunked(2).forEach { rowPrayers ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowPrayers.forEach { prayer ->
                    val isCurrent = prayer.nameEnglish.equals(currentPrayerName, ignoreCase = true)
                    val isSunrise = prayer.id == "sunrise"
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = prayer.nameEnglish,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                if (!isSunrise) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .clickable { onTogglePrayer(prayer) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.material3.Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = if (prayer.isFardCompleted) {
                                                "Completed"
                                            } else {
                                                "Mark ${prayer.nameEnglish} complete"
                                            },
                                            tint = if (prayer.isFardCompleted) SalahEmerald
                                            else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = prayer.nameArabic,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = prayer.time,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                if (rowPrayers.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun SalahHomeHero(
    layout: SalahHomeLayout,
    state: SalahUiState,
    onToggleCurrent: () -> Unit
) {
    when (layout) {
        SalahHomeLayout.CLASSIC_COMPACT -> ClassicHeroCard(state, onToggleCurrent)
        SalahHomeLayout.BENTO_SPLIT -> BentoHero(state, onToggleCurrent)
        SalahHomeLayout.CIRCULAR_RING -> CircularHero(state, onToggleCurrent)
        SalahHomeLayout.SEAMLESS_EDITORIAL -> SeamlessHero(state, onToggleCurrent)
        SalahHomeLayout.DAILY_STREAK_DOTS -> DailyDotsHero(state, onToggleCurrent)
    }
}
