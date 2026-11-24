package com.sakinah.deen.data.quran

import android.content.Context
import com.sakinah.deen.content.SeedQuranDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * The verified, bundled 114-surah / 6,236-ayah Quran pack.
 *
 * Provenance: Quran.com API v4 (`/quran/verses/uthmani` for the Madani Uthmani text and
 * `/quran/translations/20` for Saheeh International). The asset is committed verbatim and
 * pinned by [SHA256] plus byte size, so any edit or truncation is detected before the
 * text ever reaches the reader.
 *
 * [audit] is pure JVM code: the same checks run in the seeder (on the real asset) and in
 * `QuranOfflinePackTest`, which proves the shipped APK contains all 114 surahs in order
 * with correct per-surah ayah counts and no duplicates.
 */
object QuranOfflinePack {

    /** Asset holding the full pack in the shared `SeedQuranDocument` shape. */
    const val ASSET = "seed_quran_full.json"

    /** Pack format version; bump when the asset is regenerated. */
    const val VERSION = 1

    const val TOTAL_SURAHS = 114

    /** Kufic/Madani verse count for the whole mushaf. */
    const val TOTAL_AYAHS = 6236

    /** SHA-256 of the bundled asset, byte for byte. */
    const val SHA256 = "cc26751224af1651f7878b26c40f9fb74fef3d765553428aef7043e12a8a8c8c"

    const val BYTES = 2_509_358

    const val SOURCE = "Quran.com API v4 — /quran/verses/uthmani + /quran/translations/20"

    const val ARABIC_EDITION = "Uthmani, Madani mushaf (King Fahd Complex)"

    const val TRANSLATION_EDITION = "Saheeh International (Quran.com resource 20)"

    /** Quran.com translation resource id the bundled translation matches. */
    const val TRANSLATION_ID = 20

    /**
     * A few muqatta'at verses (e.g. Ta-Ha) are legitimately unvocalized, so the audit
     * tolerates a handful of ayahs without tashkeel rather than flagging the whole pack.
     */
    private const val MAX_AYAHS_WITHOUT_TASHKEEL = 3

    private val ARABIC_LETTER = Regex("[\\u0621-\\u064A]")
    private val TASHKEEL = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Result of checking the bundled asset: checksum, size, structure and content. */
    data class PackAudit(
        val expectedSha256: String,
        val actualSha256: String,
        val actualBytes: Int,
        val checksumMatches: Boolean,
        val surahCount: Int,
        val ayahCount: Int,
        val issues: List<String>,
        val document: SeedQuranDocument?
    ) {
        val isPristine: Boolean
            get() = checksumMatches && issues.isEmpty() && document != null

        val summary: String
            get() = buildString {
                append(if (isPristine) "verified" else "failed")
                append(" — $surahCount/$TOTAL_SURAHS surahs, $ayahCount/$TOTAL_AYAHS ayahs")
                append(", sha256 ").append(actualSha256.take(16)).append("…")
                if (!checksumMatches) append(", checksum mismatch (expected ${expectedSha256.take(16)}…)")
                if (issues.isNotEmpty()) append(", issues: ").append(issues.joinToString("; "))
            }
    }

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xFF
                append(HEX[value ushr 4])
                append(HEX[value and 0x0F])
            }
        }
    }

    fun sha256(text: String): String = sha256(text.toByteArray(Charsets.UTF_8))

    /**
     * Verifies the checksum first (cheap rejection of a truncated asset) and only then
     * decodes and audits the contents. Never throws: a corrupt asset comes back as a
     * [PackAudit] with [PackAudit.isPristine] false and a human-readable reason.
     */
    fun audit(jsonText: String, expectedSha256: String = SHA256): PackAudit {
        val bytes = jsonText.toByteArray(Charsets.UTF_8)
        val actual = sha256(bytes)
        val checksumMatches = expectedSha256.isBlank() || actual.equals(expectedSha256, ignoreCase = true)
        if (!checksumMatches) {
            return PackAudit(
                expectedSha256 = expectedSha256,
                actualSha256 = actual,
                actualBytes = bytes.size,
                checksumMatches = false,
                surahCount = 0,
                ayahCount = 0,
                issues = listOf("asset checksum does not match the pinned pack"),
                document = null
            )
        }
        val document = runCatching { json.decodeFromString<SeedQuranDocument>(jsonText) }.getOrElse {
            return PackAudit(
                expectedSha256 = expectedSha256,
                actualSha256 = actual,
                actualBytes = bytes.size,
                checksumMatches = true,
                surahCount = 0,
                ayahCount = 0,
                issues = listOf("asset is not valid seed JSON: ${it.message}"),
                document = null
            )
        }
        return PackAudit(
            expectedSha256 = expectedSha256,
            actualSha256 = actual,
            actualBytes = bytes.size,
            checksumMatches = true,
            surahCount = document.surahs.size,
            ayahCount = document.surahs.sumOf { it.ayahs.size },
            issues = audit(document),
            document = document
        )
    }

    /** Structural and content checks over an already-decoded pack. */
    fun audit(document: SeedQuranDocument): List<String> {
        val issues = mutableListOf<String>()
        val surahs = document.surahs
        if (surahs.size != TOTAL_SURAHS) {
            issues += "expected $TOTAL_SURAHS surahs, found ${surahs.size}"
        }
        surahs.map { it.number }.let { numbers ->
            if (numbers != (1..TOTAL_SURAHS).toList()) {
                issues += "surah order is not 1..$TOTAL_SURAHS"
            }
        }
        var total = 0
        var unvocalized = 0
        surahs.forEach { surah ->
            val ayahs = surah.ayahs
            total += ayahs.size
            if (surah.versesCount > 0 && surah.versesCount != ayahs.size) {
                issues += "surah ${surah.number} declares ${surah.versesCount} ayahs but ships ${ayahs.size}"
            }
            val numbers = ayahs.map { it.number }
            if (numbers != (1..ayahs.size).toList()) {
                issues += "surah ${surah.number} ayah numbers are not sequential"
            }
            if (surah.nameArabic.isBlank() || surah.nameEnglish.isBlank()) {
                issues += "surah ${surah.number} is missing its name"
            }
            ayahs.forEach { ayah ->
                if (ayah.arabic.isBlank()) {
                    issues += "surah ${surah.number}:${ayah.number} has no Arabic text"
                } else {
                    if (!ARABIC_LETTER.containsMatchIn(ayah.arabic)) {
                        issues += "surah ${surah.number}:${ayah.number} has no Arabic letters"
                    }
                    if (!TASHKEEL.containsMatchIn(ayah.arabic)) unvocalized++
                }
                if (ayah.translation.isBlank()) {
                    issues += "surah ${surah.number}:${ayah.number} has no translation"
                }
            }
        }
        if (total != TOTAL_AYAHS) {
            issues += "expected $TOTAL_AYAHS ayahs, found $total"
        }
        if (unvocalized > MAX_AYAHS_WITHOUT_TASHKEEL) {
            issues += "$unvocalized ayahs are missing tashkeel (diacritics)"
        }
        if (surahs.map { it.number }.distinct().size != surahs.size) {
            issues += "duplicate surah numbers"
        }
        return issues
    }

    /** True when [surahCount] and [ayahCount] cover the whole mushaf. */
    fun isComplete(surahCount: Int, ayahCount: Int): Boolean =
        surahCount >= TOTAL_SURAHS && ayahCount >= TOTAL_AYAHS

    private val HEX = "0123456789abcdef".toCharArray()

    // ------------------------------------------------------------------------------------
    // QUL word-by-word pack (scripts/pack_qul_bundle.py)
    // ------------------------------------------------------------------------------------

    /** Gzipped QUL bundle holding words, WBW translations, layouts, morphology and tafsir. */
    const val QUL_ASSET = "qul_offline.db.gz"

    /** File name the bundle is unpacked to inside `context.filesDir`. */
    const val QUL_DB_NAME = "qul_offline.db"

    /** Bump when the DB schema or the bundled dataset changes. */
    const val QUL_DB_VERSION = 1

    /** Size and SHA-256 of the unpacked database, printed by the packer. */
    const val QUL_DB_BYTES = 108_216_320L
    const val QUL_DB_SHA256 = "51f5979458d61c7a1d0da96c280ff7a08de9a34fb009844ce4b6816eec8003ec"

    private const val QUL_MARKER_SUFFIX = ".sha256"
    private const val UNPACK_BUFFER_BYTES = 256 * 1024

    private val qulUnpackMutex = Mutex()

    @Volatile
    private var qulUnpacked: File? = null

    /**
     * Returns the verified read-only QUL database, unpacking it on first use.
     *
     * Deliberately never called from `Application.onCreate` or the seeder: a 108 MB gzip
     * expansion on the main thread would be a visible startup stall. The first reader
     * query awaits this on [Dispatchers.IO], and a [Mutex] makes concurrent callers share
     * the single unpack. The SHA-256 is checked before the temp file is renamed, so a
     * truncated or tampered asset can never reach SQLite; a marker file makes the check
     * a one-time cost instead of rehashing the database on every launch.
     */
    suspend fun ensureQulDatabaseFile(context: Context): File = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        qulUnpacked?.takeIf { it.exists() }?.let { return@withContext it }
        qulUnpackMutex.withLock {
            qulUnpacked?.takeIf { it.exists() }?.let { return@withLock it }

            val target = File(appContext.filesDir, QUL_DB_NAME)
            val marker = File(appContext.filesDir, QUL_DB_NAME + QUL_MARKER_SUFFIX)
            if (target.length() == QUL_DB_BYTES && markerMatches(marker)) {
                qulUnpacked = target
                return@withLock target
            }

            val temp = File(appContext.filesDir, "$QUL_DB_NAME.tmp")
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                appContext.assets.open(QUL_ASSET).use { asset ->
                    GZIPInputStream(asset, UNPACK_BUFFER_BYTES).use { gzip ->
                        FileOutputStream(temp).use { output ->
                            val buffer = ByteArray(UNPACK_BUFFER_BYTES)
                            while (true) {
                                val read = gzip.read(buffer)
                                if (read < 0) break
                                digest.update(buffer, 0, read)
                                output.write(buffer, 0, read)
                            }
                            output.fd.sync()
                        }
                    }
                }
            } catch (error: Exception) {
                temp.delete()
                throw IllegalStateException(
                    "Bundled QUL database could not be unpacked. Reinstall the app.",
                    error
                )
            }

            val actual = hex(digest.digest())
            if (temp.length() != QUL_DB_BYTES || !actual.equals(QUL_DB_SHA256, ignoreCase = true)) {
                temp.delete()
                throw IllegalStateException(
                    "Bundled QUL database failed verification " +
                        "(${temp.length()} bytes, sha256 ${actual.take(16)}…). Reinstall the app."
                )
            }
            if (target.exists() && !target.delete()) {
                temp.delete()
                throw IllegalStateException("Could not replace the cached QUL database.")
            }
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IllegalStateException("Could not move the QUL database into place.")
            }
            runCatching { marker.writeText(QUL_DB_SHA256) }
            qulUnpacked = target
            target
        }
    }

    /** True when the marker records the checksum this build expects. */
    private fun markerMatches(marker: File): Boolean = runCatching {
        marker.isFile && marker.readText().trim().equals(QUL_DB_SHA256, ignoreCase = true)
    }.getOrDefault(false)

    private fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xFF
            append(HEX[value ushr 4])
            append(HEX[value and 0x0F])
        }
    }
}
