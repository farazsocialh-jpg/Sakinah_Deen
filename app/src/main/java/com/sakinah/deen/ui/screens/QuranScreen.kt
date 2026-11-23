package com.sakinah.deen.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.R
import com.sakinah.deen.data.model.AyahModel
import com.sakinah.deen.data.model.QuranArabicScript
import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.data.prefs.QuranReadingLayout
import com.sakinah.deen.data.prefs.QuranReadingMode
import com.sakinah.deen.ui.components.ArabicVerseBlock
import com.sakinah.deen.ui.components.QuranOfflineStatusBanner
import com.sakinah.deen.ui.components.SakinahCard
import com.sakinah.deen.ui.components.UnifiedReaderPill
import com.sakinah.deen.ui.components.SegmentedPillBar
import com.sakinah.deen.ui.components.StatusBadge
import com.sakinah.deen.ui.rememberInject

import com.sakinah.deen.ui.theme.UthmanicFontFamily
import com.sakinah.deen.ui.viewmodel.AyahSearchResult
import com.sakinah.deen.ui.viewmodel.QuranUiIntent
import com.sakinah.deen.ui.viewmodel.QuranViewModel
import com.sakinah.deen.util.ArabicNormalizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranScreen(
    modifier: Modifier = Modifier,
    initialSurahNumber: Int = 1,
    initialAyahNumber: Int = 1,
    deepLinkNonce: Int = 0,
    focusMode: Boolean = false,
    viewModel: QuranViewModel? = null
) {
    val vm: QuranViewModel = viewModel ?: koinViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val surah = state.currentSurah
    // The reader renders the selected Arabic script without re-querying Room.
    val displaySurah = remember(surah, state.arabicScript) {
        if (surah.ayahs.isEmpty()) {
            surah
        } else {
            surah.copy(
                ayahs = surah.ayahs.map { ayah ->
                    ayah.copy(arabicText = ayah.arabicFor(state.arabicScript))
                }
            )
        }
    }

    // The flowing mus-haf page is chunked so only visible paragraphs compose: building one
    // AnnotatedString for all of Al-Baqarah froze the reader on every recomposition.
    val mushafParagraphs = remember(displaySurah.ayahs) {
        displaySurah.ayahs.chunked(MUSHAF_PARAGRAPH_AYAHS).map { chunk ->
            MushafParagraph(firstAyah = chunk.first().numberInSurah, ayahs = chunk)
        }
    }

    var studioSheetTab by remember { mutableIntStateOf(-1) } // -1 closed; 0=Surah, 1=Translation, 2=Tafsir
    var translationQuery by remember { mutableStateOf("") }
    var selectedAyahNumber by remember { mutableIntStateOf(initialAyahNumber) }
    var highlightedAyahNumber by remember { mutableIntStateOf(0) }
    var hifzOverlayActive by remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var showQuranOptions by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    // 0 means no ayah action bar; any other value is the ayah it acts on.
    var actionBarAyah by remember { mutableIntStateOf(0) }

    val appearancePrefs = rememberInject<AppearancePreferences>()
    val appearanceConfig by appearancePrefs.effectiveConfig.collectAsStateWithLifecycle()
    val readingLayout = appearanceConfig.quranReadingLayout
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Both presentations prepend a header item in the continuous page; the verse layouts do not.
    val usesHeaderItem = !state.isStudyMode || readingLayout == QuranReadingLayout.CONTINUOUS_MUSHAF

    LaunchedEffect(focusMode) {
        // Focus mode reads the flowing page; the reader branch follows the same state value,
        // so the request has to go through the view model rather than a local flag.
        if (focusMode && state.readingMode != QuranReadingMode.MUSHAF) {
            vm.onIntent(QuranUiIntent.SetReadingMode(QuranReadingMode.MUSHAF))
        }
        if (focusMode) {
            hifzOverlayActive = false
        }
    }

    LaunchedEffect(initialSurahNumber, initialAyahNumber, deepLinkNonce) {
        if (deepLinkNonce > 0 || initialSurahNumber != 1 || initialAyahNumber != 1) {
            // A deep link always resolves to a visible verse row.
            if (state.readingMode != QuranReadingMode.STUDY) {
                vm.onIntent(QuranUiIntent.SetReadingMode(QuranReadingMode.STUDY))
            }
            // An explicit jump wins over the position left by the previous session.
            vm.onIntent(QuranUiIntent.ClearRestoreScrollIndex)
            vm.onIntent(QuranUiIntent.SelectSurahAtAyah(initialSurahNumber, initialAyahNumber))
            selectedAyahNumber = initialAyahNumber
        }
    }

    LaunchedEffect(
        state.scrollTargetAyah,
        surah.number,
        surah.ayahs.size,
        state.readingMode,
        readingLayout
    ) {
        val target = state.scrollTargetAyah ?: return@LaunchedEffect
        val idx = surah.ayahs.indexOfFirst { it.numberInSurah == target }
        if (idx >= 0) {
            selectedAyahNumber = target
            actionBarAyah = target
            val itemIndex = if (usesHeaderItem) idx + 1 else idx
            listState.animateScrollToItem(itemIndex)
            highlightedAyahNumber = target
            vm.onIntent(QuranUiIntent.ClearScrollTarget)
            delay(2000)
            if (highlightedAyahNumber == target) highlightedAyahNumber = 0
        }
    }

    // Reopen exactly where the last session stopped; applied once, then cleared. The stored
    // index is only used for the chapter it belongs to, so a placeholder page cannot eat it.
    LaunchedEffect(
        state.restoreScrollIndex,
        state.restoreSurahNumber,
        surah.number,
        surah.ayahs.size,
        usesHeaderItem
    ) {
        val target = state.restoreScrollIndex ?: return@LaunchedEffect
        if (state.restoreSurahNumber != surah.number || surah.ayahs.isEmpty()) {
            return@LaunchedEffect
        }
        val maxIndex = surah.ayahs.size + if (usesHeaderItem) 1 else 0
        listState.scrollToItem(target.coerceIn(0, maxIndex))
        vm.onIntent(QuranUiIntent.ClearRestoreScrollIndex)
    }

    // Persist the position when a scroll settles. The first emission is the idle state at
    // composition, so it is dropped to avoid overwriting the restored index with 0.
    LaunchedEffect(listState, surah.number, surah.ayahs.size, state.readingMode, readingLayout) {
        if (surah.ayahs.isEmpty()) return@LaunchedEffect
        val headerItems = if (usesHeaderItem) 1 else 0
        snapshotFlow { listState.isScrollInProgress }
            .drop(1)
            .filter { !it }
            .collect {
                val index = listState.firstVisibleItemIndex
                val ayahNumber = (index - headerItems + 1).coerceIn(1, surah.ayahs.size)
                vm.onIntent(QuranUiIntent.SaveReadingPosition(surah.number, ayahNumber, index))
            }
    }

    val translationIsRtl = isRtlLanguage(state.selectedTranslationIsoCode)
    val ayahUiState = QuranAyahUiState(
        selectedAyahNumber = selectedAyahNumber,
        highlightedAyahNumber = highlightedAyahNumber,
        hifzOverlayActive = hifzOverlayActive,
        hifzMistakes = state.hifzMistakes,
        translationIsRtl = translationIsRtl,
        arabicFontSizeSp = state.arabicFontSize,
        focusMode = focusMode,
        bookmarkedAyahNumbers = state.bookmarkedAyahNumbers
    )
    val onAyahClick: (AyahModel) -> Unit = { ayah ->
        selectedAyahNumber = ayah.numberInSurah
        actionBarAyah = ayah.numberInSurah
        vm.onIntent(
            QuranUiIntent.SaveReadingPosition(
                surah.number,
                ayah.numberInSurah,
                listState.firstVisibleItemIndex
            )
        )
    }
    val onTafseerClick: (AyahModel) -> Unit = { ayah ->
        selectedAyahNumber = ayah.numberInSurah
        vm.onIntent(QuranUiIntent.SelectAyahForTafseer(ayah.numberInSurah))
        studioSheetTab = 2
    }
    val onTagMistakeClick: (AyahModel, String) -> Unit = { ayah, tag ->
        vm.onIntent(
            QuranUiIntent.SetHifzMistakeTag(
                surah = surah.number,
                ayah = ayah.numberInSurah,
                wordPos = 0,
                tag = tag
            )
        )
    }
    val haptic = LocalHapticFeedback.current
    val onToggleBookmark: (AyahModel) -> Unit = { ayah ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        vm.onIntent(QuranUiIntent.ToggleBookmark(surah.number, ayah.numberInSurah))
    }
    val actionAyah = surah.ayahs.firstOrNull { it.numberInSurah == actionBarAyah }
    val ayahShareBody = actionAyah?.let { ayah ->
        listOfNotNull(
            cleanQuranArabic(ayah.arabicText),
            ayah.translation.takeIf { it.isNotBlank() },
            context.getString(R.string.quran_reference, surah.number, ayah.numberInSurah)
        ).joinToString("\n\n")
    }
    val copyActionAyah: () -> Unit = {
        ayahShareBody?.let { clipboard.setText(AnnotatedString(it)) }
    }
    val shareActionAyah: () -> Unit = {
        ayahShareBody?.let { body ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, body)
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.quran_share_subject))
            }
            context.startActivity(
                Intent.createChooser(send, context.getString(R.string.quran_action_share))
            )
        }
    }
    val toggleStudyMode: () -> Unit = {
        vm.onIntent(QuranUiIntent.ToggleReadingMode)
    }

    Box(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (readingLayout == QuranReadingLayout.THUMB_BOTTOM_DOCK) {
                    Text(
                        text = "${surah.number}. ${surah.nameEnglish} · ${surah.revelationType} · ${surah.ayahCount} Ayahs",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (!focusMode) {
                    // Null until Room reports coverage: no "missing pack" flash on launch.
                    // A complete pack stays silent — the banner is only for genuinely
                    // missing or partial coverage, whose details also live in the options sheet.
                    state.offlineStatus
                        ?.takeIf { !it.isComplete }
                        ?.let { QuranOfflineStatusBanner(status = it) }
                }

                if (searchExpanded || state.searchQuery.isNotBlank()) {
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
                            } else {
                                IconButton(onClick = { searchExpanded = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close search")
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                }

                state.errorMessage?.let { errorMsg ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
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

                if (searchExpanded && state.searchQuery.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        SearchResults(
                            isSearching = state.isSearching,
                            results = state.searchResults,
                            onResultClick = { result ->
                                vm.onIntent(QuranUiIntent.ClearSearch)
                                vm.onIntent(
                                    QuranUiIntent.SelectSurahAtAyah(
                                        result.surahNumber,
                                        result.ayahNumber
                                    )
                                )
                                selectedAyahNumber = result.ayahNumber
                                searchExpanded = false
                            }
                        )
                    }
                } else if (state.isStudyMode) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        QuranReadingLayoutContent(
                            layout = readingLayout,
                            surah = displaySurah,
                            state = ayahUiState,
                            onAyahClick = onAyahClick,
                            onTafseer = onTafseerClick,
                            onTagMistake = onTagMistakeClick,
                            onToggleBookmark = onToggleBookmark,
                            onToggleStudyMode = toggleStudyMode,
                            listState = listState
                        )
                    }
                } else {
                    // The mus-haf page used to build one AnnotatedString for the whole
                    // surah and compose a chip per ayah, which janked on long surahs.
                    // Paragraph chunks are lazily composed with stable keys instead.
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "mushaf-surah-card", contentType = "header") {
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
                                            lineHeight = 40.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        items(
                            items = mushafParagraphs,
                            key = { paragraph -> "mushaf-${paragraph.firstAyah}" },
                            contentType = { "paragraph" }
                        ) { paragraph ->
                            MushafParagraphText(
                                paragraph = paragraph,
                                selectedAyahNumber = selectedAyahNumber,
                                fontSizeSp = state.arabicFontSize
                            )
                        }

                        if (!focusMode) {
                            item(key = "mushaf-ayah-chips", contentType = "chips") {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(displaySurah.ayahs, key = { it.numberInSurah }) { ayah ->
                                        val active = ayah.numberInSurah == selectedAyahNumber
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (active) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.clickable {
                                                // Selecting from the chip bar is how the mushaf page
                                                // reaches one ayah's actions without per-ayah rows.
                                                selectedAyahNumber = ayah.numberInSurah
                                                actionBarAyah = ayah.numberInSurah
                                                vm.onIntent(
                                                    QuranUiIntent.SaveReadingPosition(
                                                        surah.number,
                                                        ayah.numberInSurah,
                                                        listState.firstVisibleItemIndex
                                                    )
                                                )
                                            }
                                        ) {
                                            Text(
                                                text = stringResource(
                                                    R.string.quran_ayah_chip,
                                                    ayah.numberInSurah
                                                ),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (active) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(
                                                    horizontal = 10.dp,
                                                    vertical = 6.dp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!focusMode && actionAyah != null) {
                AyahActionBar(
                    ayahNumber = actionAyah.numberInSurah,
                    bookmarked = actionBarAyah in state.bookmarkedAyahNumbers,
                    onBookmark = { onToggleBookmark(actionAyah) },
                    onCopy = copyActionAyah,
                    onShare = shareActionAyah,
                    onTafseer = { onTafseerClick(actionAyah) },
                    onClose = { actionBarAyah = 0 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 72.dp)
                )
            }

            UnifiedReaderPill(
                title = "${surah.number}. ${surah.nameEnglish} ▾",
                onTitleClick = { studioSheetTab = 0 },
                onMenuClick = { showQuranOptions = true },
                menuContentDescription = stringResource(R.string.quran_options_title),
                modifier = Modifier.align(Alignment.BottomCenter),
                actions = {
                    ReaderPillLayoutMenu(
                        selected = readingLayout,
                        onSelect = { appearancePrefs.updateQuranReadingLayout(it) }
                    )
                    ReaderPillModeButton(
                        studyMode = state.isStudyMode,
                        onClick = { vm.onIntent(QuranUiIntent.ToggleReadingMode) }
                    )
                }
            )
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
                        Text(
                            text = "Arabic script",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SegmentedPillBar(
                            options = QuranArabicScript.entries.map { it.label },
                            selectedIndex = state.arabicScript.ordinal,
                            onSelect = { index ->
                                vm.onIntent(
                                    QuranUiIntent.SelectArabicScript(
                                        QuranArabicScript.entries[index]
                                    )
                                )
                            }
                        )
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
                        if (!state.isLoadingTranslation && filteredTranslations.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (state.availableTranslations.isEmpty()) {
                                        "Translation catalog could not be loaded. Check your connection and retry."
                                    } else {
                                        "No translations match “$translationQuery”."
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                OutlinedButton(
                                    onClick = { vm.onIntent(QuranUiIntent.LoadTranslations) }
                                ) {
                                    Text("Retry", fontSize = 11.sp)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp)
                            ) {
                                items(
                                    filteredTranslations,
                                    key = {
                                        "${it.id}_${it.isoCode}_${it.name}_${it.authorName}_${it.editionKey}"
                                    }
                                ) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                vm.onIntent(
                                                    QuranUiIntent.SelectTranslation(
                                                        translationId = item.id,
                                                        languageName = item.languageName.ifBlank { item.name },
                                                        isoCode = item.isoCode,
                                                        editionKey = item.editionKey,
                                                        source = item.source
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
                                                text = "${item.languageName.ifBlank { item.isoCode }} · ${item.authorName.ifBlank { item.editionKey }}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (state.selectedTranslationId == item.id) {
                                            Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
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
                                listOf(
                                    "Ibn Kathir",
                                    "Al-Tabari",
                                    "Al-Qurtubi",
                                    "Al-Sa'di",
                                    "Ma'ariful Quran"
                                ).forEach { scholar ->
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

                            if (state.selectedTafseerContent.isBlank()) {
                                val packId = tafseerPackIdFor(state.selectedScholar)
                                if (state.isDownloadingTafseer) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Text(
                                                text = "Downloading ${state.selectedScholar} from Quran.com · " +
                                                    "${(state.tafseerDownloadProgress * 100).toInt()}%",
                                                fontSize = 12.sp
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { state.tafseerDownloadProgress },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                } else if (packId != null) {
                                    Button(
                                        onClick = { vm.onIntent(QuranUiIntent.DownloadTafseerPack(packId)) },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Download ${state.selectedScholar} Pack", fontSize = 12.sp)
                                    }
                                }
                            }

                            state.tafseerErrorMessage?.let { error ->
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            state.errorMessage?.let { error ->
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

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

    if (showQuranOptions) {
        ModalBottomSheet(onDismissRequest = { showQuranOptions = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.quran_options_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = { searchExpanded = true; showQuranOptions = false }) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.quran_options_search))
                }
                // Reading mode and layout are both selectable here and in the pill bar.
                Text(
                    text = stringResource(R.string.quran_reading_mode_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                SegmentedPillBar(
                    options = QuranReadingMode.entries.map { it.localizedLabel() },
                    selectedIndex = QuranReadingMode.entries.indexOf(state.readingMode).coerceAtLeast(0),
                    onSelect = { index ->
                        QuranReadingMode.entries.getOrNull(index)?.let {
                            vm.onIntent(QuranUiIntent.SetReadingMode(it))
                        }
                    }
                )
                Text(
                    text = stringResource(R.string.quran_layout_menu_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                SegmentedPillBar(
                    options = QuranReadingLayout.entries.map { it.shortLabel() },
                    selectedIndex = QuranReadingLayout.entries.indexOf(readingLayout),
                    onSelect = { index ->
                        QuranReadingLayout.entries.getOrNull(index)?.let {
                            appearancePrefs.updateQuranReadingLayout(it)
                        }
                    }
                )
                TextButton(onClick = { showBookmarks = true; showQuranOptions = false }) {
                    Icon(Icons.Default.Bookmark, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.quran_options_bookmarks))
                }
                TextButton(onClick = { studioSheetTab = 1; showQuranOptions = false }) {
                    Icon(Icons.Default.Translate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.quran_options_translation))
                }
                TextButton(onClick = { studioSheetTab = 2; showQuranOptions = false }) {
                    Icon(Icons.Default.Book, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.quran_options_tafseer))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.quran_options_hifz),
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(
                        checked = hifzOverlayActive,
                        onCheckedChange = { hifzOverlayActive = it }
                    )
                }
                state.offlineStatus?.let { status ->
                    Text(
                        text = pluralStringResource(
                            R.plurals.quran_offline_compact,
                            status.totalAyahs,
                            status.ayahsAvailable,
                            status.totalAyahs
                        ),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showBookmarks) {
        ModalBottomSheet(onDismissRequest = { showBookmarks = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.quran_bookmarks_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { showBookmarks = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.quran_action_close)
                        )
                    }
                }
                if (state.bookmarks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.quran_bookmarks_empty),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                    ) {
                        items(
                            items = state.bookmarks,
                            key = { "${it.surahNumber}:${it.ayahNumber}" }
                        ) { bookmark ->
                            val surahName = state.surahs
                                .find { it.number == bookmark.surahNumber }
                                ?.nameEnglish
                                ?: stringResource(R.string.quran_surah_chip, bookmark.surahNumber)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showBookmarks = false
                                        selectedAyahNumber = bookmark.ayahNumber
                                        actionBarAyah = bookmark.ayahNumber
                                        vm.onIntent(
                                            QuranUiIntent.SelectSurahAtAyah(
                                                bookmark.surahNumber,
                                                bookmark.ayahNumber
                                            )
                                        )
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(
                                            R.string.quran_bookmark_jump,
                                            bookmark.surahNumber,
                                            bookmark.ayahNumber
                                        ),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "$surahName · " + stringResource(
                                            R.string.quran_ayah_chip,
                                            bookmark.ayahNumber
                                        ),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        vm.onIntent(
                                            QuranUiIntent.ToggleBookmark(
                                                bookmark.surahNumber,
                                                bookmark.ayahNumber
                                            )
                                        )
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(
                                            R.string.quran_bookmark_remove
                                        ),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
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

/** Full, localized preset name used by menus and Settings. */
@Composable
internal fun QuranReadingLayout.localizedLabel(): String = when (this) {
    QuranReadingLayout.DIVIDER_HAIRLINE -> stringResource(R.string.quran_layout_divider_hairline)
    QuranReadingLayout.CONTINUOUS_MUSHAF -> stringResource(R.string.quran_layout_continuous_mushaf)
    QuranReadingLayout.SPLIT_COLUMN -> stringResource(R.string.quran_layout_split_column)
    QuranReadingLayout.THUMB_BOTTOM_DOCK -> stringResource(R.string.quran_layout_thumb_bottom_dock)
}

/** Compact name for the four-option segmented bar, where long labels would wrap. */
@Composable
internal fun QuranReadingLayout.shortLabel(): String = when (this) {
    QuranReadingLayout.DIVIDER_HAIRLINE -> stringResource(R.string.quran_layout_short_hairline)
    QuranReadingLayout.CONTINUOUS_MUSHAF -> stringResource(R.string.quran_layout_short_mushaf)
    QuranReadingLayout.SPLIT_COLUMN -> stringResource(R.string.quran_layout_short_split)
    QuranReadingLayout.THUMB_BOTTOM_DOCK -> stringResource(R.string.quran_layout_short_dock)
}

@Composable
internal fun QuranReadingMode.localizedLabel(): String = when (this) {
    QuranReadingMode.STUDY -> stringResource(R.string.quran_mode_study)
    QuranReadingMode.MUSHAF -> stringResource(R.string.quran_mode_mushaf)
}

/** Layout picker shown inside the bottom reader pill. */
@Composable
private fun ReaderPillLayoutMenu(
    selected: QuranReadingLayout,
    onSelect: (QuranReadingLayout) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true }
        ) {
            Icon(
                imageVector = Icons.Default.ViewColumn,
                contentDescription = stringResource(R.string.quran_pill_layout),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QuranReadingLayout.entries.forEach { layout ->
                DropdownMenuItem(
                    text = { Text(text = layout.localizedLabel(), fontSize = 13.sp) },
                    onClick = {
                        expanded = false
                        onSelect(layout)
                    },
                    leadingIcon = {
                        if (layout == selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Spacer(Modifier.size(16.dp))
                        }
                    }
                )
            }
        }
    }
}

/** One-tap switch between the verse pages and the flowing mushaf page. */
@Composable
private fun ReaderPillModeButton(
    studyMode: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (studyMode) Icons.Default.ViewAgenda
            else Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = stringResource(R.string.quran_pill_reading_mode),
            tint = if (studyMode) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(19.dp)
        )
    }
}

/** Bookmark, copy, share and tafsir actions for the ayah last tapped. */
@Composable
private fun AyahActionBar(
    ayahNumber: Int,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onTafseer: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(horizontal = 12.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(R.string.quran_ayah_chip, ayahNumber),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp, end = 2.dp)
            )
            IconButton(onClick = onBookmark) {
                Icon(
                    imageVector = if (bookmarked) Icons.Default.Bookmark
                    else Icons.Outlined.BookmarkBorder,
                    contentDescription = stringResource(
                        if (bookmarked) R.string.quran_bookmark_remove else R.string.quran_bookmark_add
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.quran_action_copy),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.quran_action_share),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onTafseer) {
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = stringResource(R.string.quran_action_tafseer),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.quran_action_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun QuranUtilityBar(
    surah: com.sakinah.deen.data.model.SurahModel,
    translationIsoCode: String,
    isStudyMode: Boolean,
    hifzOverlayActive: Boolean,
    searchExpanded: Boolean,
    onSurahClick: () -> Unit,
    onToggleStudyMode: () -> Unit,
    onSearchClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onHifzClick: () -> Unit,
    onStudioClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSurahClick),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${surah.number}. ${surah.nameEnglish} ▾",
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = surah.nameArabic,
                    fontFamily = UthmanicFontFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        QuranStudyPill(isStudyMode = isStudyMode, onClick = onToggleStudyMode)
        QuranUtilityIcon(
            icon = Icons.Default.Search,
            description = "Search Quran",
            active = searchExpanded,
            onClick = onSearchClick
        )
        QuranLanguageBadge(isoCode = translationIsoCode, onClick = onLanguageClick)
        QuranUtilityIcon(
            icon = if (hifzOverlayActive) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            description = "Toggle Hifz mode",
            active = hifzOverlayActive,
            onClick = onHifzClick
        )
        QuranUtilityIcon(
            icon = Icons.Default.Book,
            description = "Quran Study Studio",
            onClick = onStudioClick
        )
    }
}

@Composable
private fun QuranStudyPill(
    isStudyMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (isStudyMode) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (isStudyMode) "To Mus-haf" else "To Study",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isStudyMode) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun QuranLanguageBadge(
    isoCode: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = isoCode.uppercase().take(3).ifBlank { "ENG" },
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun QuranUtilityIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false
) {
    Surface(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun QuranThumbDock(
    surah: com.sakinah.deen.data.model.SurahModel,
    translationIsoCode: String,
    isStudyMode: Boolean,
    hifzOverlayActive: Boolean,
    onSurahClick: () -> Unit,
    onToggleStudyMode: () -> Unit,
    onSearchClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onHifzClick: () -> Unit,
    onStudioClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onSurahClick),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "${surah.number}. ${surah.nameEnglish} ▾",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            QuranUtilityIcon(Icons.Default.Search, "Search Quran", onSearchClick)
            QuranLanguageBadge(translationIsoCode, onLanguageClick)
            QuranUtilityIcon(
                icon = if (hifzOverlayActive) Icons.Default.VisibilityOff
                else Icons.Default.Visibility,
                description = "Toggle Hifz mode",
                active = hifzOverlayActive,
                onClick = onHifzClick
            )
            QuranStudyPill(isStudyMode = isStudyMode, onClick = onToggleStudyMode)
            QuranUtilityIcon(Icons.Default.Book, "Quran Study Studio", onStudioClick)
        }
    }
}

@Composable
private fun tafseerPackIdFor(scholar: String): String? =
    com.sakinah.deen.data.repository.TafseerDownloadRepository.packIdForScholar(scholar)

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
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = results,
            key = { "${'$'}{it.surahNumber}:${'$'}{it.ayahNumber}" },
            contentType = { "search-hit" }
        ) { result ->
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
                        arabicText = cleanQuranArabic(result.textUthmani),
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
    isTargetHighlighted: Boolean = false,
    translationIsRtl: Boolean = false,
    arabicFontSizeSp: Int = 24,
    hifzOverlayActive: Boolean = false,
    hifzTag: String? = null,
    showTafseerAction: Boolean = true,
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
            containerColor = when {
                isTargetHighlighted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isTargetHighlighted) 2.5.dp else if (isSelected) 1.5.dp else 1.dp,
            color = if (isTargetHighlighted) {
                MaterialTheme.colorScheme.primary
            } else if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
            }
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

                if (showTafseerAction) {
                    FilledTonalButton(onClick = onTafseerClick) {
                        Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tafseer", fontSize = 11.sp)
                    }
                }
            }

            val marker = ArabicNormalizer.toArabicIndicNumerals(ayah.numberInSurah)
            ArabicVerseBlock(
                arabicText = "${cleanQuranArabic(ayah.arabicText)} ﴿$marker﴾",
                fontSizeSp = arabicFontSizeSp,
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

private const val MUSHAF_PARAGRAPH_AYAHS = 12

/** A lazily composed slice of the flowing mushaf page. */
private data class MushafParagraph(
    val firstAyah: Int,
    val ayahs: List<AyahModel>
)

/**
 * One paragraph of the mushaf. Harakat need room to breathe, hence the 1.9x line height,
 * explicit font padding and vertical inset — the previous 1.7x/trimmed layout clipped the
 * ascenders and descenders of diacritics at small sizes.
 */
@Composable
private fun MushafParagraphText(
    paragraph: MushafParagraph,
    selectedAyahNumber: Int,
    fontSizeSp: Int
) {
    val accent = MaterialTheme.colorScheme.primary
    val text = remember(paragraph, selectedAyahNumber, accent) {
        buildAnnotatedString {
            paragraph.ayahs.forEachIndexed { index, ayah ->
                if (index > 0) append(" ")
                if (ayah.numberInSurah == selectedAyahNumber) {
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
                        append(cleanQuranArabic(ayah.arabicText))
                    }
                } else {
                    append(cleanQuranArabic(ayah.arabicText))
                }
                val digits = ArabicNormalizer.toArabicIndicNumerals(ayah.numberInSurah)
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
                    append(" \uFD3F$digits\uFD3E")
                }
            }
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            style = TextStyle(
                fontFamily = UthmanicFontFamily,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.9f).sp,
                platformStyle = PlatformTextStyle(includeFontPadding = true),
                color = MaterialTheme.colorScheme.onSurface
            ),
            textAlign = TextAlign.Justify
        )
    }
}
