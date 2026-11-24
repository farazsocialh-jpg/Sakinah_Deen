import React, { useState } from 'react';
import { BookOpen, Bookmark, ChevronLeft, ChevronRight, Share2, Volume2 } from 'lucide-react';

interface AyahItem {
  reference: string;
  surahName: string;
  arabic: string;
  translation: string;
}

const CURATED_AYAHS: AyahItem[] = [
  {
    reference: '2:152',
    surahName: 'Al-Baqarah',
    arabic: 'فَاذْكُرُونِي أَذْكُرْكُمْ وَاشْكُرُوا لِي وَلَا تَكْفُرُونِ',
    translation: 'So remember Me; I will remember you. And be grateful to Me and do not deny Me.',
  },
  {
    reference: '94:5-6',
    surahName: 'Ash-Sharh',
    arabic: 'فَإِنَّ مَعَ الْعُسْرِ يُسْرًا • إِنَّ مَعَ الْعُسْرِ يُسْرًا',
    translation: 'For indeed, with hardship comes ease. Indeed, with hardship comes ease.',
  },
  {
    reference: '65:3',
    surahName: 'At-Talaq',
    arabic: 'وَيَرْزُقْهُ مِنْ حَيْثُ لَا يَحْتَسِبُ وَمَن يَتَوَكَّلْ عَلَى اللَّهِ فَهُوَ حَسْبُهُ',
    translation: 'And whoever relies upon Allah, then He is sufficient for him. Indeed, Allah will accomplish His purpose.',
  },
  {
    reference: '13:28',
    surahName: 'Ar-Ra\'d',
    arabic: 'الَّذِينَ آمَنُوا وَتَطْمَئِنُّ قُلُوبُهُم بِذِكْرِ اللَّهِ ۗ أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ',
    translation: 'Verily, in the remembrance of Allah do hearts find rest.',
  },
  {
    reference: '39:53',
    surahName: 'Az-Zumar',
    arabic: 'قُلْ يَا عِبَادِيَ الَّذِينَ أَسْرَفُوا عَلَىٰ أَنفُسِهِمْ لَا تَقْنَطُوا مِن رَّحْمَةِ اللَّهِ ۚ إِنَّ اللَّهَ يَغْفِرُ الذُّنُوبَ جَمِيعًا',
    translation: 'Say, "O My servants who have transgressed against themselves, do not despair of the mercy of Allah. Indeed, Allah forgives all sins."',
  },
  {
    reference: '2:186',
    surahName: 'Al-Baqarah',
    arabic: 'وَإِذَا سَأَلَكَ عِبَادِي عَنِّي فَإِنِّي قَرِيبٌ ۖ أُجِيبُ دَعْوَةَ الدَّاعِ إِذَا دَعَانِ',
    translation: 'And when My servants ask you concerning Me, indeed I am near. I respond to the invocation of the supplicant when he calls upon Me.',
  },
];

export const QuranSection: React.FC = () => {
  const [index, setIndex] = useState<number>(0);
  const [bookmarks, setBookmarks] = useState<Record<string, boolean>>({});
  const [isCopied, setIsCopied] = useState<boolean>(false);

  const current = CURATED_AYAHS[index];
  const isBookmarked = !!bookmarks[current.reference];

  const handleNext = () => setIndex((i) => (i + 1) % CURATED_AYAHS.length);
  const handlePrev = () => setIndex((i) => (i - 1 + CURATED_AYAHS.length) % CURATED_AYAHS.length);

  const toggleBookmark = () => {
    setBookmarks((prev) => ({
      ...prev,
      [current.reference]: !prev[current.reference],
    }));
  };

  const copyVerse = () => {
    navigator.clipboard?.writeText?.(
      `"${current.translation}" — Surah ${current.surahName} (${current.reference})`
    );
    setIsCopied(true);
    setTimeout(() => setIsCopied(false), 2000);
  };

  return (
    <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-6 md:p-8 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <BookOpen className="w-5 h-5 text-emerald-400" />
            Curated Ayah of the Day
          </h2>
          <p className="text-slate-400 text-sm mt-0.5">
            Offline rotating verses synchronized with the ImanCore Android glance widget suite
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handlePrev}
            className="p-2 rounded-lg bg-slate-950 border border-slate-800 text-slate-300 hover:text-white hover:border-slate-700 transition"
          >
            <ChevronLeft className="w-4 h-4" />
          </button>
          <span className="text-xs font-mono text-slate-400 px-2">
            {index + 1} / {CURATED_AYAHS.length}
          </span>
          <button
            onClick={handleNext}
            className="p-2 rounded-lg bg-slate-950 border border-slate-800 text-slate-300 hover:text-white hover:border-slate-700 transition"
          >
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Verse Card */}
      <div className="bg-slate-950/80 border border-slate-800/90 rounded-2xl p-6 md:p-8 space-y-6">
        <div className="flex items-center justify-between border-b border-slate-800/80 pb-4">
          <div className="flex items-center gap-3">
            <span className="text-xs font-bold uppercase tracking-wider px-2.5 py-1 rounded-md bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
              Surah {current.surahName}
            </span>
            <span className="text-xs font-mono text-slate-400">Ayah {current.reference}</span>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={toggleBookmark}
              className={`p-2 rounded-lg border transition ${
                isBookmarked
                  ? 'bg-amber-500/20 border-amber-500/40 text-amber-300'
                  : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
              }`}
              title="Bookmark Ayah"
            >
              <Bookmark className={`w-4 h-4 ${isBookmarked ? 'fill-current' : ''}`} />
            </button>
            <button
              onClick={copyVerse}
              className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-400 hover:text-slate-200 transition"
              title="Copy translation"
            >
              <Share2 className="w-4 h-4" />
            </button>
            {isCopied && <span className="text-xs text-emerald-400 font-medium">Copied!</span>}
          </div>
        </div>

        {/* Arabic Calligraphy / Text */}
        <div
          dir="rtl"
          className="text-2xl md:text-3xl text-right leading-loose font-serif text-emerald-100/95 py-2 select-text"
          style={{ fontFamily: "'Amiri', 'Traditional Arabic', serif" }}
        >
          {current.arabic}
        </div>

        {/* Translation */}
        <div className="text-slate-300 text-base md:text-lg leading-relaxed italic border-l-2 border-emerald-500/40 pl-4 py-1">
          "{current.translation}"
        </div>
      </div>
    </div>
  );
};
