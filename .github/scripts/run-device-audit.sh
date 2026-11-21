#!/usr/bin/env bash
# The single device leg of CI: one emulator boot, three phases.
#
#   1. instrumented tests   (gates the job)
#   2. accessibility        (report-only; publishes measured findings)
#   3. UI/UX monkey         (gates the job on a crash or ANR)
#
# The emulator action runs each line of its `script:` input as its own `sh -c`, so the phases
# live here. Every phase always runs; the final exit status decides the job.
set -uo pipefail

readonly OUT="device-report"
mkdir -p "$OUT"

tests_status=0
monkey_status=0

echo "== 1/3 instrumented tests =="
./gradlew :app:connectedDebugAndroidTest --stacktrace --no-daemon > "$OUT/instrumented.log" 2>&1 \
  || tests_status=$?
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
            detail = (failure.get("message") or failure.text or "failed").strip()
            print(f"::error title={name}::{detail.splitlines()[0]}"[:1000])
print(f"{len(files)} result file(s), {failed} failing test(s)")
PY

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

echo "== 2/3 accessibility =="
# Start from a clean activity so the measurement never inherits the test harness state.
adb shell am force-stop com.imancore.app >/dev/null 2>&1 || true
bash .github/scripts/run-a11y-audit.sh || echo "Accessibility leg reported findings (its exit status does not fail the job)."

echo "== 3/3 UI/UX exploratory audit =="
adb shell am force-stop com.imancore.app >/dev/null 2>&1 || true
bash .github/scripts/run-uiux-audit.sh || monkey_status=$?

# Keep the accessibility and UI/UX sections next to the test log for one artifact.
cp a11y-report/a11y.md "$OUT/a11y.md" 2>/dev/null || true
cp uiux-report/report.md "$OUT/uiux.md" 2>/dev/null || true

status=0
[ "$tests_status" -eq 0 ] || status=1
[ "$monkey_status" -eq 0 ] || status=1
echo "Device legs finished: tests=$tests_status, uiux=$monkey_status"
exit "$status"
