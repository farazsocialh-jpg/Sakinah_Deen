export type JourneyTrack = 'NEW_MUSLIM' | 'ISTIQAMAH';

export type Madhhab = 'Hanafi' | 'Shafii' | 'Maliki' | 'Hanbali';

export type CalculationMethod = 
  | 'Muslim World League'
  | 'Umm al-Qura (Makkah)'
  | 'ISNA (North America)'
  | 'Egyptian General Authority';

export type AsrJuristicMethod = 'Standard (Shafi\'i/Maliki/Hanbali)' | 'Hanafi (Double Shadow)';

export interface PrayerItem {
  id: 'fajr' | 'sunrise' | 'dhuhr' | 'asr' | 'maghrib' | 'isha';
  nameEnglish: string;
  nameArabic: string;
  time: string;
  fardRakats: number;
  sunnahBefore: number;
  sunnahAfter: number;
  isFardCompleted: boolean;
  isJamaah: boolean;
  isSunnahCompleted: boolean;
  isNawafilCompleted: boolean;
  nawafilName?: string;
}

export interface QadhaLedger {
  fajr: number;
  dhuhr: number;
  asr: number;
  maghrib: number;
  isha: number;
  witr: number;
  dailyGoalPace: number;
}

export interface VisualPostureStep {
  id: number;
  name: string;
  arabicTitle: string;
  postureKey: 'takbir' | 'qiyam' | 'ruku' | 'itidal' | 'sujud' | 'jalsah' | 'tashahhud' | 'taslim';
  postureAngleText: string;
  footPositioning: string;
  handPlacement: string;
  arabicRecitation: string;
  phoneticTransliteration: string;
  englishTranslation: string;
  sunnahNotes: string;
  durationSec: number;
}

export interface Ayah {
  numberInSurah: number;
  arabicText: string;
  transliteration: string;
  translation: string;
  tafseerIbnKathir: string;
  tafseerTabari: string;
  tafseerQurtubi: string;
  tafseerSadi: string;
  tafseerMaarifulQuran: string;
  hadithProofQuote?: string;
  memorizationMistake?: 'slip' | 'tajweed' | 'mastered' | null;
}

export interface Surah {
  number: number;
  nameEnglish: string;
  nameArabic: string;
  englishMeaning: string;
  revelationType: 'Makki' | 'Madani';
  ayahCount: number;
  juzNumber: number;
  ayahs: Ayah[];
}

export interface HadithItem {
  id: string;
  bookKey: string;
  bookTitle: string;
  bookTotalCount: number;
  hadithNumber: number;
  chapterTitle: string;
  grade: 'SAHIH' | 'HASAN' | 'DA\'IF';
  gradingScholar: string;
  arabicText: string;
  sanad: string;
  matnTranslation: string;
  bookmarked?: boolean;
}

export interface TravelConcessionState {
  distanceFromHomeKm: number;
  thresholdKm: number; // 88 km
  isConcessionActive: boolean;
  homeCity: string;
  currentLocationName: string;
  vehicleHeadingDeg: number;
  kaabaBearingDeg: number;
  speedKmh: number;
  bluetoothConnected: boolean;
}

export interface WealthZakatState {
  nisabStandard: 'gold' | 'silver';
  goldSpotPricePerGram: number; // e.g. 88.40 USD
  silverSpotPricePerGram: number; // e.g. 1.15 USD
  cashInHandAndBank: number;
  goldValueHeld: number;
  silverValueHeld: number;
  businessInventory: number;
  stocksInvestments: number;
  immediateDebts: number;
  sadaqahJarCollected: number;
  sadaqahMonthlyGoal: number;
  recentDonations: Array<{ id: string; amount: number; date: string; note: string }>;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
  sources?: Array<{ title: string; ref: string; targetScreen: 'quran' | 'hadith' }>;
  madhhabComparison?: {
    hanafi: string;
    maliki: string;
    shafii: string;
    hanbali: string;
  };
}

export type DeviceMode = 'android' | 'ios' | 'expanded';
export type ScreenId = 
  | 'onboarding'
  | 'salah'
  | 'visual_guide'
  | 'quran'
  | 'hadith'
  | 'travel'
  | 'wealth'
  | 'scholar_chat';
