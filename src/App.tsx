import React, { useEffect, useState } from 'react';
import {
  Clock,
  Compass,
  BookOpen,
  Sparkles,
  Smartphone,
  ShieldCheck,
  Moon,
  Github,
  Download,
} from 'lucide-react';
import { PrayerTimesSection } from './components/PrayerTimesSection';
import { QiblaSection } from './components/QiblaSection';
import { QuranSection } from './components/QuranSection';
import { TasbihSection } from './components/TasbihSection';
import { WidgetsSimulator } from './components/WidgetsSimulator';
import { AndroidBuildStatus } from './components/AndroidBuildStatus';

type TabType = 'prayers' | 'qibla' | 'quran' | 'tasbih' | 'widgets' | 'build';

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabType>('prayers');
  const [apiStatus, setApiStatus] = useState<'checking' | 'online' | 'offline'>('checking');

  // Connectivity is observed from the API itself; never hardcode a port or assume it is live.
  useEffect(() => {
    let active = true;
    fetch('/api/status')
      .then((res) => {
        if (active) setApiStatus(res.ok ? 'online' : 'offline');
      })
      .catch(() => {
        if (active) setApiStatus('offline');
      });
    return () => {
      active = false;
    };
  }, []);

  const navItems: { id: TabType; label: string; icon: React.ComponentType<{ className?: string }> }[] = [
    { id: 'prayers', label: 'Prayer Times', icon: Clock },
    { id: 'qibla', label: 'Qibla Finder', icon: Compass },
    { id: 'quran', label: 'Quran Reader', icon: BookOpen },
    { id: 'tasbih', label: 'Digital Tasbih', icon: Sparkles },
    { id: 'widgets', label: 'Glance Widgets', icon: Smartphone },
    { id: 'build', label: 'Android Releases', icon: ShieldCheck },
  ];

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
      {/* Top Navigation Bar */}
      <header className="sticky top-0 z-50 bg-slate-950/80 backdrop-blur-md border-b border-slate-800/80 px-4 md:px-8 py-3.5">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-emerald-600 to-emerald-400 flex items-center justify-center shadow-lg shadow-emerald-500/20 text-xl font-bold text-slate-950">
              🌙
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-bold text-lg text-white tracking-tight">ImanCore</span>
                <span className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                  com.imancore.app
                </span>
              </div>
              <p className="text-xs text-slate-400 hidden sm:block">
                Islamic Companion & Android Widget Suite
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <a
              href="/api/apk/debug"
              download="imancore-debug.apk"
              className="hidden sm:flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs shadow-md transition"
            >
              <Download className="w-3.5 h-3.5" />
              <span>Get Android APK</span>
            </a>
            <div className="h-4 w-px bg-slate-800 hidden sm:block" />
            <div
              className={`flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-full font-mono border ${
                apiStatus === 'online'
                  ? 'text-emerald-400 bg-emerald-950/40 border-emerald-500/30'
                  : apiStatus === 'offline'
                    ? 'text-rose-400 bg-rose-950/40 border-rose-500/30'
                    : 'text-slate-400 bg-slate-900 border-slate-700'
              }`}
              title="Derived from GET /api/status"
            >
              <span
                className={`w-2 h-2 rounded-full ${
                  apiStatus === 'online'
                    ? 'bg-emerald-400 animate-pulse'
                    : apiStatus === 'offline'
                      ? 'bg-rose-400'
                      : 'bg-slate-500'
                }`}
              />
              API {apiStatus}
            </div>
          </div>
        </div>
      </header>

      {/* Main Body with Tabs */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 md:p-8 space-y-6">
        {/* Navigation Tabs */}
        <div className="flex items-center gap-1 border-b border-slate-800/80 overflow-x-auto pb-1 no-scrollbar">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id)}
                className={`flex items-center gap-2 px-4 py-2.5 text-xs md:text-sm font-semibold rounded-xl whitespace-nowrap transition ${
                  isActive
                    ? 'bg-slate-900 text-emerald-400 border border-slate-800 shadow-sm'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50'
                }`}
              >
                <Icon className={`w-4 h-4 ${isActive ? 'text-emerald-400' : 'text-slate-500'}`} />
                <span>{item.label}</span>
              </button>
            );
          })}
        </div>

        {/* Tab Content Panels */}
        <div className="transition-all duration-200">
          {activeTab === 'prayers' && <PrayerTimesSection />}
          {activeTab === 'qibla' && <QiblaSection />}
          {activeTab === 'quran' && <QuranSection />}
          {activeTab === 'tasbih' && <TasbihSection />}
          {activeTab === 'widgets' && <WidgetsSimulator />}
          {activeTab === 'build' && <AndroidBuildStatus />}
        </div>
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-900 bg-slate-950 py-6 px-4 md:px-8 text-center text-xs text-slate-500">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-3">
          <span>ImanCore (com.imancore.app) • Jetpack Compose + KMP + Express</span>
          <div className="flex items-center gap-4">
            <button
              onClick={() => setActiveTab('widgets')}
              className="hover:text-slate-300 transition"
            >
              6 Glance Widgets
            </button>
            <span>•</span>
            <button
              onClick={() => setActiveTab('build')}
              className="hover:text-slate-300 transition"
            >
              Release Artifacts
            </button>
          </div>
        </div>
      </footer>
    </div>
  );
};

export default App;
