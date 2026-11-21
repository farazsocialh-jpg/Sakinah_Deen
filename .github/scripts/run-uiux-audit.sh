#!/usr/bin/env bash
#
# Keyless exploratory UI audit. Runs on the already-booted emulator inside
# reactivecircus/android-emulator-runner (which executes each line of its `script:` input as a
# separate `sh -c`, hence a file), drives the debug build with Android's own monkey, captures
# screenshots and fails the job if the app crashes or ANRs. No API key or LLM account involved.
set -euo pipefail

readonly PACKAGE="com.imancore.app"
readonly REPORT_DIR="uiux-report"
readonly SHOTS="${REPORT_DIR}/screens"
readonly MONKEY_EVENTS=200
readonly MAX_SHOTS=20

mkdir -p "${SHOTS}"

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c

# Launch through the launcher intent so no activity name has to be hardcoded.
adb shell monkey -p "${PACKAGE}" -c android.intent.category.LAUNCHER 1 \
  > "${REPORT_DIR}/launch.log" 2>&1 || true
sleep 8
adb exec-out screencap -p > "${SHOTS}/00-launch.png"

# Explore while sampling the screen: monkey is throttled so the captures land on varied screens.
adb shell monkey -p "${PACKAGE}" --pct-syskeys 0 --throttle 400 --ignore-timeouts -v "${MONKEY_EVENTS}" \
  > "${REPORT_DIR}/monkey.log" 2>&1 &
monkey_pid=$!

shot=1
while kill -0 "${monkey_pid}" 2>/dev/null && [ "${shot}" -le "${MAX_SHOTS}" ]; do
  adb exec-out screencap -p > "${SHOTS}/$(printf '%02d' "${shot}")-explore.png"
  shot=$((shot + 1))
  sleep 6
done
wait "${monkey_pid}" || true

adb logcat -d > "${REPORT_DIR}/logcat.txt"
adb shell dumpsys activity activities > "${REPORT_DIR}/activities.txt" 2>&1 || true

crashes="$(grep -E "FATAL EXCEPTION|ANR in ${PACKAGE}" "${REPORT_DIR}/logcat.txt" || true)"

{
  echo "# UI/UX smoke audit"
  echo
  echo "- Package: \`${PACKAGE}\`"
  echo "- Exploration: ${MONKEY_EVENTS} throttled monkey events"
  echo "- Screenshots: $(find "${SHOTS}" -name '*.png' | wc -l)"
  echo
  echo "## Crashes and ANRs"
  echo
  if [ -n "${crashes}" ]; then
    echo '```'
    echo "${crashes}"
    echo '```'
  else
    echo "None detected."
  fi
} > "${REPORT_DIR}/report.md"

if [ -n "${crashes}" ]; then
  echo "::error::The app crashed or ANR'd during the exploratory audit; see uiux-report/report.md"
  exit 1
fi

echo "Exploratory audit finished without crashes."
