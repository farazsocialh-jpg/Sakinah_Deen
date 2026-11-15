package com.sakinah.deen.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    val focusMode: Boolean
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

@Composable
private fun TafseerLink(
    onClick: () -> Unit,
    text: String = "Tafseer",
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
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

@Composable
private fun DividerHairlineLayout(
    surah: SurahModel,
    state: QuranAyahUiState,
    onAyahClick: (AyahModel) -> Unit,
    onTafseer: (AyahModel) -> Unit,
    onTagMistake: (AyahModel, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        itemsIndexed(surah.ayahs, key = { _, ayah -> ayah.numberInSurah }) { index, ayah ->
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
                    if (state.selectedAyahNumber == ayah.numberInSurah) {
                        Text(
                            text = "Reading",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                AyahArabic(ayah, state.arabicFontSizeSp)
                TranslationText(ayah, state)
                HifzTagRow(surah, ayah, state, onTagMistake)
            }
            if (index < surah.ayahs.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 2.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            }
        }
    }
}

@Composable
private fun ContinuousMushafLayout(
    surah: SurahModel,
    state: QuranAyahUiState,
    onAyahClick: (AyahModel) -> Unit,
    onTafseer: (AyahModel) -> Unit,
    onTagMistake: (AyahModel, String) -> Unit,
    onToggleStudyMode: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(
                        text = "سورة ${surah.nameArabic} · ${surah.nameEnglish}",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    surah.ayahs.forEach { ayah ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAyahClick(ayah) },
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            AyahArabic(ayah, (state.arabicFontSizeSp + 4).coerceAtMost(36))
                            if (ayah.translation.isNotBlank()) {
                                Text(
                                    text = ayah.translation,
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TafseerLink(onClick = { onTafseer(ayah) })
                            HifzTagRow(surah, ayah, state, onTagMistake)
                        }
                    }
                }
            }
            Spacer(Modifier.height(54.dp))
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
                text = "Study | Mus-haf",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AlternatingRowsLayout(
    surah: SurahModel,
    state: QuranAyahUiState,
    onAyahClick: (AyahModel) -> Unit,
    onTafseer: (AyahModel) -> Unit,
    onTagMistake: (AyahModel, String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(surah.ayahs, key = { _, ayah -> ayah.numberInSurah }) { index, ayah ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (index % 2 == 0) MaterialTheme.colorScheme.background
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                    .clickable { onAyahClick(ayah) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.width(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "${ayah.numberInSurah}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AyahArabic(ayah, state.arabicFontSizeSp)
                    TranslationText(ayah, state)
                    HifzTagRow(surah, ayah, state, onTagMistake)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TafseerLink(onClick = { onTafseer(ayah) }, text = "Tafseer >")
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactCardsLayout(
    surah: SurahModel,
    state: QuranAyahUiState,
    onAyahClick: (AyahModel) -> Unit,
    onTafseer: (AyahModel) -> Unit,
    onTagMistake: (AyahModel, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(surah.ayahs, key = { _, ayah -> ayah.numberInSurah }) { _, ayah ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAyahClick(ayah) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (state.selectedAyahNumber == ayah.numberInSurah) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                    }
                )
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AyahNumberBadge(ayah.numberInSurah)
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                TafseerLink(
                                    onClick = { onTafseer(ayah) },
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                        HifzTagRow(surah, ayah, state, onTagMistake)
                    }
                    AyahArabic(ayah, state.arabicFontSizeSp)
                    TranslationText(ayah, state)
                }
            }
        }
    }
}

@Composable
private fun SplitColumnLayout(
    surah: SurahModel,
    state: QuranAyahUiState,
    onAyahClick: (AyahModel) -> Unit,
    onTafseer: (AyahModel) -> Unit,
    onTagMistake: (AyahModel, String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(surah.ayahs, key = { _, ayah -> ayah.numberInSurah }) { index, ayah ->
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
                    AyahNumberBadge(ayah.numberInSurah)
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
            if (index < surah.ayahs.lastIndex) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                )
            }
        }
    }
}

@Composable
private fun ImmersiveZenLayout(
    state: QuranAyahUiState,
    surah: SurahModel,
    onAyahClick: (AyahModel) -> Unit,
    onTafseer: (AyahModel) -> Unit,
    onTagMistake: (AyahModel, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 54.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(surah.ayahs, key = { _, ayah -> ayah.numberInSurah }) { _, ayah ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onAyahClick(ayah)
                        onTafseer(ayah)
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "﴿${ArabicNormalizer.toArabicIndicNumerals(ayah.numberInSurah)}﴾",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                AyahArabic(ayah, (state.arabicFontSizeSp + 3).coerceAtMost(36))
                if (ayah.translation.isNotBlank()) {
                    Text(
                        text = ayah.translation,
                        fontFamily = FontFamily.Serif,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HifzTagRow(surah, ayah, state, onTagMistake)
            }
        }
    }
}

@Composable
private fun FocusSpotlightLayout(
    surah: SurahModel,
    state: QuranAyahUiState,
    onAyahClick: (AyahModel) -> Unit,
    onTafseer: (AyahModel) -> Unit,
    onTagMistake: (AyahModel, String) -> Unit
) {
    var activeAyahNumber by rememberSaveable {
        mutableIntStateOf(state.selectedAyahNumber.coerceAtLeast(1))
    }
    var bookmarkedAyah by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = {
                (activeAyahNumber.toFloat() / surah.ayahs.size.coerceAtLeast(1))
                    .coerceIn(0f, 1f)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(surah.ayahs, key = { _, ayah -> ayah.numberInSurah }) { _, ayah ->
                val active = ayah.numberInSurah == activeAyahNumber
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            activeAyahNumber = ayah.numberInSurah
                            onAyahClick(ayah)
                        },
                    shape = RoundedCornerShape(if (active) 14.dp else 8.dp),
                    color = if (active) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                    border = if (active) BorderStroke(
                        2.dp,
                        MaterialTheme.colorScheme.primary
                    ) else null
                ) {
                    Column(
                        modifier = Modifier.padding(if (active) 12.dp else 8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
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
                                if (active) {
                                    TafseerLink(onClick = { onTafseer(ayah) })
                                }
                            }
                            if (active) {
                                IconButton(
                                    onClick = {
                                        bookmarkedAyah = if (bookmarkedAyah == ayah.numberInSurah) 0
                                        else ayah.numberInSurah
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (bookmarkedAyah == ayah.numberInSurah) {
                                            Icons.Filled.Bookmark
                                        } else {
                                            Icons.Outlined.BookmarkBorder
                                        },
                                        contentDescription = "Bookmark ayah",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                        AyahArabic(ayah, if (active) state.arabicFontSizeSp else state.arabicFontSizeSp - 2)
                        if (active) {
                            TranslationText(ayah, state)
                            HifzTagRow(surah, ayah, state, onTagMistake)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbBottomDockLayout(
    surah: SurahModel,
    state: QuranAyahUiState,
    onAyahClick: (AyahModel) -> Unit,
    onTafseer: (AyahModel) -> Unit,
    onTagMistake: (AyahModel, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(surah.ayahs, key = { _, ayah -> ayah.numberInSurah }) { _, ayah ->
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
                    TafseerLink(onClick = { onTafseer(ayah) })
                }
                AyahArabic(ayah, state.arabicFontSizeSp)
                TranslationText(ayah, state)
                HifzTagRow(surah, ayah, state, onTagMistake)
            }
        }
    }
}

@Composable
private fun ArabicTapExpandLayout(
    surah: SurahModel,
    state: QuranAyahUiState,
    onTafseer: (AyahModel) -> Unit,
    onTagMistake: (AyahModel, String) -> Unit
) {
    var expandedAyahNumber by rememberSaveable { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(surah.ayahs, key = { _, ayah -> ayah.numberInSurah }) { _, ayah ->
            val expanded = expandedAyahNumber == ayah.numberInSurah
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        expandedAyahNumber = if (expanded) 0 else ayah.numberInSurah
                    },
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${ayah.numberInSurah}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    AyahArabic(
                        ayah = ayah,
                        fontSizeSp = state.arabicFontSizeSp,
                        modifier = Modifier.weight(1f)
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TranslationText(ayah, state)
                            HifzTagRow(surah, ayah, state, onTagMistake)
                            TafseerLink(onClick = { onTafseer(ayah) }, text = "Read Tafseer")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeftAccentRibbonLayout(
    surah: SurahModel,
    state: QuranAyahUiState,
    onAyahClick: (AyahModel) -> Unit,
    onTafseer: (AyahModel) -> Unit,
    onTagMistake: (AyahModel, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = "${surah.revelationType} · ${surah.ayahCount} Verses · Juz ${surah.juzNumber}",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            itemsIndexed(surah.ayahs, key = { _, ayah -> ayah.numberInSurah }) { _, ayah ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .clickable { onAyahClick(ayah) },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            .padding(vertical = 3.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "${ayah.numberInSurah}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        AyahArabic(ayah, state.arabicFontSizeSp)
                        TranslationText(ayah, state)
                        HifzTagRow(surah, ayah, state, onTagMistake)
                        TafseerLink(onClick = { onTafseer(ayah) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun QuranReadingLayoutContent(
    layout: QuranReadingLayout,
    surah: SurahModel,
    state: QuranAyahUiState,
    onAyahClick: (AyahModel) -> Unit,
    onTafseer: (AyahModel) -> Unit,
    onTagMistake: (AyahModel, String) -> Unit,
    onToggleStudyMode: () -> Unit
) {
    when (layout) {
        QuranReadingLayout.DIVIDER_HAIRLINE -> DividerHairlineLayout(
            surah, state, onAyahClick, onTafseer, onTagMistake
        )
        QuranReadingLayout.CONTINUOUS_MUSHAF -> ContinuousMushafLayout(
            surah, state, onAyahClick, onTafseer, onTagMistake, onToggleStudyMode
        )
        QuranReadingLayout.ALTERNATING_ROWS -> AlternatingRowsLayout(
            surah, state, onAyahClick, onTafseer, onTagMistake
        )
        QuranReadingLayout.COMPACT_CARDS -> CompactCardsLayout(
            surah, state, onAyahClick, onTafseer, onTagMistake
        )
        QuranReadingLayout.SPLIT_COLUMN -> SplitColumnLayout(
            surah, state, onAyahClick, onTafseer, onTagMistake
        )
        QuranReadingLayout.IMMERSIVE_ZEN -> ImmersiveZenLayout(
            state, surah, onAyahClick, onTafseer, onTagMistake
        )
        QuranReadingLayout.FOCUS_SPOTLIGHT -> FocusSpotlightLayout(
            surah, state, onAyahClick, onTafseer, onTagMistake
        )
        QuranReadingLayout.THUMB_BOTTOM_DOCK -> ThumbBottomDockLayout(
            surah, state, onAyahClick, onTafseer, onTagMistake
        )
        QuranReadingLayout.ARABIC_TAP_EXPAND -> ArabicTapExpandLayout(
            surah, state, onTafseer, onTagMistake
        )
        QuranReadingLayout.LEFT_ACCENT_RIBBON -> LeftAccentRibbonLayout(
            surah, state, onAyahClick, onTafseer, onTagMistake
        )
    }
}
