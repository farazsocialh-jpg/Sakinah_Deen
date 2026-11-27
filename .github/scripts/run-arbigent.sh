#!/bin/sh
# Run the Arbigent UX suite on the emulator started by android-emulator-runner.
#
# This lives in its own file because that action splits the `script:` input on
# newlines and executes each line with a separate `sh -c`. A multi-line block in
# the workflow therefore cannot share variables, functions or loops between
# lines. The workflow passes this single-line invocation instead:
#
#   script: sh .github/scripts/run-arbigent.sh
#
# Keep the script POSIX sh (no arrays, pipefail or process substitution).

set -u

LOG=arbigent-run.log
ATTEMPT_LOG=arbigent-attempt.log
PROBE_BODY=arbigent-probe.json
EXIT_FILE=arbigent-exit-code
RESULT_FILE=arbigent-result/result.yml
APK=app/build/outputs/apk/debug/app-debug.apk
PROJECT_FILE=.arbigent/imancore_ux_audit.yaml

# A provider/quota failure is an HTTP status with context, or an explicit
# provider error message. Bare numbers would also match timestamps, screenshot
# names and step counts, which used to hide real UI failures behind a retry.
API_STATUS_RE='(HTTP|https?[-_ ]?status|status[-_ ]?code|code)[^0-9]{0,24}(401|403|404|429|500|502|503|504)([^0-9]|$)'
API_WORD_RE='RESOURCE_EXHAUSTED|rate[-_ ]?limit|quota[-_ ]?exceeded|insufficient[-_ ]?quota|service[-_ ]?unavailable|overloaded|missing (gemini|openai) api key|invalid[-_ ]?api[-_ ]?key|api key not valid|free[-_ ]?tier[-_ ]?requests'
API_ERROR_PATTERN="$API_STATUS_RE|$API_WORD_RE"

: > "$LOG"
rm -f "$EXIT_FILE" "$PROBE_BODY" "$ATTEMPT_LOG"
rm -rf arbigent-result

run_args="--os=android --project-file=$PROJECT_FILE"
initial_ids="${ARBIGENT_SCENARIO_IDS:-}"
if [ -n "$initial_ids" ]; then
  echo "Limiting this run to scenario ids: $initial_ids"
fi

# --- provider configuration -------------------------------------------------
# Sets MODEL / ENDPOINT / KEY / API_ENV and AI_ARGS. The key is never put on
# the command line: Arbigent reads OPENAI_API_KEY / GEMINI_API_KEY from the
# environment, and the CLI masks those values in its own output.
configure_provider() {
  MODEL=""; ENDPOINT=""; KEY=""; API_ENV=""
  case "$1" in
    github)
      # GitHub Models was retired in 2026. Keep the tier available as an
      # opt-in for a future restoration, but do not spend a probe on it by
      # default when the default GITHUB_TOKEN is always present.
      [ "${ENABLE_GITHUB_MODELS:-false}" = "true" ] || return 1
      [ -n "${GITHUB_TOKEN:-}" ] || return 1
      MODEL="$GITHUB_MODEL"
      ENDPOINT="https://models.github.ai/inference"
      KEY="$GITHUB_TOKEN"
      API_ENV=openai
      ;;
    groq)
      [ -n "${GROQ_API_KEY:-}" ] || return 1
      MODEL="$GROQ_MODEL"
      ENDPOINT="https://api.groq.com/openai/v1"
      KEY="$GROQ_API_KEY"
      API_ENV=openai
      ;;
    gemini)
      [ -n "${GEMINI_API_KEY:-}" ] || return 1
      MODEL="$GEMINI_MODEL"
      KEY="$GEMINI_API_KEY"
      API_ENV=gemini
      ;;
    openrouter)
      [ -n "${OPENROUTER_API_KEY:-}" ] || return 1
      MODEL="$OPENROUTER_MODEL"
      ENDPOINT="https://openrouter.ai/api/v1"
      KEY="$OPENROUTER_API_KEY"
      API_ENV=openai
      ;;
    *)
      return 1
      ;;
  esac
  if [ "$API_ENV" = openai ]; then
    AI_ARGS="--ai-type=openai --openai-endpoint=$ENDPOINT/ --openai-model-name=$MODEL"
  else
    AI_ARGS="--ai-type=gemini --gemini-model-name=$MODEL"
  fi
  return 0
}

# --- pre-flight probes ------------------------------------------------------
# One-token request: cheap proof that the key works and the endpoint is alive.
probe_openai() {
  url="${1%/}/chat/completions"
  body=$(printf '{"model":"%s","messages":[{"role":"user","content":"ping"}],"max_tokens":1}' "$3")
  code=$(curl -sS --max-time 45 -o "$PROBE_BODY" -w '%{http_code}' \
    -H "Authorization: Bearer $2" \
    -H 'Content-Type: application/json' \
    --data "$body" "$url" 2>/dev/null) || code=000
  case "$code" in
    200) echo "  probe: HTTP 200"; return 0 ;;
  esac
  echo "  probe: HTTP $code $(head -c 300 "$PROBE_BODY" 2>/dev/null | tr '\n' ' ')"
  return 1
}

probe_gemini() {
  url="https://generativelanguage.googleapis.com/v1beta/models/$2:generateContent"
  code=$(curl -sS --max-time 45 -o "$PROBE_BODY" -w '%{http_code}' \
    -H "x-goog-api-key: $1" \
    -H 'Content-Type: application/json' \
    --data '{"contents":[{"parts":[{"text":"ping"}]}],"generationConfig":{"maxOutputTokens":1}}' \
    "$url" 2>/dev/null) || code=000
  case "$code" in
    200) echo "  probe: HTTP 200"; return 0 ;;
  esac
  echo "  probe: HTTP $code $(head -c 300 "$PROBE_BODY" 2>/dev/null | tr '\n' ' ')"
  return 1
}

probe_provider() {
  if [ "$API_ENV" = openai ]; then
    probe_openai "$ENDPOINT" "$KEY" "$MODEL"
  else
    probe_gemini "$KEY" "$MODEL"
  fi
}

# --- running and reading a single attempt -----------------------------------
# Each provider gets a clean result directory so a previous attempt's report
# can never be mistaken for this one's. Output is streamed and kept in $LOG;
# the CLI exit status lands in $EXIT_FILE because a pipeline reports tee's
# status, not the CLI's.
run_with_provider() {
  filter="${2:-}"
  [ -n "$filter" ] || filter="$initial_ids"
  if [ -n "$filter" ]; then
    attempt_args="$run_args --scenario-ids=$filter"
  else
    attempt_args="$run_args"
  fi

  rm -rf arbigent-result
  rm -f "$RESULT_FILE"

  log_start=$(wc -c < "$LOG" | tr -d ' ')
  echo "::group::Arbigent on $1 ($MODEL)"
  {
    if [ "$API_ENV" = openai ]; then
      OPENAI_API_KEY="$KEY" ./arbigentw run $AI_ARGS $attempt_args 2>&1 && rc=0 || rc=$?
    else
      ./arbigentw run $AI_ARGS $attempt_args 2>&1 && rc=0 || rc=$?
    fi
    echo "$rc" > "$EXIT_FILE"
  } | tee -a "$LOG"
  echo "::endgroup::"
  tail -c "+$((log_start + 1))" "$LOG" > "$ATTEMPT_LOG"
  return 0
}

# Emit one line per scenario: id<TAB>isSuccess<TAB>executionStatus.
# Arbigent's result YAML encodes the verdict in `isSuccess`; `executionStatus`
# is either a multi-line task summary or a pre-flight error string.
scenario_results() {
  [ -f "$RESULT_FILE" ] || return 0
  awk '
    function value(s, t) {
      t = s
      sub(/^[^:]*:[[:space:]]*/, "", t)
      gsub(/"/, "", t)
      sub(/[[:space:]]+$/, "", t)
      return t
    }
    function flush() {
      if (id != "") print id "\t" ok "\t" status
    }
    /^[[:space:]]*-[[:space:]]*id:/ {
      flush()
      id = value($0); ok = ""; status = ""
      next
    }
    id != "" && /^[[:space:]]*isSuccess:/ { ok = value($0); next }
    id != "" && /^[[:space:]]*executionStatus:/ { status = value($0); next }
    END { flush() }
  ' "$RESULT_FILE"
}

# Scenario ids that did not succeed, comma-separated. Empty means all passed.
failed_scenario_ids() {
  scenario_results | awk -F'\t' '
    tolower($2) == "false" || tolower($3) ~ /fail|error|timeout|cancel|interrupt/ { print $1 }
  ' | paste -sd, -
}

provider_hit_api_error() {
  files="$ATTEMPT_LOG"
  [ -f "$RESULT_FILE" ] && files="$files $RESULT_FILE"
  # Word splitting is intentional: $files is a list of paths to grep.
  # shellcheck disable=SC2086
  grep -Eqi "$API_ERROR_PATTERN" $files 2>/dev/null
}

# Failed scenarios whose own status is not a provider/quota error. These are
# real UI/assertion bugs and must never be hidden by a fallback to another
# provider, even when the same attempt also logged a quota error.
# $1=yes ignores scenarios with no status when the CLI log already proves a
# provider error; otherwise an empty status is treated as a UI failure.
ui_failure_ids() {
  ignore_empty="${1:-no}"
  candidates=$(scenario_results | awk -F'\t' -v ignore_empty="$ignore_empty" '
    {
      ok = tolower($2)
      status = tolower($3)
      failed = (ok == "false" || status ~ /fail|error|timeout|cancel|interrupt/)
      if (!failed) next
      if ($3 == "" && ignore_empty == "yes") next
      print $1 "\t" status
    }
  ')
  [ -n "$candidates" ] || return 0
  printf '%s\n' "$candidates" | while read -r id status; do
    # grep, not awk: mawk mishandles some dynamic EREs passed with -v.
    if printf '%s' "$status" | grep -Eqi "$API_ERROR_PATTERN"; then
      continue
    fi
    printf '%s\n' "$id"
  done | paste -sd, -
}

# --- the chain --------------------------------------------------------------
[ -f "$APK" ] || {
  echo "::error::Debug APK not found at $APK. Did the build step run?"
  exit 1
}
adb -s "${ANDROID_SERIAL:-emulator-5554}" install -r "$APK" || {
  echo "::error::adb install failed; cannot run the UX suite."
  exit 1
}

provider=""
retry_ids=""
for candidate in groq gemini openrouter github; do
  echo "--- provider: $candidate"
  if ! configure_provider "$candidate"; then
    echo "  skipped: no usable key configured (or tier disabled)"
    continue
  fi
  echo "  model: $MODEL"
  if ! probe_provider; then
    echo "  skipped: pre-flight probe rejected this provider"
    continue
  fi

  run_with_provider "$candidate" "$retry_ids"
  rc=$(cat "$EXIT_FILE" 2>/dev/null || echo 1)
  failed_ids=$(failed_scenario_ids)
  scenario_count=$(scenario_results | wc -l | tr -d ' ')

  if provider_hit_api_error; then
    api_error=yes
    ui_ids=$(ui_failure_ids yes)
  else
    api_error=no
    ui_ids=$(ui_failure_ids no)
  fi

  if [ "$rc" = "0" ] && [ "$scenario_count" -gt 0 ] && [ -z "$failed_ids" ]; then
    echo "::notice::All selected scenarios passed on the $candidate provider ($MODEL)."
    rm -f "$PROBE_BODY" "$EXIT_FILE" "$ATTEMPT_LOG"
    exit 0
  fi

  # A genuine UI bug outranks an incidental provider error in the same log.
  if [ -n "$ui_ids" ]; then
    echo "::error::Scenarios failed for non-provider reasons on $candidate: $ui_ids. This is a UI/assertion failure, not a quota problem."
    exit 1
  fi

  # Only an explicit provider/quota error earns a retry on another provider.
  if [ "$api_error" = "yes" ]; then
    echo "::warning::$candidate hit a provider/quota error; trying the next provider for: ${failed_ids:-all scenarios}"
    retry_ids="$failed_ids"
    provider="$candidate"
    continue
  fi

  if [ "$rc" != "0" ]; then
    echo "::error::arbigent failed to run on $candidate (exit $rc) without a provider error. See arbigent-run.log."
    exit 1
  fi

  if [ "$scenario_count" = "0" ]; then
    echo "::error::arbigent exited 0 on $candidate but wrote no scenario results. See arbigent-run.log."
    exit 1
  fi

  echo "::error::Scenarios failed on the $candidate provider: ${failed_ids:-unknown}. See arbigent-run.log."
  exit 1
done

echo "::error::No provider completed the suite. Tried groq, gemini, openrouter and (if enabled) github; each was missing a key, rejected its probe, or hit a provider error. Last provider: ${provider:-none}."
exit 1
