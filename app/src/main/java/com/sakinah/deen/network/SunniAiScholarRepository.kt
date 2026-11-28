package com.sakinah.deen.network

import com.sakinah.deen.network.model.RagQueryRequest
import com.sakinah.deen.network.model.RagStreamChunk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

interface SunniAiScholarRepository {
    fun streamAskScholar(request: RagQueryRequest): Flow<RagStreamChunk>
}

/**
 * Streams answers only from the configured, server-side AI proxy.
 *
 * There are deliberately no canned Islamic rulings in this binary: an unconfigured or
 * unreachable proxy produces an honest setup/connectivity message instead of fabricated
 * "offline" scholarship.
 */
class SunniAiScholarRepositoryImpl(
    private val aiClient: SunniAiClient? = null
) : SunniAiScholarRepository {

    override fun streamAskScholar(request: RagQueryRequest): Flow<RagStreamChunk> = flow {
        emit(
            RagStreamChunk.GuardrailDisclaimer(
                notice = "Educational Reference Guide: answers come from the configured " +
                    "AI Scholar proxy and must be verified against authentic sources. " +
                    "For a binding fatwa, consult a qualified local Mufti."
            )
        )

        val client = aiClient
        if (client == null || !client.isConfigured) {
            emitUnavailable()
            emit(RagStreamChunk.Complete)
            return@flow
        }

        try {
            client.streamScholar(request).collect { emit(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(
                RagStreamChunk.SourcesUnverified(
                    "The AI Scholar proxy could not be reached. No offline ruling is substituted."
                )
            )
            emit(RagStreamChunk.TextDelta("Connection error: ${e.localizedMessage ?: "please try again"}"))
        }
        emit(RagStreamChunk.Complete)
    }.flowOn(Dispatchers.IO)

    private suspend fun kotlinx.coroutines.flow.FlowCollector<RagStreamChunk>.emitUnavailable() {
        emit(
            RagStreamChunk.SourcesUnverified(
                "AI Scholar is not configured. Configure the backend proxy in the app settings."
            )
        )
        emit(
            RagStreamChunk.TextDelta(
                "AI Scholar requires an active internet connection and a configured backend proxy."
            )
        )
    }
}
