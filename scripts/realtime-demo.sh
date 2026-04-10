#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PARENT_DIR="$(cd "$SERVER_DIR/.." && pwd)"

SERVER_PORT="${CORAL_SERVER_PORT:-5555}"
FRONTEND_PORT="${CORAL_FRONTEND_PORT:-5173}"
CORAL_TOKEN="${CORAL_TOKEN:-test}"
NAMESPACE="${CORAL_NAMESPACE:-default}"
DEMO_SUBJECT="${DEMO_SUBJECT:-Roman Abramovich}"
RISK_AGENT_PROMPT="${RISK_AGENT_PROMPT:-Aggregate sanctions, pep, and adverse media outputs and return one final compliance decision in strict JSON.}"
TAIL_LOGS=1

usage() {
  cat <<USAGE
Usage: $(basename "$0") [options]

Options:
  --subject "NAME"      Subject/entity to screen (default: "$DEMO_SUBJECT")
  --namespace "NAME"    Session namespace (default: "$NAMESPACE")
  --no-tail              Keep processes running but do not stream logs
  --help                 Show this message

Required environment variables:
  OPENAI_API_KEY
  DILISENSE_API_KEY
  TAVILY_API_KEY

Optional environment variables:
  CORAL_TOKEN            API token for Coral server (default: test)
  CORAL_SERVER_PORT      Coral server port (default: 5555)
  CORAL_FRONTEND_PORT    React frontend port (default: 5173)
  FRONTEND_DIR           Path to coral-demoreact (default: ../coral-demoreact)
  DEMO_TMP_DIR           Working directory for generated files/logs
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --subject)
      DEMO_SUBJECT="${2:-}"
      shift 2
      ;;
    --namespace)
      NAMESPACE="${2:-}"
      shift 2
      ;;
    --no-tail)
      TAIL_LOGS=0
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 1
  fi
}

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required environment variable: $name" >&2
    exit 1
  fi
}

find_agent_dir() {
  local pattern="$1"
  local dir
  dir="$(find "$PARENT_DIR" -maxdepth 1 -mindepth 1 -type d -name "$pattern" | head -n 1 || true)"
  if [[ -z "$dir" ]]; then
    echo "Could not find agent directory matching pattern: $pattern" >&2
    exit 1
  fi
  printf '%s' "$dir"
}

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

wait_for_http() {
  local name="$1"
  local timeout_secs="$2"
  local process_id="$3"
  local url="$4"
  shift 4

  local start
  start="$(date +%s)"

  while true; do
    if curl -fsS "$@" "$url" >/dev/null 2>&1; then
      return 0
    fi

    if ! kill -0 "$process_id" >/dev/null 2>&1; then
      echo "$name process exited early. Check logs in $DEMO_TMP_DIR" >&2
      exit 1
    fi

    local now
    now="$(date +%s)"
    if (( now - start >= timeout_secs )); then
      echo "Timed out waiting for $name at $url" >&2
      exit 1
    fi

    sleep 1
  done
}

wait_for_registry_agents() {
  local timeout_secs="$1"
  local start
  start="$(date +%s)"

  while true; do
    local registry_json
    registry_json="$(curl -fsS -H "Authorization: Bearer $CORAL_TOKEN" "$CORAL_HTTP_BASE/api/v1/registry" || true)"

    if [[ -n "$registry_json" ]] \
      && grep -q '"name"[[:space:]]*:[[:space:]]*"coral-pep-agent"' <<<"$registry_json" \
      && grep -q '"name"[[:space:]]*:[[:space:]]*"coral-am-agent"' <<<"$registry_json" \
      && grep -q '"name"[[:space:]]*:[[:space:]]*"coral-sanctions-agent"' <<<"$registry_json" \
      && grep -q '"name"[[:space:]]*:[[:space:]]*"coral-rs-agent"' <<<"$registry_json"; then
      return 0
    fi

    local now
    now="$(date +%s)"
    if (( now - start >= timeout_secs )); then
      echo "Timed out waiting for all 4 agents to appear in registry." >&2
      echo "Current registry payload is in $REGISTRY_SNAPSHOT_FILE" >&2
      printf '%s\n' "$registry_json" > "$REGISTRY_SNAPSHOT_FILE"
      exit 1
    fi

    sleep 1
  done
}

cleanup() {
  trap - EXIT INT TERM

  if [[ -n "${FRONTEND_PID:-}" ]] && kill -0 "$FRONTEND_PID" >/dev/null 2>&1; then
    kill "$FRONTEND_PID" >/dev/null 2>&1 || true
  fi

  if [[ -n "${SERVER_PID:-}" ]] && kill -0 "$SERVER_PID" >/dev/null 2>&1; then
    kill "$SERVER_PID" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT INT TERM

require_cmd curl
require_cmd sed
require_cmd npm
require_cmd find
require_cmd head
require_env OPENAI_API_KEY
require_env DILISENSE_API_KEY
require_env TAVILY_API_KEY

FRONTEND_DIR="${FRONTEND_DIR:-$PARENT_DIR/coral-demoreact}"
if [[ ! -d "$FRONTEND_DIR" ]]; then
  echo "Frontend directory not found: $FRONTEND_DIR" >&2
  exit 1
fi

PEP_AGENT_DIR="$(find_agent_dir 'coral-PEP-agent')"
AM_AGENT_DIR="$(find_agent_dir 'coral-AM-agent')"
SANCTIONS_AGENT_DIR="$(find_agent_dir 'coral-sanctions-agent')"
RS_AGENT_DIR="$(find_agent_dir 'coral-RS-agent*')"

DEMO_TMP_DIR="${DEMO_TMP_DIR:-$(mktemp -d /tmp/coral-realtime-demo.XXXXXX)}"
CONFIG_FILE="$DEMO_TMP_DIR/demo-config.toml"
SESSION_PAYLOAD_FILE="$DEMO_TMP_DIR/session-request.json"
SERVER_LOG="$DEMO_TMP_DIR/server.log"
FRONTEND_LOG="$DEMO_TMP_DIR/frontend.log"
REGISTRY_SNAPSHOT_FILE="$DEMO_TMP_DIR/registry.json"

CORAL_HTTP_BASE="http://127.0.0.1:${SERVER_PORT}"
CORAL_WS_BASE="ws://127.0.0.1:${SERVER_PORT}"
FRONTEND_BASE="http://127.0.0.1:${FRONTEND_PORT}"

cat > "$CONFIG_FILE" <<CONFIG
[auth]
keys = ["$CORAL_TOKEN"]

[network]
allow_any_host = true
bind_address = "0.0.0.0"
bind_port = $SERVER_PORT

[registry]
local_agents = [
  "$PEP_AGENT_DIR",
  "$AM_AGENT_DIR",
  "$SANCTIONS_AGENT_DIR",
  "$RS_AGENT_DIR"
]
watch_local_agents = true
local_agent_rescan_timer = "5s"
include_debug_agents = false
CONFIG

(
  cd "$SERVER_DIR"
  CONFIG_FILE_PATH="$CONFIG_FILE" ./gradlew run > "$SERVER_LOG" 2>&1
) &
SERVER_PID=$!

wait_for_http "Coral server" 120 "$SERVER_PID" "$CORAL_HTTP_BASE/api_v1.json"

# API requires auth, validate token now.
curl -fsS -H "Authorization: Bearer $CORAL_TOKEN" "$CORAL_HTTP_BASE/api/v1/registry" >/dev/null

(
  cd "$FRONTEND_DIR"
  VITE_CORAL_HTTP_URL="$CORAL_HTTP_BASE" \
  VITE_CORAL_WS_URL="$CORAL_WS_BASE" \
  VITE_CORAL_TOKEN="$CORAL_TOKEN" \
  VITE_CORAL_NAMESPACE="$NAMESPACE" \
  npm run dev -- --host 127.0.0.1 --port "$FRONTEND_PORT" > "$FRONTEND_LOG" 2>&1
) &
FRONTEND_PID=$!

wait_for_http "React frontend" 90 "$FRONTEND_PID" "$FRONTEND_BASE"
wait_for_registry_agents 90

SUBJECT_JSON="$(json_escape "$DEMO_SUBJECT")"
OPENAI_JSON="$(json_escape "$OPENAI_API_KEY")"
DILISENSE_JSON="$(json_escape "$DILISENSE_API_KEY")"
TAVILY_JSON="$(json_escape "$TAVILY_API_KEY")"
RISK_PROMPT_JSON="$(json_escape "$RISK_AGENT_PROMPT")"
NAMESPACE_JSON="$(json_escape "$NAMESPACE")"

cat > "$SESSION_PAYLOAD_FILE" <<PAYLOAD
{
  "agentGraphRequest": {
    "agents": [
      {
        "id": {
          "name": "coral-pep-agent",
          "version": "0.1.0",
          "registrySourceId": { "type": "local" }
        },
        "name": "coral-pep-agent",
        "provider": { "type": "local", "runtime": "executable" },
        "options": {
          "EXTRA_INITIAL_USER_PROMPT": { "type": "string", "value": "$SUBJECT_JSON" },
          "MODEL_API_KEY": { "type": "string", "value": "$OPENAI_JSON" },
          "DILISENSE_API_KEY": { "type": "string", "value": "$DILISENSE_JSON" }
        }
      },
      {
        "id": {
          "name": "coral-am-agent",
          "version": "0.1.0",
          "registrySourceId": { "type": "local" }
        },
        "name": "coral-am-agent",
        "provider": { "type": "local", "runtime": "executable" },
        "options": {
          "EXTRA_INITIAL_USER_PROMPT": { "type": "string", "value": "$SUBJECT_JSON" },
          "MODEL_API_KEY": { "type": "string", "value": "$OPENAI_JSON" },
          "TAVILY_API_KEY": { "type": "string", "value": "$TAVILY_JSON" }
        }
      },
      {
        "id": {
          "name": "coral-sanctions-agent",
          "version": "0.1.0",
          "registrySourceId": { "type": "local" }
        },
        "name": "coral-sanctions-agent",
        "provider": { "type": "local", "runtime": "executable" },
        "options": {
          "EXTRA_INITIAL_USER_PROMPT": { "type": "string", "value": "$SUBJECT_JSON" },
          "MODEL_API_KEY": { "type": "string", "value": "$OPENAI_JSON" },
          "DILISENSE_API_KEY": { "type": "string", "value": "$DILISENSE_JSON" },
          "TAVILY_API_KEY": { "type": "string", "value": "$TAVILY_JSON" }
        }
      },
      {
        "id": {
          "name": "coral-rs-agent",
          "version": "0.1.0",
          "registrySourceId": { "type": "local" }
        },
        "name": "coral-rs-agent",
        "provider": { "type": "local", "runtime": "executable" },
        "options": {
          "EXTRA_INITIAL_USER_PROMPT": { "type": "string", "value": "$RISK_PROMPT_JSON" },
          "MODEL_API_KEY": { "type": "string", "value": "$OPENAI_JSON" }
        }
      }
    ],
    "groups": [],
    "customTools": {}
  },
  "namespaceProvider": {
    "type": "create_if_not_exists",
    "namespaceRequest": {
      "name": "$NAMESPACE_JSON",
      "annotations": {},
      "deleteOnLastSessionExit": false
    }
  },
  "execution": {
    "mode": "immediate",
    "runtimeSettings": {
      "ttl": 1800000,
      "extendedEndReport": true
    }
  }
}
PAYLOAD

SESSION_RESPONSE="$(curl -fsS \
  -X POST "$CORAL_HTTP_BASE/api/v1/local/session" \
  -H "Authorization: Bearer $CORAL_TOKEN" \
  -H "Content-Type: application/json" \
  --data @"$SESSION_PAYLOAD_FILE")"

SESSION_ID="$(printf '%s' "$SESSION_RESPONSE" | sed -n 's/.*"sessionId"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
SESSION_NAMESPACE="$(printf '%s' "$SESSION_RESPONSE" | sed -n 's/.*"namespace"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"

if [[ -z "$SESSION_ID" || -z "$SESSION_NAMESPACE" ]]; then
  echo "Failed to parse session creation response:" >&2
  echo "$SESSION_RESPONSE" >&2
  exit 1
fi

echo
echo "Realtime demo is live."
echo "Frontend: $FRONTEND_BASE"
echo "Server:   $CORAL_HTTP_BASE"
echo "Session:  $SESSION_NAMESPACE/$SESSION_ID"
echo "Subject:  $DEMO_SUBJECT"
echo "Logs:     $DEMO_TMP_DIR"
echo
echo "Press Ctrl+C to stop both backend and frontend."
echo

if (( TAIL_LOGS == 1 )); then
  tail -n 25 -f "$SERVER_LOG" "$FRONTEND_LOG"
else
  while true; do
    sleep 3600
  done
fi
