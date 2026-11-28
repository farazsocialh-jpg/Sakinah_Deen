package com.sakinah.deen.data.database

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
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
 * Hardened against restored/corrupted state: getPassphrase() is serialized. Only a
 * confirmed keystore/decryption mismatch (e.g. prefs restored onto a device without the
 * matching key) clears the corrupted entry and regenerates a fresh key; arbitrary failures
 * are never treated as a reason to discard the database. Before a mismatch recovery the old
 * database is copied into `recovery/`, and the database layer still handles the resulting
 * passphrase change through its own careful unreadable-DB path.
 */
object DatabasePassphraseProvider {

    private const val TAG = "DatabasePassphraseProvider"
    private const val KEYSTORE_ALIAS = "sakinah_db_key"
    private const val PREFS_NAME = "sakinah_db_key_prefs"
    private const val CIPHERTEXT_KEY = "ciphertext"
    private const val IV_KEY = "iv"

    /** Only thrown when the stored ciphertext provably cannot be decrypted with this key. */
    private class KeystoreMismatch(message: String, cause: Throwable? = null) : Exception(message, cause)

    @Synchronized
    fun getPassphrase(context: Context): ByteArray {
        val appContext = context.applicationContext
        return try {
            resolve(appContext)
        } catch (e: KeystoreMismatch) {
            Log.w(TAG, "Keystore/passphrase mismatch; regenerating after preserving the database", e)
            recover(appContext, e)
            resolve(appContext)
        }
        // Any other failure (I/O, security policy, ...) must propagate: silently regenerating
        // would make a still-readable database permanently undecryptable.
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
        val secretKey = try {
            keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
                ?: throw KeystoreMismatch("Keystore returned no secret for $KEYSTORE_ALIAS")
        } catch (e: java.security.GeneralSecurityException) {
            throw KeystoreMismatch("Keystore key is unusable", e)
        }

        val storedCiphertext = prefs.getString(CIPHERTEXT_KEY, null)
        val storedIv = prefs.getString(IV_KEY, null)
        if (storedCiphertext != null && storedIv != null) {
            return try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    GCMParameterSpec(128, Base64.decode(storedIv, Base64.NO_WRAP))
                )
                cipher.doFinal(Base64.decode(storedCiphertext, Base64.NO_WRAP))
            } catch (e: java.security.GeneralSecurityException) {
                // Bad tag / invalid key: the persisted ciphertext belongs to another key.
                throw KeystoreMismatch("Stored passphrase could not be decrypted", e)
            } catch (e: IllegalArgumentException) {
                // Malformed Base64/GCM parameters: the stored metadata itself is corrupt.
                throw KeystoreMismatch("Stored passphrase metadata is invalid", e)
            }
        }

        val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val ciphertext = cipher.doFinal(random)
        prefs.edit {
            putString(CIPHERTEXT_KEY, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            putString(IV_KEY, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
        }
        return random
    }

    private fun recover(appContext: Context, cause: Throwable) {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            remove(CIPHERTEXT_KEY)
            remove(IV_KEY)
        }
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) keyStore.deleteEntry(KEYSTORE_ALIAS)
        }
        // The old database was encrypted with the key we just discarded. Keep a timestamped
        // copy under recovery/ instead of deleting it; a later build (or a restored keystore)
        // may still be able to open it. The original is left for SakinahEncryptedDatabase to
        // preserve through its own unreadable-DB path when the new passphrase cannot open it.
        runCatching {
            val source = appContext.getDatabasePath("sakinah_encrypted.db")
            if (source.exists()) {
                val stamp = System.currentTimeMillis()
                val target = java.io.File(appContext.filesDir, "recovery/sakinah_passphrase_$stamp.db")
                target.parentFile?.mkdirs()
                source.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                listOf("-wal", "-shm", "-journal").forEach { suffix ->
                    val sidecar = java.io.File(source.absolutePath + suffix)
                    if (sidecar.exists()) sidecar.copyTo(java.io.File(target.absolutePath + suffix), true)
                }
            }
        }.onFailure {
            Log.w(TAG, "Could not preserve database after passphrase mismatch ($cause)", it)
        }
    }
}
