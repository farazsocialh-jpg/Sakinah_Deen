package com.sakinah.deen.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sakinah.deen.domain.qibla.QiblaMath
import com.sakinah.deen.domain.qibla.rememberTrueNorthCompassState
import com.sakinah.deen.ui.theme.DaifAmber
import com.sakinah.deen.ui.theme.SahihGreen
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class QiblaMode(val label: String) {
    MAP("Map"),
    COMPASS("Compass"),
    SUN("Sun")
}

@Composable
fun QiblaModesCard(
    latitude: Double,
    longitude: Double,
    cityName: String,
    modifier: Modifier = Modifier
) {
    var mode by rememberSaveable { mutableStateOf(QiblaMode.MAP) }
    val qiblaBearing = remember(latitude, longitude) { QiblaMath.qiblaBearingDegrees(latitude, longitude) }
    val distanceKm = remember(latitude, longitude) { QiblaMath.distanceToKaabaKm(latitude, longitude) }
    val cardinal = remember(qiblaBearing) { QiblaMath.cardinalDirection(qiblaBearing) }

    SakinahCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Qibla · $cityName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${qiblaBearing.roundToInt()}° $cardinal · $distanceKm km to Makkah",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusBadge(
                text = when (mode) {
                    QiblaMode.MAP -> "Magnet-Free"
                    QiblaMode.COMPASS -> "True North"
                    QiblaMode.SUN -> "Solar"
                },
                accentColor = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        SegmentedPillBar(
            options = QiblaMode.entries,
            selected = mode,
            labelProvider = { it.label },
            onSelect = { mode = it }
        )

        Spacer(modifier = Modifier.height(14.dp))

        when (mode) {
            QiblaMode.MAP -> QiblaMapView(latitude = latitude, longitude = longitude)
            QiblaMode.COMPASS -> QiblaCompassDial(
                latitude = latitude,
                longitude = longitude,
                qiblaBearing = qiblaBearing
            )
            QiblaMode.SUN -> QiblaSunDial(
                latitude = latitude,
                longitude = longitude,
                qiblaBearing = qiblaBearing
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun QiblaMapView(latitude: Double, longitude: Double) {
    val html = remember(latitude, longitude) {
        """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <style>html,body,#map{height:100%;margin:0;padding:0;}</style>
        </head>
        <body>
        <div id="map"></div>
        <script>
        var user = [$latitude, $longitude];
        var kaaba = [${QiblaMath.KAABA_LAT}, ${QiblaMath.KAABA_LON}];
        var map = L.map('map', { zoomControl: true }).setView(user, 18);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 20 }).addTo(map);
        L.circleMarker(user, { radius: 7, color: '#0B3B2C', fillColor: '#10B981', fillOpacity: 1 }).addTo(map);
        L.polyline([user, kaaba], { color: '#10B981', weight: 4, dashArray: '8, 6' }).addTo(map);
        </script>
        </body>
        </html>
        """.trimIndent()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = WebViewClient()
                        tag = html
                        loadDataWithBaseURL("https://openstreetmap.org", html, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    if (webView.tag != html) {
                        webView.tag = html
                        webView.loadDataWithBaseURL("https://openstreetmap.org", html, "text/html", "UTF-8", null)
                    }
                }
            )
        }
        Text(
            text = "Align yourself with the green dashed line relative to your building or street.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QiblaCompassDial(
    latitude: Double,
    longitude: Double,
    qiblaBearing: Float
) {
    val sensorState = rememberTrueNorthCompassState(
        latitude = latitude,
        longitude = longitude,
        enabled = true
    )
    val rawArrowAngle = (qiblaBearing - sensorState.trueNorthAzimuth + 360f) % 360f
    val animatedArrow by animateFloatAsState(targetValue = rawArrowAngle, label = "qibla_arrow")
    val isAligned = abs(rawArrowAngle) < 6f || abs(rawArrowAngle - 360f) < 6f

    val primaryColor = if (isAligned) SahihGreen else MaterialTheme.colorScheme.primary
    val ringColor = MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (sensorState.needsCalibration) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DaifAmber.copy(alpha = 0.14f)
            ) {
                Text(
                    text = "Low magnetic accuracy - move phone in a figure-8 or switch to Map mode.",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = DaifAmber
                )
            }
        }

        Canvas(modifier = Modifier.size(200.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 12f
            drawCircle(color = ringColor, radius = radius, center = center, style = Stroke(width = 3f))

            rotate(degrees = -sensorState.trueNorthAzimuth, pivot = center) {
                drawLine(
                    color = Color.Red.copy(alpha = 0.7f),
                    start = Offset(center.x, center.y - radius + 12f),
                    end = Offset(center.x, center.y - radius + 28f),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
            }

            rotate(degrees = animatedArrow, pivot = center) {
                val path = Path().apply {
                    moveTo(center.x, center.y - radius + 22f)
                    lineTo(center.x - 14f, center.y + 18f)
                    lineTo(center.x, center.y + 6f)
                    lineTo(center.x + 14f, center.y + 18f)
                    close()
                }
                drawPath(path = path, color = primaryColor)
            }
        }

        Text(
            text = if (isAligned) {
                "Aligned with Qibla (${qiblaBearing.roundToInt()}°)"
            } else {
                "Rotate until arrow points straight up"
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isAligned) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isAligned) SahihGreen else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QiblaSunDial(
    latitude: Double,
    longitude: Double,
    qiblaBearing: Float
) {
    val sunPosition by produceState(
        initialValue = QiblaMath.solarPosition(latitude, longitude),
        latitude,
        longitude
    ) {
        while (true) {
            value = QiblaMath.solarPosition(latitude, longitude)
            delay(60_000)
        }
    }
    val (sunAzimuth, sunElevation) = sunPosition
    val relativeQiblaFromSun = (qiblaBearing - sunAzimuth + 360f) % 360f
    val primaryColor = MaterialTheme.colorScheme.primary
    val ringColor = MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (sunElevation <= 0f) {
            Text(
                text = "The Sun is currently below the horizon (${sunElevation.roundToInt()}°). Use Map or Compass mode at night.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            Canvas(modifier = Modifier.size(200.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f - 14f
                drawCircle(color = ringColor, radius = radius, center = center, style = Stroke(width = 3f))

                drawCircle(
                    color = DaifAmber,
                    radius = 12f,
                    center = Offset(center.x, center.y - radius)
                )

                val rad = Math.toRadians(relativeQiblaFromSun.toDouble() - 90.0)
                val endX = center.x + (radius - 22f) * cos(rad).toFloat()
                val endY = center.y + (radius - 22f) * sin(rad).toFloat()
                drawLine(
                    color = primaryColor,
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 7f,
                    cap = StrokeCap.Round
                )
            }
            Text(
                text = "Point the amber Sun dot toward the Sun in the sky - the green line points to Qibla (${relativeQiblaFromSun.roundToInt()}° from Sun).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
