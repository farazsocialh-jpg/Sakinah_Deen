import express, { type Request, type Response } from 'express';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

function resolvePort(): number {
  const raw = process.env.PORT?.trim();
  if (!raw) return 3000;
  const value = Number(raw);
  if (!Number.isInteger(value) || value < 1 || value > 65535) {
    console.error(`[ImanCore] Invalid PORT="${raw}"; expected an integer from 1 to 65535.`);
    process.exit(1);
  }
  return value;
}

const PORT = resolvePort();
const HOST = process.env.HOST?.trim() || '0.0.0.0';
const isProduction = process.env.NODE_ENV === 'production';

// APK downloads expose unsigned/debug build output. By default only local/private clients may
// fetch them; set APK_DOWNLOADS_PUBLIC=1 for an intentional public artifact server, or set
// APK_DOWNLOAD_TOKEN and send it in the x-apk-token header for authenticated remote access.
const APK_ACCESS_TOKEN = process.env.APK_DOWNLOAD_TOKEN?.trim() || '';
const APK_PUBLIC = process.env.APK_DOWNLOADS_PUBLIC === '1';

const app = express();

app.use((_req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('Permissions-Policy', 'camera=(), microphone=(), payment=()');
  next();
});

app.use(express.json());

function isPrivateAddress(address: string): boolean {
  const ip = address.startsWith('::ffff:') ? address.slice(7) : address;
  return (
    ip === '127.0.0.1' ||
    ip === '::1' ||
    ip.startsWith('10.') ||
    ip.startsWith('192.168.') ||
    ip.startsWith('169.254.') ||
    /^172\.(1[6-9]|2\d|3[01])\./.test(ip)
  );
}

function canDownloadApk(req: Request): boolean {
  if (APK_ACCESS_TOKEN) {
    const provided = req.get('x-apk-token') ?? '';
    if (provided.length !== APK_ACCESS_TOKEN.length) return false;
    let diff = 0;
    for (let i = 0; i < provided.length; i += 1) {
      diff |= provided.charCodeAt(i) ^ APK_ACCESS_TOKEN.charCodeAt(i);
    }
    return diff === 0;
  }
  if (APK_PUBLIC) return true;
  return isPrivateAddress(req.ip || req.socket.remoteAddress || '');
}

function sendApk(res: Response, candidates: string[], filename: string, missingMessage: string) {
  const target = candidates.find((candidate) => fs.existsSync(candidate));
  if (!target) {
    res.status(404).json({ error: missingMessage });
    return;
  }

  res.setHeader('Content-Type', 'application/vnd.android.package-archive');
  res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
  res.setHeader('X-Content-Type-Options', 'nosniff');
  const stream = fs.createReadStream(target);
  stream.on('error', (err) => {
    console.error(`[ImanCore] Failed to stream ${filename}:`, err.message);
    if (!res.headersSent) {
      res.status(500).json({ error: 'Could not read the requested artifact.' });
    } else {
      res.destroy(err);
    }
  });
  res.on('close', () => stream.destroy());
  stream.pipe(res);
}

// API: Android Build Artifacts & System Status
app.get('/api/status', (_req, res) => {
  const outputsDir = path.resolve(__dirname, '.build-outputs');
  const appDebugApk = path.resolve(__dirname, 'app/build/outputs/apk/debug/app-debug.apk');
  const buildOutputsDebugApk = path.resolve(outputsDir, 'app-debug.apk');
  const buildOutputsReleaseApk = path.resolve(outputsDir, 'app-release.apk');
  const buildOutputsReleaseAab = path.resolve(outputsDir, 'app-release.aab');

  const getFileInfo = (filePath: string) => {
    try {
      if (fs.existsSync(filePath)) {
        const stats = fs.statSync(filePath);
        return {
          exists: true,
          sizeBytes: stats.size,
          sizeMb: (stats.size / (1024 * 1024)).toFixed(2),
          updatedAt: stats.mtime.toISOString(),
        };
      }
    } catch {
      // Ignore filesystem access errors
    }
    return { exists: false, sizeBytes: 0, sizeMb: '0', updatedAt: null };
  };

  const debugApkInfo = fs.existsSync(appDebugApk) ? getFileInfo(appDebugApk) : getFileInfo(buildOutputsDebugApk);
  const releaseApkInfo = getFileInfo(buildOutputsReleaseApk);
  const releaseAabInfo = getFileInfo(buildOutputsReleaseAab);

  res.json({
    app: 'ImanCore (com.imancore.app)',
    platform: 'Android & Kotlin Multiplatform',
    version: '1.0.0',
    serverStatus: 'running',
    port: PORT,
    timestamp: new Date().toISOString(),
    artifacts: {
      debugApk: {
        ...debugApkInfo,
        downloadUrl: '/api/apk/debug',
        filename: 'imancore-debug.apk',
      },
      releaseApk: {
        ...releaseApkInfo,
        downloadUrl: '/api/apk/release',
        filename: 'imancore-release.apk',
      },
      releaseAab: {
        ...releaseAabInfo,
        filename: 'imancore-release.aab',
      },
    },
    widgets: [
      { id: 'salah_tracker', name: 'Today\'s Salah Tracker', sizes: ['2x1', '4x1', '2x2', '4x2'] },
      { id: 'tasbih_counter', name: 'Tasbih 33-Count', sizes: ['2x1', '4x1', '2x2', '4x2'] },
      { id: 'daily_ayah', name: 'Ayah of the Day', sizes: ['2x1', '4x1', '2x2', '4x2'] },
      { id: 'ai_scholar', name: 'Ask ImanCore Scholar', sizes: ['2x1', '4x1', '2x2', '4x2'] },
      { id: 'hijri_date', name: 'Hijri Date & Sunnah Fast', sizes: ['2x1', '4x1', '2x2', '4x2'] },
      { id: 'qibla_compass', name: 'Qibla Direction', sizes: ['2x1', '4x1', '2x2', '4x2'] },
    ],
  });
});

// API: Download Debug APK
app.get('/api/apk/debug', (req, res) => {
  if (!canDownloadApk(req)) {
    res.status(403).json({ error: 'APK downloads are restricted to local/private clients.' });
    return;
  }
  sendApk(
    res,
    [
      path.resolve(__dirname, 'app/build/outputs/apk/debug/app-debug.apk'),
      path.resolve(__dirname, '.build-outputs/app-debug.apk'),
    ],
    'imancore-debug.apk',
    'Debug APK not built yet. Run ./gradlew :app:assembleDebug.',
  );
});

// API: Download Release APK
app.get('/api/apk/release', (req, res) => {
  if (!canDownloadApk(req)) {
    res.status(403).json({ error: 'APK downloads are restricted to local/private clients.' });
    return;
  }
  sendApk(
    res,
    [
      path.resolve(__dirname, 'app/build/outputs/apk/release/app-release.apk'),
      path.resolve(__dirname, '.build-outputs/app-release.apk'),
    ],
    'imancore-release.apk',
    'Release APK not found. Run ./gradlew :app:assembleRelease.',
  );
});

// Unknown API routes must answer as JSON, never with the SPA's HTML fallback.
app.use('/api', (_req, res) => {
  res.status(404).json({ error: 'Unknown API endpoint.' });
});

// Setup Vite middleware or static serving
async function startServer() {
  if (!isProduction) {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    app.use(express.static(path.resolve(__dirname, 'dist')));
    app.get('*', (_req, res) => {
      res.sendFile(path.resolve(__dirname, 'dist/index.html'));
    });
  }

  app.listen(PORT, HOST, () => {
    console.log(`[ImanCore] Server running on http://${HOST}:${PORT}`);
  });
}

startServer().catch((err) => {
  console.error('Failed to start server:', err);
  process.exit(1);
});
