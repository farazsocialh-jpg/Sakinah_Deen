package com.sakinah.deen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.data.model.ChatMessageModel
import com.sakinah.deen.data.scholar.ScholarChatSession
import com.sakinah.deen.data.scholar.ScholarHistoryStore
import com.sakinah.deen.ui.components.CitationChip
import com.sakinah.deen.ui.components.ClickableCitationText
import com.sakinah.deen.ui.components.SegmentedPillBar
import com.sakinah.deen.ui.viewmodel.AiScholarNavigationEvent
import com.sakinah.deen.ui.viewmodel.AiScholarUiIntent
import com.sakinah.deen.ui.viewmodel.AiScholarViewModel
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PER_MESSAGE_DISCLAIMER =
    "AI-generated, not a fatwa · Verify rulings with authentic sources and qualified scholars."

private val STARTER_PROMPTS = listOf(
    "How do I perform Sujud as-Sahw?",
    "Explain Zakat on gold & savings",
    "Virtues of Fajr in Sahih Hadith",
    "Rules of Qasr prayer while traveling"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiChatScreen(
    onNavigateToQuran: () -> Unit = {},
    onNavigateToHadith: () -> Unit = {},
    onNavigateToQuranDetail: ((surah: Int, ayah: Int) -> Unit)? = null,
    onNavigateToHadithDetail: ((book: String, number: Int, sectionHint: String?) -> Unit)? = null,
    viewModel: AiScholarViewModel? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier
) {
    val vm: AiScholarViewModel = viewModel ?: koinViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val activeSessionId by vm.activeSessionId.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val imeVisible = WindowInsets.isImeVisible
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showChats by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ScholarChatSession?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var showDisclaimer by remember { mutableStateOf(false) }
    var pendingPrompt by remember { mutableStateOf<String?>(null) }
    var pendingNewChat by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(vm) {
        vm.navigationEvents.collect { event ->
            when (event) {
                is AiScholarNavigationEvent.NavigateToQuran -> {
                    if (onNavigateToQuranDetail != null) {
                        onNavigateToQuranDetail(event.surah, event.ayah)
                    } else {
                        onNavigateToQuran()
                    }
                }
                is AiScholarNavigationEvent.NavigateToHadith -> {
                    if (onNavigateToHadithDetail != null) {
                        onNavigateToHadithDetail(
                            event.bookKey,
                            event.hadithNumber,
                            event.sectionHint
                        )
                    } else {
                        onNavigateToHadith()
                    }
                }
                is AiScholarNavigationEvent.CitationUnresolved -> {
                    // Never leave a citation tap feeling broken: tell the user where to look.
                    Toast.makeText(
                        context,
                        "No direct link for \"${event.sourceTitle}\". Search the Quran or Hadith tab.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Only follow the stream when a new turn is added or the user is already parked at the
    // bottom; never animate on every streamed character (that thrashes the list).
    LaunchedEffect(
        state.messages.size,
        state.messages.lastOrNull()?.content?.length,
        imeVisible,
        activeSessionId
    ) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        val nearBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ==
            state.messages.lastIndex || !listState.canScrollForward
        val isNewMessage = state.messages.size > (listState.firstVisibleItemIndex +
            listState.layoutInfo.visibleItemsInfo.size)
        if (nearBottom || isNewMessage) {
            listState.scrollToItem(state.messages.lastIndex)
        }
    }

    fun requestNewChat() {
        pendingPrompt = null
        pendingNewChat = true
        showDisclaimer = true
    }

    fun requestPrompt(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || state.isStreaming) return
        // Sending ends input: drop focus so the IME closes and the reply is not hidden.
        focusManager.clearFocus()
        keyboardController?.hide()
        if (state.disclaimerAcceptedForCurrentChat) {
            vm.onIntent(AiScholarUiIntent.SendQuery(trimmed))
        } else {
            pendingPrompt = trimmed
            pendingNewChat = false
            showDisclaimer = true
        }
    }

    fun cancelDisclaimer() {
        showDisclaimer = false
        pendingPrompt = null
        pendingNewChat = false
    }

    fun acceptDisclaimer() {
        if (pendingNewChat) {
            vm.onIntent(AiScholarUiIntent.NewChat)
            vm.onIntent(AiScholarUiIntent.AcceptDisclaimer)
        } else {
            vm.onIntent(AiScholarUiIntent.AcceptDisclaimer)
            pendingPrompt?.let { vm.onIntent(AiScholarUiIntent.SendQuery(it)) }
        }
        showDisclaimer = false
        pendingPrompt = null
        pendingNewChat = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Islamic AI Scholar",
                modifier = Modifier.weight(1f),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = { showChats = true }) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Chats",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { requestNewChat() }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New chat",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (state.messages.isEmpty()) {
            EmptyScholarState(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onPromptClick = ::requestPrompt
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(
                    items = state.messages,
                    key = { it.id },
                    contentType = { if (it.role == "user") "user" else "assistant" }
                ) { msg ->
                    val isUser = msg.role == "user"
                    val isStreamingThis = msg.id == state.streamingMessageId
                    // Only the bubble being streamed subscribes to the draft, so tokens
                    // never recompose the rest of the conversation.
                    val draftState = if (isStreamingThis) {
                        val draft by remember(msg.id) {
                            vm.draftTextFor(msg.id)
                        }.collectAsStateWithLifecycle(initialValue = null)
                        draft
                    } else {
                        null
                    }
                    val streamedText = draftState?.text.orEmpty()
                    val displayContent = if (isStreamingThis) streamedText else msg.content
                    val isPendingStream =
                        !isUser && displayContent.isBlank() && state.isStreaming
                    if (isUser) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth(0.82f)
                            ) {
                                Text(
                                    text = msg.content,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 760.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isPendingStream) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = if (state.stillVerifying) {
                                            "Still verifying sources…"
                                        } else {
                                            "Consulting authentic Islamic sources…"
                                        },
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                ClickableCitationText(
                                    text = displayContent,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp
                                    ),
                                    onQuranClick = { surah, ayah ->
                                        if (onNavigateToQuranDetail != null) {
                                            onNavigateToQuranDetail(surah, ayah)
                                        } else {
                                            onNavigateToQuran()
                                        }
                                    },
                                    onHadithClick = { bookKey, number, sectionHint ->
                                        if (onNavigateToHadithDetail != null) {
                                            onNavigateToHadithDetail(bookKey, number, sectionHint)
                                        } else {
                                            onNavigateToHadith()
                                        }
                                    }
                                )

                                if (hasMadhhabBreakdown(msg)) {
                                    MadhhabBreakdown(
                                        message = msg,
                                        activeTab = state.activeMadhhabTab,
                                        onSelectTab = {
                                            vm.onIntent(AiScholarUiIntent.SelectMadhhabTab(it))
                                        }
                                    )
                                }

                                if (isStreamingThis && state.stillVerifying) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "Still verifying sources…",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                msg.sourcesUnverified?.takeIf { it.isNotBlank() }?.let { reason ->
                                    UnverifiedSourcesNotice(reason)
                                }

                                val citations = buildList {
                                    msg.sourceTitle?.takeIf { it.isNotBlank() }?.let {
                                        add(it to msg.sourceDeepLink)
                                    }
                                    msg.citations.forEach { citation ->
                                        if (citation.title.isNotBlank()) {
                                            add(citation.title to (citation.deepLink ?: msg.sourceDeepLink))
                                        }
                                    }
                                }.distinctBy { it.first }
                                if (citations.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        citations.forEach { (title, deepLink) ->
                                            CitationChip(
                                                sourceTitle = title,
                                                onClick = {
                                                    vm.onIntent(
                                                        AiScholarUiIntent.ClickCitation(title, deepLink)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }

                                AiMessageDisclaimer()
                            }
                        }
                    }
                }
            }
        }

        state.error?.let { err ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = err,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { vm.onIntent(AiScholarUiIntent.RetryLastQuery) },
                        enabled = !state.isStreaming && state.canRetry
                    ) {
                        Text(if (state.canRetry) "Retry" else "Not available", fontSize = 11.sp)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.inputText,
                onValueChange = { vm.onIntent(AiScholarUiIntent.InputTextChanged(it)) },
                placeholder = { Text("Ask any Islamic question...", fontSize = 12.sp) },
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f),
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    // requestPrompt itself refuses while streaming; guard here as well so the
                    // send action never re-submits mid-stream.
                    onSend = { if (!state.isStreaming) requestPrompt(state.inputText) }
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (state.isStreaming) {
                // Visible, thumb-reachable escape hatch: stop keeps the partial answer.
                FilledIconButton(
                    onClick = { vm.onIntent(AiScholarUiIntent.StopStreaming) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop generating",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                FilledIconButton(
                    onClick = { requestPrompt(state.inputText) },
                    enabled = state.inputText.isNotBlank(),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

    if (showChats) {
        ModalBottomSheet(
            onDismissRequest = { showChats = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saved Chats",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { confirmClearAll = true }) {
                        Text("Clear All", fontSize = 11.sp)
                    }
                }
                OutlinedTextField(
                    value = state.chatSearchQuery,
                    onValueChange = { vm.onIntent(AiScholarUiIntent.ChatSearchChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search chats", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                HorizontalDivider()
                val visibleSessions = ScholarHistoryStore.search(sessions, state.chatSearchQuery)
                    .sortedWith(
                        compareByDescending<ScholarChatSession> { it.pinned }
                            .thenByDescending { it.updatedAtMs }
                    )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    if (visibleSessions.isEmpty()) {
                        item(key = "no-chat-match") {
                            Text(
                                text = "No saved chat matches \"${state.chatSearchQuery}\".",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(visibleSessions, key = { it.id }) { session ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.onIntent(AiScholarUiIntent.SwitchChat(session.id))
                                    showChats = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.title.ifBlank { "New chat" },
                                    fontWeight = if (session.id == activeSessionId) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (session.id == activeSessionId) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1
                                )
                                Text(
                                    text = formatChatTimestamp(session.updatedAtMs),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { vm.onIntent(AiScholarUiIntent.TogglePinChat(session.id)) }) {
                                Icon(
                                    imageVector = if (session.pinned) {
                                        Icons.Default.Star
                                    } else {
                                        Icons.Outlined.StarBorder
                                    },
                                    contentDescription = if (session.pinned) {
                                        "Unpin chat"
                                    } else {
                                        "Pin chat"
                                    },
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { pendingDelete = session }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete chat",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = ::cancelDisclaimer,
            icon = {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            },
            title = { Text("Before You Begin") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Islamic AI Scholar is an educational research companion grounded in the Quran, Kutub al-Tis'ah, and the four classical legal schools. It is an educational aid, not a fatwa service.",
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = "Answers are not a fatwa. For binding personal rulings or sensitive matters, always consult a qualified local Mufti or scholar.",
                            modifier = Modifier.padding(10.dp),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = ::acceptDisclaimer) {
                    Text("I Understand · Start Chat")
                }
            },
            dismissButton = {
                TextButton(onClick = ::cancelDisclaimer) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingDelete?.let { chat ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete chat?") },
            text = { Text("“${chat.title.ifBlank { "New chat" }}” will be removed permanently.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.onIntent(AiScholarUiIntent.DeleteChat(chat.id))
                        pendingDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear all chats?") },
            text = { Text("Every saved AI Scholar conversation will be deleted from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.onIntent(AiScholarUiIntent.ClearAllChats)
                        confirmClearAll = false
                        showChats = false
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EmptyScholarState(
    modifier: Modifier = Modifier,
    onPromptClick: (String) -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                // With the keyboard open the greeting and starter prompts no longer fit;
                // scrolling keeps every prompt reachable instead of clipping them.
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Text(
                text = "Assalamu Alaikum",
                fontFamily = FontFamily.Serif,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Ask anything about Quran, Hadith, or Fiqh",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            STARTER_PROMPTS.forEach { prompt ->
                Surface(
                    onClick = { onPromptClick(prompt) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = prompt,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun AiMessageDisclaimer() {
    Row(
        modifier = Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = PER_MESSAGE_DISCLAIMER,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        )
    }
}

private fun hasMadhhabBreakdown(message: ChatMessageModel): Boolean =
    !message.hanafiStance.isNullOrBlank() ||
        !message.malikiStance.isNullOrBlank() ||
        !message.shafiiStance.isNullOrBlank() ||
        !message.hanbaliStance.isNullOrBlank()

@Composable
private fun MadhhabBreakdown(
    message: ChatMessageModel,
    activeTab: String,
    onSelectTab: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Four Classical Madhahib Positions",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary
            )
            // A backend answer may cover only some schools: show those tabs, never empty ones.
            val availableTabs = listOf(
                Triple("hanafi", "Hanafi", message.hanafiStance),
                Triple("maliki", "Maliki", message.malikiStance),
                Triple("shafii", "Shafi'i", message.shafiiStance),
                Triple("hanbali", "Hanbali", message.hanbaliStance)
            ).filter { !it.third.isNullOrBlank() }
            if (availableTabs.isEmpty()) {
                Text(
                    text = "No school-specific note was returned for this question.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val currentIdx = availableTabs.indexOfFirst { it.first == activeTab }
                    .takeIf { it >= 0 } ?: 0
                SegmentedPillBar(
                    options = availableTabs.map { it.second },
                    selectedIndex = currentIdx,
                    onSelect = { idx -> onSelectTab(availableTabs[idx].first) }
                )
                Text(
                    text = availableTabs[currentIdx].third.orEmpty(),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Shown when the answer could not be tied to a verifiable Quran/hadith source. */
@Composable
private fun UnverifiedSourcesNotice(reason: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = reason,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

private fun formatChatTimestamp(timestampMs: Long): String =
    SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(timestampMs))
