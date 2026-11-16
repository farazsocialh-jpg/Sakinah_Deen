package com.sakinah.deen.network

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.data.repository.TafseerDownloadRepository

/**
 * Background download of a full Quran.com tafsir edition into Room. The rows are written as
 * each chapter arrives, so a retry resumes from whatever is already cached.
 */
class ContentDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
    private val tafseerRepository: TafseerDownloadRepository,
    @Suppress("UNUSED_PARAMETER") private val database: SakinahEncryptedDatabase
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_PACKAGE_ID = "key_package_id"
        const val PROGRESS_KEY = "progress"
        const val KEY_DOWNLOADED_CHAPTERS = "downloaded_chapters"
    }

    override suspend fun doWork(): Result {
        val packageId = inputData.getString(KEY_PACKAGE_ID) ?: return Result.failure()

        return try {
            val result = tafseerRepository.downloadTafseerPackage(packageId) { progress ->
                setProgressAsync(workDataOf(PROGRESS_KEY to (progress * 100).toInt()))
            }
            result.fold(
                onSuccess = { chapters ->
                    Result.success(workDataOf(KEY_DOWNLOADED_CHAPTERS to chapters))
                },
                onFailure = { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
