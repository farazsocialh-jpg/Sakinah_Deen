import React, { useState } from 'react';
import { DeviceMode, ScreenId, JourneyTrack, Madhhab, PrayerItem, QadhaLedger } from './types';
import { INITIAL_PRAYERS, INITIAL_QADHA } from './data/islamicData';
import { DeviceFrame } from './components/DeviceFrame';
import { BottomNavBar } from './components/BottomNavBar';
import { ComposeCodeModal } from './components/ComposeCodeModal';

import { OnboardingScreen } from './screens/OnboardingScreen';
import { SalahScreen } from './screens/SalahScreen';
import { VisualSalahGuideScreen } from './screens/VisualSalahGuideScreen';
import { QuranScreen } from './screens/QuranScreen';
import { HadithScreen } from './screens/HadithScreen';
import { TravelScreen } from './screens/TravelScreen';
import { WealthScreen } from './screens/WealthScreen';
import { AiChatScreen } from './screens/AiChatScreen';
import { soundEffects } from './utils/audio';

export default function App() {
  const [deviceMode, setDeviceMode] = useState<DeviceMode>('android');
  const [activeScreen, setActiveScreen] = useState<ScreenId>('salah');
  const [soundEnabled, setSoundEnabled] = useState<boolean>(true);
  const [isCodeModalOpen, setIsCodeModalOpen] = useState<boolean>(false);

  // App User Preferences & Journey State
  const [journeyTrack, setJourneyTrack] = useState<JourneyTrack>('ISTIQAMAH');
  const [selectedMadhhab, setSelectedMadhhab] = useState<Madhhab>('Hanafi');

  // Daily Prayer State
  const [prayers, setPrayers] = useState<PrayerItem[]>(INITIAL_PRAYERS);

  // Qadha al-Umri Ledger
  const [qadhaLedger, setQadhaLedger] = useState<QadhaLedger>(INITIAL_QADHA);

  // Prayer actions
  const handleToggleFard = (id: string) => {
    setPrayers((prev) =>
      prev.map((p) => (p.id === id ? { ...p, isFardCompleted: !p.isFardCompleted } : p))
    );
  };

  const handleToggleJamaah = (id: string) => {
    setPrayers((prev) =>
      prev.map((p) => (p.id === id ? { ...p, isJamaah: !p.isJamaah } : p))
    );
  };

  const handleToggleSunnah = (id: string) => {
    setPrayers((prev) =>
      prev.map((p) => (p.id === id ? { ...p, isSunnahCompleted: !p.isSunnahCompleted } : p))
    );
  };

  const handleToggleNawafil = (id: string) => {
    setPrayers((prev) =>
      prev.map((p) => (p.id === id ? { ...p, isNawafilCompleted: !p.isNawafilCompleted } : p))
    );
  };

  const handleDecrementQadha = (key: keyof Omit<QadhaLedger, 'dailyGoalPace'>) => {
    setQadhaLedger((prev) => ({
      ...prev,
      [key]: Math.max(0, prev[key] - 1)
    }));
  };

  const handleIncrementQadha = (key: keyof Omit<QadhaLedger, 'dailyGoalPace'>) => {
    setQadhaLedger((prev) => ({
      ...prev,
      [key]: prev[key] + 1
    }));
  };

  return (
    <DeviceFrame
      deviceMode={deviceMode}
      onDeviceModeChange={setDeviceMode}
      activeScreen={activeScreen}
      onScreenChange={setActiveScreen}
      onOpenCodeInspector={() => setIsCodeModalOpen(true)}
      soundEnabled={soundEnabled}
      onToggleSound={() => setSoundEnabled(!soundEnabled)}
    >
      {/* Dynamic Screen Routing */}
      <div className="flex-1 flex flex-col relative overflow-x-hidden">
        {activeScreen === 'onboarding' && (
          <OnboardingScreen
            onComplete={() => setActiveScreen('salah')}
            selectedTrack={journeyTrack}
            onTrackChange={setJourneyTrack}
            selectedMadhhab={selectedMadhhab}
            onMadhhabChange={setSelectedMadhhab}
          />
        )}

        {activeScreen === 'salah' && (
          <SalahScreen
            prayers={prayers}
            onToggleFard={handleToggleFard}
            onToggleJamaah={handleToggleJamaah}
            onToggleSunnah={handleToggleSunnah}
            onToggleNawafil={handleToggleNawafil}
            qadhaLedger={qadhaLedger}
            onDecrementQadha={handleDecrementQadha}
            onIncrementQadha={handleIncrementQadha}
            onOpenVisualGuide={() => setActiveScreen('visual_guide')}
          />
        )}

        {activeScreen === 'visual_guide' && (
          <VisualSalahGuideScreen onBackToSalah={() => setActiveScreen('salah')} />
        )}

        {activeScreen === 'quran' && <QuranScreen />}

        {activeScreen === 'hadith' && <HadithScreen />}

        {activeScreen === 'travel' && <TravelScreen />}

        {activeScreen === 'wealth' && <WealthScreen />}

        {activeScreen === 'scholar_chat' && (
          <AiChatScreen onNavigateToScreen={(screen) => setActiveScreen(screen)} />
        )}

        {/* Persistent Bottom Tab Navigation (Visible on main destinations) */}
        {activeScreen !== 'onboarding' && (
          <BottomNavBar
            activeScreen={activeScreen}
            onScreenChange={setActiveScreen}
          />
        )}
      </div>

      {/* Kotlin Jetpack Compose & Swift Code Inspector Modal */}
      <ComposeCodeModal
        isOpen={isCodeModalOpen}
        onClose={() => setIsCodeModalOpen(false)}
      />
    </DeviceFrame>
  );
}
