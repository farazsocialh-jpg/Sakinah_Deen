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
    fun unconfiguredBackend_reportsMissingProxyInsteadOfFabricatingRulings() = runBlocking {
        val chunks = SunniAiScholarRepositoryImpl()
            .streamAskScholar(RagQueryRequest(query = "How do I perform Sujud as-Sahw?"))
            .toList()

        val text = chunks.filterIsInstance<RagStreamChunk.TextDelta>()
            .joinToString("") { it.delta }
        assertTrue(text.contains("configured backend proxy", ignoreCase = true))
        assertFalse(text.contains("Sujud as-Sahw", ignoreCase = true))
        assertTrue(chunks.any { it is RagStreamChunk.SourcesUnverified })
        assertFalse(chunks.any { it is RagStreamChunk.SourceCitation })
        assertTrue(chunks.last() is RagStreamChunk.Complete)
    }

    @Test
    fun unconfiguredBackend_neverInventsTopicAnswers() = runBlocking {
        val chunks = SunniAiScholarRepositoryImpl()
            .streamAskScholar(RagQueryRequest(query = "what is tahajjud"))
            .toList()

        val text = chunks.filterIsInstance<RagStreamChunk.TextDelta>()
            .joinToString("") { it.delta }
        assertFalse(text.contains("Tahajjud", ignoreCase = true))
        assertTrue(text.contains("internet connection", ignoreCase = true))
    }

    @Test
    fun unconfiguredBackend_hasNoCannedMadhabOrSourceContent() = runBlocking {
        val chunks = SunniAiScholarRepositoryImpl()
            .streamAskScholar(RagQueryRequest(query = "what is the ruling on a new invented topic"))
            .toList()

        assertFalse(chunks.any { it is RagStreamChunk.MadhhabBreakdown })
        assertFalse(chunks.any { it is RagStreamChunk.SourceCitation })
        assertTrue(chunks.last() is RagStreamChunk.Complete)
    }
}
