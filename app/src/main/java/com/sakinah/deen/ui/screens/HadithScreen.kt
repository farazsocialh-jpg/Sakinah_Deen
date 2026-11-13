package com.sakinah.deen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.data.model.HadithModel
import com.sakinah.deen.ui.components.ArabicVerseBlock
import com.sakinah.deen.ui.components.SakinahCard
import com.sakinah.deen.ui.components.StatusBadge
import com.sakinah.deen.ui.theme.DaifAmber
import com.sakinah.deen.ui.theme.HasanTeal
import com.sakinah.deen.ui.theme.MintGreen
import com.sakinah.deen.ui.theme.SahihGreen
import com.sakinah.deen.ui.viewmodel.HadithUiIntent
import com.sakinah.deen.ui.viewmodel.HadithViewModel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun HadithScreen(
    initialBookKey: String = "all",
    initialHadithNumber: Int = 0,
    initialSearchQuery: String = "",
    deepLinkNonce: Int = 0,
    viewModel: HadithViewModel? = null,
    modifier: Modifier = Modifier
) {
    val vm: HadithViewModel = viewModel ?: koinViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var highlightedHadithId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialBookKey, initialHadithNumber, deepLinkNonce) {
        if (initialHadithNumber > 0 && initialBookKey != "all") {
            vm.onIntent(HadithUiIntent.NavigateToDeepLink("hadith/$initialBookKey/$initialHadithNumber"))
        } else if (initialBookKey != "all" || deepLinkNonce > 0) {
            vm.onIntent(HadithUiIntent.SelectBook(initialBookKey))
        }
    }
    LaunchedEffect(initialSearchQuery, deepLinkNonce) {
        if (initialSearchQuery.isNotBlank()) {
            vm.onIntent(HadithUiIntent.SearchQueryChanged(initialSearchQuery))
        }
    }
    LaunchedEffect(state.targetDeepLinkId, state.filteredHadiths) {
        val targetId = state.targetDeepLinkId ?: return@LaunchedEffect
        val idx = state.filteredHadiths.indexOfFirst { it.id == targetId }
        if (idx >= 0) {
            listState.animateScrollToItem(idx)
            highlightedHadithId = targetId
            vm.onIntent(HadithUiIntent.ClearDeepLinkTarget)
            delay(2000)
            if (highlightedHadithId == targetId) highlightedHadithId = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Kutub al-Tis‘ah Encyclopedia",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 21.sp,
            color = MaterialTheme.colorScheme.primary
        )

        // 1. Collection Carousel
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                val allSelected = state.selectedBookKey == "all"
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.clickable { vm.onIntent(HadithUiIntent.SelectBook("all")) }
                ) {
                    Text(
                        text = "All Books",
                        color = if (allSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
            items(state.books, key = { it.key }) { book ->
                val isSelected = state.selectedBookKey == book.key
                Surface(
                    modifier = Modifier.clickable { vm.onIntent(HadithUiIntent.SelectBook(book.key)) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = book.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = book.totalCount,
                            fontSize = 10.sp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }

        // 2. Language & Authenticity Grade Filters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.availableLanguages.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.availableLanguages) { language ->
                        val selected = state.selectedLanguageCode.equals(language, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { vm.onIntent(HadithUiIntent.SelectLanguage(language)) }
                        ) {
                            Text(
                                text = language.uppercase(),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("all" to "All", "sahih" to "Sahih", "hasan" to "Hasan")) { (key, label) ->
                    val selected = state.filterGrade.equals(key, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MintGreen.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MintGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.clickable { vm.onIntent(HadithUiIntent.FilterGradeChanged(key)) }
                    ) {
                        Text(
                            text = label,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 3. Chapter / Section Carousel (only when book has sections)
        if (state.sections.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    val selected = state.selectedSectionNumber == 0
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.clickable { vm.onIntent(HadithUiIntent.SelectSection(0)) }
                    ) {
                        Text(
                            text = "All sections",
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
                items(state.sections, key = { it.first }) { section ->
                    val selected = state.selectedSectionNumber == section.first
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.clickable { vm.onIntent(HadithUiIntent.SelectSection(section.first)) }
                    ) {
                        Text(
                            text = section.second,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        state.downloadProgress?.let { progress ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Downloading… ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            }
        }

        state.errorMessage?.let { message ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { vm.onIntent(HadithUiIntent.RetrySync) }) {
                        Text("Retry", fontSize = 11.sp)
                    }
                }
            }
        }

        // 4. Full-Text Arabic (diacritic-insensitive) & Translation Search
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { vm.onIntent(HadithUiIntent.SearchQueryChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search Hadith by Arabic, meaning, or number…", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (state.searchQuery.isNotBlank()) {
                    IconButton(onClick = { vm.onIntent(HadithUiIntent.ClearSearch) }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        // 5. Results Feed
        if (state.filteredHadiths.isEmpty()) {
            if (state.isLoading) {
                Row(
                    modifier = Modifier.padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Loading hadith collection…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val fallbackLang = state.availableLanguages.firstOrNull {
                    !it.equals(state.selectedLanguageCode, ignoreCase = true)
                }
                SakinahCard(contentPadding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (state.isFromDatabase) {
                                "No hadiths matched this filter in ${state.selectedLanguageCode.uppercase()}."
                            } else {
                                "Preparing the offline hadith library…"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (fallbackLang != null && state.searchQuery.isBlank()) {
                            TextButton(onClick = { vm.onIntent(HadithUiIntent.SelectLanguage(fallbackLang)) }) {
                                Text("Switch to ${fallbackLang.uppercase()} edition")
                            }
                        }
                    }
                }
            }
            return@Column
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.filteredHadiths, key = { it.id }) { hadith ->
                HadithCard(
                    hadith = hadith,
                    isTargetHighlighted = highlightedHadithId == hadith.id,
                    onToggleBookmark = { vm.onIntent(HadithUiIntent.ToggleBookmark(hadith.id)) }
                )
            }
        }
    }
}

@Composable
private fun HadithCard(
    hadith: HadithModel,
    isTargetHighlighted: Boolean = false,
    onToggleBookmark: () -> Unit
) {
    val gradeAccent = when {
        hadith.grade.contains("SAHIH", ignoreCase = true) -> SahihGreen
        hadith.grade.contains("HASAN", ignoreCase = true) -> HasanTeal
        hadith.grade.contains("DAIF", ignoreCase = true) || hadith.grade.contains("WEAK", ignoreCase = true) -> DaifAmber
        else -> MaterialTheme.colorScheme.primary
    }

    SakinahCard(
        modifier = if (isTargetHighlighted) {
            Modifier.border(
                width = 2.5.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp)
            )
        } else {
            Modifier
        },
        containerColor = if (isTargetHighlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentPadding = 16.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (hadith.chapterTitle.isNotBlank()) {
                        Text(
                            text = hadith.chapterTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                StatusBadge(
                    text = listOf(hadith.grade, hadith.gradingScholar)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    accentColor = gradeAccent
                )
            }

            if (hadith.sanad.isNotBlank()) {
                Text(
                    text = "Sanad: ${hadith.sanad}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Serif,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (hadith.arabicText.isNotBlank()) {
                ArabicVerseBlock(
                    arabicText = hadith.arabicText,
                    fontSizeSp = 21,
                    color = MaterialTheme.colorScheme.onSurface,
                    isQuran = false
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
                        contentDescription = if (hadith.bookmarked) "Remove bookmark" else "Save bookmark",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
