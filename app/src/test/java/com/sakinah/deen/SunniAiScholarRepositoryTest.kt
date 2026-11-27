package com.sakinah.deen

import com.sakinah.deen.network.SunniAiScholarRepositoryImpl
import com.sakinah.deen.network.model.RagQueryRequest
import com.sakinah.deen.network.model.RagStreamChunk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SunniAiScholarRepositoryTest {

    @Test
    fun unconfiguredBackend_usesGroundedOfflineAnswer() = runBlocking {
        val chunks = SunniAiScholarRepositoryImpl()
            .streamAskScholar(RagQueryRequest(query = "How do I perform Sujud as-Sahw?"))
            .toList()

        val text = chunks.filterIsInstance<RagStreamChunk.TextDelta>()
            .joinToString("") { it.delta }

        assertTrue("expected the offline sujud answer", text.contains("Sujud as-Sahw"))
        assertFalse("must not show the setup message", text.contains("not configured", ignoreCase = true))
        assertTrue("expected a source citation", chunks.any { it is RagStreamChunk.SourceCitation })
        assertTrue(chunks.last() is RagStreamChunk.Complete)
    }

    @Test
    fun unconfiguredBackend_answersTahajjudFromOfflineTopic() = runBlocking {
        val chunks = SunniAiScholarRepositoryImpl()
            .streamAskScholar(RagQueryRequest(query = "what is tahajjud"))
            .toList()

        val text = chunks.filterIsInstance<RagStreamChunk.TextDelta>()
            .joinToString("") { it.delta }

        assertTrue("expected tahajjud answer", text.contains("Tahajjud", ignoreCase = true))
        assertTrue("expected Al-Isra citation", chunks.any { it is RagStreamChunk.SourceCitation })
    }

    @Test
    fun unconfiguredBackend_doesNotReuseGenericAnswerForUnknownTopics() = runBlocking {
        val chunks = SunniAiScholarRepositoryImpl()
            .streamAskScholar(RagQueryRequest(query = "what is the ruling on a new invented topic"))
            .toList()

        val text = chunks.filterIsInstance<RagStreamChunk.TextDelta>()
            .joinToString("") { it.delta }

        assertTrue(text.contains("don't have a verified offline answer", ignoreCase = true))
        assertFalse(text.contains("four established Usul", ignoreCase = true))
    }
}
