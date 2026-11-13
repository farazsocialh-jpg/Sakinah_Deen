package com.sakinah.deen.network

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.data.repository.TafseerDownloadRepository
import java.io.File
import java.security.MessageDigest

class ContentDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
    private val tafseerRepository: TafseerDownloadRepository,
    private val database: SakinahEncryptedDatabase
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_PACKAGE_ID = "key_package_id"
        const val KEY_EXPECTED_CHECKSUM = "key_expected_checksum"
        const val PROGRESS_KEY = "progress"
    }

    override suspend fun doWork(): Result {
        val packageId = inputData.getString(KEY_PACKAGE_ID) ?: return Result.failure()
        val expectedChecksum = inputData.getString(KEY_EXPECTED_CHECKSUM)

        return try {
            // 1. Download file with progress updates
            val downloadResult = tafseerRepository.downloadTafseerPackage(packageId) { progress ->
                setProgressAsync(workDataOf(PROGRESS_KEY to (progress * 100).toInt()))
            }

            val file = downloadResult.getOrNull() ?: return Result.retry()

            // 2. Optional SHA-256 Checksum Validation
            if (!expectedChecksum.isNullOrBlank()) {
                val calculated = calculateSha256(file)
                if (!calculated.equals(expectedChecksum, ignoreCase = true)) {
                    file.delete()
                    return Result.failure()
                }
            }

            // 3. Attach downloaded database to Room instance
            val attachResult = tafseerRepository.attachTafseerDatabase(database, packageId)
            if (attachResult.isSuccess) {
                Result.success(workDataOf("attached_alias" to attachResult.getOrNull()))
            } else {
                Result.success() // Download succeeded even if attach deferred
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.retry()
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
