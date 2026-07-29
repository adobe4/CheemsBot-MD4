#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ ! -f app-user/google-services.json || ! -f app-admin/google-services.json ]]; then
  cat >&2 <<'MSG'
Firebase config missing.
Put google-services.json here before building:
  android-dj-kuku/app-user/google-services.json
  android-dj-kuku/app-admin/google-services.json
MSG
  exit 1
fi

if command -v mise >/dev/null 2>&1 && mise ls java 2>/dev/null | grep -q '17'; then
  mise exec java@17 -- gradle :app-user:assembleDebug :app-admin:assembleDebug --no-daemon
else
  gradle :app-user:assembleDebug :app-admin:assembleDebug --no-daemon
fi

cat <<'MSG'

APK files:
  app-user/build/outputs/apk/debug/app-user-debug.apk
  app-admin/build/outputs/apk/debug/app-admin-debug.apk
MSG
