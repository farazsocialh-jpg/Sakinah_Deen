package com.sakinah.deen.domain.sensor

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class QiblaCompassData(
    val azimuthTrueNorthDeg: Float = 0f,
    val kaabaBearingDeg: Float = 0f,
    val needleRelativeAngleDeg: Float = 0f,
    val accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
    val isVehicularTrajectoryActive: Boolean = false,
    val magneticDeclinationDeg: Float = 0f,
    val isAligned: Boolean = false,
    /** Device pitch/roll away from flat; compass headings are only trustworthy when small. */
    val tiltDeg: Float = 0f,
    val isFlat: Boolean = true,
    val hasLocation: Boolean = false,
    val hasSensor: Boolean = true,
    /** True when the raw geomagnetic field is outside the normal 15..80 uT Earth range. */
    val isMagneticInterference: Boolean = false
)

class QiblaSensorManager(
    context: Context
) : SensorEventListener {
    private val appContext = context.applicationContext

    companion object {
        /** Exact WGS-84 Kaaba coordinates requested for the geodesic Qibla calculation. */
        const val KAABA_LATITUDE = 21.422487
        const val KAABA_LONGITUDE = 39.826206

        const val WGS84_SEMI_MAJOR_AXIS_M = 6378137.0
        const val WGS84_FLATTENING = 1.0 / 298.257223563

        private const val VEHICLE_SPEED_THRESHOLD_KMH = 15.0f
        private const val MIN_SMOOTHING_ALPHA = 0.18f
        private const val FAST_SMOOTHING_ALPHA = 0.55f
        private const val FAST_TURN_THRESHOLD_DEG = 45f
        private const val ALIGNMENT_TOLERANCE_DEG = 4f
        private const val FLAT_TILT_THRESHOLD_DEG = 18f
        private const val MAGNETIC_INTERFERENCE_LOW_UT = 15f
        private const val MAGNETIC_INTERFERENCE_HIGH_UT = 80f

        /**
         * WGS-84 Vincenty inverse geodesic initial bearing from [latitude]/[longitude]
         * to the Kaaba. This is the industry-standard ellipsoidal bearing rather than a
         * spherical great-circle approximation.
         */
        fun calculateQiblaBearing(latitude: Double, longitude: Double): Float =
            vincentyInitialBearing(latitude, longitude, KAABA_LATITUDE, KAABA_LONGITUDE)

        /** Backwards-compatible alias used by the existing sensor/location code. */
        fun calculateKaabaBearing(latitude: Double, longitude: Double): Float =
            calculateQiblaBearing(latitude, longitude)

        /** Additional aliases for tests or callers that use a different naming style. */
        fun qiblaBearing(latitude: Double, longitude: Double): Float =
            calculateQiblaBearing(latitude, longitude)

        fun vincentyBearing(latitude: Double, longitude: Double): Float =
            calculateQiblaBearing(latitude, longitude)

        /**
         * General WGS-84 Vincenty inverse geodesic initial bearing. The Kaaba-specific
         * helper above delegates to this so the math can be tested independently.
         */
        fun vincentyInitialBearing(
            latitude1: Double,
            longitude1: Double,
            latitude2: Double,
            longitude2: Double
        ): Float {
            // Coincident points have no defined bearing.
            if (abs(latitude1 - latitude2) < 1e-12 && abs(longitude1 - longitude2) < 1e-12) {
                return 0f
            }

            val f = WGS84_FLATTENING

            val phi1 = Math.toRadians(latitude1)
            val phi2 = Math.toRadians(latitude2)
            val lambdaInput = Math.toRadians(longitude2 - longitude1)

            val u1 = atan((1.0 - f) * tan(phi1))
            val u2 = atan((1.0 - f) * tan(phi2))
            val sinU1 = sin(u1)
            val cosU1 = cos(u1)
            val sinU2 = sin(u2)
            val cosU2 = cos(u2)

            var lambda = lambdaInput
            var sinSigma = 0.0
            var cosSigma = 0.0
            var sinAlpha = 0.0
            var cosSquaredAlpha = 1.0
            var cos2SigmaM = 0.0

            for (iteration in 0 until 100) {
                val sinLambda = sin(lambda)
                val cosLambda = cos(lambda)

                sinSigma = sqrt(
                    (cosU2 * sinLambda) * (cosU2 * sinLambda) +
                        (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) *
                            (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda)
                )

                // Coincident or antipodal special case: no stable bearing.
                if (sinSigma < 1e-15) return 0f

                cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
                val sigma = atan2(sinSigma, cosSigma)

                sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
                cosSquaredAlpha = 1.0 - sinAlpha * sinAlpha
                cos2SigmaM = if (cosSquaredAlpha == 0.0) {
                    0.0
                } else {
                    cosSigma - 2.0 * sinU1 * sinU2 / cosSquaredAlpha
                }

                val c = f / 16.0 * cosSquaredAlpha *
                    (4.0 + f * (4.0 - 3.0 * cosSquaredAlpha))

                val previousLambda = lambda
                lambda = lambdaInput + (1.0 - c) * f * sinAlpha * (
                    sigma + c * sinSigma * (
                        cos2SigmaM + c * cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM)
                    )
                )

                if (abs(lambda - previousLambda) <= 1e-12) break
            }

            val alpha1 = atan2(
                cosU2 * sin(lambda),
                cosU1 * sinU2 - sinU1 * cosU2 * cos(lambda)
            )
            return ((Math.toDegrees(alpha1) + 360.0) % 360.0).toFloat()
        }
    }

    private val sensorManager: SensorManager? =
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val hasRotationVectorSensor: Boolean
        get() = rotationVectorSensor != null

    private val rotationVectorSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometerSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _compassFlow = MutableStateFlow(QiblaCompassData(hasSensor = hasCompassSensor))
    val compassFlow: StateFlow<QiblaCompassData> = _compassFlow.asStateFlow()

    // Buffers for fallback sensor calculation
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val remappedRotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var hasAccelerometer = false
    private var hasMagnetometer = false
    private var smoothedAzimuth = 0f
    private var hasHeading = false
    private var tiltDeg = 0f
    private var sensorAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
    private var magneticInterference = false

    // Location state for GeomagneticField declination and Kaaba bearing
    private var lastKnownLocation: Location? = null
    private var magneticDeclination = 0f
    private var kaabaBearing = 0f
    private var isListening = false

    val hasCompassSensor: Boolean
        get() = rotationVectorSensor != null ||
            (accelerometerSensor != null && magnetometerSensor != null)

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

        // Industry-standard ellipsoidal Qibla bearing.
        kaabaBearing = calculateKaabaBearing(location.latitude, location.longitude)

        val isVehicular = location.hasSpeed() &&
            (location.speed * 3.6f) > VEHICLE_SPEED_THRESHOLD_KMH
        evaluateHeadingOutput(smoothedAzimuth, tiltDeg, isVehicular = isVehicular)
    }

    fun startListening() {
        if (sensorManager == null || isListening) return
        isListening = true

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(
                this,
                rotationVectorSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            // Keep the magnetometer active even with a rotation vector so magnetic
            // interference can be detected.
            magnetometerSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
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
        isListening = false
        hasAccelerometer = false
        hasMagnetometer = false
        hasHeading = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val location = lastKnownLocation
        val isVehicleMovingFast = location != null &&
                location.hasSpeed() &&
                (location.speed * 3.6f) > VEHICLE_SPEED_THRESHOLD_KMH

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                sensorAccuracy = event.accuracy
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                // Remap the device frame to the current screen orientation. The pitch/roll
                // are still derived from the resulting orientation, so a phone held
                // upright or flat keeps a consistent azimuth reference.
                remapForDisplay(rotationMatrix, remappedRotationMatrix)
                SensorManager.getOrientation(remappedRotationMatrix, orientationAngles)
                val magneticAzimuthDeg = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360.0) % 360.0).toFloat()
                val trueNorthAzimuth = (magneticAzimuthDeg + magneticDeclination + 360f) % 360f
                tiltDeg = calculateTilt(orientationAngles)

                val vehicleHeading = location?.takeIf { isVehicleMovingFast && it.hasBearing() }
                if (vehicleHeading != null) {
                    // Over the vehicular speed threshold: prioritize the true-north GPS
                    // trajectory, but keep processing tilt/magnetic sensors below.
                    applyHeading((vehicleHeading.bearing + 360f) % 360f, isVehicular = true)
                } else {
                    applyHeading(trueNorthAzimuth, isVehicular = false)
                }
            }

            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, 3)
                hasAccelerometer = true
                computeFallbackOrientation()
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                val magnitude = sqrt(
                    event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]
                )
                magneticInterference = magnitude < MAGNETIC_INTERFERENCE_LOW_UT ||
                    magnitude > MAGNETIC_INTERFERENCE_HIGH_UT

                if (rotationVectorSensor == null) {
                    System.arraycopy(event.values, 0, magnetometerReading, 0, 3)
                    hasMagnetometer = true
                    computeFallbackOrientation()
                } else {
                    _compassFlow.value = _compassFlow.value.copy(
                        isMagneticInterference = magneticInterference
                    )
                }
            }
        }
    }

    private fun applyHeading(trueNorthHeading: Float, isVehicular: Boolean) {
        val normalized = normalizeDegrees(trueNorthHeading)
        if (!hasHeading) {
            // Snap to the first valid sample; averaging from 0 makes the dial swing for
            // several seconds before it points anywhere useful.
            smoothedAzimuth = normalized
            hasHeading = true
        } else {
            smoothedAzimuth = smoothAngle(smoothedAzimuth, normalized)
        }
        evaluateHeadingOutput(smoothedAzimuth, tiltDeg, isVehicular)
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
            remapForDisplay(rotationMatrix, remappedRotationMatrix)
            SensorManager.getOrientation(remappedRotationMatrix, orientationAngles)
            val magneticAzimuthDeg = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360.0) % 360.0).toFloat()
            val trueNorthAzimuth = (magneticAzimuthDeg + magneticDeclination + 360f) % 360f
            tiltDeg = calculateTilt(orientationAngles)

            applyHeading(trueNorthAzimuth, isVehicular = false)
        }
    }

    /**
     * Rotates the sensor frame into the current display orientation. Without this a
     * landscape tablet reports headings 90° off from what the user sees at the top of
     * the screen.
     */
    private fun remapForDisplay(source: FloatArray, destination: FloatArray) {
        when (currentDisplayRotation()) {
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                source,
                SensorManager.AXIS_Y,
                SensorManager.AXIS_MINUS_X,
                destination
            )

            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                source,
                SensorManager.AXIS_MINUS_X,
                SensorManager.AXIS_MINUS_Y,
                destination
            )

            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                source,
                SensorManager.AXIS_MINUS_Y,
                SensorManager.AXIS_X,
                destination
            )

            else -> System.arraycopy(source, 0, destination, 0, 9)
        }
    }

    private fun currentDisplayRotation(): Int {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            appContext.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }
    }

    private fun calculateTilt(orientation: FloatArray): Float {
        val pitchDeg = Math.toDegrees(orientation[1].toDouble())
        val rollDeg = Math.toDegrees(orientation[2].toDouble())
        return sqrt(pitchDeg * pitchDeg + rollDeg * rollDeg).toFloat().coerceIn(0f, 90f)
    }

    /**
     * Circular exponential moving average on [0, 360). A larger correction is applied
     * for big turns so the dial catches up quickly, while small tremors are damped.
     */
    private fun smoothAngle(currentSmoothed: Float, targetNew: Float): Float {
        var diff = (targetNew - currentSmoothed) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f

        val alpha = if (abs(diff) > FAST_TURN_THRESHOLD_DEG) {
            FAST_SMOOTHING_ALPHA
        } else {
            MIN_SMOOTHING_ALPHA
        }
        return normalizeDegrees(currentSmoothed + alpha * diff)
    }

    private fun evaluateHeadingOutput(
        headingTrueNorth: Float,
        tilt: Float,
        isVehicular: Boolean
    ) {
        val relativeNeedleAngle = normalizeDegrees(kaabaBearing - headingTrueNorth)
        val signedDelta = signedAngleDelta(relativeNeedleAngle)
        val aligned = abs(signedDelta) <= ALIGNMENT_TOLERANCE_DEG
        val isFlat = tilt <= FLAT_TILT_THRESHOLD_DEG

        _compassFlow.value = QiblaCompassData(
            azimuthTrueNorthDeg = headingTrueNorth,
            kaabaBearingDeg = kaabaBearing,
            needleRelativeAngleDeg = relativeNeedleAngle,
            accuracy = sensorAccuracy,
            isVehicularTrajectoryActive = isVehicular,
            magneticDeclinationDeg = magneticDeclination,
            isAligned = aligned,
            tiltDeg = tilt,
            isFlat = isFlat,
            hasLocation = lastKnownLocation != null,
            hasSensor = hasCompassSensor,
            isMagneticInterference = magneticInterference
        )
    }

    private fun signedAngleDelta(angle: Float): Float {
        var delta = angle % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    private fun normalizeDegrees(angle: Float): Float = (angle % 360f + 360f) % 360f

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        sensorAccuracy = accuracy
        _compassFlow.value = _compassFlow.value.copy(accuracy = accuracy)
    }
}
