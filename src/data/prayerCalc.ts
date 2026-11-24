// Astronomical Solar Declination & Equation of Time calculation (Spencer 1971 / NOAA)
// Matching the Kotlin implementation in shared/src/commonMain/.../PrayerCalculationEngineImpl.kt

export interface PrayerTimesResult {
  fajr: string;
  sunrise: string;
  dhuhr: string;
  asr: string;
  maghrib: string;
  isha: string;
  activePrayer: string;
  nextPrayer: string;
  minutesToNext: number;
}

export const CALC_METHODS: Record<string, { name: string; fajrAngle: number; ishaAngle: number; ishaInterval?: number }> = {
  MWL: { name: 'Muslim World League (MWL)', fajrAngle: 18.0, ishaAngle: 17.0 },
  ISNA: { name: 'Islamic Society of North America (ISNA)', fajrAngle: 15.0, ishaAngle: 15.0 },
  UMM_AL_QURA: { name: 'Umm Al-Qura University, Makkah', fajrAngle: 18.5, ishaAngle: 0, ishaInterval: 90 },
  EGYPT: { name: 'Egyptian General Authority', fajrAngle: 19.5, ishaAngle: 17.5 },
  KARACHI: { name: 'Univ. of Islamic Sciences, Karachi', fajrAngle: 18.0, ishaAngle: 18.0 },
  DUBAI: { name: 'Dubai Islamic Affairs', fajrAngle: 18.2, ishaAngle: 18.2 },
  QATAR: { name: 'Qatar Ministry of Awqaf', fajrAngle: 18.0, ishaAngle: 0, ishaInterval: 90 },
};

function degToRad(deg: number): number {
  return (deg * Math.PI) / 180.0;
}

function radToDeg(rad: number): number {
  return (rad * 180.0) / Math.PI;
}

export function calculatePrayerTimes(
  lat: number,
  lon: number,
  date: Date = new Date(),
  methodKey: string = 'MWL',
  hanafi: boolean = false
): PrayerTimesResult {
  const method = CALC_METHODS[methodKey] || CALC_METHODS.MWL;

  const startOfYear = new Date(date.getFullYear(), 0, 0);
  const diff = date.getTime() - startOfYear.getTime();
  const dayOfYear = Math.floor(diff / (1000 * 60 * 60 * 24));

  // Fractional year in radians
  const gamma = (2 * Math.PI * (dayOfYear - 1)) / 365.0;

  // Equation of time (in minutes)
  const eqtime =
    229.18 *
    (0.000075 +
      0.001868 * Math.cos(gamma) -
      0.032077 * Math.sin(gamma) -
      0.014615 * Math.cos(2 * gamma) -
      0.040849 * Math.sin(2 * gamma));

  // Solar declination (in radians)
  const decl =
    0.006918 -
    0.399912 * Math.cos(gamma) +
    0.070257 * Math.sin(gamma) -
    0.006758 * Math.cos(2 * gamma) +
    0.000907 * Math.sin(2 * gamma) -
    0.002697 * Math.cos(3 * gamma) +
    0.00148 * Math.sin(3 * gamma);

  // Time zone offset in hours
  const tzOffset = -date.getTimezoneOffset() / 60;

  // Solar noon in hours (local standard time)
  const noon = 12.0 + tzOffset - lon / 15.0 - eqtime / 60.0;

  const latRad = degToRad(lat);

  // Hour angle for a given solar altitude angle
  const hourAngle = (altitudeAngle: number): number => {
    const altRad = degToRad(altitudeAngle);
    const cosHA = (Math.sin(altRad) - Math.sin(latRad) * Math.sin(decl)) / (Math.cos(latRad) * Math.cos(decl));
    if (cosHA > 1.0) return 0; // sun never reaches altitude
    if (cosHA < -1.0) return Math.PI;
    return Math.acos(cosHA);
  };

  // Hour angle for Asr
  const shadowFactor = hanafi ? 2 : 1;
  const asrAlt = radToDeg(Math.atan(1.0 / (shadowFactor + Math.tan(Math.abs(latRad - decl)))));
  const asrHA = radToDeg(hourAngle(asrAlt)) / 15.0;

  // Sunrise/Sunset angle (-0.833° for refraction and solar diameter)
  const sunriseHA = radToDeg(hourAngle(-0.833)) / 15.0;

  // Fajr hour angle
  const fajrHA = radToDeg(hourAngle(-method.fajrAngle)) / 15.0;

  // Isha hour angle
  let ishaTimeHours: number;
  if (method.ishaInterval && method.ishaInterval > 0) {
    ishaTimeHours = noon + sunriseHA + method.ishaInterval / 60.0;
  } else {
    const ishaHA = radToDeg(hourAngle(-method.ishaAngle)) / 15.0;
    ishaTimeHours = noon + ishaHA;
  }

  const fajrTime = noon - fajrHA;
  const sunriseTime = noon - sunriseHA;
  const dhuhrTime = noon + 2 / 60; // 2 minutes past solar noon
  const asrTime = noon + asrHA;
  const maghribTime = noon + sunriseHA;

  const formatHours = (hours: number): string => {
    const h = (Math.floor(hours) + 24) % 24;
    const m = Math.floor((hours - Math.floor(hours)) * 60);
    const ampm = h >= 12 ? 'PM' : 'AM';
    const dispH = h % 12 === 0 ? 12 : h % 12;
    const dispM = m < 10 ? `0${m}` : m;
    return `${dispH}:${dispM} ${ampm}`;
  };

  const toMinutes = (hours: number): number => {
    const h = (Math.floor(hours) + 24) % 24;
    const m = Math.floor((hours - Math.floor(hours)) * 60);
    return h * 60 + m;
  };

  const currentMinutes = date.getHours() * 60 + date.getMinutes();

  const timesMap = [
    { name: 'Fajr', mins: toMinutes(fajrTime) },
    { name: 'Sunrise', mins: toMinutes(sunriseTime) },
    { name: 'Dhuhr', mins: toMinutes(dhuhrTime) },
    { name: 'Asr', mins: toMinutes(asrTime) },
    { name: 'Maghrib', mins: toMinutes(maghribTime) },
    { name: 'Isha', mins: toMinutes(ishaTimeHours) },
  ];

  let active = 'Isha';
  let next = 'Fajr';
  let minsToNext = 0;

  for (let i = 0; i < timesMap.length; i++) {
    const cur = timesMap[i];
    const nxt = timesMap[(i + 1) % timesMap.length];

    if (currentMinutes >= cur.mins && (i === timesMap.length - 1 || currentMinutes < nxt.mins)) {
      active = cur.name;
      next = nxt.name;
      minsToNext = nxt.mins > currentMinutes ? nxt.mins - currentMinutes : 1440 - currentMinutes + nxt.mins;
      break;
    }
  }

  if (currentMinutes < timesMap[0].mins) {
    active = 'Isha';
    next = 'Fajr';
    minsToNext = timesMap[0].mins - currentMinutes;
  }

  return {
    fajr: formatHours(fajrTime),
    sunrise: formatHours(sunriseTime),
    dhuhr: formatHours(dhuhrTime),
    asr: formatHours(asrTime),
    maghrib: formatHours(maghribTime),
    isha: formatHours(ishaTimeHours),
    activePrayer: active,
    nextPrayer: next,
    minutesToNext: minsToNext,
  };
}

export function calculateQibla(lat: number, lon: number): { bearing: number; cardinal: string; distanceKm: number } {
  const KAABA_LAT = 21.422487;
  const KAABA_LON = 39.826206;

  const lat1 = degToRad(lat);
  const lon1 = degToRad(lon);
  const lat2 = degToRad(KAABA_LAT);
  const lon2 = degToRad(KAABA_LON);

  const deltaLon = lon2 - lon1;
  const y = Math.sin(deltaLon);
  const x = Math.cos(lat1) * Math.tan(lat2) - Math.sin(lat1) * Math.cos(deltaLon);
  const qiblaRad = Math.atan2(y, x);
  let bearing = (radToDeg(qiblaRad) + 360.0) % 360.0;
  bearing = Math.round(bearing);

  // Great-circle distance
  const R = 6371; // Earth's mean radius in km
  const dLat = lat2 - lat1;
  const dLon = lon2 - lon1;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  const distanceKm = Math.round(R * c);

  const directions = ['N', 'NNE', 'NE', 'ENE', 'E', 'ESE', 'SE', 'SSE', 'S', 'SSW', 'SW', 'WSW', 'W', 'WNW', 'NW', 'NNW'];
  const index = Math.round(bearing / 22.5) % 16;
  const cardinal = directions[index];

  return { bearing, cardinal, distanceKm };
}
