package com.sakinah.deen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakinah.deen.data.model.AyahModel
import com.sakinah.deen.ui.theme.EmeraldDeep
import com.sakinah.deen.ui.theme.MintGreen
import com.sakinah.deen.ui.viewmodel.AyahSearchResult
import com.sakinah.deen.ui.viewmodel.QuranUiIntent
import com.sakinah.deen.ui.viewmodel.QuranViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranScreen(
    initialSurahNumber: Int = 1,
    initialAyahNumber: Int = 1,
    modifier: Modifier = Modifier
) {
    val viewModel: QuranViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsState()
    val surah = state.currentSurah

    var isTafseerSheetOpen by remember { mutableStateOf(false) }
    var isSurahPickerOpen by remember { mutableStateOf(false) }
    var selectedAyahNumber by remember { mutableStateOf(initialAyahNumber) }
    var hifzOverlayActive by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState()

    // Deep links (e.g. from the AI scholar) select a chapter once.
    LaunchedEffect(initialSurahNumber, initialAyahNumber) {
        viewModel.onIntent(QuranUiIntent.SelectSurah(initialSurahNumber))
        selectedAyahNumber = initialAyahNumber
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Top Navigation & Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.clickable { isSurahPickerOpen = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${surah.number}. ${surah.nameEnglish}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = EmeraldDeep
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${surah.nameArabic})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Layout toggle: Mus-haf vs Verse-by-Verse
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(modifier = Modifier.padding(2.dp)) {
                    Button(
                        onClick = { if (!state.isStudyMode) viewModel.onIntent(QuranUiIntent.ToggleLayoutMode) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isStudyMode) EmeraldDeep else Color.Transparent,
                            contentColor = if (state.isStudyMode) Color.White else EmeraldDeep
                        )
                    ) {
                        Text("Study", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { if (state.isStudyMode) viewModel.onIntent(QuranUiIntent.ToggleLayoutMode) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!state.isStudyMode) EmeraldDeep else Color.Transparent,
                            contentColor = if (!state.isStudyMode) Color.White else EmeraldDeep
                        )
                    ) {
                        Text("Mus-haf", fontSize = 11.sp)
                    }
                }
            }
        }

        // 2. Offline search over the encrypted Quran cache
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.onIntent(QuranUiIntent.SearchAyahs(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search ayah text or translation…", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Quran") },
            trailingIcon = {
                if (state.searchQuery.isNotBlank()) {
                    IconButton(onClick = { viewModel.onIntent(QuranUiIntent.ClearSearch) }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            shape = RoundedCornerShape(18.dp),
            singleLine = true
        )

        if (state.searchQuery.isNotBlank()) {
            SearchResults(
                isSearching = state.isSearching,
                results = state.searchResults,
                onResultClick = { result ->
                    viewModel.onIntent(QuranUiIntent.ClearSearch)
                    viewModel.onIntent(QuranUiIntent.SelectSurah(result.surahNumber))
                    selectedAyahNumber = result.ayahNumber
                    viewModel.onIntent(QuranUiIntent.SelectAyahForTafseer(result.ayahNumber))
                }
            )
            return@Column
        }

        // 3. Hifz Overlay toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hifz Memorization Marker Canvas",
                fontSize = 12.sp,
                color = EmeraldDeep,
                fontWeight = FontWeight.SemiBold
            )
            Button(
                onClick = { hifzOverlayActive = !hifzOverlayActive },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hifzOverlayActive) MintGreen else Color.LightGray.copy(alpha = 0.3f)
                )
            ) {
                Text(if (hifzOverlayActive) "Active" else "Off", fontSize = 11.sp, color = EmeraldDeep)
            }
        }

        state.contentNotice?.let { notice ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MintGreen.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, MintGreen.copy(alpha = 0.35f))
            ) {
                Text(
                    text = notice,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = EmeraldDeep
                )
            }
        }

        // 4. Reading Canvas (verse by verse)
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(surah.ayahs) { ayah ->
                AyahCard(
                    ayah = ayah,
                    isSelected = selectedAyahNumber == ayah.numberInSurah,
                    onClick = { selectedAyahNumber = ayah.numberInSurah },
                    onTafseerClick = {
                        selectedAyahNumber = ayah.numberInSurah
                        viewModel.onIntent(QuranUiIntent.SelectAyahForTafseer(ayah.numberInSurah))
                        isTafseerSheetOpen = true
                    }
                )
            }
        }
    }

    // 5. Surah list browser
    if (isSurahPickerOpen) {
        ModalBottomSheet(onDismissRequest = { isSurahPickerOpen = false }) {
            Text(
                text = "Surah Index",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = EmeraldDeep
            )
            LazyColumn(modifier = Modifier.padding(bottom = 24.dp)) {
                items(state.surahs) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.onIntent(QuranUiIntent.SelectSurah(item.number))
                                selectedAyahNumber = 1
                                isSurahPickerOpen = false
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${item.number}",
                            modifier = Modifier.width(32.dp),
                            fontWeight = FontWeight.Bold,
                            color = EmeraldDeep
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item.nameEnglish, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                text = "${item.ayahCount} ayat · ${item.revelationType}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Text(text = item.nameArabic, fontSize = 14.sp, color = EmeraldDeep)
                    }
                }
            }
        }
    }

    // 6. Tafseer bottom sheet (cached commentary only)
    if (isTafseerSheetOpen) {
        val ayah = surah.ayahs.find { it.numberInSurah == selectedAyahNumber }
        ModalBottomSheet(
            onDismissRequest = { isTafseerSheetOpen = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sunni Tafseer Studio · Ayah $selectedAyahNumber",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = EmeraldDeep
                    )
                    IconButton(onClick = { isTafseerSheetOpen = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Ibn Kathir", "Al-Tabari", "Al-Qurtubi", "Al-Sa'di", "Ma'ariful Quran").forEach { scholar ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (state.selectedScholar == scholar) EmeraldDeep else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (state.selectedScholar == scholar) EmeraldDeep else MaterialTheme.colorScheme.outline),
                            modifier = Modifier.clickable {
                                viewModel.onIntent(QuranUiIntent.SelectScholar(scholar))
                            }
                        ) {
                            Text(
                                text = scholar,
                                color = if (state.selectedScholar == scholar) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                )

                ayah?.hadithProofQuote?.let { quote ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MintGreen.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, MintGreen.copy(alpha = 0.3f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            Icon(Icons.Default.FormatQuote, contentDescription = null, tint = EmeraldDeep)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = quote,
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldDeep
                            )
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(results) { result ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onResultClick(result) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${result.surahName.ifBlank { "Surah ${result.surahNumber}" }} · ${result.surahNumber}:${result.ayahNumber}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldDeep
                    )
                    Text(
                        text = result.textUthmani,
                        fontFamily = FontFamily.Serif,
                        fontSize = 18.sp,
                        color = EmeraldDeep,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (result.translation.isNotBlank()) {
                        Text(
                            text = result.translation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
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
    onClick: () -> Unit,
    onTafseerClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MintGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MintGreen else MaterialTheme.colorScheme.outline
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
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MintGreen.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Ayah ${ayah.numberInSurah}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldDeep
                    )
                }

                FilledTonalButton(onClick = onTafseerClick) {
                    Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tafseer", fontSize = 11.sp)
                }
            }

            Text(
                text = "${ayah.arabicText} ۝${ayah.numberInSurah}",
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = EmeraldDeep,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )

            if (ayah.transliteration.isNotBlank()) {
                Text(
                    text = ayah.transliteration,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (ayah.translation.isNotBlank()) {
                Text(
                    text = ayah.translation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
