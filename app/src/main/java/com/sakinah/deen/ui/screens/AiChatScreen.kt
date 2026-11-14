package com.sakinah.deen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.data.scholar.ScholarChatSession
import com.sakinah.deen.ui.components.CitationChip
import com.sakinah.deen.ui.components.ClickableCitationText
import com.sakinah.deen.ui.components.SakinahCard
import com.sakinah.deen.ui.components.SegmentedPillBar
import com.sakinah.deen.ui.theme.EmeraldDeep
import com.sakinah.deen.ui.theme.MintGreen
import com.sakinah.deen.ui.viewmodel.AiScholarNavigationEvent
import com.sakinah.deen.ui.viewmodel.AiScholarUiIntent
import com.sakinah.deen.ui.viewmodel.AiScholarViewModel
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val AI_DISCLAIMER =
    "This response is AI-generated. Please verify rulings and references through authentic Islamic sources and qualified scholars."

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
    var showChats by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ScholarChatSession?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
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
            }
        }
    }

    LaunchedEffect(
        state.messages.size,
        state.messages.lastOrNull()?.content?.length,
        imeVisible,
        activeSessionId
    ) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sunni AI Scholar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = sessions.firstOrNull { it.id == activeSessionId }?.title ?: "New chat",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            TextButton(onClick = { vm.onIntent(AiScholarUiIntent.NewChat) }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("New", fontSize = 11.sp)
            }
            TextButton(onClick = { showChats = true }) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Chats", fontSize = 11.sp)
            }
        }

        // 1. Orthodoxy & Fatwa Guardrail Disclaimer + Clear Action
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFFEF3C7),
            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Guardrail",
                    tint = Color(0xFF92400E),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.guardrailNotice,
                        fontSize = 11.sp,
                        color = Color(0xFF78350F),
                        lineHeight = 15.sp
                    )
                    Spacer(Modifier.size(3.dp))
                    Text(
                        text = AI_DISCLAIMER,
                        fontSize = 10.sp,
                        color = Color(0xFF92400E),
                        lineHeight = 14.sp
                    )
                }
                if (state.messages.isNotEmpty()) {
                    IconButton(
                        onClick = { vm.onIntent(AiScholarUiIntent.ClearConversation) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear conversation",
                            tint = Color(0xFF92400E),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 2. Streaming Chat Message Feed
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.messages, key = { it.id }) { msg ->
                val isUser = msg.role == "user"
                val isPendingStream = !isUser && msg.content.isBlank() && state.isStreaming

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUser) EmeraldDeep else MaterialTheme.colorScheme.surface
                        ),
                        border = if (!isUser) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .widthIn(max = 560.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                        text = "Consulting verified Sunni sources…",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else if (isUser) {
                                Text(
                                    text = msg.content,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            } else {
                                ClickableCitationText(
                                    text = msg.content,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp
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
                            }

                            // Madhhab Divergence / Consensus Box
                            val hasMadhhabBreakdown = !msg.hanafiStance.isNullOrBlank() ||
                                !msg.malikiStance.isNullOrBlank() ||
                                !msg.shafiiStance.isNullOrBlank() ||
                                !msg.hanbaliStance.isNullOrBlank()
                            if (hasMadhhabBreakdown) {
                                SakinahCard(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                    contentPadding = 10.dp
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Four Sunni Madhahib Positions",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        val tabs = listOf("hanafi" to "Hanafi", "maliki" to "Maliki", "shafii" to "Shafi'i", "hanbali" to "Hanbali")
                                        val currentIdx = tabs.indexOfFirst { it.first == state.activeMadhhabTab }.coerceAtLeast(0)
                                        SegmentedPillBar(
                                            options = tabs.map { it.second },
                                            selectedIndex = currentIdx,
                                            onSelect = { idx ->
                                                vm.onIntent(AiScholarUiIntent.SelectMadhhabTab(tabs[idx].first))
                                            }
                                        )

                                        val stanceText = when (state.activeMadhhabTab) {
                                            "maliki" -> msg.malikiStance
                                            "shafii" -> msg.shafiiStance
                                            "hanbali" -> msg.hanbaliStance
                                            else -> msg.hanafiStance
                                        }.orEmpty().ifBlank { "No school-specific note recorded for this position." }

                                        Text(
                                            text = stanceText,
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            // Clickable Source Proof Chip
                            msg.sourceTitle?.takeIf { it.isNotBlank() }?.let { title ->
                                CitationChip(
                                    sourceTitle = title,
                                    onClick = {
                                        vm.onIntent(AiScholarUiIntent.ClickCitation(title, msg.sourceDeepLink))
                                    }
                                )
                            }

                            if (!isUser && !isPendingStream) {
                                Text(
                                    text = AI_DISCLAIMER,
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp,
                                    color = if (isUser) {
                                        Color.White.copy(alpha = 0.78f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Inline Network / Timeout Error Card with Retry
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
                        enabled = !state.isStreaming
                    ) {
                        Text("Retry", fontSize = 11.sp)
                    }
                }
            }
        }

        // 3. Input Dock
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.inputText,
                onValueChange = { vm.onIntent(AiScholarUiIntent.InputTextChanged(it)) },
                placeholder = { Text("Ask any Islamic question...", fontSize = 12.sp) },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { vm.onIntent(AiScholarUiIntent.SendQuery(state.inputText)) },
                enabled = state.inputText.isNotBlank() && !state.isStreaming,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
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
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    items(sessions.sortedByDescending { it.updatedAtMs }, key = { it.id }) { session ->
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

private fun formatChatTimestamp(timestampMs: Long): String =
    SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(timestampMs))
