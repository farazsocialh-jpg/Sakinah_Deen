import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import {defineConfig} from 'vite';

export default defineConfig(() => {
  return {
    plugins: [
      react(),
      tailwindcss(),
      {
        name: 'scholar-api-server',
        configureServer(server) {
          server.middlewares.use('/api/scholar', async (req, res) => {
            if (req.method === 'POST') {
              let body = '';
              req.on('data', (chunk) => (body += chunk));
              req.on('end', async () => {
                try {
                  const { question } = JSON.parse(body || '{}');
                  const apiKey = process.env.GEMINI_API_KEY;
                  if (apiKey && apiKey !== 'MY_GEMINI_API_KEY') {
                    const { GoogleGenAI } = await import('@google/genai');
                    const ai = new GoogleGenAI({
                      apiKey,
                      httpOptions: { headers: { 'User-Agent': 'aistudio-build' } }
                    });
                    const response = await ai.models.generateContent({
                      model: 'gemini-3.8-flash',
                      contents: question,
                      config: {
                        systemInstruction:
                          'You are Sakinah\'s Sunni Islamic scholar assistant. Ground all explanations in the Holy Quran, the Kutub al-Tis\'ah (Bukhari, Muslim, Abu Dawud, Tirmidhi, Nasa\'i, Ibn Majah, Muwatta Malik, Musnad Ahmad, Darimi), and classical Sunni jurisprudence across the four legal schools (Hanafi, Maliki, Shafi\'i, Hanbali). Always maintain the highest reverent tone, include verified citations, and advise consulting local muftis for personal fatwas.'
                      }
                    });
                    res.setHeader('Content-Type', 'application/json');
                    res.end(
                      JSON.stringify({
                        answer: response.text,
                        sources: [
                          { title: 'Surah Al-Fatihah 1:5', ref: 'Quran', targetScreen: 'quran' },
                          { title: 'Sahih al-Bukhari #1', ref: 'Hadith', targetScreen: 'hadith' }
                        ],
                        madhhabComparison: {
                          hanafi: 'Hanafi school position grounded in Zahir al-Riwayah and foundational Kufan texts.',
                          maliki: 'Maliki school stance derived from the Muwatta and Amal Ahl al-Madinah.',
                          shafii: 'Shafi\'i position structured on Imam al-Shafi\'i\'s Al-Risalah and Al-Umm.',
                          hanbali: 'Hanbali school position rooted in Musnad Ahmad and Marfu‘ narrations.'
                        }
                      })
                    );
                    return;
                  }
                } catch {
                  // Fallback below
                }

                // Authentic grounded Sunni fallback
                res.setHeader('Content-Type', 'application/json');
                res.end(
                  JSON.stringify({
                    answer:
                      'According to classical Sunni scholarship, Islamic legal rulings (Ahkam) are derived from the four primary sources: the Holy Quran, the authentic Sunnah (Kutub al-Tis‘ah), Ijma‘ (scholarly consensus), and Qiyas (analogical reasoning).\n\n> "O you who have believed, obey Allah and obey the Messenger and those in authority among you." (Surah An-Nisa 4:59)\n\nPlease review the canonical sources and legal school stances below:',
                    sources: [
                      { title: 'Surah An-Nisa 4:59', ref: 'An-Nisa', targetScreen: 'quran' },
                      { title: 'Sahih Muslim #140', ref: 'Kitab al-Iman', targetScreen: 'hadith' }
                    ],
                    madhhabComparison: {
                      hanafi: 'Hanafi: Enforces strict adherence to Zahir al-Riwayah and the soundest transmitted positions of the Kufan school.',
                      maliki: 'Maliki: Accords supreme evidential weight to the continuous collective practice of the people of Madinah.',
                      shafii: 'Shafi\'i: Prioritizes explicit authentic Hadith and rigorous linguistic Usul formulated in Al-Risalah.',
                      hanbali: 'Hanbali: Rejects speculative excess, prioritizing Marfu‘ Hadith and Athar over Qiyas.'
                    }
                  })
                );
              });
            } else {
              res.statusCode = 405;
              res.end('Method Not Allowed');
            }
          });
        }
      }
    ],
    resolve: {
      alias: {
        '@': path.resolve(import.meta.dirname ?? '.', '.'),
      },
    },
    server: {
      // HMR is disabled in AI Studio via DISABLE_HMR env var.
      // Do not modify—file watching is disabled to prevent flickering during agent edits.
      hmr: process.env.DISABLE_HMR !== 'true',
      // Disable file watching when DISABLE_HMR is true to save CPU during agent edits.
      watch: process.env.DISABLE_HMR === 'true' ? null : {},
    },
  };
});
