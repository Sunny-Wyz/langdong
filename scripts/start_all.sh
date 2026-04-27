#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="/Users/weiyaozhou/Documents/langdong"
RUN_DIR="$ROOT_DIR/.run"
mkdir -p "$RUN_DIR"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"
FRONTEND_FALLBACK_PORT="${FRONTEND_FALLBACK_PORT:-3001}"
MAVEN_HOME_CANDIDATE="${MAVEN_HOME:-/Users/weiyaozhou/IdeaProjects/apache-maven-3.8.8-bin}"
MVN_CMD=""

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
DB_USERNAME_VALUE="${DB_USERNAME:-}"
DB_PASSWORD_VALUE="${DB_PASSWORD:-}"
DB_HOST_VALUE="${DB_HOST:-localhost}"
DB_PORT_VALUE="${DB_PORT:-3306}"
DB_NAME_VALUE="${DB_NAME:-spare_db}"
if [[ "$CALLBACK_TOKEN" == "langdong-local-callback-token-change-me" ]]; then
  echo "[WARN] Using default callback token for local dev. Set PYTHON_CALLBACK_TOKEN for safer setup."
fi

if [[ -z "$DB_USERNAME_VALUE" || -z "$DB_PASSWORD_VALUE" ]]; then
  echo "[ERROR] Missing DB credentials. Set DB_USERNAME and DB_PASSWORD in .env.local or shell environment."
  exit 1
fi

require_command() {
  local cmd="$1"
  local message="$2"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[ERROR] $message"
    exit 1
  fi
}

resolve_mvn_command() {
  local candidate
  local candidates=(
    "${MVN_BIN:-}"
    "${MAVEN_HOME_CANDIDATE}/bin/mvn"
    "${MAVEN_HOME_CANDIDATE}/apache-maven-3.8.8/bin/mvn"
    "/Users/weiyaozhou/IdeaProjects/apache-maven-3.8.8/bin/mvn"
  )

  for candidate in "${candidates[@]}"; do
    if [[ -n "$candidate" && -x "$candidate" ]]; then
      MVN_CMD="$candidate"
      return 0
    fi
  done

  if command -v mvn >/dev/null 2>&1; then
    MVN_CMD="$(command -v mvn)"
    return 0
  fi

  echo "[ERROR] mvn not found. Set MVN_BIN or MAVEN_HOME, or add Maven to PATH first."
  exit 1
}

wait_for_http() {
  local name="$1"
  local url="$2"
  local timeout="${3:-30}"
  local elapsed=0

  while (( elapsed < timeout )); do
    local status
    status="$(curl -sS -o /dev/null -w '%{http_code}' "$url" || true)"
    if [[ "$status" != "000" ]]; then
      echo "[OK] $name is reachable at $url (HTTP $status)"
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done

  echo "[ERROR] $name did not become reachable at $url within ${timeout}s"
  return 1
}

port_listening() {
  local port="$1"
  lsof -ti tcp:"$port" -sTCP:LISTEN >/dev/null 2>&1
}

ensure_mysql() {
  require_command "mysqladmin" "mysqladmin not found. Install MySQL client tools first."
  if mysqladmin --protocol=tcp -h "$DB_HOST_VALUE" -P "$DB_PORT_VALUE" -u "$DB_USERNAME_VALUE" -p"$DB_PASSWORD_VALUE" ping >/dev/null 2>&1; then
    echo "[OK] MySQL is reachable at ${DB_HOST_VALUE}:${DB_PORT_VALUE}"
    return
  fi

  echo "[ERROR] MySQL is not reachable at ${DB_HOST_VALUE}:${DB_PORT_VALUE}. Start MySQL and confirm DB_USERNAME/DB_PASSWORD/DB_HOST/DB_PORT in .env.local."
  exit 1
}

ensure_local_dependencies() {
  require_command "curl" "curl not found. Install curl first."
  require_command "lsof" "lsof not found. Install lsof first."
  require_command "npm" "npm not found. Install Node.js and npm first."
  require_command "conda" "conda not found. Install Conda or add it to PATH first."
  resolve_mvn_command
}

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

start_python_services() {
  echo "[INFO] Starting Python services (FastAPI + Celery)"
  cd "$ROOT_DIR/python-ai-service"
  mkdir -p logs

  export DB_USERNAME="$DB_USERNAME_VALUE"
  export DB_PASSWORD="$DB_PASSWORD_VALUE"
  export DB_HOST="$DB_HOST_VALUE"
  export DB_PORT="$DB_PORT_VALUE"
  export DB_NAME="$DB_NAME_VALUE"
  export JAVA_CALLBACK_TOKEN="$CALLBACK_TOKEN"
  export PYTHONPATH="$ROOT_DIR${PYTHONPATH:+:$PYTHONPATH}"

  local SUPERVISOR_CONF="$ROOT_DIR/python-ai-service/supervisord.conf"
  local SUPERVISOR_SOCK="$ROOT_DIR/python-ai-service/logs/supervisor.sock"

  # 检测 conda 环境内是否有 supervisord
  if conda run -n langdong supervisord --version >/dev/null 2>&1; then
    # 如果旧的 supervisord 还在跑，先停掉
    if [[ -S "$SUPERVISOR_SOCK" ]]; then
      echo "[INFO] Stopping existing supervisord instance"
      conda run -n langdong supervisorctl -c "$SUPERVISOR_CONF" shutdown >/dev/null 2>&1 || true
      sleep 2
    fi

    conda run -n langdong supervisord -c "$SUPERVISOR_CONF"
    sleep 3
    echo "[OK] Python services started via supervisord (auto-restart enabled)"
  else
    echo "[WARN] supervisord not found in conda env, falling back to direct process launch"
    nohup conda run -n langdong python -m uvicorn app.main:app --host 0.0.0.0 --port 8001 \
      < /dev/null \
      > "$RUN_DIR/python-api.log" 2>&1 &
    echo $! > "$RUN_DIR/python-api.pid"
    sleep 2
    echo "[OK] Python API started (no auto-restart)"

    nohup conda run -n langdong python -m celery -A app.services.celery_app:celery_app worker -l info \
      < /dev/null \
      > "$RUN_DIR/celery.log" 2>&1 &
    echo $! > "$RUN_DIR/celery.pid"
    sleep 2
    echo "[OK] Celery worker started (no auto-restart)"
  fi
}

start_backend() {
  echo "[INFO] Starting Spring Boot backend"
  cd "$ROOT_DIR/backend"
  APP_CORS_ALLOWED_ORIGINS="${APP_CORS_ALLOWED_ORIGINS:-http://localhost:${FRONTEND_PORT},http://127.0.0.1:${FRONTEND_PORT},http://localhost:${FRONTEND_FALLBACK_PORT},http://127.0.0.1:${FRONTEND_FALLBACK_PORT}}" \
  SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://${DB_HOST_VALUE}:${DB_PORT_VALUE}/${DB_NAME_VALUE}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}" \
  DB_USERNAME="$DB_USERNAME_VALUE" \
  DB_PASSWORD="$DB_PASSWORD_VALUE" \
  PYTHON_CALLBACK_TOKEN="$CALLBACK_TOKEN" \
  nohup "$MVN_CMD" spring-boot:run < /dev/null > "$RUN_DIR/backend.log" 2>&1 &
  echo $! > "$RUN_DIR/backend.pid"
  wait_for_http "backend" "http://localhost:8080/" 45
}

start_frontend() {
  echo "[INFO] Starting Vue frontend"
  cd "$ROOT_DIR/frontend"
  if [[ ! -d node_modules ]]; then
    echo "[INFO] node_modules missing, running npm install"
    npm install > "$RUN_DIR/frontend-npm-install.log" 2>&1
  fi
  if port_listening "$FRONTEND_PORT"; then
    echo "[WARN] Port ${FRONTEND_PORT} already in use. Vue may switch to ${FRONTEND_FALLBACK_PORT}."
  fi
  nohup npm run serve < /dev/null > "$RUN_DIR/frontend.log" 2>&1 &
  echo $! > "$RUN_DIR/frontend.pid"
  sleep 5

  if port_listening "$FRONTEND_PORT"; then
    echo "[OK] Frontend started at http://localhost:${FRONTEND_PORT}/"
    return
  fi

  if port_listening "$FRONTEND_FALLBACK_PORT"; then
    echo "[OK] Frontend started at http://localhost:${FRONTEND_FALLBACK_PORT}/"
    return
  fi

  echo "[ERROR] Frontend did not start on ${FRONTEND_PORT} or ${FRONTEND_FALLBACK_PORT}. Check $RUN_DIR/frontend.log"
  exit 1
}

ensure_local_dependencies
ensure_mysql
start_redis
start_python_services
start_backend
start_frontend

echo ""
echo "[DONE] Stack startup complete"
echo "[INFO] Logs directory: $RUN_DIR"
echo "[INFO] Run scripts/status_all.sh to check health"
