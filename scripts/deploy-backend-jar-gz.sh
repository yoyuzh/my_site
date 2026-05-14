#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIR="${REPO_ROOT}/backend"
JAR_PATH="${BACKEND_DIR}/target/yoyuzh-portal-backend-0.0.1-SNAPSHOT.jar"
LOCAL_GZ_PATH="/tmp/yoyuzh-portal-backend.jar.gz"
REMOTE_JAR_PATH="/opt/yoyuzh/yoyuzh-portal-backend.jar"
REMOTE_TMP_PATH="${REMOTE_JAR_PATH}.tmp"
REMOTE_GZ_PATH="${REMOTE_TMP_PATH}.gz"
SERVICE_NAME="my-site-api.service"
SSHPASS_BIN="/opt/homebrew/bin/sshpass"

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Missing required command: ${command_name}" >&2
    exit 1
  fi
}

require_file() {
  local file_path="$1"
  if [[ ! -f "${file_path}" ]]; then
    echo "Missing required file: ${file_path}" >&2
    exit 1
  fi
}

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required environment variable: ${name}" >&2
    exit 1
  fi
}

log_step() {
  printf '\n==> %s\n' "$1"
}

require_command gzip
require_command scp
require_command ssh
require_command shasum
require_file "${SSHPASS_BIN}"
require_file "${REPO_ROOT}/.env"
require_file "${JAR_PATH}"

set -a
source "${REPO_ROOT}/.env"
set +a

require_env YOYUZH_SERVER_HOST
require_env YOYUZH_SERVER_PORT
require_env YOYUZH_SERVER_USER
require_env YOYUZH_SERVER_PASSWORD

SSH_BASE=(
  "${SSHPASS_BIN}" -p "${YOYUZH_SERVER_PASSWORD}"
  ssh
  -o StrictHostKeyChecking=no
  -o PreferredAuthentications=password
  -o PubkeyAuthentication=no
  -p "${YOYUZH_SERVER_PORT}"
  "${YOYUZH_SERVER_USER}@${YOYUZH_SERVER_HOST}"
)

SCP_BASE=(
  "${SSHPASS_BIN}" -p "${YOYUZH_SERVER_PASSWORD}"
  scp
  -P "${YOYUZH_SERVER_PORT}"
  -o StrictHostKeyChecking=no
  -o PreferredAuthentications=password
  -o PubkeyAuthentication=no
)

log_step "Calculating local jar hash"
LOCAL_HASH="$(shasum -a 256 "${JAR_PATH}" | awk '{print $1}')"
echo "Local jar: ${JAR_PATH}"
echo "Local sha256: ${LOCAL_HASH}"

log_step "Compressing jar"
gzip -c "${JAR_PATH}" > "${LOCAL_GZ_PATH}"
ls -lh "${LOCAL_GZ_PATH}"

log_step "Cleaning remote tmp files"
"${SSH_BASE[@]}" "rm -f '${REMOTE_TMP_PATH}' '${REMOTE_GZ_PATH}'"

log_step "Uploading compressed jar"
"${SCP_BASE[@]}" "${LOCAL_GZ_PATH}" "${YOYUZH_SERVER_USER}@${YOYUZH_SERVER_HOST}:${REMOTE_GZ_PATH}"

log_step "Decompressing and verifying remote jar"
REMOTE_HASH="$("${SSH_BASE[@]}" "set -e; gzip -dc '${REMOTE_GZ_PATH}' > '${REMOTE_TMP_PATH}'; sha256sum '${REMOTE_TMP_PATH}' | awk '{print \$1}'")"
echo "Remote sha256: ${REMOTE_HASH}"

if [[ "${LOCAL_HASH}" != "${REMOTE_HASH}" ]]; then
  echo "Hash mismatch between local jar and remote tmp jar" >&2
  exit 1
fi

log_step "Backing up current remote jar"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
"${SSH_BASE[@]}" "cp '${REMOTE_JAR_PATH}' '${REMOTE_JAR_PATH}.bak-${TIMESTAMP}' && echo '${REMOTE_JAR_PATH}.bak-${TIMESTAMP}'"

log_step "Replacing remote jar and restarting service"
"${SSH_BASE[@]}" "mv '${REMOTE_TMP_PATH}' '${REMOTE_JAR_PATH}' && rm -f '${REMOTE_GZ_PATH}' && systemctl restart '${SERVICE_NAME}' && systemctl is-active '${SERVICE_NAME}'"

log_step "Verifying backend health"
"${SSH_BASE[@]}" '
  set -e
  for attempt in $(seq 1 12); do
    if curl --max-time 10 -fsS http://127.0.0.1:8080/api/v2/site/ping; then
      exit 0
    fi
    echo "Backend health check not ready yet; retrying (${attempt}/12)..." >&2
    sleep 5
  done
  echo "Backend health check failed after waiting for startup" >&2
  systemctl --no-pager -l status my-site-api.service >&2 || true
  journalctl -u my-site-api.service -n 80 --no-pager >&2 || true
  exit 1
'

log_step "Verifying public API health"
curl --max-time 15 -sS -i https://api.yoyuzh.xyz/api/v2/site/ping

log_step "Done"
