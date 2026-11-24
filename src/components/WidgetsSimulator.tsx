import React, { useState } from 'react';
import { Smartphone, RotateCw, Check, Sparkles, Compass, Calendar, BookOpen, MessageSquare } from 'lucide-react';

type WidgetSize = '2x1' | '4x1' | '2x2' | '4x2';

export const WidgetsSimulator: React.FC = () => {
  const [selectedWidget, setSelectedWidget] = useState<string>('salah');
  const [selectedSize, setSelectedSize] = useState<WidgetSize>('4x2');
  const [tasbihCount, setTasbihCount] = useState<number>(14);
  const [ayahIdx, setAyahIdx] = useState<number>(0);
  const [checkedPrayers, setCheckedPrayers] = useState<Record<string, boolean>>({
    Fajr: true,
    Dhuhr: false,
    Asr: false,
    Maghrib: false,
    Isha: false,
  });

  const verses = [
    { ref: '2:152', text: 'So remember Me; I will remember you. And be grateful to Me and do not deny Me.' },
    { ref: '94:5', text: 'For indeed, with hardship comes ease.' },
    { ref: '65:3', text: 'And whoever relies upon Allah, then He is sufficient for him.' },
    { ref: '13:28', text: 'Verily, in the remembrance of Allah do hearts find rest.' },
  ];

  const currentVerse = verses[ayahIdx % verses.length];

  const widgets = [
    { id: 'salah', name: 'Salah Tracker', icon: Check, desc: 'Next prayer, strip & one-tap checkboxes' },
    { id: 'tasbih', name: 'Tasbih 33-Count', icon: Sparkles, desc: 'SubhanAllah counter with +1 and reset' },
    { id: 'ayah', name: 'Ayah of the Day', icon: BookOpen, desc: 'Rotating Quranic verses with offline cache' },
    { id: 'qibla', name: 'Qibla Direction', icon: Compass, desc: 'Live compass heading to Kaaba' },
    { id: 'hijri', name: 'Hijri Date', icon: Calendar, desc: 'Islamic calendar, Sunnah fasts and events' },
    { id: 'scholar', name: 'ImanCore Scholar', icon: MessageSquare, desc: 'AI fiqh & Quran scholar launch card' },
  ];

  const sizes: WidgetSize[] = ['2x1', '4x1', '2x2', '4x2'];

  const toggleCheck = (name: string) => {
    setCheckedPrayers((p) => ({ ...p, [name]: !p[name] }));
  };

  return (
    <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-6 md:p-8 space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <Smartphone className="w-5 h-5 text-emerald-400" />
            Android 12–16 Glance Widget Suite
          </h2>
          <p className="text-slate-400 text-sm mt-0.5">
            Real-time interactive preview of Jetpack Glance home-screen app widgets
          </p>
        </div>

        {/* Size Selector */}
        <div className="flex items-center gap-1.5 bg-slate-950 p-1 rounded-xl border border-slate-800">
          {sizes.map((s) => (
            <button
              key={s}
              onClick={() => setSelectedSize(s)}
              className={`px-3 py-1 text-xs font-mono font-semibold rounded-lg transition ${
                selectedSize === s
                  ? 'bg-emerald-500 text-slate-950 shadow-sm'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      {/* Widget Tabs */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2">
        {widgets.map((w) => {
          const Icon = w.icon;
          const isSelected = selectedWidget === w.id;
          return (
            <button
              key={w.id}
              onClick={() => setSelectedWidget(w.id)}
              className={`p-3 rounded-xl border text-left transition ${
                isSelected
                  ? 'bg-emerald-950/40 border-emerald-500/60 text-white'
                  : 'bg-slate-950/60 border-slate-800/80 text-slate-400 hover:text-slate-200 hover:border-slate-700'
              }`}
            >
              <Icon className={`w-4 h-4 mb-2 ${isSelected ? 'text-emerald-400' : 'text-slate-400'}`} />
              <div className="text-xs font-semibold">{w.name}</div>
            </button>
          );
        })}
      </div>

      {/* Android Widget Canvas */}
      <div className="bg-slate-950 border border-slate-800 rounded-2xl p-8 flex flex-col items-center justify-center min-h-[300px]">
        <div className="text-xs font-mono text-slate-500 mb-4">
          Widget: <strong className="text-slate-300">{selectedWidget}</strong> • Size Mode: <strong className="text-slate-300">{selectedSize}</strong>
        </div>

        {/* Simulated Android Widget Shell (with 24dp rounded corners) */}
        <div
          className={`bg-slate-900 border border-slate-700/60 rounded-[24px] p-4 text-white shadow-2xl transition-all duration-300 relative overflow-hidden ${
            selectedSize === '2x1'
              ? 'w-64 h-24'
              : selectedSize === '4x1'
              ? 'w-[420px] h-24'
              : selectedSize === '2x2'
              ? 'w-64 h-64'
              : 'w-[420px] h-64'
          }`}
        >
          {/* Widget 1: Salah Tracker */}
          {selectedWidget === 'salah' && (
            <div className="h-full flex flex-col justify-between">
              <div className="flex items-center justify-between text-xs">
                <span className="font-bold text-emerald-400">SALAH TRACKER</span>
                <span className="text-slate-400 font-mono text-[11px]">Next: Asr (3:45 PM)</span>
              </div>

              {selectedSize.includes('2') ? (
                <div className="grid grid-cols-5 gap-2 my-auto">
                  {['Fajr', 'Dhuhr', 'Asr', 'Maghrib', 'Isha'].map((prayer) => (
                    <button
                      key={prayer}
                      onClick={() => toggleCheck(prayer)}
                      className={`flex flex-col items-center py-2 px-1 rounded-xl border text-center transition ${
                        checkedPrayers[prayer]
                          ? 'bg-emerald-500/20 border-emerald-500/50 text-emerald-300'
                          : 'bg-slate-950 border-slate-800 text-slate-400 hover:border-slate-700'
                      }`}
                    >
                      <span className="text-sm font-bold">
                        {checkedPrayers[prayer] ? '✓' : '○'}
                      </span>
                      <span className="text-[10px] font-medium mt-1">{prayer}</span>
                    </button>
                  ))}
                </div>
              ) : (
                <div className="flex items-center justify-between my-auto">
                  <div className="text-sm font-semibold">Asr in 2h 15m</div>
                  <div className="text-xs text-slate-400">Fajr ✓ • Dhuhr ○</div>
                </div>
              )}

              <div className="text-[10px] text-slate-500 flex justify-between">
                <span>Tap checkbox to log</span>
                <span>Al-Masjid Al-Haram</span>
              </div>
            </div>
          )}

          {/* Widget 2: Tasbih Counter */}
          {selectedWidget === 'tasbih' && (
            <div className="h-full flex flex-col justify-between">
              <div className="flex items-center justify-between text-xs">
                <span className="font-bold text-emerald-400">TASBIH COUNTER</span>
                <span className="text-slate-400 font-mono text-[11px]">Goal: 33</span>
              </div>

              <div className="my-auto flex items-center justify-between">
                <div>
                  <div className="text-3xl font-mono font-bold text-white">
                    {tasbihCount} <span className="text-sm text-slate-500">/ 33</span>
                  </div>
                  <div className="text-xs text-slate-300 font-medium mt-0.5">SubhanAllah</div>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setTasbihCount((c) => (c + 1 > 33 ? 0 : c + 1))}
                    className="px-4 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-sm shadow-md active:scale-95 transition"
                  >
                    +1
                  </button>
                  <button
                    onClick={() => setTasbihCount(0)}
                    className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 active:scale-95 transition"
                    title="Reset"
                  >
                    <RotateCw className="w-4 h-4" />
                  </button>
                </div>
              </div>

              <div className="text-[10px] text-slate-500">Glance ActionCallback: IncrementTasbihAction</div>
            </div>
          )}

          {/* Widget 3: Ayah of the Day */}
          {selectedWidget === 'ayah' && (
            <div className="h-full flex flex-col justify-between">
              <div className="flex items-center justify-between text-xs">
                <span className="font-bold text-emerald-400">AYAH OF THE DAY</span>
                <button
                  onClick={() => setAyahIdx((i) => i + 1)}
                  className="flex items-center gap-1 text-[11px] text-slate-400 hover:text-white"
                >
                  <RotateCw className="w-3 h-3" /> Next
                </button>
              </div>

              <div className="my-auto">
                <p className="text-xs md:text-sm text-slate-200 italic line-clamp-3">
                  "{currentVerse.text}"
                </p>
                <span className="text-[11px] font-semibold text-emerald-400 mt-1 block">
                  Quran {currentVerse.ref}
                </span>
              </div>

              <div className="text-[10px] text-slate-500">Tap to open Quran Reader</div>
            </div>
          )}

          {/* Widget 4: Qibla Compass */}
          {selectedWidget === 'qibla' && (
            <div className="h-full flex flex-col justify-between">
              <div className="flex items-center justify-between text-xs">
                <span className="font-bold text-emerald-400">QIBLA DIRECTION</span>
                <span className="text-slate-400 text-[11px]">True North</span>
              </div>

              <div className="my-auto flex items-center justify-between">
                <div>
                  <div className="text-3xl font-mono font-bold text-white">119° ESE</div>
                  <div className="text-xs text-slate-400 mt-0.5">London, United Kingdom</div>
                </div>
                <div className="w-12 h-12 rounded-full border border-emerald-500/40 flex items-center justify-center bg-slate-950">
                  <Compass className="w-6 h-6 text-emerald-400" />
                </div>
              </div>

              <div className="text-[10px] text-slate-500">Tap to open full compass</div>
            </div>
          )}

          {/* Widget 5: Hijri Date */}
          {selectedWidget === 'hijri' && (
            <div className="h-full flex flex-col justify-between">
              <div className="flex items-center justify-between text-xs">
                <span className="font-bold text-emerald-400">HIJRI CALENDAR</span>
                <span className="text-slate-400 text-[11px]">Umm Al-Qura</span>
              </div>

              <div className="my-auto">
                <div className="text-2xl font-bold text-white">4 Jumada al-Akhirah</div>
                <div className="text-xs text-emerald-400 font-medium mt-0.5">1448 AH • Monday Sunnah Fast</div>
              </div>

              <div className="text-[10px] text-slate-500">Includes White Days & Islamic milestones</div>
            </div>
          )}

          {/* Widget 6: ImanCore Scholar */}
          {selectedWidget === 'scholar' && (
            <div className="h-full flex flex-col justify-between">
              <div className="flex items-center justify-between text-xs">
                <span className="font-bold text-emerald-400">IMANCORE SCHOLAR</span>
                <span className="text-slate-400 text-[11px]">AI Companion</span>
              </div>

              <div className="my-auto">
                <div className="text-sm font-semibold text-white">Ask an Islamic question</div>
                <p className="text-xs text-slate-400 mt-1">
                  Answers grounded in Quran, Hadith, and classical fiqh sources
                </p>
              </div>

              <div className="text-[10px] text-slate-500">Tap to open AI Scholar chat</div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
