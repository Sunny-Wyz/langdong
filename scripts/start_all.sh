#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="/Users/weiyaozhou/Documents/langdong"
RUN_DIR="$ROOT_DIR/.run"
mkdir -p "$RUN_DIR"

load_local_env() {
  local env_file="$ROOT_DIR/.env.local"
  if [[ -f "$env_file" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    set +a
  fi
}

load_local_env

CALLBACK_TOKEN="${PYTHON_CALLBACK_TOKEN:-langdong-local-callback-token-change-me}"
if [[ "$CALLBACK_TOKEN" == "langdong-local-callback-token-change-me" ]]; then
  echo "[WARN] Using default callback token for local dev. Set PYTHON_CALLBACK_TOKEN for safer setup."
fi

start_redis() {
  if command -v redis-cli >/dev/null 2>&1 && redis-cli -p 6379 ping >/dev/null 2>&1; then
    echo "[OK] Redis already running on 6379"
    return
  fi

  if command -v redis-server >/dev/null 2>&1; then
    echo "[INFO] Starting Redis via redis-server"
    redis-server --daemonize yes --port 6379 --pidfile "$RUN_DIR/redis.pid" --logfile "$RUN_DIR/redis.log"
    sleep 1
    echo "[OK] Redis started"
    return
  fi

  if command -v docker >/dev/null 2>&1; then
    echo "[INFO] Starting Redis via Docker"
    docker rm -f langdong-redis >/dev/null 2>&1 || true
    docker run -d --name langdong-redis -p 6379:6379 redis:7 >/dev/null
    echo "[OK] Redis container started"
    return
  fi

  echo "[ERROR] Redis not available. Install redis-server or docker."
  exit 1
}

start_python_api() {
  echo "[INFO] Starting Python API"
  cd "$ROOT_DIR/python-ai-service"
  JAVA_CALLBACK_TOKEN="$CALLBACK_TOKEN" \
  conda run -n langdong python -m uvicorn app.main:app --host 0.0.0.0 --port 8001 \
    > "$RUN_DIR/python-api.log" 2>&1 &
  echo $! > "$RUN_DIR/python-api.pid"
  sleep 2
  echo "[OK] Python API started"
}

start_celery_worker() {
  echo "[INFO] Starting Celery worker"
  cd "$ROOT_DIR/python-ai-service"
  JAVA_CALLBACK_TOKEN="$CALLBACK_TOKEN" \
  conda run -n langdong python -m celery -A app.services.celery_app:celery_app worker -l info \
    > "$RUN_DIR/celery.log" 2>&1 &
  echo $! > "$RUN_DIR/celery.pid"
  sleep 2
  echo "[OK] Celery worker started"
}

start_backend() {
  echo "[INFO] Starting Spring Boot backend"
  cd "$ROOT_DIR/backend"
  PYTHON_CALLBACK_TOKEN="$CALLBACK_TOKEN" \
  mvn spring-boot:run > "$RUN_DIR/backend.log" 2>&1 &
  echo $! > "$RUN_DIR/backend.pid"
  sleep 3
  echo "[OK] Backend started"
}

start_frontend() {
  echo "[INFO] Starting Vue frontend"
  cd "$ROOT_DIR/frontend"
  if [[ ! -d node_modules ]]; then
    echo "[INFO] node_modules missing, running npm install"
    npm install > "$RUN_DIR/frontend-npm-install.log" 2>&1
  fi
  npm run serve > "$RUN_DIR/frontend.log" 2>&1 &
  echo $! > "$RUN_DIR/frontend.pid"
  sleep 3
  echo "[OK] Frontend started"
}

start_redis
start_python_api
start_celery_worker
start_backend
start_frontend

echo ""
echo "[DONE] Stack startup complete"
echo "[INFO] Logs directory: $RUN_DIR"
echo "[INFO] Run scripts/status_all.sh to check health"
