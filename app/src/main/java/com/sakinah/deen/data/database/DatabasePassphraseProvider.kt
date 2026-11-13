package com.sakinah.deen.data.database

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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
 */
object DatabasePassphraseProvider {

    private const val KEYSTORE_ALIAS = "sakinah_db_key"
    private const val PREFS_NAME = "sakinah_db_key_prefs"
    private const val CIPHERTEXT_KEY = "ciphertext"
    private const val IV_KEY = "iv"

    fun getPassphrase(context: Context): ByteArray {
        val appContext = context.applicationContext
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
}