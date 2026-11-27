package com.sakinah.deen.network

import com.sakinah.deen.BuildConfig
import com.sakinah.deen.network.model.ProofSourceDto
import com.sakinah.deen.network.model.RagQueryRequest
import com.sakinah.deen.network.model.RagStreamChunk
import com.sakinah.deen.util.CitationParser
import io.ktor.client.HttpClient
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Streaming client for the AI Scholar backend proxy.
 *
 * No model API key is compiled into the APK: the proxy URL comes from
 * `BuildConfig.AI_PROXY_URL` and the upstream provider credential stays on the server.
 * The proxy is expected to answer with the same server-sent-event shape the Gemini
 * streaming endpoint uses (`data: {"candidates": [...]}`), which keeps the existing
 * citation and madhhab extraction below unchanged.
 */
class SunniAiClient(
    private val httpClient: HttpClient,
    private val json: Json,
    private val proxyUrl: String = BuildConfig.AI_PROXY_URL
) {

    companion object {
        /** Guardrails required by the product: Ahl al-Sunnah sources only, cited proofs. */
        val SYSTEM_PROMPT = """
            You are an authentic Sunni Islamic Scholar Assistant within the ImanCore app.
            Rules:
            1. Strictly adhere to Ahl al-Sunnah wal-Jama'ah (Quran, Kutub al-Tis'ah, and the 4 Madhhabs: Hanafi, Maliki, Shafi'i, Hanbali).
            2. Every legal or theological claim MUST cite specific proofs: [Surah:Ayah] or [Book:Hadith#] or classical Fiqh authority.
            3. Format all Arabic quotes in separate blockquotes with Tashkeel.
            4. If there is Madhhab divergence, neutrally summarize the 4 positions without sectarian bias.
            5. Explicitly instruct users to consult a local qualified Mufti for personal binding fatwas (e.g., divorce, complex finance).
            6. Never issue a personal fatwa. Never speculate. If a matter is genuinely disputed, say so.
            7. NEVER invent, guess or "reconstruct" a reference. Only cite a verse or hadith you are certain exists; if you cannot verify a source, say plainly that the source could not be verified instead of producing a number.
            8. If the retrieved sources are empty, incomplete or contradict each other, say so explicitly rather than resolving the conflict by yourself.
            9. This app is an educational aid, not a fatwa service; keep that framing in the answer when a ruling is involved.

            After the answer, when the question concerns a legal ruling with known divergence,
            append a short section using exactly these four labels at line starts:
            Hanafi: ...
            Maliki: ...
            Shafi'i: ...
            Hanbali: ...
        """.trimIndent()

        private val BRACKET_CITATION = Regex("\\[([^\\[\\]\\n]{2,90})]")
        private val MADHHAB_LINE = Regex(
            "(?im)^\\s*(?:[-*•]\\s*)?\\**\\s*(Hanafi|Maliki|Shafi'?i|Hanbali)\\s*\\**\\s*[:\\-–—]\\s*(.+?)\\s*$"
        )

        private const val MIN_MADHHAB_SUMMARY = 8
        private const val MAX_CITATIONS = 8
        private const val MAX_HISTORY_MESSAGES = 8

        /** Shown instead of an HTTP/connection error when the proxy URL was never set. */
        const val NOT_CONFIGURED_MESSAGE =
            "AI Scholar is not configured yet. Please check back soon."

        const val SAFETY_REFUSAL_NOTICE =
            "This question could not be answered within the app's religious safety guidelines. " +
                "Please rephrase it or consult a local qualified scholar."

        const val NO_CITATION_NOTICE =
            "No verifiable Quran or hadith reference was found in this answer. Treat it as a " +
                "study summary and confirm the ruling with a qualified scholar."
    }

    /** HTTP failure carrying the status code so callers can decide whether to retry. */
    class ScholarHttpException(
        val status: Int,
        message: String
    ) : java.io.IOException(message)

    /** A blank (or placeholder) proxy URL means the build has no Scholar backend. */
    val isConfigured: Boolean
        get() = proxyUrl.isNotBlank() &&
            !proxyUrl.startsWith("MY_")

    /**
     * Streams a grounded answer as text deltas, source citations, and madhhab
     * comparison chips. Emits [RagStreamChunk.Complete] exactly once on success.
     *
     * With a blank [BuildConfig.AI_PROXY_URL] no request is attempted at all: the flow
     * emits one plain-language status message so the UI never shows a malformed-URL or
     * connection failure for what is really a configuration gap.
     */
    fun streamScholar(request: RagQueryRequest): Flow<RagStreamChunk> = flow {
        if (!isConfigured) {
            emit(RagStreamChunk.TextDelta(NOT_CONFIGURED_MESSAGE))
            emit(RagStreamChunk.Complete)
            return@flow
        }

        val url = proxyUrl
        httpClient.preparePost(url) {
            contentType(ContentType.Application.Json)
            setBody(buildRequestBody(request).toString())
        }.execute { httpResponse ->
            if (!httpResponse.status.isSuccess()) {
                // Structured status only: the proxy's error envelope is never echoed into a
                // user-visible message, it is summarised by the ViewModel's error taxonomy.
                val detail = runCatching { httpResponse.bodyAsText() }.getOrDefault("")
                throw ScholarHttpException(
                    status = httpResponse.status.value,
                    message = "Scholar proxy rejected the request (HTTP ${httpResponse.status.value})" +
                        detail.take(200).let { if (it.isBlank()) "" else ": $it" }
                )
            }

            val channel = httpResponse.bodyAsChannel()
            val answerBuffer = StringBuilder()
            val emittedCitations = mutableSetOf<String>()
            var emittedAnyText = false

            while (true) {
                val line = channel.readLine() ?: break
                if (!line.startsWith("data:")) continue

                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty() || payload == "[DONE]") continue

                val event = runCatching {
                    json.parseToJsonElement(payload) as? JsonObject
                }.getOrNull() ?: continue

                // Prompt-level blocking (safety filters) has no candidates.
                val blockReason = ((event["promptFeedback"] as? JsonObject)
                    ?.get("blockReason") as? JsonPrimitive)?.contentOrNull
                if (blockReason != null) {
                    emit(RagStreamChunk.SourcesUnverified("The answer was blocked by a safety filter."))
                    emit(RagStreamChunk.TextDelta(SAFETY_REFUSAL_NOTICE))
                    emittedAnyText = true
                    continue
                }

                val candidate = (event["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
                    ?: continue
                val delta = extractText(candidate)
                if (delta.isEmpty()) continue

                emittedAnyText = true
                answerBuffer.append(delta)
                emit(RagStreamChunk.TextDelta(delta))
            }

            if (!emittedAnyText) {
                emit(
                    RagStreamChunk.TextDelta(
                        "No answer was returned. Please retry, or consult a local qualified Mufti for a binding ruling."
                    )
                )
            }

            // Extract citations once on completion instead of on every token delta.
            val citations = extractCitations(answerBuffer.toString())
            for (citation in citations) {
                if (emittedCitations.size >= MAX_CITATIONS) break
                if (emittedCitations.add(citation.title)) {
                    emit(RagStreamChunk.SourceCitation(citation))
                }
            }
            // Never let an uncited answer look sourced.
            if (citations.isEmpty() && emittedAnyText) {
                emit(RagStreamChunk.SourcesUnverified(NO_CITATION_NOTICE))
            }

            for (breakdown in extractMadhhabBreakdown(answerBuffer.toString())) {
                emit(RagStreamChunk.MadhhabBreakdown(breakdown.school, breakdown.summary))
            }

            emit(RagStreamChunk.Complete)
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequestBody(request: RagQueryRequest): JsonObject = buildJsonObject {
        put(
            "systemInstruction",
            buildJsonObject {
                put(
                    "parts",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put(
                                    "text",
                                    SYSTEM_PROMPT +
                                        "\n\nRespond in language code: ${request.preferredLanguage.ifBlank { "en" }}." +
                                        "\nSchool-of-thought context: ${request.schoolOfThoughtContext ?: "ALL"}."
                                )
                            }
                        )
                    }
                )
            }
        )

        put(
            "contents",
            buildJsonArray {
                request.conversationHistory
                    .filter { it.content.isNotBlank() }
                    .takeLast(MAX_HISTORY_MESSAGES)
                    .forEach { message ->
                        add(
                            buildJsonObject {
                                put("role", if (message.role == "assistant") "model" else "user")
                                put(
                                    "parts",
                                    buildJsonArray {
                                        add(buildJsonObject { put("text", message.content) })
                                    }
                                )
                            }
                        )
                    }

                add(
                    buildJsonObject {
                        put("role", "user")
                        put(
                            "parts",
                            buildJsonArray { add(buildJsonObject { put("text", request.query) }) }
                        )
                    }
                )
            }
        )

        put(
            "generationConfig",
            buildJsonObject {
                // Gemini 3.x ignores/rejects temperature and topP. The proxy also strips
                // them from older APKs, so keep the wire format compatible with both.
                put("maxOutputTokens", 2048)
            }
        )
    }

    private fun extractText(candidate: JsonObject): String {
        val parts = ((candidate["content"] as? JsonObject)?.get("parts") as? JsonArray)
            ?: return ""
        return parts.mapNotNull { part ->
            ((part as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull
        }.joinToString("")
    }

    /**
     * The model is instructed to bracket every proof. Each bracketed span is parsed with
     * the same pure [CitationParser] the UI links with, so ranges (`2:255-257`), collection
     * aliases and number markers behave identically in citations and in prose.
     */
    private fun extractCitations(text: String): List<ProofSourceDto> =
        BRACKET_CITATION.findAll(text).mapNotNull { match ->
            val inner = match.groupValues[1].trim()
            val hit = CitationParser.find(inner).firstOrNull() ?: return@mapNotNull null
            hit.quran?.let { quran ->
                return@mapNotNull ProofSourceDto(
                    title = inner,
                    referenceType = "QURAN",
                    translation = "",
                    navigationDeepLink = quran.deepLink
                )
            }
            val hadith = hit.hadith ?: return@mapNotNull null
            ProofSourceDto(
                title = inner,
                referenceType = "HADITH",
                translation = "",
                navigationDeepLink = hadith.deepLink
            )
        }.toList()

    private data class MadhhabPosition(val school: String, val summary: String)

    private fun extractMadhhabBreakdown(text: String): List<MadhhabPosition> {
        val seen = mutableSetOf<String>()
        return MADHHAB_LINE.findAll(text).mapNotNull { match ->
            val rawSchool = match.groupValues[1].lowercase()
            val school = when {
                rawSchool.startsWith("hanafi") -> "Hanafi"
                rawSchool.startsWith("maliki") -> "Maliki"
                rawSchool.startsWith("shaf") -> "Shafi'i"
                rawSchool.startsWith("hanbali") -> "Hanbali"
                else -> return@mapNotNull null
            }
            val summary = match.groupValues[2].trim()
            if (summary.length < MIN_MADHHAB_SUMMARY) return@mapNotNull null
            if (!seen.add(school)) return@mapNotNull null
            MadhhabPosition(school, summary)
        }.take(4).toList()
    }
}
