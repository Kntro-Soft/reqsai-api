#!/usr/bin/env bash
# =============================================================================
# ws-stt-test.sh — End-to-end STT streaming test with selectable steps
#
# USAGE
#   ./scripts/ws-stt-test.sh [OPTIONS]
#
# OPTIONS
#   --session ID      use an existing session (skips 'create' and 'start')
#   --audio FILE      raw PCM file to stream; generates a TTS clip if omitted
#                     (requires macOS 'say' + 'afconvert', or ffmpeg)
#   --lang CODE       session language, default 'en'
#   --pause           include a pause → resume cycle after the first stream
#   --skip STEPS      comma-separated steps to omit
#   --no-stop         do not stop the session at the end (keep it RECORDING)
#   --no-verify       skip DB segment count check
#   --help            show this help
#
# STEPS (in order)
#   create    POST session (skipped if --session is given)
#   start     POST start (skipped if --session is given)
#   audio     stream audio via WebSocket
#   pause     POST pause + stream second audio clip + POST resume  (opt-in with --pause)
#   stop      POST stop
#   verify    query DB for saved segments
#
# ENVIRONMENT
#   REQSAI_TOKEN        JWT bearer token (required)
#   REQSAI_PROJECT_ID   project UUID (required)
#   REQSAI_BASE_URL     HTTP base URL, default http://localhost:8080
#   PGPASSWORD          DB password for verify step (default: secret)
#   PGHOST / PGUSER / PGDATABASE  DB connection (defaults: localhost / reqsai / reqsai)
#   DB_SCHEMA           tenant schema (default: tenant_test-org)
#
# EXAMPLES
#   # Minimal: create → start → stop → verify (no audio)
#   ./scripts/ws-stt-test.sh --skip audio
#
#   # Standard: all steps with provided audio
#   ./scripts/ws-stt-test.sh --audio /tmp/meeting.raw
#
#   # Pause/resume cycle
#   ./scripts/ws-stt-test.sh --audio /tmp/meeting.raw --pause
#
#   # Use existing session, skip create+start, stream audio, stop
#   ./scripts/ws-stt-test.sh --session 019ee... --audio /tmp/audio.raw
#
#   # Spanish session, no stop (keep recording after test)
#   ./scripts/ws-stt-test.sh --audio /tmp/es.raw --lang es --no-stop
# =============================================================================
set -euo pipefail

# ── Defaults ──────────────────────────────────────────────────────────────────
SESSION_ID=""
AUDIO_FILE=""
LANG="${REQSAI_LANG:-en}"
DO_PAUSE=false
DO_STOP=true
DO_VERIFY=true
SKIP_STEPS=""

TOKEN="${REQSAI_TOKEN:?Set REQSAI_TOKEN env var}"
BASE_URL="${REQSAI_BASE_URL:-http://localhost:8080}"
PROJECT_ID="${REQSAI_PROJECT_ID:?Set REQSAI_PROJECT_ID env var}"
WS_BASE="${BASE_URL/http:\/\//ws://}"
WS_BASE="${WS_BASE/https:\/\//wss://}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── Argument parsing ──────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --session)  SESSION_ID="$2"; shift 2 ;;
    --audio)    AUDIO_FILE="$2"; shift 2 ;;
    --lang)     LANG="$2"; shift 2 ;;
    --pause)    DO_PAUSE=true; shift ;;
    --no-stop)  DO_STOP=false; shift ;;
    --no-verify) DO_VERIFY=false; shift ;;
    --skip)     SKIP_STEPS="$2"; shift 2 ;;
    --help|-h)
      head -50 "$0" | grep '^#' | sed 's/^# \?//'
      exit 0
      ;;
    *) echo "Unknown option: $1. Use --help." >&2; exit 1 ;;
  esac
done

# ── Helpers ───────────────────────────────────────────────────────────────────
should_skip() { echo ",${SKIP_STEPS}," | grep -q ",${1},"; }

api_post() {
  local url="$1"; shift
  curl -sf -X POST "${url}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Api-Version: 1" \
    "$@" > /dev/null
}

stream_audio() {
  local sid="$1" file="$2"
  REQSAI_TOKEN="${TOKEN}" REQSAI_WS_URL="${WS_BASE}" \
    "${SCRIPT_DIR}/ws-stt-stream.sh" "${sid}" "${file}"
}

generate_audio() {
  local lang="$1" out="$2"
  if command -v say &>/dev/null && command -v afconvert &>/dev/null; then
    local voice="Samantha"
    local text="This is a streaming transcription test for ReqsAI."
    [[ "${lang}" == "es"* ]] && { voice="Paulina"; text="Esta es una prueba de transcripción en tiempo real para ReqsAI."; }
    local tmp="/tmp/ws-stt-tts-$$.aiff"
    say -v "${voice}" "${text}" -o "${tmp}"
    afconvert "${tmp}" -o "${out}.wav" -f WAVE -d LEI16@16000 -c 1
    dd if="${out}.wav" bs=1 skip=44 of="${out}" 2>/dev/null
    rm -f "${tmp}" "${out}.wav"
  elif command -v ffmpeg &>/dev/null; then
    ffmpeg -loglevel quiet -f lavfi -i "sine=frequency=440:duration=5" \
      -ar 16000 -ac 1 -f s16le "${out}"
  else
    echo "ERROR: no audio source (set --audio, or install macOS say/ffmpeg)" >&2
    return 1
  fi
}

sep() { echo "------------------------------------------------------------------------"; }

# ── Header ────────────────────────────────────────────────────────────────────
echo "========================================================================"
echo "  ReqsAI — STT streaming test"
echo "========================================================================"
echo "Project : ${PROJECT_ID}"
echo "Server  : ${BASE_URL}"
echo "Language: ${LANG}"
[[ -n "${SKIP_STEPS}" ]] && echo "Skipping: ${SKIP_STEPS}"
echo ""

# ── Step: create ─────────────────────────────────────────────────────────────
if [[ -n "${SESSION_ID}" ]]; then
  echo "[create] Skipped — using provided session: ${SESSION_ID}"
elif should_skip "create"; then
  echo "[create] Skipped"
else
  echo -n "[create] Creating session... "
  RESP=$(curl -sf -X POST "${BASE_URL}/api/projects/${PROJECT_ID}/sessions" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Api-Version: 1" \
    -d "{\"title\":\"ws-stt-test-$(date +%s)\",\"language\":\"${LANG}\"}")
  SESSION_ID=$(echo "${RESP}" | jq -r '.id // empty')
  [[ -n "${SESSION_ID}" ]] || { echo "FAIL"; echo "${RESP}" >&2; exit 1; }
  echo "${SESSION_ID}"
fi

[[ -n "${SESSION_ID}" ]] || { echo "ERROR: no session (provide --session or run 'create')" >&2; exit 1; }

# ── Step: start ──────────────────────────────────────────────────────────────
if [[ -n "${2:-}" ]] && should_skip "start"; then
  echo "[start]  Skipped"
elif [[ -n "${2:-}" ]]; then
  # already existing session passed via --session — skip start too unless user wants it
  echo "[start]  Skipped (existing session assumed RECORDING)"
else
  if should_skip "start"; then
    echo "[start]  Skipped"
  else
    echo -n "[start]  Starting recording... "
    api_post "${BASE_URL}/api/projects/${PROJECT_ID}/sessions/${SESSION_ID}/start" -d '{}'
    echo "RECORDING"
  fi
fi

# ── Step: audio ──────────────────────────────────────────────────────────────
if should_skip "audio"; then
  echo "[audio]  Skipped — no audio streamed"
else
  TMP_AUDIO=""
  if [[ -n "${AUDIO_FILE}" ]]; then
    [[ -f "${AUDIO_FILE}" ]] || { echo "ERROR: file not found: ${AUDIO_FILE}" >&2; exit 1; }
    RAW_FILE="${AUDIO_FILE}"
  else
    TMP_AUDIO="/tmp/ws-stt-gen-$$.raw"
    echo -n "[audio]  Generating test audio (${LANG})... "
    generate_audio "${LANG}" "${TMP_AUDIO}"
    RAW_FILE="${TMP_AUDIO}"
    echo "$(wc -c < "${RAW_FILE}" | tr -d ' ') bytes"
  fi

  echo "[audio]  Streaming to /ws/stt..."
  sep
  stream_audio "${SESSION_ID}" "${RAW_FILE}"
  sep

  [[ -n "${TMP_AUDIO}" ]] && rm -f "${TMP_AUDIO}"
fi

# ── Step: pause / resume ──────────────────────────────────────────────────────
if ${DO_PAUSE} && ! should_skip "pause"; then
  echo ""
  echo -n "[pause]  Pausing session... "
  api_post "${BASE_URL}/api/projects/${PROJECT_ID}/sessions/${SESSION_ID}/pause" -d '{}'
  echo "PAUSED (WS closed by server)"

  echo -n "[pause]  Waiting 2s... "
  sleep 2
  echo "done"

  echo -n "[resume] Resuming session... "
  api_post "${BASE_URL}/api/projects/${PROJECT_ID}/sessions/${SESSION_ID}/resume" -d '{}'
  echo "RECORDING"

  if ! should_skip "audio"; then
    echo "[resume] Streaming second audio clip..."
    TMP2="/tmp/ws-stt-resume-$$.raw"
    generate_audio "${LANG}" "${TMP2}"
    sep
    stream_audio "${SESSION_ID}" "${TMP2}"
    sep
    rm -f "${TMP2}"
  fi
fi

# ── Step: stop ────────────────────────────────────────────────────────────────
if ${DO_STOP} && ! should_skip "stop"; then
  echo -n "[stop]   Stopping session... "
  api_post "${BASE_URL}/api/projects/${PROJECT_ID}/sessions/${SESSION_ID}/stop" -d '{}'
  echo "STOPPED"
else
  echo "[stop]   Skipped — session remains active"
fi

# ── Step: verify ─────────────────────────────────────────────────────────────
if ${DO_VERIFY} && ! should_skip "verify"; then
  echo ""
  echo "[verify] Checking transcript_segments..."
  if command -v psql &>/dev/null; then
    PGPASSWORD="${PGPASSWORD:-secret}" \
    psql -h "${PGHOST:-localhost}" -U "${PGUSER:-reqsai}" -d "${PGDATABASE:-reqsai}" \
      -c "SELECT sequence, speaker_label, start_ms/1000||'s-'||end_ms/1000||'s' AS range, LEFT(text,70) AS text
          FROM \"${DB_SCHEMA:-tenant_test-org}\".transcript_segments
          WHERE session_id = '${SESSION_ID}'
          ORDER BY sequence;" 2>/dev/null \
    && ROWS=$(PGPASSWORD="${PGPASSWORD:-secret}" \
        psql -h "${PGHOST:-localhost}" -U "${PGUSER:-reqsai}" -d "${PGDATABASE:-reqsai}" \
          -t -c "SELECT count(*) FROM \"${DB_SCHEMA:-tenant_test-org}\".transcript_segments WHERE session_id='${SESSION_ID}';" \
          2>/dev/null | tr -d ' ') \
    && echo "         Total segments: ${ROWS}" \
    || echo "         (DB query failed — check PGPASSWORD / PGHOST / PGUSER / PGDATABASE)"
  else
    echo "         psql not found. Run manually:"
    echo "         SELECT * FROM transcript_segments WHERE session_id = '${SESSION_ID}';"
  fi
fi

# ── Footer ────────────────────────────────────────────────────────────────────
echo ""
echo "========================================================================"
echo "  Session: ${SESSION_ID}"
echo "========================================================================"
