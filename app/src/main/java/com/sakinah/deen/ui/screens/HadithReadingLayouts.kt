package com.sakinah.deen.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakinah.deen.data.model.HadithModel
import com.sakinah.deen.data.prefs.HadithReadingLayout
import com.sakinah.deen.ui.components.ArabicVerseBlock
import com.sakinah.deen.ui.components.SegmentedPillBar
import com.sakinah.deen.ui.viewmodel.HadithBookInfo

@Composable
private fun hadithGradeAccent(hadith: HadithModel): Color = when {
    hadith.grade.contains("SAHIH", ignoreCase = true) -> MaterialTheme.colorScheme.primary
    hadith.grade.contains("HASAN", ignoreCase = true) -> MaterialTheme.colorScheme.secondary
    hadith.grade.contains("DAIF", ignoreCase = true) ||
        hadith.grade.contains("WEAK", ignoreCase = true) -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun GradePill(hadith: HadithModel) {
    val accent = hadithGradeAccent(hadith)
    Surface(
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f))
    ) {
        Text(
            text = hadith.grade.ifBlank { "Grade" },
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1
        )
    }
}

@Composable
private fun HadithBookBadge(hadith: HadithModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${hadith.bookTitle} #${hadith.hadithNumber}",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        GradePill(hadith)
    }
}

@Composable
private fun HadithBookmarkButton(
    hadith: HadithModel,
    onToggleBookmark: (HadithModel) -> Unit
) {
    IconButton(
        onClick = { onToggleBookmark(hadith) },
        modifier = Modifier.size(28.dp)
    ) {
        Icon(
            imageVector = if (hadith.bookmarked) Icons.Default.Bookmark
            else Icons.Outlined.BookmarkBorder,
            contentDescription = if (hadith.bookmarked) "Remove bookmark" else "Save bookmark",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun HadithShareButton(
    hadith: HadithModel,
    onShare: (HadithModel) -> Unit
) {
    IconButton(
        onClick = { onShare(hadith) },
        modifier = Modifier.size(28.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = "Share hadith",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun HadithArabic(
    hadith: HadithModel,
    fontSizeSp: Int = 20,
    modifier: Modifier = Modifier
) {
    if (hadith.arabicText.isBlank()) return
    ArabicVerseBlock(
        arabicText = hadith.arabicText,
        modifier = modifier,
        fontSizeSp = fontSizeSp,
        color = MaterialTheme.colorScheme.onSurface,
        isQuran = false
    )
}

@Composable
private fun HadithEnglish(
    hadith: HadithModel,
    fontSizeSp: Int = 12,
    modifier: Modifier = Modifier
) {
    if (hadith.matnTranslation.isBlank()) return
    Text(
        text = hadith.matnTranslation,
        modifier = modifier.fillMaxWidth(),
        fontFamily = FontFamily.Serif,
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * 1.38f).sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun NarratorText(hadith: HadithModel) {
    if (hadith.sanad.isBlank()) return
    Text(
        text = hadith.sanad,
        style = MaterialTheme.typography.bodySmall,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun HighlightModifier(
    highlighted: Boolean,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp)
): Modifier = if (highlighted) {
    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
} else {
    Modifier
}

@Composable
private fun CompactDropdownLayout(
    hadiths: List<HadithModel>,
    listState: LazyListState,
    highlightedHadithId: String?,
    onToggleBookmark: (HadithModel) -> Unit,
    onShare: (HadithModel) -> Unit
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(hadiths, key = { _, hadith -> hadith.id }) { index, hadith ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (highlightedHadithId == hadith.id) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
                        } else {
                            MaterialTheme.colorScheme.background
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HadithBookBadge(hadith)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HadithShareButton(hadith, onShare)
                        HadithBookmarkButton(hadith, onToggleBookmark)
                    }
                }
                HadithArabic(hadith)
                HadithEnglish(hadith)
            }
            if (index < hadiths.lastIndex) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            }
        }
    }
}

@Composable
private fun EnglishFirstTapLayout(
    hadiths: List<HadithModel>,
    listState: LazyListState,
    highlightedHadithId: String?,
    onToggleBookmark: (HadithModel) -> Unit,
    onShare: (HadithModel) -> Unit
) {
    var expandedArabicId by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items(hadiths, key = { it.id }) { hadith ->
            val expanded = expandedArabicId == hadith.id
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(HighlightModifier(highlightedHadithId == hadith.id)),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HadithBookBadge(hadith)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.clickable {
                                    expandedArabicId = if (expanded) null else hadith.id
                                }
                            ) {
                                Text(
                                    text = if (expanded) "Arabic ▴" else "Arabic ▾",
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            HadithShareButton(hadith, onShare)
                            HadithBookmarkButton(hadith, onToggleBookmark)
                        }
                    }
                    NarratorText(hadith)
                    HadithEnglish(hadith)
                    AnimatedVisibility(visible = expanded) {
                        HadithArabic(hadith)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookLibraryGridLayout(
    books: List<HadithBookInfo>,
    hadiths: List<HadithModel>,
    listState: LazyListState,
    selectedBookKey: String,
    selectedBookTitle: String,
    highlightedHadithId: String?,
    onSelectBook: (String) -> Unit,
    onToggleBookmark: (HadithModel) -> Unit,
    onShare: (HadithModel) -> Unit
) {
    if (selectedBookKey == "all") {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            gridItems(books, key = { it.key }) { book ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectBook(book.key) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = book.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = book.totalCount,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Offline library",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        return
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = selectedBookTitle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        itemsIndexed(hadiths, key = { _, hadith -> hadith.id }) { index, hadith ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (highlightedHadithId == hadith.id) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        } else {
                            MaterialTheme.colorScheme.background
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HadithBookBadge(hadith)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HadithShareButton(hadith, onShare)
                        HadithBookmarkButton(hadith, onToggleBookmark)
                    }
                }
                HadithArabic(hadith)
                HadithEnglish(hadith)
            }
            if (index < hadiths.lastIndex) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                )
            }
        }
    }
}

@Composable
private fun LanguageTabbedLayout(
    hadiths: List<HadithModel>,
    listState: LazyListState,
    highlightedHadithId: String?,
    onToggleBookmark: (HadithModel) -> Unit,
    onShare: (HadithModel) -> Unit
) {
    var languageTab by rememberSaveable { mutableIntStateOf(2) }
    Column(modifier = Modifier.fillMaxSize()) {
        SegmentedPillBar(
            options = listOf("English", "Arabic", "Both"),
            selectedIndex = languageTab,
            onSelect = { languageTab = it },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(hadiths, key = { it.id }) { hadith ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(HighlightModifier(highlightedHadithId == hadith.id)),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HadithBookBadge(hadith)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HadithShareButton(hadith, onShare)
                                HadithBookmarkButton(hadith, onToggleBookmark)
                            }
                        }
                        NarratorText(hadith)
                        if (languageTab == 0 || languageTab == 2) HadithEnglish(hadith)
                        if (languageTab == 1 || languageTab == 2) HadithArabic(hadith)
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleBarCardsLayout(
    hadiths: List<HadithModel>,
    listState: LazyListState,
    highlightedHadithId: String?,
    onToggleBookmark: (HadithModel) -> Unit,
    onShare: (HadithModel) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(hadiths, key = { it.id }) { hadith ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(HighlightModifier(highlightedHadithId == hadith.id)),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f))
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HadithBookBadge(hadith)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HadithShareButton(hadith, onShare)
                            HadithBookmarkButton(hadith, onToggleBookmark)
                        }
                    }
                    HadithArabic(hadith, fontSizeSp = 17)
                    HadithEnglish(hadith)
                }
            }
        }
    }
}

@Composable
private fun AlternatingRowsLayout(
    hadiths: List<HadithModel>,
    listState: LazyListState,
    highlightedHadithId: String?,
    onToggleBookmark: (HadithModel) -> Unit,
    onShare: (HadithModel) -> Unit
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(hadiths, key = { _, hadith -> hadith.id }) { index, hadith ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .background(
                        when {
                            highlightedHadithId == hadith.id ->
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
                            index % 2 == 0 -> MaterialTheme.colorScheme.background
                            else -> MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HadithBookBadge(hadith)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HadithShareButton(hadith, onShare)
                            HadithBookmarkButton(hadith, onToggleBookmark)
                        }
                    }
                    HadithArabic(hadith, fontSizeSp = 17)
                    HadithEnglish(hadith)
                }
            }
        }
    }
}

@Composable
private fun FeaturedAndListLayout(
    hadiths: List<HadithModel>,
    listState: LazyListState,
    highlightedHadithId: String?,
    onToggleBookmark: (HadithModel) -> Unit,
    onShare: (HadithModel) -> Unit
) {
    val initialId = highlightedHadithId ?: hadiths.firstOrNull()?.id
    var activeHadithId by rememberSaveable { mutableStateOf(initialId) }
    LaunchedEffect(initialId, hadiths) {
        if (activeHadithId == null || hadiths.none { it.id == activeHadithId }) {
            activeHadithId = hadiths.firstOrNull()?.id
        }
    }
    val active = hadiths.firstOrNull { it.id == activeHadithId } ?: hadiths.firstOrNull()
    val remaining = hadiths.filter { it.id != active?.id }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (active != null) {
            item(key = "featured-${active.id}") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HadithBookBadge(active)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HadithShareButton(active, onShare)
                                HadithBookmarkButton(active, onToggleBookmark)
                            }
                        }
                        NarratorText(active)
                        HadithArabic(active, fontSizeSp = 20)
                        HadithEnglish(active)
                    }
                }
            }
        }
        items(remaining, key = { it.id }) { hadith ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeHadithId = hadith.id },
                shape = RoundedCornerShape(10.dp),
                color = if (highlightedHadithId == hadith.id) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "#${hadith.hadithNumber} · ${hadith.bookTitle}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = hadith.matnTranslation,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ThumbBottomDockLayout(
    hadiths: List<HadithModel>,
    listState: LazyListState,
    highlightedHadithId: String?,
    onToggleBookmark: (HadithModel) -> Unit,
    onShare: (HadithModel) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)
    ) {
        itemsIndexed(hadiths, key = { _, hadith -> hadith.id }) { index, hadith ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (highlightedHadithId == hadith.id) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        } else {
                            MaterialTheme.colorScheme.background
                        }
                    )
                    .padding(vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HadithBookBadge(hadith)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HadithShareButton(hadith, onShare)
                        HadithBookmarkButton(hadith, onToggleBookmark)
                    }
                }
                HadithArabic(hadith, fontSizeSp = 18)
                HadithEnglish(hadith)
            }
            if (index < hadiths.lastIndex) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                )
            }
        }
    }
}

@Composable
private fun SanadMatnStudyLayout(
    hadiths: List<HadithModel>,
    listState: LazyListState,
    highlightedHadithId: String?,
    onToggleBookmark: (HadithModel) -> Unit,
    onShare: (HadithModel) -> Unit
) {
    var expandedSanadId by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(hadiths, key = { it.id }) { hadith ->
            val expanded = expandedSanadId == hadith.id
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(HighlightModifier(highlightedHadithId == hadith.id)),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HadithBookBadge(hadith)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HadithShareButton(hadith, onShare)
                            HadithBookmarkButton(hadith, onToggleBookmark)
                        }
                    }
                    if (hadith.sanad.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable {
                                expandedSanadId = if (expanded) null else hadith.id
                            }
                        ) {
                            Text(
                                text = if (expanded) "Chain (Sanad) ▴" else "Chain (Sanad) ▾",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AnimatedVisibility(visible = expanded) {
                            Text(
                                text = hadith.sanad,
                                fontFamily = FontFamily.Serif,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
                    ) {
                        Column(
                            modifier = Modifier.padding(9.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            HadithArabic(hadith, fontSizeSp = 18)
                            HadithEnglish(hadith)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalistZenLayout(
    hadiths: List<HadithModel>,
    listState: LazyListState,
    highlightedHadithId: String?,
    onToggleBookmark: (HadithModel) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(hadiths, key = { _, hadith -> hadith.id }) { index, hadith ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (highlightedHadithId == hadith.id) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        } else {
                            Color.Transparent
                        }
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "#${index + 1}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                    HadithBookmarkButton(hadith, onToggleBookmark)
                }
                HadithArabic(hadith, fontSizeSp = 19)
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                )
                HadithEnglish(hadith)
            }
        }
    }
}

@Composable
internal fun HadithReadingLayoutContent(
    layout: HadithReadingLayout,
    hadiths: List<HadithModel>,
    listState: LazyListState,
    books: List<HadithBookInfo>,
    selectedBookKey: String,
    selectedBookTitle: String,
    highlightedHadithId: String?,
    onSelectBook: (String) -> Unit,
    onToggleBookmark: (HadithModel) -> Unit,
    onShare: (HadithModel) -> Unit
) {
    when (layout) {
        HadithReadingLayout.COMPACT_DROPDOWN -> CompactDropdownLayout(
            hadiths, listState, highlightedHadithId, onToggleBookmark, onShare
        )
        HadithReadingLayout.ENGLISH_FIRST_TAP -> EnglishFirstTapLayout(
            hadiths, listState, highlightedHadithId, onToggleBookmark, onShare
        )
        HadithReadingLayout.BOOK_LIBRARY_GRID -> BookLibraryGridLayout(
            books = books,
            hadiths = hadiths,
            listState = listState,
            selectedBookKey = selectedBookKey,
            selectedBookTitle = selectedBookTitle,
            highlightedHadithId = highlightedHadithId,
            onSelectBook = onSelectBook,
            onToggleBookmark = onToggleBookmark,
            onShare = onShare
        )
        HadithReadingLayout.LANGUAGE_TABBED -> LanguageTabbedLayout(
            hadiths, listState, highlightedHadithId, onToggleBookmark, onShare
        )
        HadithReadingLayout.SINGLE_BAR_CARDS -> SingleBarCardsLayout(
            hadiths, listState, highlightedHadithId, onToggleBookmark, onShare
        )
        HadithReadingLayout.ALTERNATING_ROWS -> AlternatingRowsLayout(
            hadiths, listState, highlightedHadithId, onToggleBookmark, onShare
        )
        HadithReadingLayout.FEATURED_AND_LIST -> FeaturedAndListLayout(
            hadiths, listState, highlightedHadithId, onToggleBookmark, onShare
        )
        HadithReadingLayout.THUMB_BOTTOM_DOCK -> ThumbBottomDockLayout(
            hadiths, listState, highlightedHadithId, onToggleBookmark, onShare
        )
        HadithReadingLayout.SANAD_MATN_STUDY -> SanadMatnStudyLayout(
            hadiths, listState, highlightedHadithId, onToggleBookmark, onShare
        )
        HadithReadingLayout.MINIMALIST_ZEN -> MinimalistZenLayout(
            hadiths, listState, highlightedHadithId, onToggleBookmark
        )
    }
}
