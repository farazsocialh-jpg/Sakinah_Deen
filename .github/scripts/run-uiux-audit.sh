#!/usr/bin/env bash
#
# Runs on the booted emulator inside reactivecircus/android-emulator-runner, which executes
# each line of its `script:` input as a separate `sh -c` — so multi-line shell constructs must
# live in a file like this one rather than inline in the workflow.
set -euo pipefail

adb install -r app/build/outputs/apk/debug/app-debug.apk

if [ -z "${OPENAI_API_KEY:-}" ]; then
  echo "::notice::OPENAI_API_KEY is not set, so the exploratory audit was skipped. Add the secret to enable it."
  exit 0
fi

# stdin is closed so a human-in-the-loop prompt cannot hang the emulator step.
npx qaosmonkey run --config qaos-monkey.config.ts < /dev/null
