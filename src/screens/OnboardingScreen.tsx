import React, { useState } from 'react';
import {
  Sprout,
  BookOpen,
  ChevronDown,
  ChevronUp,
  CheckCircle2,
  Sliders,
  Sparkles,
  ArrowRight
} from 'lucide-react';
import { JourneyTrack, Madhhab, CalculationMethod, AsrJuristicMethod } from '../types';
import { soundEffects } from '../utils/audio';

interface OnboardingScreenProps {
  onComplete: () => void;
  selectedTrack: JourneyTrack;
  onTrackChange: (track: JourneyTrack) => void;
  selectedMadhhab: Madhhab;
  onMadhhabChange: (m: Madhhab) => void;
}

export const OnboardingScreen: React.FC<OnboardingScreenProps> = ({
  onComplete,
  selectedTrack,
  onTrackChange,
  selectedMadhhab,
  onMadhhabChange
}) => {
  const [calcMethodOpen, setCalcMethodOpen] = useState(false);
  const [calcMethod, setCalcMethod] = useState<CalculationMethod>('Muslim World League');
  const [asrMethod, setAsrMethod] = useState<AsrJuristicMethod>('Standard (Shafi\'i/Maliki/Hanbali)');

  const calculationOptions: CalculationMethod[] = [
    'Muslim World League',
    'Umm al-Qura (Makkah)',
    'ISNA (North America)',
    'Egyptian General Authority'
  ];

  const madhhabs: Array<{ key: Madhhab; label: string; desc: string }> = [
    { key: 'Hanafi', label: 'Hanafi', desc: 'Imam Abu Hanifa (r.a)' },
    { key: 'Shafii', label: 'Shafi\'i', desc: 'Imam al-Shafi\'i (r.a)' },
    { key: 'Maliki', label: 'Maliki', desc: 'Imam Malik (r.a)' },
    { key: 'Hanbali', label: 'Hanbali', desc: 'Imam Ahmad (r.a)' }
  ];

  const handleTrackSelect = (track: JourneyTrack) => {
    soundEffects.playHapticTap();
    onTrackChange(track);
  };

  const handleContinue = () => {
    soundEffects.playTakbirChime();
    onComplete();
  };

  return (
    <div className="flex-1 flex flex-col justify-between p-5 sm:p-6 bg-[#F9FAF7] text-[#1E293B]">
      {/* Scrollable Content Container */}
      <div className="space-y-6">
        {/* 1. Header Section */}
        <div className="pt-2">
          <div className="flex items-center justify-between mb-1.5">
            <span className="text-xs font-semibold uppercase tracking-wider text-[#10B981]">
              Welcome to Sakinah
            </span>
            {/* Step indicator */}
            <div className="flex items-center gap-1.5">
              <span className="w-6 h-1.5 rounded-full bg-[#0B3B2C]" />
              <span className="w-1.5 h-1.5 rounded-full bg-slate-300" />
              <span className="w-1.5 h-1.5 rounded-full bg-slate-300" />
              <span className="text-[11px] font-medium text-slate-500 ml-1">Step 1 of 3</span>
            </div>
          </div>

          <h1 className="text-3xl sm:text-4xl font-display font-bold text-[#0B3B2C] tracking-tight">
            Sakinah
          </h1>
          <p className="text-sm text-slate-600 mt-1 leading-relaxed">
            Your Sunni Deen Companion · Rooted in the Quran &amp; Sunnah with Classical Orthodoxy.
          </p>
        </div>

        {/* 2. Interactive Track Selection Cards */}
        <div className="space-y-3">
          <label className="text-xs font-bold uppercase tracking-wider text-slate-500 block">
            Select Your Journey Path
          </label>

          {/* Track Card A: New to Islam */}
          <div
            onClick={() => handleTrackSelect('NEW_MUSLIM')}
            className={`p-4 rounded-2xl cursor-pointer transition-all duration-200 border text-left relative ${
              selectedTrack === 'NEW_MUSLIM'
                ? 'bg-white border-[#10B981] shadow-md ring-2 ring-[#10B981]/20'
                : 'bg-white/80 border-[#E2E8F0] hover:border-slate-300 hover:bg-white'
            }`}
          >
            <div className="flex items-start gap-3.5">
              <div
                className={`w-11 h-11 rounded-xl flex items-center justify-center shrink-0 transition-colors ${
                  selectedTrack === 'NEW_MUSLIM'
                    ? 'bg-[#10B981] text-white shadow-sm'
                    : 'bg-emerald-50 text-emerald-800'
                }`}
              >
                <Sprout className="w-6 h-6" />
              </div>

              <div className="flex-1 pr-6">
                <div className="flex items-center gap-2">
                  <h3 className="font-bold text-[#0B3B2C] text-base">
                    New to Islam &amp; Foundational Basics
                  </h3>
                </div>
                <p className="text-xs text-slate-600 mt-1 leading-relaxed">
                  Interactive visual Salah posture guides, Wudu walk-throughs, clear Aqeedah basics, and gradual habit building without overwhelm.
                </p>
              </div>

              {/* Radio Indicator */}
              <div
                className={`w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0 mt-1 transition-colors ${
                  selectedTrack === 'NEW_MUSLIM'
                    ? 'border-[#10B981] bg-[#10B981]'
                    : 'border-slate-300 bg-white'
                }`}
              >
                {selectedTrack === 'NEW_MUSLIM' && (
                  <div className="w-2 h-2 rounded-full bg-white" />
                )}
              </div>
            </div>
          </div>

          {/* Track Card B: Al-Istiqamah */}
          <div
            onClick={() => handleTrackSelect('ISTIQAMAH')}
            className={`p-4 rounded-2xl cursor-pointer transition-all duration-200 border text-left relative ${
              selectedTrack === 'ISTIQAMAH'
                ? 'bg-white border-[#10B981] shadow-md ring-2 ring-[#10B981]/20'
                : 'bg-white/80 border-[#E2E8F0] hover:border-slate-300 hover:bg-white'
            }`}
          >
            <div className="flex items-start gap-3.5">
              <div
                className={`w-11 h-11 rounded-xl flex items-center justify-center shrink-0 transition-colors ${
                  selectedTrack === 'ISTIQAMAH'
                    ? 'bg-[#0B3B2C] text-white shadow-sm'
                    : 'bg-emerald-50 text-emerald-900'
                }`}
              >
                <BookOpen className="w-6 h-6" />
              </div>

              <div className="flex-1 pr-6">
                <div className="flex items-center gap-2">
                  <h3 className="font-bold text-[#0B3B2C] text-base">
                    Al-Istiqamah: Complete Practice &amp; Study
                  </h3>
                </div>
                <p className="text-xs text-slate-600 mt-1 leading-relaxed">
                  Advanced Salah &amp; Qadha matrix, 9 Canonical Hadith books, multi-Tafseer split screen, and Nisab/Zakat multi-asset calculator.
                </p>
              </div>

              {/* Radio Indicator */}
              <div
                className={`w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0 mt-1 transition-colors ${
                  selectedTrack === 'ISTIQAMAH'
                    ? 'border-[#0B3B2C] bg-[#0B3B2C]'
                    : 'border-slate-300 bg-white'
                }`}
              >
                {selectedTrack === 'ISTIQAMAH' && (
                  <div className="w-2 h-2 rounded-full bg-white" />
                )}
              </div>
            </div>
          </div>
        </div>

        {/* 3. Madhhab & Calculation Settings Card (Collapsible) */}
        <div className="bg-white rounded-2xl border border-[#E2E8F0] p-4 shadow-sm overflow-hidden transition-all">
          <button
            type="button"
            onClick={() => setCalcMethodOpen(!calcMethodOpen)}
            className="w-full flex items-center justify-between text-left"
          >
            <div className="flex items-center gap-2.5">
              <Sliders className="w-4 h-4 text-[#0B3B2C]" />
              <div>
                <span className="text-xs font-bold text-[#0B3B2C]">
                  Calculation &amp; Juristic Settings
                </span>
                <p className="text-[11px] text-slate-500">
                  {calcMethod} · {selectedMadhhab}
                </p>
              </div>
            </div>
            {calcMethodOpen ? (
              <ChevronUp className="w-4 h-4 text-slate-400" />
            ) : (
              <ChevronDown className="w-4 h-4 text-slate-400" />
            )}
          </button>

          {calcMethodOpen && (
            <div className="mt-4 pt-3 border-t border-slate-100 space-y-4 animate-fadeIn">
              {/* Calculation Method Dropdown Options */}
              <div>
                <label className="text-[11px] font-semibold text-slate-600 mb-2 block">
                  Prayer Calculation Authority:
                </label>
                <div className="grid grid-cols-1 gap-1.5">
                  {calculationOptions.map((opt) => (
                    <button
                      key={opt}
                      type="button"
                      onClick={() => {
                        soundEffects.playHapticTap();
                        setCalcMethod(opt);
                      }}
                      className={`px-3 py-2 rounded-xl text-xs text-left font-medium transition-colors flex items-center justify-between ${
                        calcMethod === opt
                          ? 'bg-[#0B3B2C] text-white shadow-sm'
                          : 'bg-slate-50 text-slate-700 hover:bg-slate-100'
                      }`}
                    >
                      <span>{opt}</span>
                      {calcMethod === opt && <CheckCircle2 className="w-3.5 h-3.5 text-[#10B981]" />}
                    </button>
                  ))}
                </div>
              </div>

              {/* Madhhab School of Fiqh */}
              <div>
                <label className="text-[11px] font-semibold text-slate-600 mb-2 block">
                  School of Sunni Jurisprudence (Madhhab):
                </label>
                <div className="grid grid-cols-2 gap-2">
                  {madhhabs.map((m) => (
                    <button
                      key={m.key}
                      type="button"
                      onClick={() => {
                        soundEffects.playHapticTap();
                        onMadhhabChange(m.key);
                      }}
                      className={`p-2.5 rounded-xl text-left border transition-all ${
                        selectedMadhhab === m.key
                          ? 'border-[#10B981] bg-emerald-50/70 text-[#0B3B2C]'
                          : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300'
                      }`}
                    >
                      <div className="text-xs font-bold">{m.label}</div>
                      <div className="text-[10px] text-slate-500">{m.desc}</div>
                    </button>
                  ))}
                </div>
              </div>

              {/* Asr Juristic Method Toggle */}
              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <span className="text-[11px] font-semibold text-slate-700">
                    Asr Juristic Calculation Method
                  </span>
                  <span className="text-[10px] font-medium text-emerald-800 bg-emerald-100 px-2 py-0.5 rounded-full">
                    {asrMethod === 'Standard (Shafi\'i/Maliki/Hanbali)' ? '1x Shadow' : '2x Shadow'}
                  </span>
                </div>
                <div className="grid grid-cols-2 gap-2 p-1 bg-slate-100 rounded-xl">
                  <button
                    type="button"
                    onClick={() => {
                      soundEffects.playHapticTap();
                      setAsrMethod('Standard (Shafi\'i/Maliki/Hanbali)');
                    }}
                    className={`py-2 px-2.5 text-xs font-medium rounded-lg transition-all ${
                      asrMethod === 'Standard (Shafi\'i/Maliki/Hanbali)'
                        ? 'bg-white text-[#0B3B2C] shadow-sm font-semibold'
                        : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    Standard (Jumhur)
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      soundEffects.playHapticTap();
                      setAsrMethod('Hanafi (Double Shadow)');
                    }}
                    className={`py-2 px-2.5 text-xs font-medium rounded-lg transition-all ${
                      asrMethod === 'Hanafi (Double Shadow)'
                        ? 'bg-white text-[#0B3B2C] shadow-sm font-semibold'
                        : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    Hanafi (Asr Thani)
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 4. Bottom Action Area */}
      <div className="pt-6 pb-2">
        <button
          type="button"
          onClick={handleContinue}
          className="w-full py-4 px-6 rounded-2xl bg-[#10B981] hover:bg-[#059669] text-[#0B3B2C] font-bold text-base shadow-lg shadow-emerald-600/20 active:scale-[0.98] transition-all flex items-center justify-center gap-2 cursor-pointer"
        >
          <span>Continue to Daily Home</span>
          <ArrowRight className="w-5 h-5 text-[#0B3B2C]" />
        </button>
        <p className="text-center text-[11px] text-slate-500 mt-2.5">
          Settings can be updated anytime in your preferences.
        </p>
      </div>
    </div>
  );
};
