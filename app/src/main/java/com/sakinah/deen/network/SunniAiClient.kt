package com.sakinah.deen.network

import com.sakinah.deen.BuildConfig
import com.sakinah.deen.network.model.ProofSourceDto
import com.sakinah.deen.network.model.RagQueryRequest
import com.sakinah.deen.network.model.RagStreamChunk
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
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
 * Direct streaming client for Google AI Studio's free Gemini Flash tier.
 *
 * The API key is injected at build time (GEMINI_API_KEY). Embedding a key in an APK
 * is extractable, so the key must be restricted to the Generative Language API in
 * Google AI Studio and rotated if leaked. For multi-user distribution, route this
 * call through a server proxy instead.
 */
class SunniAiClient(
    private val httpClient: HttpClient,
    private val json: Json,
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val model: String = BuildConfig.GEMINI_MODEL
) {

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

        /** Guardrails required by the product: Ahl al-Sunnah sources only, cited proofs. */
        val SYSTEM_PROMPT = """
            You are an authentic Sunni Islamic Scholar Assistant within the Sakinah app.
            Rules:
            1. Strictly adhere to Ahl al-Sunnah wal-Jama'ah (Quran, Kutub al-Tis'ah, and the 4 Madhhabs: Hanafi, Maliki, Shafi'i, Hanbali).
            2. Every legal or theological claim MUST cite specific proofs: [Surah:Ayah] or [Book:Hadith#] or classical Fiqh authority.
            3. Format all Arabic quotes in separate blockquotes with Tashkeel.
            4. If there is Madhhab divergence, neutrally summarize the 4 positions without sectarian bias.
            5. Explicitly instruct users to consult a local qualified Mufti for personal binding fatwas (e.g., divorce, complex finance).
            6. Never issue a personal fatwa. Never speculate. If a matter is genuinely disputed, say so.

            After the answer, when the question concerns a legal ruling with known divergence,
            append a short section using exactly these four labels at line starts:
            Hanafi: ...
            Maliki: ...
            Shafi'i: ...
            Hanbali: ...
        """.trimIndent()

        private val BRACKET_CITATION = Regex("\\[([^\\[\\]\\n]{2,90})]")
        private val QURAN_REFERENCE = Regex("(\\d{1,3})\\s*:\\s*(\\d{1,3})")
        private val HADITH_NUMBER = Regex("(?:#|No\\.?|Hadith)\\s*(\\d{1,5})", RegexOption.IGNORE_CASE)
        private val MADHHAB_LINE = Regex(
            "(?im)^\\s*(?:[-*•]\\s*)?\\**\\s*(Hanafi|Maliki|Shafi'?i|Hanbali)\\s*\\**\\s*[:\\-–—]\\s*(.+?)\\s*$"
        )

        private val HADITH_BOOK_KEYS = mapOf(
            "bukhari" to "bukhari",
            "muslim" to "muslim",
            "abu dawud" to "abudawud",
            "abudawud" to "abudawud",
            "tirmidhi" to "tirmidhi",
            "nasa'i" to "nasai",
            "nasai" to "nasai",
            "ibn majah" to "ibnmajah",
            "ibnmajah" to "ibnmajah",
            "malik" to "malik",
            "nawawi" to "nawawi"
        )

        private const val MAX_CITATIONS = 8
        private const val MAX_HISTORY_MESSAGES = 8
    }

    val isConfigured: Boolean
        get() = apiKey.isNotBlank() &&
            !apiKey.equals("MY_GEMINI_API_KEY", ignoreCase = true) &&
            !apiKey.startsWith("MY_")

    /**
     * Streams a grounded answer as text deltas, source citations, and madhhab
     * comparison chips. Emits [RagStreamChunk.Complete] exactly once on success.
     */
    fun streamScholar(request: RagQueryRequest): Flow<RagStreamChunk> = flow {
        check(isConfigured) { "Gemini API key is not configured" }

        val url = "$BASE_URL/models/$model:streamGenerateContent"
        httpClient.preparePost(url) {
            contentType(ContentType.Application.Json)
            parameter("alt", "sse")
            parameter("key", apiKey)
            setBody(buildRequestBody(request).toString())
        }.execute { httpResponse ->
            if (!httpResponse.status.isSuccess()) {
                val detail = runCatching { httpResponse.bodyAsText() }.getOrDefault("")
                error(
                    "Gemini request failed (HTTP ${httpResponse.status.value})" +
                        detail.take(300).let { if (it.isBlank()) "" else ": $it" }
                )
            }

            val channel = httpResponse.bodyAsChannel()
            val answerBuffer = StringBuilder()
            val emittedCitations = mutableSetOf<String>()
            var emittedAnyText = false

            while (true) {
                val line = channel.readUTF8Line() ?: break
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
                    emit(
                        RagStreamChunk.TextDelta(
                            "This question could not be answered within the app's religious safety guidelines. " +
                                "Please rephrase it or consult a local qualified scholar."
                        )
                    )
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

                for (citation in extractCitations(answerBuffer.toString())) {
                    if (emittedCitations.size >= MAX_CITATIONS) break
                    if (emittedCitations.add(citation.title)) {
                        emit(RagStreamChunk.SourceCitation(citation))
                    }
                }
            }

            if (!emittedAnyText) {
                emit(
                    RagStreamChunk.TextDelta(
                        "No answer was returned. Please retry, or consult a local qualified Mufti for a binding ruling."
                    )
                )
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
                    buildJsonArray { add(buildJsonObject { put("text", SYSTEM_PROMPT) }) }
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
                put("temperature", 0.35)
                put("topP", 0.95)
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
     * The model is instructed to bracket every proof. Bracketed spans that contain a
     * Quran verse reference or a hadith number become tappable source citations.
     */
    private fun extractCitations(text: String): List<ProofSourceDto> =
        BRACKET_CITATION.findAll(text).mapNotNull { match ->
            val inner = match.groupValues[1].trim()
            val lower = inner.lowercase()

            val quranMatch = QURAN_REFERENCE.find(inner)
            if (quranMatch != null) {
                val surah = quranMatch.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val ayah = quranMatch.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                if (surah !in 1..114 || ayah < 1) return@mapNotNull null

                return@mapNotNull ProofSourceDto(
                    title = inner,
                    referenceType = "QURAN",
                    translation = "",
                    navigationDeepLink = "quran/$surah/$ayah"
                )
            }

            val bookKey = HADITH_BOOK_KEYS.entries
                .firstOrNull { lower.contains(it.key) }
                ?.value
                ?: return@mapNotNull null
            val number = HADITH_NUMBER.find(inner)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null

            ProofSourceDto(
                title = inner,
                referenceType = "HADITH",
                translation = "",
                navigationDeepLink = "hadith/$bookKey/$number"
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
            if (summary.length < 15) return@mapNotNull null
            if (!seen.add(school)) return@mapNotNull null
            MadhhabPosition(school, summary)
        }.take(4).toList()
    }
}
