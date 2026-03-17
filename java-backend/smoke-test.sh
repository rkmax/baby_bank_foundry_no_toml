#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
PORT="${BABY_BANK_TEST_PORT:-18080}"
SERVER_LOG="$SCRIPT_DIR/build/server.log"

mkdir -p "$SCRIPT_DIR/build"

BABY_BANK_PORT="$PORT" "$SCRIPT_DIR/run.sh" >"$SERVER_LOG" 2>&1 &
SERVER_PID=$!

cleanup() {
  kill "$SERVER_PID" 2>/dev/null || true
  wait "$SERVER_PID" 2>/dev/null || true
}

trap cleanup EXIT INT TERM

wait_for_server() {
  attempts=0
  while [ "$attempts" -lt 50 ]; do
    if curl -fsS "http://127.0.0.1:$PORT/api/health" >/dev/null 2>&1; then
      return 0
    fi
    attempts=$((attempts + 1))
    sleep 0.2
  done
  echo "Server did not start. See $SERVER_LOG" >&2
  return 1
}

wait_for_server

curl -fsS "http://127.0.0.1:$PORT/api/state" >/dev/null
curl -fsS "http://127.0.0.1:$PORT/api/contract" >/dev/null
curl -fsS "http://127.0.0.1:$PORT/api/findings" >/dev/null
curl -fsS \
  -H 'Content-Type: application/json' \
  -d '{"alias":"alice"}' \
  "http://127.0.0.1:$PORT/api/signup" >/dev/null
curl -fsS \
  -H 'Content-Type: application/json' \
  -d '{"confirmAlias":"alice","lockBlocks":10,"amountEth":1.5}' \
  "http://127.0.0.1:$PORT/api/deposit" >/dev/null
curl -fsS \
  -H 'Content-Type: application/json' \
  -d '{"amount":10}' \
  "http://127.0.0.1:$PORT/api/advance-blocks" >/dev/null
curl -fsS \
  -H 'Content-Type: application/json' \
  -d '{}' \
  "http://127.0.0.1:$PORT/api/withdraw" >/dev/null

echo "Smoke test passed on port $PORT."
