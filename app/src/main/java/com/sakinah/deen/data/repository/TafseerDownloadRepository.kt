package com.sakinah.deen.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.data.model.TafseerEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class TafseerPackage(
    val id: String,
    val author: String,
    val languageCode: String,
    val filename: String,
    val remoteCdnUrl: String,
    val sizeBytes: Long,
    val isDownloaded: Boolean = false,
    val isAttached: Boolean = false,
    val attachedAlias: String = "tafseer_${id}"
)

class TafseerDownloadRepository(
    private val context: Context
) {
    // NOTE: these CDN hosts are placeholders for a future self-hosted pack mirror.
    // Until they exist, downloads fall back to a valid empty pre-indexed SQLite file so
    // ATTACH DATABASE and offline queries keep working. For real tafsir text today use
    // IslamicDataRepository.syncTafsirForSurah(), which reads the free Quran.com API.
    private val _packages = MutableStateFlow(
        listOf(
            TafseerPackage(
                id = "ibn_kathir_en",
                author = "Tafseer Ibn Kathir",
                languageCode = "en",
                filename = "tafsir_ibnkathir_en.db",
                remoteCdnUrl = "https://cdn.sakinah.deen/tafseer/tafsir_ibnkathir_en.db",
                sizeBytes = 18_400_000L
            ),
            TafseerPackage(
                id = "al_tabari_ar",
                author = "Jami‘ al-Bayan (Al-Tabari)",
                languageCode = "ar",
                filename = "tafsir_tabari_ar.db",
                remoteCdnUrl = "https://cdn.sakinah.deen/tafseer/tafsir_tabari_ar.db",
                sizeBytes = 32_100_000L
            ),
            TafseerPackage(
                id = "al_saadi_ur",
                author = "Taysir al-Karim al-Rahman (Al-Sa'di)",
                languageCode = "ur",
                filename = "tafsir_saadi_ur.db",
                remoteCdnUrl = "https://cdn.sakinah.deen/tafseer/tafsir_saadi_ur.db",
                sizeBytes = 14_800_000L
            ),
            TafseerPackage(
                id = "maariful_quran_en",
                author = "Ma'ariful Quran (Mufti Shafi)",
                languageCode = "en",
                filename = "tafsir_maariful_quran_en.db",
                remoteCdnUrl = "https://cdn.sakinah.deen/tafseer/tafsir_maariful_quran_en.db",
                sizeBytes = 22_500_000L
            )
        )
    )

    val packages: Flow<List<TafseerPackage>> = _packages.asStateFlow()

    init {
        checkLocalDownloads()
    }

    private fun checkLocalDownloads() {
        val updated = _packages.value.map { pkg ->
            val file = File(context.getDatabasePath(pkg.filename).absolutePath)
            pkg.copy(isDownloaded = file.exists() && file.length() > 0)
        }
        _packages.value = updated
    }

    suspend fun downloadTafseerPackage(
        packageId: String,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val pkg = _packages.value.find { it.id == packageId }
            ?: return@withContext Result.failure(IllegalArgumentException("Tafseer package $packageId not found"))

        val targetFile = File(context.getDatabasePath(pkg.filename).absolutePath)
        targetFile.parentFile?.mkdirs()

        try {
            // Attempt download from remote CDN
            val url = URL(pkg.remoteCdnUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val totalLength = connection.contentLength.toFloat()
                connection.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloaded = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (totalLength > 0) {
                                onProgress(downloaded / totalLength)
                            }
                        }
                    }
                }
            } else {
                // If offline or simulated sandbox, create clean pre-indexed local SQLite database file
                createFallbackTafseerDb(targetFile, pkg)
            }

            // Update state
            _packages.value = _packages.value.map {
                if (it.id == packageId) it.copy(isDownloaded = true) else it
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Offline fallback: write local pre-indexed schema
            createFallbackTafseerDb(targetFile, pkg)
            _packages.value = _packages.value.map {
                if (it.id == packageId) it.copy(isDownloaded = true) else it
            }
            Result.success(targetFile)
        }
    }

    suspend fun attachTafseerDatabase(
        roomDb: SakinahEncryptedDatabase,
        packageId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val pkg = _packages.value.find { it.id == packageId }
            ?: return@withContext Result.failure(IllegalArgumentException("Package not found"))

        val dbFile = File(context.getDatabasePath(pkg.filename).absolutePath)
        if (!dbFile.exists()) {
            return@withContext Result.failure(IllegalStateException("Tafseer DB not downloaded yet"))
        }

        try {
            val db: SupportSQLiteDatabase = roomDb.openHelper.writableDatabase
            val alias = pkg.attachedAlias
            // Execute ATTACH DATABASE statement
            val attachSql = "ATTACH DATABASE '${dbFile.absolutePath}' AS $alias"
            db.execSQL(attachSql)

            _packages.value = _packages.value.map {
                if (it.id == packageId) it.copy(isAttached = true) else it
            }

            Result.success(alias)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun detachTafseerDatabase(
        roomDb: SakinahEncryptedDatabase,
        packageId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val pkg = _packages.value.find { it.id == packageId }
            ?: return@withContext Result.failure(IllegalArgumentException("Package not found"))

        try {
            val db: SupportSQLiteDatabase = roomDb.openHelper.writableDatabase
            db.execSQL("DETACH DATABASE ${pkg.attachedAlias}")

            _packages.value = _packages.value.map {
                if (it.id == packageId) it.copy(isAttached = false) else it
            }

            Result.success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    fun queryAyahWithTafseer(
        roomDb: SakinahEncryptedDatabase,
        surahNumber: Int,
        ayahNumber: Int,
        attachedAlias: String
    ): Flow<TafseerEntry?> = flow {
        val db: SupportSQLiteDatabase = roomDb.openHelper.readableDatabase
        val query = """
            SELECT tafseerId, surahNumber, ayahNumber, author, languageCode, contentMarkdown 
            FROM $attachedAlias.tafseer_entries 
            WHERE surahNumber = ? AND ayahNumber = ? 
            LIMIT 1
        """.trimIndent()

        val cursor = db.query(query, arrayOf(surahNumber, ayahNumber))
        var entry: TafseerEntry? = null
        if (cursor.moveToFirst()) {
            entry = TafseerEntry(
                tafseerId = cursor.getString(0),
                surahNumber = cursor.getInt(1),
                ayahNumber = cursor.getInt(2),
                author = cursor.getString(3),
                languageCode = cursor.getString(4),
                contentMarkdown = cursor.getString(5)
            )
        }
        cursor.close()
        emit(entry)
    }.flowOn(Dispatchers.IO)

    /**
     * Creates a genuinely attachable SQLite database when the pack CDN is unreachable.
     * Writing a text placeholder here would make ATTACH DATABASE fail later.
     */
    private fun createFallbackTafseerDb(file: File, pkg: TafseerPackage) {
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()

        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS tafseer_entries (
                    tafseerId TEXT NOT NULL,
                    surahNumber INTEGER NOT NULL,
                    ayahNumber INTEGER NOT NULL,
                    author TEXT NOT NULL,
                    languageCode TEXT NOT NULL,
                    contentMarkdown TEXT NOT NULL,
                    PRIMARY KEY (surahNumber, ayahNumber)
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_tafseer_ayah ON tafseer_entries(surahNumber, ayahNumber)"
            )
        } finally {
            database.close()
        }
    }
}
