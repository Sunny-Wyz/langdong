#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="/Users/weiyaozhou/Documents/langdong"
RUN_DIR="$ROOT_DIR/.run"

stop_by_pidfile() {
  local name="$1"
  local pid_file="$2"

  if [[ -f "$pid_file" ]]; then
    local pid
    pid="$(cat "$pid_file")"
    if ps -p "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
      sleep 1
      if ps -p "$pid" >/dev/null 2>&1; then
        kill -9 "$pid" >/dev/null 2>&1 || true
      fi
      echo "[OK] Stopped $name (pid=$pid)"
    else
      echo "[INFO] $name already stopped"
    fi
    rm -f "$pid_file"
  else
    echo "[INFO] No pid file for $name"
  fi
}

stop_by_port() {
  local name="$1"
  local port="$2"
  local pids=""
  pids="$(lsof -ti tcp:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "$pids" ]]; then
    echo "$pids" | xargs kill >/dev/null 2>&1 || true
    sleep 1
    pids="$(lsof -ti tcp:"$port" -sTCP:LISTEN 2>/dev/null || true)"
    if [[ -n "$pids" ]]; then
      echo "$pids" | xargs kill -9 >/dev/null 2>&1 || true
    fi
    echo "[OK] Cleared $name on port $port"
  fi
}

stop_by_pidfile "frontend" "$RUN_DIR/frontend.pid"
stop_by_pidfile "backend" "$RUN_DIR/backend.pid"
stop_by_pidfile "celery" "$RUN_DIR/celery.pid"
stop_by_pidfile "python-api" "$RUN_DIR/python-api.pid"

# 停掉 supervisord 管理的 Python 进程
SUPERVISOR_CONF="$ROOT_DIR/python-ai-service/supervisord.conf"
SUPERVISOR_SOCK="$ROOT_DIR/python-ai-service/logs/supervisor.sock"
if [[ -S "$SUPERVISOR_SOCK" ]]; then
  echo "[INFO] Shutting down supervisord"
  conda run -n langdong supervisorctl -c "$SUPERVISOR_CONF" shutdown >/dev/null 2>&1 || true
  sleep 2
  echo "[OK] supervisord stopped"
fi

pkill -f "spring-boot:run" >/dev/null 2>&1 || true
pkill -f "app.main:app --host 0.0.0.0 --port 8001" >/dev/null 2>&1 || true
pkill -f "celery -A app.services.celery_app:celery_app worker" >/dev/null 2>&1 || true
pkill -f "supervisord.*supervisord.conf" >/dev/null 2>&1 || true

stop_by_port "backend" "8080"
stop_by_port "python-api" "8001"
stop_by_port "frontend" "3000"
stop_by_port "frontend" "3001"

if command -v redis-cli >/dev/null 2>&1 && redis-cli -p 6379 ping >/dev/null 2>&1; then
  redis-cli -p 6379 shutdown >/dev/null 2>&1 || true
  echo "[OK] Redis shutdown requested"
fi

if command -v docker >/dev/null 2>&1; then
  docker rm -f langdong-redis >/dev/null 2>&1 || true
fi

echo "[DONE] Stack stopped"
