package com.sakinah.deen

import com.sakinah.deen.fakes.FakeSunniAiScholarRepository
import com.sakinah.deen.network.model.RagStreamChunk
import com.sakinah.deen.ui.viewmodel.AiScholarUiIntent
import com.sakinah.deen.ui.viewmodel.AiScholarViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class AiScholarViewModelTest {
    private lateinit var testDispatcher: TestDispatcher
    @Before fun setUp() { testDispatcher = StandardTestDispatcher(); Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }
    @Test
    fun sendQuery_appendsMessages() = runTest(testDispatcher) {
        val repo = FakeSunniAiScholarRepository()
        val vm = AiScholarViewModel(repo)
        vm.onIntent(AiScholarUiIntent.SendQuery("Hello"))
        // allow streaming coroutine
        advanceUntilIdle()
        assertTrue(vm.uiState.value.messages.any { it.role == "user" && it.content == "Hello" })
        assertFalse(vm.uiState.value.isStreaming)
    }

    @Test
    fun sendQuery_error_setsError() = runTest(testDispatcher) {
        val repo = FakeSunniAiScholarRepository(shouldThrow = IllegalStateException("boom"))
        val vm = AiScholarViewModel(repo)
        vm.onIntent(AiScholarUiIntent.SendQuery("Hello"))
        advanceUntilIdle()
        assertEquals("boom", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isStreaming)
    }

    @Test
    fun rateLimitedQuery_showsRateLimitMessage() = runTest(testDispatcher) {
        val repo = FakeSunniAiScholarRepository(shouldThrow = IllegalStateException("Gemini request failed (HTTP 429)"))
        val vm = AiScholarViewModel(repo)
        vm.onIntent(AiScholarUiIntent.SendQuery("Hello"))
        advanceUntilIdle()

        val error = vm.uiState.value.error.orEmpty()
        assertTrue(error.contains("rate limited"))
        assertFalse(vm.uiState.value.isStreaming)
    }

    @Test
    fun serverError_showsUnavailableMessage() = runTest(testDispatcher) {
        val repo = FakeSunniAiScholarRepository(shouldThrow = IllegalStateException("HTTP 503 from proxy"))
        val vm = AiScholarViewModel(repo)
        vm.onIntent(AiScholarUiIntent.SendQuery("Hello"))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.error.orEmpty().contains("temporarily unavailable"))
    }

    @Test
    fun offlineQuery_showsOfflineMessage() = runTest(testDispatcher) {
        val repo = FakeSunniAiScholarRepository(shouldThrow = java.io.IOException("Unable to resolve host"))
        val vm = AiScholarViewModel(repo)
        vm.onIntent(AiScholarUiIntent.SendQuery("Hello"))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.error.orEmpty().contains("Check your network"))
    }

    @Test
    fun streamingAnswer_isAppendedToAssistantMessage() = runTest(testDispatcher) {
        val repo = FakeSunniAiScholarRepository(
            chunks = listOf(
                RagStreamChunk.TextDelta("Two prostrations "),
                RagStreamChunk.TextDelta("of forgetfulness."),
                RagStreamChunk.Complete
            )
        )
        val vm = AiScholarViewModel(repo)
        vm.onIntent(AiScholarUiIntent.SendQuery("Sujud as-Sahw?"))
        advanceUntilIdle()

        val answer = vm.uiState.value.messages.last()
        assertEquals("Two prostrations of forgetfulness.", answer.content)
        assertFalse(vm.uiState.value.isStreaming)
    }

    @Test
    fun clearConversation_clearsMessages() = runTest(testDispatcher) {
        val repo = FakeSunniAiScholarRepository()
        val vm = AiScholarViewModel(repo)
        vm.onIntent(AiScholarUiIntent.ClearConversation)
        assertTrue(vm.uiState.value.messages.isEmpty())
    }
}