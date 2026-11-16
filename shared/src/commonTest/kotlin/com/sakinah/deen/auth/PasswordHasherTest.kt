package com.sakinah.deen.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordHasherTest {

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> ((byte.toInt() and 0xff) + 0x100).toString(16).substring(1) }

    @Test
    fun sha256_matchesKnownVectors() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            PasswordHasher.sha256("abc".encodeToByteArray()).toHex()
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            PasswordHasher.sha256(ByteArray(0)).toHex()
        )
    }

    @Test
    fun pbkdf2_matchesRfcVectors() {
        // Published PBKDF2-HMAC-SHA256 vectors (P="password", S="salt", dkLen=32).
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            PasswordHasher.pbkdf2(
                "password".encodeToByteArray(),
                "salt".encodeToByteArray(),
                1,
                32
            ).toHex()
        )
        assertEquals(
            "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43",
            PasswordHasher.pbkdf2(
                "password".encodeToByteArray(),
                "salt".encodeToByteArray(),
                2,
                32
            ).toHex()
        )
    }

    @Test
    fun hashRoundTripsAndUsesRandomSalts() {
        val firstSalt = PasswordHasher.newSalt()
        val secondSalt = PasswordHasher.newSalt()
        assertTrue(firstSalt.isNotBlank())
        assertTrue(firstSalt != secondSalt, "every account must get its own salt")

        val stored = PasswordHasher.hash("correct horse battery staple", firstSalt)
        assertTrue(stored.startsWith("pbkdf2_sha256$"))
        assertTrue(PasswordHasher.verify("correct horse battery staple", firstSalt, stored))
        assertFalse(PasswordHasher.verify("wrong password", firstSalt, stored))
        assertFalse(PasswordHasher.needsUpgrade(stored))
    }

    @Test
    fun legacyFnvRecordsStillVerifyAndAreUpgraded() {
        val legacySalt = "1234-5-sakinah"
        val legacyRecord = PasswordHasher.legacyHash(legacySalt, "old-secret")
        assertTrue(legacyRecord.contains(':'))
        assertTrue(PasswordHasher.needsUpgrade(legacyRecord))
        assertTrue(PasswordHasher.verify("old-secret", legacySalt, legacyRecord))
        assertFalse(PasswordHasher.verify("other-secret", legacySalt, legacyRecord))
    }
}
