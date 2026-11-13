package com.sakinah.deen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.data.model.AyahModel
import com.sakinah.deen.ui.components.ArabicVerseBlock
import com.sakinah.deen.ui.components.SakinahCard
import com.sakinah.deen.ui.components.SegmentedPillBar
import com.sakinah.deen.ui.components.StatusBadge
import com.sakinah.deen.ui.theme.MintGreen
import com.sakinah.deen.ui.theme.UthmanicFontFamily
import com.sakinah.deen.ui.viewmodel.AyahSearchResult
import com.sakinah.deen.ui.viewmodel.QuranUiIntent
import com.sakinah.deen.ui.viewmodel.QuranViewModel
import com.sakinah.deen.util.ArabicNormalizer
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranScreen(
    initialSurahNumber: Int = 1,
    initialAyahNumber: Int = 1,
    deepLinkNonce: Int = 0,
    viewModel: QuranViewModel? = null,
    modifier: Modifier = Modifier
) {
    val vm: QuranViewModel = viewModel ?: koinViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val surah = state.currentSurah

    var studioSheetTab by remember { mutableIntStateOf(-1) } // -1 closed; 0=Surah, 1=Translation, 2=Tafsir
    var translationQuery by remember { mutableStateOf("") }
    var selectedAyahNumber by remember { mutableIntStateOf(initialAyahNumber) }
    var hifzOverlayActive by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(initialSurahNumber, initialAyahNumber, deepLinkNonce) {
        if (deepLinkNonce > 0 || initialSurahNumber != 1 || initialAyahNumber != 1) {
            vm.onIntent(QuranUiIntent.SelectSurahAtAyah(initialSurahNumber, initialAyahNumber))
            selectedAyahNumber = initialAyahNumber
        }
    }

    LaunchedEffect(state.scrollTargetAyah, surah.number, surah.ayahs.size) {
        val target = state.scrollTargetAyah ?: return@LaunchedEffect
        val idx = surah.ayahs.indexOfFirst { it.numberInSurah == target }
        if (idx >= 0) {
            selectedAyahNumber = target
            if (state.isStudyMode) {
                listState.animateScrollToItem(idx)
            }
            vm.onIntent(QuranUiIntent.ClearScrollTarget)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Top Navigation & Layout Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .weight(1f)
                    .clickable { studioSheetTab = 0 }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${surah.number}. ${surah.nameEnglish}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = surah.nameArabic,
                        fontFamily = UthmanicFontFamily,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SegmentedPillBar(
                options = listOf("Study", "Mus-haf"),
                selectedIndex = if (state.isStudyMode) 0 else 1,
                onSelect = { idx ->
                    val wantStudy = idx == 0
                    if (state.isStudyMode != wantStudy) {
                        vm.onIntent(QuranUiIntent.ToggleLayoutMode)
                    }
                },
                modifier = Modifier.width(154.dp)
            )
        }

        // 2. Offline Arabic (diacritic-insensitive) & Translation Search
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { vm.onIntent(QuranUiIntent.SearchAyahs(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search ayah text, Arabic or translation…", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Quran") },
            trailingIcon = {
                if (state.searchQuery.isNotBlank()) {
                    IconButton(onClick = { vm.onIntent(QuranUiIntent.ClearSearch) }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        if (state.searchQuery.isNotBlank()) {
            SearchResults(
                isSearching = state.isSearching,
                results = state.searchResults,
                onResultClick = { result ->
                    vm.onIntent(QuranUiIntent.ClearSearch)
                    vm.onIntent(QuranUiIntent.SelectSurahAtAyah(result.surahNumber, result.ayahNumber))
                    selectedAyahNumber = result.ayahNumber
                    vm.onIntent(QuranUiIntent.SelectAyahForTafseer(result.ayahNumber))
                }
            )
            return@Column
        }

        // 3. Controls Row: Translation Chip + Hifz Mode + Studio Shortcut
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .weight(1f)
                    .clickable { studioSheetTab = 1 }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = state.selectedTranslationLanguage,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (state.isLoadingTranslation) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (hifzOverlayActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.clickable { hifzOverlayActive = !hifzOverlayActive }
            ) {
                Text(
                    text = if (hifzOverlayActive) "Hifz: On" else "Hifz: Off",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hifzOverlayActive) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        // Friendly offline / download error card with Retry
        state.errorMessage?.let { errorMsg ->
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
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { vm.onIntent(QuranUiIntent.RetrySync) }) {
                        Text("Retry", fontSize = 11.sp)
                    }
                }
            }
        }

        state.contentNotice?.let { notice ->
            SakinahCard(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                contentPadding = 12.dp
            ) {
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 4. Reading Canvas: Study (verse-by-verse) vs Continuous RTL Mus-haf
        if (state.isStudyMode) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(surah.ayahs, key = { "${surah.number}:${it.numberInSurah}" }) { ayah ->
                    val hifzTag = state.hifzMistakes[surah.number to ayah.numberInSurah]
                    AyahCard(
                        ayah = ayah,
                        isSelected = selectedAyahNumber == ayah.numberInSurah,
                        translationIsRtl = isRtlLanguage(state.selectedTranslationIsoCode),
                        hifzOverlayActive = hifzOverlayActive,
                        hifzTag = hifzTag,
                        onTagMistake = { tag ->
                            vm.onIntent(
                                QuranUiIntent.SetHifzMistakeTag(
                                    surah = surah.number,
                                    ayah = ayah.numberInSurah,
                                    wordPos = 0,
                                    tag = tag
                                )
                            )
                        },
                        onClick = { selectedAyahNumber = ayah.numberInSurah },
                        onTafseerClick = {
                            selectedAyahNumber = ayah.numberInSurah
                            vm.onIntent(QuranUiIntent.SelectAyahForTafseer(ayah.numberInSurah))
                            studioSheetTab = 2
                        }
                    )
                }
            }
        } else {
            // Continuous Mus-haf RTL Page Flow
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SakinahCard(contentPadding = 18.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "سورة ${surah.nameArabic} · ${surah.nameEnglish}",
                            fontFamily = UthmanicFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (surah.number != 1 && surah.number != 9) {
                            Text(
                                text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                                fontFamily = UthmanicFontFamily,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        val accent = MaterialTheme.colorScheme.primary
                        val mushafText = remember(surah.ayahs, selectedAyahNumber, accent) {
                            buildAnnotatedString {
                                surah.ayahs.forEachIndexed { index, ayah ->
                                    if (index > 0) append(" ")
                                    val isHighlighted = ayah.numberInSurah == selectedAyahNumber
                                    if (isHighlighted) {
                                        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
                                            append(ayah.arabicText)
                                        }
                                    } else {
                                        append(ayah.arabicText)
                                    }
                                    val digits = ArabicNormalizer.toArabicIndicNumerals(ayah.numberInSurah)
                                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
                                        append(" ﴿$digits﴾")
                                    }
                                }
                            }
                        }

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Text(
                                text = mushafText,
                                modifier = Modifier.fillMaxWidth(),
                                fontFamily = UthmanicFontFamily,
                                fontSize = 25.sp,
                                lineHeight = 44.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Justify
                            )
                        }
                    }
                }

                // Quick Ayah Tafsir selector strip under Mus-haf page
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    surah.ayahs.forEach { ayah ->
                        val active = ayah.numberInSurah == selectedAyahNumber
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable {
                                selectedAyahNumber = ayah.numberInSurah
                                vm.onIntent(QuranUiIntent.SelectAyahForTafseer(ayah.numberInSurah))
                                studioSheetTab = 2
                            }
                        ) {
                            Text(
                                text = "Ayah ${ayah.numberInSurah}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // 5. Consolidated Tabbed Reader Studio Sheet (Surah | Translation | Tafsir)
    if (studioSheetTab >= 0) {
        val ayah = surah.ayahs.find { it.numberInSurah == selectedAyahNumber }
        val filteredTranslations = remember(state.availableTranslations, translationQuery) {
            state.availableTranslations.filter { item ->
                translationQuery.isBlank() ||
                    item.name.contains(translationQuery, true) ||
                    item.languageName.contains(translationQuery, true) ||
                    item.authorName.contains(translationQuery, true)
            }
        }

        ModalBottomSheet(
            onDismissRequest = { studioSheetTab = -1 },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quran Study Studio",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { studioSheetTab = -1 }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                SegmentedPillBar(
                    options = listOf("Surah", "Translation", "Tafsir"),
                    selectedIndex = studioSheetTab.coerceIn(0, 2),
                    onSelect = { studioSheetTab = it }
                )

                when (studioSheetTab) {
                    0 -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                        ) {
                            items(state.surahs, key = { it.number }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            vm.onIntent(QuranUiIntent.SelectSurah(item.number))
                                            selectedAyahNumber = 1
                                            studioSheetTab = -1
                                        }
                                        .padding(horizontal = 8.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${item.number}",
                                        modifier = Modifier.width(34.dp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.nameEnglish,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${item.ayahCount} ayat · ${item.revelationType}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = item.nameArabic,
                                        fontFamily = UthmanicFontFamily,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        OutlinedTextField(
                            value = translationQuery,
                            onValueChange = { translationQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search language or translator") },
                            singleLine = true
                        )
                        if (state.isLoadingTranslation) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                        ) {
                            items(filteredTranslations, key = { it.id }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            vm.onIntent(
                                                QuranUiIntent.SelectTranslation(
                                                    translationId = item.id,
                                                    languageName = item.languageName.ifBlank { item.name },
                                                    isoCode = item.isoCode
                                                )
                                            )
                                            studioSheetTab = -1
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${item.languageName.ifBlank { item.isoCode }} · ${item.authorName}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (state.selectedTranslationId == item.id) {
                                        Icon(Icons.Default.Translate, contentDescription = null, tint = MintGreen)
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 440.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "${surah.nameEnglish} · Ayah $selectedAyahNumber",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("Ibn Kathir", "Al-Tabari", "Al-Qurtubi", "Al-Sa'di", "Ma'ariful Quran").forEach { scholar ->
                                    val selected = state.selectedScholar == scholar
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.clickable {
                                            vm.onIntent(QuranUiIntent.SelectScholar(scholar))
                                        }
                                    ) {
                                        Text(
                                            text = scholar,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = state.selectedTafseerContent.ifBlank {
                                    "No cached commentary for this ayah in ${state.selectedScholar}. " +
                                        "Download the tafseer pack to read it offline."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            ayah?.hadithProofQuote?.let { quote ->
                                SakinahCard(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    contentPadding = 12.dp
                                ) {
                                    Row {
                                        Icon(Icons.Default.FormatQuote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = quote,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    isSearching: Boolean,
    results: List<AyahSearchResult>,
    onResultClick: (AyahSearchResult) -> Unit
) {
    if (isSearching && results.isEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp))
        }
        return
    }

    if (results.isEmpty()) {
        Text(
            text = "No matching ayah in the offline database.",
            modifier = Modifier.padding(vertical = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(results) { result ->
            SakinahCard(
                onClick = { onResultClick(result) },
                contentPadding = 14.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${result.surahName.ifBlank { "Surah ${result.surahNumber}" }} · ${result.surahNumber}:${result.ayahNumber}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ArabicVerseBlock(
                        arabicText = result.textUthmani,
                        fontSizeSp = 19,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (result.translation.isNotBlank()) {
                        Text(
                            text = result.translation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AyahCard(
    ayah: AyahModel,
    isSelected: Boolean,
    translationIsRtl: Boolean = false,
    hifzOverlayActive: Boolean = false,
    hifzTag: String? = null,
    onTagMistake: (String) -> Unit = {},
    onClick: () -> Unit,
    onTafseerClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MintGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    StatusBadge(
                        text = "Ayah ${ayah.numberInSurah}",
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    if (!hifzTag.isNullOrBlank()) {
                        StatusBadge(
                            text = hifzTag,
                            accentColor = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                FilledTonalButton(onClick = onTafseerClick) {
                    Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tafseer", fontSize = 11.sp)
                }
            }

            val marker = ArabicNormalizer.toArabicIndicNumerals(ayah.numberInSurah)
            ArabicVerseBlock(
                arabicText = "${ayah.arabicText} ﴿$marker﴾",
                fontSizeSp = 24,
                color = MaterialTheme.colorScheme.onSurface,
                isQuran = true
            )

            if (ayah.transliteration.isNotBlank()) {
                Text(
                    text = ayah.transliteration,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (ayah.translation.isNotBlank()) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (translationIsRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Text(
                        text = ayah.translation,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Serif,
                        textAlign = if (translationIsRtl) TextAlign.End else TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (hifzOverlayActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Tajweed", "Mutashabihat", "Memory Slip").forEach { tag ->
                        val active = hifzTag == tag
                        TextButton(
                            onClick = { onTagMistake(if (active) "" else tag) }
                        ) {
                            Text(
                                text = if (active) "✓ $tag" else "+ $tag",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun isRtlLanguage(isoCode: String): Boolean =
    isoCode.lowercase() in setOf("ar", "ur", "fa", "ps", "sd", "ug")
