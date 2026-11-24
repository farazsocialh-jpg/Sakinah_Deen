package com.sakinah.deen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakinah.deen.data.model.AyahModel
import com.sakinah.deen.data.model.SurahModel
import com.sakinah.deen.data.quran.QulMorphology
import com.sakinah.deen.data.quran.QulWord
import com.sakinah.deen.R
import com.sakinah.deen.data.prefs.QuranReadingLayout
import com.sakinah.deen.data.prefs.WbwLayoutMode
import com.sakinah.deen.ui.components.ArabicVerseBlock
import com.sakinah.deen.ui.components.SakinahCard
import com.sakinah.deen.ui.theme.UthmanicFontFamily
import com.sakinah.deen.util.ArabicNormalizer

internal data class QuranAyahUiState(
    val selectedAyahNumber: Int,
    val highlightedAyahNumber: Int,
    val hifzOverlayActive: Boolean,
    val hifzMistakes: Map<Pair<Int, Int>, String>,
    val translationIsRtl: Boolean,
    val arabicFontSizeSp: Int,
    val focusMode: Boolean,
    val bookmarkedAyahNumbers: Set<Int> = emptySet(),
    /** Word-by-word presentation plus the state the modes that need it read from. */
    val wbwMode: WbwLayoutMode = WbwLayoutMode.DEFAULT,
    val selectedWbwWordKey: String? = null,
    val revealedWbwWordKeys: Set<String> = emptySet(),
    val wbwMorphology: Map<String, QulMorphology> = emptyMap()
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
    listState: LazyListState,
    wbwWords: Map<Int, List<QulWord>> = emptyMap(),
    qulScript: String = QulWord.QUL_SCRIPT_UTHMANI,
    onWordClick: (QulWord) -> Unit = {},
    onToggleWbwReveal: (QulWord) -> Unit = {}
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
                WbwWordsBlock(
                    words = wbwWords[ayah.numberInSurah].orEmpty(),
                    state = state,
                    script = qulScript,
                    onWordClick = onWordClick,
                    onToggleReveal = onToggleWbwReveal
                )
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
    listState: LazyListState,
    wbwWords: Map<Int, List<QulWord>> = emptyMap(),
    qulScript: String = QulWord.QUL_SCRIPT_UTHMANI,
    onWordClick: (QulWord) -> Unit = {},
    onToggleWbwReveal: (QulWord) -> Unit = {}
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
                    WbwWordsBlock(
                        words = wbwWords[ayah.numberInSurah].orEmpty(),
                        state = state,
                        script = qulScript,
                        onWordClick = onWordClick,
                        onToggleReveal = onToggleWbwReveal,
                        fontSizeSp = 16
                    )
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
    listState: LazyListState,
    wbwWords: Map<Int, List<QulWord>> = emptyMap(),
    qulScript: String = QulWord.QUL_SCRIPT_UTHMANI,
    onWordClick: (QulWord) -> Unit = {},
    onToggleWbwReveal: (QulWord) -> Unit = {}
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
                    WbwWordsBlock(
                        words = wbwWords[ayah.numberInSurah].orEmpty(),
                        state = state,
                        script = qulScript,
                        onWordClick = onWordClick,
                        onToggleReveal = onToggleWbwReveal,
                        fontSizeSp = 16
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

/** Preset 4: one-handed reading with a floating bottom dock and generous bottom inset. */
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
    onToggleWbwReveal: (QulWord) -> Unit = {}
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
                WbwWordsBlock(
                    words = wbwWords[ayah.numberInSurah].orEmpty(),
                    state = state,
                    script = qulScript,
                    onWordClick = onWordClick,
                    onToggleReveal = onToggleWbwReveal
                )
                TranslationText(ayah, state)
                HifzTagRow(surah, ayah, state, onTagMistake)
            }
        }
    }
}

/**
 * Word-by-word strip in the reader's chosen [WbwLayoutMode].
 *
 * Only the affordances change between modes: every one renders the verbatim QUL word
 * (`uthmani` or `indopak`, never reordered or post-processed). The whole strip is wrapped
 * in an RTL layout direction so word 1 sits on the right edge and rows wrap right-to-left
 * whatever the app locale is, which is what a reader expects from a Quran line.
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
    onWordClick: (QulWord) -> Unit,
    onToggleReveal: (QulWord) -> Unit,
    fontSizeSp: Int = 18
) {
    if (words.isEmpty()) return
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        when (state.wbwMode) {
            WbwLayoutMode.INTERLINEAR_RTL ->
                WbwInterlinear(words, state, script, fontSizeSp, onWordClick)
            WbwLayoutMode.MUSHAF_TAP ->
                WbwMushafLine(words, state, script, fontSizeSp, onWordClick)
            WbwLayoutMode.VERSE_BOTTOM_DOCK ->
                WbwVerseDock(words, state, script, fontSizeSp, onWordClick)
            WbwLayoutMode.WORD_CARDS_RTL ->
                WbwWordCards(words, state, script, fontSizeSp, onWordClick)
            WbwLayoutMode.GLOSSARY_LIST ->
                WbwGlossary(words, state, script, fontSizeSp, onWordClick)
            WbwLayoutMode.VOCAB_QUIZ ->
                WbwVocabQuiz(words, state, script, fontSizeSp, onWordClick, onToggleReveal)
        }
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

/** Label + value line for root / part of speech inside the word-by-word modes. */
@Composable
private fun WbwMetaLine(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Default: borderless RTL interlinear — Arabic with its meaning directly underneath. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WbwInterlinear(
    words: List<QulWord>,
    state: QuranAyahUiState,
    script: String,
    fontSizeSp: Int,
    onWordClick: (QulWord) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        words.forEach { word ->
            key(word.wordKey) {
                Column(
                    modifier = Modifier
                        .wbwHighlight(state.selectedWbwWordKey == word.wordKey)
                        .clickable { onWordClick(word) }
                        .padding(horizontal = 3.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = word.arabicFor(script),
                        fontFamily = UthmanicFontFamily,
                        fontSize = fontSizeSp.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (word.translation.isNotBlank()) {
                        Text(
                            text = word.translation,
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (word.transliteration.isNotBlank()) {
                        Text(
                            text = word.transliteration,
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}

/** Continuous, label-free RTL Arabic line; a tap opens the word's morphology sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WbwMushafLine(
    words: List<QulWord>,
    state: QuranAyahUiState,
    script: String,
    fontSizeSp: Int,
    onWordClick: (QulWord) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        words.forEach { word ->
            key(word.wordKey) {
                Text(
                    text = word.arabicFor(script),
                    modifier = Modifier
                        .wbwHighlight(state.selectedWbwWordKey == word.wordKey)
                        .clickable { onWordClick(word) }
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                    fontFamily = UthmanicFontFamily,
                    fontSize = fontSizeSp.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Verse plus a compact dock: the line stays label-free, and the dock below reports the
 * tapped word (Arabic on the right, meaning / transliteration / root / POS on the left).
 */
@Composable
private fun WbwVerseDock(
    words: List<QulWord>,
    state: QuranAyahUiState,
    script: String,
    fontSizeSp: Int,
    onWordClick: (QulWord) -> Unit
) {
    val active = words.firstOrNull { it.wordKey == state.selectedWbwWordKey }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WbwMushafLine(words, state, script, fontSizeSp, onWordClick)
        SakinahCard(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            contentPadding = 10.dp
        ) {
            if (active == null) {
                Text(
                    text = stringResource(R.string.quran_wbw_dock_hint),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val morphology = state.wbwMorphology[active.wordKey]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = active.arabicFor(script),
                        fontFamily = UthmanicFontFamily,
                        fontSize = 26.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (active.translation.isNotBlank()) {
                            Text(
                                text = active.translation,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (active.transliteration.isNotBlank()) {
                            Text(
                                text = active.transliteration,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        morphology?.root?.takeIf { it.isNotBlank() }?.let { root ->
                            WbwMetaLine(stringResource(R.string.quran_wbw_root), root)
                        }
                        morphology?.pos?.takeIf { it.isNotBlank() }?.let { pos ->
                            WbwMetaLine(stringResource(R.string.quran_wbw_pos), pos)
                        }
                    }
                }
            }
        }
    }
}

/** Rounded tiles flowing right-to-left, one word each. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WbwWordCards(
    words: List<QulWord>,
    state: QuranAyahUiState,
    script: String,
    fontSizeSp: Int,
    onWordClick: (QulWord) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        words.forEach { word ->
            key(word.wordKey) {
                SakinahCard(
                    modifier = Modifier.wbwHighlight(state.selectedWbwWordKey == word.wordKey),
                    onClick = { onWordClick(word) },
                    contentPadding = 10.dp,
                    fillWidth = false
                ) {
                    Text(
                        text = word.arabicFor(script),
                        fontFamily = UthmanicFontFamily,
                        fontSize = (fontSizeSp - 2).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (word.transliteration.isNotBlank()) {
                        Text(
                            text = word.transliteration,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    if (word.translation.isNotBlank()) {
                        Text(
                            text = word.translation,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Two columns per word in exact recitation order: Arabic right, explanation left. */
@Composable
private fun WbwGlossary(
    words: List<QulWord>,
    state: QuranAyahUiState,
    script: String,
    fontSizeSp: Int,
    onWordClick: (QulWord) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        words.forEachIndexed { index, word ->
            key(word.wordKey) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wbwHighlight(state.selectedWbwWordKey == word.wordKey)
                        .clickable { onWordClick(word) }
                        .padding(vertical = 5.dp, horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = word.arabicFor(script),
                        modifier = Modifier.weight(0.4f),
                        fontFamily = UthmanicFontFamily,
                        fontSize = fontSizeSp.sp,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column(
                        modifier = Modifier.weight(0.6f),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        if (word.translation.isNotBlank()) {
                            Text(
                                text = word.translation,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (word.transliteration.isNotBlank()) {
                            Text(
                                text = word.transliteration,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        state.wbwMorphology[word.wordKey]
                            ?.root
                            ?.takeIf { it.isNotBlank() }
                            ?.let { root -> WbwMetaLine(stringResource(R.string.quran_wbw_root), root) }
                    }
                }
                if (index < words.lastIndex) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                    )
                }
            }
        }
    }
}

/** Self-testing: the Arabic stays visible while each meaning waits behind a mask pill. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WbwVocabQuiz(
    words: List<QulWord>,
    state: QuranAyahUiState,
    script: String,
    fontSizeSp: Int,
    onWordClick: (QulWord) -> Unit,
    onToggleReveal: (QulWord) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        words.forEach { word ->
            key(word.wordKey) {
                val revealed = word.wordKey in state.revealedWbwWordKeys
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = word.arabicFor(script),
                        modifier = Modifier
                            .clickable { onWordClick(word) }
                            .padding(horizontal = 3.dp),
                        fontFamily = UthmanicFontFamily,
                        fontSize = fontSizeSp.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (revealed) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { onToggleReveal(word) }
                    ) {
                        Text(
                            text = if (revealed && word.translation.isNotBlank()) {
                                word.translation
                            } else {
                                stringResource(R.string.quran_wbw_quiz_masked)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            color = if (revealed) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
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
    onToggleStudyMode: () -> Unit,
    listState: LazyListState,
    wbwWords: Map<Int, List<QulWord>> = emptyMap(),
    qulScript: String = QulWord.QUL_SCRIPT_UTHMANI,
    onWordClick: (QulWord) -> Unit = {},
    onToggleWbwReveal: (QulWord) -> Unit = {}
) {
    when (layout) {
        QuranReadingLayout.DIVIDER_HAIRLINE -> DividerHairlineLayout(
            surah, state, onAyahClick, onTafseer, onTagMistake, onToggleBookmark, listState,
            wbwWords, qulScript, onWordClick, onToggleWbwReveal
        )
        QuranReadingLayout.CONTINUOUS_MUSHAF -> ContinuousMushafLayout(
            surah, state, onAyahClick, onTafseer, onTagMistake, onToggleBookmark,
            onToggleStudyMode, listState, wbwWords, qulScript, onWordClick, onToggleWbwReveal
        )
        QuranReadingLayout.SPLIT_COLUMN -> SplitColumnLayout(
            surah, state, onAyahClick, onTafseer, onTagMistake, onToggleBookmark, listState,
            wbwWords, qulScript, onWordClick, onToggleWbwReveal
        )
        QuranReadingLayout.THUMB_BOTTOM_DOCK -> ThumbBottomDockLayout(
            surah, state, onAyahClick, onTafseer, onTagMistake, onToggleBookmark, listState,
            wbwWords, qulScript, onWordClick, onToggleWbwReveal
        )
    }
}
