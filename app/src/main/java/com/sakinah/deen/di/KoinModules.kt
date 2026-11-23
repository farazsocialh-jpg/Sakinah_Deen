package com.sakinah.deen.di

import androidx.core.content.edit
import com.sakinah.deen.data.database.DatabaseSeeder
import com.sakinah.deen.data.database.OfflineContentSeeder
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.data.prayer.AlAdhanApiClient
import com.sakinah.deen.data.prayer.PrayerMethodSettings
import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.data.prefs.PreferencesPrayerMethodSettings
import com.sakinah.deen.data.prefs.QuranReaderPreferences
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.prefs.SalahGuideProgressStore
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
import com.sakinah.deen.service.PrayerAlarmScheduler
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val preferencesModule = module {
    single { SakinahPreferences(androidContext()) }
    single { AppearancePreferences(androidContext()) }
    // Reading mode, last-read position and ayah bookmarks of the Quran reader.
    single { QuranReaderPreferences(androidContext()) }
    // Read by the Visual Salah Guide and by SalahGuideReminderWorker, so it is a singleton.
    single { SalahGuideProgressStore(androidContext()) }
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
                    ?.let { database ->
                        DatabaseSeeder.seedContentOf(
                            database,
                            DatabaseSeeder.PreferencesSeedStateStore(get())
                        )
                    }
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
        // Read homeCoordinates dynamically so it always reflects the current pref value
        // instead of capturing a snapshot at singleton creation.
        TravelStatusManager(
            homeAnchorProvider = { get<SakinahPreferences>().homeCoordinates },
            safarSettingsProvider = { get<SakinahPreferences>().safarSettings }
        )
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
                requestTimeoutMillis = com.sakinah.deen.network.SakinahHttpTimeout.REQUEST_TIMEOUT_MS
                connectTimeoutMillis = com.sakinah.deen.network.SakinahHttpTimeout.CONNECT_TIMEOUT_MS
                socketTimeoutMillis = com.sakinah.deen.network.SakinahHttpTimeout.SOCKET_TIMEOUT_MS
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
    single {
        PrayerAlarmScheduler(
            context = androidContext(),
            astronomyEngine = get(),
            preferences = get(),
            prayerTimes = getOrNull()
        )
    }
}

val repositoryModule = module {
    single { TafseerDownloadRepository(androidContext(), get(), get()) }
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
    // Never reach into GlobalContext here: this module may be loaded in a test or a
    // secondary Koin application, and the worker factory must use the same container.
    single { KoinWorkerFactory(getKoin()) }
}

/**
 * Account records (emails, password hashes, profile) live in EncryptedSharedPreferences.
 * The previous plain store is migrated once and kept only as a fallback when the platform
 * keystore is unavailable, so a rotated key never locks the user out of the app.
 */
private fun encryptedAccountSettings(context: android.content.Context): android.content.SharedPreferences? {
    val legacy = context.getSharedPreferences("sakinah_account_settings", android.content.Context.MODE_PRIVATE)
    val encrypted = runCatching {
        val masterKey = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            "sakinah_account_settings_encrypted",
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    if (encrypted == null) {
        // Never downgrade account credentials to plaintext: report the failure and keep the
        // session in memory for this run instead of writing it unencrypted to disk.
        com.sakinah.deen.data.prefs.SensitivePreferences.reportUnavailable(
            "Encrypted account storage is unavailable. You may need to sign in again after " +
                "restarting because credentials will not be written in plaintext."
        )
        return null
    }

    if (!encrypted.getBoolean("account_store_migrated", false)) {
        val editor = encrypted.edit()
        legacy.all.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        // Not atomic unless committed: only mark complete after the encrypted copy is written.
        val written = runCatching { editor.commit() }.getOrDefault(false)
        if (!written) return encrypted
    }

    // Verify the encrypted copy before the plaintext rows are removed.
    if (encrypted.getBoolean("account_store_migrated", false)) return encrypted
    val verified = legacy.all.all { (key, value) -> encrypted.all[key] == value }
    if (!verified) return encrypted

    encrypted.edit(commit = true) { putBoolean("account_store_migrated", true) }
    legacy.edit(commit = true) { clear() }
    return encrypted
}

/**
 * Minimal in-memory [android.content.SharedPreferences] used only when the platform keystore
 * cannot provide encrypted storage. Nothing is ever written to disk, so a failed keystore
 * degrades to "sign in again after restart" rather than to plaintext credentials.
 */
private class InMemorySharedPreferences : android.content.SharedPreferences {

    private val values = linkedMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = LinkedHashMap(values)
    override fun getString(key: String?, defValue: String?): String? =
        values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        values[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): android.content.SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(
        listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    private inner class Editor : android.content.SharedPreferences.Editor {

        private val pending = linkedMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
        override fun putStringSet(key: String?, values: MutableSet<String>?) =
            apply { if (key != null) pending[key] = values }
        override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
        override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
        override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
        override fun putBoolean(key: String?, value: Boolean) =
            apply { if (key != null) pending[key] = value }
        override fun remove(key: String?) = apply { if (key != null) removals += key }
        override fun clear() = apply { clearAll = true }

        override fun commit(): Boolean {
            if (clearAll) values.clear()
            removals.forEach(values::remove)
            values.putAll(pending)
            pending.clear()
            removals.clear()
            clearAll = false
            return true
        }

        override fun apply() {
            commit()
        }
    }
}

val accountModule = module {
    single<com.russhwolf.settings.Settings>(qualifier = org.koin.core.qualifier.named("account_settings")) {
        val encrypted = encryptedAccountSettings(androidContext())
        if (encrypted == null) {
            // Ephemeral, in-memory store: no account data is written to plaintext storage.
            com.russhwolf.settings.SharedPreferencesSettings(InMemorySharedPreferences())
        } else {
            com.russhwolf.settings.SharedPreferencesSettings(encrypted)
        }
    }
    single<com.sakinah.deen.auth.AuthRepository> {
        com.sakinah.deen.auth.PersistentAuthRepository(
            prefs = get(org.koin.core.qualifier.named("account_settings")),
            // Signing out must also clear Credential Manager, otherwise Google would
            // silently restore the previous session on the next sign-in attempt.
            onSignOut = {
                com.sakinah.deen.auth.GoogleSignInManager(androidContext()).clearCredentialState()
            }
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
                withContext(Dispatchers.IO) {
                    runCatching {
                        androidContext().cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                    }
                }
            }
        )
    }
}

val viewModelModule = module {
    viewModel { SalahViewModel(get(), get(), get(), get(), getOrNull(), getOrNull(), getOrNull(), getOrNull(), getOrNull()) }
    viewModel {
        QuranViewModel(
            getOrNull(), getOrNull(), getOrNull(), getOrNull(), getOrNull(), getOrNull(),
            getOrNull()
        )
    }
    viewModel { HadithViewModel(getOrNull(), getOrNull(), getOrNull()) }
    viewModel { TravelViewModel(get(), get(), getOrNull()) }
    viewModel { WealthViewModel(get(), getOrNull(), getOrNull(), observeMonthRollover = true) }
    viewModel { AiScholarViewModel(get(), androidContext(), get()) }
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
