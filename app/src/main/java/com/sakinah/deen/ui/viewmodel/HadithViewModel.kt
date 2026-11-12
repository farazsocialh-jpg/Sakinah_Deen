package com.sakinah.deen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakinah.deen.data.database.entity.HadithEntryEntity
import com.sakinah.deen.data.model.HadithModel
import com.sakinah.deen.network.IslamicDataRepository
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
    val searchQuery: String = "",
    val filterGrade: String = "all",
    val hadiths: List<HadithModel> = emptyList(),
    val filteredHadiths: List<HadithModel> = emptyList(),
    val isLoading: Boolean = false,
    val bookmarkedIds: Set<String> = emptySet(),
    val targetDeepLinkId: String? = null,
    /** True once the encrypted database has supplied at least one hadith. */
    val isFromDatabase: Boolean = false
)

sealed class HadithUiIntent {
    data class SelectBook(val bookKey: String) : HadithUiIntent()
    data class SearchQueryChanged(val query: String) : HadithUiIntent()
    data class FilterGradeChanged(val grade: String) : HadithUiIntent()
    data class ToggleBookmark(val hadithId: String) : HadithUiIntent()
    data class NavigateToDeepLink(val hadithIdOrSlug: String) : HadithUiIntent()
    data object ClearSearch : HadithUiIntent()
}

/**
 * Hadith reader backed by the encrypted Room tables seeded from
 * `seed_hadith_nawawi.json` / `seed_hisn_muslim.json` and by any downloaded
 * Kutub al-Tis'ah editions. The bundled canon below is used only as a placeholder
 * until the database emits rows (for example on the very first cold start).
 */
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
        HadithBookInfo("ahmad", "Musnad Ahmad", "27,647 Hadiths"),
        HadithBookInfo("darimi", "Sunan al-Darimi", "3,500 Hadiths")
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
        ),
        HadithModel(
            id = "muslim-571",
            bookKey = "muslim",
            bookTitle = "Sahih Muslim",
            bookTotalCount = 7500,
            hadithNumber = 571,
            chapterTitle = "Book of Mosques & Places of Prayer (Kitab al-Masajid)",
            grade = "SAHIH",
            gradingScholar = "Imam Muslim",
            arabicText = "إِذَا شَكَّ أَحَدُكُمْ فِي صَلاَتِهِ فَلَمْ يَدْرِ كَمْ صَلَّى ثَلاَثًا أَمْ أَرْبَعًا فَلْيَطْرَحِ الشَّكَّ وَلْيَبْنِ عَلَى مَا اسْتَيْقَنَ ثُمَّ يَسْجُدُ سَجْدَتَيْنِ قَبْلَ أَنْ يُسَلِّمَ.",
            sanad = "Abu Sa'id al-Khudri (r.a) narrated from the Messenger of Allah (ﷺ):",
            matnTranslation = "When any of you is in doubt during his prayer and does not know how much he prayed—three or four—let him discard doubt and build upon certainty, then prostrate twice before giving Salam.",
            bookmarked = false
        ),
        HadithModel(
            id = "abudawud-1205",
            bookKey = "abudawud",
            bookTitle = "Sunan Abi Dawud",
            bookTotalCount = 5274,
            hadithNumber = 1205,
            chapterTitle = "Book of Prayer (Kitab al-Salah) - Travel",
            grade = "SAHIH",
            gradingScholar = "Al-Albani",
            arabicText = "كَانَ رَسُولُ اللَّهِ صلى الله عليه وسلم إِذَا ارْتَحَلَ قَبْلَ أَنْ تَزِيغَ الشَّمْسُ أَخَّرَ الظُّهْرَ إِلَى وَقْتِ الْعَصْرِ ثُمَّ نَزَلَ فَجَمَعَ بَيْنَهُمَا.",
            sanad = "Anas ibn Malik (r.a) narrated:",
            matnTranslation = "If the Messenger of Allah (ﷺ) set out on a journey before the sun declined, he would delay Dhuhr until the time of Asr, then halt and combine them.",
            bookmarked = false
        ),
        HadithModel(
            id = "tirmidhi-1987",
            bookKey = "tirmidhi",
            bookTitle = "Jami‘ at-Tirmidhi",
            bookTotalCount = 3956,
            hadithNumber = 1987,
            chapterTitle = "Chapters on Righteousness & Good Relations",
            grade = "HASAN SAHIH",
            gradingScholar = "Abu Isa at-Tirmidhi",
            arabicText = "تَبَسُّمُكَ فِي وَجْهِ أَخِيكَ لَكَ صَدَقَةٌ.",
            sanad = "Abu Dharr (r.a) narrated from the Prophet (ﷺ):",
            matnTranslation = "Your smiling in the face of your brother is an act of charity (Sadaqah) for you.",
            bookmarked = true
        )
    )

    private val _uiState = MutableStateFlow(
        HadithUiState(
            books = canonicalBooks,
            hadiths = placeholderCorpus,
            filteredHadiths = placeholderCorpus,
            bookmarkedIds = setOf("bukhari-1", "muslim-140", "tirmidhi-1987")
        )
    )
    val uiState: StateFlow<HadithUiState> = _uiState.asStateFlow()

    init {
        observeDatabaseHadiths()
    }

    fun onIntent(intent: HadithUiIntent) {
        when (intent) {
            is HadithUiIntent.SelectBook -> {
                _uiState.update { it.copy(selectedBookKey = intent.bookKey, isLoading = true) }
                applyFilterAndSearch()
                if (intent.bookKey != "all" && intent.bookKey in com.sakinah.deen.network.IslamicDataRepository.SUPPORTED_HADITH_BOOKS) {
                    viewModelScope.launch {
                        val repo = islamicRepository
                        val available = repo?.isBookAvailable(intent.bookKey) ?: true
                        if (!available) {
                            repo?.syncHadithBook(intent.bookKey)
                        }
                        _uiState.update { it.copy(isLoading = false) }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
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
        }
    }

    // ------------------------------------------------------------------ Room wiring

    private fun observeDatabaseHadiths() {
        val repo = islamicRepository ?: return
        viewModelScope.launch {
            repo.observeAllHadiths(limit = 500).collect { entries ->
                if (entries.isEmpty()) return@collect
                val bookmarked = _uiState.value.bookmarkedIds
                val mapped = entries.map { entry ->
                    entry.toModel(bookmarked.contains("${entry.bookKey}-${entry.hadithNumber}"))
                }

                _uiState.update {
                    it.copy(
                        books = buildBooks(entries),
                        hadiths = mapped,
                        isFromDatabase = true,
                        isLoading = false
                    )
                }
                applyFilterAndSearch()
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
        id = "$bookKey-$hadithNumber",
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

    // ------------------------------------------------------------------ Filtering

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
        // Handle formats like "bukhari-5063", "hadith/bukhari/5063", or "bukhari 5063"
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
