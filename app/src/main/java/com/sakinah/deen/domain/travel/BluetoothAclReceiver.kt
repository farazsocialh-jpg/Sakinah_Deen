package com.sakinah.deen.domain.travel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat

class BluetoothAclReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "sakinah_travel_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }

            val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                "In-Car Audio"
            } else {
                device?.name ?: "In-Car Audio"
            }

            // Dispatch notification to recite Dua al-Safar
            dispatchDuaAlSafarNotification(context, deviceName)
        }
    }

    private fun dispatchDuaAlSafarNotification(context: Context, deviceName: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Travel & Safar Concessions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for vehicle audio connection and Dua al-Safar recitation"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Vehicle Connected · $deviceName")
            .setContentText("Dua al-Safar: سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَٰذَا")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَٰذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ ، وَإِنَّا إِلَىٰ رَبِّنَا لَمُنْقَلِبُونَ\n" +
                            "Glory be to Him Who has subjected this to us. Travel Safar concessions active."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
