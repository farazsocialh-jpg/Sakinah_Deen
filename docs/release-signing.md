# Release signing & Play-ready builds

ImanCore builds a signed **AAB** (Android App Bundle) in CI from repository secrets.
No keystore, password, or key is ever committed to this repository.

## 0. Package identity (read before touching Google Cloud)

The app displays as **ImanCore**, and the identifier that Google Play and the Android OAuth
client use is `com.imancore.app`. Only the shipped identifier changed; internal identities
were deliberately kept so installed data and signing keep working.

| What | Value | Notes |
| --- | --- | --- |
| `applicationId` / Play package / OAuth package name | `com.imancore.app` | New; must match the Android OAuth client exactly |
| Gradle `namespace`, Kotlin package, `R` class | `com.sakinah.deen` | Unchanged internal namespace |
| Upload keystore file and alias | `keystore/sakinah_deen_aqmirza.jks`, alias `sakinah_deen_aqmirza` | Unchanged. The old-looking name is intentional — never regenerate it for the rename |
| Room database name, SharedPreferences keys, notification channel IDs | unchanged | Keeps existing installs' data readable |
| App label | `ImanCore` | From `app/src/main/res/values/strings.xml` |

Because `applicationId` changed, this is a **new Play listing**: the Play Console app and the
Android OAuth client are both created against `com.imancore.app`, and an already-installed
`com.sakinah.deen` build will not update into it in place.

## 1. Create the upload keystore (once)

Run this on your own machine and keep the file plus passwords somewhere safe.
Losing the upload key means you must request a key reset from Google Play.

The example below keeps the original filename, alias and passwords
(`sakinah_deen_aqmirza`). They are deliberately **not** renamed with the app: the signing
identity is what Play and Google Sign-In trust, and the alias is what `KEY_ALIAS` in CI
expects. Only `applicationId` changed.

```bash
mkdir -p keystore
keytool -genkeypair -v \
  -keystore keystore/sakinah_deen_aqmirza.jks \
  -alias sakinah_deen_aqmirza \
  -storepass sakinah_deen_aqmirza \
  -keypass sakinah_deen_aqmirza \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storetype JKS
```

Recommended: use an **upload key** (Play App Signing holds the real app signing key).
`-validity 10000` comfortably exceeds Play's 25-year requirement.

## 2. Add the repository secrets

GitHub → your repository → **Settings → Secrets and variables → Actions → Repository secrets → New repository secret**.
Create exactly these (under **Repository secrets**, not Environment secrets):

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | Base64 of `keystore/sakinah_deen_aqmirza.jks` (see below) |
| `KEYSTORE_PASSWORD` | `sakinah_deen_aqmirza` |
| `KEY_ALIAS` | `sakinah_deen_aqmirza` |
| `KEY_PASSWORD` | `sakinah_deen_aqmirza` |
| `AI_PROXY_URL` | Optional — backend proxy for the AI Scholar (raw Gemini key stays server-side) |
| `NISAB_RATE_URL` | Optional — metal-price endpoint for live nisab rates |

Encode the keystore:

```bash
# macOS
base64 -i keystore/sakinah_deen_aqmirza.jks | tr -d '\n'
# Linux
base64 -w0 keystore/sakinah_deen_aqmirza.jks
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore\sakinah_deen_aqmirza.jks"))
```

Or with the GitHub CLI:

```bash
base64 -w0 keystore/sakinah_deen_aqmirza.jks > keystore/sakinah_deen_aqmirza.b64
gh secret set KEYSTORE_BASE64 < keystore/sakinah_deen_aqmirza.b64
gh secret set KEYSTORE_PASSWORD --body "sakinah_deen_aqmirza"
gh secret set KEY_ALIAS --body "sakinah_deen_aqmirza"
gh secret set KEY_PASSWORD --body "sakinah_deen_aqmirza"
```

> A leaked `KEYSTORE_BASE64` plus passwords lets someone publish as you. Treat them
> as production credentials, and rotate with a Play upload-key reset if exposed.

## 3. Run the release build

The workflows are `.github/workflows/release.yml` (AAB + APK) and `.github/workflows/signed-release-apk.yml` (signed APK), and support two triggers:

- **Tag push** — build a versioned release:
  ```bash
  git tag v1.2.0
  git push origin v1.2.0
  ```
- **Manual** — Actions → *Release* (or *Signed Release APK*) → *Run workflow*. Optionally pass a version name;
  otherwise the tag (minus `v`) or `1.0.0` is used. `versionCode` comes from the
  workflow run number, so it always increases.

The job fails immediately with a clear `::error::` annotation if any signing secret
is missing, then verifies the produced AAB and APK signatures with `jarsigner` and
`apksigner` before uploading.

Artifacts: `sakinah-release-aab-<version>`, `sakinah-release-apk-<version>`, and
`sakinah-mapping-<version>` (R8 `mapping.txt`; upload it to Play for readable
crash reports).

## 4. Build a signed release locally (optional)

`app/build.gradle.kts` reads credentials from environment variables or `keystore.properties` (git-ignored).
Environment variables win when both are present.

```bash
cp keystore.properties.example keystore.properties   # already prefilled with sakinah_deen_aqmirza
export VERSION_NAME=1.2.0 VERSION_CODE=42
./gradlew :app:bundleRelease :app:assembleRelease
```

Or entirely from the environment:

```bash
export KEYSTORE_PATH="$PWD/keystore/sakinah_deen_aqmirza.jks"
export KEYSTORE_PASSWORD=sakinah_deen_aqmirza
export KEY_ALIAS=sakinah_deen_aqmirza
export KEY_PASSWORD=sakinah_deen_aqmirza
./gradlew :app:bundleRelease :app:assembleRelease
```

Outputs:
- AAB (Play Store): `app/build/outputs/bundle/release/app-release.aab`
- APK (Direct install): `app/build/outputs/apk/release/app-release.apk`

`keystore.properties` is git-ignored. Environment variables win over the file when both
are set, so CI can keep secrets in the environment while developers keep them in the file.

If no keystore is configured, `assembleRelease` and `bundleRelease` fail immediately with:

```text
Release signing config is missing: provide keystore.properties or
KEYSTORE_PATH/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD.
```

This is deliberate: the build never silently falls back to a debug key or emits an
unsigned artifact that Play would reject.

### CI: materialise `keystore.properties` from secrets

The release workflows export the four variables directly, which already satisfies the
build. A job that prefers the properties file — for example to reuse the same command
locally — can write it next to the Gradle root before building:

```bash
cat > keystore.properties <<EOF
storeFile=$RUNNER_TEMP/sakinah_release.jks
storePassword=$KEYSTORE_PASSWORD
keyAlias=$KEY_ALIAS
keyPassword=$KEY_PASSWORD
EOF
./gradlew :app:assembleRelease
```

`storeFile` is resolved relative to the repository root, so either a repository-relative
path (`keystore/sakinah_deen_aqmirza.jks`) or an absolute runner path works. The file must
stay untracked; `.gitignore` already excludes `keystore.properties`, `*.jks` and `keystore/`.

## 5. Upload to Play

1. Play Console → your app → **Testing → Internal testing → Create new release**.
2. Upload the `.aab` from the workflow artifact.
3. Paste the version's `mapping.txt` under **App bundle explorer → Downloads** for
   deobfuscated stack traces.

The Play Console app must be created for the package `com.imancore.app`. Play derives the
package from the first uploaded bundle, so check **App information** shows `com.imancore.app`
before promoting beyond internal testing. If you still have an app registered under
`com.sakinah.deen`, it stays separate and needs its own listing (or is simply retired).

## 6. Google Sign-In (Credential Manager)

The app signs in with Google through Android's Credential Manager and a Google ID token —
no Firebase project or billing is involved. Two OAuth clients are configured once in
[Google Cloud Console](https://console.cloud.google.com/apis/credentials). Use the same
Google Cloud project as before; the rename does not require a new project.

### 6.1 Android client (the one that changed)

*Create credentials → OAuth client ID → Android*.

- **Package name:** `com.imancore.app` — this is the `applicationId`, not the Gradle
  `namespace`. A client registered against `com.sakinah.deen` will not authenticate the new
  build.
- **SHA-1 certificate fingerprint:** one entry per key that signs a build users run. Add them
  as separate Android OAuth clients (the console allows multiple clients in the same project)
  or as additional fingerprints on a single client where the console offers it:

  | Build | Keystore | How to read the SHA-1 |
  | --- | --- | --- |
  | Local debug / debug APK | `~/.android/debug.keystore`, alias `androiddebugkey`, password `android` | `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android` |
  | Locally built release / CI-signed APK or AAB | `keystore/sakinah_deen_aqmirza.jks`, alias `sakinah_deen_aqmirza` | `keytool -list -v -keystore keystore/sakinah_deen_aqmirza.jks -alias sakinah_deen_aqmirza` |
  | Installs delivered by Play (Play App Signing) | Google-held key | Play Console → **Test and release → Setup → App signing** → SHA-1 of the *app signing key* |

  Paste the fingerprint exactly as `keytool` prints it under `SHA1:` — uppercase hex with
  colons, no spaces. Missing the fingerprint of the key Play re-signs with is the usual cause
  of "sign-in works from Android Studio but not from the Play build".

To confirm what a local build will present, run `./gradlew :app:signingReport`: it lists each
variant's keystore alias and `SHA-1`/`SHA-256` so you can compare them with the console.
The package name itself lives in `app/build.gradle.kts` under `defaultConfig.applicationId`; to
check a built APK, run `"$ANDROID_HOME"/build-tools/*/aapt2 dump badging app-release.apk | head -1`
and confirm it reports `package: name='com.imancore.app'`, which is the id the Android OAuth
client must be registered against.

> The rename makes the old Android client for `com.sakinah.deen` unused. Leave it while old
> test builds still exist, then delete it in **Credentials**; deleting it never affects the
> new client or the Web client.

### 6.2 Web application client (unchanged)

*Create credentials → OAuth client ID → Web application*. No redirect URI is needed for the
ID-token flow. If a Web client already exists for the project, keep it — its **Client ID** is
reused as-is and is the value the app passes to Credential Manager as `serverClientId`.

Supply that Client ID — never hardcode it — in one of these places:

```properties
# local.properties (git-ignored)
GOOGLE_WEB_CLIENT_ID=1234567890-abcdef.apps.googleusercontent.com
```

```bash
# or the environment
export GOOGLE_WEB_CLIENT_ID="1234567890-abcdef.apps.googleusercontent.com"
```

In CI, the same value is the `GOOGLE_WEB_CLIENT_ID` repository secret. The workflows warn and
build with sign-in disabled when it is absent; they never fall back to a built-in id, and the
Android OAuth client above is what makes that id usable from `com.imancore.app`.

Cosmetic but user-visible: set **OAuth consent screen → App name** to `ImanCore`, so the
account chooser no longer shows the old name. No client id changes when you rename it.

The build reads `local.properties` first, then the environment (and `keystore.properties`
inside CI). If the value is blank the app still builds; tapping **Continue with Google**
shows an inline "not configured for this build" message instead of crashing.

Google returns identity only (`uniqueId`, email, name, photo). Gender, date of birth, city
and country are collected in the profile-completion step after the first sign-in; city and
country are pre-filled from the device's last known prayer location via `Geocoder`.

### 6.3 Verify on a device

1. Install the debug or signed release build, then confirm the installed package is the new
   id: `adb shell pm list packages | grep imancore` should print
   `package:com.imancore.app`.
2. Sign in with **Continue with Google**. A `DEVELOPER_ERROR` / `10:` from Credential Manager
   means the package name or SHA-1 does not match an Android OAuth client; a
   `401 invalid_client` means the Web client id is wrong or belongs to another project.
3. For a signed release APK, cross-check the certificate:
   `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` should show
   the same SHA-1 you registered.
4. If you are testing an internal-testing build from Play, repeat step 2 on that install — it
   is signed with Play's app signing key, whose fingerprint is registered separately.
