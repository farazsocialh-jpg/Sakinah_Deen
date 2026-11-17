package com.sakinah.deen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakinah.deen.data.database.entity.HadithEntryEntity
import com.sakinah.deen.data.model.HadithBookCatalog
import com.sakinah.deen.data.model.HadithEditionResource
import com.sakinah.deen.data.model.HadithModel
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.network.IslamicDataRepository
import com.sakinah.deen.settings.SettingsRepository
import com.sakinah.deen.util.ArabicNormalizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HadithBookInfo(
    val key: String,
    val title: String,
    val totalCount: String
)

data class HadithUiState(
    val books: List<HadithBookInfo> = emptyList(),
    val selectedBookKey: String = "all",
    val selectedLanguageCode: String = "eng",
    /** Language actually read for a single book when the requested one is unavailable. */
    val resolvedLanguageCode: String? = null,
    val availableLanguages: List<String> = emptyList(),
    /** All catalog languages as (ISO code, human-readable name), ordered for the picker. */
    val availableLanguageOptions: List<Pair<String, String>> = emptyList(),
    val sections: List<Pair<Int, String>> = emptyList(),
    val selectedSectionNumber: Int = 0,
    val searchQuery: String = "",
    val filterGrade: String = "all",
    val hadiths: List<HadithModel> = emptyList(),
    val filteredHadiths: List<HadithModel> = emptyList(),
    val isLoading: Boolean = false,
    val downloadProgress: Float? = null,
    val bookmarkedIds: Set<String> = emptySet(),
    val targetDeepLinkId: String? = null,
    val deepLinkToastMessage: String? = null,
    /** True once the encrypted database has supplied at least one hadith. */
    val isFromDatabase: Boolean = false,
    /** Brief notice when a collection does not publish the selected language. */
    val languageFallbackNotice: String? = null,
    /** Surfaced failure for a collection sync, retried through [HadithUiIntent.RetrySync]. */
    val errorMessage: String? = null
)

sealed class HadithUiIntent {
    data class SelectBook(val bookKey: String) : HadithUiIntent()
    data class SelectLanguage(val languageCode: String) : HadithUiIntent()
    data class SelectSection(val sectionNumber: Int) : HadithUiIntent()
    data class SearchQueryChanged(val query: String) : HadithUiIntent()
    data class FilterGradeChanged(val grade: String) : HadithUiIntent()
    data class ToggleBookmark(val hadithId: String) : HadithUiIntent()
    data class NavigateToDeepLink(
        val hadithIdOrSlug: String,
        val sectionHint: String = ""
    ) : HadithUiIntent()
    data object ClearDeepLinkTarget : HadithUiIntent()
    data object ClearDeepLinkToast : HadithUiIntent()
    data object ClearSearch : HadithUiIntent()
    data object RetrySync : HadithUiIntent()
    data object ReloadCatalog : HadithUiIntent()
}

class HadithViewModel(
    private val islamicRepository: IslamicDataRepository? = null,
    private val preferences: SakinahPreferences? = null,
    private val settingsRepository: SettingsRepository? = null,
    /** Background dispatcher for the large "All Books" filter; injectable for tests. */
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val canonicalBooks = listOf(
        HadithBookInfo("bukhari", "Sahih al-Bukhari", "7,563 Hadiths"),
        HadithBookInfo("muslim", "Sahih Muslim", "7,500 Hadiths"),
        HadithBookInfo("abudawud", "Sunan Abi Dawud", "5,274 Hadiths"),
        HadithBookInfo("tirmidhi", "Jami‘ at-Tirmidhi", "3,956 Hadiths"),
        HadithBookInfo("nasai", "Sunan an-Nasa'i", "5,760 Hadiths"),
        HadithBookInfo("ibnmajah", "Sunan Ibn Majah", "4,341 Hadiths"),
        HadithBookInfo("malik", "Muwatta Malik", "1,858 Hadiths"),
        HadithBookInfo("nawawi", "40 Hadith Nawawi", "42 Hadiths")
    )

    private val placeholderCorpus = listOf(
        HadithModel(
            id = "bukhari-1",
            bookKey = "bukhari",
            bookTitle = "Sahih al-Bukhari",
            bookTotalCount = 7563,
            hadithNumber = 1,
            chapterTitle = "Book of Revelation (Bad' al-Wahy)",
            grade = "SAHIH",
            gradingScholar = "Muttafaq ‘Alayh",
            arabicText = "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى، فَمَنْ كَانَتْ هِجْرَتُهُ إِلَى دُنْيَا يُصِيبُهَا أَوْ إِلَى امْرَأَةٍ يَنْكِحُهَا فَهِجْرَتُهُ إِلَى مَا هَاجَرَ إِلَيْهِ.",
            sanad = "Humaidi narrated from Sufyan, from Yahya ibn Sa'id al-Ansari, from Umar ibn al-Khattab (r.a):",
            matnTranslation = "Actions are judged by intentions, and every person will get what they intended. So whoever emigrated for worldly gain or to marry, his emigration is for that to which he emigrated.",
            bookmarked = true
        ),
        HadithModel(
            id = "bukhari-5063",
            bookKey = "bukhari",
            bookTitle = "Sahih al-Bukhari",
            bookTotalCount = 7563,
            hadithNumber = 5063,
            chapterTitle = "Book of Virtues of the Quran",
            grade = "SAHIH",
            gradingScholar = "Imam al-Bukhari",
            arabicText = "خَيْرُكُمْ مَنْ تَعَلَّمَ الْقُرْآنَ وَعَلَّمَهُ.",
            sanad = "Hajjaj narrated from Shu'bah, from Uthman ibn Affan (r.a):",
            matnTranslation = "The best among you are those who learn the Quran and teach it to others.",
            bookmarked = false
        ),
        HadithModel(
            id = "muslim-140",
            bookKey = "muslim",
            bookTitle = "Sahih Muslim",
            bookTotalCount = 7500,
            hadithNumber = 140,
            chapterTitle = "Book of Faith (Kitab al-Iman)",
            grade = "SAHIH",
            gradingScholar = "Imam Muslim",
            arabicText = "بُنِيَ الإِسْلاَمُ عَلَى خَمْسٍ: شَهَادَةِ أَنْ لاَ إِلَهَ إِلاَّ اللَّهُ وَأَنَّ مُحَمَّدًا عَبْدُهُ وَرَسُولُهُ، وَإِقَامِ الصَّلاَةِ، وَإِيتَاءِ الزَّكَاةِ، وَحَجِّ الْبَيْتِ، وَصَوْمِ رَمَضَانَ.",
            sanad = "Abdullah ibn Umar (r.a) narrated from the Messenger of Allah (ﷺ):",
            matnTranslation = "Islam is founded upon five pillars: testifying there is no deity worthy of worship except Allah and Muhammad is His servant and messenger, establishing prayer, giving Zakat, Hajj, and fasting Ramadan.",
            bookmarked = true
        )
    )

    private var hadithCatalog: List<HadithBookCatalog> = emptyList()
    private var databaseJob: Job? = null
    private var allBooksSyncJob: Job? = null
    private var filterJob: Job? = null
    private var pendingDeepLinkTarget: Triple<String, Int, String>? = null
    private var pendingDeepLinkSlug: String? = null
    private var pendingDeepLinkHint: String = ""
    private var hasReceivedDatabaseRows = false

    private val initialBookmarks: Set<String> = if (preferences != null) {
        if (!preferences.hasInitializedHadithBookmarks) {
            val seed = setOf("bukhari-1", "muslim-140")
            preferences.bookmarkedHadithIds = seed
            preferences.hasInitializedHadithBookmarks = true
            seed
        } else {
            preferences.bookmarkedHadithIds
        }
    } else {
        setOf("bukhari-1", "muslim-140")
    }

    private val _uiState = MutableStateFlow(
        HadithUiState(
            books = canonicalBooks,
            selectedLanguageCode = preferences?.hadithLanguageCode
                ?: settingsRepository?.settings?.value?.appearance?.hadithLanguageCode
                ?: "eng",
            hadiths = placeholderCorpus.map { h -> h.copy(bookmarked = isBookmarkMatch(h.id, h.bookKey, h.hadithNumber, initialBookmarks)) },
            filteredHadiths = placeholderCorpus.map { h -> h.copy(bookmarked = isBookmarkMatch(h.id, h.bookKey, h.hadithNumber, initialBookmarks)) },
            bookmarkedIds = initialBookmarks
        )
    )
    val uiState: StateFlow<HadithUiState> = _uiState.asStateFlow()

    init {
        observeDatabaseHadiths()
        loadCatalog()
    }

    fun onIntent(intent: HadithUiIntent) {
        when (intent) {
            is HadithUiIntent.SelectBook -> {
                _uiState.update {
                    it.copy(
                        selectedBookKey = intent.bookKey,
                        resolvedLanguageCode = null,
                        selectedSectionNumber = 0,
                        isLoading = intent.bookKey != "all",
                        errorMessage = null,
                        downloadProgress = null
                    )
                }
                updateDerivedFromCatalog(intent.bookKey)
                applyFilterAndSearch()
                if (intent.bookKey != "all") {
                    syncBook(intent.bookKey, _uiState.value.selectedLanguageCode)
                } else {
                    syncAllBooks(_uiState.value.selectedLanguageCode)
                }
                observeDatabaseHadiths()
            }
            is HadithUiIntent.SelectLanguage -> {
                preferences?.hadithLanguageCode = intent.languageCode
                settingsRepository?.let { repo ->
                    viewModelScope.launch {
                        repo.updateAppearance(
                            repo.settings.value.appearance.copy(hadithLanguageCode = intent.languageCode)
                        )
                    }
                }
                _uiState.update {
                    it.copy(
                        selectedLanguageCode = intent.languageCode,
                        resolvedLanguageCode = null,
                        selectedSectionNumber = 0,
                        isLoading = _uiState.value.selectedBookKey != "all",
                        errorMessage = null,
                        downloadProgress = null
                    )
                }
                updateDerivedFromCatalog(_uiState.value.selectedBookKey)
                applyFilterAndSearch()
                if (_uiState.value.selectedBookKey != "all") {
                    syncBook(_uiState.value.selectedBookKey, intent.languageCode)
                } else {
                    syncAllBooks(intent.languageCode)
                }
                observeDatabaseHadiths()
            }
            is HadithUiIntent.SelectSection -> {
                _uiState.update { it.copy(selectedSectionNumber = intent.sectionNumber) }
                observeDatabaseHadiths()
            }
            is HadithUiIntent.SearchQueryChanged -> {
                _uiState.update { it.copy(searchQuery = intent.query) }
                // 30,000+ rows: debounce keystrokes and filter off the main thread.
                applyFilterAndSearch(debounceMillis = 250)
            }
            is HadithUiIntent.FilterGradeChanged -> {
                _uiState.update { it.copy(filterGrade = intent.grade) }
                applyFilterAndSearch()
            }
            is HadithUiIntent.ToggleBookmark -> toggleBookmark(intent.hadithId)
            is HadithUiIntent.NavigateToDeepLink -> navigateToDeepLink(intent.hadithIdOrSlug, intent.sectionHint)
            is HadithUiIntent.ClearDeepLinkTarget -> _uiState.update { it.copy(targetDeepLinkId = null) }
            is HadithUiIntent.ClearDeepLinkToast -> _uiState.update { it.copy(deepLinkToastMessage = null) }
            is HadithUiIntent.ClearSearch -> {
                _uiState.update { it.copy(searchQuery = "") }
                applyFilterAndSearch()
            }
            is HadithUiIntent.RetrySync -> {
                val bookKey = _uiState.value.selectedBookKey
                if (bookKey != "all") {
                    syncBook(bookKey, _uiState.value.selectedLanguageCode)
                } else {
                    syncAllBooks(_uiState.value.selectedLanguageCode)
                }
            }
            is HadithUiIntent.ReloadCatalog -> loadCatalog()
        }
    }

    private fun syncBook(bookKey: String, languageCode: String) {
        val repo = islamicRepository ?: run {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        val catalogBook = hadithCatalog.firstOrNull { it.bookKey == bookKey }
        val supported = catalogBook?.editions?.map { it.languageCode.lowercase() }?.distinct().orEmpty()
        val effectiveLanguage = when {
            supported.isEmpty() || languageCode.lowercase() in supported -> languageCode
            "eng" in supported -> "eng"
            "ara" in supported -> "ara"
            else -> supported.first()
        }
        if (!effectiveLanguage.equals(languageCode, ignoreCase = true)) {
            _uiState.update {
                it.copy(
                    resolvedLanguageCode = effectiveLanguage,
                    languageFallbackNotice = "$bookKey does not offer ${languageCode.uppercase()}; showing ${effectiveLanguage.uppercase()} instead."
                )
            }
            observeDatabaseHadiths()
        } else {
            _uiState.update { it.copy(resolvedLanguageCode = null, languageFallbackNotice = null) }
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, downloadProgress = 0f) }
            try {
                val available = repo.isBookAvailable(bookKey, effectiveLanguage)
                if (!available) {
                    val result = repo.syncHadithBook(bookKey, effectiveLanguage) { progress ->
                        _uiState.update { it.copy(downloadProgress = progress) }
                    }
                    result.exceptionOrNull()?.let { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        _uiState.update {
                            it.copy(errorMessage = "Could not load $bookKey ($effectiveLanguage): ${error.message ?: "network error"}")
                        }
                        failPendingDeepLink()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                // Includes OutOfMemoryError from an oversized edition: report it instead of
                // letting the download crash the whole app.
                _uiState.update {
                    it.copy(errorMessage = "Could not load $bookKey ($effectiveLanguage): ${t.message ?: t::class.simpleName ?: "download failed"}")
                }
                failPendingDeepLink()
            } finally {
                _uiState.update { it.copy(isLoading = false, downloadProgress = null) }
            }
        }
    }

    private fun loadCatalog() {
        val repo = islamicRepository ?: return
        viewModelScope.launch {
            try {
                val catalog = repo.fetchHadithCatalog()
                hadithCatalog = catalog
            _uiState.update { state ->
                val selectedCatalog = catalog.firstOrNull { it.bookKey == state.selectedBookKey }
                val editions = if (state.selectedBookKey == "all") {
                    catalog.flatMap { it.editions }
                } else {
                    selectedCatalog?.editions.orEmpty()
                }
                val languageOptions = languageOptionsFor(editions)
                state.copy(
                    books = if (catalog.isNotEmpty()) {
                        catalog.map { info ->
                            HadithBookInfo(
                                key = info.bookKey,
                                title = info.bookTitle.ifBlank { info.bookKey },
                                totalCount = "${info.editions.size} editions"
                            )
                        }
                    } else {
                        state.books
                    },
                    availableLanguages = languageOptions.map { it.first }
                        .ifEmpty { state.availableLanguages },
                    availableLanguageOptions = languageOptions
                        .ifEmpty { state.availableLanguageOptions },
                    sections = if (state.selectedBookKey == "all") {
                        emptyList()
                    } else {
                        selectedCatalog?.sections?.toList()?.sortedBy { it.first } ?: state.sections
                    }
                )
            }
            if (_uiState.value.selectedBookKey == "all") {
                syncAllBooks(_uiState.value.selectedLanguageCode)
            }
            // Replay any deep link that arrived before the catalog was ready.
            val queuedSlug = pendingDeepLinkSlug
            val queuedHint = pendingDeepLinkHint
            if (queuedSlug != null) {
                pendingDeepLinkSlug = null
                pendingDeepLinkHint = ""
                navigateToDeepLink(queuedSlug, queuedHint)
            }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                _uiState.update { it.copy(errorMessage = "Could not load hadith catalog: ${t.message ?: t::class.simpleName ?: "error"}") }
            }
        }
    }

    private fun updateDerivedFromCatalog(bookKey: String) {
        val editions = if (bookKey == "all") {
            hadithCatalog.flatMap { it.editions }
        } else {
            hadithCatalog.firstOrNull { it.bookKey == bookKey }?.editions.orEmpty()
        }
        val options = languageOptionsFor(editions)
        val selectedCatalog = hadithCatalog.firstOrNull { it.bookKey == bookKey }
        _uiState.update { state ->
            state.copy(
                availableLanguages = options.map { it.first }.ifEmpty { state.availableLanguages },
                availableLanguageOptions = options.ifEmpty { state.availableLanguageOptions },
                sections = if (bookKey == "all") {
                    emptyList()
                } else {
                    selectedCatalog?.sections?.toList()?.sortedBy { it.first } ?: state.sections
                }
            )
        }
    }

    private fun languageOptionsFor(
        editions: List<HadithEditionResource>
    ): List<Pair<String, String>> {
        val priority = listOf("eng", "ara", "urd", "ind", "tur", "fra", "ben")
        val byCode = linkedMapOf<String, String>()
        editions.forEach { edition ->
            val code = edition.languageCode.trim().lowercase()
            if (code.isBlank()) return@forEach
            val displayName = edition.languageName.trim().ifBlank {
                IslamicDataRepository.languageDisplayName(code)
            }
            byCode.putIfAbsent(code, displayName)
        }
        return byCode.entries.sortedWith(
            compareBy(
                { entry -> priority.indexOf(entry.key).takeIf { it >= 0 } ?: priority.size },
                { entry -> entry.value.lowercase() }
            )
        ).map { it.key to it.value }
    }

    private fun syncAllBooks(languageCode: String) {
        val repo = islamicRepository ?: return
        val catalog = hadithCatalog
        if (catalog.isEmpty()) return
        allBooksSyncJob?.cancel()
        allBooksSyncJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, errorMessage = null, downloadProgress = 0f)
            }
            val fallbackBooks = mutableListOf<String>()
            val failures = mutableListOf<String>()
            try {
            catalog.forEachIndexed { index, book ->
                val supported = book.editions.map { it.languageCode.lowercase() }
                    .filter { it.isNotBlank() }
                    .distinct()
                val effectiveLanguage = when {
                    supported.isEmpty() || languageCode.lowercase() in supported -> languageCode
                    "eng" in supported -> "eng"
                    "ara" in supported -> "ara"
                    else -> supported.first()
                }
                if (!effectiveLanguage.equals(languageCode, ignoreCase = true)) {
                    fallbackBooks += book.bookTitle.ifBlank { book.bookKey }
                }
                val available = runCatching {
                    repo.isBookAvailable(book.bookKey, effectiveLanguage)
                }.getOrDefault(false)
                if (!available) {
                    val result = repo.syncHadithBook(book.bookKey, effectiveLanguage) { progress ->
                        _uiState.update {
                            it.copy(
                                downloadProgress = (index + progress) / catalog.size.toFloat()
                            )
                        }
                    }
                    val failure = result.exceptionOrNull()
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    if (failure != null) {
                        failures += "${book.bookTitle.ifBlank { book.bookKey }}: ${failure.message ?: "download failed"}"
                    }
                }
                _uiState.update {
                    it.copy(downloadProgress = (index + 1f) / catalog.size.toFloat())
                }
            }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                failures += "Download aborted: ${t.message ?: t::class.simpleName ?: "unknown error"}"
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    downloadProgress = null,
                    languageFallbackNotice = if (fallbackBooks.isNotEmpty()) {
                        "${languageCode.uppercase()} is unavailable in ${fallbackBooks.take(3).joinToString(", ")}" +
                            (if (fallbackBooks.size > 3) " and ${fallbackBooks.size - 3} more" else "") +
                            "; showing the closest available edition on those cards."
                    } else {
                        null
                    },
                    errorMessage = if (failures.isNotEmpty()) {
                        "Some collections could not be downloaded: ${failures.take(2).joinToString("; ")}"
                    } else {
                        it.errorMessage
                    }
                )
            }
        }
    }

    private fun preferredAllBookEntries(
        entries: List<HadithEntryEntity>,
        selectedLanguageCode: String
    ): List<HadithEntryEntity> {
        return entries.groupBy { it.bookKey }.values.flatMap { rows ->
            rows.filter { it.languageCode.equals(selectedLanguageCode, ignoreCase = true) }
                .ifEmpty { rows.filter { it.languageCode.equals("eng", ignoreCase = true) } }
                .ifEmpty { rows.filter { it.languageCode.equals("ara", ignoreCase = true) } }
                .ifEmpty {
                    val closest = rows.firstOrNull()?.languageCode
                    rows.filter { it.languageCode == closest }
                }
        }
    }

    private fun observeDatabaseHadiths() {
        databaseJob?.cancel()
        val repo = islamicRepository ?: return
        val bookKey = _uiState.value.selectedBookKey
        val languageCode = _uiState.value.selectedLanguageCode
        val queryLanguageCode = _uiState.value.resolvedLanguageCode ?: languageCode
        val sectionNumber = _uiState.value.selectedSectionNumber
        databaseJob = viewModelScope.launch {
            val flow = when {
                bookKey == "all" -> repo.observeAllHadiths()
                sectionNumber > 0 -> repo.observeHadithsBySection(bookKey, queryLanguageCode, sectionNumber)
                else -> repo.observeHadiths(bookKey, queryLanguageCode)
            }
            try {
                flow.collect { rawEntries ->
                    if (rawEntries.isEmpty() && !hasReceivedDatabaseRows && bookKey == "all") {
                        return@collect
                    }
                    hasReceivedDatabaseRows = true
                    // "All Books" chooses the selected edition per collection, with an
                    // English/Arabic fallback for books that do not publish that language.
                    // Filtering and mapping 30,000+ rows must never run on the main thread.
                    val bookmarked = _uiState.value.bookmarkedIds
                    val mapped = withContext(Dispatchers.Default) {
                        val entries = if (bookKey == "all") {
                            preferredAllBookEntries(rawEntries, languageCode)
                        } else {
                            rawEntries
                        }
                        entries.map { entry ->
                            val canonicalId = "${entry.bookKey}-${entry.languageCode}-${entry.hadithNumber}"
                            entry.toModel(
                                bookmarked = isBookmarkMatch(
                                    canonicalId, entry.bookKey, entry.hadithNumber, bookmarked
                                ),
                                selectedLanguageCode = languageCode,
                                detectFallback = bookKey == "all" ||
                                    !queryLanguageCode.equals(languageCode, ignoreCase = true)
                            )
                        }
                    }

                    val pending = pendingDeepLinkTarget
                    val resolvedModel = if (pending != null) {
                        val candidates = mapped.filter {
                            it.bookKey.equals(pending.first, ignoreCase = true) &&
                                it.matchesCitationNumber(pending.second)
                        }
                        val arabicMatches = candidates.filter { it.arabicNumberMatches(pending.second) }
                        val preferred = arabicMatches.ifEmpty { candidates }
                        val section = resolveSectionNumber(pending.third, _uiState.value.sections)
                        preferred.firstOrNull { hadith ->
                            pending.third.isBlank() ||
                                sectionMatches(hadith.chapterTitle, pending.third) ||
                                (section > 0 && hadith.sectionNumber == section)
                        } ?: candidates.firstOrNull()
                    } else {
                        null
                    }
                    val resolvedDeepLink = resolvedModel?.id
                    if (resolvedDeepLink != null) {
                        pendingDeepLinkTarget = null
                    } else if (
                        pending != null &&
                        pending.third.isNotBlank() &&
                        _uiState.value.selectedSectionNumber > 0
                    ) {
                        // The hinted section did not contain this number; retry against the whole book.
                        _uiState.update { it.copy(selectedSectionNumber = 0) }
                        observeDatabaseHadiths()
                        return@collect
                    }

                    _uiState.update {
                        it.copy(
                            hadiths = mapped,
                            isFromDatabase = mapped.isNotEmpty() || it.isFromDatabase,
                            isLoading = false,
                            errorMessage = null,
                            selectedSectionNumber = resolvedModel?.sectionNumber
                                ?.takeIf { number -> number > 0 }
                                ?: it.selectedSectionNumber,
                            targetDeepLinkId = resolvedDeepLink ?: it.targetDeepLinkId
                        )
                    }
                    applyFilterAndSearch()
                    if (
                        pendingDeepLinkTarget != null &&
                        resolvedDeepLink == null &&
                        mapped.isNotEmpty() &&
                        _uiState.value.selectedSectionNumber == 0
                    ) {
                        failPendingDeepLink()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Could not read the hadith library: ${e.message ?: "database error"}"
                    )
                }
            }
        }
    }

    /**
     * Citations from the Scholar use the Dar-us-Salam/Arabic reference. In editions where
     * that differs from the sequential hadith number (e.g. Sahih Muslim 657.01), match both.
     */
    private fun HadithModel.matchesCitationNumber(number: Int): Boolean =
        hadithNumber == number || arabicNumberMatches(number)

    private fun HadithModel.arabicNumberMatches(number: Int): Boolean {
        val arabic = arabicNumber.trim()
        if (arabic.isEmpty()) return false
        return arabic == number.toString() || arabic.startsWith("$number.")
    }

    private fun HadithEntryEntity.toModel(
        bookmarked: Boolean,
        selectedLanguageCode: String,
        detectFallback: Boolean
    ): HadithModel = HadithModel(
        id = "$bookKey-$languageCode-$hadithNumber",
        bookKey = bookKey,
        bookTitle = bookTitle,
        bookTotalCount = 0,
        hadithNumber = hadithNumber,
        arabicNumber = arabicNumber,
        chapterTitle = chapterTitle,
        grade = grade.ifBlank { "UNGRADED" },
        gradingScholar = gradingScholar,
        arabicText = arabicText,
        sanad = "",
        matnTranslation = englishText,
        bookmarked = bookmarked,
        sectionNumber = sectionNumber,
        languageCode = languageCode,
        languageName = IslamicDataRepository.languageDisplayName(languageCode, languageCode),
        isLanguageFallback = detectFallback &&
            !languageCode.equals(selectedLanguageCode, ignoreCase = true)
    )

    private fun applyFilterAndSearch(debounceMillis: Long = 0) {
        if (debounceMillis <= 0) {
            // Immediate paths (book/section/grade changes) stay synchronous so state is
            // already current when the intent returns.
            filterJob?.cancel()
            _uiState.update { it.copy(filteredHadiths = filterHadiths(it)) }
            return
        }

        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            delay(debounceMillis)
            val snapshot = _uiState.value
            val results = withContext(computeDispatcher) { filterHadiths(snapshot) }
            // Drop a stale result if another keystroke or filter landed meanwhile.
            if (_uiState.value.searchQuery == snapshot.searchQuery) {
                _uiState.update { it.copy(filteredHadiths = results) }
            }
        }
    }

    private fun filterHadiths(current: HadithUiState): List<HadithModel> {
        val query = current.searchQuery.trim()
        val bookKey = current.selectedBookKey
        val sectionNumber = current.selectedSectionNumber
        val gradeFilter = current.filterGrade.uppercase()

        return current.hadiths.filter { hadith ->
            val matchesBook = (bookKey == "all" || hadith.bookKey.equals(bookKey, ignoreCase = true))
            val matchesSection = (sectionNumber == 0 || hadith.sectionNumber == 0 || hadith.sectionNumber == sectionNumber)
            val matchesGrade = (gradeFilter == "ALL" || hadith.grade.uppercase().contains(gradeFilter))
            val matchesQuery = query.isBlank() ||
                ArabicNormalizer.matches(hadith.matnTranslation, query) ||
                ArabicNormalizer.matches(hadith.arabicText, query) ||
                ArabicNormalizer.matches(hadith.sanad, query) ||
                ArabicNormalizer.matches(hadith.chapterTitle, query) ||
                ArabicNormalizer.matches(hadith.bookTitle, query) ||
                hadith.hadithNumber.toString() == query

            matchesBook && matchesSection && matchesGrade && matchesQuery
        }
    }

    private fun toggleBookmark(hadithId: String) {
        val targetModel = _uiState.value.hadiths.firstOrNull { it.id == hadithId }
        val legacyKey = targetModel?.let { "${it.bookKey}-${it.hadithNumber}" }
        val bookmarks = _uiState.value.bookmarkedIds.toMutableSet()

        val wasBookmarked = bookmarks.contains(hadithId) || (legacyKey != null && bookmarks.contains(legacyKey))
        val isNowBookmarked = if (wasBookmarked) {
            bookmarks.remove(hadithId)
            if (legacyKey != null) bookmarks.remove(legacyKey)
            false
        } else {
            bookmarks.add(hadithId)
            true
        }

        preferences?.bookmarkedHadithIds = bookmarks
        preferences?.hasInitializedHadithBookmarks = true

        val updatedList = _uiState.value.hadiths.map {
            if (it.id == hadithId) it.copy(bookmarked = isNowBookmarked) else it
        }

        _uiState.update {
            it.copy(
                bookmarkedIds = bookmarks,
                hadiths = updatedList
            )
        }
        applyFilterAndSearch()
    }

    private fun isBookmarkMatch(
        canonicalId: String,
        bookKey: String,
        hadithNumber: Int,
        bookmarks: Set<String>
    ): Boolean = canonicalId in bookmarks || "$bookKey-$hadithNumber" in bookmarks

    private fun navigateToDeepLink(hadithIdOrSlug: String, sectionHint: String = "") {
        val cleaned = hadithIdOrSlug.trim().removePrefix("hadith/").removePrefix("hadith-")
        val slashParts = cleaned.replace("-", "/").split("/").filter { it.isNotBlank() }
        val parsedBook = slashParts.firstOrNull { part -> part.all { it.isLetterOrDigit() || it == '_' } }?.lowercase()
        val parsedNumber = slashParts.lastOrNull { part -> part.all { it.isDigit() } }?.toIntOrNull()
            ?: Regex("#?(\\d+)").findAll(hadithIdOrSlug).lastOrNull()?.groupValues?.getOrNull(1)?.toIntOrNull()
        // Cold start: the catalog has not finished loading yet, so queue the target
        // instead of failing with a "could not open link" toast.
        if (_uiState.value.books.isEmpty()) {
            pendingDeepLinkSlug = hadithIdOrSlug
            pendingDeepLinkHint = sectionHint
            return
        }
        val knownBook = parsedBook != null && _uiState.value.books.any {
            it.key.equals(parsedBook, ignoreCase = true)
        }
        if (parsedBook == null || parsedNumber == null || parsedNumber <= 0 || !knownBook) {
            pendingDeepLinkTarget = null
            _uiState.update {
                it.copy(
                    deepLinkToastMessage = buildManualSearchHint(
                        parsedBook ?: "Hadith",
                        parsedNumber ?: 0,
                        sectionHint
                    )
                )
            }
            return
        }
        val hint = sectionHint.trim().takeIf { it.isNotBlank() }
        val matchedSection = resolveSectionNumber(hint, _uiState.value.sections)

        val candidates = _uiState.value.hadiths.filter { h ->
            h.id.equals(hadithIdOrSlug, ignoreCase = true) ||
                (
                    h.bookKey.equals(parsedBook, true) &&
                        h.matchesCitationNumber(parsedNumber) &&
                        (
                            hint == null ||
                                h.sectionNumber == matchedSection ||
                                sectionMatches(h.chapterTitle, hint)
                            )
                    )
        }
        val target = candidates.firstOrNull { it.arabicNumberMatches(parsedNumber) }
            ?: candidates.firstOrNull()

        if (target != null) {
            pendingDeepLinkTarget = null
            _uiState.update {
                it.copy(
                    selectedBookKey = target.bookKey,
                    selectedSectionNumber = target.sectionNumber.takeIf { number -> number > 0 }
                        ?: matchedSection,
                    searchQuery = "",
                    filterGrade = "all",
                    targetDeepLinkId = target.id
                )
            }
            updateDerivedFromCatalog(target.bookKey)
            observeDatabaseHadiths()
            applyFilterAndSearch()
        } else {
            pendingDeepLinkTarget = Triple(parsedBook, parsedNumber, hint.orEmpty())
            _uiState.update {
                it.copy(
                    selectedBookKey = parsedBook,
                    selectedSectionNumber = matchedSection,
                    searchQuery = "",
                    filterGrade = "all"
                )
            }
            updateDerivedFromCatalog(parsedBook)
            syncBook(parsedBook, _uiState.value.selectedLanguageCode)
            observeDatabaseHadiths()
            applyFilterAndSearch()
        }
    }

    private fun buildManualSearchHint(bookKey: String, number: Int, sectionHint: String): String {
        val bookTitle = _uiState.value.books.firstOrNull {
            it.key.equals(bookKey, ignoreCase = true)
        }?.title ?: bookKey.replaceFirstChar { it.uppercase() }
        val locationLabel = if (sectionHint.isNotBlank()) "$bookTitle - $sectionHint" else bookTitle
        val numPart = if (number > 0) "Hadith #$number" else "this Hadith"
        return "Could not open link. Please search $numPart in [$locationLabel] in the Hadith menu."
    }

    private fun failPendingDeepLink() {
        val pending = pendingDeepLinkTarget ?: return
        pendingDeepLinkTarget = null
        _uiState.update {
            it.copy(
                deepLinkToastMessage = buildManualSearchHint(
                    pending.first,
                    pending.second,
                    pending.third
                )
            )
        }
    }

    private fun resolveSectionNumber(hint: String?, sections: List<Pair<Int, String>>): Int {
        if (hint.isNullOrBlank()) return 0
        return sections.firstOrNull { sectionMatches(it.second, hint) }?.first ?: 0
    }

    private fun sectionMatches(sectionName: String, hint: String): Boolean {
        val normalizedSection = normalizeSectionText(sectionName)
        val normalizedHint = normalizeSectionText(hint)
        if (normalizedSection.isBlank() || normalizedHint.isBlank()) return false
        if (normalizedSection.contains(normalizedHint) || normalizedHint.contains(normalizedSection)) return true
        val keywords = normalizedHint.split(' ').filter { it.length >= 3 }
        return keywords.any { keyword -> normalizedSection.contains(keyword) }
    }

    private fun normalizeSectionText(value: String): String = value
        .lowercase()
        .replace(Regex("\\b(book|kitab|the|of|al|and)\\b"), " ")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
