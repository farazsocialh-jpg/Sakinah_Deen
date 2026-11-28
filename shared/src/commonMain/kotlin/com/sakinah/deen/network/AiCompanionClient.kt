package com.sakinah.deen.network

import com.sakinah.deen.network.model.RagQueryRequest
import com.sakinah.deen.network.model.RagStreamChunk
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

interface AiCompanionClient {
    fun streamScholar(request: RagQueryRequest): Flow<RagStreamChunk>
}

/** Structured failure from the proxy: the status and stable error code, never an API key. */
class ScholarProxyException(
    val status: Int,
    val code: String?,
    message: String,
) : RuntimeException(message)

@Serializable
private data class GeminiTextPart(val text: String)

@Serializable
private data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiTextPart>,
)

@Serializable
private data class GeminiGenerationConfig(val maxOutputTokens: Int)

/** Wire format accepted by worker/src/index.js (and by Gemini's streamGenerateContent). */
@Serializable
private data class GeminiStreamRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig(2048),
)

/**
 * Streams a scholar answer from a self-hosted backend proxy. The proxy owns the
 * model API key and any retrieval; the mobile binary only knows [proxyBaseUrl] and
 * never carries a credential. Responses are Server-Sent Events with Gemini-shaped
 * `data:` frames, not plain lines of text.
 */
class KtorAiCompanionClient(
    private val httpClient: HttpClient,
    private val proxyBaseUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) : AiCompanionClient {

    override fun streamScholar(request: RagQueryRequest): Flow<RagStreamChunk> = flow {
        emit(
            RagStreamChunk.GuardrailDisclaimer(
                "Educational Reference Guide: Verified against authentic Islamic Quran, Hadith, and classical Fiqh texts. " +
                    "For personal binding fatwas, consult a local qualified Mufti."
            )
        )

        val response = httpClient.post(proxyBaseUrl) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(buildRequest(request)))
        }
        if (!response.status.isSuccess()) {
            // The proxy returns { error: { code, message } }; summarise it, never echo a raw body.
            val raw = runCatching { response.bodyAsText() }.getOrDefault("")
            val errorObject = runCatching {
                (json.parseToJsonElement(raw) as? JsonObject)?.get("error") as? JsonObject
            }.getOrNull()
            val code = (errorObject?.get("code") as? JsonPrimitive)?.content
            val detail = (errorObject?.get("message") as? JsonPrimitive)?.content
                ?.take(MAX_ERROR_DETAIL_CHARS)
            throw ScholarProxyException(
                status = response.status.value,
                code = code,
                message = "Scholar proxy rejected the request (HTTP ${response.status.value})" +
                    (code?.let { " [$it]" } ?: "") +
                    (detail?.let { ": $it" } ?: ""),
            )
        }

        val channel = response.bodyAsChannel()
        var receivedText = false
        while (true) {
            val line = channel.readLine() ?: break
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty() || payload == "[DONE]") continue

            val event = runCatching {
                json.parseToJsonElement(payload) as? JsonObject
            }.getOrNull() ?: continue

            val blockReason = ((event["promptFeedback"] as? JsonObject)
                ?.get("blockReason") as? JsonPrimitive)?.content
            if (!blockReason.isNullOrBlank()) {
                emit(RagStreamChunk.SourcesUnverified("The answer was blocked by a safety filter."))
                continue
            }

            val candidate = (event["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
                ?: continue
            val parts = (candidate["content"] as? JsonObject)?.get("parts") as? JsonArray
                ?: continue
            val delta = parts.mapNotNull { part ->
                (part as? JsonObject)?.get("text") as? JsonPrimitive
            }.joinToString("") { it.content }
            if (delta.isEmpty()) continue

            receivedText = true
            emit(RagStreamChunk.TextDelta(delta))
        }

        if (!receivedText) {
            emit(RagStreamChunk.TextDelta("No answer was returned. Please retry, or consult a local qualified Mufti."))
        }
        emit(RagStreamChunk.Complete)
    }

    private fun buildRequest(request: RagQueryRequest): GeminiStreamRequest {
        val history = request.conversationHistory
            .filter { it.content.isNotBlank() }
            .takeLast(MAX_HISTORY_MESSAGES)
            .map { message ->
                GeminiContent(
                    role = if (message.role == "assistant") "model" else "user",
                    parts = listOf(GeminiTextPart(message.content)),
                )
            }
        val instruction = buildString {
            append(SYSTEM_PROMPT)
            append("\n\nRespond in language code: ")
            append(request.preferredLanguage.ifBlank { "en" })
            append(".\nSchool-of-thought context: ")
            append(request.schoolOfThoughtContext?.takeIf { it.isNotBlank() } ?: "ALL")
            append('.')
        }
        return GeminiStreamRequest(
            systemInstruction = GeminiContent(parts = listOf(GeminiTextPart(instruction))),
            contents = history + GeminiContent(
                role = "user",
                parts = listOf(GeminiTextPart(request.query)),
            ),
        )
    }

    companion object {
        private const val MAX_HISTORY_MESSAGES = 8
        private const val MAX_ERROR_DETAIL_CHARS = 120
        private const val SYSTEM_PROMPT =
            "You are the ImanCore scholar. Answer as an educational reference guide grounded in " +
                "the Quran, authentic Hadith and classical fiqh. Cite sources in brackets. " +
                "State uncertainty where sources differ; do not issue a binding fatwa."
    }
}
