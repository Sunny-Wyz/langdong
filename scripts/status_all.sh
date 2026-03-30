#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="/Users/weiyaozhou/Documents/langdong"
RUN_DIR="$ROOT_DIR/.run"

check_pid() {
  local name="$1"
  local pid_file="$2"
  if [[ -f "$pid_file" ]]; then
    local pid
    pid="$(cat "$pid_file")"
    if ps -p "$pid" >/dev/null 2>&1; then
      echo "[UP]   $name pid=$pid"
      return
    fi
  fi
  echo "[DOWN] $name"
}

check_http() {
  local name="$1"
  local url="$2"
  if curl -sS -o /dev/null "$url" >/dev/null 2>&1; then
    echo "[UP]   $name health"
  else
    echo "[DOWN] $name health"
  fi
}

check_pid "python-api" "$RUN_DIR/python-api.pid"
check_pid "celery" "$RUN_DIR/celery.pid"
check_pid "backend" "$RUN_DIR/backend.pid"
check_pid "frontend" "$RUN_DIR/frontend.pid"

if command -v redis-cli >/dev/null 2>&1 && redis-cli -p 6379 ping >/dev/null 2>&1; then
  echo "[UP]   redis"
else
  echo "[DOWN] redis"
fi

check_http "python-api" "http://localhost:8001/health"
check_http "backend" "http://localhost:8080/"
