package com.sakinah.deen

import com.sakinah.deen.data.quran.QuranOfflinePack
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the committed QUL bundle is exactly the database the Kotlin constants pin.
 *
 * Pure JVM: it streams the gzip, hashes the unpacked bytes and compares them with
 * [QuranOfflinePack.QUL_DB_SHA256]. A truncated or regenerated asset without updated
 * constants fails here before it can reach a device, where the runtime check would
 * reject it at first reader query.
 */
class QuranOfflinePackQulTest {

    private fun asset(): File {
        val candidates = listOf(
            File("src/main/assets/${QuranOfflinePack.QUL_ASSET}"),
            File("app/src/main/assets/${QuranOfflinePack.QUL_ASSET}")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Missing ${QuranOfflinePack.QUL_ASSET}; run scripts/pack_qul_bundle.py")
    }

    @Test
    fun bundledGzipMatchesPinnedSizeAndChecksum() {
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        GZIPInputStream(asset().inputStream().buffered()).use { gzip ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = gzip.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                bytes += read
            }
        }
        assertEquals(QuranOfflinePack.QUL_DB_BYTES, bytes)
        assertEquals(
            QuranOfflinePack.QUL_DB_SHA256,
            digest.digest().joinToString("") { "%02x".format(it) }
        )
    }

    @Test
    fun compressedAssetIsMuchSmallerThanTheDatabase() {
        assertTrue(asset().length() < QuranOfflinePack.QUL_DB_BYTES / 3)
    }
}
