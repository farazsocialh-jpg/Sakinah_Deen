# Bundled resources: icons, fonts, Adhan audio, and offline seed data

Everything here ships inside the APK so first launch works with no network.

## Launcher icon & splash

| File | Purpose |
| --- | --- |
| `res/drawable/ic_launcher_background.xml` | Deep-emerald gradient layer |
| `res/drawable/ic_launcher_foreground.xml` | Rub el Hizb star + crescent, inside the 66dp safe zone |
| `res/drawable/ic_launcher_monochrome.xml` | Android 13+ themed-icon silhouette |
| `res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive icon wiring (background/foreground/monochrome) |
| `res/mipmap-anydpi-v26/ic_launcher_round.xml` | Round variant |
| `res/values/themes.xml` | `Theme.Sakinah` + `Theme.Sakinah.Splash` (AndroidX SplashScreen) |

`minSdk` is 29, so the `-v26` adaptive icon is sufficient; no PNG density buckets are needed.

## Fonts (`res/font`)

| Asset | Source | License |
| --- | --- | --- |
| `uthmanic_hafs.ttf` | KFGQPC HAFS Uthmanic Script via `thetruetruth/quran-data-kfgqpc` | KFGQPC terms (free to distribute with Quran text) |
| `amiri_regular.ttf`, `amiri_bold.ttf` | Google Fonts — Amiri | SIL Open Font License 1.1 |

Wired in `ui/theme/Type.kt` as `UthmanicFontFamily`, `AmiriFontFamily`, `QuranTextStyle`,
and `ArabicQuoteTextStyle`. Use `QuranTextStyle` for ayah text so diacritics and waqf
marks render correctly.

## Audio (`res/raw`)

| Asset | Content | Source |
| --- | --- | --- |
| `adhan_makkah.ogg` | Standard Makkah Dhuhr Adhan (used for Dhuhr/Asr/Maghrib/Isha) | [archive.org — 4-9-13 Makkah Duhur Adhan](https://archive.org/download/4-9-13MakkahDuhurAdhanSheikhYunusKhoja/) |
| `adhan_fajr.ogg` | Makkah Fajr Adhan (includes *As-salatu khayrun min an-nawm*) | [archive.org — Makkah Adhan Al-Fajr](https://archive.org/download/MakkahAdhanAl-fajr5-9-13SheikhYunusKhoja/) |
| `dua_al_safar.mp3` | Recitation of Quran 43:13–14, the verse behind Dua al-Safar | [cdn.islamic.network](https://cdn.islamic.network/quran/audio/128/ar.alafasy/4338.mp3) (Mishary al-Afasy) |

> A forced refresh (`-PforceDownloadAssets=true`) re-fetches `dua_al_safar.mp3` as the
> single recitation of 43:13, not the committed 43:13+43:14 concatenation. Rebuild the
> two-ayah file by hand if you need the longer version.

> **Check before publishing to Play.** The two archive.org recordings do not declare a
> licence in their item metadata. They are bundled here as working placeholders. For a
> store release either confirm redistribution rights or replace them with your own
> recordings — put the new files at the same paths, or set `adhanSoundUri` /
> `duaSafarSoundUri` in `SakinahPreferences` at runtime.

`NotificationChannels.adhanSoundUri()` binds `R.raw.adhan_makkah` to the high-priority
Salah channel, so the channel itself plays the Adhan even if the app process is dead.
`AdhanPlaybackService` picks `R.raw.adhan_fajr` when `PrayerType.FAJR` fires and
`R.raw.dua_al_safar` for the Bluetooth travel prompt.

## Seed data (`assets`)

| Asset | Contents | Provenance |
| --- | --- | --- |
| `seed_quran_full.json` | The complete mushaf: 114 surahs / 6,236 ayahs, Uthmani text + Saheeh International. Version 1, 2,509,358 bytes, SHA-256 `cc26751224af1651f7878b26c40f9fb74fef3d765553428aef7043e12a8a8c8c` (pinned in `QuranOfflinePack`) | Quran.com API v4 (`quran/verses/uthmani`, `quran/translations/20`) |
| `seed_quran_surahs.json` | 114 surah metadata records (names, meaning, revelation place, verse count) | Quran.com API v4 (`/chapters`) |
| `seed_hadith_nawawi.json` | All 42 hadith of Al-Arba'in an-Nawawiyyah with Arabic and English. The `grade` and `gradingScholar` fields ship empty: no scholarly grading has been added, and the UI only shows a grade when one is present. | fawazahmed0/hadith-api `eng-nawawi` + `ara-nawawi` |
| `seed_hisn_muslim.json` | 39 adhkar across 10 daily categories (morning/evening, after salah, before sleeping, waking, travel, leaving/entering the home, eating, distress, illness, masjid) with Arabic, transliteration, translation, reference, grading, repeat count and variant note. `reviewStatus` is `pending_scholarly_review`. | Hisn al-Muslim (al-Qahtani) numbering; Quranic passages cross-checked against the Quran.com Uthmani text |

These were generated from the live APIs rather than typed by hand. `DatabaseSeeder`
parses them inside a `RoomDatabase.Callback.onCreate` and upserts into the encrypted
database, so the reader and hadith screens are populated on the very first launch.

The Quran pack is checksum- and structure-verified before it is written: `QuranOfflinePack`
checks the pinned SHA-256, then validates 114 ordered surahs, per-surah ayah counts sum to
6,236, sequential ayah numbering, Arabic letters, tashkeel presence and non-empty
translations. `QuranOfflinePackTest` runs the same audit against the shipped asset, and
`OfflineSeedingTest` proves the whole mushaf lands in Room from the asset alone.
Everything is a REPLACE upsert, so re-running the seeder is safe.

To regenerate after changing scope, re-run the generator script described in the commit
that added these files, or extend `syncSurah` / `syncHadithBook` in `IslamicDataRepository`
at runtime instead.

## Adhkar provenance and review status

`seed_hisn_muslim.json` carries a `version`, `source`, `note` and `reviewStatus` field. Every
entry must have non-blank Arabic, a translation, a locator-bearing reference (`Sahih Muslim
597`, `Quran 2:255`), a grading and a repeat count of at least one; `AdhkarSeedTest` fails the
build otherwise. `repeatCount` is clamped to >= 1 when rows are written.

`reviewStatus` is currently `pending_scholarly_review`: the wording follows the printed Hisn
al-Muslim editions and the cited narrations, but the posture descriptions in the Visual Salah
Guide have **not** been signed off by a qualified scholar. The Adhkar tab and the step cards
say so, and the TTS button carries an explicit "learn Tajweed from a qualified teacher" note.

Editing the asset is enough to ship a corrected edition: `DatabaseSeeder.adhkarSeedHash`
(FNV-1a over the asset text) is stored in preferences and a changed hash replaces the whole
adhkar table in one transaction on the next launch, so no Room version bump is needed for
content edits. Room schema **v11** (`MIGRATION_10_11`) added the grading, repeat count,
transliteration and variant-note columns that this provenance needs.

## Build-time refresh tasks

`downloadFontAssets` and `downloadAudioAssets` run before `preBuild`:

- they **no-op** when the committed asset already exists and meets a minimum size;
- they **never fail the build** on network errors (the committed asset stays);
- pass `-PforceDownloadAssets=true` to re-fetch everything:

```bash
./gradlew :app:assembleDebug -PforceDownloadAssets=true
```

This keeps GitHub Actions hermetic by default while still letting you refresh assets
without hand-uploading binaries.

## Localization

`res/values/strings.xml` (English), `values-ar` (Arabic, RTL), and `values-ur` (Urdu)
cover app chrome, notification channels, Adhan/Safar notifications, and common actions.
The large Compose screen bodies (Salah, Quran, Hadith, Wealth, AI chat) still contain
inline English copy — see the follow-up list rather than assuming they are localized.
