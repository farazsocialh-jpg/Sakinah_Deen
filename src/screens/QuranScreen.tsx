import React, { useState } from 'react';
import {
  BookOpen,
  ChevronDown,
  Layers,
  Sparkles,
  Quote,
  Check,
  X,
  Volume2,
  Bookmark,
  Share2,
  Filter,
  Eye,
  SlidersHorizontal
} from 'lucide-react';
import { SAMPLE_SURAHS } from '../data/islamicData';
import { Ayah, Surah } from '../types';
import { soundEffects } from '../utils/audio';

type TafseerScholar = 'Ibn Kathir' | 'Al-Tabari' | 'Al-Qurtubi' | 'Al-Sa\'di' | 'Ma\'ariful Quran';
type TafseerLanguage = 'English' | 'Arabic' | 'Urdu' | 'Indonesian' | 'French';

export const QuranScreen: React.FC = () => {
  const [selectedSurahIndex, setSelectedSurahIndex] = useState(0);
  const [layoutMode, setLayoutMode] = useState<'study' | 'mushaf'>('study');
  const [selectedAyahNumber, setSelectedAyahNumber] = useState<number | null>(1);
  const [isTafseerSheetOpen, setIsTafseerSheetOpen] = useState(false);
  const [selectedScholar, setSelectedScholar] = useState<TafseerScholar>('Ibn Kathir');
  const [selectedLang, setSelectedLang] = useState<TafseerLanguage>('English');
  const [hifzOverlayActive, setHifzOverlayActive] = useState(false);
  const [surahDropdownOpen, setSurahDropdownOpen] = useState(false);
  const [juzJumpModalOpen, setJuzJumpModalOpen] = useState(false);

  // Mutable copy of surahs for memorization mistake tagging
  const [surahs, setSurahs] = useState<Surah[]>(SAMPLE_SURAHS);

  const currentSurah = surahs[selectedSurahIndex];
  const activeAyah = currentSurah.ayahs.find((a) => a.numberInSurah === selectedAyahNumber) || currentSurah.ayahs[0];

  const handleAyahClick = (ayahNum: number) => {
    soundEffects.playHapticTap();
    setSelectedAyahNumber(ayahNum);
  };

  const handleOpenTafseer = (ayahNum: number) => {
    soundEffects.playHapticTap();
    setSelectedAyahNumber(ayahNum);
    setIsTafseerSheetOpen(true);
  };

  const setAyahMistake = (ayahNum: number, tag: 'slip' | 'tajweed' | 'mastered' | null) => {
    soundEffects.playHapticTap();
    setSurahs((prev) => {
      const copy = JSON.parse(JSON.stringify(prev)) as Surah[];
      const s = copy[selectedSurahIndex];
      const ayah = s.ayahs.find((a) => a.numberInSurah === ayahNum);
      if (ayah) {
        ayah.memorizationMistake = tag;
      }
      return copy;
    });
  };

  const getTafseerText = (ayah: Ayah, scholar: TafseerScholar): string => {
    switch (scholar) {
      case 'Ibn Kathir':
        return ayah.tafseerIbnKathir;
      case 'Al-Tabari':
        return ayah.tafseerTabari;
      case 'Al-Qurtubi':
        return ayah.tafseerQurtubi;
      case 'Al-Sa\'di':
        return ayah.tafseerSadi;
      case 'Ma\'ariful Quran':
        return ayah.tafseerMaarifulQuran;
      default:
        return ayah.tafseerIbnKathir;
    }
  };

  return (
    <div className="flex-1 flex flex-col p-4 sm:p-5 bg-[#F9FAF7] text-[#1E293B] space-y-4 pb-20 relative">
      {/* 1. Top Navigation & Controls */}
      <div className="flex items-center justify-between gap-2">
        {/* Surah Selector Dropdown Button */}
        <div className="relative">
          <button
            type="button"
            onClick={() => setSurahDropdownOpen(!surahDropdownOpen)}
            className="px-3 py-1.5 rounded-2xl bg-white border border-[#E2E8F0] shadow-xs flex items-center gap-2 text-left"
          >
            <div className="w-7 h-7 rounded-lg bg-emerald-100 text-[#0B3B2C] flex items-center justify-center font-bold text-xs">
              {currentSurah.number}
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <span className="font-bold text-xs text-[#0B3B2C]">
                  {currentSurah.nameEnglish}
                </span>
                <span className="text-[10px] bg-slate-100 text-slate-600 px-1.5 py-0.2 rounded font-medium">
                  {currentSurah.revelationType}
                </span>
              </div>
              <div className="text-[10px] text-slate-500 font-arabic">
                {currentSurah.nameArabic} · {currentSurah.ayahCount} Ayahs
              </div>
            </div>
            <ChevronDown className="w-3.5 h-3.5 text-slate-400 ml-1" />
          </button>

          {/* Surah Dropdown Modal */}
          {surahDropdownOpen && (
            <div className="absolute top-full left-0 mt-1.5 w-64 bg-white rounded-2xl border border-slate-200 shadow-xl z-40 p-2 space-y-1 animate-fadeIn">
              <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 px-2 py-1 block">
                Select Surah
              </span>
              {surahs.map((s, idx) => (
                <button
                  key={s.number}
                  type="button"
                  onClick={() => {
                    soundEffects.playHapticTap();
                    setSelectedSurahIndex(idx);
                    setSelectedAyahNumber(1);
                    setSurahDropdownOpen(false);
                  }}
                  className={`w-full p-2 rounded-xl text-left flex items-center justify-between transition-colors ${
                    selectedSurahIndex === idx
                      ? 'bg-emerald-50 text-[#0B3B2C] font-bold'
                      : 'hover:bg-slate-50 text-slate-700'
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <span className="w-5 text-center text-xs text-slate-400 font-mono">
                      {s.number}
                    </span>
                    <div>
                      <div className="text-xs">{s.nameEnglish}</div>
                      <div className="text-[10px] text-slate-500">{s.englishMeaning}</div>
                    </div>
                  </div>
                  <span className="font-arabic text-sm text-[#0B3B2C]">{s.nameArabic}</span>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Juz Quick-Jump Button */}
        <button
          type="button"
          onClick={() => setJuzJumpModalOpen(true)}
          className="px-2.5 py-1.5 rounded-xl bg-white border border-[#E2E8F0] text-xs font-semibold text-slate-700 hover:bg-slate-50 flex items-center gap-1 shadow-xs"
        >
          <Layers className="w-3.5 h-3.5 text-[#10B981]" />
          <span>Juz {currentSurah.juzNumber}</span>
        </button>

        {/* Mode Switcher: Study Mode vs Mus-haf Page Mode */}
        <div className="flex items-center bg-slate-200 p-0.5 rounded-xl">
          <button
            type="button"
            onClick={() => {
              soundEffects.playHapticTap();
              setLayoutMode('study');
            }}
            className={`px-2.5 py-1 text-[11px] font-semibold rounded-lg transition-all ${
              layoutMode === 'study'
                ? 'bg-[#0B3B2C] text-white shadow-xs'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Study
          </button>
          <button
            type="button"
            onClick={() => {
              soundEffects.playHapticTap();
              setLayoutMode('mushaf');
            }}
            className={`px-2.5 py-1 text-[11px] font-semibold rounded-lg transition-all ${
              layoutMode === 'mushaf'
                ? 'bg-[#0B3B2C] text-white shadow-xs'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Mus-haf
          </button>
        </div>
      </div>

      {/* Hifz Memorization Toolbar Toggle */}
      <div className="flex items-center justify-between px-1">
        <button
          type="button"
          onClick={() => {
            soundEffects.playHapticTap();
            setHifzOverlayActive(!hifzOverlayActive);
          }}
          className={`px-3 py-1 rounded-full text-xs font-semibold transition-all flex items-center gap-1.5 border ${
            hifzOverlayActive
              ? 'bg-[#0B3B2C] text-[#10B981] border-[#0B3B2C] shadow-xs'
              : 'bg-white text-slate-600 border-slate-200 hover:border-slate-300'
          }`}
        >
          <Sparkles className="w-3.5 h-3.5" />
          <span>Hifz Memorization Tools</span>
        </button>

        {hifzOverlayActive && (
          <div className="flex items-center gap-2 text-[10px] text-slate-500">
            <span className="flex items-center gap-1">
              <span className="w-2 h-2 rounded-full bg-red-500" /> Slip
            </span>
            <span className="flex items-center gap-1">
              <span className="w-2 h-2 rounded-full bg-amber-500" /> Tajweed
            </span>
            <span className="flex items-center gap-1">
              <span className="w-2 h-2 rounded-full bg-emerald-500" /> Mastered
            </span>
          </div>
        )}
      </div>

      {/* Surah Bismillah Banner */}
      {currentSurah.number !== 9 && (
        <div className="text-center py-2">
          <p className="font-arabic text-xl sm:text-2xl text-[#0B3B2C] tracking-wide">
            بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ
          </p>
        </div>
      )}

      {/* 2. Reading Canvas */}
      {layoutMode === 'study' ? (
        /* Verse-by-Verse Study Mode */
        <div className="space-y-3">
          {currentSurah.ayahs.map((ayah) => {
            const isSelected = selectedAyahNumber === ayah.numberInSurah;

            return (
              <div
                key={ayah.numberInSurah}
                onClick={() => handleAyahClick(ayah.numberInSurah)}
                className={`p-4 rounded-3xl border transition-all cursor-pointer ${
                  isSelected
                    ? 'bg-emerald-50/80 border-[#10B981] ring-2 ring-[#10B981]/20 shadow-xs'
                    : 'bg-white border-[#E2E8F0] hover:border-slate-300'
                }`}
              >
                {/* Header row with Ayah marker & Hifz badges */}
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    {/* End-of-ayah marker */}
                    <div className="w-6 h-6 rounded-full border border-emerald-600/40 bg-emerald-100/50 flex items-center justify-center text-[10px] font-bold text-[#0B3B2C] font-mono">
                      {ayah.numberInSurah}
                    </div>
                    <span className="text-xs text-slate-400 font-medium">
                      Ayah {ayah.numberInSurah}
                    </span>

                    {/* Hifz tag indicator */}
                    {ayah.memorizationMistake === 'slip' && (
                      <span className="text-[10px] bg-red-100 text-red-700 px-2 py-0.5 rounded-full font-bold">
                        Slip marked
                      </span>
                    )}
                    {ayah.memorizationMistake === 'tajweed' && (
                      <span className="text-[10px] bg-amber-100 text-amber-800 px-2 py-0.5 rounded-full font-bold">
                        Tajweed note
                      </span>
                    )}
                    {ayah.memorizationMistake === 'mastered' && (
                      <span className="text-[10px] bg-emerald-100 text-emerald-800 px-2 py-0.5 rounded-full font-bold">
                        Mastered
                      </span>
                    )}
                  </div>

                  {/* Actions: Tafseer button & Sound */}
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        soundEffects.playRecitation(2.5);
                      }}
                      title="Listen to Ayah"
                      className="p-1.5 rounded-lg text-slate-400 hover:text-[#0B3B2C] hover:bg-slate-100"
                    >
                      <Volume2 className="w-4 h-4" />
                    </button>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleOpenTafseer(ayah.numberInSurah);
                      }}
                      className="px-2.5 py-1 rounded-xl bg-emerald-900/10 hover:bg-emerald-900/20 text-[#0B3B2C] text-xs font-semibold flex items-center gap-1 border border-emerald-700/20"
                    >
                      <BookOpen className="w-3.5 h-3.5 text-[#10B981]" />
                      <span>Tafseer</span>
                    </button>
                  </div>
                </div>

                {/* Uthmani Arabic Text */}
                <div className="py-2 text-right">
                  <p className="font-arabic text-2xl sm:text-3xl text-[#0B3B2C] leading-loose">
                    {ayah.arabicText}
                    <span className="text-emerald-600 font-arabic text-xl mr-2">
                      {' '}۝{ayah.numberInSurah}
                    </span>
                  </p>
                </div>

                {/* Transliteration */}
                <div className="text-xs text-slate-500 font-medium italic mt-1">
                  {ayah.transliteration}
                </div>

                {/* English Translation */}
                <div className="text-xs text-slate-800 font-medium mt-1 leading-relaxed">
                  {ayah.translation}
                </div>

                {/* Hifz Quick Marker Toolbar (Visible when Hifz mode is active) */}
                {hifzOverlayActive && (
                  <div className="mt-3 pt-2.5 border-t border-slate-200/80 flex items-center justify-between text-xs">
                    <span className="text-[11px] text-slate-500 font-semibold">
                      Mark Memorization Status:
                    </span>
                    <div className="flex items-center gap-1.5">
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          setAyahMistake(ayah.numberInSurah, 'slip');
                        }}
                        className={`px-2 py-0.5 rounded-lg text-[10px] font-bold border ${
                          ayah.memorizationMistake === 'slip'
                            ? 'bg-red-500 text-white border-red-500'
                            : 'bg-white text-red-600 border-red-200 hover:bg-red-50'
                        }`}
                      >
                        ● Slip
                      </button>
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          setAyahMistake(ayah.numberInSurah, 'tajweed');
                        }}
                        className={`px-2 py-0.5 rounded-lg text-[10px] font-bold border ${
                          ayah.memorizationMistake === 'tajweed'
                            ? 'bg-amber-500 text-white border-amber-500'
                            : 'bg-white text-amber-700 border-amber-200 hover:bg-amber-50'
                        }`}
                      >
                        ● Tajweed
                      </button>
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          setAyahMistake(ayah.numberInSurah, 'mastered');
                        }}
                        className={`px-2 py-0.5 rounded-lg text-[10px] font-bold border ${
                          ayah.memorizationMistake === 'mastered'
                            ? 'bg-emerald-600 text-white border-emerald-600'
                            : 'bg-white text-emerald-700 border-emerald-200 hover:bg-emerald-50'
                        }`}
                      >
                        ● Mastered
                      </button>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      ) : (
        /* Mus-haf Page Continuous Mode */
        <div className="bg-[#FFFDF9] rounded-3xl p-6 border-2 border-emerald-900/15 shadow-sm text-right leading-loose">
          <p className="font-arabic text-2xl sm:text-3xl text-[#0B3B2C] leading-loose">
            {currentSurah.ayahs.map((a) => (
              <span
                key={a.numberInSurah}
                onClick={() => handleOpenTafseer(a.numberInSurah)}
                className={`cursor-pointer px-1 py-0.5 rounded transition-colors ${
                  selectedAyahNumber === a.numberInSurah
                    ? 'bg-emerald-200 text-emerald-950 font-bold'
                    : 'hover:bg-emerald-50'
                }`}
              >
                {a.arabicText}{' '}
                <span className="text-emerald-700 font-arabic text-xl">۝{a.numberInSurah} </span>
              </span>
            ))}
          </p>
        </div>
      )}

      {/* 3. Expandable Tafseer Bottom Sheet */}
      {isTafseerSheetOpen && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-end justify-center p-0 animate-fadeIn">
          <div className="bg-white rounded-t-3xl w-full max-w-lg max-h-[82vh] flex flex-col shadow-2xl border-t border-slate-200 animate-slideUp">
            {/* Sheet Drag Handle */}
            <div className="w-10 h-1.5 bg-slate-300 rounded-full mx-auto my-3" />

            {/* Sheet Header */}
            <div className="px-5 pb-3 border-b border-slate-100 flex items-center justify-between">
              <div>
                <span className="text-xs font-bold text-[#10B981] uppercase tracking-wider">
                  Sunni Tafseer Studio
                </span>
                <h3 className="text-base font-bold text-[#0B3B2C]">
                  {currentSurah.nameEnglish} · Ayah {activeAyah.numberInSurah}
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setIsTafseerSheetOpen(false)}
                className="p-1 text-slate-400 hover:text-slate-700"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Scholar Tabs: [Ibn Kathir] [Al-Tabari] [Al-Qurtubi] [Al-Sa'di] [Ma'ariful Quran] */}
            <div className="flex items-center gap-1.5 px-5 py-2.5 border-b border-slate-100 overflow-x-auto scrollbar-none">
              {(
                ['Ibn Kathir', 'Al-Tabari', 'Al-Qurtubi', "Al-Sa'di", "Ma'ariful Quran"] as TafseerScholar[]
              ).map((scholar) => (
                <button
                  key={scholar}
                  type="button"
                  onClick={() => {
                    soundEffects.playHapticTap();
                    setSelectedScholar(scholar);
                  }}
                  className={`px-3 py-1 text-xs rounded-full whitespace-nowrap transition-all font-medium ${
                    selectedScholar === scholar
                      ? 'bg-[#0B3B2C] text-white shadow-xs font-bold'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                >
                  {scholar}
                </button>
              ))}
            </div>

            {/* Language Selector Chips */}
            <div className="flex items-center gap-1 px-5 py-1.5 bg-slate-50 text-[11px] text-slate-500 overflow-x-auto">
              <span className="font-semibold text-slate-700 mr-1">Language:</span>
              {(['English', 'Arabic', 'Urdu', 'Indonesian', 'French'] as TafseerLanguage[]).map(
                (lang) => (
                  <button
                    key={lang}
                    type="button"
                    onClick={() => {
                      soundEffects.playHapticTap();
                      setSelectedLang(lang);
                    }}
                    className={`px-2 py-0.5 rounded font-medium ${
                      selectedLang === lang
                        ? 'bg-emerald-200 text-emerald-950 font-bold'
                        : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    {lang}
                  </button>
                )
              )}
            </div>

            {/* Tafseer Scrollable Body */}
            <div className="p-5 overflow-y-auto space-y-3.5 flex-1 text-xs">
              {/* Ayah Snippet Banner */}
              <div className="p-3 bg-emerald-50 rounded-2xl border border-emerald-200 text-right">
                <p className="font-arabic text-xl text-[#0B3B2C]">
                  {activeAyah.arabicText}
                </p>
                <p className="text-[11px] text-slate-600 text-left mt-1 italic">
                  "{activeAyah.translation}"
                </p>
              </div>

              {/* Scholar Exegesis Matn */}
              <div className="space-y-1.5">
                <div className="flex items-center gap-2">
                  <span className="font-bold text-sm text-[#0B3B2C]">
                    Tafseer {selectedScholar}
                  </span>
                  <span className="text-[10px] bg-emerald-100 text-emerald-800 px-2 py-0.5 rounded-full font-medium">
                    Verified Sunni Source
                  </span>
                </div>
                <p className="text-slate-700 leading-relaxed text-xs">
                  {getTafseerText(activeAyah, selectedScholar)}
                </p>
              </div>

              {/* Distinct Quote Box for Narrated Athar & Hadith Proofs */}
              {activeAyah.hadithProofQuote && (
                <div className="p-3 bg-slate-50 rounded-2xl border border-slate-200 space-y-1.5">
                  <div className="flex items-center gap-1.5 text-emerald-800 font-bold text-[11px]">
                    <Quote className="w-3.5 h-3.5 text-[#10B981]" />
                    <span>Narrated Hadith Proof &amp; Classical Athar:</span>
                  </div>
                  <p className="text-slate-700 italic leading-relaxed text-[11px]">
                    "{activeAyah.hadithProofQuote}"
                  </p>
                </div>
              )}
            </div>

            {/* Bottom Sheet Action Footer */}
            <div className="p-4 border-t border-slate-100 flex items-center justify-end">
              <button
                type="button"
                onClick={() => setIsTafseerSheetOpen(false)}
                className="w-full py-2.5 rounded-xl bg-[#0B3B2C] text-white font-semibold text-xs"
              >
                Close Studio
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Juz Jump Modal */}
      {juzJumpModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl p-5 w-full max-w-sm space-y-4 shadow-xl border border-slate-200 animate-fadeIn">
            <div className="flex items-center justify-between">
              <h3 className="font-bold text-base text-[#0B3B2C]">Quick Juz Selector</h3>
              <button onClick={() => setJuzJumpModalOpen(false)} className="p-1 text-slate-400">
                <X className="w-5 h-5" />
              </button>
            </div>
            <p className="text-xs text-slate-500">
              Select one of the 30 Ajza' of the Holy Quran:
            </p>
            <div className="grid grid-cols-6 gap-2 max-h-56 overflow-y-auto p-1">
              {Array.from({ length: 30 }, (_, i) => i + 1).map((juz) => (
                <button
                  key={juz}
                  onClick={() => {
                    soundEffects.playHapticTap();
                    setJuzJumpModalOpen(false);
                  }}
                  className={`py-2 rounded-xl text-xs font-bold transition-colors ${
                    juz === currentSurah.juzNumber
                      ? 'bg-[#0B3B2C] text-white'
                      : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                  }`}
                >
                  {juz}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
