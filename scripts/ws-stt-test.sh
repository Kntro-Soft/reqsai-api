#!/usr/bin/env bash
# =============================================================================
# ws-stt-test.sh — Interactive STT session controller
#
# USAGE
#   ./scripts/ws-stt-test.sh [OPTIONS]
#
# OPTIONS
#   --audio FILE      raw PCM file to stream (16 kHz / 16-bit / mono)
#   --lang CODE       session language, default 'en'
#   --session ID      attach to an existing session (skip create+start)
#   --realtime-pause  advance audio playback time during pause (skip paused audio)
#   --help            show this help
#
# KEYS (single keypress — no Enter needed)
#   s   create session + start recording + begin streaming
#   p   pause  — stops streaming, server closes WS
#   r   resume — reopens WS, continues streaming from paused position
#   x   stop   — permanently ends the session
#   v   verify — show saved transcript segments from DB
#   q   quit   — stops session if active, exits
#
# ENVIRONMENT
#   REQSAI_TOKEN       JWT bearer token (required)
#   REQSAI_PROJECT_ID  project UUID (required)
#   REQSAI_BASE_URL    HTTP base URL, default http://localhost:8080
#   PGPASSWORD / PGHOST / PGUSER / PGDATABASE   for [v]erify step
#   DB_SCHEMA          tenant schema, default 'tenant_test-org'
#
# REQUIREMENTS
#   websocat    brew install websocat
#   curl / jq   brew install jq
#   pv          brew install pv  (optional — rate limiting; Python 3 used as fallback)
# =============================================================================

# Disable job-done notifications from background processes
set +m

# ── Args ──────────────────────────────────────────────────────────────────────
AUDIO_FILE=""
LANG="${REQSAI_LANG:-en}"
SESSION_ID_ARG=""
SIMULATE_REALTIME_PAUSE=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --audio)            AUDIO_FILE="$2";    shift 2 ;;
    --lang)             LANG="$2";          shift 2 ;;
    --session)          SESSION_ID_ARG="$2"; shift 2 ;;
    --realtime-pause)   SIMULATE_REALTIME_PAUSE=true; shift ;;
    --help|-h)
      head -45 "$0" | grep '^#' | sed 's/^# \?//'; exit 0 ;;
    *) echo "Unknown option: $1. Use --help." >&2; exit 1 ;;
  esac
done

[[ -n "${AUDIO_FILE}" && ! -f "${AUDIO_FILE}" ]] && {
  echo "ERROR: file not found: ${AUDIO_FILE}" >&2; exit 1
}

# ── Env ───────────────────────────────────────────────────────────────────────
TOKEN="${REQSAI_TOKEN:?Set REQSAI_TOKEN env var}"
BASE_URL="${REQSAI_BASE_URL:-http://localhost:8080}"
PROJECT_ID="${REQSAI_PROJECT_ID:?Set REQSAI_PROJECT_ID env var}"
WS_BASE="${BASE_URL/http:\/\//ws://}"
WS_BASE="${WS_BASE/https:\/\//wss://}"

# ── State ─────────────────────────────────────────────────────────────────────
STATE="IDLE"
SESSION_ID="${SESSION_ID_ARG:-}"
STREAM_PID=""
STREAM_START_MS=0
PAUSE_START_MS=0
BYTES_STREAMED=0      # bytes sent before the current streaming segment
MSG=""                # info line shown in the UI

AUDIO_BYTES=0
AUDIO_DURATION_S=0
[[ -n "${AUDIO_FILE}" ]] && {
  AUDIO_BYTES=$(wc -c < "${AUDIO_FILE}" | tr -d ' ')
  AUDIO_DURATION_S=$(( AUDIO_BYTES / 32000 ))
}

# ── Helpers ───────────────────────────────────────────────────────────────────
# Check if date supports %N (nanoseconds)
if [[ "$(date +%s%3N)" == *N ]]; then
  if command -v perl &>/dev/null; then
    now_ms() { perl -MTime::HiRes=time -e 'printf "%.0f\n", time * 1000'; }
  elif command -v python3 &>/dev/null; then
    now_ms() { python3 -c 'import time; print(int(time.time() * 1000))'; }
  elif command -v ruby &>/dev/null; then
    now_ms() { ruby -e 'puts (Time.now.to_f * 1000).to_i'; }
  else
    now_ms() { echo "$(date +%s)000"; }
  fi
else
  now_ms() { date +%s%3N; }
fi

stream_pos_s() {
  if [[ -z "${STREAM_PID}" ]] || ! kill -0 "${STREAM_PID}" 2>/dev/null; then
    echo $(( BYTES_STREAMED / 32000 ))
  else
    local elapsed_ms=$(( $(now_ms) - STREAM_START_MS ))
    echo $(( (BYTES_STREAMED + elapsed_ms * 32) / 32000 ))
  fi
}

api_post() {
  local url="$1"; shift
  local http
  http=$(curl -s --max-time 10 -o /tmp/.stt-resp -w "%{http_code}" -X POST "${url}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Api-Version: 1" "$@")
  if [[ "${http}" != 2* ]]; then
    MSG="ERROR HTTP ${http}: $(jq -r '.message // .error // .' /tmp/.stt-resp 2>/dev/null)"
    return 1
  fi
  cat /tmp/.stt-resp
}

start_stream() {
  [[ -z "${AUDIO_FILE}" ]] && return  # no audio — WS not opened, session still RECORDING

  local url="${WS_BASE}/ws/stt?session=${SESSION_ID}&token=${TOKEN}"
  local skip=${BYTES_STREAMED}

  if command -v pv &>/dev/null; then
    if [[ $skip -gt 0 && $skip -lt $AUDIO_BYTES ]]; then
      tail -c +$(( skip + 1 )) "${AUDIO_FILE}" | \
        pv -q -L 32000 | websocat --binary "${url}" >/dev/null 2>&1 &
    else
      pv -q -L 32000 "${AUDIO_FILE}" | websocat --binary "${url}" >/dev/null 2>&1 &
    fi
  elif command -v python3 &>/dev/null; then
    python3 -c '
import sys, subprocess, time
skip, audio_file, ws_url = int(sys.argv[1]), sys.argv[2], sys.argv[3]
CHUNK, SLEEP = 4096, 0.128
proc = subprocess.Popen(["websocat", "--binary", ws_url], stdin=subprocess.PIPE)
with open(audio_file, "rb") as f:
    f.seek(skip)
    while chunk := f.read(CHUNK):
        proc.stdin.write(chunk); proc.stdin.flush(); time.sleep(SLEEP)
proc.stdin.close(); proc.wait()
' "${skip}" "${AUDIO_FILE}" "${url}" >/dev/null 2>&1 &
  else
    tail -c +$(( skip + 1 )) "${AUDIO_FILE}" | websocat --binary "${url}" >/dev/null 2>&1 &
  fi

  STREAM_PID=$!
  STREAM_START_MS=$(now_ms)
}

stop_stream() {
  if [[ -n "${STREAM_PID}" ]] && kill -0 "${STREAM_PID}" 2>/dev/null; then
    local elapsed_ms=$(( $(now_ms) - STREAM_START_MS ))
    BYTES_STREAMED=$(( BYTES_STREAMED + elapsed_ms * 32 ))
    [[ $BYTES_STREAMED -gt $AUDIO_BYTES ]] && BYTES_STREAMED=${AUDIO_BYTES}
    kill "${STREAM_PID}" 2>/dev/null || true
    wait "${STREAM_PID}" 2>/dev/null || true
  fi
  STREAM_PID=""
}

# ── UI ────────────────────────────────────────────────────────────────────────
draw() {
  clear
  echo "========================================================================"
  echo "  ReqsAI STT — Interactive Controller"
  echo "========================================================================"
  printf "  Project  : %s\n" "${PROJECT_ID}"
  printf "  Language : %s (Realtime Pause: %s)\n" "${LANG}" "${SIMULATE_REALTIME_PAUSE}"
  if [[ $AUDIO_BYTES -gt 0 ]]; then
    local pos; pos=$(stream_pos_s)
    printf "  Audio    : %s  (%ss total — at %ss)\n" \
      "${AUDIO_FILE##*/}" "${AUDIO_DURATION_S}" "${pos}"
  else
    printf "  Audio    : (none — lifecycle test only)\n"
  fi
  echo "------------------------------------------------------------------------"
  printf "  Session  : %s\n" "${SESSION_ID:-not created yet}"
  printf "  State    : %s\n" "${STATE}"
  [[ -n "${MSG}" ]] && printf "  Info     : %s\n" "${MSG}"
  echo "------------------------------------------------------------------------"
  case "${STATE}" in
    IDLE)
      printf "  [s] start\n" ;;
    RECORDING)
      printf "  [p] pause    [x] stop    [v] verify    [q] quit\n" ;;
    PAUSED)
      printf "  [r] resume   [x] stop    [v] verify    [q] quit\n" ;;
    STOPPED)
      printf "  [v] verify   [q] quit\n" ;;
  esac
  echo ""
  printf "> "
}

show_verify() {
  clear
  echo "=== Transcript segments — ${SESSION_ID} ==="
  if command -v psql &>/dev/null; then
    PGPASSWORD="${PGPASSWORD:-secret}" \
    psql -h "${PGHOST:-localhost}" -U "${PGUSER:-reqsai}" -d "${PGDATABASE:-reqsai}" \
      -c "SELECT sequence,
               start_ms/1000||'s–'||end_ms/1000||'s' AS range,
               LEFT(text, 80) AS text
          FROM \"${DB_SCHEMA:-tenant_test-org}\".transcript_segments
          WHERE session_id = '${SESSION_ID}'
          ORDER BY sequence;" 2>/dev/null \
    || echo "(DB query failed — check PGPASSWORD / PGHOST / PGUSER / PGDATABASE)"
  else
    echo "psql not found. Run manually:"
    echo "  SELECT * FROM transcript_segments WHERE session_id = '${SESSION_ID}';"
  fi
  echo ""
  read -r -s -n 1 -p "Press any key to return..."
  MSG="Last verified: $(date +%H:%M:%S)"
}

# ── Cleanup ───────────────────────────────────────────────────────────────────
cleanup() {
  tput cnorm 2>/dev/null || true
  stop_stream
  if [[ "${STATE}" == "RECORDING" || "${STATE}" == "PAUSED" ]]; then
    echo ""
    echo "Stopping session on exit..."
    api_post "${BASE_URL}/api/projects/${PROJECT_ID}/sessions/${SESSION_ID}/stop" \
      -d '{}' >/dev/null 2>&1 || true
  fi
  echo ""
}

trap cleanup EXIT INT TERM
tput civis 2>/dev/null || true  # hide cursor during interactive mode

# ── Attach to existing session ────────────────────────────────────────────────
if [[ -n "${SESSION_ID}" ]]; then
  STATE="RECORDING"
  start_stream
  MSG="Attached to existing session"
fi

# ── Main loop ─────────────────────────────────────────────────────────────────
while true; do
  draw
  read -r -s -n 1 key 2>/dev/null || key="q"

  case "${key}" in

    s|S)
      if [[ "${STATE}" == "IDLE" ]]; then
        MSG="Creating session..."
        draw
        RESP=$(api_post "${BASE_URL}/api/projects/${PROJECT_ID}/sessions" \
          -d "{\"title\":\"ws-stt-$(date +%s)\",\"language\":\"${LANG}\"}") || continue
        SESSION_ID=$(echo "${RESP}" | jq -r '.id')
        api_post "${BASE_URL}/api/projects/${PROJECT_ID}/sessions/${SESSION_ID}/start" \
          -d '{}' >/dev/null || continue
        BYTES_STREAMED=0
        start_stream
        STATE="RECORDING"
        MSG="Started$([ -n "${AUDIO_FILE}" ] && echo " — streaming audio" || echo " — no audio")"
      fi
      ;;

    p|P)
      if [[ "${STATE}" == "RECORDING" ]]; then
        stop_stream
        api_post "${BASE_URL}/api/projects/${PROJECT_ID}/sessions/${SESSION_ID}/pause" \
          -d '{}' >/dev/null || { start_stream; continue; }
        PAUSE_START_MS=$(now_ms)
        STATE="PAUSED"
        MSG="Paused at $(( BYTES_STREAMED / 32000 ))s"
      fi
      ;;

    r|R)
      if [[ "${STATE}" == "PAUSED" ]]; then
        api_post "${BASE_URL}/api/projects/${PROJECT_ID}/sessions/${SESSION_ID}/resume" \
          -d '{}' >/dev/null || continue
        if [[ "${SIMULATE_REALTIME_PAUSE}" == "true" ]]; then
          pause_duration_ms=$(( $(now_ms) - PAUSE_START_MS ))
          BYTES_STREAMED=$(( BYTES_STREAMED + pause_duration_ms * 32 ))
          [[ $BYTES_STREAMED -gt $AUDIO_BYTES ]] && BYTES_STREAMED=${AUDIO_BYTES}
        fi
        start_stream
        STATE="RECORDING"
        MSG="Resumed from $(( BYTES_STREAMED / 32000 ))s"
      fi
      ;;

    x|X)
      if [[ "${STATE}" == "RECORDING" || "${STATE}" == "PAUSED" ]]; then
        stop_stream
        api_post "${BASE_URL}/api/projects/${PROJECT_ID}/sessions/${SESSION_ID}/stop" \
          -d '{}' >/dev/null || continue
        STATE="STOPPED"
        MSG="Stopped. Total audio sent: $(( BYTES_STREAMED / 32000 ))s"
      fi
      ;;

    v|V)
      [[ -n "${SESSION_ID}" ]] && show_verify
      ;;

    q|Q)
      exit 0
      ;;

  esac
done
