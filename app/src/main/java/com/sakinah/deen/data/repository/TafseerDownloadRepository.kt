package com.sakinah.deen.data.repository

import android.content.Context
import com.sakinah.deen.data.database.dao.TafsirDao
import com.sakinah.deen.network.IslamicDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * A Quran.com v4 tafsir edition that can be cached in Room's `tafsir_entries` table.
 *
 * There is no downloadable database file anymore: every chapter is fetched from
 * `api.quran.com/api/v4/tafsirs/{id}/by_chapter/{chapter}` and stored as rows, so progress
 * and partial downloads are real rather than a simulated file transfer.
 */
data class TafseerPackage(
    val id: String,
    val author: String,
    val languageCode: String,
    /** Quran.com API v4 tafsir resource id used for every Room row of this pack. */
    val tafsirId: Int,
    /** Chapters with at least one cached commentary row. */
    val downloadedChapters: Int = 0
) {
    val isDownloaded: Boolean get() = downloadedChapters >= TOTAL_CHAPTERS

    val isPartiallyDownloaded: Boolean
        get() = downloadedChapters in 1 until TOTAL_CHAPTERS

    val missingChapters: Int get() = (TOTAL_CHAPTERS - downloadedChapters).coerceAtLeast(0)

    /** Explicit cache state shown in Settings and the reader instead of a spinner. */
    val statusLabel: String
        get() = when {
            isDownloaded -> "Offline Ready ($TOTAL_CHAPTERS/$TOTAL_CHAPTERS surahs)"
            downloadedChapters > 0 ->
                "Partial Offline ($downloadedChapters/$TOTAL_CHAPTERS surahs — resume download)"
            else -> "Not downloaded"
        }

    companion object {
        const val TOTAL_CHAPTERS = 114
    }
}

class TafseerDownloadRepository(
    @Suppress("UNUSED_PARAMETER") context: Context,
    private val tafsirDao: TafsirDao? = null,
    private val islamicRepository: IslamicDataRepository? = null
) {
    companion object {
        /** Retry backoff for one chapter before it is reported as failed. */
        private const val RETRY_BASE_DELAY_MILLIS = 400L

        private val TAFSIR_ID_BY_PACK = mapOf(
            "ibn_kathir_en" to 169,
            "al_tabari_ar" to 15,
            "al_qurtubi_ar" to 90,
            "al_saadi_ar" to 91,
            "maariful_quran_en" to 168
        )

        private val PACK_BY_SCHOLAR = mapOf(
            "Ibn Kathir" to "ibn_kathir_en",
            "Al-Tabari" to "al_tabari_ar",
            "Al-Qurtubi" to "al_qurtubi_ar",
            "Al-Sa'di" to "al_saadi_ar",
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
                author = "Tafsir Ibn Kathir (Abridged)",
                languageCode = "en",
                tafsirId = 169
            ),
            TafseerPackage(
                id = "al_tabari_ar",
                author = "Jami‘ al-Bayan (Al-Tabari)",
                languageCode = "ar",
                tafsirId = 15
            ),
            TafseerPackage(
                id = "al_qurtubi_ar",
                author = "Al-Jami‘ li Ahkam al-Qur'an (Al-Qurtubi)",
                languageCode = "ar",
                tafsirId = 90
            ),
            TafseerPackage(
                id = "al_saadi_ar",
                author = "Taysir al-Karim al-Rahman (Al-Sa'di)",
                languageCode = "ar",
                tafsirId = 91
            ),
            TafseerPackage(
                id = "maariful_quran_en",
                author = "Ma'ariful Quran (Mufti Shafi)",
                languageCode = "en",
                tafsirId = 168
            )
        )
    )

    val packages: Flow<List<TafseerPackage>> = _packages.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val packageMutexes = mutableMapOf<String, Mutex>()

    /** Retries a single chapter fetch with exponential backoff before reporting failure. */
    private suspend fun downloadChapter(
        repo: IslamicDataRepository,
        pkg: TafseerPackage,
        surahNumber: Int,
        sourceKey: String,
        attempts: Int = 3
    ): Result<Int> {
        var lastError: Throwable? = null
        repeat(attempts) { attempt ->
            val result = repo.syncTafsirForChapter(
                tafsirId = pkg.tafsirId,
                surahNumber = surahNumber,
                languageCode = pkg.languageCode
            )
            val error = result.exceptionOrNull()
            if (error == null) return result
            if (error is kotlinx.coroutines.CancellationException) return result
            lastError = error
            // Drop whatever half of the chapter landed so the retry writes a clean copy.
            runCatching { tafsirDao?.deleteForSurah(sourceKey, surahNumber) }
            if (attempt < attempts - 1) {
                delay(RETRY_BASE_DELAY_MILLIS * (1L shl attempt))
            }
        }
        return Result.failure(
            lastError ?: IOException("Quran.com did not return Surah $surahNumber")
        )
    }

    private fun mutexFor(packageId: String): Mutex = synchronized(packageMutexes) {
        packageMutexes.getOrPut(packageId) { Mutex() }
    }

    init {
        // Room reads must never run on the constructing (main) thread.
        scope.launch { refreshDownloadStatus() }
    }

    /** Reconciles the pack list with the chapters actually present in Room. */
    suspend fun refreshDownloadStatus() = withContext(Dispatchers.IO) {
        val dao = tafsirDao ?: return@withContext
        _packages.update { packages ->
            packages.map { pkg ->
                val chapters = runCatching {
                    dao.countSurahsForSource("tafsir_${pkg.tafsirId}")
                }.getOrDefault(pkg.downloadedChapters)
                pkg.copy(downloadedChapters = chapters)
            }
        }
    }

    /**
     * Downloads every chapter of an edition from Quran.com v4 into Room.
     *
     * Chapters are fetched and written one at a time, so memory stays bounded and a failure
     * late in the run never discards the chapters already cached. [onProgress] receives
     * 0f..1f of the chapters completed; cancelled downloads keep their partial rows.
     */
    suspend fun downloadTafseerPackage(
        packageId: String,
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        val pkg = _packages.value.find { it.id == packageId }
            ?: return@withContext Result.failure(
                IllegalArgumentException("Tafseer package $packageId not found")
            )
        val repo = islamicRepository
            ?: return@withContext Result.failure(
                IOException("Quran.com tafsir service is not available right now")
            )

        val sourceKey = "tafsir_${pkg.tafsirId}"

        mutexFor(packageId).withLock {
            var savedChapters = 0
            var firstError: Throwable? = null

            try {
                for (surahNumber in 1..TafseerPackage.TOTAL_CHAPTERS) {
                    ensureActive()
                    // A chapter only counts as cached when it holds a commentary row for
                    // every ayah the offline Quran text has, so an interrupted download
                    // resumes instead of silently serving a half chapter.
                    val expectedAyahs = runCatching {
                        repo.ayahCountForSurah(surahNumber)
                    }.getOrDefault(0)
                    val cachedRows = runCatching {
                        tafsirDao?.countForSurah(sourceKey, surahNumber) ?: 0
                    }.getOrDefault(0)
                    val alreadyComplete = cachedRows > 0 &&
                        (expectedAyahs <= 0 || cachedRows >= expectedAyahs)
                    if (alreadyComplete) {
                        savedChapters++
                        onProgress(surahNumber.toFloat() / TafseerPackage.TOTAL_CHAPTERS)
                        continue
                    }
                    if (cachedRows > 0) {
                        runCatching { tafsirDao?.deleteForSurah(sourceKey, surahNumber) }
                    }
                    val result = downloadChapter(repo, pkg, surahNumber, sourceKey)
                    if (result.isSuccess) {
                        savedChapters++
                    } else {
                        val error = result.exceptionOrNull()
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        if (firstError == null) firstError = error
                    }
                    onProgress(surahNumber.toFloat() / TafseerPackage.TOTAL_CHAPTERS)
                    _packages.update { packages ->
                        packages.map {
                            if (it.id == packageId) {
                                it.copy(downloadedChapters = maxOf(it.downloadedChapters, savedChapters))
                            } else {
                                it
                            }
                        }
                    }
                    // Stay a polite consumer of the free API.
                    delay(60)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                refreshDownloadStatus()
                throw e
            } catch (e: Exception) {
                if (firstError == null) firstError = e
            } finally {
                refreshDownloadStatus()
            }

            if (savedChapters > 0) {
                Result.success(savedChapters)
            } else {
                Result.failure(
                    firstError ?: IOException("Quran.com returned no commentary for this edition")
                )
            }
        }
    }

    /** Removes every cached commentary row for an edition. */
    suspend fun removeTafseerPackage(packageId: String) = withContext(Dispatchers.IO) {
        val pkg = _packages.value.find { it.id == packageId } ?: return@withContext
        tafsirDao?.deleteBySource("tafsir_${pkg.tafsirId}")
        _packages.update { packages ->
            packages.map {
                if (it.id == packageId) it.copy(downloadedChapters = 0) else it
            }
        }
    }

    /** Removes every cached tafsir edition from Room. */
    suspend fun clearCachedTafsir() = withContext(Dispatchers.IO) {
        tafsirDao?.deleteAll()
        _packages.update { packages -> packages.map { it.copy(downloadedChapters = 0) } }
    }
}
