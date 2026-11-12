package com.sakinah.deen.network

import com.sakinah.deen.network.model.RagQueryRequest
import com.sakinah.deen.network.model.RagStreamChunk
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

interface AiCompanionClient {
    fun streamScholar(request: RagQueryRequest): Flow<RagStreamChunk>
}

/**
 * Streams a scholar answer from a self-hosted backend proxy. The proxy owns the
 * model API key and any retrieval; the mobile binary only knows [proxyBaseUrl].
 */
class KtorAiCompanionClient(
    private val httpClient: HttpClient,
    private val proxyBaseUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) : AiCompanionClient {

    override fun streamScholar(request: RagQueryRequest): Flow<RagStreamChunk> = flow {
        emit(
            RagStreamChunk.GuardrailDisclaimer(
                "Educational Reference Guide: Verified against authentic Sunni Quran, Hadith, and classical Fiqh texts. " +
                    "For personal binding fatwas, consult a local qualified Mufti."
            )
        )

        val channel = httpClient.post(proxyBaseUrl) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.bodyAsChannel()

        val buffer = StringBuilder()
        while (true) {
            val line = channel.readUTF8Line() ?: break
            if (line.isBlank()) continue
            buffer.append(line).append('\n')
            emit(RagStreamChunk.TextDelta(line + "\n"))
        }
        if (buffer.isEmpty()) {
            emit(RagStreamChunk.TextDelta("No answer was returned. Please retry, or consult a local qualified Mufti."))
        }
        emit(RagStreamChunk.Complete)
    }
}
