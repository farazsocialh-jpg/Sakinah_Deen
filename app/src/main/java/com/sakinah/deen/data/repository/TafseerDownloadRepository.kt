package com.sakinah.deen.data.repository

import android.content.Context
import com.sakinah.deen.data.database.dao.TafsirDao
import com.sakinah.deen.data.database.entity.TafsirEntryEntity
import com.sakinah.deen.data.model.TafsirResource
import com.sakinah.deen.network.IslamicDataRepository
import com.sakinah.deen.util.CitationParser
import kotlinx.coroutines.CoroutineDispatcher
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
    /** Edition title from the Quran.com catalog, e.g. "Tafsir Ibn Kathir". */
    val title: String,
    /** Scholar or publisher, e.g. "Hafiz Ibn Kathir". */
    val author: String,
    val languageName: String,
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
                "Partial ($downloadedChapters/$TOTAL_CHAPTERS surahs — resume download)"
            else -> "Download"
        }

    companion object {
        const val TOTAL_CHAPTERS = 114

        fun sourceKey(tafsirId: Int): String = "tafsir_$tafsirId"

        /**
         * Offline floor: the long-standing five editions plus verified multi-language defaults
         * from Quran.com. The live `/resources/tafsirs` catalog supersedes these when online.
         */
        fun fallbackPackages(): List<TafseerPackage> = listOf(
            TafseerPackage("ibn_kathir_en", "Ibn Kathir (Abridged)", "Hafiz Ibn Kathir", "English", "en", 169),
            TafseerPackage("al_tabari_ar", "Tafsir al-Tabari", "Tabari", "Arabic", "ar", 15),
            TafseerPackage("al_qurtubi_ar", "Al-Qurtubi", "Qurtubi", "Arabic", "ar", 90),
            TafseerPackage("al_saadi_ar", "السعدي Al-Sa'di", "Saddi", "Arabic", "ar", 91),
            TafseerPackage("maariful_quran_en", "Ma'arif al-Qur'an", "Mufti Muhammad Shafi", "English", "en", 168),
            TafseerPackage("tafsir_160", "Tafsir Ibn Kathir", "Hafiz Ibn Kathir", "Urdu", "ur", 160),
            TafseerPackage("tafsir_159", "Bayan ul Quran", "Dr. Israr Ahmad", "Urdu", "ur", 159),
            TafseerPackage("tafsir_157", "Fi Zilal al-Quran", "Sayyid Ibrahim Qutb", "Urdu", "ur", 157),
            TafseerPackage("tafsir_165", "Tafsir Ahsanul Bayaan", "Bayaan Foundation", "Bengali", "bn", 165),
            TafseerPackage("tafsir_381", "Tafsir Fathul Majid", "AbdulRahman Bin Hasan Al-Alshaikh", "Bengali", "bn", 381),
            TafseerPackage("tafsir_170", "Al-Sa'di", "Saddi", "Russian", "ru", 170),
            TafseerPackage("tafsir_804", "Rebar Kurdish Tafsir", "Rebar Kurdish Tafsir", "Kurdish", "ku", 804),
            TafseerPackage("tafsir_16", "Tafsir Muyassar", "المیسر", "Arabic", "ar", 16)
        )

        /** Converts every catalog edition into a downloadable package, keyed by tafsir id. */
        fun fromCatalog(entries: List<TafsirResource>): List<TafseerPackage> =
            entries.map { entry ->
                val rawLanguage = entry.languageName.ifBlank { entry.translatedName }
                val code = entry.languageCode.ifBlank {
                    IslamicDataRepository.tafsirLanguageCode(rawLanguage)
                }
                TafseerPackage(
                    id = sourceKey(entry.id),
                    title = entry.name.ifBlank { entry.translatedName.ifBlank { "Tafsir ${entry.id}" } },
                    author = entry.authorName.ifBlank { "Quran.com" },
                    languageName = IslamicDataRepository.languageDisplayName(code, rawLanguage)
                        .ifBlank { "Unknown" },
                    languageCode = code,
                    tafsirId = entry.id
                )
            }.distinctBy { it.tafsirId }
    }
}

/**
 * Downloads any Quran.com tafsir edition into Room. The catalog is refreshed from the API when
 * available, but a fresh install already has a multi-language offline fallback catalog.
 */
class TafseerDownloadRepository(
    @Suppress("UNUSED_PARAMETER") context: Context? = null,
    private val tafsirDao: TafsirDao? = null,
    private val islamicRepository: IslamicDataRepository? = null,
    /** Narrowed in unit tests so a full 114-chapter run is not needed. */
    private val chaptersToDownload: IntRange = 1..TafseerPackage.TOTAL_CHAPTERS,
    /** Injectable so unit tests can drain all repository work with their test dispatcher. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    refreshCatalogOnStart: Boolean = true
) {
    companion object {
        /** Retry backoff for one chapter before it is reported as failed. */
        private const val RETRY_BASE_DELAY_MILLIS = 400L

        /** Accepts both catalog package ids and the canonical `tafsir_<id>` form. */
        fun tafsirIdFromPackageId(packageId: String): Int? =
            packageId.removePrefix("tafsir_").toIntOrNull().takeIf {
                packageId.startsWith("tafsir_") && it != null && it > 0
            }
    }

    private val _packages = MutableStateFlow(TafseerPackage.fallbackPackages())
    val packages: Flow<List<TafseerPackage>> = _packages.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val packageMutexes = mutableMapOf<Int, Mutex>()

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

    private fun mutexFor(tafsirId: Int): Mutex = synchronized(packageMutexes) {
        packageMutexes.getOrPut(tafsirId) { Mutex() }
    }

    init {
        scope.launch {
            if (refreshCatalogOnStart) refreshCatalog()
            refreshDownloadStatus()
        }
    }

    /** Reconciles the pack list with fully cached ayah rows, never just chapter presence. */
    suspend fun refreshDownloadStatus() = withContext(ioDispatcher) {
        val dao = tafsirDao ?: return@withContext
        _packages.update { packages ->
            packages.map { pkg ->
                val counts = runCatching {
                    dao.getAyahCountsBySurah(TafseerPackage.sourceKey(pkg.tafsirId))
                }.getOrDefault(emptyList()).associate { it.surahNumber to it.ayahCount }
                var complete = 0
                for (surahNumber in 1..TafseerPackage.TOTAL_CHAPTERS) {
                    val expectedFromDb = islamicRepository?.let { repo ->
                        runCatching { repo.ayahCountForSurah(surahNumber) }.getOrDefault(0)
                    } ?: 0
                    val expected = expectedFromDb.takeIf { it > 0 }
                        ?: CitationParser.AYAH_COUNTS.getOrNull(surahNumber - 1)
                        ?: 0
                    if (expected > 0 && (counts[surahNumber] ?: 0) >= expected) complete++
                }
                pkg.copy(downloadedChapters = complete)
            }
        }
    }

    /**
     * Merges the live catalog with the offline floor, preferring the freshest metadata while
     * preserving cached progress and the legacy package id of the long-standing editions.
     */
    suspend fun refreshCatalog(): List<TafseerPackage> = withContext(ioDispatcher) {
        val catalog = islamicRepository?.fetchTafseerCatalog().orEmpty()
        if (catalog.isEmpty()) return@withContext _packages.value

        val previous = _packages.value
        val merged = buildList {
            addAll(TafseerPackage.fallbackPackages())
            addAll(TafseerPackage.fromCatalog(catalog))
        }.groupBy { it.tafsirId }.map { (tafsirId, editions) ->
            val fallback = editions.firstOrNull { it.id != TafseerPackage.sourceKey(tafsirId) }
            val live = editions.lastOrNull { it.id == TafseerPackage.sourceKey(tafsirId) }
            val preferred = when {
                fallback != null && live != null -> fallback.copy(
                    title = live.title,
                    author = live.author,
                    languageName = live.languageName,
                    languageCode = live.languageCode
                )
                else -> live ?: fallback!!
            }
            preferred.copy(
                downloadedChapters = previous.firstOrNull { it.tafsirId == tafsirId }
                    ?.downloadedChapters ?: 0
            )
        }.sortedWith(compareBy({ it.languageName }, { it.title }))

        _packages.value = merged
        merged
    }

    /** Returns a downloadable package for any tafsir id, even one not yet in the catalog. */
    suspend fun ensurePackage(tafsirId: Int): TafseerPackage = withContext(ioDispatcher) {
        require(tafsirId > 0) { "A Quran.com tafsir id is required" }
        _packages.value.firstOrNull { it.tafsirId == tafsirId }?.let { return@withContext it }
        val created = TafseerPackage(
            id = TafseerPackage.sourceKey(tafsirId),
            title = "Tafsir $tafsirId",
            author = "Quran.com",
            languageName = "Unknown",
            languageCode = "en",
            tafsirId = tafsirId
        )
        _packages.update { it + created }
        refreshDownloadStatus()
        _packages.value.first { it.tafsirId == tafsirId }
    }

    private suspend fun resolvePackageId(packageId: String): TafseerPackage? {
        tafsirIdFromPackageId(packageId)?.let { return ensurePackage(it) }
        return _packages.value.firstOrNull { it.id == packageId }
    }

    /**
     * Downloads every chapter of an edition from Quran.com v4 into Room.
     *
     * Chapters are fetched and written one at a time, so memory stays bounded and a failure
     * late in the run never discards the chapters already cached. [onProgress] receives
     * 0f..1f of the chapters completed; cancelled downloads keep their partial rows.
     */
    suspend fun downloadTafseerPackage(
        tafsirId: Int,
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = withContext(ioDispatcher) {
        val pkg = runCatching { ensurePackage(tafsirId) }.getOrElse {
            return@withContext Result.failure(it)
        }
        val repo = islamicRepository
            ?: return@withContext Result.failure(
                IOException("Quran.com tafsir service is not available right now")
            )

        val sourceKey = TafseerPackage.sourceKey(pkg.tafsirId)

        mutexFor(pkg.tafsirId).withLock {
            var savedChapters = 0
            var firstError: Throwable? = null

            try {
                for (surahNumber in chaptersToDownload) {
                    ensureActive()
                    // A chapter only counts as cached when it holds a commentary row for
                    // every ayah the offline Quran text has, so an interrupted download
                    // resumes instead of silently serving a half chapter.
                    val expectedAyahs = runCatching {
                        repo.ayahCountForSurah(surahNumber)
                    }.getOrDefault(0).takeIf { it > 0 }
                        ?: CitationParser.AYAH_COUNTS.getOrNull(surahNumber - 1)
                        ?: 0
                    val cachedRows = runCatching {
                        tafsirDao?.countForSurah(sourceKey, surahNumber) ?: 0
                    }.getOrDefault(0)
                    val alreadyComplete = expectedAyahs > 0 && cachedRows >= expectedAyahs
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
                            if (it.tafsirId == pkg.tafsirId) {
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

    /** Package-id entry point retained for Settings and the background worker. */
    suspend fun downloadTafseerPackage(
        packageId: String,
        onProgress: (Float) -> Unit = {}
    ): Result<Int> {
        val pkg = resolvePackageId(packageId)
            ?: return Result.failure(IllegalArgumentException("Tafseer package $packageId not found"))
        return downloadTafseerPackage(pkg.tafsirId, onProgress)
    }

    /** Reads a cached ayah directly from the same Room source the downloader writes. */
    suspend fun getCachedTafsir(
        tafsirId: Int,
        surahNumber: Int,
        ayahNumber: Int
    ): TafsirEntryEntity? = withContext(ioDispatcher) {
        tafsirDao?.getTafsir(TafseerPackage.sourceKey(tafsirId), surahNumber, ayahNumber)
    }

    /** Removes every cached commentary row for an edition. */
    suspend fun removeTafseerPackage(tafsirId: Int) = withContext(ioDispatcher) {
        tafsirDao?.deleteBySource(TafseerPackage.sourceKey(tafsirId))
        _packages.update { packages ->
            packages.map {
                if (it.tafsirId == tafsirId) it.copy(downloadedChapters = 0) else it
            }
        }
    }

    /** Package-id entry point retained for Settings. */
    suspend fun removeTafseerPackage(packageId: String) {
        resolvePackageId(packageId)?.let { removeTafseerPackage(it.tafsirId) }
    }

    /** Removes every cached tafsir edition from Room. */
    suspend fun clearCachedTafsir() = withContext(ioDispatcher) {
        tafsirDao?.deleteAll()
        _packages.update { packages -> packages.map { it.copy(downloadedChapters = 0) } }
    }
}
