package com.sakinah.deen.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakinah.deen.data.model.AyahModel
import com.sakinah.deen.data.model.SurahModel
import com.sakinah.deen.data.quran.QulWord
import com.sakinah.deen.R
import com.sakinah.deen.data.prefs.QuranReadingLayout
import com.sakinah.deen.data.prefs.QuranReadingMode
import com.sakinah.deen.ui.components.ArabicVerseBlock
import com.sakinah.deen.ui.components.SegmentedPillBar
import com.sakinah.deen.ui.theme.UthmanicFontFamily
import com.sakinah.deen.util.ArabicNormalizer
import kotlin.math.roundToInt

internal data class QuranAyahUiState(
    val selectedAyahNumber: Int,
    val highlightedAyahNumber: Int,
    val hifzOverlayActive: Boolean,
    val hifzMistakes: Map<Pair<Int, Int>, String>,
    val translationIsRtl: Boolean,
    val arabicFontSizeSp: Int,
    val translationFontSizeSp: Int = 13,
    val focusMode: Boolean,
    val bookmarkedAyahNumbers: Set<Int> = emptySet(),
    /** False hides the word-by-word strip without losing the reader's choice. */
    val wbwEnabled: Boolean = false,
    val selectedWbwWordKey: String? = null
)

internal fun cleanQuranArabic(raw: String): String =
    raw.replace("{", "").replace("}", "").trim()

/**
 * Builds ayah groups from the commentary actually available for the open surah. Consecutive
 * ayahs with cached tafsir become a group (capped at four), and ayahs without cached tafsir are
 * still grouped so every header has a meaningful label.
 */
internal fun tafsirAyahGroups(ayahCount: Int, availableAyahs: Set<Int>): List<IntRange> {
    if (ayahCount <= 0) return emptyList()
    val groups = mutableListOf<IntRange>()
    fun appendRun(numbers: List<Int>) {
        if (numbers.isEmpty()) return
        var start = numbers.first()
        var previous = start
        for (number in numbers.drop(1)) {
            if (number == previous + 1 && number - start < 4) {
                previous = number
            } else {
                groups += start..previous
                start = number
                previous = number
            }
        }
        groups += start..previous
    }

    appendRun(availableAyahs.filter { it in 1..ayahCount }.sorted())
    val covered = groups.flatMap { it.toList() }.toSet()
    appendRun((1..ayahCount).filterNot { it in covered })
    return groups.distinct().sortedBy { it.first }
}

internal fun tafsirLabelFor(groups: List<IntRange>, ayahNumber: Int): String {
    val group = groups.firstOrNull { ayahNumber in it }
        ?: return "Tafsir $ayahNumber"
    return "Tafsir ${group.first}:${group.last}"
}

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
            .defaultMinSize(minWidth = 28.dp, minHeight = 28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$number",
            fontSize = 11.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
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
        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
        isQuran = true,
        lineHeightMultiplier = 1.65f
    )
}

/** Header shared by the inline ayah card: number, single-ayah Tafsir link and bookmark. */
@Composable
private fun AyahHeaderRow(
    ayah: AyahModel,
    bookmarked: Boolean,
    tafsirLabel: String,
    onTafseer: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AyahNumberBadge(ayah.numberInSurah)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TafseerLink(text = tafsirLabel, onClick = onTafseer)
            BookmarkToggle(bookmarked = bookmarked, onClick = onToggleBookmark)
        }
    }
}

/** Preset 1: translation on the left, Arabic on the right, per verse. */
@Composable
private fun SplitColumnLayout(
    surah: SurahModel,
    state: QuranAyahUiState,
    onAyahClick: (AyahModel) -> Unit,
    onTafseer: (AyahModel) -> Unit,
    onTagMistake: (AyahModel, String) -> Unit,
    onToggleBookmark: (AyahModel) -> Unit,
    listState: LazyListState,
    wbwWords: Map<Int, List<QulWord>> = emptyMap(),
    qulScript: String = QulWord.QUL_SCRIPT_UTHMANI,
    onWordClick: (QulWord) -> Unit = {},
    tafsirGroups: List<IntRange> = emptyList(),
    translationFontSp: Int = 13
) {
    val ayahs = surah.ayahs
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        itemsIndexed(
            items = ayahs,
            key = { _, ayah -> ayahKey(surah, ayah) },
            contentType = { _, _ -> "ayah" }
        ) { index, ayah ->
            Column(modifier = Modifier.fillMaxWidth()) {
                if (index == 0 && surah.number != 1 && surah.number != 9) {
                    BismillahHeader(modifier = Modifier.padding(bottom = 8.dp))
                }
                    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAyahClick(ayah) }
                            .drawBehind {
                                val dividerX = size.width * 0.48f
                                drawLine(
                                    color = dividerColor,
                                    start = Offset(dividerX, 0f),
                                    end = Offset(dividerX, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            },
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(0.48f)
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
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
                            TafseerLink(
                                text = tafsirLabelFor(tafsirGroups, ayah.numberInSurah),
                                onClick = { onTafseer(ayah) }
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(0.52f)
                                .padding(vertical = 4.dp)
                        ) {
                            AyahArabic(ayah, state.arabicFontSizeSp)
                            WbwWordsBlock(
                                words = wbwWords[ayah.numberInSurah].orEmpty(),
                                state = state,
                                script = qulScript,
                                onWordClick = onWordClick
                            )
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
}

/** Preset 2: one-handed reading with a floating bottom dock and generous bottom inset. */
@Composable
private fun ThumbBottomDockLayout(
    surah: SurahModel,
    state: QuranAyahUiState,
    onAyahClick: (AyahModel) -> Unit,
    onTafseer: (AyahModel) -> Unit,
    onTagMistake: (AyahModel, String) -> Unit,
    onToggleBookmark: (AyahModel) -> Unit,
    listState: LazyListState,
    wbwWords: Map<Int, List<QulWord>> = emptyMap(),
    qulScript: String = QulWord.QUL_SCRIPT_UTHMANI,
    onWordClick: (QulWord) -> Unit = {},
    tafsirGroups: List<IntRange> = emptyList(),
    translationFontSp: Int = 13
) {
    val ayahs = surah.ayahs
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(
            items = ayahs,
            key = { _, ayah -> ayahKey(surah, ayah) },
            contentType = { _, _ -> "ayah" }
        ) { index, ayah ->
            Column(modifier = Modifier.fillMaxWidth()) {
                if (index == 0 && surah.number != 1 && surah.number != 9) {
                    BismillahHeader(modifier = Modifier.padding(bottom = 6.dp))
                }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAyahClick(ayah) }
                            .padding(vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AyahNumberBadge(ayah.numberInSurah)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TafseerLink(
                                text = tafsirLabelFor(tafsirGroups, ayah.numberInSurah),
                                onClick = { onTafseer(ayah) }
                            )
                                BookmarkToggle(
                                    bookmarked = ayah.numberInSurah in state.bookmarkedAyahNumbers,
                                    onClick = { onToggleBookmark(ayah) }
                                )
                            }
                        }
                        AyahArabic(ayah, state.arabicFontSizeSp)
                        WbwWordsBlock(
                            words = wbwWords[ayah.numberInSurah].orEmpty(),
                            state = state,
                            script = qulScript,
                            onWordClick = onWordClick
                        )
                        TranslationText(ayah, state, fontSize = translationFontSp)
                        HifzTagRow(surah, ayah, state, onTagMistake)
                    }
            }
        }
    }
}

/**
 * Word-by-word strip: verbatim QUL words (`uthmani` or `indopak`, never reordered or
 * post-processed) in an RTL layout so word 1 sits on the right edge, with each meaning
 * underneath. Every word is clickable and opens the word-meaning/root sheet.
 *
 * Words come from memory (the ViewModel preloads the chapter), so nothing here queries
 * SQLite while scrolling. Each word is keyed by its stable `word_key`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WbwWordsBlock(
    words: List<QulWord>,
    state: QuranAyahUiState,
    script: String,
    onWordClick: (QulWord) -> Unit
) {
    if (words.isEmpty() || !state.wbwEnabled) return
    // One presentation: interlinear RTL. Every word stays clickable and opens the
    // word-meaning/root sheet from the reader, so no secondary layout is needed.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        WbwInterlinear(
            words = words,
            state = state,
            script = script,
            arabicFontSp = state.arabicFontSizeSp,
            translationFontSp = state.translationFontSizeSp,
            onWordClick = onWordClick
        )
    }
}

/** Subtle emphasis for the word the reader last tapped; nothing else is boxed. */
@Composable
private fun Modifier.wbwHighlight(active: Boolean): Modifier =
    if (active) {
        this
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
    } else {
        this
    }

/** The only word-by-word presentation: borderless RTL Arabic with its meaning underneath. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WbwInterlinear(
    words: List<QulWord>,
    state: QuranAyahUiState,
    script: String,
    arabicFontSp: Int,
    translationFontSp: Int,
    onWordClick: (QulWord) -> Unit
) {
    // No height cap and no take(n): every word of the ayah wraps into the FlowRow.
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        words.forEach { word ->
            key(word.wordKey) {
                Column(
                    modifier = Modifier
                        .wbwHighlight(state.selectedWbwWordKey == word.wordKey)
                        .clickable { onWordClick(word) }
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = word.arabicFor(script),
                        fontFamily = UthmanicFontFamily,
                        fontSize = arabicFontSp.sp,
                        lineHeight = (arabicFontSp * 1.65f).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (word.translation.isNotBlank()) {
                        Text(
                            text = word.translation,
                            fontSize = translationFontSp.sp,
                            lineHeight = (translationFontSp * 1.2f).sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (word.transliteration.isNotBlank()) {
                        Text(
                            text = word.transliteration,
                            fontSize = (translationFontSp * 0.8f).sp,
                            lineHeight = (translationFontSp).sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
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
    listState: LazyListState,
    wbwWords: Map<Int, List<QulWord>> = emptyMap(),
    qulScript: String = QulWord.QUL_SCRIPT_UTHMANI,
    onWordClick: (QulWord) -> Unit = {},
    tafsirGroups: List<IntRange> = emptyList(),
    translationFontSp: Int = 13
) {
    when (layout) {
        QuranReadingLayout.SPLIT_COLUMN -> SplitColumnLayout(
            surah, state, onAyahClick, onTafseer, onTagMistake, onToggleBookmark, listState,
            wbwWords, qulScript, onWordClick, tafsirGroups, translationFontSp
        )
        QuranReadingLayout.THUMB_BOTTOM_DOCK -> ThumbBottomDockLayout(
            surah, state, onAyahClick, onTafseer, onTagMistake, onToggleBookmark, listState,
            wbwWords, qulScript, onWordClick, tafsirGroups, translationFontSp
        )
    }
}

// ----------------------------------------------------------------------------------------
// Reading options sheet with a pinned Live Mini-Preview
// ----------------------------------------------------------------------------------------

/**
 * The reading-options sheet: a pinned preview that answers "what will this look like?"
 * before the reader leaves the sheet, and the controls that change it directly below.
 *
 * The preview renders Surah Al-Fatihah 1:1 from the bundled data only — the Uthmani line is
 * the same verbatim string the reader shows, the IndoPak line is the QUL column, and the
 * word tokens are the four QUL rows for 1:1. Nothing here invents or rewrites Quranic text.
 */
@Composable
internal fun QuranReadingOptionsSheet(
    readingMode: QuranReadingMode,
    onReadingModeSelect: (QuranReadingMode) -> Unit,
    readingLayout: QuranReadingLayout,
    onReadingLayoutSelect: (QuranReadingLayout) -> Unit,
    wbwEnabled: Boolean,
    onWbwEnabledChange: (Boolean) -> Unit,
    wbwScript: String,
    onWbwScriptSelect: (String) -> Unit,
    wbwLanguages: List<String>,
    wbwLanguage: String,
    onWbwLanguageSelect: (String) -> Unit,
    translationLanguage: String,
    onSearch: () -> Unit,
    onBookmarks: () -> Unit,
    onTranslation: () -> Unit,
    onTafsir: () -> Unit,
    hifzEnabled: Boolean,
    onHifzChange: (Boolean) -> Unit,
    offlineSummary: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.quran_options_title),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        // Search sits above the preview so it is reachable without scrolling past it.
        Surface(
            onClick = onSearch,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = stringResource(R.string.quran_options_search),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LiveMiniPreviewCard(
            readingMode = readingMode,
            readingLayout = readingLayout,
            wbwEnabled = wbwEnabled,
            script = wbwScript,
            translationLanguage = translationLanguage
        )
        // Only this column scrolls; the preview above stays pinned while options move.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReadingOptionsSectionLabel(stringResource(R.string.quran_reading_mode_title))
            SegmentedPillBar(
                options = QuranReadingMode.entries.map { it.localizedLabel() },
                selectedIndex = QuranReadingMode.entries.indexOf(readingMode).coerceAtLeast(0),
                onSelect = { index ->
                    QuranReadingMode.entries.getOrNull(index)?.let(onReadingModeSelect)
                }
            )

            ReadingOptionsSectionLabel(stringResource(R.string.quran_layout_menu_title))
            SegmentedPillBar(
                options = QuranReadingLayout.entries.map { it.shortLabel() },
                selectedIndex = QuranReadingLayout.entries.indexOf(readingLayout).coerceAtLeast(0),
                onSelect = { index ->
                    QuranReadingLayout.entries.getOrNull(index)?.let(onReadingLayoutSelect)
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.quran_wbw_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (wbwEnabled) {
                        Text(
                            text = stringResource(R.string.quran_wbw_single_behavior),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(checked = wbwEnabled, onCheckedChange = onWbwEnabledChange)
            }
            AnimatedVisibility(visible = wbwEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (wbwLanguages.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.quran_wbw_language_title),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            wbwLanguages.forEach { code ->
                                val selected = code == wbwLanguage
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable { onWbwLanguageSelect(code) }
                                ) {
                                    Text(
                                        text = wbwLanguageLabel(code),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            ReadingOptionsSectionLabel(stringResource(R.string.quran_script_title))
            SegmentedPillBar(
                options = listOf(
                    stringResource(R.string.quran_wbw_script_uthmani),
                    stringResource(R.string.quran_wbw_script_indopak)
                ),
                selectedIndex = if (wbwScript == QulWord.QUL_SCRIPT_INDOPAK) 1 else 0,
                onSelect = { index ->
                    onWbwScriptSelect(
                        if (index == 1) QulWord.QUL_SCRIPT_INDOPAK
                        else QulWord.QUL_SCRIPT_UTHMANI
                    )
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.quran_translation_language_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = translationLanguage,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(onClick = onTranslation) {
                    Text(stringResource(R.string.quran_option_change), fontSize = 12.sp)
                }
            }
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReadingOptionLink(
                    icon = Icons.Default.Bookmark,
                    label = stringResource(R.string.quran_options_bookmarks),
                    onClick = onBookmarks,
                    modifier = Modifier.weight(1f)
                )
                ReadingOptionLink(
                    icon = Icons.Default.Book,
                    label = stringResource(R.string.quran_options_tafseer),
                    onClick = onTafsir,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.quran_options_hifz),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(checked = hifzEnabled, onCheckedChange = onHifzChange)
            }
            offlineSummary?.let { summary ->
                Text(
                    text = summary,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReadingOptionsSectionLabel(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ReadingOptionLink(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---- Live mini preview ----------------------------------------------------------------

/** The four QUL rows of 1:1, verbatim from the bundled qul_offline.db. */
private val QUL_PREVIEW_1_1 = listOf(
    QulWord(1, "1:1:1", 1, 1, 1, "بِسْمِ", "بِسۡمِ", "bis'mi", "In (the) name"),
    QulWord(2, "1:1:2", 1, 1, 2, "ٱللَّهِ", "اللهِ", "l-lahi", "(of) Allah"),
    QulWord(3, "1:1:3", 1, 1, 3, "ٱلرَّحْمَـٰنِ", "الرَّحۡمٰنِ", "l-raḥmāni", "the Most Gracious"),
    QulWord(4, "1:1:4", 1, 1, 4, "ٱلرَّحِيمِ", "الرَّحِيۡمِ", "l-raḥīmi", "the Most Merciful")
)

/** Bundled Uthmani reading text of 1:1, exactly as the reader renders it. */
private const val PREVIEW_1_1_UTHMANI = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"

/** Bundled Saheeh International translation of 1:1. */
private const val PREVIEW_1_1_TRANSLATION =
    "In the name of Allah, the Entirely Merciful, the Especially Merciful."

private fun previewArabicFor(script: String): String =
    if (script == QulWord.QUL_SCRIPT_INDOPAK) {
        QUL_PREVIEW_1_1.joinToString(" ") { it.indopak }
    } else {
        PREVIEW_1_1_UTHMANI
    }

@Composable
private fun LiveMiniPreviewCard(
    readingMode: QuranReadingMode,
    readingLayout: QuranReadingLayout,
    wbwEnabled: Boolean,
    script: String,
    translationLanguage: String
) {
    val subtitle = when {
        readingMode == QuranReadingMode.MUSHAF -> stringResource(R.string.quran_preview_mode_mushaf)
        readingLayout == QuranReadingLayout.SPLIT_COLUMN ->
            stringResource(R.string.quran_preview_layout_split)
        else -> stringResource(R.string.quran_preview_layout_dock)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = stringResource(R.string.quran_preview_label),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = stringResource(R.string.quran_preview_reference),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(9.dp)
            ) {
                PreviewBody(
                    readingMode = readingMode,
                    readingLayout = readingLayout,
                    wbwEnabled = wbwEnabled,
                    script = script,
                    translationLanguage = translationLanguage
                )
            }
            Text(
                text = subtitle,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PreviewBody(
    readingMode: QuranReadingMode,
    readingLayout: QuranReadingLayout,
    wbwEnabled: Boolean,
    script: String,
    translationLanguage: String
) {
    val arabic = previewArabicFor(script)
    if (readingMode == QuranReadingMode.MUSHAF) {
        PreviewMushafPage(arabic = arabic, translationLanguage = translationLanguage)
        return
    }
    when (readingLayout) {
        QuranReadingLayout.SPLIT_COLUMN -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Column(
                    modifier = Modifier.weight(0.46f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    PreviewTranslationLine(
                        translationLanguage,
                        maxLines = 3,
                        fontSize = previewMicroSp(0.73f)
                    )
                    Text(
                        text = stringResource(R.string.quran_options_tafseer),
                        fontSize = previewMicroSp(0.64f).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                )
                Column(
                    modifier = Modifier.weight(0.54f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    PreviewArabicLine(arabic, fontSize = 12)
                    if (wbwEnabled) PreviewWbwStrip(script)
                }
            }
            PreviewSkeletonLine(0.8f)
        }
        QuranReadingLayout.THUMB_BOTTOM_DOCK -> Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                PreviewArabicLine(arabic, fontSize = 14)
                if (wbwEnabled) PreviewWbwStrip(script)
                PreviewTranslationLine(translationLanguage, maxLines = 2, fontSize = 9)
                PreviewSkeletonLine(0.7f)
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                )
            ) {
                Text(
                    text = "Al-Fatihah ▾",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = previewMicroSp(0.73f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun PreviewMushafPage(arabic: String, translationLanguage: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "سورة الفاتحة · Al-Fatihah",
            fontSize = previewMicroSp(0.73f).sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Text(
                text = arabic,
                modifier = Modifier.fillMaxWidth(),
                fontFamily = UthmanicFontFamily,
                fontSize = 15.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        PreviewTranslationLine(
            language = translationLanguage,
            maxLines = 2,
            fontSize = previewMicroSp(0.73f),
            center = true
        )
        PreviewSkeletonLine(0.95f)
        PreviewSkeletonLine(0.7f)
    }
}

/**
 * Tiny preview labels, derived from the theme's [androidx.compose.material3.Typography.labelSmall]
 * token. `.sp` then picks up the reading-density font scale, so previews stay proportional to
 * the real reader instead of being fixed absolute sizes.
 */
@Composable
private fun previewMicroSp(fractionOfLabel: Float): Int =
    (MaterialTheme.typography.labelSmall.fontSize.value * fractionOfLabel).roundToInt()

@Composable
private fun PreviewArabicLine(text: String, fontSize: Int) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            fontFamily = UthmanicFontFamily,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.75f).sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PreviewTranslationLine(
    language: String,
    maxLines: Int,
    fontSize: Int = 9,
    center: Boolean = false
) {
    val isEnglish = language.contains("English", ignoreCase = true)
    Text(
        text = if (isEnglish) PREVIEW_1_1_TRANSLATION
        else stringResource(R.string.quran_preview_translation_language, language),
        modifier = Modifier.fillMaxWidth(),
        fontFamily = FontFamily.Serif,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.3f).sp,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun PreviewSkeletonLine(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(5.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    )
}

/** Miniature of the single interlinear RTL presentation, using the bundled 1:1 word rows. */
@Composable
private fun PreviewWbwStrip(script: String) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            QUL_PREVIEW_1_1.forEach { word ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = word.arabicFor(script),
                        fontFamily = UthmanicFontFamily,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = word.translation,
                        fontSize = previewMicroSp(0.55f).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
