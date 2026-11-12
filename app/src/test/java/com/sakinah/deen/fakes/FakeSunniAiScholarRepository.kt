package com.sakinah.deen.fakes

import com.sakinah.deen.network.SunniAiScholarRepository
import com.sakinah.deen.network.model.RagQueryRequest
import com.sakinah.deen.network.model.RagStreamChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeSunniAiScholarRepository(
    private val chunks: List<RagStreamChunk> = listOf(RagStreamChunk.TextDelta("Hello"), RagStreamChunk.Complete),
    private val shouldThrow: Throwable? = null
) : SunniAiScholarRepository {
    override fun streamAskScholar(request: RagQueryRequest): Flow<RagStreamChunk> = flow {
        if (shouldThrow != null) throw shouldThrow
        chunks.forEach { emit(it) }
    }
}