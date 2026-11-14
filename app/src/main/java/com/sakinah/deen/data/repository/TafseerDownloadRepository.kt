package com.sakinah.deen.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.data.database.dao.TafsirDao
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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class TafseerPackage(
    val id: String,
    val author: String,
    val languageCode: String,
    val filename: String,
    val remoteCdnUrl: String,
    val sizeBytes: Long,
    /** Quran.com API v4 tafsir resource id used to fill Room's tafsir_entries table. */
    val tafsirId: Int,
    val isDownloaded: Boolean = false,
    val isAttached: Boolean = false,
    val attachedAlias: String = "tafseer_${id}"
)

class TafseerDownloadRepository(
    private val context: Context,
    private val tafsirDao: TafsirDao? = null
) {
    companion object {
        private val TAFSIR_ID_BY_PACK = mapOf(
            "ibn_kathir_en" to 169,
            "al_tabari_ar" to 15,
            "al_qurtubi_ar" to 90,
            "al_saadi_ur" to 91,
            "maariful_quran_en" to 168
        )

        private val PACK_BY_SCHOLAR = mapOf(
            "Ibn Kathir" to "ibn_kathir_en",
            "Al-Tabari" to "al_tabari_ar",
            "Al-Qurtubi" to "al_qurtubi_ar",
            "Al-Sa'di" to "al_saadi_ur",
            "Ma'ariful Quran" to "maariful_quran_en"
        )

        fun tafsirIdForPack(packageId: String): Int? = TAFSIR_ID_BY_PACK[packageId]

        fun tafsirIdForScholar(scholar: String): Int? =
            PACK_BY_SCHOLAR[scholar]?.let(::tafsirIdForPack)

        fun packIdForScholar(scholar: String): String? = PACK_BY_SCHOLAR[scholar]
    }

    private val _packages = MutableStateFlow(
        listOf(
            TafseerPackage(
                id = "ibn_kathir_en",
                author = "Tafseer Ibn Kathir",
                languageCode = "en",
                filename = "tafsir_ibnkathir_en.db",
                remoteCdnUrl = "https://cdn.sakinah.deen/tafseer/tafsir_ibnkathir_en.db",
                sizeBytes = 18_400_000L,
                tafsirId = 169
            ),
            TafseerPackage(
                id = "al_tabari_ar",
                author = "Jami‘ al-Bayan (Al-Tabari)",
                languageCode = "ar",
                filename = "tafsir_tabari_ar.db",
                remoteCdnUrl = "https://cdn.sakinah.deen/tafseer/tafsir_tabari_ar.db",
                sizeBytes = 32_100_000L,
                tafsirId = 15
            ),
            TafseerPackage(
                id = "al_qurtubi_ar",
                author = "Al-Jami‘ li Ahkam al-Qur'an (Al-Qurtubi)",
                languageCode = "ar",
                filename = "tafsir_qurtubi_ar.db",
                remoteCdnUrl = "https://cdn.sakinah.deen/tafseer/tafsir_qurtubi_ar.db",
                sizeBytes = 26_700_000L,
                tafsirId = 90
            ),
            TafseerPackage(
                id = "al_saadi_ur",
                author = "Taysir al-Karim al-Rahman (Al-Sa'di)",
                languageCode = "ar",
                filename = "tafsir_saadi_ur.db",
                remoteCdnUrl = "https://cdn.sakinah.deen/tafseer/tafsir_saadi_ur.db",
                sizeBytes = 14_800_000L,
                tafsirId = 91
            ),
            TafseerPackage(
                id = "maariful_quran_en",
                author = "Ma'ariful Quran (Mufti Shafi)",
                languageCode = "en",
                filename = "tafsir_maariful_quran_en.db",
                remoteCdnUrl = "https://cdn.sakinah.deen/tafseer/tafsir_maariful_quran_en.db",
                sizeBytes = 22_500_000L,
                tafsirId = 168
            )
        )
    )

    val packages: Flow<List<TafseerPackage>> = _packages.asStateFlow()

    init {
        checkLocalDownloads()
    }

    /**
     * Verifies package files. A `.db` with zero tafsir rows is not a usable download;
     * remove it so the UI cannot claim the pack is available offline.
     */
    private fun checkLocalDownloads() {
        deleteEmptyTafseerDatabases()
        val updated = _packages.value.map { pkg ->
            val file = databaseFile(pkg)
            pkg.copy(isDownloaded = databaseHasEntries(file))
        }
        _packages.value = updated
    }

    /**
     * Reconciles the pack list with Room and on-disk databases. Room is the source of truth
     * for Quran.com downloads, while a verified attached-pack `.db` is also accepted.
     */
    suspend fun refreshDownloadStatus() = withContext(Dispatchers.IO) {
        deleteEmptyTafseerDatabases()
        val updated = _packages.value.map { pkg ->
            val file = databaseFile(pkg)
            val fileCount = if (databaseHasEntries(file)) 1 else 0
            val roomCount = tafsirDao
                ?.takeIf { pkg.tafsirId > 0 }
                ?.countForSource("tafsir_${pkg.tafsirId}")
                ?: 0
            pkg.copy(isDownloaded = fileCount > 0 || roomCount > 0)
        }
        _packages.value = updated
    }

    suspend fun downloadTafseerPackage(
        packageId: String,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val pkg = _packages.value.find { it.id == packageId }
            ?: return@withContext Result.failure(IllegalArgumentException("Tafseer package $packageId not found"))

        val targetFile = databaseFile(pkg)
        val tempFile = File(targetFile.absolutePath + ".download")
        targetFile.parentFile?.mkdirs()
        tempFile.delete()

        try {
            val url = URL(pkg.remoteCdnUrl)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("Tafsir download failed with HTTP $responseCode")
                }

                val totalLength = connection.contentLength.toFloat()
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
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
            } finally {
                connection.disconnect()
            }

            if (!databaseHasEntries(tempFile)) {
                throw IOException("Downloaded tafsir pack contains no tafsir entries")
            }

            if (targetFile.exists() && !targetFile.delete()) {
                throw IOException("Could not replace the existing tafsir pack")
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            _packages.value = _packages.value.map {
                if (it.id == packageId) it.copy(isDownloaded = true) else it
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            tempFile.delete()
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    /** Removes a single downloaded tafseer pack and its Room commentary rows. */
    suspend fun removeTafseerPackage(packageId: String) = withContext(Dispatchers.IO) {
        val pkg = _packages.value.find { it.id == packageId } ?: return@withContext
        databaseFile(pkg).delete()
        File(databaseFile(pkg).absolutePath + ".download").delete()
        tafsirDao?.deleteBySource("tafsir_${pkg.tafsirId}")
        _packages.value = _packages.value.map {
            if (it.id == packageId) it.copy(isDownloaded = false) else it
        }
    }

    /** Removes both Room commentary and downloaded pack files used as an offline cache. */
    suspend fun clearCachedTafsir() = withContext(Dispatchers.IO) {
        tafsirDao?.deleteAll()
        _packages.value.forEach { pkg ->
            databaseFile(pkg).delete()
            File(databaseFile(pkg).absolutePath + ".download").delete()
        }
        _packages.value = _packages.value.map { it.copy(isDownloaded = false) }
    }

    suspend fun attachTafseerDatabase(
        roomDb: SakinahEncryptedDatabase,
        packageId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val pkg = _packages.value.find { it.id == packageId }
            ?: return@withContext Result.failure(IllegalArgumentException("Package not found"))

        val dbFile = databaseFile(pkg)
        if (!databaseHasEntries(dbFile)) {
            return@withContext Result.failure(IllegalStateException("Tafseer DB has no entries"))
        }

        try {
            val db: SupportSQLiteDatabase = roomDb.openHelper.writableDatabase
            val alias = pkg.attachedAlias
            db.execSQL("ATTACH DATABASE '${dbFile.absolutePath}' AS $alias")

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

    private fun databaseFile(pkg: TafseerPackage): File =
        context.getDatabasePath(pkg.filename)

    private fun databaseHasEntries(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        return runCatching {
            val database = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            try {
                database.rawQuery("SELECT COUNT(*) FROM tafseer_entries", null).use { cursor ->
                    cursor.moveToFirst() && cursor.getInt(0) > 0
                }
            } finally {
                database.close()
            }
        }.getOrDefault(false)
    }

    private fun deleteEmptyTafseerDatabases() {
        val databaseDir = context.getDatabasePath("sakinah.db").parentFile ?: return
        databaseDir.listFiles()
            ?.filter { file ->
                file.isFile && file.name.startsWith("tafsir_") &&
                    (file.name.endsWith(".db") || file.name.endsWith(".db.download"))
            }
            ?.forEach { file ->
                if (file.name.endsWith(".db.download") || !databaseHasEntries(file)) {
                    file.delete()
                }
            }
    }
}
