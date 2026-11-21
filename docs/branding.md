# Naming: ImanCore vs Sakinah

The product is **ImanCore**. Several `Sakinah` / `sakinah_deen` identifiers remain in the
codebase on purpose. This page is the source of truth for which name belongs where, so a
rename never breaks installed data, signing or the backend.

## The two names

| Name | Role |
| --- | --- |
| **ImanCore** | User-facing product name: app label, store listing, OAuth consent screen, document titles, iOS navigation title. |
| **Sakinah** | Legacy codename from before the rename. Retained as an internal identity in places where changing it would break something a user or Google already holds. |

## Where each name lives

| Location | Current value | Rule |
| --- | --- | --- |
| `app/src/main/res/values/strings.xml` app label | `ImanCore` | Product name. Change freely. |
| `ios/Sakinah/Info.plist` `CFBundleDisplayName` / `CFBundleName` | `ImanCore` | Product name. The source directory name stays `Sakinah`. |
| iOS navigation title (`ContentView.swift`) | `ImanCore` | Product name. |
| Android `applicationId`, Play package, OAuth package | `com.imancore.app` | Public identifier. Change only with a new Play listing **and** a new Android OAuth client. |
| Gradle `namespace`, Kotlin package, `R` class | `com.sakinah.deen` | Internal. Never rename: it is only a compile-time identity, but it is referenced everywhere. |
| iOS target / scheme / source directory | `Sakinah` | Internal. Keep; CI paths refer to it. |
| iOS Kotlin framework | `Shared` | Internal. Unrelated to branding. |
| Cloudflare Worker name + custom domain | `sakinahdeen`, `ai.insightsltd.com` | Deployed infrastructure. Changing the Worker name creates a *new* Worker, so the domain has to be re-attached and app builds repointed. Treat as a deliberate migration. |
| Upload keystore file and alias | `sakinah_deen_aqmirza` | **Never rename.** The alias is the signing identity Play and Google Sign-In trust; `KEY_ALIAS` in CI expects it. |
| Room database file, SharedPreferences names, notification channel ids, DataStore keys | `sakinah*` | **Never rename.** Existing installs would lose their data. |
| Repository / Gradle root project name | `ImanCore` (`settings.gradle.kts`) | Cosmetic. |
| `worker/package.json` name | `sakinah-ai-proxy` | Cosmetic, private package. |

## Rules of thumb

1. **User-visible text** says ImanCore. **Machine identifiers** may still say Sakinah.
2. Do not rename `applicationId`, the keystore alias, the Worker name, or any persisted
   storage key as part of a cosmetic cleanup — each one is a migration with data or
   store-listing consequences.
3. When a comment or doc refers to `Sakinah`, it is describing such a retained legacy
   identifier, not a second product.
4. The identifier split (`com.imancore.app` outside, `com.sakinah.deen` inside) and the OAuth
   client setup are documented in [release-signing.md](release-signing.md).
