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
import com.sakinah.deen.network.model.ChatMessageDto
import com.sakinah.deen.network.model.RagQueryRequest
import com.sakinah.deen.network.model.RagStreamChunk
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class AiScholarUiState(
    val messages: List<ChatMessageModel> = emptyList(),
    val inputText: String = "",
    val isStreaming: Boolean = false,
    val guardrailNotice: String = "Educational Reference Notice: Sourced from verified Sunni texts (Quran, Hadith, 4 Madhhabs). For binding personal fatwas, consult a local Mufti.",
    val activeMadhhabTab: String = "hanafi",
    val error: String? = null
)

sealed class AiScholarUiIntent {
    data class InputTextChanged(val text: String) : AiScholarUiIntent()
    data class SendQuery(val query: String) : AiScholarUiIntent()
    data class SelectMadhhabTab(val tab: String) : AiScholarUiIntent()
    data class ClickCitation(val sourceTitle: String, val deepLink: String? = null) : AiScholarUiIntent()
    data object RetryLastQuery : AiScholarUiIntent()
    data object ClearConversation : AiScholarUiIntent()
    data object NewChat : AiScholarUiIntent()
    data class SwitchChat(val sessionId: String) : AiScholarUiIntent()
    data class DeleteChat(val sessionId: String) : AiScholarUiIntent()
    data object ClearAllChats : AiScholarUiIntent()
}

sealed class AiScholarNavigationEvent {
    data class NavigateToQuran(val surah: Int, val ayah: Int) : AiScholarNavigationEvent()
    data class NavigateToHadith(
        val bookKey: String,
        val hadithNumber: Int,
        val sectionHint: String? = null
    ) : AiScholarNavigationEvent()
}

typealias Citation = com.sakinah.deen.data.model.Citation

class AiScholarViewModel(
    private val scholarRepository: SunniAiScholarRepository,
    private val appContext: Context? = null,
    private val preferences: com.sakinah.deen.data.prefs.SakinahPreferences? = null
) : ViewModel() {

    companion object {
        private const val STREAM_TIMEOUT_MS = 45_000L
        private val HTTP_5XX = Regex("HTTP 5\\d{2}")
        private val HTTP_429 = Regex("HTTP 429")
        private val SURAH_AYAH_REGEX = Regex("(\\d{1,3})\\s*:\\s*(\\d{1,3})")
        private val HADITH_NUMBER_REGEX = Regex("#?(\\d{1,5})")
        private const val NEW_CHAT_TITLE = "New chat"

        internal fun describeScholarFailure(error: Throwable): String {
            val message = error.localizedMessage.orEmpty()
            val lower = message.lowercase()
            return when {
                error is TimeoutCancellationException || lower.contains("timeout") || lower.contains("timed out") ->
                    "The AI Scholar request timed out. Check your connection and tap Retry."

                HTTP_429.containsMatchIn(message) ||
                    lower.contains("rate limit") ||
                    lower.contains("quota") ->
                    "The AI Scholar is receiving too many requests (rate limited). " +
                        "Please try again in a minute."

                HTTP_5XX.containsMatchIn(message) ||
                    lower.contains("service unavailable") ||
                    lower.contains("bad gateway") ->
                    "The AI Scholar proxy is temporarily unavailable (server error). " +
                        "Please retry shortly; your cached Quran and hadith are unaffected."

                error is java.io.IOException ||
                    lower.contains("unable to resolve host") ||
                    lower.contains("connect") ||
                    lower.contains("offline") ->
                    "No connection to the AI Scholar. Check your network — the Quran, " +
                        "hadith and nisab caches keep working offline."

                else -> message.ifBlank { "Failed to connect to the Sunni Scholar service." }
            }
        }
    }

    private val historyStore = appContext?.let { ScholarHistoryStore(it) }

    private val _sessions = MutableStateFlow(initialSessions())
    val sessions: StateFlow<List<ScholarChatSession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow(_sessions.value.first().id)
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    private val _uiState = MutableStateFlow(
        AiScholarUiState(
            messages = _sessions.value.first().messages.map { it.toUiModel() }
        )
    )
    val uiState: StateFlow<AiScholarUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<AiScholarNavigationEvent>()
    val navigationEvents: SharedFlow<AiScholarNavigationEvent> = _navigationEvents.asSharedFlow()

    private var streamJob: Job? = null
    private var lastSubmittedQuery: String? = null

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
            is AiScholarUiIntent.RetryLastQuery -> {
                val last = lastSubmittedQuery ?: _uiState.value.messages.lastOrNull { it.role == "user" }?.content
                if (!last.isNullOrBlank()) {
                    sendQuery(last)
                }
            }
            is AiScholarUiIntent.ClearConversation -> {
                streamJob?.cancel()
                streamJob = null
                lastSubmittedQuery = null
                _uiState.update {
                    it.copy(
                        messages = emptyList(),
                        isStreaming = false,
                        error = null
                    )
                }
                syncActiveSession(emptyList(), resetTitle = true)
            }
            is AiScholarUiIntent.NewChat -> startNewChat()
            is AiScholarUiIntent.SwitchChat -> switchChat(intent.sessionId)
            is AiScholarUiIntent.DeleteChat -> deleteChat(intent.sessionId)
            is AiScholarUiIntent.ClearAllChats -> clearAllChats()
        }
    }

    private fun startNewChat() {
        streamJob?.cancel()
        streamJob = null
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
            it.copy(messages = emptyList(), inputText = "", isStreaming = false, error = null)
        }
        persistSessions()
    }

    private fun switchChat(sessionId: String) {
        val session = _sessions.value.firstOrNull { it.id == sessionId } ?: return
        streamJob?.cancel()
        streamJob = null
        lastSubmittedQuery = session.messages.lastOrNull { it.role == "user" }?.text
        _activeSessionId.value = session.id
        _uiState.update {
            it.copy(
                messages = session.messages.map { it.toUiModel() },
                inputText = "",
                isStreaming = false,
                error = null
            )
        }
    }

    private fun deleteChat(sessionId: String) {
        streamJob?.cancel()
        streamJob = null
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
                    error = null
                )
            }
            lastSubmittedQuery = next.messages.lastOrNull { it.role == "user" }?.text
        }
        persistSessions()
    }

    private fun clearAllChats() {
        streamJob?.cancel()
        streamJob = null
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
            it.copy(messages = emptyList(), inputText = "", isStreaming = false, error = null)
        }
        persistSessions()
    }

    private fun sendQuery(queryText: String) {
        streamJob?.cancel()
        streamJob = null

        val trimmed = queryText.trim()
        if (trimmed.isBlank()) return
        lastSubmittedQuery = trimmed

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
                error = null
            )
        }
        syncActiveSession(updatedMessages)

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

        streamJob = viewModelScope.launch {
            try {
                withTimeout(STREAM_TIMEOUT_MS) {
                    scholarRepository.streamAskScholar(request).collect { chunk ->
                        when (chunk) {
                            is RagStreamChunk.TextDelta -> {
                                appendStreamingDelta(assistantMessageId, chunk.delta)
                            }
                            is RagStreamChunk.SourceCitation -> {
                                attachCitation(
                                    assistantMessageId,
                                    chunk.source.title,
                                    chunk.source.navigationDeepLink
                                )
                            }
                            is RagStreamChunk.MadhhabBreakdown -> {
                                attachMadhhabPosition(assistantMessageId, chunk.school, chunk.summary)
                            }
                            is RagStreamChunk.GuardrailDisclaimer -> {
                                _uiState.update { it.copy(guardrailNotice = chunk.notice) }
                            }
                            is RagStreamChunk.Complete -> {
                                val messages = _uiState.value.messages
                                val unanswered = messages.lastOrNull()
                                    ?.let { it.role == "assistant" && it.content.isBlank() } == true
                                _uiState.update {
                                    it.copy(
                                        isStreaming = false,
                                        error = if (unanswered) {
                                            "The Scholar returned an empty answer. Tap Retry to ask again."
                                        } else {
                                            it.error
                                        }
                                    )
                                }
                                syncActiveSession(_uiState.value.messages)
                            }
                        }
                    }
                }
                if (streamJob == coroutineContext[Job]) {
                    _uiState.update { it.copy(isStreaming = false) }
                    syncActiveSession(_uiState.value.messages)
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        error = describeScholarFailure(e)
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        error = describeScholarFailure(e)
                    )
                }
                syncActiveSession(_uiState.value.messages)
            } finally {
                if (streamJob == coroutineContext[Job]) streamJob = null
            }
        }
    }

    private fun appendStreamingDelta(messageId: String, delta: String) {
        val updated = _uiState.value.messages.map { msg ->
            if (msg.id == messageId) {
                msg.copy(content = msg.content + delta)
            } else msg
        }
        _uiState.update { it.copy(messages = updated) }
    }

    private fun attachCitation(messageId: String, citationTitle: String, deepLink: String?) {
        val updated = _uiState.value.messages.map { msg ->
            if (msg.id == messageId) {
                val citation = Citation(title = citationTitle, deepLink = deepLink)
                // Avoid duplicate citations with the same title.
                val existing = msg.citations.filterNot { it.title == citationTitle }
                msg.copy(
                    sourceTitle = citationTitle,
                    sourceDeepLink = deepLink,
                    citations = existing + citation
                )
            } else msg
        }
        _uiState.update { it.copy(messages = updated) }
    }

    private fun attachMadhhabPosition(messageId: String, school: String, summary: String) {
        val updated = _uiState.value.messages.map { msg ->
            if (msg.id == messageId) {
                when (school.lowercase()) {
                    "hanafi" -> msg.copy(hanafiStance = summary)
                    "maliki" -> msg.copy(malikiStance = summary)
                    "shafi'i", "shafii" -> msg.copy(shafiiStance = summary)
                    "hanbali" -> msg.copy(hanbaliStance = summary)
                    else -> msg
                }
            } else msg
        }
        _uiState.update { it.copy(messages = updated) }
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
            if (!recognized) return@launch
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
                ?: return@launch
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
            .sortedByDescending { it.updatedAtMs }
        return stored.ifEmpty { listOf(seedSession()) }
    }

    private fun seedSession(): ScholarChatSession = ScholarChatSession(
        id = UUID.randomUUID().toString(),
        title = "Welcome to the Sunni AI Scholar",
        updatedAtMs = System.currentTimeMillis(),
        messages = listOf(
            ScholarChatMessage(
                id = "init-1",
                role = "assistant",
                text = "As-salāmu ‘alaykum wa raḥmatullāhi wa barakātuh. I am your Sunni Deen research companion, grounded in the Holy Quran, Kutub al-Tis‘ah, and the four classical legal schools.",
                timestampMs = System.currentTimeMillis()
            ),
            ScholarChatMessage(
                id = "sample-1",
                role = "user",
                text = "How do I perform Sujud as-Sahw (Prostration of Forgetfulness)?",
                timestampMs = System.currentTimeMillis()
            ),
            ScholarChatMessage(
                id = "sample-2",
                role = "assistant",
                text = "Sujud as-Sahw (سجود السهو) consists of two prostrations performed to rectify unintentional omissions or additions in prayer, followed by Salam.\n\nGrounding Proof: In Sahih Muslim #1272 (Kitab al-Masajid — Book of Mosques and Places of Prayer), Abu Sa‘id al-Khudri reported that the Prophet (ﷺ) instructed to discard doubt, build upon certainty, and make two prostrations before concluding the prayer.",
                timestampMs = System.currentTimeMillis()
            )
        )
    )

    private fun syncActiveSession(messages: List<ChatMessageModel>, resetTitle: Boolean = false) {
        val activeId = _activeSessionId.value
        val existing = _sessions.value.firstOrNull { it.id == activeId }
        val firstQuestion = messages.firstOrNull { it.role == "user" }?.content?.trim()
        val title = when {
            resetTitle -> NEW_CHAT_TITLE
            firstQuestion.isNullOrBlank() && existing != null -> existing.title
            !firstQuestion.isNullOrBlank() -> firstQuestion.take(40)
            else -> NEW_CHAT_TITLE
        }
        val storedMessages = messages
            .filter { it.content.isNotBlank() }
            .map { msg ->
                ScholarChatMessage(
                    id = msg.id,
                    role = msg.role,
                    text = msg.content,
                    timestampMs = System.currentTimeMillis(),
                    citations = msg.citations,
                    hanafiStance = msg.hanafiStance,
                    malikiStance = msg.malikiStance,
                    shafiiStance = msg.shafiiStance,
                    hanbaliStance = msg.hanbaliStance
                )
            }
        val updated = ScholarChatSession(
            id = activeId,
            title = title.ifBlank { NEW_CHAT_TITLE },
            updatedAtMs = System.currentTimeMillis(),
            messages = storedMessages
        )
        _sessions.update { current ->
            val withoutActive = current.filterNot { it.id == activeId }
            withoutActive + updated
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
        hanbaliStance = hanbaliStance
    )

    private fun formatTimestamp(timestampMs: Long): String {
        val delta = System.currentTimeMillis() - timestampMs
        if (delta in 0..60_000L) return "Just now"
        return SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(timestampMs))
    }
}
