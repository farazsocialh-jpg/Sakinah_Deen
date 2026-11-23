package com.sakinah.deen.service

import android.content.Context
import android.net.Uri
import androidx.annotation.RawRes
import androidx.core.net.toUri
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
            return resourceUri(context, R.raw.adhan_fajr)
        }
        return resourceUri(context, muezzin.rawRes)
    }

    /** Short alert tone for "Short Tone" mode. */
    fun shortToneUri(context: Context): Uri = resourceUri(context, R.raw.short_tone)

    private fun resourceUri(context: Context, @RawRes resId: Int): Uri =
        "android.resource://${context.packageName}/$resId".toUri()
}
