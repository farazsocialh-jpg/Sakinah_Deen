package com.sakinah.deen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakinah.deen.data.model.ChatMessageModel
import com.sakinah.deen.ui.theme.EmeraldDeep
import com.sakinah.deen.ui.theme.MintGreen

@Composable
fun AiChatScreen(
    onNavigateToQuran: () -> Unit = {},
    onNavigateToHadith: () -> Unit = {},
    onNavigateToQuranDetail: ((surah: Int, ayah: Int) -> Unit)? = null,
    onNavigateToHadithDetail: ((book: String, number: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var messages by remember {
        mutableStateOf(
            listOf(
                ChatMessageModel(
                    id = "1",
                    role = "assistant",
                    content = "As-salāmu ‘alaykum wa raḥmatullāhi wa barakātuh. I am your Sunni Deen research companion, grounded in the Holy Quran, Kutub al-Tis‘ah, and the four classical legal schools.",
                    timestamp = "Just now",
                    sourceTitle = "Surah An-Nisa 4:59"
                ),
                ChatMessageModel(
                    id = "2",
                    role = "user",
                    content = "How do I perform Sujud as-Sahw (Prostration of Forgetfulness)?",
                    timestamp = "Just now"
                ),
                ChatMessageModel(
                    id = "3",
                    role = "assistant",
                    content = "Sujud as-Sahw (سجود السهو) consists of two prostrations performed to rectify unintentional omissions or additions in prayer, followed by Salam.\n\nGrounding Proof: In Sahih Muslim #571, the Prophet (ﷺ) instructed to build upon certainty and make two prostrations.",
                    timestamp = "Just now",
                    sourceTitle = "Sahih Muslim #571",
                    hanafiStance = "Hanafi: Performed after the first Salam to the right. Make 2 Sujuds, repeat Tashahhud, then conclude with Salam.",
                    malikiStance = "Maliki: Before Salam for omission (Qabli); after Salam for addition (Ba'di).",
                    shafiiStance = "Shafi'i: Always performed immediately before the first Salam (Qabli).",
                    hanbaliStance = "Hanbali: Before Salam for omission; after Salam for addition or doubt resolving on probability."
                )
            )
        )
    }

    var inputText by remember { mutableStateOf("") }
    var activeMadhhabTab by remember { mutableStateOf("hanafi") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Orthodoxy & Fatwa Guardrail Disclaimer
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFFEF3C7),
            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Guardrail",
                    tint = EmeraldDeep,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Educational Reference Notice: Sourced from verified Sunni texts (Quran, Hadith, 4 Madhhabs). For binding personal fatwas, consult a local Mufti.",
                    fontSize = 11.sp,
                    color = Color(0xFF78350F),
                    lineHeight = 15.sp
                )
            }
        }

        // 2. Streaming Chat Message Feed
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { msg ->
                val isUser = msg.role == "user"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUser) EmeraldDeep else MaterialTheme.colorScheme.surface
                        ),
                        border = if (!isUser) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                        modifier = Modifier.fillMaxWidth(0.88f)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = msg.content,
                                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )

                            // Madhhab Divergence / Consensus Box
                            if (msg.hanafiStance != null) {
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MintGreen.copy(alpha = 0.08f)),
                                    border = BorderStroke(1.dp, MintGreen.copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Four Sunni Madhahib Positions:",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = EmeraldDeep
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf("hanafi" to "Hanafi", "maliki" to "Maliki", "shafii" to "Shafi'i", "hanbali" to "Hanbali").forEach { (key, label) ->
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (activeMadhhabTab == key) EmeraldDeep else Color.Transparent,
                                                    modifier = Modifier.clickable { activeMadhhabTab = key }
                                                ) {
                                                    Text(
                                                        text = label,
                                                        color = if (activeMadhhabTab == key) Color.White else EmeraldDeep,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = when (activeMadhhabTab) {
                                                "maliki" -> msg.malikiStance ?: ""
                                                "shafii" -> msg.shafiiStance ?: ""
                                                "hanbali" -> msg.hanbaliStance ?: ""
                                                else -> msg.hanafiStance ?: ""
                                            },
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            // Clickable Source Proof Chips (Opens Page 4 or 5)
                            if (msg.sourceTitle != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = EmeraldDeep.copy(alpha = 0.08f),
                                    modifier = Modifier.clickable {
                                        val title = msg.sourceTitle ?: ""
                                        if (title.contains("Quran") || title.contains("Surah")) {
                                            val surahNum = Regex("\\d+").find(title)?.value?.toIntOrNull() ?: 1
                                            if (onNavigateToQuranDetail != null) {
                                                onNavigateToQuranDetail(surahNum, 1)
                                            } else {
                                                onNavigateToQuran()
                                            }
                                        } else {
                                            val hadithNum = Regex("\\d+").find(title)?.value?.toIntOrNull() ?: 1
                                            val bookKey = when {
                                                title.contains("Muslim", ignoreCase = true) -> "muslim"
                                                title.contains("Dawud", ignoreCase = true) -> "abudawud"
                                                title.contains("Tirmidhi", ignoreCase = true) -> "tirmidhi"
                                                else -> "bukhari"
                                            }
                                            if (onNavigateToHadithDetail != null) {
                                                onNavigateToHadithDetail(bookKey, hadithNum)
                                            } else {
                                                onNavigateToHadith()
                                            }
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Outlined.Book, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = msg.sourceTitle!!,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldDeep
                                        )
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
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
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask any Islamic question...", fontSize = 12.sp) },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        messages = messages + ChatMessageModel(
                            id = (messages.size + 1).toString(),
                            role = "user",
                            content = inputText,
                            timestamp = "Just now"
                        )
                        inputText = ""
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = MintGreen)
            }
        }
    }
}
