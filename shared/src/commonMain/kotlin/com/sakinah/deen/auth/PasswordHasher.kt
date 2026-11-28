package com.sakinah.deen.auth

/**
 * Password hashing for the on-device account store.
 *
 * Accounts are local to the device, so the stored secret must still resist offline guessing:
 * passwords are hashed with PBKDF2-HMAC-SHA256 over a per-account random salt, and only the
 * encoded hash is persisted.
 *
 * Format: `pbkdf2_sha256$<iterations>$<saltHex>$<hashHex>`. The iteration count is stored
 * in every record, so records written with an older cost still verify and are upgraded on the
 * next successful sign-in instead of being invalidated.
 */
internal object PasswordHasher {

    private const val ALGORITHM = "pbkdf2_sha256"

    /** Raised from 120k; the stored iteration count keeps old accounts verifiable. */
    internal const val ITERATIONS = 310_000

    /** Shortest accepted password for a new on-device account. */
    const val MIN_PASSWORD_LENGTH = 8
    private const val KEY_LENGTH_BYTES = 32
    private const val SALT_BYTES = 16

    /** Upper bounds for parsing untrusted stored records; anything larger is rejected. */
    private const val MAX_SALT_HEX_LENGTH = SALT_BYTES * 4
    private const val MAX_SUPPORTED_ITERATIONS = 10_000_000

    /** New random salt for a freshly created or upgraded account. */
    fun newSalt(): String = secureRandomBytes(SALT_BYTES).toHex()

    /**
     * One-way, non-secret identifier for an OAuth credential. Used when the provider payload
     * has no stable `sub` so a raw identity token is never persisted as a user id.
     */
    internal fun fingerprint(input: String): String =
        sha256(input.encodeToByteArray()).toHex().take(32)

    /** Encodes a password with a fresh hash using [salt]. */
    fun hash(password: String, salt: String): String {
        val derived = pbkdf2(
            password = password.encodeToByteArray(),
            salt = salt.hexToBytes(),
            iterations = ITERATIONS,
            keyLength = KEY_LENGTH_BYTES
        )
        return "$ALGORITHM\$$ITERATIONS\$$salt\$${derived.toHex()}"
    }

    /** True when [storedHash] should be replaced with the current algorithm/parameters. */
    fun needsUpgrade(storedHash: String): Boolean {
        if (!storedHash.startsWith("$ALGORITHM\$")) return true
        val parts = storedHash.split('$')
        if (parts.size != 4 || parts[0] != ALGORITHM) return true
        val iterations = parseIterations(parts[1]) ?: return true
        if (!isValidStoredSalt(parts[2]) || !isValidStoredHash(parts[3])) return true
        return iterations < ITERATIONS
    }

    /** True when [password] meets the current minimum length. */
    fun isAcceptablePassword(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH

    /**
     * Constant-time-ish verification against either the current format or the legacy
     * FNV-1a record, so existing installs keep working and are upgraded on next sign-in.
     * Stored fields are validated before any key length or iteration count is derived from
     * them, so a corrupted record cannot trigger a huge allocation or an unbounded KDF loop.
     */
    fun verify(password: String, salt: String, storedHash: String): Boolean {
        if (storedHash.startsWith("$ALGORITHM\$")) {
            val parts = storedHash.split('$')
            if (parts.size != 4 || parts[0] != ALGORITHM) return false
            val iterations = parseIterations(parts[1]) ?: return false
            val storedSalt = parts[2]
            val expectedHash = parts[3]
            if (!isValidStoredSalt(storedSalt) || !isValidStoredHash(expectedHash)) return false
            val actual = pbkdf2(
                password = password.encodeToByteArray(),
                salt = storedSalt.hexToBytes(),
                iterations = iterations,
                keyLength = KEY_LENGTH_BYTES
            ).toHex()
            return constantTimeEquals(actual, expectedHash)
        }
        // Legacy record written before the KDF change.
        if (salt.isBlank() || storedHash.isBlank()) return false
        return constantTimeEquals(legacyHash(salt, password), storedHash)
    }

    private fun parseIterations(value: String): Int? {
        if (value.isEmpty() || value.any { it !in '0'..'9' }) return null
        val iterations = value.toIntOrNull() ?: return null
        return if (iterations in 1..MAX_SUPPORTED_ITERATIONS) iterations else null
    }

    private fun isValidStoredSalt(value: String): Boolean =
        value.length in 2..MAX_SALT_HEX_LENGTH && value.length % 2 == 0 && isHex(value)

    private fun isValidStoredHash(value: String): Boolean =
        value.length == KEY_LENGTH_BYTES * 2 && isHex(value)

    private fun isHex(value: String): Boolean = value.isNotEmpty() && value.all { hexDigit(it) != null }

    /** The pre-KDF record: kept only to migrate existing accounts on sign-in. */
    fun legacyHash(salt: String, password: String): String {
        val input = "$salt:$password:sakinah_v1"
        var h1 = -3750763034362895579L
        var h2 = 0x517cc1b727220a95L
        for (ch in input) {
            val code = ch.code.toLong()
            h1 = (h1 xor code) * 1099511628211L
            h2 = ((h2 shl 7) or (h2 ushr 57)) xor (code * 0x9e3779b97f4a7c15UL.toLong())
        }
        return "${h1.toULong().toString(16)}:${h2.toULong().toString(16)}"
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    private fun ByteArray.toHex(): String {
        val digits = "0123456789abcdef"
        val out = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xff
            out.append(digits[value ushr 4]).append(digits[value and 0x0f])
        }
        return out.toString()
    }

    private fun String.hexToBytes(): ByteArray {
        if (length % 2 != 0) return ByteArray(0)
        val out = ByteArray(length / 2)
        for (i in out.indices) {
            val high = hexDigit(this[i * 2]) ?: return ByteArray(0)
            val low = hexDigit(this[i * 2 + 1]) ?: return ByteArray(0)
            out[i] = ((high shl 4) or low).toByte()
        }
        return out
    }

    private fun hexDigit(char: Char): Int? = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> null
    }

    // --- PBKDF2-HMAC-SHA256 -------------------------------------------------
    // Implemented in common code so Android and iOS derive identical hashes.

    internal fun pbkdf2(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        keyLength: Int
    ): ByteArray {
        val hLen = 32
        val blocks = (keyLength + hLen - 1) / hLen
        val output = ByteArray(blocks * hLen)
        for (block in 1..blocks) {
            val blockIndex = byteArrayOf(
                (block ushr 24).toByte(),
                (block ushr 16).toByte(),
                (block ushr 8).toByte(),
                block.toByte()
            )
            var u = hmacSha256(password, salt + blockIndex)
            val accumulator = u.copyOf()
            for (iteration in 2..iterations) {
                u = hmacSha256(password, u)
                for (i in accumulator.indices) {
                    accumulator[i] = (accumulator[i].toInt() xor u[i].toInt()).toByte()
                }
            }
            accumulator.copyInto(output, (block - 1) * hLen)
        }
        return output.copyOf(keyLength)
    }

    internal fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
        val blockSize = 64
        val normalizedKey = if (key.size > blockSize) sha256(key) else key
        val paddedKey = ByteArray(blockSize).also { normalizedKey.copyInto(it) }
        val innerPad = ByteArray(blockSize) { (paddedKey[it].toInt() xor 0x36).toByte() }
        val outerPad = ByteArray(blockSize) { (paddedKey[it].toInt() xor 0x5c).toByte() }
        return sha256(outerPad + sha256(innerPad + message))
    }

    internal fun sha256(input: ByteArray): ByteArray {
        // Round constants from FIPS 180-4, written as unsigned hex so none are mistyped.
        val k = intArrayOf(
            0x428a2f98u.toInt(), 0x71374491u.toInt(), 0xb5c0fbcfu.toInt(), 0xe9b5dba5u.toInt(),
            0x3956c25bu.toInt(), 0x59f111f1u.toInt(), 0x923f82a4u.toInt(), 0xab1c5ed5u.toInt(),
            0xd807aa98u.toInt(), 0x12835b01u.toInt(), 0x243185beu.toInt(), 0x550c7dc3u.toInt(),
            0x72be5d74u.toInt(), 0x80deb1feu.toInt(), 0x9bdc06a7u.toInt(), 0xc19bf174u.toInt(),
            0xe49b69c1u.toInt(), 0xefbe4786u.toInt(), 0x0fc19dc6u.toInt(), 0x240ca1ccu.toInt(),
            0x2de92c6fu.toInt(), 0x4a7484aau.toInt(), 0x5cb0a9dcu.toInt(), 0x76f988dau.toInt(),
            0x983e5152u.toInt(), 0xa831c66du.toInt(), 0xb00327c8u.toInt(), 0xbf597fc7u.toInt(),
            0xc6e00bf3u.toInt(), 0xd5a79147u.toInt(), 0x06ca6351u.toInt(), 0x14292967u.toInt(),
            0x27b70a85u.toInt(), 0x2e1b2138u.toInt(), 0x4d2c6dfcu.toInt(), 0x53380d13u.toInt(),
            0x650a7354u.toInt(), 0x766a0abbu.toInt(), 0x81c2c92eu.toInt(), 0x92722c85u.toInt(),
            0xa2bfe8a1u.toInt(), 0xa81a664bu.toInt(), 0xc24b8b70u.toInt(), 0xc76c51a3u.toInt(),
            0xd192e819u.toInt(), 0xd6990624u.toInt(), 0xf40e3585u.toInt(), 0x106aa070u.toInt(),
            0x19a4c116u.toInt(), 0x1e376c08u.toInt(), 0x2748774cu.toInt(), 0x34b0bcb5u.toInt(),
            0x391c0cb3u.toInt(), 0x4ed8aa4au.toInt(), 0x5b9cca4fu.toInt(), 0x682e6ff3u.toInt(),
            0x748f82eeu.toInt(), 0x78a5636fu.toInt(), 0x84c87814u.toInt(), 0x8cc70208u.toInt(),
            0x90befffau.toInt(), 0xa4506cebu.toInt(), 0xbef9a3f7u.toInt(), 0xc67178f2u.toInt()
        )
        val h = intArrayOf(
            0x6a09e667u.toInt(), 0xbb67ae85u.toInt(), 0x3c6ef372u.toInt(), 0xa54ff53au.toInt(),
            0x510e527fu.toInt(), 0x9b05688cu.toInt(), 0x1f83d9abu.toInt(), 0x5be0cd19u.toInt()
        )

        val bitLength = input.size.toLong() * 8L
        val paddedLength = ((input.size + 8) / 64 + 1) * 64
        val message = ByteArray(paddedLength)
        input.copyInto(message)
        message[input.size] = 0x80.toByte()
        for (i in 0 until 8) {
            message[paddedLength - 1 - i] = ((bitLength ushr (8 * i)) and 0xffL).toByte()
        }

        val w = IntArray(64)
        var offset = 0
        while (offset < paddedLength) {
            for (i in 0 until 16) {
                val j = offset + i * 4
                w[i] = ((message[j].toInt() and 0xff) shl 24) or
                    ((message[j + 1].toInt() and 0xff) shl 16) or
                    ((message[j + 2].toInt() and 0xff) shl 8) or
                    (message[j + 3].toInt() and 0xff)
            }
            for (i in 16 until 64) {
                val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
                val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }

            var a = h[0]
            var b = h[1]
            var c = h[2]
            var d = h[3]
            var e = h[4]
            var f = h[5]
            var g = h[6]
            var hh = h[7]

            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = hh + s1 + ch + k[i] + w[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj
                hh = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }

            h[0] += a
            h[1] += b
            h[2] += c
            h[3] += d
            h[4] += e
            h[5] += f
            h[6] += g
            h[7] += hh
            offset += 64
        }

        val digest = ByteArray(32)
        for (i in 0 until 8) {
            digest[i * 4] = (h[i] ushr 24).toByte()
            digest[i * 4 + 1] = (h[i] ushr 16).toByte()
            digest[i * 4 + 2] = (h[i] ushr 8).toByte()
            digest[i * 4 + 3] = h[i].toByte()
        }
        return digest
    }

    private fun Int.rotateRight(bits: Int): Int = (this ushr bits) or (this shl (32 - bits))
}

/** Cryptographically secure random bytes for salts; implemented per platform. */
internal expect fun secureRandomBytes(size: Int): ByteArray
