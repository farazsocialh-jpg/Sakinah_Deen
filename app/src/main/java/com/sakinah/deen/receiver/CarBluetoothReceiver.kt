package com.sakinah.deen.receiver

import android.Manifest
import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sakinah.deen.MainActivity
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.domain.travel.TravelStatusManager
import com.sakinah.deen.service.AdhanPlaybackService
import com.sakinah.deen.service.NotificationChannels

/**
 * Vehicle handshake: a recognised car audio head unit triggers the Dua al-Safar
 * prompt and refreshes Qasr eligibility from the last fused fix relative to the
 * saved home anchor.
 */
class CarBluetoothReceiver : BroadcastReceiver() {

    companion object {
        const val VEHICLE_NOTIFICATION_ID = 5001
        private const val ACTION_OPEN_TRAVEL = "travel"

        private val CAR_DEVICE_CLASSES = setOf(
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
            BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> handleConnected(context, intent)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> handleDisconnected(context)
        }
    }

    private fun handleConnected(context: Context, intent: Intent) {
        val device = extractDevice(intent) ?: return
        val preferences = SakinahPreferences(context)

        if (!isRecognisedCar(context, device, preferences)) return

        val carName = safeDeviceName(context, device) ?: "Vehicle Audio System"
        preferences.travelModeActive = true

        val travelState = updateTravelState(context, preferences)

        NotificationChannels.createChannels(context)
        val playbackStarted = startSafarPlaybackSafely(context)
        if (!playbackStarted) {
            showVehiclePromptNotification(
                context = context,
                carName = carName,
                qasrActive = travelState?.isQasrActive == true,
                distanceKm = travelState?.distanceKm
            )
        }
    }

    /**
     * Android 12+ forbids starting a foreground service from the background, and a
     * broadcast receiver triggered by a Bluetooth handshake counts as background.
     * Returning false lets the caller fall back to the high-priority notification
     * whose action starts playback from a user tap instead of crashing.
     */
    private fun startSafarPlaybackSafely(context: Context): Boolean {
        val serviceIntent = Intent(context, AdhanPlaybackService::class.java).apply {
            action = AdhanPlaybackService.ACTION_PLAY_DUA_SAFAR
        }
        return try {
            ContextCompat.startForegroundService(context, serviceIntent)
            true
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            ) {
                // Expected on Android 12+: the handshake is a background start.
            }
            // Either way the high-priority notification action remains available.
            false
        }
    }

    private fun handleDisconnected(context: Context) {
        SakinahPreferences(context).travelModeActive = false
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(VEHICLE_NOTIFICATION_ID)
    }

    /**
     * A car is recognised when it matches the user-saved MAC/name, or when it
     * advertises the standard car-audio Bluetooth class.
     */
    @SuppressLint("MissingPermission")
    private fun isRecognisedCar(
        context: Context,
        device: BluetoothDevice,
        preferences: SakinahPreferences
    ): Boolean {
        val savedAddress = preferences.carBluetoothAddress
        if (!savedAddress.isNullOrBlank()) {
            val address = runCatching { device.address }.getOrNull()
            if (address != null && address.equals(savedAddress, ignoreCase = true)) return true
        }

        val savedName = preferences.carBluetoothName
        val deviceName = safeDeviceName(context, device)
        if (!savedName.isNullOrBlank() && deviceName != null &&
            deviceName.contains(savedName, ignoreCase = true)
        ) {
            return true
        }

        val deviceClass = runCatching { device.bluetoothClass?.deviceClass }.getOrNull()
        if (deviceClass in CAR_DEVICE_CLASSES) return true

        // Hands-free profile bit is set on most aftermarket head units.
        val majorClass = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull()
        return majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO && deviceClass == null
    }

    /** BLUETOOTH_CONNECT is a runtime permission on Android 12+; reading name without it throws. */
    private fun safeDeviceName(context: Context, device: BluetoothDevice): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return runCatching { device.name }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private fun extractDevice(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    /**
     * Uses the device's last known fix when location permission is present. Returns
     * null when no fix exists, leaving the previous persisted state untouched.
     */
    private fun updateTravelState(
        context: Context,
        preferences: SakinahPreferences
    ): com.sakinah.deen.domain.travel.TravelState? {
        val home = preferences.homeCoordinates ?: return null
        val location = lastKnownLocation(context) ?: return null

        val travelManager = TravelStatusManager(home)
        val state = travelManager.evaluateTravelState(Pair(location.latitude, location.longitude))

        preferences.lastDistanceFromHomeKm = state.distanceKm.toFloat()
        preferences.qasrActive = state.isQasrActive
        return state
    }

    private fun lastKnownLocation(context: Context): Location? {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return null

        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        return try {
            locationManager.getProviders(true)
                .mapNotNull { provider ->
                    runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
                }
                .maxByOrNull { it.time }
        } catch (e: SecurityException) {
            null
        }
    }

    private fun showVehiclePromptNotification(
        context: Context,
        carName: String,
        qasrActive: Boolean,
        distanceKm: Double?
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", ACTION_OPEN_TRAVEL)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            101,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playDuaPendingIntent = PendingIntent.getService(
            context,
            102,
            Intent(context, AdhanPlaybackService::class.java).apply {
                action = AdhanPlaybackService.ACTION_PLAY_DUA_SAFAR
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val distanceLine = distanceKm?.let { "%.0f km from home".format(it) }
        val qasrLine = when {
            qasrActive -> "Journey exceeds ${TravelStatusManager.TRAVEL_THRESHOLD_KM.toInt()} km — Qasr (2 Rak'ah) is active."
            distanceLine != null -> "$distanceLine — Qasr not yet applicable."
            else -> "Allow location to calculate Qasr eligibility."
        }

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_TRAVEL_SAFAR)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("Connected to $carName")
            .setContentText("سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَٰذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Entering vehicular travel mode. $qasrLine\n\n" +
                        "Dua al-Safar: سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَٰذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ ، " +
                        "وَإِنَّا إِلَىٰ رَبِّنَا لَمُنْقَلِبُونَ"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_media_play, "Play Dua al-Safar", playDuaPendingIntent)
            .addAction(android.R.drawable.ic_menu_compass, "Open Road Qibla", openAppPendingIntent)
            .build()

        notificationManager.notify(VEHICLE_NOTIFICATION_ID, notification)
    }
}
