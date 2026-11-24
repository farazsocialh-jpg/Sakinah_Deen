import React, { useState, useEffect } from 'react';
import {
  calculatePrayerTimes,
  CALC_METHODS,
  PrayerTimesResult,
} from '../data/prayerCalc';
import {
  Check,
  ChevronDown,
  ChevronUp,
  MapPin,
  RefreshCw,
  Bell,
  Settings,
  Sparkles,
  Play,
  Moon,
} from 'lucide-react';

export const PrayerTimesSection: React.FC = () => {
  const [city, setCity] = useState('New York, NY');
  const [coords, setCoords] = useState<{ lat: number; lon: number }>({
    lat: 40.7128,
    lon: -74.006,
  });
  const [calcMethod, setCalcMethod] = useState('MWL');
  const [isHanafi, setIsHanafi] = useState(false);
  const [currentTime, setCurrentTime] = useState(new Date());
  const [completedPrayers, setCompletedPrayers] = useState<Record<string, boolean>>({
    fajr: true,
    dhuhr: true,
    asr: false,
    maghrib: false,
    isha: false,
  });
  const [expandedPrayer, setExpandedPrayer] = useState<string | null>(null);

  useEffect(() => {
    const timer = setInterval(() => setCurrentTime(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  const times: PrayerTimesResult = calculatePrayerTimes(
    coords.lat,
    coords.lon,
    currentTime,
    calcMethod,
    isHanafi
  );

  const fardKeys = ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha'];
  const prayedCount = fardKeys.filter((k) => completedPrayers[k]).length;

  const togglePrayer = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setCompletedPrayers((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const prayersList = [
    {
      id: 'fajr',
      nameEn: 'Fajr',
      nameAr: 'الفجر',
      time: times.fajr,
      fard: 2,
      sunnahBefore: 2,
      sunnahAfter: 0,
      window: `${times.fajr} – ${times.sunrise}`,
    },
    {
      id: 'dhuhr',
      nameEn: 'Dhuhr',
      nameAr: 'الظهر',
      time: times.dhuhr,
      fard: 4,
      sunnahBefore: 4,
      sunnahAfter: 2,
      window: `${times.dhuhr} – ${times.asr}`,
    },
    {
      id: 'asr',
      nameEn: 'Asr',
      nameAr: 'العصر',
      time: times.asr,
      fard: 4,
      sunnahBefore: 0,
      sunnahAfter: 0,
      window: `${times.asr} – ${times.maghrib}`,
    },
    {
      id: 'maghrib',
      nameEn: 'Maghrib',
      nameAr: 'المغرب',
      time: times.maghrib,
      fard: 3,
      sunnahBefore: 0,
      sunnahAfter: 2,
      window: `${times.maghrib} – ${times.isha}`,
    },
    {
      id: 'isha',
      nameEn: 'Isha',
      nameAr: 'العشاء',
      time: times.isha,
      fard: 4,
      sunnahBefore: 0,
      sunnahAfter: 2,
      window: `${times.isha} – ${times.fajr}`,
    },
  ];

  return (
    <div className="max-w-2xl mx-auto space-y-5">
      {/* Top Header - Responsive, Single Line with Ellipsis */}
      <div className="flex items-center justify-between gap-3 bg-slate-900/60 border border-slate-800/80 p-3.5 rounded-2xl">
        <div className="min-w-0 flex-1">
          <div className="text-emerald-400 font-bold text-sm tracking-wide truncate">
            14 Shawwal 1447 AH
          </div>
          <div className="text-slate-400 text-xs truncate flex items-center gap-1.5 mt-0.5">
            <span>{currentTime.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })}</span>
            <span>•</span>
            <span className="flex items-center gap-0.5 text-slate-300">
              <MapPin className="w-3 h-3 text-emerald-400 inline" /> {city}
            </span>
            <span>•</span>
            <span className="text-slate-400">{CALC_METHODS[calcMethod]?.name.split('(')[0].trim()}</span>
          </div>
        </div>

        {/* Action icons tightly grouped */}
        <div className="flex items-center gap-1.5 shrink-0">
          <button
            onClick={() => {}}
            className="px-2.5 py-1 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 text-[11px] font-bold hover:bg-emerald-500/30 transition"
          >
            Qadha
          </button>
          <button
            title="Prayer Notifications"
            className="w-8 h-8 rounded-full flex items-center justify-center text-slate-300 hover:text-emerald-400 hover:bg-slate-800 transition"
          >
            <Bell className="w-4 h-4" />
          </button>
          <button
            title="Recalculate today's times"
            className="w-8 h-8 rounded-full flex items-center justify-center text-slate-300 hover:text-emerald-400 hover:bg-slate-800 transition"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
          <button
            title="Prayer Settings"
            className="w-8 h-8 rounded-full flex items-center justify-center text-slate-300 hover:text-emerald-400 hover:bg-slate-800 transition"
          >
            <Settings className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Classic Hero Card - Unchanged Design */}
      <div className="relative overflow-hidden bg-gradient-to-br from-slate-900 to-slate-950 border border-slate-800 rounded-3xl p-5 shadow-xl">
        <div className="flex items-center justify-between text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
          <span>NEXT: {times.nextPrayer}</span>
          <span className="font-mono text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded-full border border-emerald-500/20">
            {times.minutesToNext}m left
          </span>
        </div>

        <div className="flex items-center justify-between mb-4">
          <div>
            <div className="text-3xl font-extrabold text-white flex items-center gap-3">
              <span>{times.activePrayer.toUpperCase()}</span>
              <span className="font-serif text-emerald-400 text-2xl">
                {prayersList.find((p) => p.nameEn.toLowerCase() === times.activePrayer.toLowerCase())?.nameAr || 'الصلاة'}
              </span>
            </div>
            <div className="text-xs text-slate-400 mt-1">
              Active window started at {prayersList.find((p) => p.nameEn.toLowerCase() === times.activePrayer.toLowerCase())?.time || times.fajr}
            </div>
          </div>

          <div
            onClick={(e) => {
              const activeKey = times.activePrayer.toLowerCase();
              if (activeKey in completedPrayers) {
                togglePrayer(activeKey, e);
              }
            }}
            className={`w-10 h-10 rounded-full flex items-center justify-center cursor-pointer transition shadow-md ${
              completedPrayers[times.activePrayer.toLowerCase()]
                ? 'bg-emerald-600 text-white'
                : 'border-2 border-amber-500/70 bg-amber-500/10 text-amber-400'
            }`}
          >
            <Check className="w-5 h-5 stroke-[2.5]" />
          </div>
        </div>

        {/* Draining Progress Bar */}
        <div className="w-full bg-slate-800 rounded-full h-2 mb-3 overflow-hidden">
          <div
            className="bg-gradient-to-r from-amber-500 to-emerald-500 h-2 rounded-full transition-all duration-500"
            style={{ width: `${Math.max(10, Math.min(90, 100 - (times.minutesToNext / 180) * 100))}%` }}
          />
        </div>

        {/* Reminder Callout */}
        <div className="bg-slate-950/60 border border-slate-800/80 rounded-xl px-3 py-2 flex items-center gap-2 text-xs text-slate-300">
          <Sparkles className="w-4 h-4 text-emerald-400 shrink-0" />
          <span className="font-serif italic text-emerald-200">
            “What led you into Hell?” They will say, “We were not of those who prayed.” — Quran 74:42-43
          </span>
        </div>
      </div>

      {/* Segmented Pill Bar: 2 Tabs (Times | Qibla) */}
      <div className="flex bg-slate-900/80 p-1 rounded-2xl border border-slate-800">
        <button
          className="flex-1 py-2 text-xs font-bold rounded-xl bg-slate-800 text-emerald-400 shadow-sm transition"
        >
          Times
        </button>
        <button
          onClick={() => {}}
          className="flex-1 py-2 text-xs font-semibold rounded-xl text-slate-400 hover:text-slate-200 transition"
        >
          Qibla
        </button>
      </div>

      {/* Layout F: 5-Segment Daily Progress Strip */}
      <div className="space-y-2 bg-slate-900/40 border border-slate-800/60 p-3.5 rounded-2xl">
        <div className="flex items-center justify-between text-xs font-bold px-1">
          <span className="text-slate-200">Today: {prayedCount}/5 Prayed</span>
          <span className="text-emerald-400">{prayedCount * 20}%</span>
        </div>
        <div className="grid grid-cols-5 gap-2">
          {fardKeys.map((key) => {
            const isDone = completedPrayers[key];
            return (
              <div
                key={key}
                className={`h-1.5 rounded-full transition-all duration-300 ${
                  isDone ? 'bg-[#3D5A36] shadow-sm shadow-emerald-900/40' : 'bg-slate-800'
                }`}
              />
            );
          })}
        </div>
      </div>

      {/* Layout F: Slim Single-Line Status-Accent Prayer Rows */}
      <div className="space-y-2.5">
        {prayersList.map((prayer) => {
          const isPrayed = completedPrayers[prayer.id];
          const isActive = times.activePrayer.toLowerCase() === prayer.id;
          const isExpanded = expandedPrayer === prayer.id;

          const accentColor = isPrayed
            ? 'bg-[#3D5A36]'
            : isActive
            ? 'bg-[#E68A1E]'
            : 'bg-slate-700/60';

          return (
            <div
              key={prayer.id}
              onClick={() => setExpandedPrayer(isExpanded ? null : prayer.id)}
              className="bg-slate-900/80 border border-slate-800/80 hover:border-slate-700 rounded-2xl p-3 cursor-pointer transition select-none shadow-sm"
            >
              <div className="flex items-center justify-between gap-3">
                {/* Left Accent Bar (4px x 28px) */}
                <div className={`w-1 h-7 rounded-full shrink-0 ${accentColor}`} />

                {/* Left text: Single-line prayer names */}
                <div className="flex items-center gap-2 min-w-0 flex-1">
                  <span className={`text-sm ${isActive ? 'font-bold text-white' : 'font-semibold text-slate-200'} truncate`}>
                    {prayer.nameEn}
                  </span>
                  <span className={`text-sm font-serif ${isActive ? 'text-amber-400' : 'text-emerald-400'} shrink-0`}>
                    {prayer.nameAr}
                  </span>
                </div>

                {/* Right side: Start Time */}
                <span className={`text-xs font-mono tabular-nums ${isActive ? 'font-bold text-amber-400' : 'font-medium text-slate-300'} shrink-0`}>
                  {prayer.time}
                </span>

                {/* Circular Check Toggle (30px circle inside 44px box) */}
                <div
                  onClick={(e) => togglePrayer(prayer.id, e)}
                  className="w-11 h-11 flex items-center justify-center shrink-0 -mr-2"
                >
                  {isPrayed ? (
                    <div className="w-7 h-7 rounded-full bg-[#3D5A36] text-white flex items-center justify-center shadow-md">
                      <Check className="w-4 h-4 stroke-[3]" />
                    </div>
                  ) : isActive ? (
                    <div className="w-7 h-7 rounded-full border-[1.5px] border-[#E68A1E] bg-[#E68A1E]/10 text-amber-400/50 flex items-center justify-center">
                      <Check className="w-3.5 h-3.5" />
                    </div>
                  ) : (
                    <div className="w-7 h-7 rounded-full border border-slate-700 flex items-center justify-center">
                      <div className="w-1.5 h-1.5 rounded-full bg-slate-700" />
                    </div>
                  )}
                </div>
              </div>

              {/* Tap to expand accordion */}
              {isExpanded && (
                <div className="mt-3 pt-3 border-t border-slate-800/80 space-y-2 text-xs">
                  <div className="flex items-center justify-between text-slate-400">
                    <span className="text-emerald-400 font-semibold">{prayer.window}</span>
                    <span>
                      {prayer.sunnahBefore > 0 ? `${prayer.sunnahBefore} Sunnah • ` : ''}
                      {prayer.fard} Fard
                      {prayer.sunnahAfter > 0 ? ` • ${prayer.sunnahAfter} Sunnah` : ''}
                    </span>
                  </div>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                    }}
                    className="w-full flex items-center justify-center gap-1.5 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium transition"
                  >
                    <Play className="w-3 h-3 text-emerald-400" />
                    <span>Open {prayer.nameEn} Step Guide ({prayer.fard} Rakats)</span>
                  </button>
                </div>
              )}
            </div>
          );
        })}

        {/* Slim secondary Sunrise row below Isha */}
        <div className="bg-slate-900/50 border border-slate-800/60 rounded-2xl p-3 flex items-center justify-between text-xs text-slate-400">
          <div className="flex items-center gap-3">
            <div className="w-1 h-7 rounded-full bg-slate-700" />
            <div>
              <div className="font-semibold text-slate-300">Sunrise • الشروق</div>
              <div className="text-[11px] text-slate-500">Prohibited prayer window • Fajr ends</div>
            </div>
          </div>
          <span className="font-mono text-slate-400">{times.sunrise}</span>
        </div>

        {/* Slim secondary Tahajjud row */}
        <div className="bg-slate-900/50 border border-slate-800/60 rounded-2xl p-3 flex items-center justify-between text-xs text-slate-400">
          <div className="flex items-center gap-3">
            <Moon className="w-4 h-4 text-emerald-400" />
            <div>
              <div className="font-semibold text-emerald-300">Tahajjud • Last Third of the Night</div>
              <div className="text-[11px] text-slate-500">Best recommended voluntary prayer window</div>
            </div>
          </div>
          <span className="font-mono text-emerald-400">01:30 AM – {times.fajr}</span>
        </div>
      </div>
    </div>
  );
};
