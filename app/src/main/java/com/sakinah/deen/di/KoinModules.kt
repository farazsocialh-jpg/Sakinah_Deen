package com.sakinah.deen.di

import com.sakinah.deen.data.database.DatabaseSeeder
import com.sakinah.deen.data.database.OfflineContentSeeder
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.data.prayer.AlAdhanApiClient
import com.sakinah.deen.data.prayer.PrayerMethodSettings
import com.sakinah.deen.data.prefs.PreferencesPrayerMethodSettings
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.repository.NisabRateRepository
import com.sakinah.deen.data.repository.NisabRateRepositoryImpl
import com.sakinah.deen.data.repository.PrayerTimeRepository
import com.sakinah.deen.data.repository.PrayerTimeRepositoryImpl
import com.sakinah.deen.data.repository.PreferencesNisabRateCache
import com.sakinah.deen.data.repository.SharedPreferencesNisabRateCacheStore
import com.sakinah.deen.data.repository.TafseerDownloadRepository
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngine
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngineImpl
import com.sakinah.deen.domain.astronomy.UserLocationProvider
import com.sakinah.deen.domain.sensor.QiblaSensorManager
import com.sakinah.deen.domain.travel.TravelStatusManager
import com.sakinah.deen.network.IslamicDataRepository
import com.sakinah.deen.network.SunniAiClient
import com.sakinah.deen.network.SunniAiScholarRepository
import com.sakinah.deen.network.SunniAiScholarRepositoryImpl
import com.sakinah.deen.ui.viewmodel.AiScholarViewModel
import com.sakinah.deen.ui.viewmodel.HadithViewModel
import com.sakinah.deen.ui.viewmodel.QuranViewModel
import com.sakinah.deen.ui.viewmodel.SalahViewModel
import com.sakinah.deen.ui.viewmodel.TravelViewModel
import com.sakinah.deen.ui.viewmodel.WealthViewModel
import com.sakinah.deen.work.KoinWorkerFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val preferencesModule = module {
    single { SakinahPreferences(androidContext()) }
}

val databaseModule = module {
    single {
        val dbPassphrase = com.sakinah.deen.data.database.DatabasePassphraseProvider.getPassphrase(androidContext())
        SakinahEncryptedDatabase.getInstance(androidContext(), dbPassphrase)
    }
    single { get<SakinahEncryptedDatabase>().salahDao() }
    single { get<SakinahEncryptedDatabase>().qadhaDao() }
    single { get<SakinahEncryptedDatabase>().hifzDao() }
    single { get<SakinahEncryptedDatabase>().sadaqahDao() }
    single { get<SakinahEncryptedDatabase>().quranDao() }
    single { get<SakinahEncryptedDatabase>().hadithDao() }
    single { get<SakinahEncryptedDatabase>().tafsirDao() }
    single { get<SakinahEncryptedDatabase>().adhkarDao() }
    single { get<SakinahEncryptedDatabase>().prayerTimetableDao() }
    single {
        OfflineContentSeeder(
            readAsset = DatabaseSeeder.assetReader(androidContext()),
            content = {
                runCatching { get<SakinahEncryptedDatabase>() }
                    .getOrNull()
                    ?.let(DatabaseSeeder::seedContentOf)
            }
        )
    }
}

val astronomyModule = module {
    single<PrayerCalculationEngine> { PrayerCalculationEngineImpl() }
    single { UserLocationProvider(androidContext()) }
}

val travelModule = module {
    single {
        // No saved Home means no Qasr. Never fall back to a placeholder city, or a
        // user far from that city would see 2-rakat Safar without ever setting Home.
        TravelStatusManager(get<SakinahPreferences>().homeCoordinates)
    }
    single { QiblaSensorManager(androidContext()) }
}

val networkModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }
    }
    single {
        HttpClient(OkHttp) {
            engine {
                config {
                    cache(okhttp3.Cache(java.io.File(androidContext().cacheDir, "http_cache"), 10L * 1024 * 1024))
                }
            }
            // Endpoints are free public services: surface status codes as values and
            // let repositories decide how to degrade instead of throwing on 4xx/5xx.
            expectSuccess = false
            install(ContentNegotiation) { json(get<Json>()) }
            install(HttpTimeout) {
                requestTimeoutMillis = 90_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 90_000
            }
            install(io.ktor.client.plugins.HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 3)
                retryIf { _, response -> response.status.value == 429 }
                exponentialDelay()
            }
        }
    }
    single { SunniAiClient(httpClient = get(), json = get()) }
    single {
        IslamicDataRepository(
            quranDao = get(),
            hadithDao = get(),
            tafsirDao = get(),
            client = get(),
            json = get(),
            offlineContent = getOrNull(),
            appContext = androidContext()
        )
    }
    single<SunniAiScholarRepository> {
        SunniAiScholarRepositoryImpl(aiClient = get())
    }
}

val prayerTimeModule = module {
    single<PrayerMethodSettings> { PreferencesPrayerMethodSettings(get()) }
    single { AlAdhanApiClient(client = get(), json = get()) }
    single<PrayerTimeRepository> {
        PrayerTimeRepositoryImpl(
            dao = get(),
            apiClient = get(),
            engine = get(),
            settings = get()
        )
    }
}

val repositoryModule = module {
    single { TafseerDownloadRepository(androidContext()) }
    single<NisabRateRepository> {
        val prefs = androidContext().getSharedPreferences("sakinah_nisab_rates", android.content.Context.MODE_PRIVATE)
        NisabRateRepositoryImpl(
            client = get(),
            json = get(),
            cache = PreferencesNisabRateCache(SharedPreferencesNisabRateCacheStore(prefs))
        )
    }
}

val workerModule = module {
    single { KoinWorkerFactory(get()) }
}

val accountModule = module {
    single<com.russhwolf.settings.Settings>(qualifier = org.koin.core.qualifier.named("account_settings")) {
        com.russhwolf.settings.SharedPreferencesSettings(
            androidContext().getSharedPreferences("sakinah_account_settings", android.content.Context.MODE_PRIVATE)
        )
    }
    single<com.sakinah.deen.auth.AuthRepository> {
        com.sakinah.deen.auth.PersistentAuthRepository(
            prefs = get(org.koin.core.qualifier.named("account_settings"))
        )
    }
    single<com.sakinah.deen.profile.UserProfileRepository> {
        com.sakinah.deen.profile.PersistentUserProfileRepository(
            prefs = get(org.koin.core.qualifier.named("account_settings"))
        )
    }
    single<com.sakinah.deen.settings.SettingsRepository> {
        com.sakinah.deen.settings.SettingsRepositoryImpl(
            prefs = get(org.koin.core.qualifier.named("account_settings")),
            cacheBytesProvider = {
                runCatching {
                    androidContext().cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                }.getOrDefault(0L)
            },
            onClearCache = {
                runCatching {
                    androidContext().cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                }
            }
        )
    }
}

val viewModelModule = module {
    factory { SalahViewModel(get(), get(), get(), get(), getOrNull(), getOrNull(), getOrNull(), getOrNull()) }
    factory { QuranViewModel(getOrNull(), getOrNull(), getOrNull(), getOrNull(), getOrNull(), getOrNull()) }
    factory { HadithViewModel(getOrNull(), getOrNull(), getOrNull()) }
    factory { TravelViewModel(get(), get(), getOrNull()) }
    factory { WealthViewModel(get(), getOrNull()) }
    factory { AiScholarViewModel(get()) }
}

val appModules = listOf(
    accountModule,
    preferencesModule,
    databaseModule,
    astronomyModule,
    travelModule,
    networkModule,
    prayerTimeModule,
    repositoryModule,
    workerModule,
    viewModelModule
)
