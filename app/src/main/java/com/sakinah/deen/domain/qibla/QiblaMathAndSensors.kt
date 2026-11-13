package com.sakinah.deen.domain.qibla

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object QiblaMath {
    const val KAABA_LAT = 21.4225
    const val KAABA_LON = 39.8262
    private const val EARTH_RADIUS_KM = 6371.0

    fun qiblaBearingDegrees(lat: Double, lon: Double): Float {
        val phi1 = Math.toRadians(lat)
        val phi2 = Math.toRadians(KAABA_LAT)
        val dLambda = Math.toRadians(KAABA_LON - lon)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        val deg = Math.toDegrees(atan2(y, x))
        return ((deg + 360.0) % 360.0).toFloat()
    }

    fun distanceToKaabaKm(lat: Double, lon: Double): Int {
        val dLat = Math.toRadians(KAABA_LAT - lat)
        val dLon = Math.toRadians(KAABA_LON - lon)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(KAABA_LAT)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (EARTH_RADIUS_KM * c).roundToInt()
    }

    fun cardinalDirection(deg: Float): String {
        val dirs = listOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
        )
        val idx = (((deg + 11.25f) % 360f) / 22.5f).toInt()
        return dirs[idx.coerceIn(0, dirs.lastIndex)]
    }

    fun solarPosition(
        lat: Double,
        lon: Double,
        timeMillis: Long = System.currentTimeMillis()
    ): Pair<Float, Float> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            this.timeInMillis = timeMillis
        }
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val utcHours = cal.get(Calendar.HOUR_OF_DAY) +
            cal.get(Calendar.MINUTE) / 60.0 +
            cal.get(Calendar.SECOND) / 3600.0

        val gamma = 2.0 * PI / 365.0 * (dayOfYear - 1 + (utcHours - 12.0) / 24.0)
        val eqTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) -
                0.040849 * sin(2 * gamma)
            )
        val decl = 0.006918 -
            0.399912 * cos(gamma) +
            0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) +
            0.00148 * sin(3 * gamma)

        val trueSolarTimeMin = ((utcHours * 60.0 + eqTime + 4.0 * lon) % 1440.0 + 1440.0) % 1440.0
        val hourAngleDeg = if (trueSolarTimeMin / 4.0 < 0) {
            trueSolarTimeMin / 4.0 + 180.0
        } else {
            trueSolarTimeMin / 4.0 - 180.0
        }
        val hourAngleRad = Math.toRadians(hourAngleDeg)
        val latRad = Math.toRadians(lat)

        val cosZenith = (sin(latRad) * sin(decl) + cos(latRad) * cos(decl) * cos(hourAngleRad))
            .coerceIn(-1.0, 1.0)
        val zenithRad = acos(cosZenith)
        val elevationDeg = (90.0 - Math.toDegrees(zenithRad)).toFloat()

        val denom = cos(latRad) * sin(zenithRad)
        val azimuthDeg = if (abs(denom) < 1e-6) {
            180.0
        } else {
            val cosAz = ((sin(latRad) * cosZenith - sin(decl)) / denom).coerceIn(-1.0, 1.0)
            val baseAz = Math.toDegrees(acos(cosAz))
            if (hourAngleDeg > 0) (baseAz + 180.0) % 360.0 else (540.0 - baseAz) % 360.0
        }

        return azimuthDeg.toFloat() to elevationDeg
    }
}

@Immutable
data class CompassSensorState(
    val trueNorthAzimuth: Float,
    val needsCalibration: Boolean
)

@Composable
fun rememberTrueNorthCompassState(
    latitude: Double,
    longitude: Double,
    enabled: Boolean = true
): CompassSensorState {
    val context = LocalContext.current
    var smoothedAzimuth by remember { mutableFloatStateOf(0f) }
    var accuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }

    val declination = remember(latitude, longitude) {
        GeomagneticField(
            latitude.toFloat(),
            longitude.toFloat(),
            0f,
            System.currentTimeMillis()
        ).declination
    }

    DisposableEffect(context, enabled, declination) {
        if (!enabled) return@DisposableEffect onDispose { }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientation = FloatArray(3)
            private var hasInitial = false

            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)

                val magneticAzimuth = ((Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f)
                val trueAzimuth = (magneticAzimuth + declination + 360f) % 360f

                if (!hasInitial) {
                    smoothedAzimuth = trueAzimuth
                    hasInitial = true
                } else {
                    var delta = trueAzimuth - smoothedAzimuth
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f
                    smoothedAzimuth = (smoothedAzimuth + 0.14f * delta + 360f) % 360f
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, newAccuracy: Int) {
                accuracy = newAccuracy
            }
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    return CompassSensorState(
        trueNorthAzimuth = smoothedAzimuth,
        needsCalibration = accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW ||
            accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
    )
}
