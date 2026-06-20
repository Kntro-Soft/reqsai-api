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

API=(-s -f
  -H "Authorization: Bearer ${TOKEN}"
  -H "Content-Type: application/json"
  -H "Api-Version: 1"
)

case "${ACTION}" in
  create)
    [[ -n "${PROJECT_ID}" ]] || { echo "ERROR: set REQSAI_PROJECT_ID for 'create'" >&2; exit 1; }
    RESP=$(curl "${API[@]}" -X POST \
      "${BASE_URL}/api/projects/${PROJECT_ID}/sessions" \
      -d "{\"title\":\"${TITLE}\",\"language\":\"${LANG}\"}")
    ID=$(echo "${RESP}" | jq -r '.id // empty')
    [[ -n "${ID}" ]] || { echo "ERROR: could not parse session id — ${RESP}" >&2; exit 1; }
    echo "${ID}"
    ;;

  start|pause|resume|stop)
    [[ -n "${SESSION_ID}" ]] || { echo "ERROR: $0 ${ACTION} requires SESSION_ID" >&2; exit 1; }
    [[ -n "${PROJECT_ID}" ]] || { echo "ERROR: set REQSAI_PROJECT_ID" >&2; exit 1; }
    # Map action → endpoint name
    case "${ACTION}" in
      start)  ENDPOINT="start"  ;;
      pause)  ENDPOINT="pause"  ;;
      resume) ENDPOINT="resume" ;;
      stop)   ENDPOINT="stop"   ;;
    esac
    curl "${API[@]}" -X POST \
      "${BASE_URL}/api/projects/${PROJECT_ID}/sessions/${SESSION_ID}/${ENDPOINT}" \
      -d '{}' > /dev/null
    echo "${ACTION}: ${SESSION_ID}"
    ;;

  *)
    echo "ERROR: unknown action '${ACTION}'. Use: create | start | pause | resume | stop" >&2
    exit 1
    ;;
esac
