package com.sakinah.deen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakinah.deen.data.database.dao.HifzDao
import com.sakinah.deen.data.database.entity.HifzMistakeEntity
import com.sakinah.deen.data.model.AyahModel
import com.sakinah.deen.data.model.SurahModel
import com.sakinah.deen.data.repository.TafseerDownloadRepository
import com.sakinah.deen.data.repository.TafseerPackage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val isDownloadingTafseer: Boolean = false
)

sealed class QuranUiIntent {
    data class SelectSurah(val surahNumber: Int) : QuranUiIntent()
    data class SelectAyahForTafseer(val ayahNumber: Int) : QuranUiIntent()
    data class SelectScholar(val scholarName: String) : QuranUiIntent()
    data class SetHifzMistakeTag(val surah: Int, val ayah: Int, val wordPos: Int, val tag: String) : QuranUiIntent()
    data class DownloadTafseerPack(val packageId: String) : QuranUiIntent()
    data object ToggleLayoutMode : QuranUiIntent()
}

class QuranViewModel(
    private val hifzDao: HifzDao,
    private val tafseerRepository: TafseerDownloadRepository
) : ViewModel() {

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

    private val _uiState = MutableStateFlow(
        QuranUiState(
            currentSurah = sampleSurah,
            surahs = listOf(sampleSurah),
            selectedTafseerContent = sampleSurah.ayahs[0].tafseerIbnKathir
        )
    )
    val uiState: StateFlow<QuranUiState> = _uiState.asStateFlow()

    init {
        observeTafseerPackages()
    }

    fun onIntent(intent: QuranUiIntent) {
        when (intent) {
            is QuranUiIntent.SelectSurah -> {
                val found = _uiState.value.surahs.find { it.number == intent.surahNumber } ?: sampleSurah
                _uiState.update {
                    it.copy(
                        currentSurah = found,
                        activeHighlightedAyah = 1,
                        selectedTafseerContent = found.ayahs.firstOrNull()?.tafseerIbnKathir ?: ""
                    )
                }
            }
            is QuranUiIntent.SelectAyahForTafseer -> {
                val ayah = _uiState.value.currentSurah.ayahs.find { it.numberInSurah == intent.ayahNumber }
                val content = when (_uiState.value.selectedScholar) {
                    "Al-Tabari" -> ayah?.tafseerTabari
                    "Al-Qurtubi" -> ayah?.tafseerQurtubi
                    "Al-Sa'di" -> ayah?.tafseerSadi
                    "Ma'ariful Quran" -> ayah?.tafseerMaarifulQuran
                    else -> ayah?.tafseerIbnKathir
                } ?: ""
                _uiState.update {
                    it.copy(
                        activeHighlightedAyah = intent.ayahNumber,
                        selectedTafseerContent = content
                    )
                }
            }
            is QuranUiIntent.SelectScholar -> {
                _uiState.update { it.copy(selectedScholar = intent.scholarName) }
                onIntent(QuranUiIntent.SelectAyahForTafseer(_uiState.value.activeHighlightedAyah))
            }
            is QuranUiIntent.SetHifzMistakeTag -> setHifzMistakeTag(intent.surah, intent.ayah, intent.wordPos, intent.tag)
            is QuranUiIntent.DownloadTafseerPack -> downloadTafseerPack(intent.packageId)
            is QuranUiIntent.ToggleLayoutMode -> _uiState.update { it.copy(isStudyMode = !it.isStudyMode) }
        }
    }

    private fun observeTafseerPackages() {
        viewModelScope.launch {
            tafseerRepository.packages.collect { pkgs ->
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
            hifzDao.upsertMistake(
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
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingTafseer = true) }
            tafseerRepository.downloadTafseerPackage(packageId)
            _uiState.update { it.copy(isDownloadingTafseer = false) }
        }
    }
}
