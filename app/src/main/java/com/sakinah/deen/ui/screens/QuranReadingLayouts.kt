package com.sakinah.deen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakinah.deen.data.model.AyahModel
import com.sakinah.deen.data.model.SurahModel
import com.sakinah.deen.R
import com.sakinah.deen.data.prefs.QuranReadingLayout
import com.sakinah.deen.ui.components.ArabicVerseBlock
import com.sakinah.deen.util.ArabicNormalizer

internal data class QuranAyahUiState(
    val selectedAyahNumber: Int,
    val highlightedAyahNumber: Int,
    val hifzOverlayActive: Boolean,
    val hifzMistakes: Map<Pair<Int, Int>, String>,
    val translationIsRtl: Boolean,
    val arabicFontSizeSp: Int,
    val focusMode: Boolean,
    val bookmarkedAyahNumbers: Set<Int> = emptySet()
)

internal fun cleanQuranArabic(raw: String): String =
    raw.replace("{", "").replace("}", "").trim()

private fun verseWithMarker(ayah: AyahModel): String {
    val marker = ArabicNormalizer.toArabicIndicNumerals(ayah.numberInSurah)
    return "${cleanQuranArabic(ayah.arabicText)} ﴿$marker﴾"
}

private fun hifzTagFor(surah: SurahModel, ayah: AyahModel, state: QuranAyahUiState): String? =
    state.hifzMistakes[surah.number to ayah.numberInSurah]

@Composable
private fun AyahNumberBadge(number: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$number",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

/** Per-ayah bookmark toggle; shown on every verse row in the study layouts. */
@Composable
private fun BookmarkToggle(
    bookmarked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = stringResource(
                if (bookmarked) R.string.quran_bookmark_remove else R.string.quran_bookmark_add
            ),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TafseerLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null
) {
    Text(
        text = text ?: stringResource(R.string.quran_options_tafseer),
        modifier = modifier.clickable(onClick = onClick),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun TranslationText(
    ayah: AyahModel,
    state: QuranAyahUiState,
    fontSize: Int = 13
) {
    if (ayah.translation.isBlank()) return
    Text(
        text = ayah.translation,
        modifier = Modifier.fillMaxWidth(),
        fontFamily = FontFamily.Serif,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.35f).sp,
        textAlign = if (state.translationIsRtl) TextAlign.End else TextAlign.Start,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun HifzTagRow(
    surah: SurahModel,
    ayah: AyahModel,
    state: QuranAyahUiState,
    onTagMistake: (AyahModel, String) -> Unit
) {
    if (!state.hifzOverlayActive || state.focusMode) return
    val activeTag = hifzTagFor(surah, ayah, state)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("Tajweed", "Mutashabihat", "Memory Slip").forEach { tag ->
            val active = activeTag == tag
            Surface(
                shape = RoundedCornerShape(50),
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable {
                    onTagMistake(ayah, if (active) "" else tag)
                }
            ) {
                Text(
                    text = if (active) "✓ $tag" else "+ $tag",
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AyahArabic(
    ayah: AyahModel,
    fontSizeSp: Int,
    modifier: Modifier = Modifier
) {
    ArabicVerseBlock(
        arabicText = verseWithMarker(ayah),
        modifier = modifier,
        fontSizeSp = fontSizeSp,
        color = MaterialTheme.colorScheme.onSurface,
        isQuran = true
    )
}

/** Preset 1: a single reading column separated by hairline dividers. */
@Composable
private fun DividerHairlineLayout(
    surah: SurahModel,
    state: QuranAyahUiState,
    onAyahClick: (AyahModel) -> Unit,
    onTafseer: (AyahModel) -> Unit,
    onTagMistake: (AyahModel, String) -> Unit,
    onToggleBookmark: (AyahModel) -> Unit,
    listState: LazyListState
) {
    val ayahs = surah.ayahs
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
    ) {
        itemsIndexed(
            items = ayahs,
            key = { _, ayah -> ayahKey(surah, ayah) },
            contentType = { _, _ -> "ayah" }
        ) { index, ayah ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAyahClick(ayah) }
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AyahNumberBadge(ayah.numberInSurah)
                        TafseerLink(onClick = { onTafseer(ayah) })
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.selectedAyahNumber == ayah.numberInSurah) {
                            Text(
                                text = stringResource(R.string.quran_selected_badge),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        BookmarkToggle(
                            bookmarked = ayah.numberInSurah in state.bookmarkedAyahNumbers,
                            onClick = { onToggleBookmark(ayah) }
                        )
                    }
                }
                AyahArabic(ayah, state.arabicFontSizeSp)
                TranslationText(ayah, state)
                HifzTagRow(surah, ayah, state, onTagMistake)
            }
            if (index < ayahs.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 2.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            }
        }
    }
}

/** Preset 2: a flowing mushaf page — Arabic first, translation as a quiet sub-line. */
@Composable
private fun ContinuousMushafLayout(
    surah: SurahModel,
    state: QuranAyahUiState,
    onAyahClick: (AyahModel) -> Unit,
    onTafseer: (AyahModel) -> Unit,
    onTagMistake: (AyahModel, String) -> Unit,
    onToggleBookmark: (AyahModel) -> Unit,
    onToggleStudyMode: () -> Unit,
    listState: LazyListState
) {
    val ayahs = surah.ayahs
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "mushaf-header", contentType = "header") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "سورة ${surah.nameArabic} · ${surah.nameEnglish}",
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            itemsIndexed(
                items = ayahs,
                key = { _, ayah -> ayahKey(surah, ayah) },
                contentType = { _, _ -> "ayah" }
            ) { _, ayah ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAyahClick(ayah) }
                        .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AyahArabic(ayah, (state.arabicFontSizeSp + 4).coerceAtMost(36))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AyahNumberBadge(ayah.numberInSurah)
                            TafseerLink(onClick = { onTafseer(ayah) })
                        }
                        BookmarkToggle(
                            bookmarked = ayah.numberInSurah in state.bookmarkedAyahNumbers,
                            onClick = { onToggleBookmark(ayah) }
                        )
                    }
                    TranslationText(ayah, state, fontSize = 12)
                    HifzTagRow(surah, ayah, state, onTagMistake)
                }
            }
            item(key = "mushaf-footer", contentType = "footer") { Spacer(Modifier.height(54.dp)) }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .clickable(onClick = onToggleStudyMode),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ) {
            Text(
                text = stringResource(R.string.quran_switch_to_study),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Preset 3: translation on the left, Arabic on the right, per verse. */
@Composable
private fun SplitColumnLayout(
    surah: SurahModel,
    state: QuranAyahUiState,
    onAyahClick: (AyahModel) -> Unit,
    onTafseer: (AyahModel) -> Unit,
    onTagMistake: (AyahModel, String) -> Unit,
    onToggleBookmark: (AyahModel) -> Unit,
    listState: LazyListState
) {
    val ayahs = surah.ayahs
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        itemsIndexed(
            items = ayahs,
            key = { _, ayah -> ayahKey(surah, ayah) },
            contentType = { _, _ -> "ayah" }
        ) { index, ayah ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clickable { onAyahClick(ayah) },
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.48f)
                        .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AyahNumberBadge(ayah.numberInSurah)
                        BookmarkToggle(
                            bookmarked = ayah.numberInSurah in state.bookmarkedAyahNumbers,
                            onClick = { onToggleBookmark(ayah) }
                        )
                    }
                    TranslationText(ayah, state, fontSize = 12)
                    HifzTagRow(surah, ayah, state, onTagMistake)
                    TafseerLink(onClick = { onTafseer(ayah) })
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                )
                Column(
                    modifier = Modifier
                        .weight(0.52f)
                        .padding(vertical = 10.dp)
                ) {
                    AyahArabic(ayah, state.arabicFontSizeSp)
                }
            }
            if (index < ayahs.lastIndex) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                )
            }
        }
    }
}

/** Preset 4: one-handed reading with a floating bottom dock and generous bottom inset. */
@Composable
private fun ThumbBottomDockLayout(
    surah: SurahModel,
    state: QuranAyahUiState,
    onAyahClick: (AyahModel) -> Unit,
    onTafseer: (AyahModel) -> Unit,
    onTagMistake: (AyahModel, String) -> Unit,
    onToggleBookmark: (AyahModel) -> Unit,
    listState: LazyListState
) {
    val ayahs = surah.ayahs
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = ayahs,
            key = { _, ayah -> ayahKey(surah, ayah) },
            contentType = { _, _ -> "ayah" }
        ) { _, ayah ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAyahClick(ayah) }
                    .padding(vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AyahNumberBadge(ayah.numberInSurah)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TafseerLink(onClick = { onTafseer(ayah) })
                        BookmarkToggle(
                            bookmarked = ayah.numberInSurah in state.bookmarkedAyahNumbers,
                            onClick = { onToggleBookmark(ayah) }
                        )
                    }
                }
                AyahArabic(ayah, state.arabicFontSizeSp)
                TranslationText(ayah, state)
                HifzTagRow(surah, ayah, state, onTagMistake)
            }
        }
    }
}

/** Stable list key: unique per surah and ayah, independent of list position. */
internal fun ayahKey(surah: SurahModel, ayah: AyahModel): String =
    "${surah.number}:${ayah.numberInSurah}"

@Composable
internal fun QuranReadingLayoutContent(
    layout: QuranReadingLayout,
    surah: SurahModel,
    state: QuranAyahUiState,
    onAyahClick: (AyahModel) -> Unit,
    onTafseer: (AyahModel) -> Unit,
    onTagMistake: (AyahModel, String) -> Unit,
    onToggleBookmark: (AyahModel) -> Unit,
    onToggleStudyMode: () -> Unit,
    listState: LazyListState
) {
    when (layout) {
        QuranReadingLayout.DIVIDER_HAIRLINE -> DividerHairlineLayout(
            surah, state, onAyahClick, onTafseer, onTagMistake, onToggleBookmark, listState
        )
        QuranReadingLayout.CONTINUOUS_MUSHAF -> ContinuousMushafLayout(
            surah, state, onAyahClick, onTafseer, onTagMistake, onToggleBookmark,
            onToggleStudyMode, listState
        )
        QuranReadingLayout.SPLIT_COLUMN -> SplitColumnLayout(
            surah, state, onAyahClick, onTafseer, onTagMistake, onToggleBookmark, listState
        )
        QuranReadingLayout.THUMB_BOTTOM_DOCK -> ThumbBottomDockLayout(
            surah, state, onAyahClick, onTafseer, onTagMistake, onToggleBookmark, listState
        )
    }
}
