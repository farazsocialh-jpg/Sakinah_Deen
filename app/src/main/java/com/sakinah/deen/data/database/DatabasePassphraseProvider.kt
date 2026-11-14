package com.sakinah.deen.data.database

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Derives the SQLCipher passphrase from the Android Keystore instead of a hardcoded
 * string. A random 32-byte key is generated on first launch, encrypted with a
 * Keystore-backed AES key, and persisted as ciphertext. Subsequent launches decrypt
 * it, so the raw passphrase never lives in the APK or in plain SharedPreferences.
 *
 * Hardened against restored/corrupted state: getPassphrase() is serialized and any
 * keystore or decryption failure (e.g. prefs restored onto a device without the
 * matching key) clears the corrupted entry and regenerates a fresh key instead of
 * crashing on launch. The database layer is expected to handle the resulting
 * passphrase change by recreating the (encrypted) database if opening fails.
 */
object DatabasePassphraseProvider {

    private const val TAG = "DatabasePassphraseProvider"
    private const val KEYSTORE_ALIAS = "sakinah_db_key"
    private const val PREFS_NAME = "sakinah_db_key_prefs"
    private const val CIPHERTEXT_KEY = "ciphertext"
    private const val IV_KEY = "iv"

    @Synchronized
    fun getPassphrase(context: Context): ByteArray {
        val appContext = context.applicationContext
        return try {
            resolve(appContext)
        } catch (e: Exception) {
            Log.w(TAG, "Keystore/passphrase state is corrupt or mismatched; regenerating", e)
            recover(appContext)
            resolve(appContext)
        }
    }

    private fun resolve(appContext: Context): ByteArray {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            keyGenerator.generateKey()
        }
        val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey

        val storedCiphertext = prefs.getString(CIPHERTEXT_KEY, null)
        val storedIv = prefs.getString(IV_KEY, null)
        if (storedCiphertext != null && storedIv != null) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(128, Base64.decode(storedIv, Base64.NO_WRAP))
            )
            return cipher.doFinal(Base64.decode(storedCiphertext, Base64.NO_WRAP))
        }

        val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val ciphertext = cipher.doFinal(random)
        prefs.edit()
            .putString(CIPHERTEXT_KEY, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(IV_KEY, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
        return random
    }

    private fun recover(appContext: Context) {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(CIPHERTEXT_KEY).remove(IV_KEY).apply()
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) keyStore.deleteEntry(KEYSTORE_ALIAS)
        }
        // The old database was encrypted with the key we just discarded, so it can never
        // be opened again. Remove it before the new passphrase creates a fresh database.
        runCatching { appContext.deleteDatabase("sakinah_encrypted.db") }
    }
}
