package com.sakinah.deen.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Encrypted-at-rest store for values that must not sit in plain SharedPreferences:
 * saved coordinates (a home address is identifiable) and the Zakat balance sheet.
 *
 * If the platform keystore cannot create the encrypted store, [error] says so instead of the
 * app quietly writing those values in the clear. The owning preference class decides how to
 * degrade; the state is exposed so the UI can show a storage-security warning.
 */
object SensitivePreferences {

    private const val FILE_NAME = "sakinah_sensitive_encrypted"

    private val _error = MutableStateFlow<String?>(null)

    /** Non-null when encrypted storage is unavailable; safe to display to the user. */
    val error: StateFlow<String?> = _error.asStateFlow()

    @Suppress("DEPRECATION")
    fun create(context: Context): SharedPreferences? = runCatching {
        val masterKey = androidx.security.crypto.MasterKey.Builder(context)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.onFailure {
        _error.value =
            "Device secure storage is unavailable. Location and Zakat values are stored " +
                "without at-rest encryption until it recovers."
    }.getOrNull()

    /** Records a failure detected outside [create]. */
    fun reportUnavailable(reason: String) {
        _error.value = reason
    }

    internal fun clearErrorForTesting() {
        _error.value = null
    }
}
