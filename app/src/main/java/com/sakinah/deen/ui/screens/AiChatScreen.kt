package com.sakinah.deen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@Composable
fun AiChatScreen(
    onNavigateToQuran: () -> Unit = {},
    onNavigateToHadith: () -> Unit = {},
    onNavigateToQuranDetail: ((surah: Int, ayah: Int) -> Unit)? = null,
    onNavigateToHadithDetail: ((book: String, number: Int) -> Unit)? = null,
    viewModel: AiScholarViewModel? = null,
    modifier: Modifier = Modifier
) {
    val vm: AiScholarViewModel = viewModel ?: koinViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

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
                        onNavigateToHadithDetail(event.bookKey, event.hadithNumber)
                    } else {
                        onNavigateToHadith()
                    }
                }
            }
        }
    }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content?.length) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
                Text(
                    text = state.guardrailNotice,
                    fontSize = 11.sp,
                    color = Color(0xFF78350F),
                    lineHeight = 15.sp,
                    modifier = Modifier.weight(1f)
                )
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
                        modifier = Modifier.fillMaxWidth(0.90f)
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
                                    onHadithClick = { bookKey, number ->
                                        if (onNavigateToHadithDetail != null) {
                                            onNavigateToHadithDetail(bookKey, number)
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
}
