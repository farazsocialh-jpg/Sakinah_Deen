package com.sakinah.deen.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.webkit.WebViewAssetLoader
import com.sakinah.deen.BuildConfig
import com.sakinah.deen.domain.qibla.CompassSensorState
import com.sakinah.deen.domain.qibla.QiblaAction
import com.sakinah.deen.domain.qibla.QiblaMath
import com.sakinah.deen.domain.qibla.QiblaPermissionState
import com.sakinah.deen.domain.qibla.QiblaStatus
import com.sakinah.deen.domain.qibla.QiblaUiState
import com.sakinah.deen.domain.qibla.rememberTrueNorthCompassState
import com.sakinah.deen.ui.theme.DaifAmber
import com.sakinah.deen.ui.theme.SahihGreen
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class QiblaMode(val label: String) {
    COMPASS("Compass"),
    MAP("Map"),
    SUN("Sun")
}

/**
 * Virtual origin the bundled Leaflet page is served from. Using a real https origin for the
 * document (instead of loadDataWithBaseURL, which gives the page an opaque origin) is what
 * lets leaflet.js/css and the map tiles load without cross-origin blocking.
 */
private const val QIBLA_MAP_DOMAIN = "app.imancore.local"
private const val QIBLA_MAP_URL = "https://$QIBLA_MAP_DOMAIN/assets/leaflet/qibla_map.html"

/** How long the map may stay without a single rendered tile before the native fallback wins. */
private const val MAP_LOAD_TIMEOUT_MS = 12_000L

@Composable
fun QiblaModesCard(
    latitude: Double,
    longitude: Double,
    cityName: String,
    modifier: Modifier = Modifier,
    /** False while the app has no usable coordinates yet (cold start / no permission). */
    locationAvailable: Boolean = true
) {
    // Open on the True-North compass: it works offline and needs no tile downloads.
    var mode by rememberSaveable { mutableStateOf(QiblaMode.COMPASS) }
    val context = LocalContext.current

    var permission by remember { mutableStateOf(currentPermissionState(context)) }
    var permissionRequestInFlight by remember { mutableStateOf(false) }
    var locationServicesEnabled by remember { mutableStateOf(isLocationEnabled(context)) }

    val activity = context.findActivity()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionRequestInFlight = false
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            hasLocationPermission(context)
        permission = when {
            granted -> QiblaPermissionState.GRANTED
            activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> QiblaPermissionState.DENIED
            else -> QiblaPermissionState.PERMANENTLY_DENIED
        }
    }

    // Permission and the system location toggle can both change while the user is in Settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permission = currentPermissionState(context)
        locationServicesEnabled = isLocationEnabled(context)
    }

    val onAction: (QiblaAction) -> Unit = { action ->
        when (action) {
            QiblaAction.GRANT_PERMISSION -> {
                permissionRequestInFlight = true
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }

            QiblaAction.OPEN_SETTINGS -> {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
                runCatching { context.startActivity(intent) }
            }

            QiblaAction.ENABLE_LOCATION -> {
                runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            }

            // Sensor states recover on their own; the banner disappears with the next reading.
            QiblaAction.RETRY -> Unit
        }
    }

    val qiblaBearing = remember(latitude, longitude) { QiblaMath.qiblaBearingDegrees(latitude, longitude) }
    val distanceKm = remember(latitude, longitude) { QiblaMath.distanceToKaabaKm(latitude, longitude) }
    val cardinal = remember(qiblaBearing) { QiblaMath.cardinalDirection(qiblaBearing) }

    // Listeners exist only while the Compass mode is on screen: switching to Map/Sun, leaving
    // the Qibla tab or pausing the app all release the magnetometer.
    val sensorState = rememberTrueNorthCompassState(
        latitude = latitude,
        longitude = longitude,
        enabled = mode == QiblaMode.COMPASS
    )

    val status = QiblaStatus(
        permission = permission,
        permissionRequestInFlight = permissionRequestInFlight,
        locationServicesEnabled = locationServicesEnabled,
        hasCoordinates = locationAvailable,
        hasSensor = sensorState.hasSensor,
        accuracy = sensorState.accuracy,
        tiltDeg = sensorState.tiltDeg,
        headingDeg = sensorState.trueNorthAzimuth,
        qiblaBearingDeg = qiblaBearing,
        distanceKm = distanceKm,
        magneticInterference = sensorState.isMagneticInterference
    )
    val uiState = remember(status) { status.toUiState() }

    val haptic = LocalHapticFeedback.current
    LaunchedEffect(uiState is QiblaUiState.NearAligned) {
        if (uiState is QiblaUiState.NearAligned) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    SakinahCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Qibla Compass · $cityName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${qiblaBearing.roundToInt()}° $cardinal · $distanceKm km to Makkah",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
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

        // A missing location breaks every mode, so the CTA sits above the mode content.
        if (uiState.isLocationRelated) {
            QiblaStateBanner(uiState = uiState, onAction = onAction)
            Spacer(modifier = Modifier.height(12.dp))
        }

        when (mode) {
            QiblaMode.MAP -> QiblaMapView(
                latitude = latitude,
                longitude = longitude,
                qiblaBearing = qiblaBearing
            )

            QiblaMode.COMPASS -> QiblaCompassPanel(
                uiState = uiState,
                status = status,
                sensorState = sensorState,
                onAction = onAction
            )

            QiblaMode.SUN -> QiblaSunDial(
                latitude = latitude,
                longitude = longitude,
                qiblaBearing = qiblaBearing
            )
        }
    }
}

/* ---------------------------------------------------------------------------------------- */
/* Compass mode                                                                              */
/* ---------------------------------------------------------------------------------------- */

@Composable
private fun QiblaCompassPanel(
    uiState: QiblaUiState,
    status: QiblaStatus,
    sensorState: CompassSensorState,
    onAction: (QiblaAction) -> Unit
) {
    val reading = status.reading
    val hasBearing = status.hasCoordinates
    val heading = sensorState.trueNorthAzimuth
    val targetQibla = (status.qiblaBearingDeg - heading + 360f) % 360f

    // Unwrap the heading so 359° -> 1° animates one degree clockwise instead of a full turn.
    var unwrappedHeading by remember { mutableFloatStateOf(heading) }
    LaunchedEffect(heading) {
        unwrappedHeading += shortestAngleDelta(unwrappedHeading, heading)
    }
    val animatedHeading by animateFloatAsState(unwrappedHeading, tween(180), label = "qibla_heading")
    val animatedQibla by animateFloatAsState(
        targetValue = if (hasBearing) targetQibla else 0f,
        animationSpec = tween(180),
        label = "qibla_needle"
    )

    val aligned = uiState is QiblaUiState.NearAligned
    val primaryColor = if (aligned) SahihGreen else MaterialTheme.colorScheme.primary
    val ringColor = MaterialTheme.colorScheme.outline
    val spokenSummary = buildSpokenSummary(uiState, status)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = spokenSummary },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (uiState !is QiblaUiState.Ready && uiState !is QiblaUiState.NearAligned &&
            !uiState.isLocationRelated
        ) {
            QiblaStateBanner(uiState = uiState, onAction = onAction)
        }

        Canvas(modifier = Modifier.size(200.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 12f
            drawCircle(color = ringColor, radius = radius, center = center, style = Stroke(width = 3f))

            // Fixed north marker on the rose; the arrow rotates against the true-north heading.
            rotate(degrees = -animatedHeading, pivot = center) {
                drawLine(
                    color = Color.Red.copy(alpha = 0.7f),
                    start = Offset(center.x, center.y - radius + 12f),
                    end = Offset(center.x, center.y - radius + 28f),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
            }

            if (hasBearing && sensorState.hasSensor) {
                rotate(degrees = animatedQibla, pivot = center) {
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
        }

        Text(
            text = when {
                !sensorState.hasSensor -> "No compass sensor — use Map or Sun mode"
                !hasBearing -> "Set a location to see the Qibla needle"
                aligned -> "Aligned with the Qibla (${status.qiblaBearingDeg.roundToInt()}°)"
                else -> reading.turnInstruction
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (aligned) FontWeight.SemiBold else FontWeight.Normal,
            color = if (aligned) SahihGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Exact numbers are always on screen (audit #49), never just a needle.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QiblaMetric("HEADING", if (sensorState.hasSensor) "${heading.roundToInt()}°" else "—")
            QiblaMetric("QIBLA", if (hasBearing) "${status.qiblaBearingDeg.roundToInt()}°" else "—")
            QiblaMetric("ACCURACY", sensorState.accuracy.label)
        }

        Text(
            text = "${status.distanceKm} km to Makkah · tilt ${sensorState.tiltDeg.roundToInt()}°",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (reading.isHeadingUnreliable || sensorState.isMagneticInterference) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DaifAmber.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Magnetic interference: the needle can flip 180°. Move away from metal, " +
                        "speakers or a magnetic case, recalibrate with a figure-8, or switch to Map / Sun mode.",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = DaifAmber
                )
            }
        }
    }
}

/** TalkBack/VoiceOver summary: bearing, heading, accuracy and the turn to make (audit #50). */
private fun buildSpokenSummary(uiState: QiblaUiState, status: QiblaStatus): String {
    val reading = status.reading
    return buildString {
        append("Qibla compass. ")
        if (status.hasCoordinates) {
            append("Qibla bearing ${status.qiblaBearingDeg.roundToInt()} degrees. ")
            append("${status.distanceKm} kilometres to Makkah. ")
        }
        if (status.hasSensor) {
            append("Current heading ${status.headingDeg.roundToInt()} degrees. ")
            append("Sensor accuracy ${status.accuracy.label}. ")
        }
        append(uiState.message)
    }
}

@Composable
private fun QiblaMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Banner + CTA for the current state, so no state fails silently (audit #45). */
@Composable
private fun QiblaStateBanner(uiState: QiblaUiState, onAction: (QiblaAction) -> Unit) {
    val isError = uiState is QiblaUiState.Error
    val accent = when {
        isError -> MaterialTheme.colorScheme.error
        uiState is QiblaUiState.Ready || uiState is QiblaUiState.Idle -> MaterialTheme.colorScheme.primary
        else -> DaifAmber
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = uiState.message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
            uiState.action?.let { action ->
                TextButton(onClick = { onAction(action) }) {
                    Text(text = actionLabel(action), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun actionLabel(action: QiblaAction): String = when (action) {
    QiblaAction.GRANT_PERMISSION -> "Grant location"
    QiblaAction.OPEN_SETTINGS -> "Open settings"
    QiblaAction.ENABLE_LOCATION -> "Turn on"
    QiblaAction.RETRY -> "Retry"
}

/* ---------------------------------------------------------------------------------------- */
/* Map mode                                                                                  */
/* ---------------------------------------------------------------------------------------- */

private enum class MapLoadState { LOADING, READY, FAILED }

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun QiblaMapView(
    latitude: Double,
    longitude: Double,
    qiblaBearing: Float
) {
    val context = LocalContext.current
    var loadState by remember(latitude, longitude) { mutableStateOf(MapLoadState.LOADING) }
    var failureReason by remember(latitude, longitude) { mutableStateOf<String?>(null) }
    var reloadToken by remember(latitude, longitude) { mutableIntStateOf(0) }
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }

    // Serves assets/leaflet/* from https://app.imancore.local/… with correct MIME types and
    // CORS headers, so Leaflet is not blocked as a cross-origin script.
    val assetLoader = remember(context) {
        WebViewAssetLoader.Builder()
            .setDomain(QIBLA_MAP_DOMAIN)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    val mapUrl = remember(latitude, longitude, qiblaBearing, reloadToken) {
        buildString {
            append(QIBLA_MAP_URL)
            append("?lat=").append(latitude)
            append("&lon=").append(longitude)
            append("&qibla=").append(qiblaBearing)
            append("&r=").append(reloadToken)
        }
    }

    // Watchdog: an empty WebView that never paints a tile must not look like a working map.
    LaunchedEffect(mapUrl) {
        loadState = MapLoadState.LOADING
        failureReason = null
        delay(MAP_LOAD_TIMEOUT_MS)
        if (loadState == MapLoadState.LOADING) {
            failureReason = "Timed out while loading street tiles."
            loadState = MapLoadState.FAILED
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewHolder.value?.let { webView ->
                (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                runCatching { webView.stopLoading() }
                runCatching { webView.destroy() }
            }
            webViewHolder.value = null
        }
    }

    fun fail(reason: String) {
        failureReason = reason
        loadState = MapLoadState.FAILED
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            if (loadState == MapLoadState.LOADING) {
                Surface(
                    modifier = Modifier.fillMaxSize().zIndex(1f),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Loading Qibla map…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (loadState == MapLoadState.FAILED) {
                QiblaMapFallback(
                    qiblaBearing = qiblaBearing,
                    reason = failureReason,
                    onRetry = { reloadToken++ }
                )
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { webContext ->
                        WebView(webContext).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.setGeolocationEnabled(false)
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.loadsImagesAutomatically = true
                            settings.userAgentString =
                                System.getProperty("http.agent").orEmpty() +
                                " ImanCore/${BuildConfig.VERSION_NAME}"
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    android.util.Log.d(
                                        "QiblaMapJS",
                                        "${consoleMessage?.message()} @${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}"
                                    )
                                    return true
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): android.webkit.WebResourceResponse? {
                                    val url = request?.url ?: return null
                                    return assetLoader.shouldInterceptRequest(url)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    // Keep the map on its own page; nothing may navigate the WebView away.
                                    val url = request?.url?.toString().orEmpty()
                                    return !url.startsWith(QIBLA_MAP_URL, ignoreCase = true)
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    android.util.Log.e(
                                        "QiblaMap",
                                        "onReceivedError: ${error?.description} for ${request?.url}"
                                    )
                                    if (request?.isForMainFrame == true) {
                                        fail(error?.description?.toString() ?: "Map page failed to load.")
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: android.webkit.WebResourceResponse?
                                ) {
                                    android.util.Log.e(
                                        "QiblaMap",
                                        "onReceivedHttpError: ${errorResponse?.statusCode} for ${request?.url}"
                                    )
                                    if (request?.isForMainFrame == true) {
                                        fail("Map page returned HTTP ${errorResponse?.statusCode}.")
                                    }
                                }

                                override fun onRenderProcessGone(
                                    view: WebView?,
                                    detail: android.webkit.RenderProcessGoneDetail?
                                ): Boolean {
                                    android.util.Log.e("QiblaMap", "WebView renderer died")
                                    fail("The map renderer stopped unexpectedly.")
                                    return true
                                }
                            }

                            val mainHandler = Handler(Looper.getMainLooper())
                            addJavascriptInterface(
                                QiblaMapBridge(
                                    onReadyCallback = {
                                        android.util.Log.d("QiblaMap", "Leaflet page ready")
                                    },
                                    onTileLoadedCallback = {
                                        mainHandler.post {
                                            loadState = MapLoadState.READY
                                            failureReason = null
                                        }
                                    },
                                    onErrorCallback = { reason ->
                                        mainHandler.post {
                                            fail(
                                                when (reason) {
                                                    "tiles_unavailable" -> "Street tiles are unreachable on this network."
                                                    "leaflet_missing" -> "The bundled map library failed to load."
                                                    "bad_coordinates" -> "Location coordinates are missing."
                                                    else -> "Map error: $reason"
                                                }
                                            )
                                        }
                                    },
                                    onDebugCallback = { message ->
                                        // Bridge diagnostics are only useful while developing; a
                                        // release build must not write WebView URLs to logcat.
                                        if (com.sakinah.deen.BuildConfig.DEBUG) {
                                            android.util.Log.d("QiblaMapBT", message)
                                        }
                                    }
                                ),
                                "QiblaBridge"
                            )
                            webViewHolder.value = this
                            tag = mapUrl
                            loadUrl(mapUrl)
                        }
                    },
                    update = { webView ->
                        // Retry / location change reloads the page instead of stacking new WebViews.
                        if (webView.tag != mapUrl) {
                            webView.tag = mapUrl
                            webView.loadUrl(mapUrl)
                        }
                    }
                )
            }
        }

        Text(
            text = if (loadState == MapLoadState.READY) {
                "Align yourself with the green dashed line relative to your building or street."
            } else {
                "Map tiles need a network connection — Compass and Sun modes work offline."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Native fallback shown when tiles cannot be fetched: bearing ring + explicit retry. */
@Composable
private fun QiblaMapFallback(qiblaBearing: Float, reason: String?, onRetry: () -> Unit) {
    val fallbackRing = MaterialTheme.colorScheme.outline
    val fallbackPrimary = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = reason ?: "Map tiles unavailable — native Qibla bearing shown below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Canvas(
                modifier = Modifier
                    .size(96.dp)
                    .semantics {
                        contentDescription =
                            "Qibla bearing ${qiblaBearing.roundToInt()} degrees from true north"
                    }
            ) {
                val centre = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f - 6f
                drawCircle(color = fallbackRing, radius = radius, center = centre, style = Stroke(width = 3f))
                rotate(degrees = qiblaBearing, pivot = centre) {
                    drawLine(
                        color = fallbackPrimary,
                        start = centre,
                        end = Offset(centre.x, centre.y - radius + 16f),
                        strokeWidth = 7f,
                        cap = StrokeCap.Round
                    )
                }
            }
            Text(
                text = "Qibla ${qiblaBearing.roundToInt()}° from true north",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = onRetry) {
                Text("Retry map", fontSize = 12.sp)
            }
        }
    }
}

/** JS bridge exposed to the bundled map page as `window.QiblaBridge`. */
private class QiblaMapBridge(
    private val onReadyCallback: () -> Unit,
    private val onTileLoadedCallback: () -> Unit,
    private val onErrorCallback: (String) -> Unit,
    private val onDebugCallback: (String) -> Unit
) {
    @JavascriptInterface
    fun onMapReady() = onReadyCallback()

    @JavascriptInterface
    fun onTileLoaded() = onTileLoadedCallback()

    @JavascriptInterface
    fun onMapError(reason: String) = onErrorCallback(reason)

    @JavascriptInterface
    fun onDebug(message: String) = onDebugCallback(message)
}

/* ---------------------------------------------------------------------------------------- */
/* Sun mode                                                                                  */
/* ---------------------------------------------------------------------------------------- */

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
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = if (sunElevation <= 0f) {
                    "Sun is below the horizon. Qibla bearing ${qiblaBearing.roundToInt()} degrees. Use Map or Compass mode."
                } else {
                    "Qibla is ${relativeQiblaFromSun.roundToInt()} degrees clockwise from the Sun. " +
                        "Qibla bearing ${qiblaBearing.roundToInt()} degrees, Sun azimuth ${sunAzimuth.roundToInt()} degrees."
                }
            },
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
                text = "Point the amber Sun dot toward the Sun in the sky — the green line points to Qibla " +
                    "(${relativeQiblaFromSun.roundToInt()}° from Sun).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/* ---------------------------------------------------------------------------------------- */
/* Helpers                                                                                   */
/* ---------------------------------------------------------------------------------------- */

private fun shortestAngleDelta(from: Float, to: Float): Float {
    var delta = (to - from) % 360f
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return delta
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun currentPermissionState(context: Context): QiblaPermissionState {
    if (hasLocationPermission(context)) return QiblaPermissionState.GRANTED
    val activity = context.findActivity()
    val blockedByUser = activity != null &&
        (ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) || ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    return if (blockedByUser) QiblaPermissionState.DENIED else QiblaPermissionState.NOT_REQUESTED
}

private fun isLocationEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        ?: return false
    return LocationManagerCompat.isLocationEnabled(manager)
}
