#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
backend_dir="$repo_root/backend"
pid_file="/tmp/my-site-backend.pid"
log_file="/tmp/my-site-backend.log"
h2_dir="/var/lib/my-site-h2"
downloads_dir="/var/lib/my-site-downloads"
qb_config_dir="/var/lib/my-site-qb-config"
qb_runtime_config_dir="$qb_config_dir/qBittorrent/config"
aria2_pid_file="/tmp/my-site-aria2.pid"
aria2_log_file="/tmp/my-site-aria2.log"
qb_pid_file="/tmp/my-site-qb.pid"
qb_log_file="/tmp/my-site-qb.log"

if [[ ! -w /home/vscode/.m2 ]]; then
  sudo chown -R "$(id -u):$(id -g)" /home/vscode/.m2
fi

if [[ ! -w "$h2_dir" ]]; then
  sudo mkdir -p "$h2_dir"
  sudo chown -R "$(id -u):$(id -g)" "$h2_dir"
fi

for dir in "$downloads_dir" "$qb_config_dir" "$qb_runtime_config_dir"; do
  if [[ ! -w "$dir" ]]; then
    sudo mkdir -p "$dir"
    sudo chown -R "$(id -u):$(id -g)" "$dir"
  fi
done

start_aria2() {
  if [[ -f "$aria2_pid_file" ]] && kill -0 "$(cat "$aria2_pid_file")" 2>/dev/null; then
    echo "aria2 already running with PID $(cat "$aria2_pid_file"). Log: $aria2_log_file"
    return
  fi

  nohup aria2c \
    --enable-rpc \
    --rpc-listen-all=false \
    --rpc-listen-port=6800 \
    --rpc-secret="${APP_REMOTE_DOWNLOAD_ARIA2_SECRET:-devcontainer-aria2-secret}" \
    --dir="$downloads_dir" \
    --continue=true \
    --max-concurrent-downloads=5 \
    --daemon=false >"$aria2_log_file" 2>&1 &
  echo "$!" >"$aria2_pid_file"
  echo "aria2 starting with PID $(cat "$aria2_pid_file"). Log: $aria2_log_file"
}

start_qbittorrent() {
  if [[ -f "$qb_pid_file" ]] && kill -0 "$(cat "$qb_pid_file")" 2>/dev/null; then
    echo "qBittorrent already running with PID $(cat "$qb_pid_file"). Log: $qb_log_file"
    return
  fi

  cat >"$qb_runtime_config_dir/qBittorrent.conf" <<EOF
[LegalNotice]
Accepted=true

[Preferences]
WebUI\\Port=8081
WebUI\\Address=127.0.0.1
WebUI\\Username=${APP_REMOTE_DOWNLOAD_QBITTORRENT_USERNAME:-admin}
WebUI\\Password_PBKDF2="@ByteArray(woCzTA7QoE0l5xJ+ZSDFsg==:XXFgJD1R5yFc5HSzZI/vaj/KgwVu1p1GlVIIj/11r0qgCUhNtzavGKJd9aE4MCfbWZBQozfA8ntUj9ulkU72xg==)"
Downloads\\SavePath=$downloads_dir/
Downloads\\StartInPause=true
Downloads\\ScanDirsV2=@Variant(\\0\\0\\0\\x1c\\0\\0\\0\\0)
EOF

  nohup qbittorrent-nox --profile="$qb_config_dir" --webui-port=8081 >"$qb_log_file" 2>&1 &
  echo "$!" >"$qb_pid_file"
  echo "qBittorrent starting with PID $(cat "$qb_pid_file"). Log: $qb_log_file"
}

if [[ -f "$pid_file" ]] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
  echo "Backend already running with PID $(cat "$pid_file"). Log: $log_file"
  exit 0
fi

start_aria2
start_qbittorrent

cd "$backend_dir"
mkdir -p data storage

nohup mvn spring-boot:run -Dspring-boot.run.profiles=dev >"$log_file" 2>&1 &
echo "$!" >"$pid_file"

echo "Backend starting with PID $(cat "$pid_file"). Log: $log_file"
