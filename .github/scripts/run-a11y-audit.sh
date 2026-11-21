#!/usr/bin/env bash
# Device leg of the audit: install the debug APK and measure rendered accessibility.
# The emulator action runs this file as the emulator script.
set -uo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
PACKAGE="com.imancore.app"
ACTIVITY="$PACKAGE/com.sakinah.deen.MainActivity"
OUT="a11y-report"

mkdir -p "$OUT"

if [ ! -f "$APK" ]; then
  echo "::error::APK not found at $APK"
  exit 1
fi

adb wait-for-device
adb shell input keyevent 82 >/dev/null 2>&1 || true
adb install -r "$APK" | tail -2

# Skip permission dialogs so the audit reaches the UI instead of the system sheet.
adb shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1 || true
adb shell pm grant "$PACKAGE" android.permission.ACCESS_COARSE_LOCATION >/dev/null 2>&1 || true
adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true

adb shell am start -W -n "$ACTIVITY" | tail -5
sleep 5

# Accept a first-run dialog if one is still up (best effort, never fatal).
adb shell input keyevent 4 >/dev/null 2>&1 || true

set +e
python3 .github/scripts/a11y-audit.py --out "$OUT"
STATUS=$?
set -e

# Keep a screenshot and the hierarchy of the final screen for the report artifact.
adb exec-out screencap -p > "$OUT/final-screen.png" 2>/dev/null || true
adb shell uiautomator dump /sdcard/final.xml >/dev/null 2>&1 || true
adb pull /sdcard/final.xml "$OUT/final.xml" >/dev/null 2>&1 || true
adb logcat -d -t 300 > "$OUT/logcat.txt" 2>/dev/null || true

exit "$STATUS"
