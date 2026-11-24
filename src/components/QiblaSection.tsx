import React, { useState } from 'react';
import { Compass, Navigation, MapPin } from 'lucide-react';
import { calculateQibla } from '../data/prayerCalc';

export const QiblaSection: React.FC = () => {
  const [lat, setLat] = useState<number>(51.5074); // London
  const [lon, setLon] = useState<number>(-0.1278);
  const [locationName, setLocationName] = useState<string>('London, UK');

  const qibla = calculateQibla(lat, lon);

  const presets = [
    { name: 'London, UK', lat: 51.5074, lon: -0.1278 },
    { name: 'New York, USA', lat: 40.7128, lon: -74.006 },
    { name: 'Tokyo, Japan', lat: 35.6762, lon: 139.6503 },
    { name: 'Sydney, Australia', lat: -33.8688, lon: 151.2093 },
    { name: 'Paris, France', lat: 48.8566, lon: 2.3522 },
    { name: 'Singapore', lat: 1.3521, lon: 103.8198 },
  ];

  return (
    <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-6 md:p-8 space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <Compass className="w-5 h-5 text-emerald-400" />
            Qibla Direction & Kaaba Bearing
          </h2>
          <p className="text-slate-400 text-sm mt-1">
            Calculated via Great-Circle forward azimuth towards the Kaaba (21.4225° N, 39.8262° E)
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          {presets.map((p) => (
            <button
              key={p.name}
              onClick={() => {
                setLat(p.lat);
                setLon(p.lon);
                setLocationName(p.name);
              }}
              className={`px-3 py-1.5 text-xs font-medium rounded-lg border transition ${
                locationName === p.name
                  ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40'
                  : 'bg-slate-950 text-slate-300 border-slate-800 hover:border-slate-700'
              }`}
            >
              {p.name}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8 items-center pt-4">
        {/* Compass Visualizer */}
        <div className="flex flex-col items-center justify-center p-6 bg-slate-950/70 border border-slate-800/80 rounded-2xl relative">
          <div className="relative w-64 h-64 rounded-full border-2 border-slate-700 flex items-center justify-center shadow-inner">
            {/* Compass degree tick marks */}
            <span className="absolute top-2 text-xs font-bold text-red-400">N (0°)</span>
            <span className="absolute bottom-2 text-xs font-bold text-slate-500">S (180°)</span>
            <span className="absolute right-2 text-xs font-bold text-slate-500">E (90°)</span>
            <span className="absolute left-2 text-xs font-bold text-slate-500">W (270°)</span>

            {/* Inner Ring */}
            <div className="w-48 h-48 rounded-full border border-slate-800/80 flex items-center justify-center relative">
              {/* Needle rotating towards Qibla */}
              <div
                className="absolute w-full h-full flex items-center justify-center transition-transform duration-700 ease-out"
                style={{ transform: `rotate(${qibla.bearing}deg)` }}
              >
                <div className="w-1.5 h-24 bg-gradient-to-t from-transparent via-emerald-500 to-emerald-400 rounded-full relative -top-6">
                  <div className="w-4 h-4 bg-emerald-400 rounded-full absolute -top-1 -left-1.5 shadow-lg shadow-emerald-400/50 flex items-center justify-center">
                    <div className="w-1.5 h-1.5 bg-slate-950 rounded-full" />
                  </div>
                </div>
              </div>

              {/* Kaaba Symbol at Center */}
              <div className="w-12 h-12 bg-slate-900 border border-emerald-500/40 rounded-xl flex items-center justify-center shadow-md z-10">
                <span className="text-lg">🕋</span>
              </div>
            </div>
          </div>

          <div className="mt-4 text-center">
            <span className="text-3xl font-mono font-bold text-emerald-400">{qibla.bearing}°</span>
            <span className="text-slate-400 text-sm ml-2 font-medium">({qibla.cardinal})</span>
            <div className="text-xs text-slate-500 mt-0.5">True North Azimuth</div>
          </div>
        </div>

        {/* Qibla Details */}
        <div className="space-y-4">
          <div className="bg-slate-950/60 border border-slate-800 p-4 rounded-xl flex items-center justify-between">
            <div>
              <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider block">Current Location</span>
              <span className="text-base font-semibold text-white mt-0.5 block">{locationName}</span>
              <span className="text-xs font-mono text-slate-500">
                {lat.toFixed(4)}° N, {lon.toFixed(4)}° E
              </span>
            </div>
            <MapPin className="w-6 h-6 text-emerald-400" />
          </div>

          <div className="bg-slate-950/60 border border-slate-800 p-4 rounded-xl flex items-center justify-between">
            <div>
              <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider block">Kaaba Coordinate</span>
              <span className="text-base font-semibold text-white mt-0.5 block">Al-Masjid Al-Haram</span>
              <span className="text-xs font-mono text-slate-500">21.4225° N, 39.8262° E</span>
            </div>
            <span className="text-2xl">🕋</span>
          </div>

          <div className="bg-slate-950/60 border border-slate-800 p-4 rounded-xl flex items-center justify-between">
            <div>
              <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider block">Great-Circle Distance</span>
              <span className="text-2xl font-mono font-bold text-emerald-400 mt-0.5 block">
                {qibla.distanceKm.toLocaleString()} km
              </span>
              <span className="text-xs text-slate-500">Direct geodesic path to Makkah</span>
            </div>
            <Navigation className="w-6 h-6 text-emerald-400" />
          </div>
        </div>
      </div>
    </div>
  );
};
