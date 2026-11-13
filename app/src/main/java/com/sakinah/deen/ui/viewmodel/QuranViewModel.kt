package com.sakinah.deen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakinah.deen.data.database.dao.HifzDao
import com.sakinah.deen.data.database.dao.QuranDao
import com.sakinah.deen.data.database.entity.HifzMistakeEntity
import com.sakinah.deen.data.database.entity.QuranAyahEntity
import com.sakinah.deen.data.database.entity.SurahMetadataEntity
import com.sakinah.deen.data.model.AyahModel
import com.sakinah.deen.data.model.SurahModel
import com.sakinah.deen.data.repository.TafseerDownloadRepository
import com.sakinah.deen.data.repository.TafseerPackage
import com.sakinah.deen.network.IslamicDataRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    val isStudyMode: Boolean = true,
    val downloadedTafseers: List<TafseerPackage> = emptyList(),
    val availableTafseers: List<TafseerPackage> = emptyList(),
    val selectedTafseerContent: String = "",
    val selectedScholar: String = "Ibn Kathir",
    val hifzMistakes: Map<Pair<Int, Int>, String> = emptyMap(),
    val isDownloadingTafseer: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<AyahSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    /** Honest status when the requested content is not in the local database yet. */
    val contentNotice: String? = null,
    /** Surfaced failure for a download/search, with [QuranUiIntent.RetrySync] as retry. */
    val errorMessage: String? = null
)

sealed class QuranUiIntent {
    data class SelectSurah(val surahNumber: Int) : QuranUiIntent()
    data class SelectAyahForTafseer(val ayahNumber: Int) : QuranUiIntent()
    data class SelectScholar(val scholarName: String) : QuranUiIntent()
    data class SetHifzMistakeTag(val surah: Int, val ayah: Int, val wordPos: Int, val tag: String) : QuranUiIntent()
    data class DownloadTafseerPack(val packageId: String) : QuranUiIntent()
    data class SearchAyahs(val query: String) : QuranUiIntent()
    data object ClearSearch : QuranUiIntent()
    data object RetrySync : QuranUiIntent()
    data object ToggleLayoutMode : QuranUiIntent()
}

class QuranViewModel(
    private val hifzDao: HifzDao? = null,
    private val tafseerRepository: TafseerDownloadRepository? = null,
    private val islamicRepository: IslamicDataRepository? = null,
    private val quranDao: QuranDao? = null
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
    private var searchJob: Job? = null

    /** True once Room has supplied chapter metadata, i.e. the offline library is usable. */
    private var hasDatabaseSurahs = false

    private val _uiState = MutableStateFlow(
        QuranUiState(
            currentSurah = sampleSurah,
            surahs = listOf(sampleSurah.copy(ayahs = emptyList())),
            selectedTafseerContent = sampleSurah.ayahs[0].tafseerIbnKathir
        )
    )
    val uiState: StateFlow<QuranUiState> = _uiState.asStateFlow()

    init {
        observeTafseerPackages()
        observeRoomSurahs()
        observeSelectedSurahAyahs()
    }

    fun onIntent(intent: QuranUiIntent) {
        when (intent) {
            is QuranUiIntent.SelectSurah -> selectSurah(intent.surahNumber)
            is QuranUiIntent.SelectAyahForTafseer -> selectAyahForTafseer(intent.ayahNumber)
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
            is QuranUiIntent.ToggleLayoutMode -> _uiState.update { it.copy(isStudyMode = !it.isStudyMode) }
        }
    }

    // ------------------------------------------------------------------ Room wiring

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

    private fun observeSelectedSurahAyahs() {
        val repo = islamicRepository ?: return
        viewModelScope.launch {
            selectedSurahNumber.collectLatest { surahNumber ->
                repo.observeSurah(surahNumber).collect { ayahs ->
                    if (ayahs.isEmpty()) {
                        if (!hasDatabaseSurahs) return@collect
                        _uiState.update {
                            if (it.currentSurah.number == surahNumber) {
                                it.copy(
                                    contentNotice = "Surah $surahNumber is not in the offline database yet. " +
                                        "Download it from the Quran library while online."
                                )
                            } else it
                        }
                        return@collect
                    }
                    applyAyahs(surahNumber, ayahs)
                }
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

        val highlighted = mapped.firstOrNull { it.numberInSurah == _uiState.value.activeHighlightedAyah }
            ?: mapped.first()

        _uiState.update {
            it.copy(
                currentSurah = chapter,
                activeHighlightedAyah = highlighted.numberInSurah,
                selectedTafseerContent = highlighted.tafseerIbnKathir,
                contentNotice = null
            )
        }
    }

    private fun selectSurah(surahNumber: Int) {
        val state = _uiState.value
        val listing = state.surahs.find { it.number == surahNumber }
        selectedSurahNumber.value = surahNumber

        if (listing == null) {
            // Before the encrypted database is reachable the bundled Al-Fatihah keeps the
            // reader useful; once it is, never substitute unrelated verses for a request.
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
                    activeHighlightedAyah = 1,
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
                activeHighlightedAyah = 1,
                selectedTafseerContent = "",
                contentNotice = "Loading surah ${listing.nameEnglish}…"
            )
        }

        // Fetch ayahs on demand for any uncached surah.
        if (islamicRepository != null) syncSurah(surahNumber)
    }

    private fun retrySync() {
        val surahNumber = _uiState.value.currentSurah.number
        _uiState.update { it.copy(errorMessage = null) }
        syncSurah(surahNumber)
    }

    private fun syncSurah(surahNumber: Int) {
        val repo = islamicRepository ?: return
        viewModelScope.launch {
            try {
                val result = repo.syncSurah(surahNumber)
                result.exceptionOrNull()?.let { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _uiState.update {
                        it.copy(errorMessage = "Could not load surah $surahNumber: ${error.message ?: "network error"}")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Could not load surah $surahNumber: ${e.message ?: "network error"}")
                }
            }
        }
    }

    /**
     * Tafseer is only shown when it is actually cached (downloaded or bundled). The
     * encrypted database is the single source of truth; unsupported scholars show a
     * download prompt instead of invented commentary.
     */
    private fun selectAyahForTafseer(ayahNumber: Int) {
        val ayah = _uiState.value.currentSurah.ayahs.find { it.numberInSurah == ayahNumber }
            ?: return

        _uiState.update { it.copy(activeHighlightedAyah = ayahNumber) }

        val cached = when (_uiState.value.selectedScholar) {
            "Ibn Kathir" -> ayah.tafseerIbnKathir
            "Al-Tabari" -> ayah.tafseerTabari
            "Al-Qurtubi" -> ayah.tafseerQurtubi
            "Al-Sa'di" -> ayah.tafseerSadi
            "Ma'ariful Quran" -> ayah.tafseerMaarifulQuran
            else -> ayah.tafseerIbnKathir
        }

        if (cached.isNotBlank()) {
            _uiState.update { it.copy(selectedTafseerContent = cached, contentNotice = null) }
            return
        }

        val repo = islamicRepository ?: return
        viewModelScope.launch {
            val tafsir = runCatching { repo.getTafsir(ayahNumber = ayahNumber, surahNumber = _uiState.value.currentSurah.number) }
                .getOrNull()
            _uiState.update { state ->
                if (tafsir != null) {
                    state.copy(selectedTafseerContent = tafsir.text, contentNotice = null)
                } else {
                    state.copy(
                        selectedTafseerContent = "",
                        contentNotice = "No cached tafseer for ${state.currentSurah.nameEnglish} " +
                            "$ayahNumber. Download the tafseer pack to read ${state.selectedScholar} offline."
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

    // ------------------------------------------------------------------ Auxiliary

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

    private fun setHifzMistakeTag(surah: Int, ayah: Int, wordPos: Int, tag: String) {
        viewModelScope.launch {
            hifzDao?.upsertMistake(
                HifzMistakeEntity(
                    surahNumber = surah,
                    ayahNumber = ayah,
                    wordPosition = wordPos,
                    mistakeType = tag,
                    lastReviewedTimestamp = System.currentTimeMillis()
                )
            )
            val updated = _uiState.value.hifzMistakes.toMutableMap()
            updated[Pair(surah, ayah)] = tag
            _uiState.update { it.copy(hifzMistakes = updated) }
        }
    }

    private fun downloadTafseerPack(packageId: String) {
        val repo = tafseerRepository ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingTafseer = true, errorMessage = null) }
            try {
                val result = repo.downloadTafseerPackage(packageId)
                result.exceptionOrNull()?.let { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _uiState.update {
                        it.copy(errorMessage = "Tafseer download failed: ${error.message ?: "network error"}")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Tafseer download failed: ${e.message ?: "network error"}")
                }
            } finally {
                _uiState.update { it.copy(isDownloadingTafseer = false) }
            }
        }
    }

    // ------------------------------------------------------------------ Mapping

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

    /**
     * Bundled/seed content carries only the Uthmani text and translation; classical
     * commentary is left blank so the UI reports a missing download instead of
     * displaying fabricated tafseer.
     */
    private fun QuranAyahEntity.toAyahModel(): AyahModel = AyahModel(
        numberInSurah = ayahNumber,
        arabicText = textUthmani,
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
