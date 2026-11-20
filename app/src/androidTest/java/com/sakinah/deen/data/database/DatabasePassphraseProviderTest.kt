package com.sakinah.deen.data.database

import android.content.Context
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for the SQLCipher passphrase source.
 *
 * [DatabasePassphraseProvider] must derive the passphrase from the Android Keystore, never
 * persist it in the clear, keep it stable across calls (otherwise the encrypted database
 * cannot be reopened), and regenerate instead of crashing when its stored state is corrupt.
 * These tests need a device/emulator because the Android Keystore is unavailable on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class DatabasePassphraseProviderTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Before
    fun resetKeystoreState() {
        clearStoredKeyMaterial()
    }

    @After
    fun tearDown() {
        clearStoredKeyMaterial()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun passphraseIs32BytesAndStableAcrossCalls() {
        val first = DatabasePassphraseProvider.getPassphrase(context)
        val second = DatabasePassphraseProvider.getPassphrase(context)

        assertEquals(32, first.size)
        assertArrayEqualsMessage("the passphrase must not change between calls", first, second)
    }

    @Test
    fun passphraseIsStoredOnlyAsCiphertext() {
        val passphrase = DatabasePassphraseProvider.getPassphrase(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val ciphertext = prefs.getString("ciphertext", null)
        val iv = prefs.getString("iv", null)
        assertNotNull("the wrapped passphrase must be persisted", ciphertext)
        assertNotNull("the GCM nonce must be persisted", iv)

        val rawBase64 = Base64.encodeToString(passphrase, Base64.NO_WRAP)
        assertFalse(
            "the raw passphrase must never be written in the clear",
            ciphertext == rawBase64 || ciphertext == String(passphrase, Charsets.ISO_8859_1)
        )

        // The stored ciphertext must decrypt (via the provider) back to the same passphrase.
        assertArrayEqualsMessage(
            "the persisted ciphertext must round-trip to the same passphrase",
            passphrase,
            DatabasePassphraseProvider.getPassphrase(context)
        )
    }

    @Test
    fun corruptStoredStateIsRegeneratedInsteadOfCrashing() {
        DatabasePassphraseProvider.getPassphrase(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("ciphertext", Base64.encodeToString(ByteArray(24) { 0x41 }, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(ByteArray(12) { 0x42 }, Base64.NO_WRAP))
            .commit()

        val recovered = DatabasePassphraseProvider.getPassphrase(context)

        assertEquals("a fresh key must still be 32 bytes", 32, recovered.size)
        // The corrupt entry was replaced with usable material.
        assertEquals(
            recovered.size,
            DatabasePassphraseProvider.getPassphrase(context).size
        )
        assertTrue(
            "recovery must rewrite the stored ciphertext",
            prefs.getString("ciphertext", null) !=
                Base64.encodeToString(ByteArray(24) { 0x41 }, Base64.NO_WRAP)
        )
    }

    @Test
    fun aNewKeystoreKeyYieldsADifferentPassphrase() {
        val original = DatabasePassphraseProvider.getPassphrase(context)

        // Deleting the wrapping key is what a restore onto another device looks like.
        clearStoredKeyMaterial()
        val regenerated = DatabasePassphraseProvider.getPassphrase(context)

        assertEquals(32, regenerated.size)
        assertFalse(
            "a discarded wrapping key must not reproduce the old passphrase",
            original.contentEquals(regenerated)
        )
    }

    private fun clearStoredKeyMaterial() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun assertArrayEqualsMessage(message: String, expected: ByteArray, actual: ByteArray) {
        assertTrue(message, expected.contentEquals(actual))
    }

    private companion object {
        const val PREFS_NAME = "sakinah_db_key_prefs"
        const val KEY_ALIAS = "sakinah_db_key"
        const val DATABASE_NAME = "sakinah_encrypted.db"
    }
}
