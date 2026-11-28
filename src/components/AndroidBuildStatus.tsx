import React, { useState, useEffect } from 'react';
import { Download, ShieldCheck, RefreshCw, AlertCircle } from 'lucide-react';

interface ArtifactInfo {
  exists: boolean;
  sizeBytes: number;
  sizeMb: string;
  updatedAt: string | null;
  downloadUrl?: string;
  filename: string;
}

interface BuildStatusResponse {
  app: string;
  platform: string;
  version: string;
  serverStatus: string;
  artifacts: {
    debugApk: ArtifactInfo;
    releaseApk: ArtifactInfo;
    releaseAab: ArtifactInfo;
  };
}

const ACCENTS = {
  emerald: {
    badge: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    button: 'bg-emerald-500 hover:bg-emerald-400 text-slate-950',
  },
  indigo: {
    badge: 'bg-indigo-500/10 text-indigo-400 border-indigo-500/20',
    button: 'bg-indigo-600 hover:bg-indigo-500 text-white',
  },
};

interface ArtifactCardProps {
  label: string;
  artifact: ArtifactInfo | undefined;
  accent: keyof typeof ACCENTS;
  note: string;
}

const ArtifactCard: React.FC<ArtifactCardProps> = ({ label, artifact, accent, note }) => {
  const tone = ACCENTS[accent];
  const sizeLabel = artifact?.exists ? `${artifact.sizeMb} MB` : 'Not available';
  const updatedLabel = artifact?.updatedAt
    ? new Date(artifact.updatedAt).toLocaleString()
    : 'Build time unavailable';

  return (
    <div className="bg-slate-950/80 border border-slate-800 rounded-xl p-5 flex flex-col justify-between">
      <div>
        <div className="flex items-center justify-between mb-3">
          <span className={`text-xs font-bold uppercase tracking-wider px-2 py-0.5 rounded border ${tone.badge}`}>
            {label}
          </span>
          <span className="text-xs font-mono text-slate-400">{sizeLabel}</span>
        </div>
        <h3 className="text-base font-bold text-white break-all">{artifact?.filename ?? label}</h3>
        <p className="text-xs text-slate-400 mt-1">{note}</p>
      </div>

      <div className="mt-5 pt-3 border-t border-slate-800/80 flex items-center justify-between gap-3">
        <span className="text-[11px] font-mono text-slate-500">{updatedLabel}</span>
        {artifact?.exists && artifact.downloadUrl ? (
          <a
            href={artifact.downloadUrl}
            download={artifact.filename}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-bold text-xs shadow-md transition ${tone.button}`}
          >
            <Download className="w-3.5 h-3.5" />
            Download {label}
          </a>
        ) : (
          <span className="flex items-center gap-1.5 text-[11px] text-slate-500">
            <AlertCircle className="w-3.5 h-3.5" />
            Not built yet
          </span>
        )}
      </div>
    </div>
  );
};

export const AndroidBuildStatus: React.FC = () => {
  const [data, setData] = useState<BuildStatusResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchStatus = () => {
    setLoading(true);
    setError(null);
    fetch('/api/status')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json() as Promise<BuildStatusResponse>;
      })
      .then((json) => {
        setData(json);
        setLoading(false);
      })
      .catch(() => {
        setError('Build status is unavailable. Check that the local server is running.');
        setLoading(false);
      });
  };

  useEffect(() => {
    fetchStatus();
  }, []);

  return (
    <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-6 md:p-8 space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <ShieldCheck className="w-5 h-5 text-emerald-400" />
            {data?.app ?? 'Android Artifacts'}
          </h2>
          <p className="text-slate-400 text-sm mt-0.5">
            {data
              ? `${data.platform}${data.version ? ` • version ${data.version}` : ''}`
              : 'Artifact information is supplied by /api/status.'}
          </p>
        </div>

        <button
          onClick={fetchStatus}
          disabled={loading}
          className="flex items-center gap-2 px-3 py-1.5 text-xs font-semibold rounded-lg bg-slate-950 border border-slate-800 text-slate-300 hover:text-white transition disabled:opacity-50"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          Refresh Status
        </button>
      </div>

      {error && (
        <p className="text-xs text-rose-300 bg-rose-950/40 border border-rose-500/30 rounded-lg px-3 py-2">
          {error}
        </p>
      )}

      {/* Artifact Downloads Grid — every size, name and timestamp comes from /api/status */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <ArtifactCard
          label="Debug APK"
          artifact={data?.artifacts?.debugApk}
          accent="emerald"
          note="Debug-signed APK from the current local build."
        />
        <ArtifactCard
          label="Release APK"
          artifact={data?.artifacts?.releaseApk}
          accent="indigo"
          note="Release APK, only when produced by a signed build."
        />
      </div>
    </div>
  );
};
