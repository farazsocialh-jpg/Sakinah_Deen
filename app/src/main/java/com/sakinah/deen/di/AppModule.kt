package com.sakinah.deen.di

import android.content.Context
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.data.database.dao.HifzDao
import com.sakinah.deen.data.database.dao.QadhaDao
import com.sakinah.deen.data.database.dao.SadaqahDao
import com.sakinah.deen.data.database.dao.SalahDao
import com.sakinah.deen.data.repository.TafseerDownloadRepository
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngine
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl
import com.sakinah.deen.domain.travel.TravelStatusManager

class SakinahContainer(private val context: Context) {

    private val dbPassphrase: ByteArray by lazy {
        com.sakinah.deen.data.database.DatabasePassphraseProvider.getPassphrase(context)
    }

    val database: SakinahEncryptedDatabase by lazy {
        SakinahEncryptedDatabase.getInstance(context, dbPassphrase)
    }

    val salahDao: SalahDao by lazy {
        database.salahDao()
    }

    val qadhaDao: QadhaDao by lazy {
        database.qadhaDao()
    }

    val hifzDao: HifzDao by lazy {
        database.hifzDao()
    }

    val sadaqahDao: SadaqahDao by lazy {
        database.sadaqahDao()
    }

    val astronomyEngine: PrayerCalculationEngine by lazy {
        PrayerCalculationEngineImpl()
    }

    val travelManager: TravelStatusManager by lazy {
        TravelStatusManager()
    }

    val tafseerRepository: TafseerDownloadRepository by lazy {
        TafseerDownloadRepository(context, database.tafsirDao())
    }
}
