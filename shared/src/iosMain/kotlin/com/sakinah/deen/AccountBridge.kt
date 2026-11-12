package com.sakinah.deen

import com.sakinah.deen.auth.AuthRepository
import com.sakinah.deen.auth.AuthState
import com.sakinah.deen.auth.AuthUser
import com.sakinah.deen.profile.Madhhab
import com.sakinah.deen.profile.JourneyTrack
import com.sakinah.deen.profile.UserProfile
import com.sakinah.deen.profile.UserProfileRepository
import com.sakinah.deen.settings.AdhanChoice
import com.sakinah.deen.settings.AdhanSettings
import com.sakinah.deen.settings.AppearanceSettings
import com.sakinah.deen.settings.AsrMethod
import com.sakinah.deen.settings.CalculationMethod
import com.sakinah.deen.settings.HighLatitudeRule
import com.sakinah.deen.settings.PrayerSettings
import com.sakinah.deen.settings.SettingsRepository
import com.sakinah.deen.settings.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.Koin

/**
 * Thin Swift-facing facade. Kotlin `Result` and enum types bridge poorly to
 * Objective-C, so the iOS app talks to plain strings and nullable error
 * messages instead of the richer shared API.
 */
object SharedDependencies {
    private val koinApp: Koin by lazy {
        runCatching { GlobalContext.get() }.getOrElse {
            startKoin { modules(com.sakinah.deen.di.sharedModule) }.koin
        }
    }

    val authRepository: AuthRepository get() = koinApp.get()
    val userProfileRepository: UserProfileRepository get() = koinApp.get()
    val settingsRepository: SettingsRepository get() = koinApp.get()
}

class AccountBridge(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val auth: AuthRepository = SharedDependencies.authRepository
    private val profiles: UserProfileRepository = SharedDependencies.userProfileRepository
    private val settings: SettingsRepository = SharedDependencies.settingsRepository

    val currentUser: AuthUser?
        get() = (auth.authState.value as? AuthState.Authenticated)?.user

    val isSignedIn: Boolean
        get() = auth.authState.value is AuthState.Authenticated

    fun observeAuth(onChange: (String) -> Unit) {
        scope.launch {
            auth.authState.collect { state ->
                val mode = when (state) {
                    is AuthState.Authenticated -> "authenticated"
                    is AuthState.Error -> "error:${state.message}"
                    AuthState.Guest -> "guest"
                    AuthState.Loading -> "loading"
                }
                onChange(mode)
            }
        }
    }

    suspend fun signIn(email: String, password: String): String? =
        auth.signIn(email, password).exceptionOrNull()?.message

    suspend fun signUp(email: String, password: String, displayName: String): String? =
        auth.signUp(email, password, displayName).exceptionOrNull()?.message

    suspend fun resetPassword(email: String): String? =
        auth.sendPasswordReset(email).exceptionOrNull()?.message

    suspend fun signInWithGoogle(idToken: String): String? =
        auth.signInWithGoogle(idToken).exceptionOrNull()?.message

    suspend fun signInWithApple(identityToken: String): String? =
        auth.signInWithApple(identityToken).exceptionOrNull()?.message

    suspend fun continueAsGuest(): String? =
        auth.continueAsGuest().exceptionOrNull()?.message

    suspend fun signOut() = auth.signOut()

    suspend fun deleteAccount(): String? = auth.deleteAccount().exceptionOrNull()?.message

    suspend fun updateProfile(
        displayName: String,
        defaultCity: String,
        madhhab: String,
        journeyTrack: String
    ): String? {
        val user = currentUser ?: return "Not signed in"
        val profile = UserProfile(
            displayName = displayName,
            email = user.email,
            avatarUrl = user.avatarUrl,
            defaultCity = defaultCity,
            madhhab = runCatching { Madhhab.valueOf(madhhab) }.getOrDefault(Madhhab.SHAFI),
            journeyTrack = runCatching { JourneyTrack.valueOf(journeyTrack) }.getOrDefault(JourneyTrack.SEEKER)
        )
        profiles.update(profile)
        return null
    }

    suspend fun updatePrayerSettings(
        calculationMethod: String,
        asrMethod: String,
        highLatitudeRule: String,
        hijriOffset: Int
    ) {
        settings.updatePrayer(
            PrayerSettings(
                calculationMethod = runCatching { CalculationMethod.valueOf(calculationMethod) }
                    .getOrDefault(CalculationMethod.MWL),
                asrMethod = runCatching { AsrMethod.valueOf(asrMethod) }.getOrDefault(AsrMethod.SHAFI),
                highLatitudeRule = runCatching { HighLatitudeRule.valueOf(highLatitudeRule) }
                    .getOrDefault(HighLatitudeRule.MIDDLE_OF_NIGHT),
                hijriOffset = hijriOffset
            )
        )
    }

    suspend fun updateAdhanSettings(
        adhanChoice: String,
        carBluetoothAutoPlay: Boolean,
        fajrEnabled: Boolean,
        dhuhrEnabled: Boolean,
        asrEnabled: Boolean,
        maghribEnabled: Boolean,
        ishaEnabled: Boolean
    ) {
        settings.updateAdhan(
            AdhanSettings(
                fajrEnabled = fajrEnabled,
                dhuhrEnabled = dhuhrEnabled,
                asrEnabled = asrEnabled,
                maghribEnabled = maghribEnabled,
                ishaEnabled = ishaEnabled,
                adhanChoice = runCatching { AdhanChoice.valueOf(adhanChoice) }
                    .getOrDefault(AdhanChoice.MAKKAH),
                carBluetoothAutoPlay = carBluetoothAutoPlay
            )
        )
    }

    suspend fun updateAppearanceSettings(theme: String, arabicFontSize: Int, language: String) {
        settings.updateAppearance(
            AppearanceSettings(
                theme = runCatching { ThemeMode.valueOf(theme) }.getOrDefault(ThemeMode.SYSTEM),
                arabicFontSize = arabicFontSize,
                translationLanguage = language
            )
        )
    }

    fun cacheSizeLabel(): String = "${settings.cacheSizeBytes()} bytes"

    suspend fun clearCache() = settings.clearCache()
}
