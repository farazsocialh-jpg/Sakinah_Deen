import React, { useState, useEffect } from 'react';
import {
  Calendar,
  Volume2,
  Navigation,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  X,
  Plus,
  Minus,
  Sparkles,
  Award,
  Clock,
  MapPin,
  TrendingUp,
  Flame
} from 'lucide-react';
import { PrayerItem, QadhaLedger } from '../types';
import { soundEffects } from '../utils/audio';

interface SalahScreenProps {
  prayers: PrayerItem[];
  onToggleFard: (prayerId: string) => void;
  onToggleJamaah: (prayerId: string) => void;
  onToggleSunnah: (prayerId: string) => void;
  onToggleNawafil: (prayerId: string) => void;
  qadhaLedger: QadhaLedger;
  onDecrementQadha: (prayerKey: keyof Omit<QadhaLedger, 'dailyGoalPace'>) => void;
  onIncrementQadha: (prayerKey: keyof Omit<QadhaLedger, 'dailyGoalPace'>) => void;
  onOpenVisualGuide: () => void;
}

export const SalahScreen: React.FC<SalahScreenProps> = ({
  prayers,
  onToggleFard,
  onToggleJamaah,
  onToggleSunnah,
  onToggleNawafil,
  qadhaLedger,
  onDecrementQadha,
  onIncrementQadha,
  onOpenVisualGuide
}) => {
  const [activeTab, setActiveTab] = useState<'today' | 'qadha'>('today');
  const [expandedPrayerId, setExpandedPrayerId] = useState<string | null>('asr');
  const [isPlayingAdhan, setIsPlayingAdhan] = useState(false);
  const [mosqueModalOpen, setMosqueModalOpen] = useState(false);
  const [qadhaModalOpen, setQadhaModalOpen] = useState(false);

  // Countdown simulation (1 hr 24 mins)
  const [secondsRemaining, setSecondsRemaining] = useState(5040);

  useEffect(() => {
    const timer = setInterval(() => {
      setSecondsRemaining((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  const formatCountdown = (totalSecs: number) => {
    const h = Math.floor(totalSecs / 3600);
    const m = Math.floor((totalSecs % 3600) / 60);
    const s = totalSecs % 60;
    return `${h.toString().padStart(2, '0')}h ${m.toString().padStart(2, '0')}m ${s.toString().padStart(2, '0')}s`;
  };

  const handlePlayAdhan = () => {
    setIsPlayingAdhan(true);
    soundEffects.playAdhanMotif(() => {
      setIsPlayingAdhan(false);
    });
  };

  const completedFardCount = prayers.filter((p) => p.fardRakats > 0 && p.isFardCompleted).length;
  const totalFardCount = prayers.filter((p) => p.fardRakats > 0).length;

  const totalMissedPrayers =
    qadhaLedger.fajr +
    qadhaLedger.dhuhr +
    qadhaLedger.asr +
    qadhaLedger.maghrib +
    qadhaLedger.isha +
    qadhaLedger.witr;

  const daysToComplete = Math.ceil(totalMissedPrayers / (qadhaLedger.dailyGoalPace || 1));

  return (
    <div className="flex-1 flex flex-col p-4 sm:p-5 bg-[#F9FAF7] text-[#1E293B] space-y-4 pb-20">
      {/* 1. Top Bar & Dynamic Date Header */}
      <div className="flex items-center justify-between">
        <div>
          <div className="flex items-center gap-1.5 text-xs font-bold text-[#0B3B2C]">
            <Calendar className="w-3.5 h-3.5 text-[#10B981]" />
            <span>14 Jumada al-Ula 1448 AH</span>
          </div>
          <p className="text-[11px] text-slate-500 font-medium">
            Tuesday · 11 November 2026 · London, UK
          </p>
        </div>

        {/* Quick Mode Switcher Pill */}
        <div className="flex items-center bg-slate-200/80 p-0.5 rounded-full border border-slate-300">
          <button
            onClick={() => {
              soundEffects.playHapticTap();
              setActiveTab('today');
            }}
            className={`px-3 py-1 text-xs font-semibold rounded-full transition-all ${
              activeTab === 'today'
                ? 'bg-[#0B3B2C] text-white shadow-xs'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Today ({completedFardCount}/{totalFardCount})
          </button>
          <button
            onClick={() => {
              soundEffects.playHapticTap();
              setActiveTab('qadha');
            }}
            className={`px-3 py-1 text-xs font-semibold rounded-full transition-all ${
              activeTab === 'qadha'
                ? 'bg-[#0B3B2C] text-white shadow-xs'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Qadha Tracker
          </button>
        </div>
      </div>

      {activeTab === 'today' ? (
        <>
          {/* 2. Hero Active Prayer Card (Deep Emerald Gradient) */}
          <div className="rounded-3xl bg-gradient-to-br from-[#0B3B2C] via-[#0F4836] to-[#134E3A] text-white p-5 shadow-lg border border-emerald-800/40 relative overflow-hidden">
            {/* Subtle Islamic Arabesque background geometry */}
            <div className="absolute -right-8 -bottom-8 w-40 h-40 rounded-full border-[18px] border-emerald-400/5 pointer-events-none" />
            <div className="absolute right-12 -top-12 w-32 h-32 rounded-full border-8 border-emerald-400/5 pointer-events-none" />

            <div className="relative z-10 space-y-3.5">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-bold tracking-widest text-[#10B981] uppercase bg-emerald-950/60 px-2.5 py-1 rounded-full border border-emerald-500/20">
                  Current / Next Prayer
                </span>
                {/* Live Countdown Timer Chip */}
                <div className="flex items-center gap-1.5 bg-black/30 backdrop-blur-sm px-2.5 py-1 rounded-full text-xs font-mono font-medium text-emerald-200 border border-emerald-500/30">
                  <Clock className="w-3.5 h-3.5 text-[#10B981] animate-spin" />
                  <span>Starts in {formatCountdown(secondsRemaining)}</span>
                </div>
              </div>

              {/* Prayer Name & Arabic */}
              <div className="flex items-baseline justify-between pt-1">
                <div>
                  <h2 className="text-3xl font-display font-bold tracking-tight text-white flex items-center gap-2">
                    <span>ASR</span>
                    <span className="text-xl font-normal text-emerald-300/80 font-arabic">
                      العصر
                    </span>
                  </h2>
                  <p className="text-xs text-emerald-200/80 mt-0.5">
                    Standard Time: 03:52 PM · 4 Rakats Fard
                  </p>
                </div>

                <button
                  onClick={onOpenVisualGuide}
                  className="px-2.5 py-1 rounded-xl bg-[#10B981]/20 hover:bg-[#10B981]/30 text-emerald-200 text-xs font-medium border border-emerald-400/30 transition-colors flex items-center gap-1"
                >
                  <Sparkles className="w-3 h-3 text-[#10B981]" />
                  <span>How to Pray</span>
                </button>
              </div>

              {/* Elapsed Window Progress Bar */}
              <div className="space-y-1">
                <div className="flex justify-between text-[10px] text-emerald-200/70">
                  <span>Dhuhr 12:45 PM</span>
                  <span>Window: 65% Elapsed</span>
                  <span>Asr 03:52 PM</span>
                </div>
                <div className="w-full h-2 rounded-full bg-black/40 overflow-hidden p-0.5">
                  <div
                    className="h-full rounded-full bg-gradient-to-r from-emerald-400 to-[#10B981] transition-all duration-500"
                    style={{ width: '65%' }}
                  />
                </div>
              </div>

              {/* Quick Action Buttons */}
              <div className="grid grid-cols-2 gap-2 pt-1">
                <button
                  type="button"
                  onClick={handlePlayAdhan}
                  disabled={isPlayingAdhan}
                  className={`py-2 px-3 rounded-xl text-xs font-semibold flex items-center justify-center gap-1.5 transition-all ${
                    isPlayingAdhan
                      ? 'bg-[#10B981] text-emerald-950 font-bold animate-pulse'
                      : 'bg-white/10 hover:bg-white/20 text-white border border-white/10'
                  }`}
                >
                  <Volume2 className="w-4 h-4 text-[#10B981]" />
                  <span>{isPlayingAdhan ? 'Adhan Playing...' : 'Hear Adhan'}</span>
                </button>

                <button
                  type="button"
                  onClick={() => setMosqueModalOpen(true)}
                  className="py-2 px-3 rounded-xl bg-white/10 hover:bg-white/20 text-white text-xs font-semibold flex items-center justify-center gap-1.5 border border-white/10 transition-colors"
                >
                  <Navigation className="w-4 h-4 text-[#10B981]" />
                  <span>Nearby Mosque</span>
                </button>
              </div>
            </div>
          </div>

          {/* 3. Daily Prayer Matrix (Vertical List of Cards) */}
          <div className="space-y-2.5">
            <div className="flex items-center justify-between px-1">
              <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
                Today's Daily Prayer Matrix
              </span>
              <span className="text-xs font-medium text-emerald-800">
                {completedFardCount} of {totalFardCount} Fard Completed
              </span>
            </div>

            <div className="space-y-2">
              {prayers.map((prayer) => {
                const isExpanded = expandedPrayerId === prayer.id;
                const isSunrise = prayer.id === 'sunrise';

                return (
                  <div
                    key={prayer.id}
                    className={`rounded-2xl border transition-all duration-200 bg-white shadow-xs ${
                      prayer.isFardCompleted
                        ? 'border-emerald-300 ring-1 ring-emerald-400/20'
                        : 'border-[#E2E8F0]'
                    }`}
                  >
                    {/* Top Row: Click to Expand / View Times */}
                    <div className="p-3.5 flex items-center justify-between gap-3">
                      {/* Left: Prayer Details */}
                      <div
                        onClick={() => {
                          soundEffects.playHapticTap();
                          setExpandedPrayerId(isExpanded ? null : prayer.id);
                        }}
                        className="flex items-center gap-3 cursor-pointer flex-1"
                      >
                        <div
                          className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 font-bold text-xs ${
                            prayer.isFardCompleted
                              ? 'bg-emerald-100 text-[#0B3B2C]'
                              : isSunrise
                              ? 'bg-amber-100 text-amber-900'
                              : 'bg-slate-100 text-slate-700'
                          }`}
                        >
                          {isSunrise ? '☀️' : prayer.nameArabic.slice(0, 2)}
                        </div>

                        <div>
                          <div className="flex items-center gap-2">
                            <span className="font-bold text-sm text-[#0B3B2C]">
                              {prayer.nameEnglish}
                            </span>
                            <span className="text-xs text-slate-400 font-arabic">
                              {prayer.nameArabic}
                            </span>
                            {prayer.isFardCompleted && (
                              <span className="text-[10px] font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full border border-emerald-200">
                                Completed
                              </span>
                            )}
                          </div>
                          <span className="text-xs font-medium text-slate-500">
                            {prayer.time} · {isSunrise ? 'Ishraq Window' : `${prayer.fardRakats} Rakat Fard`}
                          </span>
                        </div>
                      </div>

                      {/* Right Action Icons (Fard check & Jama'ah toggle) */}
                      {!isSunrise && (
                        <div className="flex items-center gap-2 shrink-0">
                          {/* Jama'ah Mosque Icon Toggle */}
                          <button
                            type="button"
                            title="Toggled if prayed in congregation (Jama'ah - 27x reward)"
                            onClick={() => {
                              soundEffects.playHapticTap();
                              onToggleJamaah(prayer.id);
                            }}
                            className={`p-2 rounded-xl transition-all border ${
                              prayer.isJamaah
                                ? 'bg-[#0B3B2C] text-[#10B981] border-[#0B3B2C] shadow-xs'
                                : 'bg-slate-50 text-slate-400 border-slate-200 hover:text-slate-600'
                            }`}
                          >
                            <span className="text-xs">🕌</span>
                          </button>

                          {/* Fard Checkbox (Tap to toggle completion) */}
                          <button
                            type="button"
                            title="Mark Fard complete"
                            onClick={() => {
                              soundEffects.playTakbirChime();
                              onToggleFard(prayer.id);
                            }}
                            className={`w-9 h-9 rounded-xl flex items-center justify-center transition-all border ${
                              prayer.isFardCompleted
                                ? 'bg-[#10B981] text-white border-[#10B981] shadow-xs scale-105'
                                : 'bg-white text-transparent border-slate-300 hover:border-[#10B981]'
                            }`}
                          >
                            <Check className="w-5 h-5 stroke-[2.5]" />
                          </button>
                        </div>
                      )}

                      {/* Accordion expand chevron */}
                      <button
                        onClick={() => setExpandedPrayerId(isExpanded ? null : prayer.id)}
                        className="p-1 text-slate-400 hover:text-slate-700"
                      >
                        {isExpanded ? (
                          <ChevronUp className="w-4 h-4" />
                        ) : (
                          <ChevronDown className="w-4 h-4" />
                        )}
                      </button>
                    </div>

                    {/* Expandable Bottom Row: Sunnah & Nawafil Checkboxes */}
                    {isExpanded && (
                      <div className="px-3.5 pb-3.5 pt-2 border-t border-slate-100 bg-slate-50/70 rounded-b-2xl space-y-2 animate-fadeIn text-xs">
                        <div className="flex items-center justify-between text-slate-600 font-medium">
                          <span>Voluntary &amp; Sunnah Prayers:</span>
                          <span className="text-[11px] text-slate-400">Sunan Mu'akkadah</span>
                        </div>

                        <div className="grid grid-cols-2 gap-2">
                          {/* Sunnah Toggle */}
                          {prayer.sunnahBefore > 0 || prayer.sunnahAfter > 0 ? (
                            <button
                              type="button"
                              onClick={() => {
                                soundEffects.playHapticTap();
                                onToggleSunnah(prayer.id);
                              }}
                              className={`p-2.5 rounded-xl border text-left flex items-center justify-between transition-colors ${
                                prayer.isSunnahCompleted
                                  ? 'bg-emerald-50 border-emerald-300 text-[#0B3B2C]'
                                  : 'bg-white border-slate-200 text-slate-700 hover:border-slate-300'
                              }`}
                            >
                              <div>
                                <div className="font-semibold text-xs">
                                  {prayer.sunnahBefore > 0 && `${prayer.sunnahBefore} Rakat Pre-Sunnah `}
                                  {prayer.sunnahAfter > 0 && `${prayer.sunnahAfter} Rakat Post-Sunnah`}
                                </div>
                                <div className="text-[10px] text-slate-500">Sunnah Mu'akkadah</div>
                              </div>
                              <div
                                className={`w-4 h-4 rounded-md border flex items-center justify-center ${
                                  prayer.isSunnahCompleted
                                    ? 'bg-[#10B981] border-[#10B981] text-white'
                                    : 'border-slate-300 bg-white'
                                }`}
                              >
                                {prayer.isSunnahCompleted && <Check className="w-3 h-3 stroke-[3]" />}
                              </div>
                            </button>
                          ) : null}

                          {/* Nawafil / Witr Toggle */}
                          {prayer.nawafilName ? (
                            <button
                              type="button"
                              onClick={() => {
                                soundEffects.playHapticTap();
                                onToggleNawafil(prayer.id);
                              }}
                              className={`p-2.5 rounded-xl border text-left flex items-center justify-between transition-colors ${
                                prayer.isNawafilCompleted
                                  ? 'bg-emerald-50 border-emerald-300 text-[#0B3B2C]'
                                  : 'bg-white border-slate-200 text-slate-700 hover:border-slate-300'
                              }`}
                            >
                              <div>
                                <div className="font-semibold text-xs">{prayer.nawafilName}</div>
                                <div className="text-[10px] text-slate-500">Nafl / Optional</div>
                              </div>
                              <div
                                className={`w-4 h-4 rounded-md border flex items-center justify-center ${
                                  prayer.isNawafilCompleted
                                    ? 'bg-[#10B981] border-[#10B981] text-white'
                                    : 'border-slate-300 bg-white'
                                }`}
                              >
                                {prayer.isNawafilCompleted && <Check className="w-3 h-3 stroke-[3]" />}
                              </div>
                            </button>
                          ) : null}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        </>
      ) : (
        /* 4. Qadha al-Umri Ledger View */
        <div className="space-y-4">
          <div className="bg-gradient-to-br from-[#0B3B2C] to-[#134E3A] text-white p-5 rounded-3xl shadow-md border border-emerald-800">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs uppercase tracking-wider text-[#10B981] font-bold">
                Qadha al-Umri Ledger
              </span>
              <span className="text-xs font-semibold bg-emerald-950/70 px-2.5 py-0.5 rounded-full border border-emerald-700/50">
                Pace: {qadhaLedger.dailyGoalPace} / day
              </span>
            </div>

            <div className="flex items-baseline gap-2">
              <span className="text-4xl font-display font-bold">{totalMissedPrayers}</span>
              <span className="text-emerald-200 text-sm">Total Unfulfilled Prayers</span>
            </div>

            <div className="mt-3 p-3 bg-black/30 rounded-2xl border border-emerald-500/20 flex items-center gap-2.5">
              <TrendingUp className="w-5 h-5 text-[#10B981] shrink-0" />
              <p className="text-xs text-emerald-100">
                At your target pace of <strong>{qadhaLedger.dailyGoalPace} Qadha per day</strong>, you will clear your ledger in approx <strong>{daysToComplete} days</strong>.
              </p>
            </div>
          </div>

          {/* Stepper Cards for each prayer: Fajr, Dhuhr, Asr, Maghrib, Isha, Witr */}
          <div className="space-y-2">
            {(
              [
                { key: 'fajr', title: 'Fajr Qadha', count: qadhaLedger.fajr, rakats: 2 },
                { key: 'dhuhr', title: 'Dhuhr Qadha', count: qadhaLedger.dhuhr, rakats: 4 },
                { key: 'asr', title: 'Asr Qadha', count: qadhaLedger.asr, rakats: 4 },
                { key: 'maghrib', title: 'Maghrib Qadha', count: qadhaLedger.maghrib, rakats: 3 },
                { key: 'isha', title: 'Isha Qadha', count: qadhaLedger.isha, rakats: 4 },
                { key: 'witr', title: 'Witr Wajib Qadha', count: qadhaLedger.witr, rakats: 3 }
              ] as const
            ).map((row) => (
              <div
                key={row.key}
                className="bg-white p-3.5 rounded-2xl border border-[#E2E8F0] shadow-xs flex items-center justify-between"
              >
                <div>
                  <h4 className="font-bold text-sm text-[#0B3B2C]">{row.title}</h4>
                  <span className="text-xs text-slate-500">{row.rakats} Rakats each</span>
                </div>

                <div className="flex items-center gap-3">
                  <div className="text-right">
                    <span className="text-lg font-bold font-mono text-[#0B3B2C]">
                      {row.count}
                    </span>
                    <span className="text-[10px] text-slate-400 block">missed</span>
                  </div>

                  <div className="flex items-center gap-1.5">
                    {/* Stepper: -1 (or increment if accidentally missed another) */}
                    <button
                      onClick={() => {
                        soundEffects.playHapticTap();
                        onIncrementQadha(row.key);
                      }}
                      title="Add 1 missed"
                      className="w-8 h-8 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 flex items-center justify-center font-bold"
                    >
                      <Plus className="w-3.5 h-3.5" />
                    </button>

                    {/* Quick +1 Prayed button (decrements ledger with haptic feedback) */}
                    <button
                      onClick={() => {
                        soundEffects.playTakbirChime();
                        onDecrementQadha(row.key);
                      }}
                      disabled={row.count <= 0}
                      className="px-3 py-1.5 rounded-xl bg-[#10B981] hover:bg-[#059669] disabled:opacity-40 text-emerald-950 font-bold text-xs flex items-center gap-1 transition-transform active:scale-95 shadow-sm"
                    >
                      <Check className="w-3.5 h-3.5" />
                      <span>Prayed</span>
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Mosque Finder Modal */}
      {mosqueModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-end sm:items-center justify-center p-3">
          <div className="bg-white rounded-3xl p-5 w-full max-w-sm space-y-4 shadow-xl border border-slate-200 animate-slideUp">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <MapPin className="w-5 h-5 text-[#0B3B2C]" />
                <h3 className="font-bold text-base text-[#0B3B2C]">Nearby Sunni Mosques</h3>
              </div>
              <button onClick={() => setMosqueModalOpen(false)} className="p-1 text-slate-400">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-2 text-xs">
              <div className="p-3 bg-emerald-50 rounded-2xl border border-emerald-200">
                <div className="font-bold text-[#0B3B2C]">Central London Sunni Mosque</div>
                <div className="text-slate-600">0.8 km away · Asr Jama'ah at 04:00 PM</div>
                <div className="text-[10px] text-emerald-800 font-medium mt-1">
                  Walking distance: 10 mins
                </div>
              </div>
              <div className="p-3 bg-slate-50 rounded-2xl border border-slate-200">
                <div className="font-bold text-[#0B3B2C]">Al-Hikmah Islamic Centre</div>
                <div className="text-slate-600">1.4 km away · Asr Jama'ah at 04:15 PM</div>
              </div>
            </div>

            <button
              onClick={() => setMosqueModalOpen(false)}
              className="w-full py-2.5 rounded-xl bg-[#0B3B2C] text-white font-semibold text-xs"
            >
              Close
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
