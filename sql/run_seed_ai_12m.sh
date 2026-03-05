#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-spare_db}"
DB_USER="${DB_USER:-admin}"
DB_PASS="${DB_PASS:-123456}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/seed_ai_12m_medium_enterprise.sql"

if [[ ! -f "${SQL_FILE}" ]]; then
  echo "SQL file not found: ${SQL_FILE}" >&2
  exit 1
fi

echo "Running seed script: ${SQL_FILE}"
echo "Target: ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"

mysql \
  -h"${DB_HOST}" \
  -P"${DB_PORT}" \
  -u"${DB_USER}" \
  -p"${DB_PASS}" \
  "${DB_NAME}" < "${SQL_FILE}"

echo "Seed completed. Review validation SQL output above."
