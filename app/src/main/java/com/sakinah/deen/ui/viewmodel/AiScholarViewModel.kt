package com.sakinah.deen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakinah.deen.data.model.ChatMessageModel
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
}

sealed class AiScholarNavigationEvent {
    data class NavigateToQuran(val surah: Int, val ayah: Int) : AiScholarNavigationEvent()
    data class NavigateToHadith(val bookKey: String, val hadithNumber: Int) : AiScholarNavigationEvent()
}

class AiScholarViewModel(
    private val scholarRepository: SunniAiScholarRepository
) : ViewModel() {

    companion object {
        private const val STREAM_TIMEOUT_MS = 45_000L
        private val HTTP_5XX = Regex("HTTP 5\\d{2}")
        private val HTTP_429 = Regex("HTTP 429")
        private val SURAH_AYAH_REGEX = Regex("(\\d{1,3})\\s*:\\s*(\\d{1,3})")
        private val HADITH_NUMBER_REGEX = Regex("#?(\\d{1,5})")

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

    private val _uiState = MutableStateFlow(
        AiScholarUiState(
            messages = listOf(
                ChatMessageModel(
                    id = "init-1",
                    role = "assistant",
                    content = "As-salāmu ‘alaykum wa raḥmatullāhi wa barakātuh. I am your Sunni Deen research companion, grounded in the Holy Quran, Kutub al-Tis‘ah, and the four classical legal schools.",
                    timestamp = "Just now",
                    sourceTitle = "Surah An-Nisa 4:59",
                    sourceDeepLink = "quran/4/59"
                ),
                ChatMessageModel(
                    id = "sample-1",
                    role = "user",
                    content = "How do I perform Sujud as-Sahw (Prostration of Forgetfulness)?",
                    timestamp = "Just now"
                ),
                ChatMessageModel(
                    id = "sample-2",
                    role = "assistant",
                    content = "Sujud as-Sahw (سجود السهو) consists of two prostrations performed to rectify unintentional omissions or additions in prayer, followed by Salam.\n\nGrounding Proof: In Sahih Muslim #1271 (Kitab al-Masajid), the Prophet (ﷺ) instructed to build upon certainty and make two prostrations before concluding the prayer.",
                    timestamp = "Just now",
                    sourceTitle = "Sahih Muslim #1271",
                    hanafiStance = "Hanafi: Performed after the first Salam to the right. Make 2 Sujuds, repeat Tashahhud, then conclude with Salam.",
                    malikiStance = "Maliki: Before Salam for omission (Qabli); after Salam for addition (Ba'di).",
                    shafiiStance = "Shafi'i: Always performed immediately before the first Salam (Qabli).",
                    hanbaliStance = "Hanbali: Before Salam for omission; after Salam for addition or doubt resolving on probability.",
                    sourceDeepLink = "hadith/muslim/1271"
                )
            )
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
            }
        }
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

        val updatedMessages = _uiState.value.messages + userMsg + pendingAssistantMsg

        _uiState.update {
            it.copy(
                messages = updatedMessages,
                inputText = "",
                isStreaming = true,
                error = null
            )
        }

        val historyDto = updatedMessages.filter { it.content.isNotBlank() }.takeLast(6).map {
            ChatMessageDto(role = it.role, content = it.content)
        }

        val request = RagQueryRequest(
            query = trimmed,
            conversationHistory = historyDto,
            preferredLanguage = "en",
            schoolOfThoughtContext = "ALL"
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
                                _uiState.update { it.copy(isStreaming = false) }
                            }
                        }
                    }
                }
                if (streamJob == coroutineContext[Job]) {
                    _uiState.update { it.copy(isStreaming = false) }
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
                msg.copy(sourceTitle = citationTitle, sourceDeepLink = deepLink)
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
                _navigationEvents.emit(AiScholarNavigationEvent.NavigateToHadith(book, num))
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
            val bookKey = when {
                lower.contains("muslim") -> "muslim"
                lower.contains("bukhari") -> "bukhari"
                lower.contains("dawud") -> "abudawud"
                lower.contains("tirmidhi") -> "tirmidhi"
                lower.contains("nasai") || lower.contains("nasa'i") -> "nasai"
                lower.contains("majah") -> "ibnmajah"
                lower.contains("nawawi") -> "nawawi"
                lower.contains("malik") || lower.contains("muwatta") -> "malik"
                else -> "bukhari"
            }
            val num = HADITH_NUMBER_REGEX.findAll(sourceTitle).lastOrNull()?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            _navigationEvents.emit(AiScholarNavigationEvent.NavigateToHadith(bookKey, num))
        }
    }
}
