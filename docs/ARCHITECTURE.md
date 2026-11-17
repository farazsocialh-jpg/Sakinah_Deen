# ImanCore / Sakinah Deen — Architecture

Android-first Islamic companion (package `com.sakinah.deen`) with a Kotlin Multiplatform
`shared/` module for astronomy, models and settings that are also used by the iOS target.

## Modules

```
app/     Android UI (Compose), Room, alarms, services, receivers
shared/  KMP domain: prayer-time engine, Qibla math, shared models/settings
ios/     SwiftUI shell consuming `shared`
worker/  Cloudflare Worker proxying AI Scholar requests
```

## Layers in `app/`

| Layer | Location | Responsibility |
| --- | --- | --- |
| UI | `ui/screens`, `ui/components` | Stateless-ish Compose screens; all state comes from a ViewModel or prefs flow |
| Navigation | `ui/navigation/RootNavHost.kt` | `SakinahScreen` enum owns each screen's chrome (`showTopBar` / `showBottomBar`) and a `Saver`-backed back stack |
| ViewModels | `ui/viewmodel` | Intents in, immutable `*UiState` out (`StateFlow`); no Android framework types where avoidable |
| Domain | `domain/` | Pure logic: `SalahRefreshPolicy`, `LocationValidator`, `SafarPolicy`, `TravelStatusManager` |
| Data | `data/` | Room DAOs/entities, repositories, `SakinahPreferences`, AlAdhan/Quran/Hadith clients |
| Services | `service/`, `receiver/`, `work/` | Azaan playback, alarm scheduling, boot/Bluetooth receivers, periodic work |

## Prayer-time pipeline

1. `SalahViewModel` resolves the authoritative method for the country, the madhhab, the
   high-latitude rule and the manual offsets.
2. `PrayerTimeRepository` first consults the cached monthly timetable. Its cache identity is
   `lat|lon|method|school|month|latitudeRule`, so changing the rule, the school or the
   location is a different cache entry rather than a stale reuse.
3. A miss fetches the AlAdhan month endpoint; failure falls back to
   `PrayerCalculationEngine` in `shared/`.
4. The same inputs (rule + offsets) are passed to `PrayerAlarmScheduler`, so the Azaan cannot
   disagree with the screen.

High-latitude handling applies only when the sun never reaches the Fajr/Isha twilight angle;
interval-based Isha methods are untouched.

## Persistence

- One encrypted Room database (`SakinahEncryptedDatabase`, SQLCipher, schema v11) holds
  prayer logs, Qadha, Hifz mistakes, Quran/Hadith/Adhkar caches, Sadaqah and timetables.
- If the keystore passphrase can no longer open the file, the original is preserved under
  `files/recovery/` and surfaced on the Privacy screen — the app never deletes user records
  to recover from a decryption failure.
- `SakinahPreferences` (SharedPreferences) holds location, calculation settings and simple
  toggles; every location write is validated (`LocationValidator`) and invalid values are
  rejected rather than stored.

## Security posture

- No API key ships in the APK; AI Scholar traffic goes through `worker/`.
- `android:allowBackup="false"`, so a keystore-bound database is never restored onto a device
  that cannot decrypt it.
- The exported Bluetooth receiver validates its action and requires `BLUETOOTH_CONNECT`
  before reading a device; WebView bridge diagnostics are debug-only.
- Logs never include tokens, coordinates or chat content.

## Decisions

See [ADRs](adr/README.md) for the reasoning behind the prayer cache identity, the
high-latitude fallback, the database recovery path and declarative navigation chrome.
