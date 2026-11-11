package com.sakinah.deen.di

import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.data.repository.TafseerDownloadRepository
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngine
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl
import com.sakinah.deen.domain.travel.TravelStatusManager
import com.sakinah.deen.network.SunniAiScholarRepository
import com.sakinah.deen.network.SunniAiScholarRepositoryImpl
import com.sakinah.deen.ui.viewmodel.AiScholarViewModel
import com.sakinah.deen.ui.viewmodel.HadithViewModel
import com.sakinah.deen.ui.viewmodel.QuranViewModel
import com.sakinah.deen.ui.viewmodel.SalahViewModel
import com.sakinah.deen.ui.viewmodel.TravelViewModel
import com.sakinah.deen.ui.viewmodel.WealthViewModel
import org.koin.dsl.module

val databaseModule = module {
    single {
        val dbPassphrase = "SakinahSecureOfflineMasterKey2026".toByteArray(Charsets.UTF_8)
        SakinahEncryptedDatabase.getInstance(get(), dbPassphrase)
    }
    single { get<SakinahEncryptedDatabase>().salahDao() }
    single { get<SakinahEncryptedDatabase>().qadhaDao() }
    single { get<SakinahEncryptedDatabase>().hifzDao() }
    single { get<SakinahEncryptedDatabase>().sadaqahDao() }
}

val astronomyModule = module {
    single<PrayerCalculationEngine> { PrayerCalculationEngineImpl() }
}

val travelModule = module {
    single { TravelStatusManager() }
}

val networkModule = module {
    single<SunniAiScholarRepository> { SunniAiScholarRepositoryImpl() }
}

val repositoryModule = module {
    single { TafseerDownloadRepository(get()) }
}

val viewModelModule = module {
    factory { SalahViewModel(get(), get(), get(), get()) }
    factory { QuranViewModel(get(), get()) }
    factory { HadithViewModel() }
    factory { TravelViewModel(get(), get()) }
    factory { WealthViewModel(get()) }
    factory { AiScholarViewModel(get()) }
}

val appModules = listOf(
    databaseModule,
    astronomyModule,
    travelModule,
    networkModule,
    repositoryModule,
    viewModelModule
)
