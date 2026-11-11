import {
  PrayerItem,
  QadhaLedger,
  VisualPostureStep,
  Surah,
  HadithItem,
  TravelConcessionState,
  WealthZakatState
} from '../types';

export const INITIAL_PRAYERS: PrayerItem[] = [
  {
    id: 'fajr',
    nameEnglish: 'Fajr',
    nameArabic: 'الفجر',
    time: '05:14 AM',
    fardRakats: 2,
    sunnahBefore: 2,
    sunnahAfter: 0,
    isFardCompleted: true,
    isJamaah: true,
    isSunnahCompleted: true,
    isNawafilCompleted: false,
    nawafilName: 'Ishraq'
  },
  {
    id: 'sunrise',
    nameEnglish: 'Sunrise',
    nameArabic: 'الشروق',
    time: '06:38 AM',
    fardRakats: 0,
    sunnahBefore: 0,
    sunnahAfter: 0,
    isFardCompleted: false,
    isJamaah: false,
    isSunnahCompleted: false,
    isNawafilCompleted: false,
    nawafilName: 'Duha (Chasht)'
  },
  {
    id: 'dhuhr',
    nameEnglish: 'Dhuhr',
    nameArabic: 'الظهر',
    time: '12:45 PM',
    fardRakats: 4,
    sunnahBefore: 4,
    sunnahAfter: 2,
    isFardCompleted: true,
    isJamaah: true,
    isSunnahCompleted: true,
    isNawafilCompleted: true,
    nawafilName: '2 Rakat Nafl'
  },
  {
    id: 'asr',
    nameEnglish: 'Asr',
    nameArabic: 'العصر',
    time: '03:52 PM',
    fardRakats: 4,
    sunnahBefore: 4,
    sunnahAfter: 0,
    isFardCompleted: false,
    isJamaah: false,
    isSunnahCompleted: false,
    isNawafilCompleted: false,
    nawafilName: 'Sunnah Ghair Mu\'akkadah'
  },
  {
    id: 'maghrib',
    nameEnglish: 'Maghrib',
    nameArabic: 'المغرب',
    time: '06:18 PM',
    fardRakats: 3,
    sunnahBefore: 0,
    sunnahAfter: 2,
    isFardCompleted: false,
    isJamaah: false,
    isSunnahCompleted: false,
    isNawafilCompleted: false,
    nawafilName: 'Awwabin (6 Rakat)'
  },
  {
    id: 'isha',
    nameEnglish: 'Isha',
    nameArabic: 'العشاء',
    time: '07:44 PM',
    fardRakats: 4,
    sunnahBefore: 4,
    sunnahAfter: 2,
    isFardCompleted: false,
    isJamaah: false,
    isSunnahCompleted: false,
    isNawafilCompleted: false,
    nawafilName: 'Tahajjud & Witr (3 Rakat)'
  }
];

export const INITIAL_QADHA: QadhaLedger = {
  fajr: 12,
  dhuhr: 8,
  asr: 14,
  maghrib: 3,
  isha: 9,
  witr: 12,
  dailyGoalPace: 2
};

export const VISUAL_SALAH_STEPS: VisualPostureStep[] = [
  {
    id: 1,
    name: 'Takbirat al-Ihram',
    arabicTitle: 'تكبيرة الإحرام',
    postureKey: 'takbir',
    postureAngleText: 'Standing erect, 0° torso tilt, eyes focused on Sujud spot',
    footPositioning: 'Feet shoulder-width apart (~4 fingers to 1 handspan), parallel toward Qibla',
    handPlacement: 'Raise both hands up to earlobes (or shoulder level), palms open facing Qibla, fingers natural',
    arabicRecitation: 'اللَّهُ أَكْبَرُ',
    phoneticTransliteration: 'Allāhu Akbar',
    englishTranslation: 'Allah is the Greatest',
    sunnahNotes: 'Recite at the exact moment of lifting the hands. Enter sacred consecrated state of Salah.',
    durationSec: 3
  },
  {
    id: 2,
    name: 'Qiyam & Surah Al-Fatihah',
    arabicTitle: 'القيام وقراءة الفاتحة',
    postureKey: 'qiyam',
    postureAngleText: 'Upright vertical posture, back straight, eyes pinned to prostration ground',
    footPositioning: 'Weight balanced evenly on both feet, motionless',
    handPlacement: 'Right hand grasping left wrist or forearm resting below chest / on navel',
    arabicRecitation: 'بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ ۝ الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ ۝ الرَّحْمَٰنِ الرَّحِيمِ',
    phoneticTransliteration: 'Bismillāhir-Raḥmānir-Raḥīm. Al-ḥamdu lillāhi Rabbil-‘ālamīn. Ar-Raḥmānir-Raḥīm.',
    englishTranslation: 'In the name of Allah, Most Gracious, Most Merciful. All praise is for Allah—Lord of all worlds. The Entirely Merciful, the Especially Merciful.',
    sunnahNotes: 'Recite Thana (Subhanaka Allahumma...) first, then Ta\'awwudh, Basmalah, and complete Al-Fatihah.',
    durationSec: 8
  },
  {
    id: 3,
    name: 'Ruku‘ (The Bowing)',
    arabicTitle: 'الركوع',
    postureKey: 'ruku',
    postureAngleText: 'Torso hinged precisely 90° horizontal to floor; head aligned with spine (neither dropped nor raised)',
    footPositioning: 'Legs straight with slight knee flex if needed, feet anchored',
    handPlacement: 'Firmly cup kneecaps with fingers spread out wide like gripping an arch',
    arabicRecitation: 'سُبْحَانَ رَبِّيَ الْعَظِيمِ',
    phoneticTransliteration: 'Subḥāna Rabbiyal-‘Aẓīm (3 times)',
    englishTranslation: 'Glory be to my Lord, the Most Magnificent',
    sunnahNotes: 'Stay stationary in Ruku until bones settle (Tum\'aninah). Repeat Tasbih odd number of times (3, 5, 7).',
    durationSec: 5
  },
  {
    id: 4,
    name: 'I‘tidal (Rising from Ruku‘)',
    arabicTitle: 'الاعتدال والرفع',
    postureKey: 'itidal',
    postureAngleText: 'Completely straight standing posture before descending to floor',
    footPositioning: 'Feet firm on the mat, steady balance',
    handPlacement: 'Hands naturally lowered by sides (or folded according to Hanbali view)',
    arabicRecitation: 'سَمِعَ اللَّهُ لِمَنْ حَمِدَهُ ۝ رَبَّنَا وَلَكَ الْحَمْدُ حَمْدًا كَثِيرًا طَيِّبًا مُبَارَكًا فِيهِ',
    phoneticTransliteration: 'Sami‘ Allāhu liman ḥamidah. Rabbanā wa lakal-ḥamd, ḥamdan kathīran ṭayyiban mubārakan fīh.',
    englishTranslation: 'Allah hears whoever praises Him. Our Lord, to You is all praise—abundant, pure, and blessed.',
    sunnahNotes: 'Ensure full straight spine settlement before beginning descent into prostration.',
    durationSec: 4
  },
  {
    id: 5,
    name: 'Sujud (Prostration)',
    arabicTitle: 'السجود',
    postureKey: 'sujud',
    postureAngleText: '7 bodily points touching floor: forehead + nose, both palms, both knees, toes bent toward Qibla',
    footPositioning: 'Heels held together or slightly apart, toes flexed forward gripping floor toward Qibla',
    handPlacement: 'Palms flat on mat level with shoulders or ears, fingers together pointing toward Qibla; elbows lifted off floor',
    arabicRecitation: 'سُبْحَانَ رَبِّيَ الْأَعْلَى',
    phoneticTransliteration: 'Subḥāna Rabbiyal-A‘lā (3 times)',
    englishTranslation: 'Glory be to my Lord, the Most High',
    sunnahNotes: 'The closest a servant comes to Allah is during Sujud. Supplicate with sincerity and humility.',
    durationSec: 6
  },
  {
    id: 6,
    name: 'Jalsah (Sitting Between Sujuds)',
    arabicTitle: 'الجلسة بين السجدتين',
    postureKey: 'jalsah',
    postureAngleText: 'Upright seated posture, spine erect, eyes lowered to thighs',
    footPositioning: 'Iftirash: sit on left foot folded flat underneath, right foot kept upright with toes bent toward Qibla',
    handPlacement: 'Rest palms flat on knees/thighs with fingertips reaching kneecap edge',
    arabicRecitation: 'رَبِّ اغْفِرْ لِي ، رَبِّ اغْفِرْ لِي ، وَارْحَمْنِي وَعَافِنِي وَاهْدِنِي وَارْزُقْنِي',
    phoneticTransliteration: 'Rabbighfir lī, Rabbighfir lī, warḥamnī, wa ‘āfinī, wahdinī, warzuqnī.',
    englishTranslation: 'My Lord forgive me, my Lord forgive me, have mercy on me, grant me health, guide me, and provide for me.',
    sunnahNotes: 'Pause calmly until every vertebra returns to its natural position.',
    durationSec: 4
  },
  {
    id: 7,
    name: 'Tashahhud & Salawat',
    arabicTitle: 'التشهد والصلاة الإبراهيمية',
    postureKey: 'tashahhud',
    postureAngleText: 'Seated calmly. In 2-rakat prayers: Iftirash. In final sitting of 3/4 rakat: Tawarruk (Shafi\'i/Hanbali) or Iftirash (Hanafi)',
    footPositioning: 'Right foot upright with toes bent; left foot extended underneath shin',
    handPlacement: 'Left hand flat on left knee. Right hand fingers curled into a ring (thumb & middle finger) with index pointing toward Qibla',
    arabicRecitation: 'التَّحِيَّاتُ لِلَّهِ وَالصَّلَوَاتُ وَالطَّيِّبَاتُ ، السَّلَامُ عَلَيْكَ أَيُّهَا النَّبِيُّ وَرَحْمَةُ اللَّهِ وَبَرَكَاتُهُ ، السَّلَامُ عَلَيْنَا وَعَلَى عِبَادِ اللَّهِ الصَّالِحِينَ ، أَشْهَدُ أَنْ لَا إِلَهَ إِلَّا اللَّهُ وَأَشْهَدُ أَنَّ مُحَمَّدًا عَبْدُهُ وَرَسُولُهُ',
    phoneticTransliteration: 'At-taḥiyyātu lillāhi waṣ-ṣalawātu waṭ-ṭayyibāt. As-salāmu ‘alayka ayyuhan-Nabiyyu wa raḥmatullāhi wa barakātuh. As-salāmu ‘alaynā wa ‘alā ‘ibādillāhiṣ-ṣāliḥīn. Ash-hadu allā ilāha illallāh, wa ash-hadu anna Muḥammadan ‘abduhū wa Rasūluh.',
    englishTranslation: 'All greetings, prayers, and pure things belong to Allah. Peace be upon you, O Prophet, and the mercy of Allah and His blessings. Peace be upon us and upon the righteous servants of Allah. I bear witness that there is no deity worthy of worship except Allah, and I bear witness that Muhammad is His servant and messenger.',
    sunnahNotes: 'Raise the index finger slightly while reciting the testimony of faith (Ash-hadu allā ilāha illallāh).',
    durationSec: 10
  },
  {
    id: 8,
    name: 'Taslim (The Concluding Salam)',
    arabicTitle: 'التسليم',
    postureKey: 'taslim',
    postureAngleText: 'Turn head fully to the right so white of cheek is visible from behind, then turn to the left',
    footPositioning: 'Remain seated steadily in final sitting position',
    handPlacement: 'Hands resting peacefully on thighs',
    arabicRecitation: 'السَّلَامُ عَلَيْكُمْ وَرَحْمَةُ اللَّهِ',
    phoneticTransliteration: 'As-salāmu ‘alaykum wa raḥmatullāh (Right, then Left)',
    englishTranslation: 'May the peace and mercy of Allah be upon you',
    sunnahNotes: 'Complete the first Salam before commencing the head turn to the left. Exits the sacred state of Salah.',
    durationSec: 4
  }
];

export const CANONICAL_BOOKS = [
  { key: 'bukhari', title: 'Sahih al-Bukhari', author: 'Imam al-Bukhari', count: 7563, downloaded: true },
  { key: 'muslim', title: 'Sahih Muslim', author: 'Imam Muslim', count: 7500, downloaded: true },
  { key: 'abudawud', title: 'Sunan Abi Dawud', author: 'Imam Abu Dawud', count: 5274, downloaded: true },
  { key: 'tirmidhi', title: 'Jami‘ at-Tirmidhi', author: 'Imam at-Tirmidhi', count: 3956, downloaded: false },
  { key: 'nasai', title: 'Sunan an-Nasa\'i', author: 'Imam an-Nasa\'i', count: 5760, downloaded: false },
  { key: 'ibnmajah', title: 'Sunan Ibn Majah', author: 'Imam Ibn Majah', count: 4341, downloaded: false },
  { key: 'muwatta', title: 'Muwatta Malik', author: 'Imam Malik ibn Anas', count: 1858, downloaded: true },
  { key: 'ahmad', title: 'Musnad Ahmad', author: 'Imam Ahmad ibn Hanbal', count: 27647, downloaded: false },
  { key: 'darimi', title: 'Sunan al-Darimi', author: 'Imam al-Darimi', count: 3500, downloaded: false }
];

export const SAMPLE_HADITHS: HadithItem[] = [
  {
    id: 'bukhari-1',
    bookKey: 'bukhari',
    bookTitle: 'Sahih al-Bukhari',
    bookTotalCount: 7563,
    hadithNumber: 1,
    chapterTitle: 'Book of Revelation (Bad\' al-Wahy)',
    grade: 'SAHIH',
    gradingScholar: 'Muttafaq ‘Alayh (Highest Consensus)',
    arabicText: 'إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى، فَمَنْ كَانَتْ هِجْرَتُهُ إِلَى دُنْيَا يُصِيبُهَا أَوْ إِلَى امْرَأَةٍ يَنْكِحُهَا فَهِجْرَتُهُ إِلَى مَا هَاجَرَ إِلَيْهِ.',
    sanad: 'Humaidi narrated to us from Sufyan, from Yahya ibn Sa\'id al-Ansari, from Muhammad ibn Ibrahim al-Taymi, from Alqamah ibn Waqqas al-Laythi, from Umar ibn al-Khattab (may Allah be pleased with him):',
    matnTranslation: 'Actions are judged by intentions, and every person will get what they intended. So whoever emigrated for worldly gain or to marry a woman, his emigration is for that to which he emigrated.',
    bookmarked: true
  },
  {
    id: 'bukhari-5063',
    bookKey: 'bukhari',
    bookTitle: 'Sahih al-Bukhari',
    bookTotalCount: 7563,
    hadithNumber: 5063,
    chapterTitle: 'Book of the Virtues of the Quran (Fada\'il al-Qur\'an)',
    grade: 'SAHIH',
    gradingScholar: 'Imam al-Bukhari',
    arabicText: 'خَيْرُكُمْ مَنْ تَعَلَّمَ الْقُرْآنَ وَعَلَّمَهُ.',
    sanad: 'Hajjaj ibn Minhal narrated from Shu\'bah, from Alqamah ibn Marthad, from Sa\'d ibn Ubaydah, from Abu Abd al-Rahman al-Sulami, from Uthman ibn Affan (may Allah be pleased with him):',
    matnTranslation: 'The best among you are those who learn the Quran and teach it to others.',
    bookmarked: false
  },
  {
    id: 'muslim-140',
    bookKey: 'muslim',
    bookTitle: 'Sahih Muslim',
    bookTotalCount: 7500,
    hadithNumber: 140,
    chapterTitle: 'Book of Faith (Kitab al-Iman)',
    grade: 'SAHIH',
    gradingScholar: 'Imam Muslim',
    arabicText: 'بُنِيَ الإِسْلاَمُ عَلَى خَمْسٍ: شَهَادَةِ أَنْ لاَ إِلَهَ إِلاَّ اللَّهُ وَأَنَّ مُحَمَّدًا عَبْدُهُ وَرَسُولُهُ، وَإِقَامِ الصَّلاَةِ، وَإِيتَاءِ الزَّكَاةِ، وَحَجِّ الْبَيْتِ، وَصَوْمِ رَمَضَانَ.',
    sanad: 'Sahl ibn Uthman narrated from Abu Bakr ibn Ayyash, from Asim, from Abu Wa\'il, from Abdullah ibn Umar (may Allah be pleased with them both):',
    matnTranslation: 'Islam is founded upon five pillars: testifying that there is none worthy of worship except Allah and that Muhammad is His servant and messenger, establishing the prayer, paying Zakat, performing pilgrimage to the House, and fasting Ramadan.',
    bookmarked: true
  },
  {
    id: 'tirmidhi-1987',
    bookKey: 'tirmidhi',
    bookTitle: 'Jami‘ at-Tirmidhi',
    bookTotalCount: 3956,
    hadithNumber: 1987,
    chapterTitle: 'Book of Righteousness & Relations (Birr wa-Silah)',
    grade: 'HASAN',
    gradingScholar: 'Graded Hasan Sahih by Imam at-Tirmidhi; authenticated by Al-Albani',
    arabicText: 'اتَّقِ اللَّهَ حَيْثُمَا كُنْتَ، وَأَتْبِعِ السَّيِّئَةَ الْحَسَنَةَ تَمْحُهَا، وَخَالِقِ النَّاسَ بِخُلُقٍ حَسَنٍ.',
    sanad: 'Mahmud ibn Ghaylan narrated from Waki\', from Sufyan, from Habib ibn Abi Thabit, from Maymun ibn Abi Shabib, from Abu Dharr al-Ghifari (may Allah be pleased with him):',
    matnTranslation: 'Fear Allah wherever you may be; follow up an evil deed with a good deed and it will erase it; and interact with people with noble character.',
    bookmarked: false
  },
  {
    id: 'abudawud-1205',
    bookKey: 'abudawud',
    bookTitle: 'Sunan Abi Dawud',
    bookTotalCount: 5274,
    hadithNumber: 1205,
    chapterTitle: 'Book of Prayer: Travel Prayer Concessions (Salat al-Safar)',
    grade: 'SAHIH',
    gradingScholar: 'Authentic (Sahih) according to Ibn Hajar al-Asqalani',
    arabicText: 'كَانَ رَسُولُ اللَّهِ صلى الله عليه وسلم إِذَا ارْتَحَلَ قَبْلَ أَنْ تَزِيغَ الشَّمْسُ أَخَّرَ الظُّهْرَ إِلَى وَقْتِ الْعَصْرِ ثُمَّ نَزَلَ فَجَمَعَ بَيْنَهُمَا.',
    sanad: 'Musaddad narrated from Yahya, from Husayn al-Mu\'allim, from Yahya ibn Abi Kathir, from Anas ibn Malik:',
    matnTranslation: 'When the Messenger of Allah (peace be upon him) departed on a journey before the sun passed its meridian, he would delay Dhuhr until the time of Asr, then halt and combine the two (Jam‘ Ta’khir).',
    bookmarked: true
  }
];

export const SAMPLE_SURAHS: Surah[] = [
  {
    number: 1,
    nameEnglish: 'Al-Fatihah',
    nameArabic: 'الفاتحة',
    englishMeaning: 'The Opening',
    revelationType: 'Makki',
    ayahCount: 7,
    juzNumber: 1,
    ayahs: [
      {
        numberInSurah: 1,
        arabicText: 'بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ',
        transliteration: 'Bismillāhir-Raḥmānir-Raḥīm',
        translation: 'In the name of Allah, the Entirely Merciful, the Especially Merciful.',
        tafseerIbnKathir: 'The Basmalah is an opening of sanctification and blessing before every noble action. The scholars of Ahl al-Sunnah agree it is recited to seek divine aid and protection from Shaytan.',
        tafseerTabari: 'Tabari reports from Ibn Abbas that "Allah" is the possessor of divine majesty and devotion over all creation, "Ar-Rahman" possesses boundless mercy extending to all, and "Ar-Rahim" confers specific mercy upon the believers.',
        tafseerQurtubi: 'Qurtubi discusses whether the Basmalah constitutes an independent Ayah in Al-Fatihah (Shafi\'i view) or an opening separation marker (Maliki & Hanafi view). Both agreed on its supreme virtue.',
        tafseerSadi: 'Begin every movement, study, and prayer seeking grace through all of Allah’s Beautiful Names, acknowledging our utter dependency on Him.',
        tafseerMaarifulQuran: 'Mufti Muhammad Shafi clarifies the linguistic weight of Rahman and Rahim, showing how divine mercy preceded divine wrath in the creation of the cosmos.',
        hadithProofQuote: 'The Prophet (ﷺ) said: "Any significant deed that does not begin with Bismillah is clipped of blessing." (Ahmad, Ibn Majah)',
        memorizationMistake: 'mastered'
      },
      {
        numberInSurah: 2,
        arabicText: 'الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ',
        transliteration: 'Al-ḥamdu lillāhi Rabbil-‘ālamīn',
        translation: 'All praise is due to Allah, Lord of all the worlds.',
        tafseerIbnKathir: 'Al-Hamd is comprehensive gratitude with love and veneration. The "Al" signifies all forms of perfection belong inherently to Allah alone, the Creator and Sustainer of all existence.',
        tafseerTabari: 'Rabb signifies the Master who owns, nurtures, rectifies, and governs all created realms—mankind, jinn, angels, and celestial bodies.',
        tafseerQurtubi: 'Gratitude is the head of all worship. When Adam sneezed upon his creation, he uttered: Al-Hamdulillah, and Allah replied with His mercy.',
        tafseerSadi: 'Praise belongs to Him both for His absolute perfection of attributes and for the torrential blessings He showers upon his creation.',
        tafseerMaarifulQuran: 'Points out the shift from Basmalah to Al-Hamd: recognition of mercy naturally compels the human heart to spontaneously overflow in gratitude.',
        hadithProofQuote: 'Allah said in Hadith Qudsi: "I have divided prayer between Myself and My servant into two halves... When he says: All praise is due to Allah, Allah says: My servant has praised Me."'
      },
      {
        numberInSurah: 3,
        arabicText: 'الرَّحْمَٰنِ الرَّحِيمِ',
        transliteration: 'Ar-Raḥmānir-Raḥīm',
        translation: 'The Entirely Merciful, the Especially Merciful.',
        tafseerIbnKathir: 'Reiterating these two lofty attributes immediately after "Rabb al-Alamin" balances majestic awe with intimate hope, so the servant draws near with longing rather than despair.',
        tafseerTabari: 'Divine compassion embraces the whole universe in sustenance, and preserves the righteous in faith.',
        tafseerQurtubi: 'Ar-Rahman is non-transferable to any creation; Ar-Rahim may be figuratively used to describe a merciful servant.',
        tafseerSadi: 'All grace that reaches creation from birth to paradise is the manifest fruit of His Mercy.',
        tafseerMaarifulQuran: 'Reinforces optimism; even when facing trials or repentance, His mercy outstrips His anger.'
      },
      {
        numberInSurah: 4,
        arabicText: 'مَالِكِ يَوْمِ الدِّينِ',
        transliteration: 'Māliki Yawmid-Dīn',
        translation: 'Sovereign of the Day of Recompense.',
        tafseerIbnKathir: 'On that Day, all earthly kings, tyrants, and rulers will be stripped of their illusions of power. To Whom belongs sovereignty this Day? To Allah, the One, the Subduer.',
        tafseerTabari: 'Yawm al-Din is the Day of Final Accounting and Retribution, where justice is executed with supreme precision.',
        tafseerQurtubi: 'Recited as "Maalik" (Owner) and "Malik" (King) in verified Mutawatir Qira\'at, uniting ownership with ultimate reign.',
        tafseerSadi: 'Contemplating this Ayah awakens Taqwa (mindful awe) and restrains the soul from transgression.',
        tafseerMaarifulQuran: 'Reminds the believer that accountability is certain; justice will rectify all temporal oppressions.'
      },
      {
        numberInSurah: 5,
        arabicText: 'إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ',
        transliteration: 'Iyyāka na‘budu wa iyyāka nasta‘īn',
        translation: 'It is You alone we worship, and You alone we ask for help.',
        tafseerIbnKathir: 'This verse is the pivot of the entire Quran: Pure Tawhid. Fronting the pronoun ("Iyyaka") denotes strict exclusivity (Hasr): worship is directed to none other, and aid is sought from none other.',
        tafseerTabari: 'We submit our limbs in obedience to You alone, free from Shirk (polytheism) and Riya (showing off), and lean exclusively upon Your power.',
        tafseerQurtubi: 'Ibadah cures pride and arrogance; Isti\'anah cures self-reliance and conceit.',
        tafseerSadi: 'Putting worship before seeking aid emphasizes that pleasing Allah is the purpose, while His assistance is the indispensable means.',
        tafseerMaarifulQuran: 'Ibn al-Qayyim authored his classical magnum opus "Madarij al-Salikin" (Ranks of the Seekers) entirely around the commentary of this single Ayah.'
      },
      {
        numberInSurah: 6,
        arabicText: 'اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ',
        transliteration: 'Ihdinaṣ-ṣirāṭal-mustaqīm',
        translation: 'Guide us to the straight path.',
        tafseerIbnKathir: 'The Straight Path is Islam, the Book of Allah, and the Sunnah of Muhammad (ﷺ) without deviation, innovation, or extremism.',
        tafseerTabari: 'Guidance here entails both showing the path (Hidayat al-Dalaalah) and granting the internal strength to steadfastly tread it until death (Hidayat al-Tawfiq).',
        tafseerQurtubi: 'Even the righteous ask for guidance constantly in every prayer, because perseverance in faith requires continuous divine support.',
        tafseerSadi: 'A path that is wide, illuminated, clear, and leads directly to the pleasure of Allah and His gardens.',
        tafseerMaarifulQuran: 'The greatest blessing one can ask for is clarity of heart and steadfastness on the prophetic methodology.'
      },
      {
        numberInSurah: 7,
        arabicText: 'صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ',
        transliteration: 'Ṣirāṭalladhīna an‘amta ‘alayhim, ghayril-maghḍūbi ‘alayhim walāḍ-ḍāllīn',
        translation: 'The path of those upon whom You have bestowed favor, not of those who have evoked [Your] anger or of those who are astray.',
        tafseerIbnKathir: 'Those favored are defined in Surah An-Nisa (4:69): the Prophets, the Truthful (Siddiqin), the Martyrs (Shuhada), and the Righteous (Salihin). The Prophet (ﷺ) clarified in authentic Hadith that those who incurred anger are those who knew the truth but abandoned it, and the astray are those who worshipped without knowledge.',
        tafseerTabari: 'A warning against intellectual arrogance and ignorant spiritual zeal.',
        tafseerQurtubi: 'At the end of this recitation, it is recommended for both Imam and follower to pronounce "Ameen" (O Allah, answer our supplication).',
        tafseerSadi: 'The middle path between negligence and extremism.',
        tafseerMaarifulQuran: 'Concludes with a heartfelt collective prayer for the Ummah to stay united upon authentic orthodox Sunni scholarship.',
        hadithProofQuote: 'The Prophet (ﷺ) said: "When the Imam says Ameen, say Ameen, for whoever’s Ameen coincides with the angels has their past sins forgiven." (Al-Bukhari 780)'
      }
    ]
  },
  {
    number: 112,
    nameEnglish: 'Al-Ikhlas',
    nameArabic: 'الإخلاص',
    englishMeaning: 'The Sincerity',
    revelationType: 'Makki',
    ayahCount: 4,
    juzNumber: 30,
    ayahs: [
      {
        numberInSurah: 1,
        arabicText: 'قُلْ هُوَ اللَّهُ أَحَدٌ',
        transliteration: 'Qul Huwallāhu Aḥad',
        translation: 'Say, "He is Allah, [who is] One.',
        tafseerIbnKathir: 'He is the Unique, without peer, minister, rival, equal, or likeness. This Surah is equivalent to one-third of the Quran in reward because it articulates pure divine unity (Tawhid al-Asma was-Sifat).',
        tafseerTabari: 'Refutation against all forms of polytheism and anthropomorphism.',
        tafseerQurtubi: 'Ahad implies absolute singularity that cannot be divided or multiplied.',
        tafseerSadi: 'He is singular in His majestic greatness and unmatched beauty.',
        tafseerMaarifulQuran: 'Revealed when the Quraysh asked: Describe the lineage of your Lord to us.'
      },
      {
        numberInSurah: 2,
        arabicText: 'اللَّهُ الصَّمَدُ',
        transliteration: 'Allāhuṣ-Ṣamad',
        translation: 'Allah, the Eternal Refuge.',
        tafseerIbnKathir: 'Ibn Abbas said: The Master who is complete in His nobility, the Magnificent who is complete in His grandeur, upon whom all creation depends for their needs, while He needs none.',
        tafseerTabari: 'As-Samad is the self-sufficient Lord who neither eats nor drinks and has no hollow core.',
        tafseerQurtubi: 'The Sanctuary to which all souls run in distress.',
        tafseerSadi: 'All needs in the heavens and earth are brought before His doorstep.',
        tafseerMaarifulQuran: 'Synthesizes total transcendence with universal providence.'
      },
      {
        numberInSurah: 3,
        arabicText: 'لَمْ يَلِدْ وَلَمْ يُولَدْ',
        transliteration: 'Lam yalid wa lam yūlad',
        translation: 'He neither begets nor is born,',
        tafseerIbnKathir: 'He has no child, no parent, and no consort. Pure refutation of pagan and theological claims attributing offspring to the Creator.',
        tafseerTabari: 'Nothing preceded Him, and nothing emanates from Him as physical progeny.',
        tafseerQurtubi: 'Eternal without beginning, Everlasting without end.',
        tafseerSadi: 'He is above any deficiency or biological dependency.',
        tafseerMaarifulQuran: 'Clear and unequivocal assertion of monotheism.'
      },
      {
        numberInSurah: 4,
        arabicText: 'وَلَمْ يَكُنْ لَهُ كُفُوًا أَحَدٌ',
        transliteration: 'Wa lam yakul-lahū kufuwan aḥad',
        translation: 'Nor is there to Him any equivalent."',
        tafseerIbnKathir: 'There is nothing whatever like unto Him, and He is the Hearing, the Seeing (Surah Ash-Shura 42:11).',
        tafseerTabari: 'No equal, no likeness, and no counterpart among anything that exists.',
        tafseerQurtubi: 'The crown of Islamic creed.',
        tafseerSadi: 'He has no likeness in His Names, Attributes, or Actions.',
        tafseerMaarifulQuran: 'The fortress of the believer against doubts and philosophical speculation.'
      }
    ]
  }
];

export const INITIAL_TRAVEL_STATE: TravelConcessionState = {
  distanceFromHomeKm: 74,
  thresholdKm: 88,
  isConcessionActive: false,
  homeCity: 'Home Anchor (London, UK)',
  currentLocationName: 'M40 Motorway, Milepost 46',
  vehicleHeadingDeg: 42,
  kaabaBearingDeg: 118,
  speedKmh: 68,
  bluetoothConnected: true
};

export const INITIAL_WEALTH_STATE: WealthZakatState = {
  nisabStandard: 'gold',
  goldSpotPricePerGram: 88.40,
  silverSpotPricePerGram: 1.15,
  cashInHandAndBank: 14200,
  goldValueHeld: 4500,
  silverValueHeld: 320,
  businessInventory: 0,
  stocksInvestments: 6800,
  immediateDebts: 1800,
  sadaqahJarCollected: 76,
  sadaqahMonthlyGoal: 100,
  recentDonations: [
    { id: '1', amount: 5, date: 'Today, 08:30 AM', note: 'Morning barakah charity' },
    { id: '2', amount: 20, date: 'Yesterday', note: 'Local orphan sponsorship support' },
    { id: '3', amount: 10, date: '3 days ago', note: 'Jumu\'ah mosque micro-giving' },
    { id: '4', amount: 1, date: '4 days ago', note: 'Daily gratitude micro-sadaqah' }
  ]
};
