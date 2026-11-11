import React, { useState, useEffect } from 'react';
import {
  Compass,
  Navigation,
  Car,
  Bluetooth,
  Volume2,
  AlertTriangle,
  CheckCircle2,
  MapPin,
  Clock,
  Sparkles,
  ArrowRight,
  RotateCw
} from 'lucide-react';
import { INITIAL_TRAVEL_STATE } from '../data/islamicData';
import { TravelConcessionState } from '../types';
import { soundEffects } from '../utils/audio';

export const TravelScreen: React.FC = () => {
  const [travelState, setTravelState] = useState<TravelConcessionState>(INITIAL_TRAVEL_STATE);
  const [isPlayingDua, setIsPlayingDua] = useState(false);
  const [simulatedHeading, setSimulatedHeading] = useState(travelState.vehicleHeadingDeg);

  // Sync heading
  const kaabaAzimuth = travelState.kaabaBearingDeg; // e.g. 118° East-Southeast from UK
  const relativeKaabaAngle = (kaabaAzimuth - simulatedHeading + 360) % 360;
  const isOffCourse = Math.abs(relativeKaabaAngle) > 25 && Math.abs(relativeKaabaAngle) < 335;

  const isSafar = travelState.distanceFromHomeKm >= travelState.thresholdKm;

  const handleToggleSafarSimulation = () => {
    soundEffects.playHapticTap();
    setTravelState((prev) => ({
      ...prev,
      distanceFromHomeKm: prev.distanceFromHomeKm >= 88 ? 45 : 94,
      isConcessionActive: prev.distanceFromHomeKm < 88
    }));
  };

  const handlePlayDua = () => {
    setIsPlayingDua(true);
    soundEffects.playRecitation(4, () => {
      setIsPlayingDua(false);
    });
  };

  return (
    <div className="flex-1 flex flex-col p-4 sm:p-5 bg-[#F9FAF7] text-[#1E293B] space-y-4 pb-20">
      {/* 1. Travel Status Header Banner */}
      <div className="rounded-3xl bg-white border border-[#E2E8F0] p-4 sm:p-5 shadow-xs space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div
              className={`w-9 h-9 rounded-xl flex items-center justify-center font-bold ${
                isSafar ? 'bg-emerald-100 text-[#0B3B2C]' : 'bg-slate-100 text-slate-700'
              }`}
            >
              <Car className="w-5 h-5" />
            </div>
            <div>
              <span className="text-xs font-bold text-[#0B3B2C] block">
                {travelState.homeCity}
              </span>
              <span className="text-[11px] text-slate-500">{travelState.currentLocationName}</span>
            </div>
          </div>

          {/* Status Badge */}
          <span
            className={`px-3 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider ${
              isSafar
                ? 'bg-[#10B981] text-emerald-950 border border-emerald-400'
                : 'bg-slate-100 text-slate-700 border border-slate-300'
            }`}
          >
            {isSafar ? 'Safar / Traveler Active' : 'Resident Mode'}
          </span>
        </div>

        {/* Distance Progress Bar vs 88km Threshold */}
        <div className="space-y-1.5 pt-1">
          <div className="flex justify-between text-xs font-medium">
            <span className="text-slate-600">
              Traveled Distance: <strong>{travelState.distanceFromHomeKm} km</strong>
            </span>
            <span className="text-emerald-800 font-bold font-mono">
              Threshold: {travelState.thresholdKm} km
            </span>
          </div>

          <div className="w-full h-2.5 rounded-full bg-slate-100 overflow-hidden p-0.5 border border-slate-200">
            <div
              className={`h-full rounded-full transition-all duration-500 ${
                isSafar ? 'bg-[#10B981]' : 'bg-[#0B3B2C]'
              }`}
              style={{
                width: `${Math.min((travelState.distanceFromHomeKm / travelState.thresholdKm) * 100, 100)}%`
              }}
            />
          </div>

          <div className="flex items-center justify-between text-[11px] text-slate-500 pt-0.5">
            <span>
              {isSafar
                ? '✓ Exceeded 88 km threshold. Qasr (shortening) and Jam‘ (combining) are permissible.'
                : `Need ${travelState.thresholdKm - travelState.distanceFromHomeKm} km more to activate full Safar concession.`}
            </span>
            <button
              onClick={handleToggleSafarSimulation}
              className="text-[#10B981] hover:underline font-semibold ml-2 shrink-0"
            >
              Simulate {isSafar ? '<88km' : '>88km'}
            </button>
          </div>
        </div>
      </div>

      {/* 2. Qasr & Jam' Prayer Timeline */}
      <div className="bg-white rounded-3xl border border-[#E2E8F0] p-4 sm:p-5 shadow-xs space-y-3">
        <div className="flex items-center justify-between">
          <div>
            <span className="text-xs font-bold uppercase tracking-wider text-[#10B981]">
              Traveler Prayer Concessions (Rukhsah)
            </span>
            <h3 className="text-base font-bold text-[#0B3B2C]">Qasr &amp; Jam‘ Rules Matrix</h3>
          </div>
          <span className="text-xs bg-emerald-50 text-[#0B3B2C] px-2.5 py-1 rounded-full font-bold border border-emerald-200">
            {isSafar ? 'Qasr Active' : 'Normal Form'}
          </span>
        </div>

        {/* Comparison table */}
        <div className="grid grid-cols-5 gap-1.5 text-center text-xs">
          {[
            { name: 'Fajr', normal: 2, qasr: 2, changed: false },
            { name: 'Dhuhr', normal: 4, qasr: 2, changed: true },
            { name: 'Asr', normal: 4, qasr: 2, changed: true },
            { name: 'Maghrib', normal: 3, qasr: 3, changed: false },
            { name: 'Isha', normal: 4, qasr: 2, changed: true }
          ].map((item) => (
            <div
              key={item.name}
              className={`p-2.5 rounded-2xl border ${
                item.changed && isSafar
                  ? 'bg-emerald-50 border-emerald-300 ring-1 ring-emerald-400/20'
                  : 'bg-slate-50 border-slate-200'
              }`}
            >
              <div className="font-bold text-[#0B3B2C] text-xs">{item.name}</div>
              <div className="text-[11px] text-slate-500 line-through mt-0.5">
                {item.normal} Rakats
              </div>
              <div
                className={`text-sm font-bold font-mono mt-0.5 ${
                  item.changed && isSafar ? 'text-emerald-700' : 'text-slate-800'
                }`}
              >
                {isSafar ? item.qasr : item.normal} R
              </div>
            </div>
          ))}
        </div>

        {/* Permissible Combining Windows Explanation */}
        <div className="p-3 bg-slate-50 rounded-2xl border border-slate-200 text-xs space-y-1.5">
          <div className="font-bold text-[#0B3B2C] flex items-center gap-1.5">
            <Clock className="w-3.5 h-3.5 text-[#10B981]" />
            <span>Permissible Combining Windows (Jam‘):</span>
          </div>
          <p className="text-slate-600 leading-relaxed text-[11px]">
            <strong>Jam‘ Taqdim (Early Combination):</strong> Pray Dhuhr (2 R) + Asr (2 R) during Dhuhr time, or Maghrib (3 R) + Isha (2 R) during Maghrib time.
          </p>
          <p className="text-slate-600 leading-relaxed text-[11px]">
            <strong>Jam‘ Ta’khir (Delayed Combination):</strong> Delay Dhuhr and pray together with Asr during Asr time, or delay Maghrib to Isha time before midnight.
          </p>
        </div>
      </div>

      {/* 3. In-Vehicle Qibla Compass */}
      <div className="bg-white rounded-3xl border border-[#E2E8F0] p-4 sm:p-5 shadow-xs space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Compass className="w-4 h-4 text-[#10B981]" />
            <h3 className="font-bold text-sm text-[#0B3B2C]">In-Vehicle Road Qibla Compass</h3>
          </div>
          <span className="text-xs font-mono font-bold text-emerald-800">
            Bearing: {kaabaAzimuth}° ESE
          </span>
        </div>

        {/* Deviation Warning Pill */}
        {isOffCourse && (
          <div className="p-2.5 bg-amber-50 rounded-2xl border border-amber-200 flex items-center gap-2 text-xs text-amber-900 animate-fadeIn">
            <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0" />
            <span>
              Vehicle heading is currently diverted {Math.round(relativeKaabaAngle)}° away from Kaaba direction.
            </span>
          </div>
        )}

        {/* Canvas Compass Dial */}
        <div className="relative py-4 flex flex-col items-center justify-center">
          <div className="relative w-52 h-52 rounded-full border-4 border-slate-200 bg-gradient-to-b from-[#F9FAF7] to-white shadow-inner flex items-center justify-center">
            {/* Cardinal markers */}
            <span className="absolute top-2 text-[10px] font-bold text-slate-400">N</span>
            <span className="absolute bottom-2 text-[10px] font-bold text-slate-400">S</span>
            <span className="absolute left-2 text-[10px] font-bold text-slate-400">W</span>
            <span className="absolute right-2 text-[10px] font-bold text-slate-400">E</span>

            {/* Heading Ring Rotated by simulated heading */}
            <div
              className="absolute inset-0 flex items-center justify-center transition-transform duration-300"
              style={{ transform: `rotate(${simulatedHeading}deg)` }}
            >
              {/* Car Front indicator */}
              <div className="absolute top-0 w-2 h-4 bg-slate-800 rounded-t-xs" />
            </div>

            {/* Kaaba Arrow Needle (Pointing dynamically to Kaaba relative to car) */}
            <div
              className="absolute inset-0 flex items-center justify-center transition-transform duration-300 pointer-events-none"
              style={{ transform: `rotate(${relativeKaabaAngle}deg)` }}
            >
              {/* Green Needle pointing to Kaaba */}
              <div className="w-0 h-0 border-l-[10px] border-l-transparent border-r-[10px] border-r-transparent border-b-[60px] border-b-[#10B981] -translate-y-12 drop-shadow-md" />
              {/* South tail */}
              <div className="w-0 h-0 border-l-[6px] border-l-transparent border-r-[6px] border-r-transparent border-t-[35px] border-t-slate-300 translate-y-10" />
            </div>

            {/* Central Kaaba Icon Hub */}
            <div className="w-10 h-10 rounded-full bg-[#0B3B2C] text-white flex items-center justify-center font-bold text-xs shadow-md z-10 border-2 border-white">
              🕋
            </div>
          </div>

          <div className="mt-3 text-center">
            <span className="text-xs font-bold text-[#0B3B2C]">
              Kaaba Direction: {relativeKaabaAngle > 180 ? `${360 - Math.round(relativeKaabaAngle)}° to Left` : `${Math.round(relativeKaabaAngle)}° to Right`}
            </span>
            <p className="text-[10px] text-slate-500">
              Vehicle Heading: {simulatedHeading}° · Kaaba Absolute: {kaabaAzimuth}°
            </p>
          </div>

          {/* Heading Slider / Simulator */}
          <div className="w-full max-w-xs mt-3 flex items-center gap-2 text-xs">
            <span className="text-slate-400 text-[10px]">0°</span>
            <input
              type="range"
              min="0"
              max="359"
              value={simulatedHeading}
              onChange={(e) => setSimulatedHeading(parseInt(e.target.value, 10))}
              className="flex-1 accent-[#10B981]"
            />
            <span className="text-slate-400 text-[10px]">360°</span>
            <button
              onClick={() => setSimulatedHeading((prev) => (prev + 45) % 360)}
              title="Rotate heading"
              className="p-1 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-600"
            >
              <RotateCw className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>

      {/* 4. Bluetooth Dua al-Safar Quick Card */}
      <div className="bg-gradient-to-br from-[#0B3B2C] to-[#134E3A] text-white rounded-3xl p-5 shadow-sm space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Bluetooth className="w-4 h-4 text-[#10B981]" />
            <span className="text-xs font-bold tracking-wide">
              Vehicle Bluetooth: Connected
            </span>
          </div>
          <span className="text-[10px] bg-emerald-950/80 text-emerald-300 px-2 py-0.5 rounded-full border border-emerald-700/50">
            Auto-Prompt on Drive
          </span>
        </div>

        <div className="space-y-1.5">
          <h4 className="font-bold text-sm text-white flex items-center gap-2">
            <span>Dua al-Safar</span>
            <span className="font-arabic text-sm text-emerald-300 font-normal">دعاء السفر</span>
          </h4>
          <p className="font-arabic text-lg text-emerald-100 text-right leading-relaxed">
            سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَٰذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ ، وَإِنَّا إِلَىٰ رَبِّنَا لَمُنْقَلِبُونَ
          </p>
          <p className="text-xs text-emerald-200/90 italic">
            "Subḥānal-ladhī sakh-khara lanā hādhā wa mā kunnā lahū muqrinīn, wa innā ilā Rabbinā lamunqalibūn."
          </p>
          <p className="text-[11px] text-emerald-300/80">
            "Glory be to Him Who has subjected this to us, and we could never have achieved it by ourselves. And indeed, to our Lord we will return."
          </p>
        </div>

        <button
          type="button"
          onClick={handlePlayDua}
          disabled={isPlayingDua}
          className={`w-full py-2.5 px-4 rounded-xl text-xs font-bold flex items-center justify-center gap-2 transition-all ${
            isPlayingDua
              ? 'bg-[#10B981] text-emerald-950 animate-pulse'
              : 'bg-white/15 hover:bg-white/25 text-white border border-white/20'
          }`}
        >
          <Volume2 className="w-4 h-4 text-[#10B981]" />
          <span>{isPlayingDua ? 'Playing Dua via Car Audio...' : 'Play Dua al-Safar on Car Audio'}</span>
        </button>
      </div>
    </div>
  );
};
