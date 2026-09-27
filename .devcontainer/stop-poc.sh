#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
jar="$PWD/target/campusflow-1.0.0.jar"
pidfile="$PWD/.poc/app.pid"
if [[ -f "$pidfile" ]]; then
  pid="$(cat "$pidfile")"
  if [[ "$pid" =~ ^[0-9]+$ ]] && [[ -r "/proc/$pid/cmdline" ]] && tr '\0' '\n' < "/proc/$pid/cmdline" | grep -Fxq -- "$jar"; then
    kill "$pid"
    for ((i=0; i<30; i++)); do
      if ! kill -0 "$pid" 2>/dev/null; then
        rm -f "$pidfile"
        echo "CampusFlow stopped; H2 data is retained."
        exit 0
      fi
      sleep 1
    done
    echo "The process is still shutting down. Retry after checking .poc/app.log."
    exit 1
  fi
  rm -f "$pidfile"
fi
echo "CampusFlow is not running."
