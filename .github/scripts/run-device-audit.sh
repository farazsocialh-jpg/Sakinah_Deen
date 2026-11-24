#!/usr/bin/env bash
# The single device leg of CI: one emulator boot, staged so that every failure says what it is.
#
#   0 adb + emulator readiness
#   1 compile debug + androidTest APKs      (a compile error is reported as a compile error)
#   2 APK presence                          (never as "APK not found" after a failed build)
#   3 install
#   4 instrumented tests                    (gates the job)
#   5 accessibility                         (report-only; publishes measured findings)
#   6 UI/UX monkey                          (gates the job on a crash or ANR)
#
# reactivecircus/android-emulator-runner runs each line of its `script:` input as its own
# `sh -c`, so the stages live in this file. Each stage appends a row to
# `device-report/status.md`, its failures get their own `::error title=` annotation, and the
# failed stage names are collected in `device-report/failed-stages.txt` so the workflow can
# quote them in the job failure.
set -uo pipefail

readonly OUT="device-report"
readonly PACKAGE="com.imancore.app"
readonly APK="app/build/outputs/apk/debug/app-debug.apk"
readonly TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

mkdir -p "$OUT"
: > "$OUT/failed-stages.txt"
ROWS=()

record() { # stage, result, detail
  ROWS+=("| $1 | $2 | $3 |")
}

fail() { # CODE, message
  echo "::error title=$1::$2"
  echo "$1" >> "$OUT/failed-stages.txt"
}

collect_diagnostics() {
  adb logcat -d -b crash > "$OUT/logcat-crash.txt" 2>/dev/null || true
  adb logcat -d -t 500 > "$OUT/logcat-tail.txt" 2>/dev/null || true
  adb shell dumpsys activity activities > "$OUT/activities.txt" 2>&1 || true
  adb shell getprop > "$OUT/emulator-props.txt" 2>&1 || true
  adb devices -l > "$OUT/adb-devices.txt" 2>&1 || true
}

write_status() {
  {
    echo "## Device audit"
    echo
    echo "| Stage | Result | Detail |"
    echo "| --- | --- | --- |"
    printf '%s\n' "${ROWS[@]+"${ROWS[@]}"}"
    echo
    if [ -s "$OUT/failed-stages.txt" ]; then
      echo "Failed stage(s): \`$(paste -sd, "$OUT/failed-stages.txt")\`"
    else
      echo "All stages passed."
    fi
  } > "$OUT/status.md"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    cat "$OUT/status.md" >> "$GITHUB_STEP_SUMMARY"
  fi
  cat "$OUT/status.md"
}

# --------------------------------------------------------------- 0 adb/emulator
echo "== 0/6 adb and emulator =="
adb start-server >/dev/null 2>&1 || true
if ! adb wait-for-device; then
  fail EMULATOR_UNREACHABLE "adb cannot reach the emulator: no device in 'adb devices'. The emulator failed to boot or was not attached; this is not an application failure."
  record "adb/emulator" "**FAIL**" "adb device unreachable"
  collect_diagnostics
  write_status
  exit 1
fi

boot_completed=""
for _ in $(seq 1 60); do
  boot_completed=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  if [ "$boot_completed" = "1" ]; then break; fi
  sleep 5
done
if [ "$boot_completed" != "1" ]; then
  fail EMULATOR_BOOT_TIMEOUT "The emulator never reported sys.boot_completed=1 within 5 minutes; see device-report/emulator-props.txt."
  record "adb/emulator" "**FAIL**" "boot timeout"
  collect_diagnostics
  write_status
  exit 1
fi
record "adb/emulator" "ok" "boot_completed=1"

# --------------------------------------------------------------- 1 compile APKs
echo "== 1/6 compile app and androidTest APKs =="
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  --stacktrace --no-daemon > "$OUT/build.log" 2>&1
build_status=$?
if [ "$build_status" -ne 0 ]; then
  tail -n 80 "$OUT/build.log" || true
  fail ANDROID_COMPILATION_FAILED "Gradle could not build the debug/test APKs (exit $build_status). This is a compilation failure, not an emulator problem; emulator stages were skipped. See device-report/build.log."
  record "compilation" "**FAIL**" "see build.log"
  write_status
  exit 1
fi
record "compilation" "ok" "assembleDebug + assembleDebugAndroidTest"

# ------------------------------------------------------------- 2 APK presence
echo "== 2/6 APK presence =="
missing=""
for artifact in "$APK" "$TEST_APK"; do
  if [ ! -s "$artifact" ]; then missing="$missing $artifact"; fi
done
if [ -n "$missing" ]; then
  fail APK_NOT_PRODUCED "Gradle reported success but these artifacts are missing or empty:$missing"
  record "apk presence" "**FAIL**" "missing:$missing"
  write_status
  exit 1
fi
record "apk presence" "ok" "$(du -h "$APK" | cut -f1) app, $(du -h "$TEST_APK" | cut -f1) test"

# ------------------------------------------------------------------ 3 install
echo "== 3/6 install =="
if ! adb install -r -t "$APK" > "$OUT/install.log" 2>&1; then
  tail -n 40 "$OUT/install.log" || true
  fail APK_INSTALL_FAILED "adb could not install the debug APK; see device-report/install.log."
  record "install" "**FAIL**" "adb install failed"
  collect_diagnostics
  write_status
  exit 1
fi
record "install" "ok" "$(tail -n 1 "$OUT/install.log" | tr -d '\r')"

# ------------------------------------------------------- 4 instrumented tests
echo "== 4/6 instrumented tests =="
tests_status=0
./gradlew :app:connectedDebugAndroidTest --stacktrace --no-daemon \
  > "$OUT/instrumented.log" 2>&1 || tests_status=$?
tail -n 40 "$OUT/instrumented.log" || true

# Test-name-level annotations stay visible on the run page, unlike the raw job log.
python3 - <<'PY' || true
import glob
import xml.etree.ElementTree as ET

files = glob.glob("app/build/outputs/androidTest-results/**/*.xml", recursive=True)
failed = 0
for path in files:
    for case in ET.parse(path).getroot().iter("testcase"):
        for failure in list(case.iter("failure")) + list(case.iter("error")):
            failed += 1
            name = f"{case.get('classname')}.{case.get('name')}"
            # Assertion message first, then the stack: the annotation is the only part of a
            # failing device run that is readable without an admin GitHub token.
            message = " ".join((failure.get("message") or "").split())
            trace = " ".join((failure.text or "").split())
            detail = message or "failed"
            if trace and trace not in message:
                detail = f"{detail} | {trace}"
            print(f"::error title={name}::{detail[:600]}")
print(f"{len(files)} result file(s), {failed} failing test(s)")
PY

if [ "$tests_status" -ne 0 ]; then
  if grep -qE "FATAL EXCEPTION|ANR in ${PACKAGE}" "$OUT/instrumented.log" 2>/dev/null; then
    fail INSTRUMENTATION_CRASH "The app crashed or ANR'd while the instrumented tests were running (exit $tests_status); see device-report/instrumented.log and logcat-crash.txt."
    record "instrumented tests" "**FAIL**" "crash/ANR during the run"
  else
    fail INSTRUMENTATION_TESTS_FAILED "Instrumented tests failed (exit $tests_status); see the annotations above and device-report/instrumented.log."
    record "instrumented tests" "**FAIL**" "test failures"
  fi
  collect_diagnostics
else
  record "instrumented tests" "ok" "$(grep -cE 'PASSED|SUCCESS' "$OUT/instrumented.log" 2>/dev/null || echo 0) pass marker(s)"
fi

{
  echo "## Instrumented tests"
  echo
  if [ "$tests_status" -eq 0 ]; then
    echo "Passed."
  else
    echo "Failed (exit ${tests_status}); see \`device-report/instrumented.log\`."
  fi
  echo
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"

# ----------------------------------------------------------- 5 accessibility
echo "== 5/6 accessibility =="
# Start from a clean activity so the measurement never inherits the test harness state.
adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
a11y_status=0
bash .github/scripts/run-a11y-audit.sh || a11y_status=$?
if [ "$a11y_status" -ne 0 ]; then
  # Report-only by design: the counts are published, the gate is opt-in (a11y_gate).
  record "accessibility" "reported" "findings published (exit ${a11y_status} is not a job failure)"
else
  record "accessibility" "ok" "no findings collected"
fi

# ------------------------------------------------------------- 6 UI/UX monkey
echo "== 6/6 UI/UX exploratory audit =="
adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
monkey_status=0
bash .github/scripts/run-uiux-audit.sh || monkey_status=$?
if [ "$monkey_status" -ne 0 ]; then
  fail UIUX_CRASH_OR_ANR "The app crashed or ANR'd during the exploratory monkey run; see uiux-report/report.md and uiux-report/logcat.txt."
  record "ui/ux monkey" "**FAIL**" "crash or ANR"
else
  record "ui/ux monkey" "ok" "no crash or ANR"
fi

# Keep the accessibility and UI/UX sections next to the test log for one artifact.
cp a11y-report/a11y.md "$OUT/a11y.md" 2>/dev/null || true
cp uiux-report/report.md "$OUT/uiux.md" 2>/dev/null || true
collect_diagnostics

status=0
[ "$tests_status" -eq 0 ] || status=1
[ "$monkey_status" -eq 0 ] || status=1
echo "Device stages finished: tests=$tests_status, a11y=$a11y_status (report only), uiux=$monkey_status"
write_status
exit "$status"
