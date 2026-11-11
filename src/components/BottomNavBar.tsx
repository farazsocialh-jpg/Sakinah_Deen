import React from 'react';
import {
  Compass,
  BookOpen,
  Library,
  Coins,
  MessageSquare,
  Sparkles
} from 'lucide-react';
import { ScreenId } from '../types';
import { soundEffects } from '../utils/audio';

interface BottomNavBarProps {
  activeScreen: ScreenId;
  onScreenChange: (screen: ScreenId) => void;
}

export const BottomNavBar: React.FC<BottomNavBarProps> = ({
  activeScreen,
  onScreenChange
}) => {
  const tabs: Array<{
    id: ScreenId;
    label: string;
    icon: React.ReactNode;
  }> = [
    {
      id: 'salah',
      label: 'Salah',
      icon: <span className="text-base">🕌</span>
    },
    {
      id: 'quran',
      label: 'Quran',
      icon: <BookOpen className="w-5 h-5" />
    },
    {
      id: 'hadith',
      label: 'Hadith',
      icon: <Library className="w-5 h-5" />
    },
    {
      id: 'travel',
      label: 'Safar',
      icon: <Compass className="w-5 h-5" />
    },
    {
      id: 'wealth',
      label: 'Zakat',
      icon: <Coins className="w-5 h-5" />
    },
    {
      id: 'scholar_chat',
      label: 'Scholar',
      icon: <MessageSquare className="w-5 h-5" />
    }
  ];

  return (
    <nav className="fixed bottom-0 left-0 right-0 z-40 bg-white/95 backdrop-blur-md border-t border-[#E2E8F0] px-2 py-1.5 shadow-lg">
      <div className="grid grid-cols-6 items-center max-w-lg mx-auto">
        {tabs.map((tab) => {
          const isActive = activeScreen === tab.id;
          return (
            <button
              key={tab.id}
              type="button"
              onClick={() => {
                soundEffects.playHapticTap();
                onScreenChange(tab.id);
              }}
              className={`flex flex-col items-center justify-center py-1 transition-all ${
                isActive
                  ? 'text-[#0B3B2C] font-bold scale-105'
                  : 'text-slate-400 hover:text-slate-700'
              }`}
            >
              <div
                className={`p-1 rounded-xl transition-colors ${
                  isActive ? 'bg-[#10B981]/20 text-[#0B3B2C]' : ''
                }`}
              >
                {tab.icon}
              </div>
              <span
                className={`text-[10px] tracking-tight mt-0.5 ${
                  isActive ? 'font-bold text-[#0B3B2C]' : 'font-medium'
                }`}
              >
                {tab.label}
              </span>
            </button>
          );
        })}
      </div>
    </nav>
  );
};
