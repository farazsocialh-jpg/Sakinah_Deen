package com.sakinah.deen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakinah.deen.data.database.entity.HadithEntryEntity
import com.sakinah.deen.data.model.HadithBookCatalog
import com.sakinah.deen.data.model.HadithModel
import com.sakinah.deen.network.IslamicDataRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HadithBookInfo(
    val key: String,
    val title: String,
    val totalCount: String
)

data class HadithUiState(
    val books: List<HadithBookInfo> = emptyList(),
    val selectedBookKey: String = "all",
    val selectedLanguageCode: String = "eng",
    val availableLanguages: List<String> = listOf("eng", "ara"),
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
    /** True once the encrypted database has supplied at least one hadith. */
    val isFromDatabase: Boolean = false,
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
    data class NavigateToDeepLink(val hadithIdOrSlug: String) : HadithUiIntent()
    data object ClearSearch : HadithUiIntent()
    data object RetrySync : HadithUiIntent()
}

class HadithViewModel(
    private val islamicRepository: IslamicDataRepository? = null
) : ViewModel() {

    private val canonicalBooks = listOf(
        HadithBookInfo("bukhari", "Sahih al-Bukhari", "7,563 Hadiths"),
        HadithBookInfo("muslim", "Sahih Muslim", "7,500 Hadiths"),
        HadithBookInfo("abudawud", "Sunan Abi Dawud", "5,274 Hadiths"),
        HadithBookInfo("tirmidhi", "Jami‘ at-Tirmidhi", "3,956 Hadiths"),
        HadithBookInfo("nasai", "Sunan an-Nasa'i", "5,760 Hadiths"),
        HadithBookInfo("ibnmajah", "Sunan Ibn Majah", "4,341 Hadiths"),
        HadithBookInfo("muwatta", "Muwatta Malik", "1,858 Hadiths"),
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

    private val _uiState = MutableStateFlow(
        HadithUiState(
            books = canonicalBooks,
            hadiths = placeholderCorpus,
            filteredHadiths = placeholderCorpus,
            bookmarkedIds = setOf("bukhari-1", "muslim-140")
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
                        selectedSectionNumber = 0,
                        isLoading = true,
                        errorMessage = null,
                        downloadProgress = null
                    )
                }
                updateDerivedFromCatalog(intent.bookKey)
                applyFilterAndSearch()
                if (intent.bookKey != "all") {
                    syncBook(intent.bookKey, _uiState.value.selectedLanguageCode)
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
                observeDatabaseHadiths()
            }
            is HadithUiIntent.SelectLanguage -> {
                _uiState.update {
                    it.copy(
                        selectedLanguageCode = intent.languageCode,
                        selectedSectionNumber = 0,
                        isLoading = true,
                        errorMessage = null,
                        downloadProgress = null
                    )
                }
                updateDerivedFromCatalog(_uiState.value.selectedBookKey)
                applyFilterAndSearch()
                if (_uiState.value.selectedBookKey != "all") {
                    syncBook(_uiState.value.selectedBookKey, intent.languageCode)
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
                observeDatabaseHadiths()
            }
            is HadithUiIntent.SelectSection -> {
                _uiState.update { it.copy(selectedSectionNumber = intent.sectionNumber) }
                observeDatabaseHadiths()
            }
            is HadithUiIntent.SearchQueryChanged -> {
                _uiState.update { it.copy(searchQuery = intent.query) }
                applyFilterAndSearch()
            }
            is HadithUiIntent.FilterGradeChanged -> {
                _uiState.update { it.copy(filterGrade = intent.grade) }
                applyFilterAndSearch()
            }
            is HadithUiIntent.ToggleBookmark -> toggleBookmark(intent.hadithId)
            is HadithUiIntent.NavigateToDeepLink -> navigateToDeepLink(intent.hadithIdOrSlug)
            is HadithUiIntent.ClearSearch -> {
                _uiState.update { it.copy(searchQuery = "") }
                applyFilterAndSearch()
            }
            is HadithUiIntent.RetrySync -> {
                val bookKey = _uiState.value.selectedBookKey
                if (bookKey != "all") syncBook(bookKey, _uiState.value.selectedLanguageCode)
            }
        }
    }

    private fun syncBook(bookKey: String, languageCode: String) {
        val repo = islamicRepository ?: run {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, downloadProgress = 0f) }
            try {
                val available = repo.isBookAvailable(bookKey, languageCode)
                if (!available) {
                    val result = repo.syncHadithBook(bookKey, languageCode) { progress ->
                        _uiState.update { it.copy(downloadProgress = progress) }
                    }
                    result.exceptionOrNull()?.let { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        _uiState.update {
                            it.copy(errorMessage = "Could not load $bookKey ($languageCode): ${error.message ?: "network error"}")
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Could not load $bookKey ($languageCode): ${e.message ?: "network error"}")
                }
            } finally {
                _uiState.update { it.copy(isLoading = false, downloadProgress = null) }
            }
        }
    }

    private fun loadCatalog() {
        val repo = islamicRepository ?: return
        viewModelScope.launch {
            val catalog = repo.fetchHadithCatalog()
            hadithCatalog = catalog
            _uiState.update { state ->
                val selectedCatalog = catalog.firstOrNull { it.bookKey == state.selectedBookKey }
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
                    availableLanguages = selectedCatalog?.editions?.map { it.languageCode }?.distinct()?.ifEmpty { state.availableLanguages }
                        ?: state.availableLanguages,
                    sections = selectedCatalog?.sections?.toList()?.sortedBy { it.first } ?: state.sections
                )
            }
        }
    }

    private fun updateDerivedFromCatalog(bookKey: String) {
        val selectedCatalog = hadithCatalog.firstOrNull { it.bookKey == bookKey } ?: return
        _uiState.update { state ->
            state.copy(
                availableLanguages = selectedCatalog.editions.map { it.languageCode }.distinct().ifEmpty { state.availableLanguages },
                sections = selectedCatalog.sections.toList().sortedBy { it.first }
            )
        }
    }

    private fun observeDatabaseHadiths() {
        databaseJob?.cancel()
        val repo = islamicRepository ?: return
        val bookKey = _uiState.value.selectedBookKey
        val languageCode = _uiState.value.selectedLanguageCode
        val sectionNumber = _uiState.value.selectedSectionNumber
        databaseJob = viewModelScope.launch {
            val flow = when {
                bookKey == "all" -> repo.observeAllHadiths()
                sectionNumber > 0 -> repo.observeHadithsBySection(bookKey, languageCode, sectionNumber)
                else -> repo.observeHadiths(bookKey, languageCode)
            }
            try {
                flow.collect { entries ->
                    if (entries.isEmpty()) return@collect
                    val bookmarked = _uiState.value.bookmarkedIds
                    val mapped = entries.map { entry ->
                        entry.toModel(bookmarked.contains("${entry.bookKey}-${entry.hadithNumber}"))
                    }
                    _uiState.update {
                        it.copy(
                            hadiths = mapped,
                            isFromDatabase = true,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                    applyFilterAndSearch()
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

    private fun buildBooks(entries: List<HadithEntryEntity>): List<HadithBookInfo> =
        entries
            .groupBy { it.bookKey to it.bookTitle }
            .map { (keyAndTitle, group) ->
                HadithBookInfo(
                    key = keyAndTitle.first,
                    title = keyAndTitle.second,
                    totalCount = "${group.size} Hadiths"
                )
            }
            .sortedBy { it.key }

    private fun HadithEntryEntity.toModel(bookmarked: Boolean): HadithModel = HadithModel(
        id = "$bookKey-$languageCode-$hadithNumber",
        bookKey = bookKey,
        bookTitle = bookTitle,
        bookTotalCount = 0,
        hadithNumber = hadithNumber,
        chapterTitle = chapterTitle,
        grade = grade.ifBlank { "UNGRADED" },
        gradingScholar = gradingScholar,
        arabicText = arabicText,
        sanad = "",
        matnTranslation = englishText,
        bookmarked = bookmarked
    )

    private fun applyFilterAndSearch() {
        val current = _uiState.value
        val query = current.searchQuery.trim().lowercase()
        val bookKey = current.selectedBookKey
        val gradeFilter = current.filterGrade.uppercase()

        val results = current.hadiths.filter { hadith ->
            val matchesBook = (bookKey == "all" || hadith.bookKey.equals(bookKey, ignoreCase = true))
            val matchesGrade = (gradeFilter == "ALL" || hadith.grade.uppercase().contains(gradeFilter))
            val matchesQuery = query.isBlank() ||
                    hadith.matnTranslation.lowercase().contains(query) ||
                    hadith.arabicText.contains(query) ||
                    hadith.sanad.lowercase().contains(query) ||
                    hadith.hadithNumber.toString() == query ||
                    hadith.chapterTitle.lowercase().contains(query)

            matchesBook && matchesGrade && matchesQuery
        }

        _uiState.update { it.copy(filteredHadiths = results) }
    }

    private fun toggleBookmark(hadithId: String) {
        val bookmarks = _uiState.value.bookmarkedIds.toMutableSet()
        val isNowBookmarked = if (bookmarks.contains(hadithId)) {
            bookmarks.remove(hadithId)
            false
        } else {
            bookmarks.add(hadithId)
            true
        }

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

    private fun navigateToDeepLink(hadithIdOrSlug: String) {
        val normalized = hadithIdOrSlug.replace("/", "-").replace(" ", "-").replace("hadith-", "")
        val target = _uiState.value.hadiths.find {
            it.id.equals(normalized, ignoreCase = true) ||
                    normalized.contains(it.hadithNumber.toString())
        }

        if (target != null) {
            _uiState.update {
                it.copy(
                    selectedBookKey = target.bookKey,
                    searchQuery = "",
                    targetDeepLinkId = target.id
                )
            }
            applyFilterAndSearch()
        }
    }
}
