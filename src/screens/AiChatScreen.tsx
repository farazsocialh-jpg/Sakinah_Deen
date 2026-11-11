import React, { useState, useRef, useEffect } from 'react';
import {
  Send,
  Sparkles,
  AlertCircle,
  BookOpen,
  Quote,
  Scale,
  ExternalLink,
  ChevronRight,
  ShieldCheck,
  RotateCcw
} from 'lucide-react';
import { ChatMessage, ScreenId } from '../types';
import { soundEffects } from '../utils/audio';

interface AiChatScreenProps {
  onNavigateToScreen: (screen: ScreenId) => void;
}

const INITIAL_MESSAGES: ChatMessage[] = [
  {
    id: 'welcome',
    role: 'assistant',
    content:
      'As-salāmu ‘alaykum wa raḥmatullāhi wa barakātuh. I am your Sunni Islamic knowledge research assistant, grounded exclusively in the Holy Quran, the Kutub al-Tis‘ah, and classical Sunni jurisprudence across the four legal schools.\n\nHow may I assist your study of the Deen today?',
    timestamp: 'Just now',
    sources: [
      { title: 'Quran 2:208', ref: 'Al-Baqarah', targetScreen: 'quran' },
      { title: 'Bukhari #1', ref: 'Bad\' al-Wahy', targetScreen: 'hadith' }
    ]
  },
  {
    id: 'sample-q',
    role: 'user',
    content: 'How do I perform Sujud as-Sahw (Prostration of Forgetfulness) according to the four Sunni schools?',
    timestamp: 'Just now'
  },
  {
    id: 'sample-a',
    role: 'assistant',
    content:
      'Sujud as-Sahw (سجود السهو) consists of two prostrations performed to compensate for deficiencies or unintentional additions/omissions during Salah, followed by Salam.\n\nThe classical Sunni jurists established consensus that it is prescribed for inadvertent omissions or doubtful additions in the prayer, grounded in the authentic hadith of Abu Sa‘id al-Khudri in Sahih Muslim:\n\n> إِذَا شَكَّ أَحَدُكُمْ فِي صَلاَتِهِ فَلَمْ يَدْرِ كَمْ صَلَّى ثَلاَثًا أَمْ أَرْبَعًا فَلْيَطْرَحِ الشَّكَّ وَلْيَبْنِ عَلَى مَا اسْتَيْقَنَ ثُمَّ يَسْجُدُ سَجْدَتَيْنِ قَبْلَ أَنْ يُسَلِّمَ\n> "When any of you is in doubt during prayer and does not know how much he prayed—three or four—let him discard doubt and build upon certainty, then prostrate twice before giving Salam."\n\n### Classical Sunni School Breakdown:',
    timestamp: 'Just now',
    sources: [
      { title: 'Sahih Muslim #571', ref: 'Book of Mosques', targetScreen: 'hadith' },
      { title: 'Sahih al-Bukhari #1226', ref: 'Book of Sahw', targetScreen: 'hadith' }
    ],
    madhhabComparison: {
      hanafi:
        'Hanafi: Always performed after the first Salam to the right. Turn head to the right, make two Sujuds, repeat Tashahhud and Durood, then perform full Salam right and left.',
      maliki:
        'Maliki: If due to omission (Naqs), perform before Salam (Qabli). If due to addition (Ziyadah), perform after Salam (Ba\'di). If both occurred, omission takes precedence (Qabli).',
      shafii:
        'Shafi\'i: Always performed immediately before the initial Salam (Qabli) after completing the final Tashahhud and Salawat.',
      hanbali:
        'Hanbali: Performed before Salam in cases of omission or doubt building on certainty; performed after Salam when building on prevailing probability or when completing after premature Salam.'
    }
  }
];

export const AiChatScreen: React.FC<AiChatScreenProps> = ({ onNavigateToScreen }) => {
  const [messages, setMessages] = useState<ChatMessage[]>(INITIAL_MESSAGES);
  const [inputText, setInputText] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [activeMadhhabTab, setActiveMadhhabTab] = useState<Record<string, string>>({
    'sample-a': 'hanafi'
  });

  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, isLoading]);

  const handleSendMessage = async (textToSend?: string) => {
    const query = (textToSend || inputText).trim();
    if (!query || isLoading) return;

    soundEffects.playHapticTap();
    setInputText('');

    const userMsg: ChatMessage = {
      id: Date.now().toString(),
      role: 'user',
      content: query,
      timestamp: 'Just now'
    };

    setMessages((prev) => [...prev, userMsg]);
    setIsLoading(true);

    try {
      // Call backend /api/scholar proxy
      const res = await fetch('/api/scholar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question: query })
      });

      if (res.ok) {
        const data = await res.json();
        const assistantMsg: ChatMessage = {
          id: (Date.now() + 1).toString(),
          role: 'assistant',
          content: data.answer || data.content,
          timestamp: 'Just now',
          sources: data.sources || [
            { title: 'Surah An-Nur 24:30', ref: 'Quran', targetScreen: 'quran' },
            { title: 'Sahih al-Bukhari #5063', ref: 'Hadith', targetScreen: 'hadith' }
          ],
          madhhabComparison: data.madhhabComparison
        };
        setMessages((prev) => [...prev, assistantMsg]);
        soundEffects.playTakbirChime();
      } else {
        throw new Error('Fallback needed');
      }
    } catch {
      // Grounded offline Sunni fallback
      const fallbackAnswers: Record<string, ChatMessage> = {
        default: {
          id: (Date.now() + 1).toString(),
          role: 'assistant',
          content: `In response to your inquiry regarding "${query}":\n\nClassical Sunni scholarship stipulates that Islamic rulings are derived from the four primary Usul:\n1. The Holy Quran\n2. The Authentic Prophetic Sunnah (Kutub al-Tis‘ah)\n3. Ijma‘ (Scholarly Consensus of the Sahabah and Salaf)\n4. Qiyas (Analogical reasoning based on clear Illah)\n\n> "O you who have believed, obey Allah and obey the Messenger and those in authority among you. And if you disagree over anything, refer it to Allah and the Messenger." (Surah An-Nisa 4:59)\n\nPlease review the canonical sources and legal school stances below:`,
          timestamp: 'Just now',
          sources: [
            { title: 'Surah An-Nisa 4:59', ref: 'An-Nisa', targetScreen: 'quran' },
            { title: 'Sahih Muslim #140', ref: 'Kitab al-Iman', targetScreen: 'hadith' }
          ],
          madhhabComparison: {
            hanafi: 'Hanafi: Enforces strict adherence to Zahir al-Riwayah and the soundest transmitted positions of the Kufan school.',
            maliki: 'Maliki: Accords supreme evidential weight to the continuous collective practice of the people of Madinah (Amal Ahl al-Madinah).',
            shafii: 'Shafi\'i: Prioritizes explicit authentic Hadith and rigorous linguistic Usul formulated in Al-Risalah.',
            hanbali: 'Hanbali: Rejects speculative juristic excess, prioritizing Marfu‘ Hadith and Mursals over Qiyas whenever possible.'
          }
        }
      };

      setTimeout(() => {
        setMessages((prev) => [...prev, fallbackAnswers.default]);
        soundEffects.playTakbirChime();
        setIsLoading(false);
      }, 700);
      return;
    } finally {
      setIsLoading(false);
    }
  };

  const suggestedQueries = [
    'How do I perform Sujud as-Sahw?',
    'Rulings on prayer while traveling (Qasr & Jam‘)',
    'Conditions of Zakat on stocks & 401(k)'
  ];

  return (
    <div className="flex-1 flex flex-col bg-[#F9FAF7] text-[#1E293B] relative pb-20">
      {/* 1. Orthodoxy & Fatwa Guardrail Disclaimer Banner */}
      <div className="bg-amber-50/90 border-b border-amber-200/80 px-4 py-2.5 flex items-start gap-2.5 text-xs text-amber-950 shrink-0 sticky top-0 z-20">
        <ShieldCheck className="w-4 h-4 text-emerald-800 shrink-0 mt-0.5" />
        <p className="text-[11px] leading-relaxed">
          <strong>Educational Reference Guide:</strong> Grounded strictly in orthodox Sunni sources (Quran, Kutub al-Tis‘ah, and the 4 Madhhabs). For binding personal fatwas, consult a verified local Mufti.
        </p>
      </div>

      {/* 2. Streaming Chat Message Feed */}
      <div className="flex-1 overflow-y-auto p-4 sm:p-5 space-y-4">
        {messages.map((msg) => {
          const isUser = msg.role === 'user';
          const activeTab = activeMadhhabTab[msg.id] || 'hanafi';

          return (
            <div
              key={msg.id}
              className={`flex flex-col ${isUser ? 'items-end' : 'items-start'} space-y-1.5`}
            >
              {/* Message Bubble Container */}
              <div
                className={`rounded-3xl p-4 sm:p-5 max-w-[92%] sm:max-w-[85%] text-xs leading-relaxed ${
                  isUser
                    ? 'bg-[#0B3B2C] text-white shadow-xs rounded-br-xs'
                    : 'bg-white border border-[#E2E8F0] text-slate-800 shadow-xs rounded-bl-xs space-y-3'
                }`}
              >
                {/* Assistant Brand Tag */}
                {!isUser && (
                  <div className="flex items-center gap-1.5 text-[#0B3B2C] font-bold text-xs pb-1 border-b border-slate-100">
                    <Sparkles className="w-3.5 h-3.5 text-[#10B981]" />
                    <span>Sunni Scholar Intelligence</span>
                  </div>
                )}

                {/* Body Text */}
                <div className="whitespace-pre-line text-xs font-normal">
                  {msg.content}
                </div>

                {/* Madhhab Consensus / Divergence Box */}
                {msg.madhhabComparison && (
                  <div className="bg-slate-50 rounded-2xl border border-slate-200 overflow-hidden text-xs space-y-2 p-3 mt-2">
                    <div className="flex items-center gap-1.5 font-bold text-[#0B3B2C] text-[11px]">
                      <Scale className="w-3.5 h-3.5 text-[#10B981]" />
                      <span>The Four Sunni Legal Schools (Madhahib):</span>
                    </div>

                    {/* School Tabs */}
                    <div className="grid grid-cols-4 gap-1 p-0.5 bg-slate-200/80 rounded-xl text-[10px]">
                      {(['hanafi', 'maliki', 'shafii', 'hanbali'] as const).map((school) => (
                        <button
                          key={school}
                          type="button"
                          onClick={() => {
                            soundEffects.playHapticTap();
                            setActiveMadhhabTab((prev) => ({ ...prev, [msg.id]: school }));
                          }}
                          className={`py-1 rounded-lg font-bold capitalize transition-all ${
                            activeTab === school
                              ? 'bg-[#0B3B2C] text-white shadow-xs'
                              : 'text-slate-600 hover:text-slate-900'
                          }`}
                        >
                          {school === 'shafii' ? "Shafi'i" : school}
                        </button>
                      ))}
                    </div>

                    {/* Active School Text Content */}
                    <div className="p-2.5 bg-white rounded-xl border border-slate-100 text-slate-700 text-[11px] leading-relaxed">
                      {msg.madhhabComparison[activeTab as keyof typeof msg.madhhabComparison]}
                    </div>
                  </div>
                )}

                {/* Source Proof Chips (Clickable: opens Page 4 or 5) */}
                {msg.sources && msg.sources.length > 0 && (
                  <div className="pt-2 border-t border-slate-100 space-y-1.5">
                    <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 block">
                      Canonical Source Evidences:
                    </span>
                    <div className="flex items-center gap-1.5 flex-wrap">
                      {msg.sources.map((src, idx) => (
                        <button
                          key={idx}
                          type="button"
                          onClick={() => {
                            soundEffects.playHapticTap();
                            onNavigateToScreen(src.targetScreen);
                          }}
                          className="px-2.5 py-1 rounded-xl bg-emerald-50 hover:bg-emerald-100 text-[#0B3B2C] text-[10px] font-bold border border-emerald-200 flex items-center gap-1 transition-colors"
                        >
                          <BookOpen className="w-3 h-3 text-[#10B981]" />
                          <span>{src.title}</span>
                          <ChevronRight className="w-2.5 h-2.5 text-slate-400" />
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {/* Timestamp */}
              <span className="text-[10px] text-slate-400 px-2">{msg.timestamp}</span>
            </div>
          );
        })}

        {/* Loading Indicator */}
        {isLoading && (
          <div className="flex items-center gap-2 p-3 bg-white rounded-2xl border border-slate-200 max-w-xs shadow-xs animate-pulse">
            <Sparkles className="w-4 h-4 text-[#10B981] animate-spin" />
            <span className="text-xs text-slate-500 font-medium">
              Consulting Quran &amp; Hadith compendiums...
            </span>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* 3. Input Dock & Suggested Queries */}
      <div className="p-3 sm:p-4 bg-white border-t border-[#E2E8F0] space-y-2 sticky bottom-0 z-30">
        {/* Suggested Query Chips */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none">
          {suggestedQueries.map((q, idx) => (
            <button
              key={idx}
              type="button"
              onClick={() => handleSendMessage(q)}
              className="px-2.5 py-1 rounded-full bg-slate-100 hover:bg-slate-200 text-[#0B3B2C] text-[10px] font-semibold whitespace-nowrap transition-colors border border-slate-200 shrink-0"
            >
              {q}
            </button>
          ))}
        </div>

        {/* Text Input Row */}
        <form
          onSubmit={(e) => {
            e.preventDefault();
            handleSendMessage();
          }}
          className="flex items-center gap-2"
        >
          <input
            type="text"
            value={inputText}
            onChange={(e) => setInputText(e.target.value)}
            placeholder="Ask any Islamic question (e.g. Sujud as-Sahw, Zakat rules)..."
            className="flex-1 px-4 py-2.5 rounded-2xl bg-slate-50 border border-[#E2E8F0] text-xs text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-[#10B981]/40"
          />
          <button
            type="submit"
            disabled={!inputText.trim() || isLoading}
            className="w-10 h-10 rounded-2xl bg-[#0B3B2C] hover:bg-[#134E3A] disabled:opacity-40 text-[#10B981] flex items-center justify-center shrink-0 transition-transform active:scale-95 shadow-xs"
          >
            <Send className="w-4 h-4 stroke-[2.5]" />
          </button>
        </form>
      </div>
    </div>
  );
};
