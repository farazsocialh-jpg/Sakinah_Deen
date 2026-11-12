package com.sakinah.deen.domain.sensor

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class QiblaCompassData(
    val azimuthTrueNorthDeg: Float = 0f,
    val kaabaBearingDeg: Float = 118f,
    val needleRelativeAngleDeg: Float = 118f,
    val accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
    val isVehicularTrajectoryActive: Boolean = false,
    val magneticDeclinationDeg: Float = 0f
)

class QiblaSensorManager(
    private val context: Context
) : SensorEventListener {

    companion object {
        private const val KAABA_LATITUDE = 21.4225
        private const val KAABA_LONGITUDE = 39.8262
        private const val VEHICLE_SPEED_THRESHOLD_KMH = 15.0f
        private const val EMA_ALPHA = 0.15f // Exponential moving average damping factor
    }

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val rotationVectorSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometerSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _compassFlow = MutableStateFlow(QiblaCompassData())
    val compassFlow: StateFlow<QiblaCompassData> = _compassFlow.asStateFlow()

    // Buffers for fallback sensor calculation
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var hasAccelerometer = false
    private var hasMagnetometer = false
    private var smoothedAzimuth = 0f
    private var sensorAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH

    // Location state for GeomagneticField declination and Kaaba bearing
    private var lastKnownLocation: Location? = null
    private var magneticDeclination = 0f
    private var kaabaBearing = 118f // Default London-to-Mecca bearing

    fun updateLocation(location: Location) {
        this.lastKnownLocation = location

        // Calculate magnetic declination to true north
        val geomagneticField = GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            location.altitude.toFloat(),
            System.currentTimeMillis()
        )
        magneticDeclination = geomagneticField.declination

        // Calculate direct Great-Circle bearing towards the Holy Kaaba
        kaabaBearing = calculateKaabaBearing(location.latitude, location.longitude)

        evaluateHeadingOutput(smoothedAzimuth)
    }

    fun startListening() {
        if (sensorManager == null) return

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(
                this,
                rotationVectorSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        } else {
            accelerometerSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            magnetometerSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val location = lastKnownLocation
        val isVehicleMovingFast = location != null &&
                location.hasSpeed() &&
                (location.speed * 3.6f) > VEHICLE_SPEED_THRESHOLD_KMH

        if (isVehicleMovingFast && location.hasBearing()) {
            // Over vehicular speed threshold (>15 km/h): prioritize GPS trajectory heading
            val vehicleHeading = location.bearing
            smoothedAzimuth = smoothAngle(smoothedAzimuth, vehicleHeading)
            evaluateHeadingOutput(smoothedAzimuth, isVehicular = true)
            return
        }

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val magneticAzimuthRad = orientationAngles[0]
                val magneticAzimuthDeg = (Math.toDegrees(magneticAzimuthRad.toDouble()).toFloat() + 360f) % 360f
                val trueNorthAzimuth = (magneticAzimuthDeg + magneticDeclination + 360f) % 360f

                smoothedAzimuth = smoothAngle(smoothedAzimuth, trueNorthAzimuth)
                evaluateHeadingOutput(smoothedAzimuth, isVehicular = false)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, 3)
                hasAccelerometer = true
                computeFallbackOrientation()
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, 3)
                hasMagnetometer = true
                computeFallbackOrientation()
            }
        }
    }

    private fun computeFallbackOrientation() {
        if (!hasAccelerometer || !hasMagnetometer) return

        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        if (success) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val magneticAzimuthRad = orientationAngles[0]
            val magneticAzimuthDeg = (Math.toDegrees(magneticAzimuthRad.toDouble()).toFloat() + 360f) % 360f
            val trueNorthAzimuth = (magneticAzimuthDeg + magneticDeclination + 360f) % 360f

            smoothedAzimuth = smoothAngle(smoothedAzimuth, trueNorthAzimuth)
            evaluateHeadingOutput(smoothedAzimuth, isVehicular = false)
        }
    }

    /**
     * Exponential Moving Average (EMA) on circular domain [0, 360)
     * Handles 359° -> 1° discontinuity to eliminate needle wrapping jitter
     */
    private fun smoothAngle(currentSmoothed: Float, targetNew: Float): Float {
        var diff = (targetNew - currentSmoothed) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return (currentSmoothed + EMA_ALPHA * diff + 360f) % 360f
    }

    private fun evaluateHeadingOutput(headingTrueNorth: Float, isVehicular: Boolean = false) {
        val relativeNeedleAngle = (kaabaBearing - headingTrueNorth + 360f) % 360f

        _compassFlow.value = QiblaCompassData(
            azimuthTrueNorthDeg = headingTrueNorth,
            kaabaBearingDeg = kaabaBearing,
            needleRelativeAngleDeg = relativeNeedleAngle,
            accuracy = sensorAccuracy,
            isVehicularTrajectoryActive = isVehicular,
            magneticDeclinationDeg = magneticDeclination
        )
    }

    private fun calculateKaabaBearing(lat: Double, lon: Double): Float {
        val phi1 = Math.toRadians(lat)
        val phi2 = Math.toRadians(KAABA_LATITUDE)
        val deltaLambda = Math.toRadians(KAABA_LONGITUDE - lon)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val bearingRad = atan2(y, x)
        return ((Math.toDegrees(bearingRad) + 360.0) % 360.0).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        sensorAccuracy = accuracy
        _compassFlow.value = _compassFlow.value.copy(accuracy = accuracy)
    }
}
