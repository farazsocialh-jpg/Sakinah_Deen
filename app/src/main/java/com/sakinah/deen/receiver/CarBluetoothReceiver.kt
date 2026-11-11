package com.sakinah.deen.receiver

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sakinah.deen.MainActivity
import com.sakinah.deen.service.AdhanPlaybackService
import com.sakinah.deen.service.NotificationChannels

class CarBluetoothReceiver : BroadcastReceiver() {

    companion object {
        const val VEHICLE_NOTIFICATION_ID = 5001
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                if (device != null && isCarAudioDevice(device)) {
                    val deviceName = try {
                        device.name ?: "Vehicle Audio System"
                    } catch (e: SecurityException) {
                        "Vehicle Audio System"
                    }
                    showVehiclePromptNotification(context, deviceName)
                }
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(VEHICLE_NOTIFICATION_ID)
            }
        }
    }

    private fun isCarAudioDevice(device: BluetoothDevice): Boolean {
        val btClass = device.bluetoothClass ?: return false
        val deviceClass = btClass.deviceClass

        return deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
                deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE ||
                deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES
    }

    private fun showVehiclePromptNotification(context: Context, carName: String) {
        NotificationChannels.createChannels(context)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // 1. Open Road Qibla Screen in App
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "travel")
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            101,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Immediate audio action: Stream Dua al-Safar over Car Bluetooth
        val playDuaIntent = Intent(context, AdhanPlaybackService::class.java).apply {
            action = AdhanPlaybackService.ACTION_PLAY_DUA_SAFAR
        }
        val playDuaPendingIntent = PendingIntent.getService(
            context,
            102,
            playDuaIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_TRAVEL_SAFAR)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("Connected to $carName")
            .setContentText("سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَٰذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Entering vehicular travel mode. Would you like to recite Dua al-Safar and activate Qibla road tracking?")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_media_play, "Play Dua al-Safar", playDuaPendingIntent)
            .addAction(android.R.drawable.ic_menu_compass, "Open Road Qibla", openAppPendingIntent)
            .build()

        notificationManager.notify(VEHICLE_NOTIFICATION_ID, notification)
    }
}
