package com.sakinah.deen.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakinah.deen.data.model.ChatMessageModel
import com.sakinah.deen.data.scholar.ScholarChatMessage
import com.sakinah.deen.data.scholar.ScholarChatSession
import com.sakinah.deen.data.scholar.ScholarHistoryStore
import com.sakinah.deen.ui.components.extractSectionHint
import com.sakinah.deen.network.SunniAiScholarRepository
import com.sakinah.deen.util.stripHtmlForDisplay
import com.sakinah.deen.network.model.ChatMessageDto
import com.sakinah.deen.network.model.RagQueryRequest
import com.sakinah.deen.network.model.RagStreamChunk
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

/**
 * The live answer being streamed. It is deliberately *not* part of the message list:
 * concatenating one string per delta is O(n²) allocations and, worse, copies the whole
 * conversation on every token so every bubble recomposes.
 */
data class StreamingDraft(
    val sessionId: String,
    val messageId: String,
    val text: String = ""
)

/** Distinct failure classes so the UI can offer the right recovery for each. */
enum class ScholarErrorKind {
    OFFLINE,
    RATE_LIMIT,
    TIMEOUT,
    SERVER,
    SAFETY,
    CONFIG,
    UNKNOWN
}

data class AiScholarUiState(
    val messages: List<ChatMessageModel> = emptyList(),
    val inputText: String = "",
    val isStreaming: Boolean = false,
    val guardrailNotice: String = "Educational Reference Notice: Sourced from authentic Islamic texts (Quran, Hadith, 4 Madhhabs). For binding personal fatwas, consult a local Mufti.",
    val activeMadhhabTab: String = "hanafi",
    val error: String? = null,
    /** Machine-readable counterpart of [error], used for the retry affordance. */
    val errorKind: ScholarErrorKind? = null,
    /** True once the pre-chat research disclaimer is accepted for the active session. */
    val disclaimerAcceptedForCurrentChat: Boolean = false,
    /** Set after 15s of waiting so the reader knows work is still ongoing. */
    val stillVerifying: Boolean = false,
    /** Id of the assistant message whose text comes from the streaming draft. */
    val streamingMessageId: String? = null,
    val chatSearchQuery: String = ""
) {
    val canRetry: Boolean
        get() = errorKind != ScholarErrorKind.CONFIG && errorKind != ScholarErrorKind.SAFETY
}

sealed class AiScholarUiIntent {
    data class InputTextChanged(val text: String) : AiScholarUiIntent()
    data class SendQuery(val query: String) : AiScholarUiIntent()
    data class SelectMadhhabTab(val tab: String) : AiScholarUiIntent()
    data class ClickCitation(val sourceTitle: String, val deepLink: String? = null) : AiScholarUiIntent()
    data object AcceptDisclaimer : AiScholarUiIntent()
    data object RetryLastQuery : AiScholarUiIntent()
    data object StopStreaming : AiScholarUiIntent()
    data object ClearConversation : AiScholarUiIntent()
    data object NewChat : AiScholarUiIntent()
    data class SwitchChat(val sessionId: String) : AiScholarUiIntent()
    data class DeleteChat(val sessionId: String) : AiScholarUiIntent()
    data class TogglePinChat(val sessionId: String) : AiScholarUiIntent()
    data class ChatSearchChanged(val query: String) : AiScholarUiIntent()
    data object ClearAllChats : AiScholarUiIntent()
}

sealed class AiScholarNavigationEvent {
    data class NavigateToQuran(val surah: Int, val ayah: Int) : AiScholarNavigationEvent()
    data class NavigateToHadith(
        val bookKey: String,
        val hadithNumber: Int,
        val sectionHint: String? = null
    ) : AiScholarNavigationEvent()

    /** A citation whose source could not be mapped to a Quran/Hadith destination. */
    data class CitationUnresolved(val sourceTitle: String) : AiScholarNavigationEvent()
}

typealias Citation = com.sakinah.deen.data.model.Citation

class AiScholarViewModel(
    private val scholarRepository: SunniAiScholarRepository,
    private val appContext: Context? = null,
    private val preferences: com.sakinah.deen.data.prefs.SakinahPreferences? = null
) : ViewModel() {

    companion object {
        private const val STREAM_TIMEOUT_MS = 45_000L

        /** After this much waiting the UI switches to "Still verifying sources…". */
        const val SLOW_STREAM_NOTICE_MS = 15_000L

        private const val MAX_RETRIES = 2
        private const val RETRY_BASE_DELAY_MS = 600L
        private const val RETRY_JITTER_MS = 250L

        private val HTTP_5XX = Regex("HTTP 5\\d{2}")
        private val HTTP_429 = Regex("HTTP 429")
        private val SURAH_AYAH_REGEX = Regex("(\\d{1,3})\\s*:\\s*(\\d{1,3})")
        private val HADITH_NUMBER_REGEX = Regex("#?(\\d{1,5})")
        private const val NEW_CHAT_TITLE = "New chat"
        private val LEGACY_SEED_MESSAGE_IDS = setOf("init-1", "sample-1", "sample-2")

        internal fun classifyScholarFailure(error: Throwable): ScholarErrorKind {
            val message = error.localizedMessage.orEmpty()
            val lower = message.lowercase()
            return when {
                error is TimeoutCancellationException ||
                    lower.contains("timeout") ||
                    lower.contains("timed out") -> ScholarErrorKind.TIMEOUT

                lower.contains("safety") ||
                    lower.contains("blocked") ||
                    lower.contains("guidelines") -> ScholarErrorKind.SAFETY

                HTTP_429.containsMatchIn(message) ||
                    lower.contains("rate limit") ||
                    lower.contains("too many requests") ||
                    lower.contains("quota") -> ScholarErrorKind.RATE_LIMIT

                HTTP_5XX.containsMatchIn(message) ||
                    lower.contains("service unavailable") ||
                    lower.contains("bad gateway") -> ScholarErrorKind.SERVER

                lower.contains("not configured") ||
                    lower.contains("missing gemini_api_key") -> ScholarErrorKind.CONFIG

                error is java.io.IOException ||
                    lower.contains("unable to resolve host") ||
                    lower.contains("connect") ||
                    lower.contains("offline") -> ScholarErrorKind.OFFLINE

                else -> ScholarErrorKind.UNKNOWN
            }
        }

        internal fun describeScholarFailure(error: Throwable): String =
            describeFailure(classifyScholarFailure(error), error)

        internal fun describeFailure(kind: ScholarErrorKind, error: Throwable): String =
            when (kind) {
                ScholarErrorKind.TIMEOUT ->
                    "The AI Scholar took too long to answer (45s timeout). Your question was not " +
                        "lost — tap Retry to try again."

                ScholarErrorKind.SAFETY ->
                    "This question falls outside the app's religious safety guidelines. Rephrase it, " +
                        "or ask a local qualified scholar."

                ScholarErrorKind.RATE_LIMIT ->
                    "The AI Scholar is receiving too many requests (rate limited). " +
                        "Please try again in a minute."

                ScholarErrorKind.SERVER ->
                    "The AI Scholar proxy is temporarily unavailable (server error). " +
                        "Please retry shortly; your cached Quran and hadith are unaffected."

                ScholarErrorKind.CONFIG ->
                    "AI Scholar is not configured on this build yet. Quran, hadith and the " +
                        "offline library keep working."

                ScholarErrorKind.OFFLINE ->
                    "No connection to the AI Scholar. Check your network — the Quran, " +
                        "hadith and nisab caches keep working offline."

                ScholarErrorKind.UNKNOWN ->
                    error.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "Failed to connect to the Islamic Scholar service."
            }

        private fun isRetryable(kind: ScholarErrorKind): Boolean =
            kind == ScholarErrorKind.RATE_LIMIT ||
                kind == ScholarErrorKind.SERVER ||
                kind == ScholarErrorKind.TIMEOUT
    }

    private val historyStore = appContext?.let { ScholarHistoryStore(it) }

    private val _sessions = MutableStateFlow(initialSessions())
    val sessions: StateFlow<List<ScholarChatSession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow(_sessions.value.first().id)
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    private val _uiState = MutableStateFlow(
        AiScholarUiState(
            messages = _sessions.value.first().messages.map { it.toUiModel() },
            disclaimerAcceptedForCurrentChat = _sessions.value.first().messages.isNotEmpty()
        )
    )
    val uiState: StateFlow<AiScholarUiState> = _uiState.asStateFlow()

    private val _streamingDraft = MutableStateFlow<StreamingDraft?>(null)
    val streamingDraft: StateFlow<StreamingDraft?> = _streamingDraft.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<AiScholarNavigationEvent>()
    val navigationEvents: SharedFlow<AiScholarNavigationEvent> = _navigationEvents.asSharedFlow()

    private var streamJob: Job? = null
    private var slowNoticeJob: Job? = null
    private var lastSubmittedQuery: String? = null

    /**
     * Emissions for one message id only, so a chat bubble that is not being streamed never
     * recomposes while tokens arrive.
     */
    fun draftTextFor(messageId: String): Flow<StreamingDraft?> =
        _streamingDraft
            .map { draft -> draft?.takeIf { it.messageId == messageId } }
            .distinctUntilChanged()

    fun onIntent(intent: AiScholarUiIntent) {
        when (intent) {
            is AiScholarUiIntent.InputTextChanged -> {
                _uiState.update { it.copy(inputText = intent.text) }
            }
            is AiScholarUiIntent.SendQuery -> {
                sendQuery(intent.query)
            }
            is AiScholarUiIntent.SelectMadhhabTab -> {
                _uiState.update { it.copy(activeMadhhabTab = intent.tab) }
            }
            is AiScholarUiIntent.ClickCitation -> {
                handleCitationClick(intent.sourceTitle, intent.deepLink)
            }
            is AiScholarUiIntent.AcceptDisclaimer -> {
                _uiState.update { it.copy(disclaimerAcceptedForCurrentChat = true) }
            }
            is AiScholarUiIntent.RetryLastQuery -> {
                val last = lastSubmittedQuery
                    ?: _uiState.value.messages.lastOrNull { it.role == "user" }?.content
                if (!last.isNullOrBlank()) {
                    sendQuery(last)
                }
            }
            is AiScholarUiIntent.StopStreaming -> stopStreaming()
            is AiScholarUiIntent.ClearConversation -> {
                abandonStream()
                lastSubmittedQuery = null
                _uiState.update {
                    it.copy(
                        messages = emptyList(),
                        isStreaming = false,
                        error = null,
                        errorKind = null,
                        stillVerifying = false,
                        disclaimerAcceptedForCurrentChat = false
                    )
                }
                syncActiveSession(emptyList(), resetTitle = true)
            }
            is AiScholarUiIntent.NewChat -> startNewChat()
            is AiScholarUiIntent.SwitchChat -> switchChat(intent.sessionId)
            is AiScholarUiIntent.DeleteChat -> deleteChat(intent.sessionId)
            is AiScholarUiIntent.TogglePinChat -> togglePin(intent.sessionId)
            is AiScholarUiIntent.ChatSearchChanged -> {
                _uiState.update { it.copy(chatSearchQuery = intent.query) }
            }
            is AiScholarUiIntent.ClearAllChats -> clearAllChats()
        }
    }

    private fun startNewChat() {
        abandonStream()
        lastSubmittedQuery = null
        val session = ScholarChatSession(
            id = UUID.randomUUID().toString(),
            title = NEW_CHAT_TITLE,
            updatedAtMs = System.currentTimeMillis(),
            messages = emptyList()
        )
        _sessions.update { listOf(session) + it }
        _activeSessionId.value = session.id
        _uiState.update {
            it.copy(
                messages = emptyList(),
                inputText = "",
                isStreaming = false,
                error = null,
                errorKind = null,
                stillVerifying = false,
                streamingMessageId = null,
                disclaimerAcceptedForCurrentChat = false
            )
        }
        persistSessions()
    }

    private fun switchChat(sessionId: String) {
        if (_activeSessionId.value == sessionId) return
        // Anything already streamed is written back to the chat it belongs to, never to the
        // chat the reader is moving to.
        abandonStream()
        val session = _sessions.value.firstOrNull { it.id == sessionId } ?: return
        lastSubmittedQuery = session.messages.lastOrNull { it.role == "user" }?.text
        _activeSessionId.value = session.id
        _uiState.update {
            it.copy(
                messages = session.messages.map { it.toUiModel() },
                inputText = "",
                isStreaming = false,
                error = null,
                errorKind = null,
                stillVerifying = false,
                streamingMessageId = null,
                disclaimerAcceptedForCurrentChat = session.messages.isNotEmpty()
            )
        }
    }

    private fun deleteChat(sessionId: String) {
        if (_streamingDraft.value?.sessionId == sessionId) {
            streamJob?.cancel()
            streamJob = null
            slowNoticeJob?.cancel()
            slowNoticeJob = null
            _streamingDraft.value = null
        }
        val remaining = _sessions.value.filterNot { it.id == sessionId }
        if (remaining.isEmpty()) {
            startNewChat()
            return
        }
        _sessions.value = remaining
        if (_activeSessionId.value == sessionId) {
            val next = remaining.first()
            _activeSessionId.value = next.id
            _uiState.update {
                it.copy(
                    messages = next.messages.map { it.toUiModel() },
                    inputText = "",
                    isStreaming = false,
                    error = null,
                    errorKind = null,
                    stillVerifying = false,
                    streamingMessageId = null,
                    disclaimerAcceptedForCurrentChat = next.messages.isNotEmpty()
                )
            }
            lastSubmittedQuery = next.messages.lastOrNull { it.role == "user" }?.text
        }
        persistSessions()
    }

    private fun togglePin(sessionId: String) {
        _sessions.update { sessions ->
            val now = System.currentTimeMillis()
            sessions
                .map { session ->
                    if (session.id == sessionId) {
                        session.copy(pinned = !session.pinned, updatedAtMs = now)
                    } else {
                        session
                    }
                }
                .sortedWith(
                    compareByDescending<ScholarChatSession> { it.pinned }
                        .thenByDescending { it.updatedAtMs }
                )
        }
        persistSessions()
    }

    private fun clearAllChats() {
        abandonStream()
        lastSubmittedQuery = null
        val session = ScholarChatSession(
            id = UUID.randomUUID().toString(),
            title = NEW_CHAT_TITLE,
            updatedAtMs = System.currentTimeMillis(),
            messages = emptyList()
        )
        _sessions.value = listOf(session)
        _activeSessionId.value = session.id
        _uiState.update {
            it.copy(
                messages = emptyList(),
                inputText = "",
                isStreaming = false,
                error = null,
                errorKind = null,
                stillVerifying = false,
                streamingMessageId = null,
                disclaimerAcceptedForCurrentChat = false
            )
        }
        persistSessions()
    }

    private fun sendQuery(queryText: String) {
        val trimmed = queryText.trim()
        if (trimmed.isBlank()) return
        // A new question supersedes the previous stream, but its partial answer is kept in
        // the chat it was typed into.
        abandonStream()
        lastSubmittedQuery = trimmed

        val sessionId = _activeSessionId.value
        val userMessageId = UUID.randomUUID().toString()
        val assistantMessageId = UUID.randomUUID().toString()

        val userMsg = ChatMessageModel(
            id = userMessageId,
            role = "user",
            content = trimmed,
            timestamp = "Just now"
        )

        val pendingAssistantMsg = ChatMessageModel(
            id = assistantMessageId,
            role = "assistant",
            content = "",
            timestamp = "Just now"
        )

        val previousMessages = _uiState.value.messages
        val updatedMessages = previousMessages + userMsg + pendingAssistantMsg

        _uiState.update {
            it.copy(
                messages = updatedMessages,
                inputText = "",
                isStreaming = true,
                error = null,
                errorKind = null,
                stillVerifying = false,
                streamingMessageId = assistantMessageId
            )
        }
        syncSession(sessionId, updatedMessages)

        // Build history from the prior turns only, so the current user message is not
        // duplicated when the proxy re-appends it as the active turn.
        val historyDto = previousMessages.filter { it.content.isNotBlank() }.takeLast(6).map {
            ChatMessageDto(role = it.role, content = it.content)
        }

        val request = RagQueryRequest(
            query = trimmed,
            conversationHistory = historyDto,
            preferredLanguage = java.util.Locale.getDefault().language.ifBlank { "en" },
            schoolOfThoughtContext = preferences?.madhhab?.name ?: "ALL"
        )

        streamAnswer(
            request = request,
            sessionId = sessionId,
            assistantMessageId = assistantMessageId,
            attempt = 0
        )
    }

    private fun streamAnswer(
        request: RagQueryRequest,
        sessionId: String,
        assistantMessageId: String,
        attempt: Int
    ) {
        _streamingDraft.value = StreamingDraft(sessionId, assistantMessageId)
        startSlowNotice()

        streamJob = viewModelScope.launch {
            try {
                withTimeout(STREAM_TIMEOUT_MS) {
                    scholarRepository.streamAskScholar(request).collect { chunk ->
                        // Isolation: a delta is only ever applied to the chat that started
                        // the stream. A stale emitter cannot leak into the reader's chat.
                        if (_activeSessionId.value != sessionId) return@collect
                        when (chunk) {
                            is RagStreamChunk.TextDelta -> appendDraft(delta = chunk.delta)
                            is RagStreamChunk.SourceCitation -> attachCitation(
                                assistantMessageId,
                                chunk.source.title,
                                chunk.source.navigationDeepLink
                            )
                            is RagStreamChunk.MadhhabBreakdown -> attachMadhhabPosition(
                                assistantMessageId,
                                chunk.school,
                                chunk.summary
                            )
                            is RagStreamChunk.GuardrailDisclaimer -> {
                                _uiState.update { it.copy(guardrailNotice = chunk.notice) }
                            }
                            is RagStreamChunk.SourcesUnverified -> attachUnverifiedSources(
                                assistantMessageId,
                                chunk.reason
                            )
                            is RagStreamChunk.Complete -> completeStream(sessionId, assistantMessageId)
                        }
                    }
                }
                if (streamJob == coroutineContext[Job]) {
                    completeStream(sessionId, assistantMessageId)
                }
            } catch (e: TimeoutCancellationException) {
                onStreamFailure(e, request, sessionId, assistantMessageId, attempt)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                onStreamFailure(e, request, sessionId, assistantMessageId, attempt)
            } finally {
                slowNoticeJob?.cancel()
                slowNoticeJob = null
                if (streamJob == coroutineContext[Job]) {
                    streamJob = null
                    _uiState.update { it.copy(stillVerifying = false) }
                }
            }
        }
    }

    private suspend fun onStreamFailure(
        error: Throwable,
        request: RagQueryRequest,
        sessionId: String,
        assistantMessageId: String,
        attempt: Int
    ) {
        val kind = classifyScholarFailure(error)
        val streamedSoFar = _streamingDraft.value?.text.orEmpty()
        // Only retry when nothing has been shown yet: re-streaming over visible text would
        // duplicate the answer.
        if (attempt < MAX_RETRIES && streamedSoFar.isBlank() && isRetryable(kind)) {
            val backoff = RETRY_BASE_DELAY_MS * (1L shl attempt) +
                Random.nextLong(0, RETRY_JITTER_MS)
            delay(backoff)
            if (_activeSessionId.value == sessionId) {
                streamAnswer(request, sessionId, assistantMessageId, attempt + 1)
            }
            return
        }
        finishStreamWithError(kind, error)
    }

    private fun finishStreamWithError(kind: ScholarErrorKind, error: Throwable) {
        finalizeDraft()
        _uiState.update {
            it.copy(
                isStreaming = false,
                stillVerifying = false,
                streamingMessageId = null,
                error = describeFailure(kind, error),
                errorKind = kind
            )
        }
        syncActiveSession(_uiState.value.messages)
    }

    private fun completeStream(sessionId: String, assistantMessageId: String) {
        finalizeDraft()
        val messages = _uiState.value.messages
        val unanswered = messages.lastOrNull()
            ?.let { it.id == assistantMessageId && it.content.isBlank() } == true
        _uiState.update {
            it.copy(
                isStreaming = false,
                stillVerifying = false,
                streamingMessageId = null,
                error = if (unanswered) {
                    "The Scholar returned an empty answer. Tap Retry to ask again."
                } else {
                    it.error
                }
            )
        }
        syncSession(sessionId, _uiState.value.messages)
    }

    private fun stopStreaming() {
        val draft = _streamingDraft.value
        val text = draft?.text.orEmpty()
        streamJob?.cancel()
        streamJob = null
        slowNoticeJob?.cancel()
        slowNoticeJob = null
        _streamingDraft.value = null
        _uiState.update { state ->
            val messages = when {
                draft == null -> state.messages
                text.isBlank() -> state.messages.filterNot { it.id == draft.messageId }
                else -> state.messages.map { msg ->
                    if (msg.id == draft.messageId) {
                        msg.copy(content = stripHtmlForDisplay(text))
                    } else {
                        msg
                    }
                }
            }
            state.copy(
                messages = messages,
                isStreaming = false,
                stillVerifying = false,
                streamingMessageId = null,
                error = null,
                errorKind = null
            )
        }
        draft?.let { persistDraftResult(it, text) }
    }

    /** Cancels the live stream and keeps whatever was produced in its own chat. */
    private fun abandonStream() {
        val draft = _streamingDraft.value
        streamJob?.cancel()
        streamJob = null
        slowNoticeJob?.cancel()
        slowNoticeJob = null
        _streamingDraft.value = null
        _uiState.update { state ->
            // Drop an answer that never produced a token instead of leaving an empty bubble.
            val messages = if (draft != null && draft.text.isBlank()) {
                state.messages.filterNot { it.id == draft.messageId }
            } else {
                state.messages
            }
            state.copy(
                messages = messages,
                isStreaming = false,
                stillVerifying = false,
                streamingMessageId = null
            )
        }
        draft?.let { persistDraftResult(it, it.text) }
    }

    private fun persistDraftResult(draft: StreamingDraft, text: String) {
        val clean = stripHtmlForDisplay(text)
        if (clean.isBlank()) return
        // Apply the draft to its own message first: the UI list may still hold the blank
        // placeholder when the reader switches chats mid-answer.
        val withDraft = _uiState.value.messages.map { msg ->
            if (msg.id == draft.messageId) msg.copy(content = clean) else msg
        }
        if (draft.sessionId == _activeSessionId.value) {
            syncSession(draft.sessionId, withDraft)
            return
        }
        val now = System.currentTimeMillis()
        _sessions.update { sessions ->
            sessions.map { session ->
                if (session.id != draft.sessionId) {
                    session
                } else {
                    val withoutPlaceholder = session.messages.filterNot { it.id == draft.messageId }
                    session.copy(
                        messages = withoutPlaceholder + ScholarChatMessage(
                            id = draft.messageId,
                            role = "assistant",
                            text = clean,
                            timestampMs = now
                        ),
                        updatedAtMs = now
                    )
                }
            }
        }
        persistSessions()
    }

    private fun startSlowNotice() {
        slowNoticeJob?.cancel()
        slowNoticeJob = viewModelScope.launch {
            delay(SLOW_STREAM_NOTICE_MS)
            if (_uiState.value.isStreaming) {
                _uiState.update { it.copy(stillVerifying = true) }
            }
        }
    }

    /** Appends to the draft only: the message list is untouched per token. */
    private fun appendDraft(delta: String) {
        if (delta.isEmpty()) return
        _streamingDraft.update { draft ->
            draft?.copy(text = draft.text + delta)
        }
    }

    /** Moves the accumulated draft into the message, once. */
    private fun finalizeDraft() {
        val draft = _streamingDraft.value ?: return
        _streamingDraft.value = null
        val clean = stripHtmlForDisplay(draft.text)
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == draft.messageId) msg.copy(content = clean) else msg
                },
                streamingMessageId = null
            )
        }
    }

    private fun attachCitation(messageId: String, citationTitle: String, deepLink: String?) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id != messageId) {
                        msg
                    } else {
                        val citation = Citation(title = citationTitle, deepLink = deepLink)
                        // Avoid duplicate citations with the same title.
                        val existing = msg.citations.filterNot { it.title == citationTitle }
                        msg.copy(
                            sourceTitle = citationTitle,
                            sourceDeepLink = deepLink,
                            citations = existing + citation
                        )
                    }
                }
            )
        }
    }

    private fun attachMadhhabPosition(messageId: String, school: String, summary: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id != messageId) {
                        msg
                    } else {
                        when (school.lowercase()) {
                            "hanafi" -> msg.copy(hanafiStance = summary)
                            "maliki" -> msg.copy(malikiStance = summary)
                            "shafi'i", "shafii" -> msg.copy(shafiiStance = summary)
                            "hanbali" -> msg.copy(hanbaliStance = summary)
                            else -> msg
                        }
                    }
                }
            )
        }
    }

    private fun attachUnverifiedSources(messageId: String, reason: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == messageId) msg.copy(sourcesUnverified = reason) else msg
                }
            )
        }
    }

    private fun handleCitationClick(sourceTitle: String, deepLink: String?) {
        viewModelScope.launch {
            if (!deepLink.isNullOrBlank() && deepLink.startsWith("quran/")) {
                val parts = deepLink.removePrefix("quran/").split("/")
                val surah = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(1, 114) ?: 1
                val ayah = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                _navigationEvents.emit(AiScholarNavigationEvent.NavigateToQuran(surah, ayah))
                return@launch
            }
            if (!deepLink.isNullOrBlank() && deepLink.startsWith("hadith/")) {
                val parts = deepLink.removePrefix("hadith/").split("/")
                val book = parts.getOrNull(0)?.lowercase() ?: "bukhari"
                val num = parts.getOrNull(1)?.toIntOrNull() ?: 1
                _navigationEvents.emit(
                    AiScholarNavigationEvent.NavigateToHadith(
                        bookKey = book,
                        hadithNumber = num,
                        sectionHint = extractSectionHint(sourceTitle)
                    )
                )
                return@launch
            }

            val quranMatch = SURAH_AYAH_REGEX.find(sourceTitle)
            if (quranMatch != null || sourceTitle.contains("Quran", true) || sourceTitle.contains("Surah", true)) {
                val surah = quranMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 114) ?: 1
                val ayah = quranMatch?.groupValues?.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                _navigationEvents.emit(AiScholarNavigationEvent.NavigateToQuran(surah, ayah))
                return@launch
            }

            val lower = sourceTitle.lowercase()
            val recognized = lower.contains("muslim") || lower.contains("bukhari") ||
                lower.contains("dawud") || lower.contains("tirmidhi") ||
                lower.contains("nasai") || lower.contains("nasa'i") ||
                lower.contains("majah") || lower.contains("nawawi") ||
                lower.contains("malik") || lower.contains("muwatta") ||
                lower.contains("ahmad") || lower.contains("darimi")
            if (!recognized) {
                _navigationEvents.emit(AiScholarNavigationEvent.CitationUnresolved(sourceTitle))
                return@launch
            }
            val bookKey = when {
                lower.contains("muslim") -> "muslim"
                lower.contains("bukhari") -> "bukhari"
                lower.contains("dawud") -> "abudawud"
                lower.contains("tirmidhi") -> "tirmidhi"
                lower.contains("nasai") || lower.contains("nasa'i") -> "nasai"
                lower.contains("majah") -> "ibnmajah"
                lower.contains("nawawi") -> "nawawi"
                lower.contains("malik") || lower.contains("muwatta") -> "malik"
                lower.contains("ahmad") -> "ahmad"
                lower.contains("darimi") -> "darimi"
                else -> return@launch
            }
            val num = HADITH_NUMBER_REGEX.findAll(sourceTitle).lastOrNull()?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (num == null) {
                // Recognised collection without a number, or nothing matched at all: say so
                // instead of ignoring the tap.
                _navigationEvents.emit(AiScholarNavigationEvent.CitationUnresolved(sourceTitle))
                return@launch
            }
            _navigationEvents.emit(
                AiScholarNavigationEvent.NavigateToHadith(
                    bookKey = bookKey,
                    hadithNumber = num,
                    sectionHint = extractSectionHint(sourceTitle)
                )
            )
        }
    }

    private fun initialSessions(): List<ScholarChatSession> {
        val stored = historyStore?.load().orEmpty()
            .filter { it.id.isNotBlank() }
            .map { session ->
                val cleaned = session.messages.filterNot { it.id in LEGACY_SEED_MESSAGE_IDS }
                val legacyTitle = session.title.contains("Sunni", ignoreCase = true) ||
                    session.title.startsWith("Welcome to", ignoreCase = true)
                val recoveredTitle = cleaned.firstOrNull { it.role == "user" }
                    ?.text
                    ?.trim()
                    ?.take(40)
                    ?.takeIf { it.isNotBlank() }
                session.copy(
                    title = if (legacyTitle) recoveredTitle ?: NEW_CHAT_TITLE else session.title,
                    messages = cleaned
                )
            }
            .sortedWith(
                compareByDescending<ScholarChatSession> { it.pinned }
                    .thenByDescending { it.updatedAtMs }
            )
        return stored.ifEmpty { listOf(emptySession()) }
    }

    private fun emptySession(): ScholarChatSession = ScholarChatSession(
        id = UUID.randomUUID().toString(),
        title = NEW_CHAT_TITLE,
        updatedAtMs = System.currentTimeMillis(),
        messages = emptyList()
    )

    private fun syncActiveSession(messages: List<ChatMessageModel>, resetTitle: Boolean = false) {
        syncSession(_activeSessionId.value, messages, resetTitle)
    }

    private fun syncSession(
        sessionId: String,
        messages: List<ChatMessageModel>,
        resetTitle: Boolean = false
    ) {
        val existing = _sessions.value.firstOrNull { it.id == sessionId }
        val firstQuestion = messages.firstOrNull { it.role == "user" }?.content?.trim()
        val title = when {
            resetTitle -> NEW_CHAT_TITLE
            firstQuestion.isNullOrBlank() && existing != null -> existing.title
            !firstQuestion.isNullOrBlank() -> firstQuestion.take(40)
            else -> NEW_CHAT_TITLE
        }
        val now = System.currentTimeMillis()
        val storedMessages = messages
            .filter { it.content.isNotBlank() }
            .map { msg ->
                ScholarChatMessage(
                    id = msg.id,
                    role = msg.role,
                    text = msg.content,
                    timestampMs = now,
                    citations = msg.citations,
                    hanafiStance = msg.hanafiStance,
                    malikiStance = msg.malikiStance,
                    shafiiStance = msg.shafiiStance,
                    hanbaliStance = msg.hanbaliStance,
                    sourcesUnverified = msg.sourcesUnverified
                )
            }
        val updated = ScholarChatSession(
            id = sessionId,
            title = title.ifBlank { NEW_CHAT_TITLE },
            updatedAtMs = now,
            messages = storedMessages,
            pinned = existing?.pinned ?: false
        )
        _sessions.update { current ->
            val withoutTarget = current.filterNot { it.id == sessionId }
            withoutTarget + updated
        }
        persistSessions()
    }

    private fun persistSessions() {
        historyStore?.save(_sessions.value)
    }

    private fun ScholarChatMessage.toUiModel(): ChatMessageModel = ChatMessageModel(
        id = id,
        role = role,
        content = text,
        timestamp = formatTimestamp(timestampMs),
        citations = citations,
        hanafiStance = hanafiStance,
        malikiStance = malikiStance,
        shafiiStance = shafiiStance,
        hanbaliStance = hanbaliStance,
        sourcesUnverified = sourcesUnverified
    )

    private fun formatTimestamp(timestampMs: Long): String {
        val delta = System.currentTimeMillis() - timestampMs
        if (delta in 0..60_000L) return "Just now"
        return SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(timestampMs))
    }
}
