package com.sakinah.deen.data.database

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Progress of the bundled offline library, exposed so the UI can wait for first content. */
sealed interface SeedStatus {
    data object Idle : SeedStatus
    data object Seeding : SeedStatus
    data class Ready(val summary: DatabaseSeeder.SeedSummary) : SeedStatus
    data class Failed(val reason: String) : SeedStatus

    /** Queries may safely proceed: content is present, or seeding gave up. */
    val isSettled: Boolean get() = this is Ready || this is Failed
}

/**
 * Runs the bundled offline seeding eagerly at process start and tracks it, so the
 * Quran/hadith screens show the seeded content on a fresh install in airplane mode
 * instead of waiting for a download.
 *
 * [awaitReady] is what the data layer waits on before emitting Room queries; it is
 * bounded, so a pathological database open degrades to "queries proceed, Room
 * invalidation delivers rows when they land" rather than hanging the screen.
 */
class OfflineContentSeeder(
    private val readAsset: (String) -> String?,
    private val content: () -> DatabaseSeeder.SeedContent?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val defaultTimeoutMillis: Long = 10_000L
) {

    private val _status = MutableStateFlow<SeedStatus>(SeedStatus.Idle)
    val status: StateFlow<SeedStatus> = _status.asStateFlow()

    @Volatile
    private var settled = CompletableDeferred<SeedStatus>()
    private val startLock = Any()
    private var started = false

    /** Idempotent: repeated calls (app start, first query, workers) seed at most once. */
    fun start() {
        synchronized(startLock) {
            if (started) return
            started = true
            _status.value = SeedStatus.Seeding
            if (settled.isCompleted) settled = CompletableDeferred()
        }
        scope.launch {
            var cancellation: CancellationException? = null
            val result: SeedStatus = try {
                SeedStatus.Ready(DatabaseSeeder.seedOnce(readAsset, content))
            } catch (e: CancellationException) {
                cancellation = e
                SeedStatus.Failed(e.message ?: "cancelled")
            } catch (e: Throwable) {
                SeedStatus.Failed(e.message ?: e::class.java.simpleName)
            }
            // Always settle so awaitReady never hangs; allow a later retry after failure.
            _status.value = result
            settled.complete(result)
            if (result is SeedStatus.Failed) {
                synchronized(startLock) { started = false }
            }
            cancellation?.let { throw it }
        }
    }

    /**
     * Suspends until the bundled content has been seeded (or seeding failed), then
     * returns the final status. Starts seeding if nobody has yet.
     */
    suspend fun awaitReady(timeoutMillis: Long = defaultTimeoutMillis): SeedStatus {
        start()
        return withTimeoutOrNull(timeoutMillis) { settled.await() } ?: _status.value
    }
}
