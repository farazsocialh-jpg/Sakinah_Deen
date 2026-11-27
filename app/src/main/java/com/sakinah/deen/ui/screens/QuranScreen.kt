package com.sakinah.deen.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.R
import com.sakinah.deen.data.model.AyahModel
import com.sakinah.deen.data.model.QuranArabicScript
import com.sakinah.deen.data.model.SurahModel
import com.sakinah.deen.data.model.TafsirText
import com.sakinah.deen.network.IslamicDataRepository
import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.data.prefs.QuranReaderPreferences
import com.sakinah.deen.data.prefs.QuranReadingLayout
import com.sakinah.deen.data.prefs.QuranReadingMode
import com.sakinah.deen.data.quran.QulWord
import com.sakinah.deen.ui.components.ArabicVerseBlock
import com.sakinah.deen.ui.components.QuranOfflineStatusBanner
import com.sakinah.deen.ui.components.SakinahCard
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt
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

    var studioSheetTab by remember { mutableIntStateOf(-1) } // -1 closed; 0=Surah, 1=Translation
    var translationQuery by remember { mutableStateOf("") }
    var tafseerLanguageFilter by remember { mutableStateOf("all") }
    var selectedTafsirAyah by remember { mutableStateOf<Int?>(null) }
    var showTafsirBooksSheet by remember { mutableStateOf(false) }
    var selectedAyahNumber by remember { mutableIntStateOf(initialAyahNumber) }
    var highlightedAyahNumber by remember { mutableIntStateOf(0) }
    var hifzOverlayActive by remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var showQuranOptions by remember { mutableStateOf(false) }
    var showWbwLanguageSheet by remember { mutableStateOf(false) }
    var showFontSlider by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var quickJumpQuery by remember { mutableStateOf("") }
    var quickJumpSurah by remember { mutableIntStateOf(surah.number) }
    var scrubberActive by remember { mutableStateOf(false) }
    var scrubberVisible by remember { mutableStateOf(false) }
    val scrubberAlpha by animateFloatAsState(
        targetValue = if (scrubberVisible && !focusMode) 1f else 0f,
        label = "scrubber_alpha"
    )
    var scrubberAyah by remember { mutableIntStateOf(1) }
    var overscrollHandled by remember { mutableStateOf(false) }
    // 0 means no ayah action bar; any other value is the ayah it acts on.
    var actionBarAyah by remember { mutableIntStateOf(0) }
    // Downward reading collapses the dock; upward/top/tap restores the full six actions.
    var dockCollapsed by remember { mutableStateOf(false) }

    val appearancePrefs = rememberInject<AppearancePreferences>()
    val appearanceConfig by appearancePrefs.effectiveConfig.collectAsStateWithLifecycle()
    val readingLayout = appearanceConfig.quranReadingLayout
    val baseDensity = LocalDensity.current
    val readingDensity = Density(
        density = baseDensity.density,
        fontScale = (baseDensity.fontScale * appearanceConfig.fontScale.multiplier).coerceIn(0.8f, 2.0f)
    )
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val readerPrefs = remember(context) { QuranReaderPreferences(context) }
    val sharedTranslationSelection by readerPrefs.selectedTranslation.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val atEndOfSurah by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 &&
                info.visibleItemsInfo.lastOrNull()?.index == info.totalItemsCount - 1
        }
    }

    // Both presentations prepend a header item in the continuous page; the verse layouts do not.
    val usesHeaderItem = !state.isStudyMode

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

    LaunchedEffect(surah.number) {
        quickJumpSurah = surah.number
        actionBarAyah = 0
        dockCollapsed = false
    }

    LaunchedEffect(dockCollapsed) {
        if (dockCollapsed) {
            showFontSlider = false
            actionBarAyah = 0
        }
    }

    LaunchedEffect(showFontSlider) {
        if (showFontSlider) actionBarAyah = 0
    }

    LaunchedEffect(showQuranOptions) {
        if (showQuranOptions) actionBarAyah = 0
    }

    LaunchedEffect(studioSheetTab) {
        if (studioSheetTab >= 0) actionBarAyah = 0
    }
    LaunchedEffect(showTafsirBooksSheet) {
        if (showTafsirBooksSheet) {
            actionBarAyah = 0
            vm.onIntent(QuranUiIntent.RefreshTafseerCatalog)
        }
    }
    LaunchedEffect(selectedTafsirAyah) {
        if (selectedTafsirAyah != null) actionBarAyah = 0
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { it }
            .collect { showFontSlider = false }
    }

    // The vertical scrubber appears only while reading is moving (or being dragged) and
    // fades away shortly after, instead of sitting on the page permanently.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress || scrubberActive }
            .collect { moving ->
                if (moving) {
                    scrubberVisible = true
                } else {
                    delay(900)
                    if (!listState.isScrollInProgress && !scrubberActive) {
                        scrubberVisible = false
                    }
                }
            }
    }

    // Track direction from the lazy list itself so the dock can recede while the reader
    // moves forward, without interfering with the scrubber or the saved scroll position.
    LaunchedEffect(listState, surah.number, state.readingMode, readingLayout, focusMode) {
        var lastIndex = listState.firstVisibleItemIndex
        var lastOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            val atTop = index == 0 && offset < 8
            val delta = (index - lastIndex) * 1_000_000 + (offset - lastOffset)
            dockCollapsed = when {
                atTop -> false
                delta < -4 -> false
                delta > 4 && !showFontSlider && actionBarAyah == 0 && !focusMode -> true
                else -> dockCollapsed
            }
            lastIndex = index
            lastOffset = offset
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
    val tafsirGroups = remember(displaySurah.ayahs.size, state.tafsirByAyah) {
        tafsirAyahGroups(
            ayahCount = displaySurah.ayahs.size,
            availableAyahs = state.tafsirByAyah.filterValues { it.isNotBlank() }.keys
        )
    }
    val ayahUiState = QuranAyahUiState(
        selectedAyahNumber = selectedAyahNumber,
        highlightedAyahNumber = highlightedAyahNumber,
        hifzOverlayActive = hifzOverlayActive,
        hifzMistakes = state.hifzMistakes,
        translationIsRtl = translationIsRtl,
        arabicFontSizeSp = appearanceConfig.arabicFontSize,
        translationFontSizeSp = appearanceConfig.translationFontSize,
        focusMode = focusMode,
        bookmarkedAyahNumbers = state.bookmarkedAyahNumbers,
        wbwEnabled = state.wbwEnabled,
        selectedWbwWordKey = state.selectedQulWord?.wordKey
    )
    val onAyahClick: (AyahModel) -> Unit = { ayah ->
        selectedAyahNumber = ayah.numberInSurah
        if (actionBarAyah == ayah.numberInSurah) {
            actionBarAyah = 0
        } else {
            actionBarAyah = ayah.numberInSurah
            showFontSlider = false
        }
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
        selectedTafsirAyah = ayah.numberInSurah
        vm.onIntent(QuranUiIntent.SelectAyahForTafseer(ayah.numberInSurah))
    }
    val moveSelectedTafsirGroup: (Int) -> Unit = { target ->
        if (surah.ayahs.any { it.numberInSurah == target }) {
            selectedTafsirAyah = target
            selectedAyahNumber = target
            vm.onIntent(QuranUiIntent.SelectAyahForTafseer(target))
        }
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
    val jumpToSurah: (Int) -> Unit = { number ->
        val target = number.coerceIn(1, 114)
        vm.onIntent(QuranUiIntent.SelectSurahAtAyah(target, 1))
        selectedAyahNumber = 1
        studioSheetTab = -1
        overscrollHandled = false
    }
    val jumpToAyah: (Int, Int) -> Unit = { number, ayah ->
        vm.onIntent(QuranUiIntent.SelectSurahAtAyah(number, ayah))
        selectedAyahNumber = ayah
        studioSheetTab = -1
    }
    val previousSurah = (surah.number - 1).coerceAtLeast(1)
    val nextSurah = (surah.number + 1).coerceAtMost(114)
    val nextSurahName = state.surahs.firstOrNull { it.number == nextSurah }?.nameEnglish
        ?: "Surah $nextSurah"

    Box(modifier = modifier.fillMaxSize()) {
            if (state.isLoadingTranslation) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!focusMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StudyChevron(
                            glyph = "‹",
                            description = "Previous Surah",
                            enabled = surah.number > 1,
                            onClick = { jumpToSurah(previousSurah) }
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                                .clickable { studioSheetTab = 0 }
                        ) {
                            Text(
                                text = "${surah.number}. ${surah.nameEnglish} ▾ · ${surah.revelationType} · ${surah.ayahCount} Ayahs",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        StudyChevron(
                            glyph = "›",
                            description = "Next Surah",
                            enabled = surah.number < 114,
                            onClick = { jumpToSurah(nextSurah) }
                        )
                    }
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

                state.contentNotice?.let { notice ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = notice,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
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
                        CompositionLocalProvider(LocalDensity provides readingDensity) {
                            QuranReadingLayoutContent(
                                layout = readingLayout,
                                surah = displaySurah,
                                state = ayahUiState,
                                onAyahClick = onAyahClick,
                                onTafseer = onTafseerClick,
                                onTagMistake = onTagMistakeClick,
                                onToggleBookmark = onToggleBookmark,
                                listState = listState,
                                wbwWords = state.qulWordsByAyah,
                                qulScript = state.qulScript,
                                onWordClick = { word ->
                                    vm.onIntent(QuranUiIntent.SelectQulWord(word.wordKey))
                                },
                                tafsirGroups = tafsirGroups,
                                translationFontSp = appearanceConfig.translationFontSize
                            )
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(scrubberAlpha)
                        ) {
                            FastAyahScrubber(
                                ayahCount = displaySurah.ayahs.size,
                                listState = listState,
                                active = scrubberActive,
                                activeAyah = scrubberAyah,
                                enabled = scrubberVisible && !focusMode,
                                onActiveChange = { scrubberActive = it },
                                onAyahChange = { scrubberAyah = it }
                            )
                        }
                        if (atEndOfSurah && surah.number < 114 && !focusMode) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .pointerInput(nextSurah) {
                                        detectVerticalDragGestures(
                                            onDragEnd = { overscrollHandled = false },
                                            onDragCancel = { overscrollHandled = false }
                                        ) { _, dragAmount ->
                                            if (dragAmount < -6f && !overscrollHandled) {
                                                overscrollHandled = true
                                                jumpToSurah(nextSurah)
                                            }
                                        }
                                    }
                                    .clickable { jumpToSurah(nextSurah) },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                shadowElevation = 4.dp
                            ) {
                                Text(
                                    text = "Pull up or tap for Next Surah: $nextSurahName ↑",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    // The mus-haf page used to build one AnnotatedString for the whole
                    // surah and compose a chip per ayah, which janked on long surahs.
                    // Paragraph chunks are lazily composed with stable keys instead.
                    CompositionLocalProvider(LocalDensity provides readingDensity) {
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
                                        BismillahHeader()
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
                                fontSizeSp = appearanceConfig.arabicFontSize
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

            if (showFontSlider && !focusMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.08f))
                        .clickable { showFontSlider = false }
                )
            }

            if (!focusMode) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val collapseOffset by animateDpAsState(
                        targetValue = if (dockCollapsed) maxOf(0.dp, (maxWidth - 64.dp) / 2) else 0.dp,
                        animationSpec = spring()
                    )
                    AnimatedContent(
                        targetState = dockCollapsed,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .offset(x = collapseOffset),
                        transitionSpec = {
                            (fadeIn(animationSpec = spring()) + scaleIn(
                                initialScale = 0.82f,
                                animationSpec = spring()
                            )) togetherWith
                                (fadeOut(animationSpec = spring()) + scaleOut(
                                    targetScale = 0.82f,
                                    animationSpec = spring()
                                ))
                        }
                    ) { collapsed ->
                        if (collapsed) {
                            Surface(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { dockCollapsed = false }
                                    .semantics { contentDescription = "Expand study controls" },
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                ),
                                shadowElevation = 8.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.MoreHoriz,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        } else {
                            Surface(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                shape = RoundedCornerShape(26.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                ),
                                shadowElevation = 8.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    StudyDockIcon(
                                        description = "Toggle reading layout",
                                        active = false,
                                        onClick = {
                                            val next = if (readingLayout == QuranReadingLayout.SPLIT_COLUMN) {
                                                QuranReadingLayout.THUMB_BOTTOM_DOCK
                                            } else {
                                                QuranReadingLayout.SPLIT_COLUMN
                                            }
                                            appearancePrefs.updateQuranReadingLayout(next)
                                        }
                                    ) { color ->
                                        Icon(
                                            imageVector = if (readingLayout == QuranReadingLayout.SPLIT_COLUMN) {
                                                Icons.Outlined.ViewAgenda
                                            } else {
                                                Icons.Outlined.ViewColumn
                                            },
                                            contentDescription = null,
                                            tint = color,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    StudyDockIcon(
                                        description = stringResource(R.string.quran_pill_reading_mode),
                                        active = state.isStudyMode,
                                        onClick = toggleStudyMode
                                    ) { color ->
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                            contentDescription = null,
                                            tint = color,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    StudyDockIcon(
                                        description = "Toggle Hifz mistake highlighting",
                                        active = hifzOverlayActive,
                                        onClick = { hifzOverlayActive = !hifzOverlayActive }
                                    ) { color ->
                                        Icon(
                                            imageVector = Icons.Outlined.Psychology,
                                            contentDescription = null,
                                            tint = color,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    StudyDockIcon(
                                        description = "Toggle word-by-word",
                                        active = state.wbwEnabled,
                                        onClick = {
                                            vm.onIntent(QuranUiIntent.SetWbwEnabled(!state.wbwEnabled))
                                        }
                                    ) { color ->
                                        Icon(
                                            imageVector = Icons.Outlined.Translate,
                                            contentDescription = null,
                                            tint = color,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    StudyDockIcon(
                                        description = "Adjust reading font size",
                                        active = showFontSlider,
                                        onClick = { showFontSlider = !showFontSlider }
                                    ) { color ->
                                        Icon(
                                            imageVector = Icons.Outlined.FormatSize,
                                            contentDescription = null,
                                            tint = color,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    StudyDockIcon(
                                        description = stringResource(R.string.quran_options_title),
                                        active = showQuranOptions,
                                        onClick = { showQuranOptions = true }
                                    ) { color ->
                                        Icon(
                                            imageVector = Icons.Default.MoreHoriz,
                                            contentDescription = null,
                                            tint = color,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showFontSlider && !focusMode,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 84.dp),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ReaderFontSlidersCard(
                    arabicFontSize = appearanceConfig.arabicFontSize,
                    translationFontSize = appearanceConfig.translationFontSize,
                    onArabicChange = { size ->
                        appearancePrefs.update { it.copy(arabicFontSize = size) }
                    },
                    onTranslationChange = { size ->
                        appearancePrefs.update { it.copy(translationFontSize = size) }
                    }
                )
            }
        }

    // 5. Consolidated Tabbed Reader Studio Sheet (Surah | Translation | Tafsir)
    if (studioSheetTab >= 0) {
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
                        text = if (studioSheetTab == 0) {
                            "Surah & Ayah Quick Jump"
                        } else {
                            "Translation"
                        },
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { studioSheetTab = -1 }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.quran_sheet_close)
                        )
                    }
                }

                when (studioSheetTab) {
                    0 -> {
                        val ayahCountForJump = (
                            state.surahs.firstOrNull { it.number == quickJumpSurah }?.ayahCount
                                ?: if (quickJumpSurah == surah.number) surah.ayahs.size else 0
                            ).coerceAtLeast(0)
                        QuickJumpSheet(
                            query = quickJumpQuery,
                            onQueryChange = { quickJumpQuery = it },
                            surahs = state.surahs,
                            selectedSurah = quickJumpSurah,
                            onSelectSurah = { quickJumpSurah = it },
                            ayahCount = ayahCountForJump,
                            onJump = { number, ayah -> jumpToAyah(number, ayah) },
                            onPrevious = { jumpToSurah(previousSurah) },
                            onNext = { jumpToSurah(nextSurah) },
                            canGoPrevious = surah.number > 1,
                            canGoNext = surah.number < 114
                        )
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
                                        if (state.selectedTranslationId == item.id ||
                                            sharedTranslationSelection.translationId == item.id
                                        ) {
                                            Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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

    // 6. Ayah-group Tafsir sheet: opened only from an ayah header's Tafsir button.
    if (selectedTafsirAyah != null) {
        val ayahNumber = selectedTafsirAyah ?: 0
        val group = tafsirGroups.firstOrNull { ayahNumber in it }
            ?: (ayahNumber..minOf(ayahNumber + 3, surah.ayahs.size.coerceAtLeast(1)))
        val previousGroup = tafsirGroups.lastOrNull { it.last < group.first }
        val nextGroup = tafsirGroups.firstOrNull { it.first > group.last }
        ModalBottomSheet(
            onDismissRequest = { selectedTafsirAyah = null },
            sheetState = sheetState
        ) {
            CompositionLocalProvider(LocalDensity provides readingDensity) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val missingAyahs = group.filter { state.tafsirByAyah[it].isNullOrBlank() }
                LaunchedEffect(
                    state.selectedTafsirId,
                    surah.number,
                    group.first,
                    group.last,
                    state.isFetchingTafsirRange,
                    missingAyahs.joinToString(",")
                ) {
                    if (missingAyahs.isNotEmpty() && !state.isFetchingTafsirRange) {
                        vm.onIntent(
                            QuranUiIntent.FetchTafsirRange(surah.number, missingAyahs)
                        )
                    }
                }
                Text(
                    text = "Tafsir ${group.first}:${group.last}",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Tafsir · ${state.selectedTafseerTitle}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1
                    )
                }
                if (state.isFetchingTafsirRange) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Fetching missing ayahs…",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = { previousGroup?.let { moveSelectedTafsirGroup(it.first) } },
                        enabled = previousGroup != null
                    ) {
                        Text("‹ Previous", fontSize = 11.sp)
                    }
                    TextButton(
                        onClick = { nextGroup?.let { moveSelectedTafsirGroup(it.first) } },
                        enabled = nextGroup != null
                    ) {
                        Text("Next ›", fontSize = 11.sp)
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    group.forEach { number ->
                        val commentary = state.tafsirByAyah[number]
                            ?.takeIf { it.isNotBlank() }
                            ?: if (number == ayahNumber) state.selectedTafseerContent else ""
                        Text(
                            text = "Ayah $number",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        CompositionLocalProvider(
                            LocalLayoutDirection provides if (TafsirText.isRtl(state.selectedTafseerLanguage)) {
                                LayoutDirection.Rtl
                            } else {
                                LayoutDirection.Ltr
                            }
                        ) {
                            Text(
                                text = TafsirText.clean(commentary).ifBlank {
                                    "No commentary cached for ayah $number in ${state.selectedTafseerTitle}."
                                },
                                fontSize = appearanceConfig.translationFontSize.sp,
                                lineHeight = (appearanceConfig.translationFontSize * 1.4f).sp,
                                textAlign = if (TafsirText.isRtl(state.selectedTafseerLanguage)) {
                                    TextAlign.End
                                } else {
                                    TextAlign.Start
                                },
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            }
        }
    }

    // 7. Tafsir Books sheet: opened only from Reader menu → Tafsir (or an inline fallback).
    if (showTafsirBooksSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTafsirBooksSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Tafsir Books",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (state.isDownloadingTafseer) {
                    LinearProgressIndicator(
                        progress = { state.tafseerDownloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = "Tafseer Language",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val languageCodes = listOf("all") +
                    state.allTafseers.map { it.languageCode }.distinct().sorted()
                val effectiveLanguageFilter = if (languageCodes.contains(tafseerLanguageFilter)) {
                    tafseerLanguageFilter
                } else {
                    "all"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    languageCodes.forEach { code ->
                        val selected = effectiveLanguageFilter == code
                        val label = if (code == "all") {
                            "All"
                        } else {
                            state.allTafseers.firstOrNull { it.languageCode == code }
                                ?.languageName
                                ?.takeIf { it.isNotBlank() && it != "Unknown" }
                                ?: IslamicDataRepository.languageDisplayName(code, code)
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.clickable { tafseerLanguageFilter = code }
                        ) {
                            Text(
                                text = label,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "Tafseer Editions",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    state.allTafseers
                        .filter { pack ->
                            effectiveLanguageFilter == "all" ||
                                pack.languageCode == effectiveLanguageFilter
                        }
                        .forEach { pack ->
                            val selected = state.selectedTafsirId == pack.tafsirId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.onIntent(
                                            QuranUiIntent.SelectTafsir(
                                                tafsirId = pack.tafsirId,
                                                languageCode = pack.languageCode,
                                                title = pack.title
                                            )
                                        )
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pack.title,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = listOfNotNull(
                                            pack.author.takeIf { it.isNotBlank() },
                                            pack.statusLabel,
                                            "Active".takeIf { selected }
                                        ).joinToString(" · "),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!pack.isDownloaded && pack.tafsirId > 0) {
                                    IconButton(
                                        onClick = {
                                            vm.onIntent(QuranUiIntent.DownloadTafseerPack(pack.tafsirId))
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = "Download ${pack.title}",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                }
            }
        }
    }

    if (showQuranOptions) {
        ModalBottomSheet(
            onDismissRequest = { showQuranOptions = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Reader menu",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                ReaderOverflowItem(
                    icon = Icons.Default.Translate,
                    label = "Translation",
                    onClick = {
                        studioSheetTab = 1
                        showQuranOptions = false
                    }
                )
                ReaderOverflowItem(
                    icon = Icons.Default.Book,
                    label = "Tafsir",
                    onClick = {
                        showTafsirBooksSheet = true
                        showQuranOptions = false
                    }
                )
                ReaderOverflowItem(
                    icon = Icons.Default.Translate,
                    label = "Word-by-Word Language",
                    onClick = {
                        showWbwLanguageSheet = true
                        showQuranOptions = false
                    }
                )
            }
        }
    }

    if (showWbwLanguageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWbwLanguageSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Word-by-Word Language",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (state.availableWbwLanguages.isEmpty()) {
                    Text(
                        text = "No word-by-word languages are available yet.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.availableWbwLanguages.forEach { code ->
                            val selected = code == state.wbwLanguage
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    vm.onIntent(QuranUiIntent.SelectWbwLanguage(code))
                                    showWbwLanguageSheet = false
                                }
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
    }

    state.selectedQulWord?.let { word ->
        ModalBottomSheet(onDismissRequest = { vm.onIntent(QuranUiIntent.ClearQulWord) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Word ${word.wordKey}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { vm.onIntent(QuranUiIntent.ClearQulWord) }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.quran_sheet_close)
                        )
                    }
                }
                Text(
                    text = word.arabicFor(state.qulScript),
                    fontFamily = UthmanicFontFamily,
                    fontSize = 34.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                word.transliteration.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                word.translation.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                state.selectedQulMorphology?.let { morphology ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        QulMorphologyRow("Root", morphology.root.ifBlank { "—" })
                        QulMorphologyRow("Lemma", morphology.lemma.ifBlank { "—" })
                        QulMorphologyRow("Stem", morphology.stem.ifBlank { "—" })
                        QulMorphologyRow("Part of speech", morphology.pos.ifBlank { "—" })
                    }
                }
                if (state.selectedQulWordOccurrences.isNotEmpty()) {
                    Text(
                        text = "Other occurrences of this root",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    state.selectedQulWordOccurrences.take(40).forEach { occurrence ->
                        Text(
                            text = "${occurrence.surah}:${occurrence.ayah} · ${occurrence.uthmani}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
    QuranReadingLayout.SPLIT_COLUMN -> stringResource(R.string.quran_layout_split_column)
    QuranReadingLayout.THUMB_BOTTOM_DOCK -> stringResource(R.string.quran_layout_thumb_bottom_dock)
}

/** Subtle chapter stepper used on either side of the top surah pill. */
@Composable
private fun StudyChevron(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(36.dp)
            .semantics { contentDescription = description }
    ) {
        Text(
            text = glyph,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

/** Right-edge fast ayah scrubber; only its narrow strip consumes pointer input. */
@Composable
private fun FastAyahScrubber(
    ayahCount: Int,
    listState: LazyListState,
    active: Boolean,
    activeAyah: Int,
    enabled: Boolean = true,
    onActiveChange: (Boolean) -> Unit,
    onAyahChange: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    if (ayahCount <= 1) return
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val thumbHeight = 36.dp
        val travel = (maxHeight - thumbHeight).coerceAtLeast(0.dp)
        val fraction = (listState.firstVisibleItemIndex.toFloat() / (ayahCount - 1))
            .coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(18.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                .pointerInput(ayahCount, enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { onActiveChange(true) },
                        onDragEnd = { onActiveChange(false) },
                        onDragCancel = { onActiveChange(false) }
                    ) { change, _ ->
                        val y = change.position.y.coerceIn(0f, size.height.toFloat())
                        val index = ((y / size.height) * (ayahCount - 1))
                            .roundToInt()
                            .coerceIn(0, ayahCount - 1)
                        onAyahChange(index + 1)
                        scope.launch { listState.animateScrollToItem(index) }
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(thumbHeight)
                    .offset(y = travel * fraction)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(50)
                    )
            )
        }
        if (active) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 22.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "Ayah $activeAyah",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

/** Shared verbatim Bismillah header for the Mushaf page and verse-by-verse reader. */
@Composable
internal fun BismillahHeader(modifier: Modifier = Modifier) {
    Text(
        text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
        fontFamily = UthmanicFontFamily,
        fontSize = 22.sp,
        lineHeight = 40.sp,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth()
    )
}

/** Equal touch target for each bottom study action. */
@Composable
private fun StudyDockIcon(
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit
) {
    val container = if (active) MaterialTheme.colorScheme.secondaryContainer
    else Color.Transparent
    val tint = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(container, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        content(tint)
    }
}

/** Search, prev/next, and a split Surah + Ayah picker for quick navigation. */
@Composable
private fun QuickJumpSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    surahs: List<SurahModel>,
    selectedSurah: Int,
    onSelectSurah: (Int) -> Unit,
    ayahCount: Int,
    onJump: (Int, Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canGoPrevious: Boolean,
    canGoNext: Boolean
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val jumpMatch = Regex("^(\\d{1,3})\\s*:\\s*(\\d{1,3})$").matchEntire(query.trim())
    val jumpSurah = jumpMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
    val jumpAyah = jumpMatch?.groupValues?.getOrNull(2)?.toIntOrNull()
    val canJump = jumpSurah != null && jumpSurah in 1..114 && jumpAyah != null && jumpAyah > 0
    val trimmed = query.trim()
    val filtered = if (trimmed.isBlank() || canJump) {
        surahs
    } else {
        surahs.filter {
            it.number.toString().contains(trimmed) ||
                it.nameEnglish.contains(trimmed, ignoreCase = true)
        }
    }
    val submitJump = {
        if (canJump) {
            onJump(jumpSurah, jumpAyah)
            keyboard?.hide()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search Surah or jump e.g. 2:255", fontSize = 12.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search Surah")
            },
            trailingIcon = {
                if (canJump) {
                    IconButton(onClick = { submitJump() }) {
                        Icon(Icons.Default.Search, contentDescription = "Jump to ayah")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submitJump() }),
            shape = RoundedCornerShape(14.dp)
        )
        if (canJump) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { submitJump() },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "Jump to $jumpSurah:$jumpAyah",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onPrevious, enabled = canGoPrevious, modifier = Modifier.weight(1f)) {
                Text("‹ Previous", fontSize = 11.sp)
            }
            Button(onClick = onNext, enabled = canGoNext, modifier = Modifier.weight(1f)) {
                Text("Next ›", fontSize = 11.sp)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
        ) {
            LazyColumn(modifier = Modifier.weight(0.42f)) {
                items(filtered, key = { it.number }) { item ->
                    val selected = item.number == selectedSurah
                    val subtitle = listOfNotNull(
                        item.englishMeaning.takeIf { it.isNotBlank() },
                        item.revelationType.takeIf { it.isNotBlank() },
                        if (item.ayahCount > 0) "${item.ayahCount} Ayahs" else null
                    ).joinToString(" · ")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSurah(item.number) }
                            .background(
                                if (selected) MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${item.number}.",
                            modifier = Modifier.width(28.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.nameEnglish,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (subtitle.isNotBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            )
            Column(modifier = Modifier.weight(0.58f)) {
                Text(
                    text = "Ayahs · $selectedSurah",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 44.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    items(count = ayahCount) { index ->
                        val ayahNumber = index + 1
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .padding(2.dp)
                                .clickable { onJump(selectedSurah, ayahNumber) }
                        ) {
                            Text(
                                text = "$ayahNumber",
                                modifier = Modifier.padding(vertical = 8.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Compact name for the two-option segmented bar, where long labels would wrap. */
@Composable
internal fun QuranReadingLayout.shortLabel(): String = when (this) {
    QuranReadingLayout.SPLIT_COLUMN -> stringResource(R.string.quran_layout_short_split)
    QuranReadingLayout.THUMB_BOTTOM_DOCK -> stringResource(R.string.quran_layout_short_dock)
}

@Composable
internal fun QuranReadingMode.localizedLabel(): String = when (this) {
    QuranReadingMode.STUDY -> stringResource(R.string.quran_mode_study)
    QuranReadingMode.MUSHAF -> stringResource(R.string.quran_mode_mushaf)
}

/** One-tap switch between Split-column study and Thumb-zone dock. */
@Composable
private fun ReaderPillLayoutToggle(
    selected: QuranReadingLayout,
    onClick: () -> Unit
) {
    val target = if (selected == QuranReadingLayout.SPLIT_COLUMN) {
        QuranReadingLayout.THUMB_BOTTOM_DOCK
    } else {
        QuranReadingLayout.SPLIT_COLUMN
    }
    CompactPillIconButton(description = target.localizedLabel(), onClick = onClick) {
        Icon(
            imageVector = when (target) {
                QuranReadingLayout.SPLIT_COLUMN -> Icons.Default.ViewColumn
                QuranReadingLayout.THUMB_BOTTOM_DOCK -> Icons.Default.ViewAgenda
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(19.dp)
        )
    }
}

/** Compact toggle icon used by the bottom reader dock. */
@Composable
private fun ReaderPillToggleIcon(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit
) {
    CompactPillIconButton(description = description, onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp)
        )
    }
}

/** Compact circular icon wrapper so six dock actions leave room for the surah title. */
@Composable
private fun CompactPillIconButton(
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Floating reader-scoped sliders: Arabic (including WBW) and Translation (including WBW meanings). */
@Composable
private fun ReaderFontSlidersCard(
    arabicFontSize: Int,
    translationFontSize: Int,
    onArabicChange: (Int) -> Unit,
    onTranslationChange: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Arabic ${arabicFontSize}sp",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Slider(
                value = arabicFontSize.toFloat(),
                onValueChange = { onArabicChange(it.roundToInt().coerceIn(16, 40)) },
                valueRange = 16f..40f,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Translation ${translationFontSize}sp",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Slider(
                value = translationFontSize.toFloat(),
                onValueChange = { onTranslationChange(it.roundToInt().coerceIn(12, 30)) },
                valueRange = 12f..30f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Minimalist brain outline with an alert badge for Hifz mistake tracking. */
private val hifzBrainIcon: ImageVector = ImageVector.Builder(
    name = "hifz_brain_alert",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    val stroke = SolidColor(Color.White)
    addPath(
        pathData = PathParser().parsePathString("M12 5.4c-1.2-1.2-3.1-1.4-4.4-.6-1.4-.1-2.6.9-2.8 2.3-1.4.6-2.2 2-2 3.4.1 1 .8 1.9 1.8 2.3-.2 1.4.7 2.7 2.1 3.1.9.3 2 .1 2.7-.5.7.5 1.7.7 2.6.4V5.4Z").toNodes(),
        stroke = stroke,
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    )
    addPath(
        pathData = PathParser().parsePathString("M12 5.4c1.2-1.2 3.1-1.4 4.4-.6 1.4-.1 2.6.9 2.8 2.3 1.4.6 2.2 2 2 3.4-.1 1-.8 1.9-1.8 2.3.2 1.4-.7 2.7-2.1 3.1-.9.3-2 .1-2.7-.5-.7.5-1.7.7-2.6.4V5.4Z").toNodes(),
        stroke = stroke,
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    )
    addPath(
        pathData = PathParser().parsePathString("M12 5.4v13.1").toNodes(),
        stroke = stroke,
        strokeLineWidth = 1.3f,
        strokeLineCap = StrokeCap.Round
    )
    addPath(
        pathData = PathParser().parsePathString("M15.4 5.6a3 3 0 1 0 6 0a3 3 0 1 0 -6 0").toNodes(),
        stroke = stroke,
        strokeLineWidth = 1.4f,
        strokeLineJoin = StrokeJoin.Round
    )
    addPath(
        pathData = PathParser().parsePathString("M18.4 4.1v2.5").toNodes(),
        stroke = stroke,
        strokeLineWidth = 1.6f,
        strokeLineCap = StrokeCap.Round
    )
    addPath(
        pathData = PathParser().parsePathString("M18.4 8.5l0 .1").toNodes(),
        stroke = stroke,
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round
    )
}.build()

/** Canvas-backed toggle that tints a custom glyph with the active theme color. */
@Composable
private fun ReaderPillCanvasToggle(
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    glyph: @Composable (Color) -> Unit
) {
    val color = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    CompactPillIconButton(description = description, onClick = onClick) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            glyph(color)
        }
    }
}

/** Arabic ع over a divider over Latin A: interlinear word-by-word meaning. */
@Composable
private fun WbwStackedGlyph(color: Color) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    Canvas(modifier = Modifier.size(24.dp)) {
        val arabic = "ع"
        val latin = "A"
        val arabicLayout = measurer.measure(arabic, style = style)
        val latinLayout = measurer.measure(latin, style = style)
        drawText(
            measurer,
            arabic,
            topLeft = Offset((size.width - arabicLayout.size.width) / 2f, 0f),
            style = style
        )
        drawLine(
            color = color,
            start = Offset(2f, 12f),
            end = Offset(size.width - 2f, 12f),
            strokeWidth = 1.2f
        )
        drawText(
            measurer,
            latin,
            topLeft = Offset((size.width - latinLayout.size.width) / 2f, 12.5f),
            style = style
        )
    }
}

/** `aA` with stacked up/down carets for the global reading font scale. */
@Composable
private fun FontScaleGlyph(color: Color) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    Canvas(modifier = Modifier.size(24.dp)) {
        val label = "aA"
        val layout = measurer.measure(label, style = style)
        drawText(
            measurer,
            label,
            topLeft = Offset(0f, (size.height - layout.size.height) / 2f),
            style = style
        )
        drawPath(
            Path().apply {
                moveTo(18.5f, 5f)
                lineTo(21f, 8.5f)
                lineTo(16f, 8.5f)
                close()
            },
            color = color
        )
        drawPath(
            Path().apply {
                moveTo(18.5f, 19f)
                lineTo(16f, 15.5f)
                lineTo(21f, 15.5f)
                close()
            },
            color = color
        )
    }
}

/** Single row in the three-dot reader overflow sheet. */
@Composable
private fun ReaderOverflowItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** One-tap switch between the verse pages and the flowing mushaf page. */
@Composable
private fun ReaderPillModeButton(
    studyMode: Boolean,
    onClick: () -> Unit
) {
    CompactPillIconButton(
        description = stringResource(R.string.quran_pill_reading_mode),
        onClick = onClick
    ) {
        Icon(
            imageVector = if (studyMode) Icons.Default.ViewAgenda
            else Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
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
private fun QulMorphologyRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontFamily = FontFamily.Serif,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Human label for a QUL WBW language code. */
internal fun wbwLanguageLabel(code: String): String = when (code) {
    "en" -> "English"
    "ur" -> "Urdu"
    "id" -> "Indonesian"
    "tr" -> "Turkish"
    "bn" -> "Bengali"
    "hi" -> "Hindi"
    "fr" -> "French"
    "ta" -> "Tamil"
    "fa" -> "Persian"
    "inh" -> "Ingush"
    else -> code.uppercase()
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
