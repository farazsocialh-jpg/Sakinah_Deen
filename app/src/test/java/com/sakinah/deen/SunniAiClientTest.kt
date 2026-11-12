package com.sakinah.deen

import com.sakinah.deen.network.SunniAiClient
import com.sakinah.deen.network.model.RagQueryRequest
import com.sakinah.deen.network.model.RagStreamChunk
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SunniAiClientTest {

    private val json = Json { ignoreUnknownKeys = true }
    private fun request() = RagQueryRequest(query = "How many rakat is Fajr?")

    private fun textOf(chunks: List<RagStreamChunk>) =
        chunks.filterIsInstance<RagStreamChunk.TextDelta>().joinToString("") { it.delta }

    @Test
    fun blankProxyUrl_emitsNoticeAndMakesNoRequest() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.OK) }
        val client = SunniAiClient(HttpClient(engine), json, proxyUrl = "")

        val chunks = client.streamScholar(request()).toList()

        assertTrue("blank proxy URL must never hit the network", engine.requestHistory.isEmpty())
        assertEquals(SunniAiClient.NOT_CONFIGURED_MESSAGE, textOf(chunks))
        assertTrue(chunks.last() is RagStreamChunk.Complete)
        assertEquals(false, client.isConfigured)
    }

    @Test
    fun placeholderProxyUrl_isTreatedAsNotConfigured() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.OK) }
        val client = SunniAiClient(HttpClient(engine), json, proxyUrl = "MY_PROXY_URL")

        val chunks = client.streamScholar(request()).toList()

        assertTrue(engine.requestHistory.isEmpty())
        assertEquals(SunniAiClient.NOT_CONFIGURED_MESSAGE, textOf(chunks))
        assertTrue(chunks.last() is RagStreamChunk.Complete)
    }

    @Test
    fun configuredProxyUrl_postsToThatUrlAndStreamsText() = runTest {
        val sse = """
            data: {"candidates":[{"content":{"parts":[{"text":"Fajr is "}]}}]}
            data: {"candidates":[{"content":{"parts":[{"text":"two rakat."}]}}]}
        """.trimIndent()
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(sse),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
            )
        }
        val client = SunniAiClient(HttpClient(engine), json, proxyUrl = "https://proxy.example.test/api/scholar")

        val chunks = client.streamScholar(request()).toList()

        assertTrue(client.isConfigured)
        assertEquals(1, engine.requestHistory.size)
        assertEquals(
            "https://proxy.example.test/api/scholar",
            engine.requestHistory.first().url.toString()
        )
        assertEquals("Fajr is two rakat.", textOf(chunks))
        assertTrue(chunks.last() is RagStreamChunk.Complete)
    }

    @Test
    fun httpFailure_surfacesStatusForTheViewModelToExplain() = runTest {
        val engine = MockEngine { respond("upstream boom", HttpStatusCode.ServiceUnavailable) }
        val client = SunniAiClient(HttpClient(engine), json, proxyUrl = "https://proxy.example.test/api/scholar")

        val failure = requireNotNull(
            runCatching { client.streamScholar(request()).toList() }.exceptionOrNull()
        ) { "a 503 must surface as a failure the UI can explain" }

        assertTrue(failure.message.orEmpty().contains("HTTP 503"))
    }
}