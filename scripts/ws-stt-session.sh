#!/usr/bin/env bash
# =============================================================================
# ws-stt-session.sh — Manage the REST lifecycle of a discovery session
#
# USAGE
#   ./scripts/ws-stt-session.sh <ACTION> [SESSION_ID]
#
# ACTIONS
#   create              Create a new DRAFT session and print its UUID
#   start  <SESSION_ID> DRAFT → RECORDING  (open /ws/stt after this)
#   pause  <SESSION_ID> RECORDING → PAUSED  (server closes WS automatically)
#   resume <SESSION_ID> PAUSED → RECORDING  (open a new /ws/stt after this)
#   stop   <SESSION_ID> RECORDING|PAUSED → STOPPED  (server closes WS automatically)
#
# ENVIRONMENT
#   REQSAI_TOKEN        JWT bearer token (required)
#   REQSAI_PROJECT_ID   project UUID (required for 'create')
#   REQSAI_BASE_URL     base URL, default http://localhost:8080
#   REQSAI_LANG         session language for 'create', default 'en'
#   REQSAI_TITLE        session title for 'create', default 'ws-stt-test-<timestamp>'
#
# EXAMPLES
#   export REQSAI_TOKEN="eyJhbG..."
#   export REQSAI_PROJECT_ID="019ee12b-..."
#
#   SESSION=$(./scripts/ws-stt-session.sh create)
#   ./scripts/ws-stt-session.sh start  "$SESSION"
#   ./scripts/ws-stt-session.sh pause  "$SESSION"
#   ./scripts/ws-stt-session.sh resume "$SESSION"
#   ./scripts/ws-stt-session.sh stop   "$SESSION"
# =============================================================================
set -euo pipefail

ACTION="${1:?Usage: $0 <create|start|pause|resume|stop> [SESSION_ID]}"
SESSION_ID="${2:-}"

TOKEN="${REQSAI_TOKEN:?Set REQSAI_TOKEN env var}"
BASE_URL="${REQSAI_BASE_URL:-http://localhost:8080}"
PROJECT_ID="${REQSAI_PROJECT_ID:-}"
LANG="${REQSAI_LANG:-en}"
TITLE="${REQSAI_TITLE:-ws-stt-test-$(date +%s)}"

API=(-s
  --max-time 10
  -H "Authorization: Bearer ${TOKEN}"
  -H "Content-Type: application/json"
  -H "Api-Version: 1"
)

TRANSITIONS=(
  ["start"]="DRAFT → RECORDING"
  ["pause"]="RECORDING → PAUSED  (WS closed by server)"
  ["resume"]="PAUSED → RECORDING  (open a new WS now)"
  ["stop"]="RECORDING|PAUSED → STOPPED  (WS closed by server)"
)

case "${ACTION}" in
  create)
    [[ -n "${PROJECT_ID}" ]] || { echo "ERROR: set REQSAI_PROJECT_ID for 'create'" >&2; exit 1; }
    HTTP=$(curl "${API[@]}" -o /tmp/.stt-session-resp -w "%{http_code}" -X POST \
      "${BASE_URL}/api/projects/${PROJECT_ID}/sessions" \
      -d "{\"title\":\"${TITLE}\",\"language\":\"${LANG}\"}")
    RESP=$(cat /tmp/.stt-session-resp)
    if [[ "${HTTP}" != 2* ]]; then
      echo "ERROR: HTTP ${HTTP} — ${RESP}" >&2; exit 1
    fi
    ID=$(echo "${RESP}" | jq -r '.id // empty')
    [[ -n "${ID}" ]] || { echo "ERROR: could not parse session id — ${RESP}" >&2; exit 1; }
    STATUS=$(echo "${RESP}" | jq -r '.status // "DRAFT"')
    echo "Created : ${ID}"
    echo "Status  : ${STATUS}"
    echo "${ID}"
    ;;

  start|pause|resume|stop)
    [[ -n "${SESSION_ID}" ]] || { echo "ERROR: $0 ${ACTION} requires SESSION_ID" >&2; exit 1; }
    [[ -n "${PROJECT_ID}" ]] || { echo "ERROR: set REQSAI_PROJECT_ID" >&2; exit 1; }
    HTTP=$(curl "${API[@]}" -o /tmp/.stt-session-resp -w "%{http_code}" -X POST \
      "${BASE_URL}/api/projects/${PROJECT_ID}/sessions/${SESSION_ID}/${ACTION}" \
      -d '{}')
    RESP=$(cat /tmp/.stt-session-resp)
    if [[ "${HTTP}" != 2* ]]; then
      echo "ERROR: HTTP ${HTTP} — ${RESP}" >&2; exit 1
    fi
    STATUS=$(echo "${RESP}" | jq -r '.status // empty')
    echo "Action  : ${ACTION}"
    echo "Session : ${SESSION_ID}"
    echo "State   : ${TRANSITIONS[$ACTION]:-}"
    [[ -n "${STATUS}" ]] && echo "Confirmed: ${STATUS}"
    ;;

  *)
    echo "ERROR: unknown action '${ACTION}'. Use: create | start | pause | resume | stop" >&2
    exit 1
    ;;
esac
