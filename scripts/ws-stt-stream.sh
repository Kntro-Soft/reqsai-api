#!/usr/bin/env bash
# =============================================================================
# ws-stt-stream.sh — Open the /ws/stt WebSocket and optionally stream a PCM file
#
# USAGE
#   ./scripts/ws-stt-stream.sh <SESSION_ID> [AUDIO_FILE]
#
# ARGUMENTS
#   SESSION_ID   UUID of a session in RECORDING status
#   AUDIO_FILE   (optional) path to a raw PCM file (16 kHz, 16-bit, mono).
#                Omit to open an interactive connection — the WS stays open
#                and forwards stdin; press Ctrl+C to disconnect.
#
# AUDIO FORMAT
#   The file must be raw PCM — no WAV/MP3 container.
#   Convert any file:  ffmpeg -i input.mp3 -ar 16000 -ac 1 -f s16le output.raw
#
# RATE LIMITING
#   When streaming a file, audio is sent at real-time speed (32 000 bytes/s).
#   This requires either pv (brew install pv) or Python 3 (bundled on macOS).
#   If neither pv nor Python are available, the file is sent as fast as possible
#   (the STT provider usually handles bursts, but results may be less granular).
#
# ENVIRONMENT
#   REQSAI_TOKEN      JWT bearer token (required)
#   REQSAI_WS_URL     WebSocket base URL, default ws://localhost:8080
#
# REQUIREMENTS
#   websocat >= 1.12    brew install websocat
#   pv (optional)       brew install pv
#
# EXAMPLES
#   export REQSAI_TOKEN="eyJhbG..."
#
#   # Interactive: open WS, keep alive (Ctrl+C to close)
#   ./scripts/ws-stt-stream.sh 019ee142-...
#
#   # File: stream at real-time rate and exit when done
#   ./scripts/ws-stt-stream.sh 019ee142-... /tmp/meeting.raw
# =============================================================================
set -euo pipefail

SESSION_ID="${1:?Usage: $0 <SESSION_ID> [AUDIO_FILE]}"
AUDIO_FILE="${2:-}"

TOKEN="${REQSAI_TOKEN:?Set REQSAI_TOKEN env var}"
WS_BASE="${REQSAI_WS_URL:-ws://localhost:8080}"
WS_URL="${WS_BASE}/ws/stt?session=${SESSION_ID}&token=${TOKEN}"

echo "Session : ${SESSION_ID}"
echo "Endpoint: ${WS_BASE}/ws/stt?session=…&token=<redacted>"

if [[ -z "${AUDIO_FILE}" ]]; then
  # ── Interactive mode ────────────────────────────────────────────────────────
  echo "Mode    : interactive (stdin → WS). Press Ctrl+C to disconnect."
  echo "------------------------------------------------------------------------"
  exec websocat --binary -v --no-close "${WS_URL}"

else
  # ── File streaming mode ─────────────────────────────────────────────────────
  [[ -f "${AUDIO_FILE}" ]] || { echo "ERROR: file not found: ${AUDIO_FILE}" >&2; exit 1; }

  BYTES=$(wc -c < "${AUDIO_FILE}" | tr -d ' ')
  DURATION_S=$(echo "scale=1; ${BYTES} / 32000" | bc)
  echo "File    : ${AUDIO_FILE} (${BYTES} bytes ≈ ${DURATION_S}s)"
  echo "Mode    : file streaming at real-time rate (~32 KB/s)"
  echo "------------------------------------------------------------------------"

  if command -v pv &>/dev/null; then
    # pv: real-time rate limiting, progress bar
    pv -L 32000 -s "${BYTES}" "${AUDIO_FILE}" | websocat --binary --no-close "${WS_URL}"
  elif command -v python3 &>/dev/null; then
    # Python fallback: reads 4096-byte chunks (128 ms) with 128 ms sleep
    python3 - "${AUDIO_FILE}" "${WS_URL}" <<'PYEOF'
import sys, subprocess, time

audio_file, ws_url = sys.argv[1], sys.argv[2]
CHUNK = 4096
SLEEP = 0.128   # 128 ms = 4096 bytes at 16kHz/16-bit/mono real-time

proc = subprocess.Popen(
    ["websocat", "--binary", "--no-close", ws_url],
    stdin=subprocess.PIPE
)
with open(audio_file, "rb") as f:
    while chunk := f.read(CHUNK):
        proc.stdin.write(chunk)
        proc.stdin.flush()
        time.sleep(SLEEP)
proc.stdin.close()
proc.wait()
PYEOF
  else
    echo "WARNING: pv and python3 not found — streaming without rate limiting." >&2
    # shellcheck disable=SC2002
    cat "${AUDIO_FILE}" | websocat --binary --no-close "${WS_URL}"
  fi

  echo ""
  echo "Stream complete. Segments appear in:"
  echo "  DB  : SELECT * FROM transcript_segments WHERE session_id = '${SESSION_ID}';"
  echo "  STOMP: /topic/discovery/sessions/${SESSION_ID}/segments"
fi
