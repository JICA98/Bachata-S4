#!/usr/bin/env bash
set -euo pipefail

serial=${1:-7d6afed8}
game_id=${2:-CUSA00900}
output_dir=${3:-/tmp/bloodborne-fex-baseline}
package_name=com.bachatas4.android
activity=com.bachatas4.android/.DirectLaunchActivity
capture_seconds=${BACHATA_CAPTURE_SECONDS:-30}

[[ $serial =~ ^[A-Za-z0-9._:-]+$ ]] || { echo "Invalid device serial" >&2; exit 2; }
[[ $game_id =~ ^[A-Z]{4}[0-9]{5}$ ]] || { echo "Invalid game id" >&2; exit 2; }
[[ $capture_seconds =~ ^[0-9]+$ ]] || { echo "BACHATA_CAPTURE_SECONDS must be an integer" >&2; exit 2; }
(( capture_seconds >= 1 && capture_seconds <= 60 )) || {
  echo "BACHATA_CAPTURE_SECONDS must be between 1 and 60" >&2
  exit 2
}

mkdir -p "$output_dir"
adb -s "$serial" get-state >/dev/null
sessions_before=$(
  adb -s "$serial" shell run-as "$package_name" ls -1 files/logs 2>/dev/null \
    | tr -d '\r' | sort || true
)
adb -s "$serial" shell am force-stop "$package_name"
adb -s "$serial" logcat -c
adb -s "$serial" shell am start -S -n "$activity" --es game_id "$game_id" \
  >"$output_dir/activity-start.txt"

latest_session=
for _attempt in $(seq 1 60); do
  session_listing=
  if session_listing=$(adb -s "$serial" shell run-as "$package_name" ls -1 files/logs 2>/dev/null); then
    session_listing=$(printf '%s\n' "$session_listing" | tr -d '\r' | sort)
    new_sessions=$(comm -13 \
      <(printf '%s\n' "$sessions_before") \
      <(printf '%s\n' "$session_listing"))
    latest_name=$(
      printf '%s\n' "$new_sessions" \
        | awk -v needle="-${game_id}-" 'index($0, needle) > 0 { print }' \
        | sort | tail -n 1
    )
    [[ -n $latest_name ]] && latest_session="files/logs/$latest_name"
  fi
  [[ -n $latest_session ]] && break
  sleep 1
done
[[ -n $latest_session ]] || { echo "Timed out waiting for the Bloodborne session log" >&2; exit 1; }

sleep "$capture_seconds"

copy_private_file() {
  local private_path=$1
  local output_name=$2
  if adb -s "$serial" shell run-as "$package_name" test -f "$private_path"; then
    adb -s "$serial" exec-out run-as "$package_name" cat "$private_path" \
      >"$output_dir/$output_name"
  fi
}

copy_private_file "$latest_session/application.log" application.log
copy_private_file "$latest_session/shadps4.log" shadps4.log
copy_private_file "$latest_session/shadps4-internal.log" shadps4-internal.log
adb -s "$serial" logcat -d -v threadtime >"$output_dir/logcat.txt"
adb -s "$serial" exec-out screencap -p >"$output_dir/screen.png"
adb -s "$serial" shell ps -A >"$output_dir/processes.txt"
if adb -s "$serial" shell ls -la /data/tombstones >"$output_dir/tombstones.txt" 2>/dev/null; then
  :
else
  printf '%s\n' "tombstones unavailable to shell user" >"$output_dir/tombstones.txt"
fi

printf '{\n  "schemaVersion": 1,\n  "capturedAtUtc": "%s",\n  "deviceSerial": "%s",\n  "packageName": "%s",\n  "titleId": "%s",\n  "captureSeconds": %s,\n  "sessionDirectory": "%s"\n}\n' \
  "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$serial" "$package_name" "$game_id" \
  "$capture_seconds" "$latest_session" >"$output_dir/metadata.json"

printf 'bloodborne_capture=%s\nsession=%s\n' "$output_dir" "$latest_session"
