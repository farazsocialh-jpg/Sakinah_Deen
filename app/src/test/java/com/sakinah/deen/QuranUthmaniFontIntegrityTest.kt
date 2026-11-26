package com.sakinah.deen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Guards the authenticity boundary of the reader's Uthmani text: the Quran assets stay
 * byte-for-byte untouched, and the bundled official KFGQPC font keeps native cmap
 * entries for every Quranic annotation mark (U+06D6–U+06ED, including U+06DF).
 */
class QuranUthmaniFontIntegrityTest {

    private val quranAssetSha256 = mapOf(
        "qul_offline.db.gzip" to "5153f770224ba81c37f8e68a382590c1b13b97f1e9268b4cf277b2a60ead772e",
        "seed_quran_full.json" to "cc26751224af1651f7878b26c40f9fb74fef3d765553428aef7043e12a8a8c8c",
        "seed_quran_surahs.json" to "caaab98e85dd28b1fc87e08026a8afc5ad3b33597ceaeb3a6ce2c04615944c13"
    )

    private val officialUthmanicFontSha256 =
        "a0636e68e375af9552470d67773936f54d536e6586ce2608311b2fe7f9cbec3a"

    @Test
    fun quranTextAssetsRemainByteIdentical() {
        quranAssetSha256.forEach { (name, expectedSha256) ->
            val file = locate("src/main/assets/$name", "app/src/main/assets/$name")
            assertEquals("Quran asset changed: $name", expectedSha256, sha256(file))
        }
    }

    @Test
    fun bundledFontIsTheOfficialKfgqpcUthmanicHafsV18() {
        assertEquals(
            officialUthmanicFontSha256,
            sha256(locateUthmanicFont())
        )
    }

    @Test
    fun uthmanicFontMapsEveryQuranicAnnotationMarkNatively() {
        val cmap = TrueTypeCmap(locateUthmanicFont().readBytes())

        (0x06D6..0x06ED).forEach { codePoint ->
            val glyph = cmap.glyphFor(codePoint)
            assertTrue(
                "U+${codePoint.toString(16).uppercase().padStart(4, '0')} has no native glyph",
                glyph > 0
            )
        }

        // U+06DF (۟) is the historically problematic doubled-circle mark.
        assertTrue("U+06DF must map to a real glyph", cmap.glyphFor(0x06DF) > 0)
    }

    private fun locate(vararg candidates: String): File =
        candidates.asSequence().map(::File).firstOrNull(File::isFile)
            ?: error("Missing asset; tried ${candidates.joinToString()}")

    private fun locateUthmanicFont(): File = locate(
        "src/main/res/font/uthmanic_hafs1_ver18.ttf",
        "app/src/main/res/font/uthmanic_hafs1_ver18.ttf"
    )

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }
}

/**
 * Minimal sfnt cmap reader: resolves Unicode code points to non-zero glyph IDs from
 * format 4 and format 12 subtables without depending on a native text-shaping engine.
 */
private class TrueTypeCmap(private val bytes: ByteArray) {

    private val cmapOffset: Int

    init {
        require(u16(0) == 0x0001 && u16(2) == 0x0000) { "Not a TrueType sfnt font" }
        val numTables = u16(4)
        var offset = -1
        for (index in 0 until numTables) {
            val record = 12 + index * 16
            if (ascii(record, 4) == "cmap") {
                offset = u32(record + 8).toInt()
                break
            }
        }
        require(offset >= 0) { "Font has no cmap table" }
        cmapOffset = offset
    }

    fun glyphFor(codePoint: Int): Int {
        val subtableCount = u16(cmapOffset + 2)
        for (index in 0 until subtableCount) {
            val record = cmapOffset + 4 + index * 8
            val platform = u16(record)
            val encoding = u16(record + 2)
            val isUnicode = platform == 0 || (platform == 3 && (encoding == 1 || encoding == 10))
            if (!isUnicode) continue

            val subtable = cmapOffset + u32(record + 4).toInt()
            val glyph = when (u16(subtable)) {
                4 -> glyphFromFormat4(subtable, codePoint)
                12 -> glyphFromFormat12(subtable, codePoint)
                else -> 0
            }
            if (glyph > 0) return glyph
        }
        return 0
    }

    private fun glyphFromFormat4(subtable: Int, codePoint: Int): Int {
        val segmentCountX2 = u16(subtable + 6)
        val segmentCount = segmentCountX2 / 2
        val endBase = subtable + 14
        val startBase = endBase + segmentCountX2 + 2
        val deltaBase = startBase + segmentCountX2
        val rangeBase = deltaBase + segmentCountX2

        for (segment in 0 until segmentCount) {
            val end = u16(endBase + segment * 2)
            if (codePoint > end) continue
            val start = u16(startBase + segment * 2)
            if (codePoint < start) return 0

            val delta = i16(deltaBase + segment * 2)
            val rangeOffset = u16(rangeBase + segment * 2)
            if (rangeOffset == 0) return (codePoint + delta) and 0xFFFF

            val address = rangeBase + segment * 2 + rangeOffset + (codePoint - start) * 2
            val glyph = u16(address)
            return if (glyph == 0) 0 else (glyph + delta) and 0xFFFF
        }
        return 0
    }

    private fun glyphFromFormat12(subtable: Int, codePoint: Int): Int {
        val groupCount = u32(subtable + 12).toInt()
        for (group in 0 until groupCount) {
            val record = subtable + 16 + group * 12
            val start = u32(record).toInt()
            if (codePoint < start) return 0
            val end = u32(record + 4).toInt()
            if (codePoint <= end) return u32(record + 8).toInt() + (codePoint - start)
        }
        return 0
    }

    private fun u16(offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun i16(offset: Int): Int = u16(offset).let { value ->
        if (value >= 0x8000) value - 0x10000 else value
    }

    private fun u32(offset: Int): Long =
        (u16(offset).toLong() shl 16) or u16(offset + 2).toLong()

    private fun ascii(offset: Int, length: Int): String =
        String(bytes, offset, length, Charsets.US_ASCII)
}
