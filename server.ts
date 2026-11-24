import express from 'express';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = Number(process.env.PORT) || 3000;
const isProduction = process.env.NODE_ENV === 'production';

app.use(express.json());

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
    app: 'ImanCore (com.sakinah.deen)',
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
app.get('/api/apk/debug', (_req, res) => {
  const localPath = path.resolve(__dirname, 'app/build/outputs/apk/debug/app-debug.apk');
  const backupPath = path.resolve(__dirname, '.build-outputs/app-debug.apk');
  const target = fs.existsSync(localPath) ? localPath : backupPath;

  if (fs.existsSync(target)) {
    res.setHeader('Content-Type', 'application/vnd.android.package-archive');
    res.setHeader('Content-Disposition', 'attachment; filename="imancore-debug.apk"');
    fs.createReadStream(target).pipe(res);
  } else {
    res.status(404).json({ error: 'Debug APK not built yet. Run ./gradlew :app:assembleDebug.' });
  }
});

// API: Download Release APK
app.get('/api/apk/release', (_req, res) => {
  const localPath = path.resolve(__dirname, 'app/build/outputs/apk/release/app-release.apk');
  const backupPath = path.resolve(__dirname, '.build-outputs/app-release.apk');
  const target = fs.existsSync(localPath) ? localPath : backupPath;

  if (fs.existsSync(target)) {
    res.setHeader('Content-Type', 'application/vnd.android.package-archive');
    res.setHeader('Content-Disposition', 'attachment; filename="imancore-release.apk"');
    fs.createReadStream(target).pipe(res);
  } else {
    res.status(404).json({ error: 'Release APK not found. Run ./gradlew :app:assembleRelease.' });
  }
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

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`[ImanCore] Dev server running on http://0.0.0.0:${PORT}`);
  });
}

startServer().catch((err) => {
  console.error('Failed to start server:', err);
  process.exit(1);
});
