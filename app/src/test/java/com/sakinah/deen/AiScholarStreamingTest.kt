package com.sakinah.deen

import com.sakinah.deen.data.scholar.ScholarChatMessage
import com.sakinah.deen.data.scholar.ScholarChatSession
import com.sakinah.deen.data.scholar.ScholarHistoryStore
import com.sakinah.deen.network.SunniAiClient
import com.sakinah.deen.network.SunniAiScholarRepository
import com.sakinah.deen.network.model.RagQueryRequest
import com.sakinah.deen.network.model.RagStreamChunk
import com.sakinah.deen.ui.viewmodel.AiScholarUiIntent
import com.sakinah.deen.ui.viewmodel.AiScholarViewModel
import com.sakinah.deen.ui.viewmodel.ScholarErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Streaming behaviour that used to break: tokens copied the whole message list, a chat
 * switch mid-stream could leak a partial answer, and failures had one indistinguishable
 * message with no way to stop or retry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiScholarStreamingTest {

    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun suspendingRepository(prefix: String) = object : SunniAiScholarRepository {
        override fun streamAskScholar(request: RagQueryRequest): Flow<RagStreamChunk> = flow {
            emit(RagStreamChunk.TextDelta(prefix))
            awaitCancellation()
        }
    }

    @Test
    fun deltasGoToTheDraftNotTheMessageList() = runTest(testDispatcher) {
        val vm = AiScholarViewModel(suspendingRepository("first token"))
        vm.onIntent(AiScholarUiIntent.SendQuery("Question"))
        runCurrent()

        val draft = vm.streamingDraft.value
        assertEquals("first token", draft?.text)
        // The assistant bubble stays empty until the stream is finalised.
        val assistant = vm.uiState.value.messages.last()
        assertEquals("", assistant.content)
        assertEquals(assistant.id, vm.uiState.value.streamingMessageId)
        // A bubble that is not streaming receives no draft updates at all.
        val userDraft = vm.draftTextFor(vm.uiState.value.messages.first().id).first()
        assertNull(userDraft)
    }

    @Test
    fun switchingChatMidStreamKeepsThePartialAnswerInItsOwnChat() = runTest(testDispatcher) {
        val vm = AiScholarViewModel(suspendingRepository("partial from chat A"))
        vm.onIntent(AiScholarUiIntent.SendQuery("First question"))
        runCurrent()
        val chatA = vm.activeSessionId.value

        vm.onIntent(AiScholarUiIntent.NewChat)
        runCurrent()

        val chatB = vm.activeSessionId.value
        assertNotEquals(chatA, chatB)
        assertTrue("the new chat must stay empty", vm.uiState.value.messages.isEmpty())
        assertFalse(vm.uiState.value.isStreaming)
        assertNull(vm.streamingDraft.value)

        val storedA = vm.sessions.value.first { it.id == chatA }
        assertTrue(
            "chat A keeps its partial answer",
            storedA.messages.any { it.role == "assistant" && it.text.contains("partial from chat A") }
        )
        val storedB = vm.sessions.value.first { it.id == chatB }
        assertTrue(storedB.messages.isEmpty())
    }

    @Test
    fun stopGeneratingKeepsWhatWasStreamedSoFar() = runTest(testDispatcher) {
        val vm = AiScholarViewModel(suspendingRepository("half an answer"))
        vm.onIntent(AiScholarUiIntent.SendQuery("Question"))
        runCurrent()
        assertTrue(vm.uiState.value.isStreaming)

        vm.onIntent(AiScholarUiIntent.StopStreaming)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isStreaming)
        assertEquals("half an answer", vm.uiState.value.messages.last().content)
        assertNull(vm.uiState.value.error)
        assertNull(vm.streamingDraft.value)
    }

    @Test
    fun slowStreamShowsStillVerifyingAfterFifteenSeconds() = runTest(testDispatcher) {
        val vm = AiScholarViewModel(suspendingRepository(""))
        vm.onIntent(AiScholarUiIntent.SendQuery("Question"))
        runCurrent()
        assertFalse(vm.uiState.value.stillVerifying)

        advanceTimeBy(AiScholarViewModel.SLOW_STREAM_NOTICE_MS + 1)
        runCurrent()

        assertTrue(vm.uiState.value.stillVerifying)
        vm.onIntent(AiScholarUiIntent.StopStreaming)
    }

    @Test
    fun serverErrorIsRetriedWithBackoffThenSucceeds() = runTest(testDispatcher) {
        var attempts = 0
        val repo = object : SunniAiScholarRepository {
            override fun streamAskScholar(request: RagQueryRequest): Flow<RagStreamChunk> = flow {
                attempts += 1
                if (attempts == 1) throw IOException("HTTP 503 from proxy")
                emit(RagStreamChunk.TextDelta("recovered answer"))
                emit(RagStreamChunk.Complete)
            }
        }
        val vm = AiScholarViewModel(repo)
        vm.onIntent(AiScholarUiIntent.SendQuery("Question"))
        advanceUntilIdle()

        assertEquals(2, attempts)
        assertEquals("recovered answer", vm.uiState.value.messages.last().content)
        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isStreaming)
    }

    @Test
    fun rateLimitIsReportedAfterRetriesAreExhausted() = runTest(testDispatcher) {
        var attempts = 0
        val repo = object : SunniAiScholarRepository {
            override fun streamAskScholar(request: RagQueryRequest): Flow<RagStreamChunk> = flow {
                attempts += 1
                throw SunniAiClient.ScholarHttpException(429, "Scholar proxy rejected (HTTP 429)")
            }
        }
        val vm = AiScholarViewModel(repo)
        vm.onIntent(AiScholarUiIntent.SendQuery("Question"))
        advanceUntilIdle()

        assertEquals("two retries on top of the first attempt", 3, attempts)
        assertEquals(ScholarErrorKind.RATE_LIMIT, vm.uiState.value.errorKind)
        assertTrue(vm.uiState.value.error.orEmpty().contains("rate limited"))
        assertTrue(vm.uiState.value.canRetry)
    }

    @Test
    fun errorTaxonomySeparatesOfflineRateLimitTimeoutServerSafetyAndConfig() {
        assertEquals(
            ScholarErrorKind.RATE_LIMIT,
            AiScholarViewModel.classifyScholarFailure(IllegalStateException("HTTP 429"))
        )
        assertEquals(
            ScholarErrorKind.SERVER,
            AiScholarViewModel.classifyScholarFailure(IllegalStateException("HTTP 502 from proxy"))
        )
        assertEquals(
            ScholarErrorKind.OFFLINE,
            AiScholarViewModel.classifyScholarFailure(IOException("Unable to resolve host"))
        )
        assertEquals(
            ScholarErrorKind.TIMEOUT,
            AiScholarViewModel.classifyScholarFailure(IllegalStateException("request timed out"))
        )
        assertEquals(
            ScholarErrorKind.SAFETY,
            AiScholarViewModel.classifyScholarFailure(IllegalStateException("blocked by safety filter"))
        )
        assertEquals(
            ScholarErrorKind.CONFIG,
            AiScholarViewModel.classifyScholarFailure(IllegalStateException("AI Scholar is not configured"))
        )
        assertEquals(
            ScholarErrorKind.UNKNOWN,
            AiScholarViewModel.classifyScholarFailure(IllegalStateException("boom"))
        )
        // Config and safety refusals are not retryable from the UI.
        assertFalse(
            com.sakinah.deen.ui.viewmodel.AiScholarUiState(
                errorKind = ScholarErrorKind.CONFIG
            ).canRetry
        )
        assertFalse(
            com.sakinah.deen.ui.viewmodel.AiScholarUiState(
                errorKind = ScholarErrorKind.SAFETY
            ).canRetry
        )
    }

    @Test
    fun sourcesUnverifiedIsSurfacedOnTheAnswer() = runTest(testDispatcher) {
        val repo = object : SunniAiScholarRepository {
            override fun streamAskScholar(request: RagQueryRequest): Flow<RagStreamChunk> = flow {
                emit(RagStreamChunk.SourcesUnverified("No verifiable reference found."))
                emit(RagStreamChunk.TextDelta("Summary text"))
                emit(RagStreamChunk.Complete)
            }
        }
        val vm = AiScholarViewModel(repo)
        vm.onIntent(AiScholarUiIntent.SendQuery("Question"))
        advanceUntilIdle()

        assertEquals(
            "No verifiable reference found.",
            vm.uiState.value.messages.last().sourcesUnverified
        )
    }

    @Test
    fun pinningSortsAChatToTheTopAndSurvivesRetention() = runTest(testDispatcher) {
        val vm = AiScholarViewModel(suspendingRepository("x"))
        vm.onIntent(AiScholarUiIntent.SendQuery("Old chat question"))
        runCurrent()
        vm.onIntent(AiScholarUiIntent.StopStreaming)
        val oldChat = vm.activeSessionId.value

        vm.onIntent(AiScholarUiIntent.NewChat)
        vm.onIntent(AiScholarUiIntent.TogglePinChat(oldChat))
        runCurrent()

        val pinned = vm.sessions.value.first()
        assertEquals(oldChat, pinned.id)
        assertTrue(pinned.pinned)

        val retained = ScholarHistoryStore.pruneRetained(
            listOf(
                pinned,
                ScholarChatSession("recent", "Recent", System.currentTimeMillis(), emptyList()),
                ScholarChatSession("older", "Older", 1L, emptyList())
            ),
            maxSessions = 2
        )
        assertTrue(retained.any { it.id == oldChat })
        assertEquals(2, retained.size)
    }

    @Test
    fun retentionCapsMessagesAndSearchMatchesTitleOrBody() {
        val longChat = ScholarChatSession(
            id = "long",
            title = "Zakat questions",
            updatedAtMs = 10L,
            messages = (1..250).map {
                ScholarChatMessage(id = "m$it", role = "user", text = "message $it", timestampMs = it.toLong())
            }
        )
        val pruned = ScholarHistoryStore.pruneRetained(listOf(longChat), maxSessions = 5, maxMessages = 200)
        assertEquals(200, pruned.single().messages.size)
        assertEquals("message 250", pruned.single().messages.last().text)

        val sessions = listOf(
            longChat,
            ScholarChatSession("short", "Sujud as-Sahw", 5L, listOf(
                ScholarChatMessage("m", "assistant", "Two prostrations of forgetfulness", 1L)
            ))
        )
        assertEquals(listOf("long"), ScholarHistoryStore.search(sessions, "zakat").map { it.id })
        assertEquals(listOf("short"), ScholarHistoryStore.search(sessions, "forgetfulness").map { it.id })
        assertEquals(2, ScholarHistoryStore.search(sessions, " ").size)
    }
}
