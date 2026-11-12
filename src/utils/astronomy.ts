/**
 * Astronomical Solar Calculation Engine for Sakinah Web Simulator
 * Uses exact Spencer/NOAA solar declination & Equation of Time formulas.
 * Accounts for real local timezone offsets and official regional calculation standards.
 */

export type CalculationAuthority =
  | 'UMM_AL_QURA'
  | 'QATAR'
  | 'DUBAI'
  | 'KARACHI'
  | 'NORTH_AMERICA'
  | 'MUSLIM_WORLD_LEAGUE'
  | 'EGYPTIAN';

export type AsrJuristicMethod = 'STANDARD' | 'HANAFI';

export interface LocationPreset {
  name: string;
  country: string;
  latitude: number;
  longitude: number;
  timezoneOffsetHours: number; // e.g. +3 for Qatar/Saudi, +5 for Pakistan, 0 for London (GMT), -5 for New York
  defaultMethod: CalculationAuthority;
  defaultAsr: AsrJuristicMethod;
}

export const CITY_PRESETS: LocationPreset[] = [
  {
    name: 'Doha',
    country: 'Qatar',
    latitude: 25.2854,
    longitude: 51.5310,
    timezoneOffsetHours: 3.0,
    defaultMethod: 'QATAR',
    defaultAsr: 'STANDARD'
  },
  {
    name: 'Makkah al-Mukarramah',
    country: 'Saudi Arabia',
    latitude: 21.4225,
    longitude: 39.8262,
    timezoneOffsetHours: 3.0,
    defaultMethod: 'UMM_AL_QURA',
    defaultAsr: 'STANDARD'
  },
  {
    name: 'Madinah al-Munawwarah',
    country: 'Saudi Arabia',
    latitude: 24.4686,
    longitude: 39.6142,
    timezoneOffsetHours: 3.0,
    defaultMethod: 'UMM_AL_QURA',
    defaultAsr: 'STANDARD'
  },
  {
    name: 'Dubai',
    country: 'United Arab Emirates',
    latitude: 25.2048,
    longitude: 55.2708,
    timezoneOffsetHours: 4.0,
    defaultMethod: 'DUBAI',
    defaultAsr: 'STANDARD'
  },
  {
    name: 'Karachi',
    country: 'Pakistan',
    latitude: 24.8607,
    longitude: 67.0011,
    timezoneOffsetHours: 5.0,
    defaultMethod: 'KARACHI',
    defaultAsr: 'HANAFI'
  },
  {
    name: 'London',
    country: 'United Kingdom',
    latitude: 51.5074,
    longitude: -0.1278,
    timezoneOffsetHours: 0.0,
    defaultMethod: 'MUSLIM_WORLD_LEAGUE',
    defaultAsr: 'STANDARD'
  },
  {
    name: 'New York',
    country: 'United States',
    latitude: 40.7128,
    longitude: -74.0060,
    timezoneOffsetHours: -5.0,
    defaultMethod: 'NORTH_AMERICA',
    defaultAsr: 'STANDARD'
  }
];

export interface ComputedPrayerTimes {
  fajr: string;
  sunrise: string;
  dhuhr: string;
  asr: string;
  maghrib: string;
  isha: string;
  rawHours: {
    fajr: number;
    sunrise: number;
    dhuhr: number;
    asr: number;
    maghrib: number;
    isha: number;
  };
}

export function computePrayerTimes(
  lat: number,
  lon: number,
  timezoneOffsetHours: number,
  method: CalculationAuthority = 'UMM_AL_QURA',
  asrMethod: AsrJuristicMethod = 'STANDARD',
  adjustments: { [key: string]: number } = {}
): ComputedPrayerTimes {
  const now = new Date();
  const startOfYear = new Date(now.getFullYear(), 0, 1);
  const dayOfYear = Math.floor((now.getTime() - startOfYear.getTime()) / 86400000) + 1;

  const gamma = ((2.0 * Math.PI) / 365.0) * (dayOfYear - 1);
  const eqtime =
    229.18 *
    (0.000075 +
      0.001868 * Math.cos(gamma) -
      0.032077 * Math.sin(gamma) -
      0.014615 * Math.cos(2 * gamma) -
      0.040849 * Math.sin(2 * gamma));

  const declRad =
    0.006918 -
    0.399912 * Math.cos(gamma) +
    0.070257 * Math.sin(gamma) -
    0.006758 * Math.cos(2 * gamma) +
    0.000907 * Math.sin(2 * gamma) -
    0.002697 * Math.cos(3 * gamma) +
    0.00148 * Math.sin(3 * gamma);

  const latRad = (lat * Math.PI) / 180.0;

  // 1. Solar Noon in UTC minutes, converted to device / regional local time
  const solarNoonUtcMinutes = 720.0 - 4.0 * lon - eqtime;
  const solarNoonLocalHours = solarNoonUtcMinutes / 60.0 + timezoneOffsetHours;

  // Dhuhr: Solar noon + 2 minutes
  const dhuhrHours = solarNoonLocalHours + 2.0 / 60.0;

  // Sunrise & Sunset angle (-0.8333 degrees)
  const sunAltRad = (-0.8333 * Math.PI) / 180.0;
  const cosHourAngleSun =
    (Math.sin(sunAltRad) - Math.sin(latRad) * Math.sin(declRad)) /
    (Math.cos(latRad) * Math.cos(declRad));
  const hourAngleSunDeg =
    cosHourAngleSun >= -1.0 && cosHourAngleSun <= 1.0
      ? (Math.acos(cosHourAngleSun) * 180.0) / Math.PI
      : 90.0;
  const halfDayHours = hourAngleSunDeg / 15.0;

  const sunriseHours = solarNoonLocalHours - halfDayHours;
  const maghribHours = solarNoonLocalHours + halfDayHours;

  // Fajr Angle by method
  let fajrAngle = 18.0;
  if (method === 'UMM_AL_QURA') fajrAngle = 18.5;
  else if (method === 'EGYPTIAN') fajrAngle = 19.5;
  else if (method === 'NORTH_AMERICA') fajrAngle = 15.0;
  else if (method === 'DUBAI') fajrAngle = 18.2;
  else if (method === 'QATAR') fajrAngle = 18.0;
  else if (method === 'KARACHI') fajrAngle = 18.0;

  const fajrAltRad = (-fajrAngle * Math.PI) / 180.0;
  const cosHourAngleFajr =
    (Math.sin(fajrAltRad) - Math.sin(latRad) * Math.sin(declRad)) /
    (Math.cos(latRad) * Math.cos(declRad));
  const hourAngleFajrDeg =
    cosHourAngleFajr >= -1.0 && cosHourAngleFajr <= 1.0
      ? (Math.acos(cosHourAngleFajr) * 180.0) / Math.PI
      : 108.0;
  const fajrHours = solarNoonLocalHours - hourAngleFajrDeg / 15.0;

  // Asr shadow ratio (Standard 1:1 vs Hanafi 2:1)
  const shadowRatio = asrMethod === 'HANAFI' ? 2.0 : 1.0;
  const zenithDiff = Math.abs(latRad - declRad);
  const asrAltRad = Math.atan(1.0 / (shadowRatio + Math.tan(zenithDiff)));
  const cosHourAngleAsr =
    (Math.sin(asrAltRad) - Math.sin(latRad) * Math.sin(declRad)) /
    (Math.cos(latRad) * Math.cos(declRad));
  const hourAngleAsrDeg =
    cosHourAngleAsr >= -1.0 && cosHourAngleAsr <= 1.0
      ? (Math.acos(cosHourAngleAsr) * 180.0) / Math.PI
      : 60.0;
  const asrHours = solarNoonLocalHours + hourAngleAsrDeg / 15.0;

  // Isha
  let ishaHours: number;
  if (method === 'UMM_AL_QURA' || method === 'QATAR' || method === 'DUBAI') {
    ishaHours = maghribHours + 90.0 / 60.0;
  } else {
    let ishaAngle = 17.0;
    if (method === 'EGYPTIAN') ishaAngle = 17.5;
    else if (method === 'KARACHI') ishaAngle = 18.0;
    else if (method === 'NORTH_AMERICA') ishaAngle = 15.0;

    const ishaAltRad = (-ishaAngle * Math.PI) / 180.0;
    const cosHourAngleIsha =
      (Math.sin(ishaAltRad) - Math.sin(latRad) * Math.sin(declRad)) /
      (Math.cos(latRad) * Math.cos(declRad));
    const hourAngleIshaDeg =
      cosHourAngleIsha >= -1.0 && cosHourAngleIsha <= 1.0
        ? (Math.acos(cosHourAngleIsha) * 180.0) / Math.PI
        : 105.0;
    ishaHours = solarNoonLocalHours + hourAngleIshaDeg / 15.0;
  }

  const formatHours = (hRaw: number, offsetMin = 0) => {
    let h = (hRaw + offsetMin / 60.0) % 24.0;
    if (h < 0) h += 24.0;
    const hours24 = Math.floor(h);
    const mins = Math.floor((h - hours24) * 60.0);
    const period = hours24 >= 12 ? 'PM' : 'AM';
    const hours12 = hours24 % 12 === 0 ? 12 : hours24 % 12;
    return `${hours12.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')} ${period}`;
  };

  return {
    fajr: formatHours(fajrHours, adjustments.fajr || 0),
    sunrise: formatHours(sunriseHours, adjustments.sunrise || 0),
    dhuhr: formatHours(dhuhrHours, adjustments.dhuhr || 0),
    asr: formatHours(asrHours, adjustments.asr || 0),
    maghrib: formatHours(maghribHours, adjustments.maghrib || 0),
    isha: formatHours(ishaHours, adjustments.isha || 0),
    rawHours: {
      fajr: fajrHours,
      sunrise: sunriseHours,
      dhuhr: dhuhrHours,
      asr: asrHours,
      maghrib: maghribHours,
      isha: ishaHours
    }
  };
}
