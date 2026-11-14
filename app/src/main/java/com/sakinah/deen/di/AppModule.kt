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

// SakinahContainer is deprecated; all dependencies are provided via KoinModules.
// This class is kept for backward compatibility but should not be used in new code.
// All dependencies should be obtained through the Koin context instead.
class SakinahContainer(private val context: Context) {

    @Deprecated("Use Koin injection instead; this container is a duplicate source of truth.")
    private val dbPassphrase: ByteArray by lazy {
        com.sakinah.deen.data.database.DatabasePassphraseProvider.getPassphrase(context)
    }

    @Deprecated("Use Koin injection instead; this container is a duplicate source of truth.")
    val database: SakinahEncryptedDatabase by lazy {
        SakinahEncryptedDatabase.getInstance(context, dbPassphrase)
    }

    @Deprecated("Use Koin injection instead; this container is a duplicate source of truth.")
    val salahDao: SalahDao by lazy {
        database.salahDao()
    }

    @Deprecated("Use Koin injection instead; this container is a duplicate source of truth.")
    val qadhaDao: QadhaDao by lazy {
        database.qadhaDao()
    }

    @Deprecated("Use Koin injection instead; this container is a duplicate source of truth.")
    val hifzDao: HifzDao by lazy {
        database.hifzDao()
    }

    @Deprecated("Use Koin injection instead; this container is a duplicate source of truth.")
    val sadaqahDao: SadaqahDao by lazy {
        database.sadaqahDao()
    }

    @Deprecated("Use Koin injection instead; this container is a duplicate source of truth.")
    val astronomyEngine: PrayerCalculationEngine by lazy {
        PrayerCalculationEngineImpl()
    }

    @Deprecated("Use Koin injection instead; this container is a duplicate source of truth.")
    val travelManager: TravelStatusManager by lazy {
        TravelStatusManager()
    }

    @Deprecated("Use Koin injection instead; this container is a duplicate source of truth.")
    val tafseerRepository: TafseerDownloadRepository by lazy {
        TafseerDownloadRepository(context, database.tafsirDao())
    }
}
