import React, { useState, useEffect } from 'react';
import {
  Play,
  Pause,
  Repeat,
  Volume2,
  ChevronLeft,
  ChevronRight,
  Compass,
  Sparkles,
  Info,
  CheckCircle2
} from 'lucide-react';
import { VISUAL_SALAH_STEPS } from '../data/islamicData';
import { soundEffects } from '../utils/audio';

interface VisualSalahGuideScreenProps {
  onBackToSalah?: () => void;
}

export const VisualSalahGuideScreen: React.FC<VisualSalahGuideScreenProps> = ({
  onBackToSalah
}) => {
  const [currentStepIdx, setCurrentStepIdx] = useState(0);
  const [isPlayingAudio, setIsPlayingAudio] = useState(false);
  const [isLooping, setIsLooping] = useState(false);
  const [audioProgress, setAudioProgress] = useState(0);
  const [currentRakat, setCurrentRakat] = useState(1);
  const totalRakats = 2; // Default 2 Rakat Fajr or Sunnah guide

  const step = VISUAL_SALAH_STEPS[currentStepIdx];

  // Play audio simulator effect
  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (isPlayingAudio) {
      soundEffects.playRecitation(step.durationSec, () => {
        if (isLooping) {
          setAudioProgress(0);
        } else {
          setIsPlayingAudio(false);
          setAudioProgress(0);
        }
      });

      const startTime = Date.now();
      const durationMs = step.durationSec * 1000;
      interval = setInterval(() => {
        const elapsed = Date.now() - startTime;
        const pct = Math.min((elapsed / durationMs) * 100, 100);
        setAudioProgress(pct);
        if (pct >= 100) {
          if (!isLooping) {
            setIsPlayingAudio(false);
            setAudioProgress(0);
          }
        }
      }, 100);
    } else {
      setAudioProgress(0);
    }
    return () => clearInterval(interval);
  }, [isPlayingAudio, currentStepIdx, isLooping, step.durationSec]);

  const handleNextStep = () => {
    soundEffects.playHapticTap();
    setIsPlayingAudio(false);
    if (currentStepIdx < VISUAL_SALAH_STEPS.length - 1) {
      setCurrentStepIdx(currentStepIdx + 1);
    } else {
      // Completed Rakat cycle
      if (currentRakat < totalRakats) {
        setCurrentRakat(currentRakat + 1);
        setCurrentStepIdx(1); // Return to Qiyam
        soundEffects.playTakbirChime();
      } else {
        // Complete
        soundEffects.playTakbirChime();
      }
    }
  };

  const handlePrevStep = () => {
    soundEffects.playHapticTap();
    setIsPlayingAudio(false);
    if (currentStepIdx > 0) {
      setCurrentStepIdx(currentStepIdx - 1);
    }
  };

  // Pure Vector Stylized Posture Graphic Rendering
  const renderPostureFigure = (postureKey: string) => {
    switch (postureKey) {
      case 'takbir':
        return (
          <svg viewBox="0 0 200 240" className="w-48 h-48 mx-auto drop-shadow-sm">
            {/* Ground mat */}
            <path d="M20 220 L180 220" stroke="#CBD5E1" strokeWidth="4" strokeLinecap="round" />
            <path d="M40 220 L160 220" stroke="#10B981" strokeWidth="6" strokeLinecap="round" />
            {/* Feet */}
            <ellipse cx="85" cy="216" rx="8" ry="4" fill="#0B3B2C" />
            <ellipse cx="115" cy="216" rx="8" ry="4" fill="#0B3B2C" />
            {/* Legs */}
            <line x1="88" y1="216" x2="90" y2="150" stroke="#0B3B2C" strokeWidth="10" strokeLinecap="round" />
            <line x1="112" y1="216" x2="110" y2="150" stroke="#0B3B2C" strokeWidth="10" strokeLinecap="round" />
            {/* Torso */}
            <path d="M82 150 L118 150 L114 85 L86 85 Z" fill="#134E3A" />
            {/* Head */}
            <circle cx="100" cy="55" r="18" fill="#10B981" />
            {/* Gaze line down toward floor (Khushu) */}
            <line x1="100" y1="58" x2="108" y2="190" stroke="#10B981" strokeWidth="1.5" strokeDasharray="3 3" opacity="0.6" />
            {/* Arms raised up to earlobes */}
            <path d="M86 92 L62 90 L58 55" fill="none" stroke="#0B3B2C" strokeWidth="8" strokeLinecap="round" strokeLinejoin="round" />
            <path d="M114 92 L138 90 L142 55" fill="none" stroke="#0B3B2C" strokeWidth="8" strokeLinecap="round" strokeLinejoin="round" />
            {/* Hands facing Qibla */}
            <ellipse cx="58" cy="48" rx="6" ry="8" fill="#10B981" />
            <ellipse cx="142" cy="48" rx="6" ry="8" fill="#10B981" />
          </svg>
        );

      case 'qiyam':
        return (
          <svg viewBox="0 0 200 240" className="w-48 h-48 mx-auto drop-shadow-sm">
            <path d="M20 220 L180 220" stroke="#CBD5E1" strokeWidth="4" strokeLinecap="round" />
            <path d="M40 220 L160 220" stroke="#10B981" strokeWidth="6" strokeLinecap="round" />
            {/* Feet */}
            <ellipse cx="88" cy="216" rx="8" ry="4" fill="#0B3B2C" />
            <ellipse cx="112" cy="216" rx="8" ry="4" fill="#0B3B2C" />
            {/* Legs */}
            <line x1="88" y1="216" x2="90" y2="150" stroke="#0B3B2C" strokeWidth="10" strokeLinecap="round" />
            <line x1="112" y1="216" x2="110" y2="150" stroke="#0B3B2C" strokeWidth="10" strokeLinecap="round" />
            {/* Torso */}
            <path d="M82 150 L118 150 L114 85 L86 85 Z" fill="#134E3A" />
            {/* Head */}
            <circle cx="100" cy="55" r="18" fill="#10B981" />
            {/* Arms folded over chest / navel */}
            <path d="M86 92 L75 118 L125 118 L114 92" fill="none" stroke="#0B3B2C" strokeWidth="8" strokeLinecap="round" strokeLinejoin="round" />
            {/* Hands clasping wrist */}
            <rect x="90" y="112" width="20" height="12" rx="4" fill="#10B981" />
          </svg>
        );

      case 'ruku':
        return (
          <svg viewBox="0 0 200 240" className="w-48 h-48 mx-auto drop-shadow-sm">
            <path d="M20 220 L180 220" stroke="#CBD5E1" strokeWidth="4" strokeLinecap="round" />
            <path d="M120 220 L165 220" stroke="#10B981" strokeWidth="6" strokeLinecap="round" />
            {/* Feet */}
            <ellipse cx="145" cy="216" rx="10" ry="4" fill="#0B3B2C" />
            {/* Straight Legs */}
            <line x1="145" y1="216" x2="140" y2="140" stroke="#0B3B2C" strokeWidth="10" strokeLinecap="round" />
            {/* 90-degree Horizontal Back Torso */}
            <line x1="140" y1="140" x2="65" y2="140" stroke="#134E3A" strokeWidth="14" strokeLinecap="round" />
            {/* Spine Alignment Angle Guideline (90°) */}
            <path d="M132 140 A 18 18 0 0 0 140 148" fill="none" stroke="#10B981" strokeWidth="2" strokeDasharray="2 2" />
            {/* Head aligned with spine */}
            <circle cx="48" cy="140" r="16" fill="#10B981" />
            {/* Arms bracing onto knees */}
            <line x1="75" y1="140" x2="135" y2="175" stroke="#0B3B2C" strokeWidth="7" strokeLinecap="round" />
            {/* Cupped hands on kneecaps */}
            <circle cx="135" cy="175" r="7" fill="#10B981" />
          </svg>
        );

      case 'sujud':
        return (
          <svg viewBox="0 0 200 240" className="w-48 h-48 mx-auto drop-shadow-sm">
            <path d="M20 220 L180 220" stroke="#CBD5E1" strokeWidth="4" strokeLinecap="round" />
            <path d="M25 220 L175 220" stroke="#10B981" strokeWidth="6" strokeLinecap="round" />
            {/* Feet: upright toes gripping floor */}
            <ellipse cx="165" cy="214" rx="6" ry="6" fill="#0B3B2C" />
            {/* Shins flat on ground */}
            <line x1="165" y1="214" x2="125" y2="216" stroke="#0B3B2C" strokeWidth="9" strokeLinecap="round" />
            {/* Thighs elevated */}
            <line x1="125" y1="216" x2="110" y2="165" stroke="#0B3B2C" strokeWidth="10" strokeLinecap="round" />
            {/* Torso sloping down */}
            <line x1="110" y1="165" x2="65" y2="205" stroke="#134E3A" strokeWidth="12" strokeLinecap="round" />
            {/* Head prostrating (Forehead + Nose touching ground) */}
            <circle cx="45" cy="212" r="14" fill="#10B981" />
            {/* Arms elevated, palms beside ears on mat */}
            <path d="M75 195 L65 170 L55 216" fill="none" stroke="#0B3B2C" strokeWidth="6" strokeLinecap="round" strokeLinejoin="round" />
            <ellipse cx="55" cy="218" rx="6" ry="3" fill="#10B981" />
          </svg>
        );

      case 'tashahhud':
      default:
        return (
          <svg viewBox="0 0 200 240" className="w-48 h-48 mx-auto drop-shadow-sm">
            <path d="M20 220 L180 220" stroke="#CBD5E1" strokeWidth="4" strokeLinecap="round" />
            <path d="M40 220 L160 220" stroke="#10B981" strokeWidth="6" strokeLinecap="round" />
            {/* Sitting position */}
            <ellipse cx="100" cy="216" rx="28" ry="8" fill="#0B3B2C" />
            {/* Upright sitting torso */}
            <path d="M85 210 L115 210 L112 135 L88 135 Z" fill="#134E3A" />
            {/* Head */}
            <circle cx="100" cy="110" r="16" fill="#10B981" />
            {/* Arms on knees */}
            <line x1="90" y1="140" x2="78" y2="190" stroke="#0B3B2C" strokeWidth="7" strokeLinecap="round" />
            <line x1="110" y1="140" x2="122" y2="190" stroke="#0B3B2C" strokeWidth="7" strokeLinecap="round" />
            {/* Raised right index finger pointing to Qibla */}
            <circle cx="122" cy="190" r="5" fill="#10B981" />
            <line x1="122" y1="190" x2="132" y2="180" stroke="#10B981" strokeWidth="3" strokeLinecap="round" />
          </svg>
        );
    }
  };

  return (
    <div className="flex-1 flex flex-col p-4 sm:p-5 bg-[#F9FAF7] text-[#1E293B] space-y-4 pb-20">
      {/* Top Header */}
      <div className="flex items-center justify-between">
        <div>
          <span className="text-xs font-bold uppercase tracking-wider text-[#10B981]">
            Visual Salah Posture Guide
          </span>
          <h2 className="text-xl font-display font-bold text-[#0B3B2C]">
            Posture-by-Posture Walkthrough
          </h2>
        </div>

        {/* Rakat Counter Badge */}
        <div className="px-3 py-1 rounded-full bg-emerald-100 text-[#0B3B2C] text-xs font-bold border border-emerald-300">
          Rakat {currentRakat} of {totalRakats}
        </div>
      </div>

      {/* 1. Step Indicator Carousel */}
      <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none">
        {VISUAL_SALAH_STEPS.map((s, idx) => {
          const isCurrent = idx === currentStepIdx;
          const isPassed = idx < currentStepIdx;

          return (
            <button
              key={s.id}
              onClick={() => {
                soundEffects.playHapticTap();
                setIsPlayingAudio(false);
                setCurrentStepIdx(idx);
              }}
              className={`px-3 py-1.5 rounded-xl text-xs font-medium whitespace-nowrap transition-all flex items-center gap-1.5 shrink-0 ${
                isCurrent
                  ? 'bg-[#0B3B2C] text-white shadow-xs font-bold'
                  : isPassed
                  ? 'bg-emerald-100 text-emerald-900'
                  : 'bg-white text-slate-600 border border-slate-200'
              }`}
            >
              <span>{s.id}. {s.name.split(' ')[0]}</span>
              {isPassed && <CheckCircle2 className="w-3 h-3 text-[#10B981]" />}
            </button>
          );
        })}
      </div>

      {/* 2. Posture Graphic Viewport (Pure Vector / Canvas Drawing) */}
      <div className="bg-white rounded-3xl p-5 border border-[#E2E8F0] shadow-sm space-y-3">
        {/* Orientation Banner */}
        <div className="flex items-center justify-between text-xs pb-1 border-b border-slate-100">
          <div className="flex items-center gap-1.5 text-emerald-800 font-medium">
            <Compass className="w-4 h-4 text-[#10B981]" />
            <span>Facing Qibla with Calm Focus (Khushu‘)</span>
          </div>
          <span className="text-[11px] text-slate-500 font-mono">
            {step.postureAngleText.split(',')[0]}
          </span>
        </div>

        {/* Visual Vector Render */}
        <div className="py-2 flex items-center justify-center bg-gradient-to-b from-[#F9FAF7] to-white rounded-2xl border border-slate-100">
          {renderPostureFigure(step.postureKey)}
        </div>

        {/* Physical Placement Guidance Notes */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs pt-1">
          <div className="p-2.5 bg-slate-50 rounded-xl border border-slate-200">
            <span className="font-bold text-[#0B3B2C] block">Foot Positioning:</span>
            <span className="text-slate-600 text-[11px]">{step.footPositioning}</span>
          </div>
          <div className="p-2.5 bg-slate-50 rounded-xl border border-slate-200">
            <span className="font-bold text-[#0B3B2C] block">Hand Placement:</span>
            <span className="text-slate-600 text-[11px]">{step.handPlacement}</span>
          </div>
        </div>
      </div>

      {/* 3. Recitation & Meaning Card */}
      <div className="bg-white rounded-3xl p-5 border border-[#E2E8F0] shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Prescribed Recitation (Dhikr)
          </span>
          <span className="text-xs font-arabic text-emerald-800 font-bold">
            {step.arabicTitle}
          </span>
        </div>

        {/* Large Clear Arabic Text */}
        <div className="p-4 bg-emerald-50/60 rounded-2xl border border-emerald-200/80 text-center">
          <p className="font-arabic text-2xl sm:text-3xl text-[#0B3B2C] leading-loose">
            {step.arabicRecitation}
          </p>
        </div>

        {/* Phonetic Transliteration */}
        <div>
          <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider block">
            Phonetic Transliteration:
          </span>
          <p className="text-sm font-semibold text-[#0B3B2C] mt-0.5">
            "{step.phoneticTransliteration}"
          </p>
        </div>

        {/* English Translation */}
        <div>
          <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider block">
            English Meaning:
          </span>
          <p className="text-xs text-slate-700 italic mt-0.5 leading-relaxed">
            "{step.englishTranslation}"
          </p>
        </div>

        {/* Audio Trigger Bar with Play/Pause, Progress Slider, and Repeat Loop */}
        <div className="pt-2 border-t border-slate-100 flex items-center gap-3">
          <button
            type="button"
            onClick={() => setIsPlayingAudio(!isPlayingAudio)}
            className="w-10 h-10 rounded-full bg-[#10B981] hover:bg-[#059669] text-emerald-950 flex items-center justify-center shrink-0 transition-transform active:scale-95 shadow-xs"
          >
            {isPlayingAudio ? (
              <Pause className="w-5 h-5 fill-current" />
            ) : (
              <Play className="w-5 h-5 fill-current ml-0.5" />
            )}
          </button>

          {/* Progress Slider */}
          <div className="flex-1 space-y-1">
            <div className="flex justify-between text-[10px] text-slate-500 font-mono">
              <span>{isPlayingAudio ? 'Pronouncing...' : 'Tap to Listen'}</span>
              <span>{step.durationSec}s</span>
            </div>
            <div className="w-full h-2 rounded-full bg-slate-100 overflow-hidden">
              <div
                className="h-full bg-[#10B981] transition-all duration-100"
                style={{ width: `${audioProgress}%` }}
              />
            </div>
          </div>

          {/* Repeat Loop Toggle */}
          <button
            type="button"
            onClick={() => {
              soundEffects.playHapticTap();
              setIsLooping(!isLooping);
            }}
            title={isLooping ? 'Looping enabled' : 'Loop disabled'}
            className={`p-2 rounded-xl border text-xs transition-colors ${
              isLooping
                ? 'bg-[#0B3B2C] text-[#10B981] border-[#0B3B2C]'
                : 'bg-slate-50 text-slate-500 border-slate-200 hover:text-slate-700'
            }`}
          >
            <Repeat className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* 4. Navigation Footer */}
      <div className="pt-2 flex items-center justify-between gap-3">
        <button
          type="button"
          onClick={handlePrevStep}
          disabled={currentStepIdx === 0}
          className="flex-1 py-3 px-4 rounded-2xl bg-white border border-[#E2E8F0] hover:bg-slate-50 disabled:opacity-40 text-slate-700 font-bold text-xs flex items-center justify-center gap-1.5"
        >
          <ChevronLeft className="w-4 h-4" />
          <span>Previous Step</span>
        </button>

        <button
          type="button"
          onClick={handleNextStep}
          className="flex-1 py-3 px-4 rounded-2xl bg-[#0B3B2C] hover:bg-[#134E3A] text-white font-bold text-xs flex items-center justify-center gap-1.5 shadow-sm"
        >
          <span>
            {currentStepIdx === VISUAL_SALAH_STEPS.length - 1
              ? currentRakat < totalRakats
                ? 'Start Next Rakat'
                : 'Complete Salah'
              : 'Next Step'}
          </span>
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
};
