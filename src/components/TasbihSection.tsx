import React, { useState } from 'react';
import { RotateCcw, Volume2, Sparkles, CheckCircle } from 'lucide-react';

interface DhikrItem {
  arabic: string;
  transliteration: string;
  translation: string;
  target: number;
}

const DHIKR_LIST: DhikrItem[] = [
  {
    arabic: 'سُبْحَانَ اللَّهِ',
    transliteration: 'SubhanAllah',
    translation: 'Glory be to Allah',
    target: 33,
  },
  {
    arabic: 'الْحَمْدُ لِلَّهِ',
    transliteration: 'Alhamdulillah',
    translation: 'All praise is due to Allah',
    target: 33,
  },
  {
    arabic: 'اللَّهُ أَكْبَرُ',
    transliteration: 'Allahu Akbar',
    translation: 'Allah is the Greatest',
    target: 33,
  },
  {
    arabic: 'أَسْتَغْفِرُ اللَّهَ',
    transliteration: 'Astaghfirullah',
    translation: 'I seek forgiveness from Allah',
    target: 100,
  },
  {
    arabic: 'لَا إِلَٰهَ إِلَّا اللَّهُ',
    transliteration: 'La ilaha illa Allah',
    translation: 'There is no deity except Allah',
    target: 100,
  },
];

export const TasbihSection: React.FC = () => {
  const [dhikrIndex, setDhikrIndex] = useState<number>(0);
  const [count, setCount] = useState<number>(0);
  const [totalRounds, setTotalRounds] = useState<number>(0);

  const currentDhikr = DHIKR_LIST[dhikrIndex];

  const handleTap = () => {
    // Vibration feedback if supported
    if (typeof window !== 'undefined' && 'vibrate' in navigator) {
      try {
        navigator.vibrate(25);
      } catch {
        // Ignore vibration errors
      }
    }

    if (count + 1 >= currentDhikr.target) {
      setCount(0);
      setTotalRounds((r) => r + 1);
    } else {
      setCount((c) => c + 1);
    }
  };

  const handleReset = () => {
    setCount(0);
  };

  const progressPercent = Math.min(100, Math.round((count / currentDhikr.target) * 100));

  return (
    <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-6 md:p-8 space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-emerald-400" />
            Interactive Digital Tasbih Counter
          </h2>
          <p className="text-slate-400 text-sm mt-0.5">
            Synchronized with the Android Glance Tasbih widget (+1 & reset tap callbacks)
          </p>
        </div>

        {/* Dhikr Selector */}
        <div className="flex flex-wrap gap-2">
          {DHIKR_LIST.map((item, i) => (
            <button
              key={item.transliteration}
              onClick={() => {
                setDhikrIndex(i);
                setCount(0);
              }}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg border transition ${
                dhikrIndex === i
                  ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40'
                  : 'bg-slate-950 text-slate-400 border-slate-800 hover:border-slate-700'
              }`}
            >
              {item.transliteration}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8 items-center pt-2">
        {/* Main Clicker Button */}
        <div className="flex flex-col items-center justify-center">
          <button
            onClick={handleTap}
            className="w-56 h-56 rounded-full bg-gradient-to-b from-slate-900 to-slate-950 border-4 border-emerald-500/30 hover:border-emerald-500/60 shadow-2xl shadow-emerald-950/60 flex flex-col items-center justify-center transition-all transform active:scale-95 group focus:outline-none"
          >
            <span className="text-xs uppercase font-bold text-emerald-400 tracking-widest mb-1 group-hover:scale-110 transition">
              TAP TO COUNT
            </span>
            <span className="text-6xl font-mono font-bold text-white tracking-tighter">
              {count}
            </span>
            <span className="text-xs text-slate-500 mt-2 font-mono">
              TARGET: {currentDhikr.target}
            </span>
          </button>

          <div className="flex items-center gap-4 mt-6">
            <button
              onClick={handleReset}
              className="flex items-center gap-1.5 px-4 py-2 text-xs font-medium rounded-lg bg-slate-950 border border-slate-800 text-slate-400 hover:text-white hover:border-slate-700 transition"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              Reset Round
            </button>

            <span className="text-xs font-mono text-slate-500">
              Completed Rounds: <strong className="text-slate-300">{totalRounds}</strong>
            </span>
          </div>
        </div>

        {/* Current Dhikr Information */}
        <div className="bg-slate-950/70 border border-slate-800 rounded-xl p-6 space-y-4">
          <div className="text-center py-4 border-b border-slate-800/80">
            <div
              dir="rtl"
              className="text-3xl font-serif text-emerald-300 mb-2 leading-relaxed"
            >
              {currentDhikr.arabic}
            </div>
            <div className="text-lg font-semibold text-white">
              {currentDhikr.transliteration}
            </div>
            <div className="text-sm text-slate-400 italic mt-0.5">
              "{currentDhikr.translation}"
            </div>
          </div>

          <div>
            <div className="flex justify-between text-xs text-slate-400 mb-1 font-medium">
              <span>Progress</span>
              <span>{progressPercent}%</span>
            </div>
            <div className="w-full h-2.5 bg-slate-900 rounded-full overflow-hidden border border-slate-800">
              <div
                className="h-full bg-gradient-to-r from-emerald-600 to-emerald-400 rounded-full transition-all duration-300"
                style={{ width: `${progressPercent}%` }}
              />
            </div>
          </div>

          <div className="text-xs text-slate-500 flex items-center gap-2 pt-2">
            <CheckCircle className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>
              The Prophet ﷺ said: "Whoever glorifies Allah thirty-three times after every prayer..."
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};
