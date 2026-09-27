#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
root="$PWD"
jar="$root/target/campusflow-1.0.0.jar"
state="$root/.poc"
mkdir -p "$state/data"
if [[ -f "$state/app.pid" ]]; then
  pid="$(cat "$state/app.pid")"
  if [[ "$pid" =~ ^[0-9]+$ ]] && [[ -r "/proc/$pid/cmdline" ]] && tr '\0' '\n' < "/proc/$pid/cmdline" | grep -Fxq -- "$jar"; then
    echo "CampusFlow is already running. Open port 8080 in the Ports panel."
    exit 0
  fi
fi
if [[ ! -f "$jar" ]] || [[ pom.xml -nt "$jar" ]] || [[ -n "$(find src -type f -newer "$jar" -print -quit 2>/dev/null)" ]]; then
  mvn -B -ntp verify
fi
# POC uses sample accounts only. Never select a production profile here.
export SPRING_PROFILES_ACTIVE=demo
export H2_DEMO_DATA_PATH="$state/data/campusflow"
export COOKIE_SECURE=true
export SERVER_FORWARD_HEADERS_STRATEGY=framework
export SERVER_ADDRESS=0.0.0.0
export PORT=8080
nohup java -jar "$jar" > "$state/app.log" 2>&1 < /dev/null &
pid=$!
echo "$pid" > "$state/app.pid"
for ((i=0; i<120; i++)); do
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "CampusFlow stopped during startup. Check .poc/app.log."
    tail -n 40 "$state/app.log"
    exit 1
  fi
  if curl --silent --fail http://127.0.0.1:8080/api/public/config > /dev/null; then
    echo "CampusFlow is ready. Open port 8080 in the Ports panel."
    echo "For a shareable demo, change that port's visibility to Public."
    exit 0
  fi
  sleep 1
done
echo "Startup has not finished. Check .poc/app.log."
exit 1
