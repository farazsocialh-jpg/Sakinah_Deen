# KMP parity matrix

What is shared, what each platform does itself, and which gaps are still open. Android is the
reference implementation; iOS builds the same `shared/` module through `AccountBridge`.

| Area | Android | iOS | Parity |
| --- | --- | --- | --- |
| Prayer-time engine | `shared/` `PrayerCalculationEngineImpl` | same | ✅ identical |
| Qibla bearing | `shared/` | same | ✅ |
| Auth + accounts | `PersistentAuthRepository` over `Settings` | same class via `AccountBridge` | ✅ logic, ⚠️ storage (below) |
| Account storage | EncryptedSharedPreferences / `Settings` | `NSUserDefaults` via `Settings()` | ⚠️ gap |
| Session restore | `PersistentAuthRepository.loadFromPrefs` | same | ✅ |
| Password hashing | PBKDF2-HMAC-SHA256, per-account salt | same | ✅ |
| Prayer logs / Qadha / Hifz | encrypted Room | not implemented | ⚠️ iOS has no local ledger yet |
| AI Scholar | OkHttp through `worker/` | Ktor Darwin through `worker/` | ⚠️ timeouts configured per client |
| Offline Quran/Hadith | Room + bundled assets | bundled read-only | ⚠️ no per-surah download on iOS |
| Notifications | AlarmManager exact alarms | local notifications | 🚫 platform-specific by design |

## Known gaps

1. **iOS account storage is `NSUserDefaults`.** It holds salted PBKDF2 hashes rather than raw
   passwords, but hashes and the signed-in user are still in an unencrypted plist. The fix is a
   `Settings` implementation backed by Keychain (`SecItemAdd` / `SecItemCopyMatching` /
   `SecItemDelete`) and a one-time migration from the plist. This cannot be compiled or tested
   from this environment (no macOS/Xcode), so it stays open rather than shipping untested
   CryptoKit/Keychain code.
2. **HTTP timeouts.** Android uses OkHttp with explicit timeouts; iOS relies on Ktor's Darwin
   engine defaults. A shared timeout constant should be applied to both clients so a stalled
   Scholar request fails at the same point on each platform.
3. **Local records on iOS.** Prayer logs, Qadha, Hifz and Zakat are Android-only. Adding them
   means either a SQLDelight schema in `shared/` or a separate iOS store — a design decision
   that should be made before writing code.

## Rules that keep parity

- Anything in `shared/` must compile for `androidTarget` and `iosArm64`/`iosX64`; run
  `:shared:testAndroidHostTest` for the common tests.
- Platform-only behaviour (alarms, database encryption, WebView) belongs in `app/` or
  `ios/Sakinah/`, never in `shared/`.
- A rule change that affects prayer times is only complete when it reaches both the screen and
  the alarm scheduler on Android, and the iOS caller of the shared engine.
