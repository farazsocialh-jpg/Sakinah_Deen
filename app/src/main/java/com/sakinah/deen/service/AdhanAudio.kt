package com.sakinah.deen.service

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import com.sakinah.deen.R
import com.sakinah.deen.data.model.Muezzin

/** Resolves bundled Muezzin recordings with a safe fallback chain. */
object AdhanAudio {

    fun muezzinUri(
        context: Context,
        muezzin: Muezzin,
        fajrSpecialAdhan: Boolean = false,
        prayerTypeName: String? = null
    ): Uri {
        val isShortTakbir = muezzin == Muezzin.TAKBIR_AQSA || muezzin == Muezzin.TAKBIR_CAIRO
        if (fajrSpecialAdhan && !isShortTakbir && prayerTypeName.equals("FAJR", ignoreCase = true)) {
            return resourceUri(context, "adhan_fajr", R.raw.adhan_fajr)
        }
        return resourceUri(context, muezzin.rawName, R.raw.adhan_makkah)
    }

    /** Short alert tone for "Short Tone" mode. */
    fun shortToneUri(context: Context): Uri {
        val bundled = context.resources.getIdentifier("short_tone", "raw", context.packageName)
        if (bundled != 0) {
            return Uri.parse("android.resource://${context.packageName}/$bundled")
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI
    }

    private fun resourceUri(context: Context, name: String, fallback: Int): Uri {
        val id = context.resources.getIdentifier(name, "raw", context.packageName)
            .takeIf { it != 0 }
            ?: fallback
        return Uri.parse("android.resource://${context.packageName}/$id")
    }
}
