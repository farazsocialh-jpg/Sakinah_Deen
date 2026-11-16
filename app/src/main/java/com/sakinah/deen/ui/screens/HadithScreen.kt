package com.sakinah.deen.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.data.model.HadithModel
import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.data.prefs.HadithReadingLayout
import com.sakinah.deen.ui.components.SakinahCard
import com.sakinah.deen.ui.rememberInject
import com.sakinah.deen.ui.viewmodel.HadithUiIntent
import com.sakinah.deen.ui.viewmodel.HadithViewModel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HadithScreen(
    initialBookKey: String = "all",
    initialHadithNumber: Int = 0,
    initialSectionHint: String = "",
    initialSearchQuery: String = "",
    deepLinkNonce: Int = 0,
    viewModel: HadithViewModel? = null,
    modifier: Modifier = Modifier
) {
    val vm: HadithViewModel = viewModel ?: koinViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var highlightedHadithId by remember { mutableStateOf<String?>(null) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    var showBookPicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var languageQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val appearancePrefs = rememberInject<AppearancePreferences>()
    val appearanceConfig by appearancePrefs.effectiveConfig.collectAsStateWithLifecycle()
    val readingLayout = appearanceConfig.hadithReadingLayout
    val displayedHadiths = if (savedOnly) {
        state.filteredHadiths.filter { it.bookmarked }
    } else {
        state.filteredHadiths
    }
    val selectedBookTitle = state.books.firstOrNull { it.key == state.selectedBookKey }?.title
        ?: "All Books"
    val selectedSectionTitle = state.sections.firstOrNull { it.first == state.selectedSectionNumber }?.second
        .orEmpty()
    fun shareHadith(hadith: HadithModel) {
        val shareText = buildString {
            appendLine("${hadith.bookTitle} #${hadith.hadithNumber}")
            if (hadith.sanad.isNotBlank()) appendLine(hadith.sanad)
            if (hadith.arabicText.isNotBlank()) appendLine(hadith.arabicText)
            if (hadith.matnTranslation.isNotBlank()) appendLine(hadith.matnTranslation)
            append("Shared from ImanCore")
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share hadith"))
    }

    LaunchedEffect(state.deepLinkToastMessage) {
        val message = state.deepLinkToastMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        vm.onIntent(HadithUiIntent.ClearDeepLinkToast)
    }

    LaunchedEffect(initialBookKey, initialHadithNumber, initialSectionHint, deepLinkNonce) {
        if (initialHadithNumber > 0 && initialBookKey != "all") {
            vm.onIntent(
                HadithUiIntent.NavigateToDeepLink(
                    hadithIdOrSlug = "hadith/$initialBookKey/$initialHadithNumber",
                    sectionHint = initialSectionHint
                )
            )
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
            val scrollIndex = if (readingLayout == HadithReadingLayout.FEATURED_AND_LIST) 0 else idx
            listState.animateScrollToItem(scrollIndex)
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
        when (readingLayout) {
            HadithReadingLayout.THUMB_BOTTOM_DOCK -> {
                Text(
                    text = listOf(selectedBookTitle, selectedSectionTitle)
                        .filter { it.isNotBlank() && it != "All Books" }
                        .joinToString(" · ")
                        .ifBlank { "All Books" },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HadithReadingLayout.MINIMALIST_ZEN -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    HadithZenPill(
                        state = state,
                        savedOnly = savedOnly,
                        onBookClick = { showBookPicker = true },
                        onLanguageClick = { showLanguagePicker = true },
                        onGradeSelected = { vm.onIntent(HadithUiIntent.FilterGradeChanged(it)) },
                        onSearchClick = { searchExpanded = !searchExpanded },
                        onSavedClick = { savedOnly = !savedOnly }
                    )
                }
            }
            else -> {
                HadithFilterBar(
                    state = state,
                    libraryBackMode = readingLayout == HadithReadingLayout.BOOK_LIBRARY_GRID,
                    savedOnly = savedOnly,
                    onBookClick = { showBookPicker = true },
                    onAllBooks = { vm.onIntent(HadithUiIntent.SelectBook("all")) },
                    onLanguageClick = { showLanguagePicker = true },
                    onGradeSelected = { vm.onIntent(HadithUiIntent.FilterGradeChanged(it)) },
                    onSearchClick = { searchExpanded = !searchExpanded },
                    onSavedClick = { savedOnly = !savedOnly }
                )
            }
        }

        if (searchExpanded || state.searchQuery.isNotBlank()) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { vm.onIntent(HadithUiIntent.SearchQueryChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search Arabic, meaning, or number…", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { vm.onIntent(HadithUiIntent.ClearSearch) }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
        }

        state.downloadProgress?.let { progress ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Downloading… ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            }
        }

        state.languageFallbackNotice?.let { notice ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = notice,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }

        state.errorMessage?.let { message ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
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

        // 5. Reading feed
        val showLibraryGrid = readingLayout == HadithReadingLayout.BOOK_LIBRARY_GRID &&
            state.selectedBookKey == "all"
        if (!showLibraryGrid && displayedHadiths.isEmpty()) {
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
                            text = when {
                                savedOnly -> "No saved hadiths in this view yet."
                                state.isFromDatabase ->
                                    "No hadiths matched this filter in ${state.selectedLanguageCode.uppercase()}."
                                else -> "Preparing the offline hadith library…"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (fallbackLang != null && state.searchQuery.isBlank() && !savedOnly) {
                            TextButton(onClick = { vm.onIntent(HadithUiIntent.SelectLanguage(fallbackLang)) }) {
                                Text("Switch to ${fallbackLang.uppercase()} edition")
                            }
                        }
                    }
                }
            }
            return@Column
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            HadithReadingLayoutContent(
                layout = readingLayout,
                hadiths = displayedHadiths,
                listState = listState,
                books = state.books,
                selectedBookKey = state.selectedBookKey,
                selectedBookTitle = selectedBookTitle,
                highlightedHadithId = highlightedHadithId,
                onSelectBook = { vm.onIntent(HadithUiIntent.SelectBook(it)) },
                onToggleBookmark = { vm.onIntent(HadithUiIntent.ToggleBookmark(it.id)) },
                onShare = ::shareHadith
            )

            if (readingLayout == HadithReadingLayout.THUMB_BOTTOM_DOCK) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    shadowElevation = 8.dp
                ) {
                    HadithFilterBar(
                        state = state,
                        libraryBackMode = false,
                        savedOnly = savedOnly,
                        onBookClick = { showBookPicker = true },
                        onAllBooks = { vm.onIntent(HadithUiIntent.SelectBook("all")) },
                        onLanguageClick = { showLanguagePicker = true },
                        onGradeSelected = { vm.onIntent(HadithUiIntent.FilterGradeChanged(it)) },
                        onSearchClick = { searchExpanded = !searchExpanded },
                        onSavedClick = { savedOnly = !savedOnly }
                    )
                }
            }
        }
    }

    if (showBookPicker) {
        ModalBottomSheet(
            onDismissRequest = { showBookPicker = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Choose a collection",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 330.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item(key = "all-books") {
                        BookPickerRow(
                            title = "All Books",
                            subtitle = "Every collection",
                            selected = state.selectedBookKey == "all",
                            onClick = {
                                vm.onIntent(HadithUiIntent.SelectBook("all"))
                                showBookPicker = false
                            }
                        )
                    }
                    items(state.books, key = { it.key }) { book ->
                        BookPickerRow(
                            title = book.title,
                            subtitle = book.totalCount,
                            selected = state.selectedBookKey == book.key,
                            onClick = {
                                vm.onIntent(HadithUiIntent.SelectBook(book.key))
                                showBookPicker = false
                            }
                        )
                    }
                }

                if (state.sections.isNotEmpty()) {
                    Text(
                        text = "Sections",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            SectionPickerChip(
                                label = "All sections",
                                selected = state.selectedSectionNumber == 0,
                                onClick = {
                                    vm.onIntent(HadithUiIntent.SelectSection(0))
                                    showBookPicker = false
                                }
                            )
                        }
                        items(state.sections, key = { it.first }) { section ->
                            SectionPickerChip(
                                label = section.second,
                                selected = state.selectedSectionNumber == section.first,
                                onClick = {
                                    vm.onIntent(HadithUiIntent.SelectSection(section.first))
                                    showBookPicker = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLanguagePicker) {
        ModalBottomSheet(
            onDismissRequest = {
                showLanguagePicker = false
                languageQuery = ""
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Choose a language",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = languageQuery,
                    onValueChange = { languageQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search language or code") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                val languageOptions = state.availableLanguageOptions
                val filteredLanguages = remember(languageOptions, languageQuery) {
                    val query = languageQuery.trim()
                    if (query.isBlank()) {
                        languageOptions
                    } else {
                        languageOptions.filter { (code, name) ->
                            name.contains(query, ignoreCase = true) ||
                                code.contains(query, ignoreCase = true)
                        }
                    }
                }

                when {
                    languageOptions.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Language editions are still loading or unavailable.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            OutlinedButton(onClick = { vm.onIntent(HadithUiIntent.ReloadCatalog) }) {
                                Text("Retry", fontSize = 11.sp)
                            }
                        }
                    }
                    filteredLanguages.isEmpty() -> {
                        Text(
                            text = "No languages match “$languageQuery”.",
                            modifier = Modifier.padding(vertical = 18.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredLanguages, key = { it.first }) { (code, name) ->
                            val selected = code.equals(state.selectedLanguageCode, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.onIntent(HadithUiIntent.SelectLanguage(code))
                                        showLanguagePicker = false
                                        languageQuery = ""
                                    }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = code.uppercase(),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected language",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
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

@Composable
private fun HadithFilterBar(
    state: com.sakinah.deen.ui.viewmodel.HadithUiState,
    libraryBackMode: Boolean,
    savedOnly: Boolean,
    onBookClick: () -> Unit,
    onAllBooks: () -> Unit,
    onLanguageClick: () -> Unit,
    onGradeSelected: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSavedClick: () -> Unit
) {
    val bookLabel = if (libraryBackMode && state.selectedBookKey != "all") {
        "‹ All Books"
    } else {
        val title = state.books.firstOrNull { it.key == state.selectedBookKey }?.title ?: "All Books"
        "$title ▾"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier
                .weight(1f)
                .clickable { if (libraryBackMode && state.selectedBookKey != "all") onAllBooks() else onBookClick() }
        ) {
            Text(
                text = bookLabel,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.clickable(onClick = onLanguageClick)
        ) {
            Text(
                text = state.selectedLanguageCode.uppercase().take(3),
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        HadithGradeMenuChip(
            filterGrade = state.filterGrade,
            onGradeSelected = onGradeSelected
        )
        IconButton(onClick = onSearchClick, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search hadith",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onSavedClick, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (savedOnly) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (savedOnly) "Show all hadiths" else "Show saved hadiths",
                tint = if (savedOnly) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun HadithGradeMenuChip(
    filterGrade: String,
    onGradeSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when {
        filterGrade.equals("sahih", true) -> "Sahih"
        filterGrade.equals("hasan", true) -> "Hasan"
        else -> "All"
    }
    Box {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clickable { expanded = true }
        ) {
            Text(
                text = "$label ▾",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("all" to "All grades", "sahih" to "Sahih", "hasan" to "Hasan").forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text, fontSize = 12.sp) },
                    onClick = {
                        expanded = false
                        onGradeSelected(key)
                    }
                )
            }
        }
    }
}

@Composable
private fun HadithZenPill(
    state: com.sakinah.deen.ui.viewmodel.HadithUiState,
    savedOnly: Boolean,
    onBookClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onGradeSelected: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSavedClick: () -> Unit
) {
    val bookTitle = state.books.firstOrNull { it.key == state.selectedBookKey }?.title ?: "All Books"
    val grade = when {
        state.filterGrade.equals("sahih", true) -> "Sahih"
        state.filterGrade.equals("hasan", true) -> "Hasan"
        else -> "All Grades"
    }
    val language = state.selectedLanguageCode.uppercase().take(3)
    Surface(
        modifier = Modifier.fillMaxWidth(0.95f),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        shadowElevation = 5.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$bookTitle · $grade · $language ▾",
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onBookClick)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onLanguageClick, modifier = Modifier.size(30.dp)) {
                Text(language, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSearchClick, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Search, contentDescription = "Search hadith",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = {
                    val nextGrade = when {
                        state.filterGrade.equals("all", true) -> "sahih"
                        state.filterGrade.equals("sahih", true) -> "hasan"
                        else -> "all"
                    }
                    onGradeSelected(nextGrade)
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = "Filter by grade",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onSavedClick, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = if (savedOnly) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (savedOnly) "Show all hadiths" else "Show saved hadiths",
                    tint = if (savedOnly) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun BookPickerRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionPickerChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
