#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
backend_dir="$repo_root/backend"
pid_file="/tmp/my-site-backend.pid"
log_file="/tmp/my-site-backend.log"
h2_dir="/var/lib/my-site-h2"

if [[ ! -w /home/vscode/.m2 ]]; then
  sudo chown -R "$(id -u):$(id -g)" /home/vscode/.m2
fi

if [[ ! -w "$h2_dir" ]]; then
  sudo mkdir -p "$h2_dir"
  sudo chown -R "$(id -u):$(id -g)" "$h2_dir"
fi

if [[ -f "$pid_file" ]] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
  echo "Backend already running with PID $(cat "$pid_file"). Log: $log_file"
  exit 0
fi

cd "$backend_dir"
mkdir -p data storage

nohup mvn spring-boot:run -Dspring-boot.run.profiles=dev >"$log_file" 2>&1 &
echo "$!" >"$pid_file"

echo "Backend starting with PID $(cat "$pid_file"). Log: $log_file"
