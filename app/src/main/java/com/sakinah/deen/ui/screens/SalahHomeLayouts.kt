package com.sakinah.deen.ui.screens

import com.sakinah.deen.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakinah.deen.data.model.PrayerModel
import com.sakinah.deen.data.prefs.SalahHomeLayout
import com.sakinah.deen.ui.viewmodel.SalahUiState
import kotlin.math.roundToInt


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
    completed -> Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFF4ADE80)))
    remainingPercent <= 20 -> Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.error))
    else -> Brush.horizontalGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
    )
}

@Composable
private fun accentColor(completed: Boolean, remainingPercent: Int): Color = when {
    completed -> MaterialTheme.colorScheme.primary
    remainingPercent <= 20 -> MaterialTheme.colorScheme.tertiary
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
    val fallbackCity = stringResource(R.string.salah_location_fallback)
    val cityShort = state.locationName.substringBefore(",").trim().ifBlank { fallbackCity }
    val methodShort = state.authorityName.ifBlank { state.sourceLabel }
    // A stale fix must say so rather than reading as the current position.
    val staleNote = if (state.isLocationStale) {
        val hours = (state.locationAgeMinutes / 60).coerceAtLeast(1L)
        stringResource(R.string.salah_stale_location, hours)
    } else {
        ""
    }

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
            // The date and city/method lines wrap instead of being ellipsised to a few
            // characters by the Qadha pill and the icon buttons beside them.
            Text(
                text = state.hijriDate,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$shortDate · $cityShort ▾ · $methodShort$staleNote",
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
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
                    modifier = Modifier.clickable(onClick = onOpenQadha)
                ) {
                    Text(
                        text = stringResource(R.string.salah_qadha_btn),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            if (onOpenNotifications != null) {
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(34.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenNotifications),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = stringResource(R.string.salah_notifications_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (onRecalculate != null) {
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(34.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !state.isRefreshing, onClick = onRecalculate),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(15.dp)
                        )
                    } else {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.salah_recalculate_cd),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            if (!focusMode) {
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(34.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = stringResource(R.string.salah_settings_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun LiveSkySunArcCard(
    state: SalahUiState,
    modifier: Modifier = Modifier
) {
    var nowMillis by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            kotlinx.coroutines.delay(60_000L)
        }
    }

    val sunrise = state.prayerTimes[com.sakinah.deen.data.model.PrayerType.SUNRISE]
    val sunset = state.prayerTimes[com.sakinah.deen.data.model.PrayerType.MAGHRIB]
    val zone = java.time.ZoneId.systemDefault()
    fun millisFor(time: kotlinx.datetime.LocalTime?): Long? = time?.let {
        java.time.LocalTime.of(it.hour, it.minute, it.second)
            .atDate(java.time.LocalDate.now(zone)).atZone(zone).toInstant().toEpochMilli()
    }
    val start = millisFor(sunrise)
    val end = millisFor(sunset)
    val progress = if (start != null && end != null && end > start) {
        ((nowMillis - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val formatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a")
    fun label(time: kotlinx.datetime.LocalTime?): String = time?.let {
        java.time.LocalTime.of(it.hour, it.minute, it.second).format(formatter)
    } ?: "--:--"

    val sky = state.skyCondition
    val backdrop = when (sky) {
        com.sakinah.deen.ui.viewmodel.SkyCondition.RAINY -> listOf(Color(0xFF1A2A3A), Color(0xFF24394D))
        com.sakinah.deen.ui.viewmodel.SkyCondition.CLOUDY,
        com.sakinah.deen.ui.viewmodel.SkyCondition.SCATTERED_CLOUDS -> listOf(Color(0xFF24313D), Color(0xFF334656))
        com.sakinah.deen.ui.viewmodel.SkyCondition.NIGHT -> listOf(Color(0xFF0B1324), Color(0xFF16223B))
        com.sakinah.deen.ui.viewmodel.SkyCondition.FOG -> listOf(Color(0xFF2A3339), Color(0xFF3B474E))
        else -> listOf(Color(0xFF17324A), Color(0xFF2E5876))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(backdrop))
    ) {
        val arcAccent = MaterialTheme.colorScheme.tertiary
        Canvas(modifier = Modifier.matchParentSize()) {
            val insetX = 64.dp.toPx()
            val top = 20.dp.toPx()
            val bottom = 18.dp.toPx()
            val left = insetX
            val right = size.width - insetX
            val arcTop = top
            val arcBottom = size.height - bottom
            val midX = (left + right) / 2f
            val radiusX = (right - left) / 2f
            val radiusY = (arcBottom - arcTop).coerceAtLeast(1f)

            fun point(t: Float): androidx.compose.ui.geometry.Offset {
                val angle = Math.PI.toFloat() * t
                return androidx.compose.ui.geometry.Offset(
                    x = midX - radiusX * kotlin.math.cos(angle),
                    y = arcBottom - radiusY * kotlin.math.sin(angle)
                )
            }

            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = point(progress),
                end = point(1f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(6.dp.toPx(), 6.dp.toPx())
                )
            )
            val steps = 32
            for (i in 0 until steps) {
                val t0 = progress * i / steps
                val t1 = progress * (i + 1) / steps
                drawLine(
                    color = arcAccent,
                    start = point(t0),
                    end = point(t1),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            if (state.isDaylight) {
                val sun = point(progress)
                drawCircle(color = arcAccent.copy(alpha = 0.25f), radius = 11.dp.toPx(), center = sun)
                drawCircle(color = Color(0xFFFFD54F), radius = 5.dp.toPx(), center = sun)
            } else {
                drawCircle(
                    color = Color.White.copy(alpha = 0.75f),
                    radius = 2.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.68f, 16.dp.toPx())
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 2.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text("Sunrise", color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp)
            Text(label(sunrise), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 2.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text("Sunset", color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp)
            Text(label(sunset), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.28f)
            ) {
                Text(
                    text = sky?.label ?: "Sky unavailable",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            state.temperatureCelsius?.let { temp ->
                Text(
                    text = "${temp.roundToInt()}°C",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
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
        color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        border = if (completed) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .size(size)
            .clickable(onClick = onToggle)
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = if (completed) "Prayer completed" else "Mark prayer complete",
                // A light primary in dark monochrome needs a dark tick, not a fixed white one.
                tint = if (completed) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
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
            color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
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
            color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
        )
    }
}

private fun heroTitle(state: SalahUiState): String {
    val override = state.heroTitleOverride
    if (!override.isNullOrBlank()) return override
    return listOfNotNull(
        state.currentPrayerName.takeIf { it.isNotBlank() },
        state.currentPrayerArabic.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
}

@Composable
internal fun ClassicHeroCard(
    state: SalahUiState,
    onToggleCurrent: () -> Unit,
    onQadhaClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val metrics = rememberSalahHeroMetrics(state)
    val completed = state.isCurrentPrayerCompleted
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (completed) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (completed) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.hijriDate,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${state.locationName} · ${state.authorityName}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = state.gregorianDate,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (completed) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clickable(onClick = onQadhaClick)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Qadha", style = MaterialTheme.typography.labelLarge)
                        }
                        VerticalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Box(
                            modifier = Modifier
                                .clickable(onClick = onToggleCurrent)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Mark prayed",
                                tint = if (completed) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
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
                            color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
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
    val arcColor = if (completed) MaterialTheme.colorScheme.primary
    else if (metrics.remainingPercent <= 20) MaterialTheme.colorScheme.error
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
                        color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
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
                    color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
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
                            color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dotPrayers.forEach { (letter, name) ->
                        val done = state.dailyCompletionByPrayer[name] == true
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = CircleShape,
                                color = if (done) MaterialTheme.colorScheme.primary else Color.Transparent,
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
                                    val haptic = LocalHapticFeedback.current
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onTogglePrayer(prayer)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.material3.Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = if (prayer.isFardCompleted) {
                                                "Completed"
                                            } else {
                                                "Mark ${prayer.nameEnglish} complete"
                                            },
                                            tint = if (prayer.isFardCompleted) MaterialTheme.colorScheme.primary
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
    onToggleCurrent: () -> Unit,
    onQadhaClick: () -> Unit = {}
) {
    when (layout) {
        SalahHomeLayout.CLASSIC_COMPACT -> ClassicHeroCard(state, onToggleCurrent, onQadhaClick)
        SalahHomeLayout.BENTO_SPLIT -> BentoHero(state, onToggleCurrent)
        SalahHomeLayout.CIRCULAR_RING -> CircularHero(state, onToggleCurrent)
        SalahHomeLayout.SEAMLESS_EDITORIAL -> SeamlessHero(state, onToggleCurrent)
        SalahHomeLayout.DAILY_STREAK_DOTS -> DailyDotsHero(state, onToggleCurrent)
    }
}
