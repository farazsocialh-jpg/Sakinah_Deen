package com.sakinah.deen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakinah.deen.data.model.HadithModel
import com.sakinah.deen.ui.theme.EmeraldDeep
import com.sakinah.deen.ui.theme.MintGreen
import com.sakinah.deen.ui.theme.SahihGreen
import com.sakinah.deen.ui.viewmodel.HadithUiIntent
import com.sakinah.deen.ui.viewmodel.HadithViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun HadithScreen(
    initialBookKey: String = "all",
    initialSearchQuery: String = "",
    modifier: Modifier = Modifier
) {
    val viewModel: HadithViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(initialBookKey) {
        viewModel.onIntent(HadithUiIntent.SelectBook(initialBookKey))
    }
    LaunchedEffect(initialSearchQuery) {
        if (initialSearchQuery.isNotBlank()) {
            viewModel.onIntent(HadithUiIntent.SearchQueryChanged(initialSearchQuery))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Kutub al-Tis‘ah Encyclopedia",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = EmeraldDeep
        )

        // 1. Collection carousel from the encrypted database
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (state.selectedBookKey == "all") EmeraldDeep else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (state.selectedBookKey == "all") EmeraldDeep else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.clickable { viewModel.onIntent(HadithUiIntent.SelectBook("all")) }
                ) {
                    Text(
                        text = "All Books",
                        color = if (state.selectedBookKey == "all") Color.White else EmeraldDeep,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
            items(state.books) { book ->
                val isSelected = state.selectedBookKey == book.key
                Card(
                    modifier = Modifier.clickable { viewModel.onIntent(HadithUiIntent.SelectBook(book.key)) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MintGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, if (isSelected) MintGreen else MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(text = book.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EmeraldDeep)
                        Text(
                            text = book.totalCount,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // 2. Full-text search
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.onIntent(HadithUiIntent.SearchQueryChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search Hadith by Arabic or English meaning...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            shape = RoundedCornerShape(18.dp),
            singleLine = true
        )

        // 3. Results
        if (state.filteredHadiths.isEmpty()) {
            if (state.isLoading) {
                Row(
                    modifier = Modifier.padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Downloading hadith collection…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            } else {
                Text(
                    text = if (state.isFromDatabase) {
                        "No hadith match this filter in the offline library."
                    } else {
                        "Preparing the offline hadith library…"
                    },
                    modifier = Modifier.padding(vertical = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.filteredHadiths) { hadith ->
                HadithCard(
                    hadith = hadith,
                    onToggleBookmark = { viewModel.onIntent(HadithUiIntent.ToggleBookmark(hadith.id)) }
                )
            }
        }
    }
}

@Composable
private fun HadithCard(
    hadith: HadithModel,
    onToggleBookmark: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${hadith.bookTitle} #${hadith.hadithNumber}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = EmeraldDeep
                    )
                    if (hadith.chapterTitle.isNotBlank()) {
                        Text(
                            text = hadith.chapterTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SahihGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = listOf(hadith.grade, hadith.gradingScholar)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SahihGreen
                    )
                }
            }

            if (hadith.sanad.isNotBlank()) {
                Text(
                    text = "Sanad: ${hadith.sanad}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Serif,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            if (hadith.arabicText.isNotBlank()) {
                Text(
                    text = hadith.arabicText,
                    fontFamily = FontFamily.Serif,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldDeep,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (hadith.matnTranslation.isNotBlank()) {
                Text(
                    text = "\"${hadith.matnTranslation}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = if (hadith.bookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (hadith.bookmarked) "Remove bookmark" else "Save",
                        tint = EmeraldDeep
                    )
                }
            }
        }
    }
}
