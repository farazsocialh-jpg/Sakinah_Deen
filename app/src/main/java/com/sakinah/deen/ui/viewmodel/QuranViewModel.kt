package com.sakinah.deen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakinah.deen.data.database.dao.HifzDao
import com.sakinah.deen.data.database.dao.QuranDao
import com.sakinah.deen.data.database.entity.HifzMistakeEntity
import com.sakinah.deen.data.database.entity.QuranAyahEntity
import com.sakinah.deen.data.database.entity.SurahMetadataEntity
import com.sakinah.deen.data.model.AyahModel
import com.sakinah.deen.data.model.QuranArabicScript
import com.sakinah.deen.data.model.QuranTranslationResource
import com.sakinah.deen.data.model.SurahModel
import com.sakinah.deen.data.prefs.AyahBookmark
import com.sakinah.deen.data.prefs.QuranReaderPreferences
import com.sakinah.deen.data.prefs.QuranReadingMode
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.data.quran.QulDataSource
import com.sakinah.deen.data.quran.QulMorphology
import com.sakinah.deen.data.quran.QulSurahInfo
import com.sakinah.deen.data.quran.QulWord
import com.sakinah.deen.data.quran.QuranOfflineStatus
import com.sakinah.deen.data.repository.TafseerDownloadRepository
import com.sakinah.deen.data.repository.TafseerPackage
import com.sakinah.deen.network.IslamicDataRepository
import com.sakinah.deen.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A local full-text hit over the encrypted Quran cache. */
data class AyahSearchResult(
    val surahNumber: Int,
    val surahName: String,
    val ayahNumber: Int,
    val textUthmani: String,
    val translation: String
)

data class QuranUiState(
    val currentSurah: SurahModel,
    val surahs: List<SurahModel> = emptyList(),
    val activeHighlightedAyah: Int = 1,
    val scrollTargetAyah: Int? = null,
    /** Verse-by-verse study pages versus the flowing mushaf page; persisted by the reader. */
    val readingMode: QuranReadingMode = QuranReadingMode.STUDY,
    /** Reader list index restored from the last session; applied once, then cleared. */
    val restoreScrollIndex: Int? = null,
    /** Chapter the restored index belongs to, so a placeholder page never consumes it. */
    val restoreSurahNumber: Int? = null,
    /** Ayahs saved by the reader, newest first. */
    val bookmarks: List<AyahBookmark> = emptyList(),
    val downloadedTafseers: List<TafseerPackage> = emptyList(),
    val availableTafseers: List<TafseerPackage> = emptyList(),
    val selectedTafseerContent: String = "",
    /** Download/lookup failure shown inside the Tafseer sheet. */
    val tafseerErrorMessage: String? = null,
    val selectedScholar: String = "Ibn Kathir",
    val hifzMistakes: Map<Pair<Int, Int>, String> = emptyMap(),
    val isDownloadingTafseer: Boolean = false,
    /** Share of the 114 chapters fetched from Quran.com for the current tafsir download. */
    val tafseerDownloadProgress: Float = 0f,
    val searchQuery: String = "",
    val searchResults: List<AyahSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    /** Honest status when the requested content is not in the local database yet. */
    val contentNotice: String? = null,
    /** Surfaced failure for a download/search, with [QuranUiIntent.RetrySync] as retry. */
    val errorMessage: String? = null,
    val selectedTranslationId: Int = IslamicDataRepository.DEFAULT_TRANSLATION_ID,
    val selectedTranslationLanguage: String = "English",
    val selectedTranslationIsoCode: String = "en",
    val selectedTranslationEditionKey: String = "",
    val selectedTranslationSource: String = QuranTranslationResource.SOURCE_QURAN_COM,
    /** Arabic script edition shown in the reader (Uthmani, IndoPak or Imlaei). */
    val arabicScript: QuranArabicScript = QuranArabicScript.UTHMANI,
    val arabicFontSize: Int = 24,
    val availableTranslations: List<QuranTranslationResource> = emptyList(),
    val isLoadingTranslation: Boolean = false,
    /** QUL word-by-word language (ISO code) and the words preloaded for the open surah. */
    val wbwLanguage: String = QuranReaderPreferences.DEFAULT_WBW_LANGUAGE,
    val availableWbwLanguages: List<String> = emptyList(),
    /** Ayah number -> words, loaded with a single indexed query per surah. */
    val qulWordsByAyah: Map<Int, List<QulWord>> = emptyMap(),
    /** "uthmani" or "indopak", persisted with the rest of the reader state. */
    val qulScript: String = QuranReaderPreferences.DEFAULT_WBW_SCRIPT,
    val mushafLayout: String = QuranReaderPreferences.DEFAULT_MUSHAF_LAYOUT,
    /** Word whose morphology sheet is open, plus its root occurrences. */
    val selectedQulWord: QulWord? = null,
    val selectedQulMorphology: QulMorphology? = null,
    val selectedQulWordOccurrences: List<QulWord> = emptyList(),
    /** Offline QUL surah introduction and the open ayah's topics. */
    val surahInfo: QulSurahInfo? = null,
    val ayahTopics: List<Pair<String, String?>> = emptyList(),
    /** Live coverage of the bundled 114-surah pack; null until Room reports once. */
    val offlineStatus: QuranOfflineStatus? = null
) {
    /** Convenience for the reader branches that render one presentation or the other. */
    val isStudyMode: Boolean get() = readingMode == QuranReadingMode.STUDY

    /** Ayah numbers of the current surah that are bookmarked. */
    val bookmarkedAyahNumbers: Set<Int>
        get() = bookmarks.asSequence()
            .filter { it.surahNumber == currentSurah.number }
            .map { it.ayahNumber }
            .toSet()
}

sealed class QuranUiIntent {
    data class SelectSurah(val surahNumber: Int) : QuranUiIntent()
    data class SelectSurahAtAyah(val surahNumber: Int, val ayahNumber: Int) : QuranUiIntent()
    data object ClearScrollTarget : QuranUiIntent()
    data class SelectAyahForTafseer(val ayahNumber: Int) : QuranUiIntent()
    data class SelectArabicScript(val script: QuranArabicScript) : QuranUiIntent()
    data class SelectWbwLanguage(val lang: String) : QuranUiIntent()
    data class SelectQulScript(val script: String) : QuranUiIntent()
    data class SelectMushafLayout(val layout: String) : QuranUiIntent()
    data class SelectQulWord(val wordKey: String) : QuranUiIntent()
    data object ClearQulWord : QuranUiIntent()
    data class SelectScholar(val scholarName: String) : QuranUiIntent()
    data class SetHifzMistakeTag(val surah: Int, val ayah: Int, val wordPos: Int, val tag: String) : QuranUiIntent()
    data class DownloadTafseerPack(val packageId: String) : QuranUiIntent()
    data class SearchAyahs(val query: String) : QuranUiIntent()
    data object ClearSearch : QuranUiIntent()
    data object RetrySync : QuranUiIntent()
    data object ToggleReadingMode : QuranUiIntent()
    data class SetReadingMode(val mode: QuranReadingMode) : QuranUiIntent()
    data class ToggleBookmark(val surahNumber: Int, val ayahNumber: Int) : QuranUiIntent()
    data class SaveReadingPosition(
        val surahNumber: Int,
        val ayahNumber: Int,
        val scrollIndex: Int
    ) : QuranUiIntent()
    data object ClearRestoreScrollIndex : QuranUiIntent()
    data class SelectTranslation(
        val translationId: Int,
        val languageName: String = "",
        val isoCode: String = "",
        val editionKey: String = "",
        val source: String = QuranTranslationResource.SOURCE_QURAN_COM
    ) : QuranUiIntent()
    data object LoadTranslations : QuranUiIntent()
}

class QuranViewModel(
    private val hifzDao: HifzDao? = null,
    private val tafseerRepository: TafseerDownloadRepository? = null,
    private val islamicRepository: IslamicDataRepository? = null,
    private val quranDao: QuranDao? = null,
    private val sakinahPreferences: SakinahPreferences? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val quranReaderPreferences: QuranReaderPreferences? = null,
    private val qulDatabase: QulDataSource? = null
) : ViewModel() {

    /** Only used before the encrypted database has been seeded/reached. */
    private val sampleSurah = SurahModel(
        number = 1,
        nameEnglish = "Al-Fatihah",
        nameArabic = "الفاتحة",
        englishMeaning = "The Opening",
        revelationType = "Makki",
        ayahCount = 7,
        juzNumber = 1,
        ayahs = listOf(
            AyahModel(1, "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", "Bismillāhir-Raḥmānir-Raḥīm", "In the name of Allah, the Entirely Merciful, the Especially Merciful.", "The Basmalah is an opening of sanctification and blessing before every noble action.", "Tabari reports from Ibn Abbas that Allah is the possessor of divine majesty.", "Qurtubi discusses the virtue of commencing with the Basmalah.", "Begin every action seeking grace.", "Linguistic clarity on mercy preceding wrath."),
            AyahModel(2, "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ", "Al-ḥamdu lillāhi Rabbil-‘ālamīn", "All praise is due to Allah, Lord of all the worlds.", "Al-Hamd is comprehensive gratitude belonging inherently to Allah alone.", "Rabb signifies the Master who owns and nurtures.", "Gratitude is the head of all worship.", "Praise belongs to Him for His perfection.", "Recognition of mercy naturally compels praise."),
            AyahModel(3, "الرَّحْمَٰنِ الرَّحِيمِ", "Ar-Raḥmānir-Raḥīm", "The Entirely Merciful, the Especially Merciful.", "Balances awe with intimate hope.", "Divine compassion embraces all.", "Ar-Rahman is non-transferable.", "All grace is the fruit of His Mercy.", "Reinforces optimism."),
            AyahModel(4, "مَالِكِ يَوْمِ الدِّينِ", "Māliki Yawmid-Dīn", "Sovereign of the Day of Recompense.", "On that Day all earthly kings are stripped of power.", "The Day of Final Accounting.", "Recited as Maalik and Malik in Mutawatir Qira'at.", "Awakens Taqwa.", "Justice will rectify all temporal oppressions."),
            AyahModel(5, "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ", "Iyyāka na‘budu wa iyyāka nasta‘īn", "It is You alone we worship, and You alone we ask for help.", "The pivot of the entire Quran: Pure Tawhid.", "We submit our limbs in obedience to You alone.", "Ibadah cures pride; Isti'anah cures self-reliance.", "Worship before aid highlights the primary aim.", "The foundation of Madarij al-Salikin."),
            AyahModel(6, "اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ", "Ihdinaṣ-ṣirāṭal-mustaqīm", "Guide us to the straight path.", "The path of Islam and the prophetic Sunnah without innovation.", "Guidance in knowing truth and having strength to tread it.", "Righteous servants continuously seek steadfastness.", "A path that is wide and illuminated.", "Steadfastness on the prophetic methodology."),
            AyahModel(7, "صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ", "Ṣirāṭalladhīna an‘amta ‘alayhim...", "The path of those upon whom You have bestowed favor...", "The favored ones are the Prophets, the Truthful, the Martyrs, and the Righteous.", "Warning against intellectual arrogance.", "Pronounce Ameen at the conclusion.", "The middle path between negligence and extremism.", "A heartfelt collective prayer for the Ummah.")
        )
    )

    private val selectedSurahNumber = MutableStateFlow(1)
    private val selectedTranslationIdFlow = MutableStateFlow(
        sakinahPreferences?.quranTranslationId
            ?: settingsRepository?.settings?.value?.appearance?.translationId
            ?: IslamicDataRepository.DEFAULT_TRANSLATION_ID
    )
    private var searchJob: Job? = null
    private var pendingTargetAyah: Int? = null

    /** True once Room has supplied chapter metadata, i.e. the offline library is usable. */
    private var hasDatabaseSurahs = false

    private val _uiState = MutableStateFlow(
        QuranUiState(
            currentSurah = sampleSurah,
            surahs = listOf(sampleSurah.copy(ayahs = emptyList())),
            selectedTafseerContent = sampleSurah.ayahs[0].tafseerIbnKathir,
            readingMode = quranReaderPreferences?.readingMode ?: QuranReadingMode.STUDY,
            bookmarks = quranReaderPreferences?.bookmarks.orEmpty(),
            wbwLanguage = quranReaderPreferences?.wbwLanguage
                ?: QuranReaderPreferences.DEFAULT_WBW_LANGUAGE,
            qulScript = quranReaderPreferences?.wbwScript
                ?: QuranReaderPreferences.DEFAULT_WBW_SCRIPT,
            mushafLayout = quranReaderPreferences?.mushafLayout
                ?: QuranReaderPreferences.DEFAULT_MUSHAF_LAYOUT
        )
    )
    val uiState: StateFlow<QuranUiState> = _uiState.asStateFlow()

    init {
        val selectedId = selectedTranslationIdFlow.value
        _uiState.update {
            it.copy(
                selectedTranslationId = selectedId,
                selectedTranslationLanguage = sakinahPreferences?.quranTranslationLanguage
                    ?: settingsRepository?.settings?.value?.appearance?.translationName
                    ?: "English",
                selectedTranslationIsoCode = settingsRepository?.settings?.value?.appearance?.translationLanguage ?: "en",
                selectedTranslationEditionKey = sakinahPreferences?.quranTranslationEditionKey.orEmpty(),
                selectedTranslationSource = sakinahPreferences?.quranTranslationSource
                    ?: QuranTranslationResource.SOURCE_QURAN_COM,
                arabicScript = sakinahPreferences?.quranArabicScript ?: QuranArabicScript.UTHMANI,
                arabicFontSize = (settingsRepository?.settings?.value?.appearance?.arabicFontSize ?: 24).coerceIn(16, 39)
            )
        }
        restoreHifzAnnotations()
        restoreLastReadPosition()
        observeOfflineStatus()
        observeSharedSettings()
        observeTafseerPackages()
        viewModelScope.launch { tafseerRepository?.refreshDownloadStatus() }
        observeRoomSurahs()
        observeSelectedSurahAyahs()
        observeQulData()
        if (islamicRepository != null) {
            viewModelScope.launch { loadTranslationCatalog() }
        }
    }

    fun onIntent(intent: QuranUiIntent) {
        when (intent) {
            is QuranUiIntent.SelectSurah -> selectSurah(intent.surahNumber, targetAyah = null)
            is QuranUiIntent.SelectSurahAtAyah -> selectSurah(intent.surahNumber, targetAyah = intent.ayahNumber)
            is QuranUiIntent.ClearScrollTarget -> _uiState.update { it.copy(scrollTargetAyah = null) }
            is QuranUiIntent.SelectAyahForTafseer -> selectAyahForTafseer(intent.ayahNumber)
            is QuranUiIntent.SelectArabicScript -> selectArabicScript(intent.script)
            is QuranUiIntent.SelectWbwLanguage -> selectWbwLanguage(intent.lang)
            is QuranUiIntent.SelectQulScript -> selectQulScript(intent.script)
            is QuranUiIntent.SelectMushafLayout -> selectMushafLayout(intent.layout)
            is QuranUiIntent.SelectQulWord -> selectQulWord(intent.wordKey)
            is QuranUiIntent.ClearQulWord -> _uiState.update {
                it.copy(
                    selectedQulWord = null,
                    selectedQulMorphology = null,
                    selectedQulWordOccurrences = emptyList()
                )
            }
            is QuranUiIntent.SelectScholar -> {
                _uiState.update { it.copy(selectedScholar = intent.scholarName) }
                onIntent(QuranUiIntent.SelectAyahForTafseer(_uiState.value.activeHighlightedAyah))
            }
            is QuranUiIntent.SetHifzMistakeTag -> setHifzMistakeTag(intent.surah, intent.ayah, intent.wordPos, intent.tag)
            is QuranUiIntent.DownloadTafseerPack -> downloadTafseerPack(intent.packageId)
            is QuranUiIntent.SearchAyahs -> searchAyahs(intent.query)
            is QuranUiIntent.ClearSearch -> {
                searchJob?.cancel()
                _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false) }
            }
            is QuranUiIntent.RetrySync -> retrySync()
            is QuranUiIntent.ToggleReadingMode -> setReadingMode(
                if (_uiState.value.readingMode == QuranReadingMode.STUDY) {
                    QuranReadingMode.MUSHAF
                } else {
                    QuranReadingMode.STUDY
                }
            )
            is QuranUiIntent.SetReadingMode -> setReadingMode(intent.mode)
            is QuranUiIntent.ToggleBookmark -> toggleBookmark(intent.surahNumber, intent.ayahNumber)
            is QuranUiIntent.SaveReadingPosition -> quranReaderPreferences?.saveReadingPosition(
                intent.surahNumber,
                intent.ayahNumber,
                intent.scrollIndex
            )
            is QuranUiIntent.ClearRestoreScrollIndex -> _uiState.update {
                it.copy(restoreScrollIndex = null, restoreSurahNumber = null)
            }
            is QuranUiIntent.SelectTranslation -> selectTranslation(
                intent.translationId,
                intent.languageName,
                intent.isoCode,
                intent.editionKey,
                intent.source
            )
            is QuranUiIntent.LoadTranslations -> viewModelScope.launch { loadTranslationCatalog() }
        }
    }

    private fun observeSharedSettings() {
        val repo = settingsRepository ?: return
        viewModelScope.launch {
            repo.settings.drop(1).collect { settings ->
                val fontSize = settings.appearance.arabicFontSize.coerceIn(16, 39)
                if (fontSize != _uiState.value.arabicFontSize) {
                    _uiState.update { it.copy(arabicFontSize = fontSize) }
                }

                val translationId = settings.appearance.translationId
                if (translationId > 0 && translationId != selectedTranslationIdFlow.value) {
                    selectedTranslationIdFlow.value = translationId
                    val language = settings.appearance.translationName.ifBlank {
                        _uiState.value.selectedTranslationLanguage
                    }
                    val iso = settings.appearance.translationLanguage.ifBlank {
                        _uiState.value.selectedTranslationIsoCode
                    }
                    sakinahPreferences?.quranTranslationId = translationId
                    sakinahPreferences?.quranTranslationLanguage = language
                    sakinahPreferences?.quranTranslationEditionKey = ""
                    sakinahPreferences?.quranTranslationSource =
                        QuranTranslationResource.SOURCE_QURAN_COM
                    _uiState.update {
                        it.copy(
                            selectedTranslationId = translationId,
                            selectedTranslationLanguage = language,
                            selectedTranslationIsoCode = iso,
                            selectedTranslationEditionKey = "",
                            selectedTranslationSource = QuranTranslationResource.SOURCE_QURAN_COM,
                            isLoadingTranslation = true
                        )
                    }
                    syncSurah(
                        _uiState.value.currentSurah.number,
                        translationId,
                        "",
                        QuranTranslationResource.SOURCE_QURAN_COM
                    )
                }
            }
        }
    }

    private fun observeRoomSurahs() {
        val repo = islamicRepository ?: return
        viewModelScope.launch {
            repo.observeSurahs().collectLatest { metadata ->
                if (metadata.isEmpty()) return@collectLatest
                hasDatabaseSurahs = true
                _uiState.update { it.copy(surahs = metadata.map { entity -> entity.toSurahModel() }) }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeSelectedSurahAyahs() {
        val repo = islamicRepository ?: return
        viewModelScope.launch {
            combine(selectedSurahNumber, selectedTranslationIdFlow) { surah, translation ->
                surah to translation
            }.flatMapLatest { (surahNumber, translationId) ->
                repo.observeSurah(surahNumber, translationId)
            }.collect { ayahs ->
                val surahNumber = selectedSurahNumber.value
                if (ayahs.isEmpty()) {
                    if (!hasDatabaseSurahs) return@collect
                    _uiState.update {
                        if (it.currentSurah.number == surahNumber) {
                            it.copy(
                                contentNotice = "Surah $surahNumber is not in the offline database yet. " +
                                    "Connect to download it or select a Juz 'Amma surah."
                            )
                        } else it
                    }
                    return@collect
                }
                applyAyahs(surahNumber, ayahs)
            }
        }
    }

    private fun applyAyahs(surahNumber: Int, ayahs: List<QuranAyahEntity>) {
        val mapped = ayahs.map { it.toAyahModel() }
        val metadata = _uiState.value.surahs.find { it.number == surahNumber }
        val chapter = (metadata ?: SurahModel(
            number = surahNumber,
            nameEnglish = ayahs.first().surahNameEnglish.orEmpty(),
            nameArabic = ayahs.first().surahNameArabic.orEmpty(),
            englishMeaning = "",
            revelationType = ayahs.first().revelationPlace.orEmpty(),
            ayahCount = ayahs.size,
            juzNumber = 0,
            ayahs = emptyList()
        )).copy(ayahCount = ayahs.size, ayahs = mapped)

        val desiredAyah = pendingTargetAyah ?: _uiState.value.activeHighlightedAyah
        val highlighted = mapped.firstOrNull { it.numberInSurah == desiredAyah } ?: mapped.first()
        val scrollTarget = pendingTargetAyah
        pendingTargetAyah = null

        _uiState.update {
            it.copy(
                currentSurah = chapter,
                activeHighlightedAyah = highlighted.numberInSurah,
                scrollTargetAyah = scrollTarget ?: it.scrollTargetAyah,
                selectedTafseerContent = highlighted.tafseerIbnKathir,
                contentNotice = null,
                errorMessage = null
            )
        }
    }

    private fun selectSurah(surahNumber: Int, targetAyah: Int?) {
        val state = _uiState.value
        if (targetAyah != null && state.currentSurah.number == surahNumber && state.currentSurah.ayahs.any { it.numberInSurah == targetAyah }) {
            pendingTargetAyah = null
            _uiState.update {
                it.copy(
                    activeHighlightedAyah = targetAyah,
                    scrollTargetAyah = targetAyah
                )
            }
            // Navigation/scroll must never open Tafsir. Only an explicit Tafseer tap may
            // show the missing-commentary state.
            return
        }

        pendingTargetAyah = targetAyah
        val listing = state.surahs.find { it.number == surahNumber }
        selectedSurahNumber.value = surahNumber
        // Preload the chapter's word-by-word data while the ayah rows arrive, and swap the
        // Surah Info card. Both stay off the main thread and are discarded if the reader
        // moved on before they landed.
        loadQulWords(surahNumber)
        loadSurahInfo(surahNumber)
        _uiState.update {
            it.copy(
                surahInfo = null,
                ayahTopics = emptyList(),
                selectedQulWord = null,
                selectedQulMorphology = null,
                selectedQulWordOccurrences = emptyList()
            )
        }
        // Switching chapters is a reading position too; the reader refines the index as
        // soon as the new page is composed and scrolled. Re-picking the open chapter must
        // not reset the position stored for it.
        if (state.currentSurah.number != surahNumber) {
            quranReaderPreferences?.saveReadingPosition(surahNumber, targetAyah ?: 1, 0)
        }

        if (listing == null) {
            val fallback = if (hasDatabaseSurahs) {
                SurahModel(
                    number = surahNumber,
                    nameEnglish = "Surah $surahNumber",
                    nameArabic = "",
                    englishMeaning = "",
                    revelationType = "",
                    ayahCount = 0,
                    juzNumber = 0,
                    ayahs = emptyList()
                )
            } else {
                sampleSurah
            }
            _uiState.update {
                it.copy(
                    currentSurah = fallback,
                    activeHighlightedAyah = targetAyah ?: 1,
                    scrollTargetAyah = targetAyah,
                    selectedTafseerContent = fallback.ayahs.firstOrNull()?.tafseerIbnKathir.orEmpty(),
                    contentNotice = if (hasDatabaseSurahs) {
                        "Surah $surahNumber is not in the offline library yet. Download it while online."
                    } else null
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                currentSurah = listing.copy(ayahs = emptyList()),
                activeHighlightedAyah = targetAyah ?: 1,
                scrollTargetAyah = targetAyah,
                selectedTafseerContent = "",
                contentNotice = "Loading surah ${listing.nameEnglish}…",
                errorMessage = null
            )
        }

        if (islamicRepository != null) {
            syncSurah(
                surahNumber,
                selectedTranslationIdFlow.value,
                _uiState.value.selectedTranslationEditionKey,
                _uiState.value.selectedTranslationSource
            )
        }
    }

    private fun retrySync() {
        val surahNumber = _uiState.value.currentSurah.number
        _uiState.update { it.copy(errorMessage = null) }
        syncSurah(
            surahNumber,
            selectedTranslationIdFlow.value,
            _uiState.value.selectedTranslationEditionKey,
            _uiState.value.selectedTranslationSource
        )
    }

    private fun syncSurah(
        surahNumber: Int,
        translationId: Int = selectedTranslationIdFlow.value,
        editionKey: String = _uiState.value.selectedTranslationEditionKey,
        source: String = _uiState.value.selectedTranslationSource
    ) {
        val repo = islamicRepository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTranslation = true, errorMessage = null) }
            try {
                val result = repo.syncSurah(
                    surahNumber = surahNumber,
                    translationId = translationId,
                    translationEditionKey = editionKey,
                    translationSource = source
                )
                result.exceptionOrNull()?.let { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _uiState.update {
                        it.copy(errorMessage = friendlySurahError(surahNumber, error))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = friendlySurahError(surahNumber, e))
                }
            } finally {
                _uiState.update { it.copy(isLoadingTranslation = false) }
            }
        }
    }

    private fun friendlySurahError(surahNumber: Int, error: Throwable): String {
        val msg = error.message.orEmpty().lowercase()
        val offline = error is java.io.IOException ||
            msg.contains("unable to resolve") ||
            msg.contains("unresolved") ||
            msg.contains("connect") ||
            msg.contains("timeout") ||
            msg.contains("network")
        return if (offline) {
            "Offline: couldn't download Surah $surahNumber. Check your internet connection and tap Retry."
        } else {
            "Could not load Surah $surahNumber right now. Tap Retry to try again."
        }
    }

    private fun selectTranslation(
        translationId: Int,
        languageName: String,
        isoCode: String,
        editionKey: String,
        source: String
    ) {
        val catalogEntry = _uiState.value.availableTranslations.firstOrNull { it.id == translationId }
        val resolvedEditionKey = editionKey.ifBlank { catalogEntry?.editionKey.orEmpty() }
        val resolvedSource = source.ifBlank {
            catalogEntry?.source ?: QuranTranslationResource.SOURCE_QURAN_COM
        }
        selectedTranslationIdFlow.value = translationId
        val language = languageName.ifBlank { _uiState.value.selectedTranslationLanguage }
        val iso = isoCode.ifBlank { _uiState.value.selectedTranslationIsoCode }
        _uiState.update {
            it.copy(
                selectedTranslationId = translationId,
                selectedTranslationLanguage = language,
                selectedTranslationIsoCode = iso,
                selectedTranslationEditionKey = resolvedEditionKey,
                selectedTranslationSource = resolvedSource,
                isLoadingTranslation = true
            )
        }
        sakinahPreferences?.quranTranslationId = translationId
        sakinahPreferences?.quranTranslationLanguage = language
        sakinahPreferences?.quranTranslationEditionKey = resolvedEditionKey
        sakinahPreferences?.quranTranslationSource = resolvedSource
        settingsRepository?.let { repo ->
            viewModelScope.launch {
                repo.updateAppearance(
                    repo.settings.value.appearance.copy(
                        translationId = translationId,
                        translationName = language,
                        translationLanguage = iso.ifBlank { repo.settings.value.appearance.translationLanguage }
                    )
                )
            }
        }
        syncSurah(
            _uiState.value.currentSurah.number,
            translationId,
            resolvedEditionKey,
            resolvedSource
        )
    }

    private fun loadTranslationCatalog() {
        val repo = islamicRepository ?: return
        viewModelScope.launch {
            val catalog = repo.fetchTranslationCatalog()
            _uiState.update { state ->
                val selected = catalog.firstOrNull { it.id == state.selectedTranslationId }
                state.copy(
                    availableTranslations = catalog,
                    selectedTranslationEditionKey = selected?.editionKey
                        ?: state.selectedTranslationEditionKey,
                    selectedTranslationSource = selected?.source
                        ?: state.selectedTranslationSource
                )
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // QUL word-by-word, morphology, surah info and offline tafsir
    // ------------------------------------------------------------------------------------

    /** Loads the language list and the open chapter's words once the DB is reachable. */
    private fun observeQulData() {
        val qul = qulDatabase ?: return
        viewModelScope.launch {
            val languages = qul.getAvailableWbwLanguages()
            _uiState.update { it.copy(availableWbwLanguages = languages) }
        }
        loadQulWords(selectedSurahNumber.value)
        loadSurahInfo(selectedSurahNumber.value)
    }

    /**
     * Preloads one surah's words in the selected language. The query is single and indexed,
     * so it is cheap enough to start as soon as a chapter is chosen rather than on first
     * visible row. A result that arrives after the reader moved on is dropped.
     */
    private fun loadQulWords(surahNumber: Int) {
        val qul = qulDatabase ?: return
        val language = _uiState.value.wbwLanguage
        viewModelScope.launch {
            val words = qul.getWordsForSurah(surahNumber, language)
            if (selectedSurahNumber.value != surahNumber || _uiState.value.wbwLanguage != language) {
                return@launch
            }
            _uiState.update { it.copy(qulWordsByAyah = words) }
        }
    }

    private fun loadSurahInfo(surahNumber: Int) {
        val qul = qulDatabase ?: return
        viewModelScope.launch {
            val info = qul.getSurahInfo(surahNumber)
            if (selectedSurahNumber.value == surahNumber) {
                _uiState.update { it.copy(surahInfo = info) }
            }
        }
    }

    private fun selectWbwLanguage(lang: String) {
        val normalized = lang.trim().lowercase().ifBlank {
            QuranReaderPreferences.DEFAULT_WBW_LANGUAGE
        }
        if (_uiState.value.wbwLanguage == normalized) return
        _uiState.update { it.copy(wbwLanguage = normalized, qulWordsByAyah = emptyMap()) }
        quranReaderPreferences?.wbwLanguage = normalized
        loadQulWords(selectedSurahNumber.value)
    }

    private fun selectQulScript(script: String) {
        val normalized = script.trim().lowercase()
            .takeIf { it in QuranReaderPreferences.WBW_SCRIPTS }
            ?: QuranReaderPreferences.DEFAULT_WBW_SCRIPT
        if (_uiState.value.qulScript == normalized) return
        _uiState.update { it.copy(qulScript = normalized) }
        quranReaderPreferences?.wbwScript = normalized
    }

    private fun selectMushafLayout(layout: String) {
        val normalized = layout.trim().ifBlank { QuranReaderPreferences.DEFAULT_MUSHAF_LAYOUT }
        if (_uiState.value.mushafLayout == normalized) return
        _uiState.update { it.copy(mushafLayout = normalized) }
        quranReaderPreferences?.mushafLayout = normalized
    }

    /** Opens the word sheet with morphology and every occurrence of the same root. */
    private fun selectQulWord(wordKey: String) {
        val word = _uiState.value.qulWordsByAyah.values
            .asSequence()
            .flatten()
            .firstOrNull { it.wordKey == wordKey }
            ?: return
        _uiState.update { it.copy(selectedQulWord = word) }
        val qul = qulDatabase ?: return
        viewModelScope.launch {
            val morphology = qul.getWordMorphology(wordKey)
            val occurrences = morphology?.root
                ?.takeIf { it.isNotBlank() }
                ?.let { qul.searchByRoot(it) }
                .orEmpty()
            if (_uiState.value.selectedQulWord?.wordKey != wordKey) return@launch
            _uiState.update {
                it.copy(
                    selectedQulMorphology = morphology,
                    selectedQulWordOccurrences = occurrences
                )
            }
        }
    }

    /** QUL tafsir slug for a scholar chip; only bundled editions are mapped. */
    private fun qulTafsirSlug(scholar: String): String? = when (scholar) {
        "Ibn Kathir" -> "ibn-kathir"
        "Al-Jalalayn", "Jalalayn" -> "jalalayn"
        else -> null
    }

    /**
     * Switches the Arabic script edition. Chapters cached before the script columns existed
     * are re-synced once so the chosen script is actually available offline.
     */
    private fun selectArabicScript(script: QuranArabicScript) {
        if (_uiState.value.arabicScript == script) return
        _uiState.update { it.copy(arabicScript = script) }
        sakinahPreferences?.quranArabicScript = script

        val repo = islamicRepository ?: return
        val surah = _uiState.value.currentSurah
        val missing = surah.ayahs.any { ayah ->
            when (script) {
                QuranArabicScript.UTHMANI -> ayah.arabicText.isBlank()
                QuranArabicScript.INDOPAK -> ayah.arabicIndopak.isBlank()
                QuranArabicScript.IMLAEI -> ayah.arabicImlaei.isBlank()
            }
        }
        if (!missing || surah.ayahs.isEmpty()) return
        syncSurah(
            surahNumber = surah.number,
            translationId = selectedTranslationIdFlow.value,
            editionKey = _uiState.value.selectedTranslationEditionKey,
            source = _uiState.value.selectedTranslationSource
        )
    }

    private fun selectAyahForTafseer(ayahNumber: Int) {
        val state = _uiState.value
        val ayah = state.currentSurah.ayahs.find { it.numberInSurah == ayahNumber }
            ?: return
        val scholar = state.selectedScholar
        val surahNumber = state.currentSurah.number

        _uiState.update {
            it.copy(
                activeHighlightedAyah = ayahNumber,
                tafseerErrorMessage = null,
                ayahTopics = emptyList()
            )
        }

        val cached = when (scholar) {
            "Ibn Kathir" -> ayah.tafseerIbnKathir
            "Al-Tabari" -> ayah.tafseerTabari
            "Al-Qurtubi" -> ayah.tafseerQurtubi
            "Al-Sa'di" -> ayah.tafseerSadi
            "Ma'ariful Quran" -> ayah.tafseerMaarifulQuran
            else -> ""
        }

        if (cached.isNotBlank()) {
            _uiState.update {
                it.copy(
                    selectedTafseerContent = cached,
                    contentNotice = null,
                    tafseerErrorMessage = null
                )
            }
            return
        }

        // Clear the previous scholar's text while the correctly mapped tafsir is loaded.
        _uiState.update {
            it.copy(
                selectedTafseerContent = "",
                contentNotice = "Loading $scholar commentary…"
            )
        }

        // The bundled QUL editions are read-only and always offline: try them before the
        // encrypted cache/network path, and surface the ayah's topics either way.
        val slug = qulTafsirSlug(scholar)
        if (slug != null && qulDatabase != null) {
            viewModelScope.launch {
                val ayahKey = "$surahNumber:$ayahNumber"
                val qulText = qulDatabase.getTafsir(slug, ayahKey)
                val topics = qulDatabase.getAyahTopics(ayahKey)
                if (qulText != null) {
                    _uiState.update { current ->
                        if (current.selectedScholar != scholar ||
                            current.currentSurah.number != surahNumber ||
                            current.activeHighlightedAyah != ayahNumber
                        ) {
                            current
                        } else {
                            current.copy(
                                selectedTafseerContent = qulText,
                                contentNotice = null,
                                tafseerErrorMessage = null,
                                ayahTopics = topics
                            )
                        }
                    }
                    return@launch
                }
                _uiState.update { current ->
                    if (current.currentSurah.number == surahNumber &&
                        current.activeHighlightedAyah == ayahNumber
                    ) {
                        current.copy(ayahTopics = topics)
                    } else {
                        current
                    }
                }
                loadRemoteTafsir(ayahNumber, surahNumber, scholar)
            }
            return
        }
        loadRemoteTafsir(ayahNumber, surahNumber, scholar)
    }

    /** Existing encrypted-cache/network tafsir path, unchanged but reusable. */
    private fun loadRemoteTafsir(ayahNumber: Int, surahNumber: Int, scholar: String) {
        val repo = islamicRepository ?: return
        val tafsirId = TafseerDownloadRepository.tafsirIdForScholar(scholar)
            ?: IslamicDataRepository.IBN_KATHIR_TAFSIR_ID
        viewModelScope.launch {
            try {
                val tafsir = repo.getTafsir(
                    ayahNumber = ayahNumber,
                    surahNumber = surahNumber,
                    tafsirId = tafsirId
                )
                _uiState.update { current ->
                    if (current.selectedScholar != scholar ||
                        current.currentSurah.number != surahNumber ||
                        current.activeHighlightedAyah != ayahNumber
                    ) {
                        current
                    } else if (tafsir != null) {
                        current.copy(
                            selectedTafseerContent = tafsir.text,
                            contentNotice = null,
                            tafseerErrorMessage = null
                        )
                    } else {
                        current.copy(
                            selectedTafseerContent = "",
                            contentNotice = "No cached commentary for ${current.currentSurah.nameEnglish} " +
                                "$ayahNumber in $scholar. Download the tafsir pack to read it offline."
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { current ->
                    if (current.selectedScholar != scholar) current
                    else current.copy(
                        contentNotice = null,
                        tafseerErrorMessage = friendlyTafseerError(scholar, e)
                    )
                }
            }
        }
    }

    private fun searchAyahs(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        _uiState.update { it.copy(searchQuery = query, isSearching = trimmed.isNotBlank()) }

        if (trimmed.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        val repo = islamicRepository ?: run {
            _uiState.update { it.copy(isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            try {
                repo.searchAyahs(trimmed).collect { rows ->
                    _uiState.update {
                        it.copy(
                            searchResults = rows.map { row -> row.toSearchResult() },
                            isSearching = false,
                            errorMessage = null
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = "Search failed: ${e.message ?: "database error"}"
                    )
                }
            }
        }
    }

    private fun observeOfflineStatus() {
        val repo = islamicRepository ?: return
        viewModelScope.launch {
            repo.observeOfflineStatus().collect { status ->
                _uiState.update { it.copy(offlineStatus = status) }
            }
        }
    }

    private fun observeTafseerPackages() {
        val repo = tafseerRepository ?: return
        viewModelScope.launch {
            repo.packages.collect { pkgs ->
                _uiState.update {
                    it.copy(
                        downloadedTafseers = pkgs.filter { p -> p.isDownloaded },
                        availableTafseers = pkgs.filter { p -> !p.isDownloaded }
                    )
                }
            }
        }
    }

    /**
     * Mutashabihat / Memory Slip tags are persisted as "surah:ayah" keys so they survive a
     * restart (and exist in guest mode, where no account sync happens).
     */
    private fun restoreHifzAnnotations() {
        val stored = sakinahPreferences?.hifzAnnotations ?: return
        if (stored.isEmpty()) return
        val restored = stored.mapNotNull { (key, tag) ->
            val parts = key.split(":")
            if (parts.size != 2) return@mapNotNull null
            val surah = parts[0].trim().toIntOrNull() ?: return@mapNotNull null
            val ayah = parts[1].trim().toIntOrNull() ?: return@mapNotNull null
            if (tag.isBlank()) null else (surah to ayah) to tag
        }.toMap()
        if (restored.isEmpty()) return
        _uiState.update { it.copy(hifzMistakes = it.hifzMistakes + restored) }
    }

    /**
     * Reopens the chapter, verse and list index of the previous session. Only a first-ever
     * launch (nothing stored) keeps the default of Surah 1, Ayah 1.
     */
    private fun restoreLastReadPosition() {
        val position = quranReaderPreferences?.lastReadPosition ?: return
        pendingTargetAyah = null
        selectedSurahNumber.value = position.surahNumber
        _uiState.update {
            it.copy(
                activeHighlightedAyah = position.ayahNumber,
                restoreScrollIndex = position.scrollIndex,
                restoreSurahNumber = position.surahNumber
            )
        }
    }

    private fun setReadingMode(mode: QuranReadingMode) {
        if (_uiState.value.readingMode == mode) return
        _uiState.update { it.copy(readingMode = mode) }
        quranReaderPreferences?.readingMode = mode
    }

    private fun toggleBookmark(surahNumber: Int, ayahNumber: Int) {
        if (surahNumber < QuranReaderPreferences.MIN_SURAH ||
            surahNumber > QuranReaderPreferences.MAX_SURAH ||
            ayahNumber < 1
        ) {
            return
        }
        val current = _uiState.value.bookmarks
        val alreadySaved = current.any {
            it.surahNumber == surahNumber && it.ayahNumber == ayahNumber
        }
        val updated = if (alreadySaved) {
            current.filterNot { it.surahNumber == surahNumber && it.ayahNumber == ayahNumber }
        } else {
            current + AyahBookmark(surahNumber, ayahNumber, System.currentTimeMillis())
        }
        _uiState.update { it.copy(bookmarks = updated) }
        quranReaderPreferences?.bookmarks = updated
    }

    private fun persistHifzAnnotations(updated: Map<Pair<Int, Int>, String>) {
        val prefs = sakinahPreferences ?: return
        prefs.hifzAnnotations = updated
            .filterValues { it.isNotBlank() }
            .mapKeys { (key, _) -> "${key.first}:${key.second}" }
    }

    private fun setHifzMistakeTag(surah: Int, ayah: Int, wordPos: Int, tag: String) {
        viewModelScope.launch {
            if (tag.isBlank()) {
                hifzDao?.deleteMistakesForAyah(surah, ayah)
            } else {
                hifzDao?.upsertMistake(
                    HifzMistakeEntity(
                        surahNumber = surah,
                        ayahNumber = ayah,
                        wordPosition = wordPos,
                        mistakeType = tag,
                        lastReviewedTimestamp = System.currentTimeMillis()
                    )
                )
            }
            val updated = _uiState.value.hifzMistakes.toMutableMap()
            if (tag.isBlank()) {
                updated.remove(Pair(surah, ayah))
            } else {
                updated[Pair(surah, ayah)] = tag
            }
            _uiState.update { it.copy(hifzMistakes = updated) }
            persistHifzAnnotations(updated)
        }
    }

    private fun downloadTafseerPack(packageId: String) {
        val repo = islamicRepository
        val packRepo = tafseerRepository
        val tafsirId = TafseerDownloadRepository.tafsirIdForPack(packageId)
        if (repo == null || tafsirId == null) {
            _uiState.update {
                it.copy(tafseerErrorMessage = "Could not identify this tafsir pack. Please choose another scholar.")
            }
            return
        }

        val state = _uiState.value
        val surahNumber = state.currentSurah.number
        val ayahNumber = state.activeHighlightedAyah
        val scholar = state.selectedScholar

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloadingTafseer = true,
                    tafseerDownloadProgress = 0f,
                    contentNotice = null,
                    tafseerErrorMessage = null
                )
            }
            try {
                val result = if (packRepo != null) {
                    packRepo.downloadTafseerPackage(packageId) { progress ->
                        _uiState.update { it.copy(tafseerDownloadProgress = progress) }
                    }
                } else {
                    repo.syncTafsirForSurah(surahNumber = surahNumber, tafsirId = tafsirId)
                }
                val error = result.exceptionOrNull()
                val savedChapters = result.getOrNull() ?: 0

                if (error != null) {
                    _uiState.update {
                        it.copy(tafseerErrorMessage = friendlyTafseerError(scholar, error))
                    }
                } else if (savedChapters <= 0) {
                    _uiState.update {
                        it.copy(
                            tafseerErrorMessage = "No $scholar commentary was returned for Surah $surahNumber. " +
                                "Check your connection and try again."
                        )
                    }
                } else {
                    packRepo?.refreshDownloadStatus()
                    _uiState.update {
                        it.copy(
                            activeHighlightedAyah = ayahNumber,
                            selectedTafseerContent = "",
                            contentNotice = null,
                            tafseerErrorMessage = null
                        )
                    }
                    selectAyahForTafseer(ayahNumber)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(tafseerErrorMessage = friendlyTafseerError(scholar, e))
                }
            } finally {
                _uiState.update {
                    it.copy(isDownloadingTafseer = false, tafseerDownloadProgress = 0f)
                }
            }
        }
    }

    private fun friendlyTafseerError(scholar: String, error: Throwable): String {
        val message = error.message.orEmpty().lowercase()
        val offline = error is java.io.IOException ||
            message.contains("connect") ||
            message.contains("timeout") ||
            message.contains("network") ||
            message.contains("unable to resolve")
        return if (offline) {
            "Offline: couldn't download $scholar commentary. Check your internet connection and try again."
        } else {
            "Couldn't download $scholar commentary: ${error.message ?: "unknown error"}"
        }
    }

    private fun SurahMetadataEntity.toSurahModel(): SurahModel = SurahModel(
        number = surahNumber,
        nameEnglish = nameSimple,
        nameArabic = nameArabic,
        englishMeaning = translatedName,
        revelationType = revelationPlace.orEmpty(),
        ayahCount = versesCount,
        juzNumber = 0,
        ayahs = emptyList()
    )

    private fun QuranAyahEntity.toAyahModel(): AyahModel = AyahModel(
        numberInSurah = ayahNumber,
        arabicText = textUthmani,
        arabicIndopak = textIndopak,
        arabicImlaei = textImlaei,
        transliteration = transliteration.orEmpty(),
        translation = translation.orEmpty(),
        tafseerIbnKathir = "",
        tafseerTabari = "",
        tafseerQurtubi = "",
        tafseerSadi = "",
        tafseerMaarifulQuran = "",
        hadithProofQuote = null
    )

    private fun QuranAyahEntity.toSearchResult(): AyahSearchResult = AyahSearchResult(
        surahNumber = surahNumber,
        surahName = surahNameEnglish.orEmpty(),
        ayahNumber = ayahNumber,
        textUthmani = textUthmani,
        translation = translation.orEmpty()
    )
}
