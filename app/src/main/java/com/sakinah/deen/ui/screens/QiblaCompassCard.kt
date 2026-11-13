package com.sakinah.deen.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakinah.deen.domain.sensor.QiblaCompassData
import com.sakinah.deen.ui.theme.EmeraldDeep
import com.sakinah.deen.ui.theme.MintGreen
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val CompassGold = Color(0xFFD4AF37)
private val CompassDialTop = Color(0xFF176B4F)
private val CompassDialBottom = Color(0xFF061E16)

/**
 * Industry-style Qibla compass: a rotating compass rose with the current screen
 * heading at the fixed lubber line, a gold Kaaba bearing needle, and explicit
 * alignment/tilt/accuracy guidance instead of a bare needle on a circle.
 */
@Composable
internal fun QiblaCompassCard(data: QiblaCompassData) {
    // Unwrap the circular angle so 359° -> 1° animates two degrees clockwise instead
    // of spinning almost a full turn backwards.
    var targetHeading by remember { mutableFloatStateOf(data.azimuthTrueNorthDeg) }
    var targetQibla by remember { mutableFloatStateOf(data.needleRelativeAngleDeg) }
    LaunchedEffect(data.azimuthTrueNorthDeg) {
        targetHeading += shortestAngleDelta(targetHeading, data.azimuthTrueNorthDeg)
    }
    LaunchedEffect(data.needleRelativeAngleDeg) {
        targetQibla += shortestAngleDelta(targetQibla, data.needleRelativeAngleDeg)
    }

    val animatedHeading by animateFloatAsState(targetHeading, tween(180), label = "heading")
    val animatedQibla by animateFloatAsState(targetQibla, tween(180), label = "qibla")
    val glow by animateFloatAsState(
        targetValue = if (data.isAligned && data.isFlat) 1f else 0f,
        animationSpec = tween(220),
        label = "alignment"
    )

    val signedDelta = shortestAngleDelta(0f, data.needleRelativeAngleDeg)
    val aligned = data.isAligned && data.isFlat
    val instruction = when {
        !data.hasSensor -> "No compass sensor on this device"
        !data.hasLocation -> "Set a location to calculate the Qibla"
        !data.isFlat -> "Hold the phone flat · ${data.tiltDeg.roundToInt()}° tilt"
        aligned -> "Aligned with the Kaaba"
        signedDelta >= 0f -> "Turn right ${signedDelta.roundToInt()}°"
        else -> "Turn left ${(-signedDelta).roundToInt()}°"
    }
    val accuracyLabel = when (data.accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High accuracy"
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium accuracy"
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low accuracy"
        else -> "Unreliable reading"
    }
    val needsCalibration = data.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW

    val cardinalPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
    }
    val degreePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, CompassGold.copy(alpha = 0.25f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0E4433), Color(0xFF082A1F))
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "QIBLA COMPASS",
                            color = MintGreen.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "True North · Kaaba Bearing",
                            color = Color.White,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Explore,
                                contentDescription = null,
                                tint = MintGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "LIVE",
                                color = MintGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Canvas(
                    modifier = Modifier
                        .size(272.dp)
                        .semantics {
                            contentDescription =
                                "Qibla compass. Heading ${data.azimuthTrueNorthDeg.roundToInt()} degrees, " +
                                    "Qibla bearing ${data.kaabaBearingDeg.roundToInt()} degrees. $instruction"
                        }
                ) {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dialRadius = radius - 10.dp.toPx()
                    val tickOuter = dialRadius - 9.dp.toPx()

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(CompassDialTop, Color(0xFF0B3A2A), CompassDialBottom),
                            center = center,
                            radius = dialRadius
                        ),
                        radius = dialRadius,
                        center = center
                    )

                    if (glow > 0f) {
                        drawCircle(
                            color = MintGreen.copy(alpha = 0.16f * glow),
                            radius = dialRadius + 7.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = MintGreen.copy(alpha = 0.70f * glow),
                            radius = dialRadius,
                            center = center,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    } else {
                        drawCircle(
                            color = CompassGold.copy(alpha = 0.45f),
                            radius = dialRadius,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                    drawCircle(
                        color = Color.White.copy(alpha = 0.07f),
                        radius = dialRadius - 14.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // The rose rotates against the fixed lubber line: north on the dial
                    // always points to true north, while the top of the phone is the
                    // lubber marker below.
                    rotate(-animatedHeading, center) {
                        for (degree in 0 until 360 step 5) {
                            val cardinal = degree % 90 == 0
                            val major = degree % 30 == 0
                            val tickLength = when {
                                cardinal -> 15.dp.toPx()
                                major -> 10.dp.toPx()
                                else -> 5.dp.toPx()
                            }
                            val angleRad = Math.toRadians(degree - 90.0)
                            val cosAngle = cos(angleRad).toFloat()
                            val sinAngle = sin(angleRad).toFloat()
                            drawLine(
                                color = when {
                                    cardinal -> Color.White.copy(alpha = 0.92f)
                                    major -> Color.White.copy(alpha = 0.65f)
                                    else -> Color.White.copy(alpha = 0.25f)
                                },
                                start = Offset(
                                    center.x + cosAngle * tickOuter,
                                    center.y + sinAngle * tickOuter
                                ),
                                end = Offset(
                                    center.x + cosAngle * (tickOuter - tickLength),
                                    center.y + sinAngle * (tickOuter - tickLength)
                                ),
                                strokeWidth = if (cardinal) 2.5.dp.toPx() else 1.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        val labelRadius = tickOuter - 29.dp.toPx()
                        for ((label, degree) in listOf(
                            "N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f
                        )) {
                            val angleRad = Math.toRadians(degree - 90.0)
                            val x = center.x + cos(angleRad).toFloat() * labelRadius
                            val y = center.y + sin(angleRad).toFloat() * labelRadius
                            cardinalPaint.textSize = 17.sp.toPx()
                            cardinalPaint.color = if (label == "N") {
                                CompassGold.toArgb()
                            } else {
                                Color.White.copy(alpha = 0.88f).toArgb()
                            }
                            drawContext.canvas.nativeCanvas.drawText(
                                label,
                                x,
                                y + cardinalPaint.textSize / 3f,
                                cardinalPaint
                            )
                        }

                        degreePaint.textSize = 8.sp.toPx()
                        degreePaint.color = Color.White.copy(alpha = 0.42f).toArgb()
                        for (degree in 30 until 360 step 30) {
                            if (degree % 90 == 0) continue
                            val angleRad = Math.toRadians(degree - 90.0)
                            val x = center.x + cos(angleRad).toFloat() * labelRadius
                            val y = center.y + sin(angleRad).toFloat() * labelRadius
                            drawContext.canvas.nativeCanvas.drawText(
                                degree.toString(),
                                x,
                                y + degreePaint.textSize / 3f,
                                degreePaint
                            )
                        }
                    }

                    // Gold Qibla needle with a Kaaba marker at its tip.
                    rotate(animatedQibla, center) {
                        val tipY = center.y - (dialRadius - 44.dp.toPx())
                        val tip = Offset(center.x, tipY)
                        val needle = Path().apply {
                            moveTo(tip.x, tip.y)
                            lineTo(center.x + 10.dp.toPx(), center.y - 2.dp.toPx())
                            lineTo(center.x, center.y + 13.dp.toPx())
                            lineTo(center.x - 10.dp.toPx(), center.y - 2.dp.toPx())
                            close()
                        }
                        drawPath(
                            path = needle,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFF8E9A6), CompassGold),
                                start = tip,
                                end = Offset(center.x, center.y + 13.dp.toPx())
                            )
                        )
                        drawPath(
                            path = needle,
                            color = Color.White.copy(alpha = 0.35f),
                            style = Stroke(width = 1.dp.toPx())
                        )

                        val kaabaSize = 17.dp.toPx()
                        val kaabaTopLeft = Offset(
                            tip.x - kaabaSize / 2f,
                            tip.y - kaabaSize / 2f
                        )
                        drawRoundRect(
                            color = Color(0xFF121212),
                            topLeft = kaabaTopLeft,
                            size = Size(kaabaSize, kaabaSize),
                            cornerRadius = CornerRadius(2.5.dp.toPx())
                        )
                        drawRect(
                            color = CompassGold,
                            topLeft = Offset(kaabaTopLeft.x, tip.y - kaabaSize * 0.20f),
                            size = Size(kaabaSize, kaabaSize * 0.22f)
                        )
                        drawRoundRect(
                            color = CompassGold.copy(alpha = 0.85f),
                            topLeft = kaabaTopLeft,
                            size = Size(kaabaSize, kaabaSize),
                            cornerRadius = CornerRadius(2.5.dp.toPx()),
                            style = Stroke(width = 1.4.dp.toPx())
                        )
                    }

                    // Fixed lubber marker: the top of the screen is where the phone points.
                    val lubber = Path().apply {
                        moveTo(center.x, center.y - dialRadius + 2.dp.toPx())
                        lineTo(center.x - 5.dp.toPx(), center.y - dialRadius + 13.dp.toPx())
                        lineTo(center.x + 5.dp.toPx(), center.y - dialRadius + 13.dp.toPx())
                        close()
                    }
                    drawPath(lubber, Color.White.copy(alpha = 0.9f))

                    drawCircle(EmeraldDeep, radius = 17.dp.toPx(), center = center)
                    drawCircle(
                        color = CompassGold.copy(alpha = 0.85f),
                        radius = 17.dp.toPx(),
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(MintGreen, radius = 4.5.dp.toPx(), center = center)
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (aligned) {
                        MintGreen.copy(alpha = 0.16f)
                    } else {
                        Color.White.copy(alpha = 0.07f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (aligned) MintGreen.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (aligned) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.ScreenRotation
                            },
                            contentDescription = null,
                            tint = if (aligned) MintGreen else Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = instruction,
                            color = if (aligned) MintGreen else Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CompassMetric("HEADING", "${data.azimuthTrueNorthDeg.roundToInt()}°")
                    CompassMetric("QIBLA", "${data.kaabaBearingDeg.roundToInt()}°")
                    CompassMetric(
                        "DECLINATION",
                        String.format(Locale.US, "%+.1f°", data.magneticDeclinationDeg)
                    )
                }

                if (needsCalibration || !data.isFlat) {
                    Text(
                        text = "$accuracyLabel · move the phone in a figure 8 and keep it level",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CompassMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.48f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp
        )
    }
}

private fun shortestAngleDelta(from: Float, to: Float): Float {
    var delta = (to - from) % 360f
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return delta
}
