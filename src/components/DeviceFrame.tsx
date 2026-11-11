import React from 'react';
import {
  Smartphone,
  Tablet,
  Code2,
  Volume2,
  VolumeX,
  Compass,
  CheckCircle2
} from 'lucide-react';
import { DeviceMode, ScreenId } from '../types';

interface DeviceFrameProps {
  deviceMode: DeviceMode;
  onDeviceModeChange: (mode: DeviceMode) => void;
  activeScreen: ScreenId;
  onScreenChange: (screen: ScreenId) => void;
  onOpenCodeInspector: () => void;
  soundEnabled: boolean;
  onToggleSound: () => void;
  children: React.ReactNode;
}

export const DeviceFrame: React.FC<DeviceFrameProps> = ({
  deviceMode,
  onDeviceModeChange,
  activeScreen,
  onScreenChange,
  onOpenCodeInspector,
  soundEnabled,
  onToggleSound,
  children
}) => {
  const screens: Array<{ id: ScreenId; label: string; number: string }> = [
    { id: 'onboarding', label: '1. Onboarding', number: 'P1' },
    { id: 'salah', label: '2. Daily Salah', number: 'P2' },
    { id: 'visual_guide', label: '3. Visual Guide', number: 'P3' },
    { id: 'quran', label: '4. Quran & Tafseer', number: 'P4' },
    { id: 'hadith', label: '5. Hadith Books', number: 'P5' },
    { id: 'travel', label: '6. Safar & Travel', number: 'P6' },
    { id: 'wealth', label: '7. Zakat & Sadaqah', number: 'P7' },
    { id: 'scholar_chat', label: '8. AI Scholar', number: 'P8' }
  ];

  return (
    <div className="min-h-screen bg-[#0B251C] text-slate-100 flex flex-col font-sans selection:bg-[#10B981]/30">
      {/* Top Universal App Bar & Device Switcher */}
      <header className="bg-[#071913] border-b border-[#144736] px-4 py-2.5 flex items-center justify-between gap-4 sticky top-0 z-50 shadow-md">
        {/* Brand Lockup */}
        <div className="flex items-center gap-3 shrink-0">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-[#10B981] to-[#0B3B2C] flex items-center justify-center text-white shadow-inner font-serif text-lg font-bold border border-emerald-400/30">
            س
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-display text-base font-bold text-emerald-100 tracking-wide">
                Sakinah
              </span>
              <span className="text-[10px] font-semibold uppercase tracking-wider text-emerald-300 bg-emerald-950/80 px-2 py-0.5 rounded-full border border-emerald-700/50">
                Sunni Deen App
              </span>
            </div>
            <p className="text-[11px] text-emerald-400/80 hidden sm:block">
              Android Material 3 &amp; iOS Native Architecture
            </p>
          </div>
        </div>

        {/* Quick Screen Selector Chips for Testing All 8 Blueprint Pages */}
        <nav className="hidden lg:flex items-center gap-1.5 overflow-x-auto py-1 max-w-2xl scrollbar-none">
          {screens.map((s) => {
            const isActive = activeScreen === s.id;
            return (
              <button
                key={s.id}
                onClick={() => onScreenChange(s.id)}
                className={`px-2.5 py-1 text-xs rounded-lg font-medium whitespace-nowrap transition-all flex items-center gap-1 ${
                  isActive
                    ? 'bg-[#10B981] text-emerald-950 font-bold shadow-sm'
                    : 'bg-[#0E3326] text-emerald-200/90 hover:bg-[#144736] hover:text-white'
                }`}
              >
                <span>{s.label}</span>
                {isActive && <CheckCircle2 className="w-3 h-3 inline" />}
              </button>
            );
          })}
        </nav>

        {/* Action Controls */}
        <div className="flex items-center gap-2 shrink-0">
          {/* Sound Toggle */}
          <button
            onClick={onToggleSound}
            title={soundEnabled ? 'Mute synthesized Islamic chimes' : 'Enable sound'}
            className="p-2 rounded-lg bg-[#0E3326] hover:bg-[#144736] text-emerald-200 text-xs transition-colors border border-emerald-700/30 flex items-center gap-1"
          >
            {soundEnabled ? (
              <Volume2 className="w-4 h-4 text-emerald-400" />
            ) : (
              <VolumeX className="w-4 h-4 text-slate-400" />
            )}
          </button>

          {/* View Kotlin Compose Code */}
          <button
            onClick={onOpenCodeInspector}
            className="px-3 py-1.5 rounded-lg bg-[#144736] hover:bg-[#1c5c47] text-emerald-100 text-xs font-medium transition-all flex items-center gap-1.5 border border-emerald-500/30 shadow-sm"
          >
            <Code2 className="w-3.5 h-3.5 text-emerald-400" />
            <span className="hidden sm:inline">Compose Code</span>
          </button>

          {/* Device Frame Viewport Switcher */}
          <div className="flex items-center bg-[#092018] p-1 rounded-xl border border-emerald-800/50">
            <button
              onClick={() => onDeviceModeChange('android')}
              title="Android Material 3 Device Frame"
              className={`p-1.5 rounded-lg text-xs font-medium transition-all flex items-center gap-1 ${
                deviceMode === 'android'
                  ? 'bg-[#10B981] text-emerald-950 font-bold shadow'
                  : 'text-emerald-300 hover:text-white'
              }`}
            >
              <Smartphone className="w-3.5 h-3.5" />
              <span className="text-[11px] hidden md:inline">Android</span>
            </button>
            <button
              onClick={() => onDeviceModeChange('ios')}
              title="Apple iOS Device Frame"
              className={`p-1.5 rounded-lg text-xs font-medium transition-all flex items-center gap-1 ${
                deviceMode === 'ios'
                  ? 'bg-[#10B981] text-emerald-950 font-bold shadow'
                  : 'text-emerald-300 hover:text-white'
              }`}
            >
              <Smartphone className="w-3.5 h-3.5" />
              <span className="text-[11px] hidden md:inline">iOS</span>
            </button>
            <button
              onClick={() => onDeviceModeChange('expanded')}
              title="Responsive Mobile View"
              className={`p-1.5 rounded-lg text-xs font-medium transition-all flex items-center gap-1 ${
                deviceMode === 'expanded'
                  ? 'bg-[#10B981] text-emerald-950 font-bold shadow'
                  : 'text-emerald-300 hover:text-white'
              }`}
            >
              <Tablet className="w-3.5 h-3.5" />
              <span className="text-[11px] hidden md:inline">Responsive</span>
            </button>
          </div>
        </div>
      </header>

      {/* Mobile Screen Selector Strip (Mobile Screens) */}
      <div className="lg:hidden bg-[#0A2219] px-3 py-2 border-b border-[#144736] flex items-center gap-1.5 overflow-x-auto scrollbar-none">
        {screens.map((s) => (
          <button
            key={s.id}
            onClick={() => onScreenChange(s.id)}
            className={`px-2 py-1 text-[11px] rounded-md font-medium whitespace-nowrap transition-colors ${
              activeScreen === s.id
                ? 'bg-[#10B981] text-emerald-950 font-bold'
                : 'bg-[#0E3326] text-emerald-300'
            }`}
          >
            {s.label}
          </button>
        ))}
      </div>

      {/* Main Viewport Container */}
      <main className="flex-1 flex items-center justify-center p-2 sm:p-4 md:p-6 overflow-y-auto">
        {deviceMode === 'expanded' ? (
          /* Full Responsive Viewport Container */
          <div className="w-full max-w-lg md:max-w-xl lg:max-w-2xl bg-[#F9FAF7] text-[#1E293B] rounded-3xl shadow-2xl border border-emerald-900/40 overflow-hidden flex flex-col min-h-[820px] transition-all">
            {children}
          </div>
        ) : (
          /* Physical Smartphone Simulation Frame */
          <div
            className={`relative transition-all duration-300 shadow-2xl overflow-hidden flex flex-col ${
              deviceMode === 'android'
                ? 'w-[390px] sm:w-[412px] h-[870px] rounded-[44px] bg-[#000000] border-[10px] border-[#1C2C25] ring-1 ring-emerald-500/20'
                : 'w-[390px] sm:w-[402px] h-[870px] rounded-[52px] bg-[#000000] border-[12px] border-[#151D19] ring-1 ring-emerald-500/20'
            }`}
          >
            {/* Native Hardware Status Bar */}
            {deviceMode === 'android' ? (
              /* Android Material 3 Status Bar */
              <div className="h-7 bg-[#F9FAF7] text-[#1E293B] px-6 flex items-center justify-between text-[11px] font-semibold select-none shrink-0 z-30 pt-1">
                <span>09:04</span>
                {/* Camera Hole Punch */}
                <div className="w-3.5 h-3.5 rounded-full bg-black mx-auto ring-1 ring-emerald-900/30" />
                <div className="flex items-center gap-1.5 text-[10px]">
                  <span>5G</span>
                  <span>88%</span>
                </div>
              </div>
            ) : (
              /* iOS Status Bar with Dynamic Island */
              <div className="h-10 bg-[#F9FAF7] text-[#1E293B] px-7 flex items-center justify-between text-[11px] font-semibold select-none shrink-0 z-30 pt-1 relative">
                <span>9:41</span>
                {/* Dynamic Island */}
                <div className="absolute left-1/2 -translate-x-1/2 top-2 w-28 h-6 bg-black rounded-full flex items-center justify-center px-2 gap-1.5">
                  <div className="w-2.5 h-2.5 rounded-full bg-emerald-900/60 ring-1 ring-emerald-600/40" />
                  <div className="w-2.5 h-2.5 rounded-full bg-emerald-400/90" />
                </div>
                <div className="flex items-center gap-1.5 text-[10px]">
                  <span>LTE</span>
                  <div className="w-5 h-2.5 border border-current rounded-sm p-0.5 flex items-center">
                    <div className="w-3 h-full bg-current rounded-xs" />
                  </div>
                </div>
              </div>
            )}

            {/* Screen Inner Render Surface */}
            <div className="flex-1 bg-[#F9FAF7] text-[#1E293B] overflow-y-auto relative flex flex-col">
              {children}
            </div>

            {/* Native Bottom System Gesture Bar */}
            {deviceMode === 'android' ? (
              /* Android Gesture Bar */
              <div className="h-6 bg-[#F9FAF7] flex items-center justify-center shrink-0 pb-1">
                <div className="w-32 h-1 bg-slate-400 rounded-full" />
              </div>
            ) : (
              /* iOS Home Indicator Bar */
              <div className="h-7 bg-[#F9FAF7] flex items-center justify-center shrink-0 pb-2">
                <div className="w-36 h-1.5 bg-slate-900/80 rounded-full" />
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  );
};
