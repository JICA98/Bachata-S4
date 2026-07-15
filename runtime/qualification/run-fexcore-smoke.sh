#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 adb-serial" >&2
  exit 64
fi

serial=$1
project_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
adb_bin=${ADB:-adb}
apk="$project_root/android/BachataS4/app/build/outputs/apk/playstore/debug/app-playstore-debug.apk"
test_apk="$project_root/android/BachataS4/app/build/outputs/apk/androidTest/playstore/debug/app-playstore-debug-androidTest.apk"
build_directory="$project_root/runtime/build/fex-phase0-evidence"
evidence="$project_root/runtime/evidence/sm8650/fex-phase0.json"
marker="FEXCORE_SMOKE_OK revision=f2b679f6028ce1c38875233aecfcf5d3f8ebecec gpr=ok stack=ok fp=ok threads=ok tls=ok callback=ok invalidation=ok"

require_equal() {
  local actual=$1
  local expected=$2
  local label=$3
  if [[ "$actual" != "$expected" ]]; then
    echo "FEX Phase 0 runner: $label expected '$expected', got '$actual'" >&2
    exit 1
  fi
}

require_equal "$("$adb_bin" -s "$serial" get-state | tr -d '\r')" "device" "device state"
node "$project_root/runtime/tests/verify-apk-runtime.mjs" "$apk"
require_equal "$("$adb_bin" -s "$serial" shell getprop ro.product.cpu.abi | tr -d '\r')" "arm64-v8a" "ABI"
require_equal "$("$adb_bin" -s "$serial" shell getprop ro.soc.model | tr -d '\r')" "SM8650" "SoC"
require_equal "$("$adb_bin" -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')" "36" "SDK"
require_equal "$("$adb_bin" -s "$serial" shell getconf PAGESIZE | tr -d '\r')" "4096" "page size"

mkdir -p "$build_directory"
run_directory=$(mktemp -d "$build_directory/run.XXXXXX")
"$adb_bin" -s "$serial" install -r "$apk" >"$run_directory/install-app.txt"
"$adb_bin" -s "$serial" install -r -t "$test_apk" >"$run_directory/install-test.txt"

logcat_start=$("$adb_bin" -s "$serial" shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r')
started_ns=$(date +%s%N)
set +e
timeout 45s "$adb_bin" -s "$serial" shell am instrument -w -r \
  -e class com.bachatas4.android.FexCoreSmokeDeviceTest \
  com.bachatas4.android.test/androidx.test.runner.AndroidJUnitRunner >"$run_directory/instrumentation.raw" 2>&1
instrumentation_status=$?
set -e
finished_ns=$(date +%s%N)
"$adb_bin" -s "$serial" logcat -d -T "$logcat_start" -s BachataFexSmoke:I '*:S' >"$run_directory/logcat.raw"

if [[ $instrumentation_status -ne 0 ]]; then
  echo "FEX Phase 0 runner: instrumentation failed with status $instrumentation_status" >&2
  exit "$instrumentation_status"
fi
if ! grep -F -- "$marker" "$run_directory/instrumentation.raw" "$run_directory/logcat.raw" >/dev/null; then
  echo "FEX Phase 0 runner: exact smoke marker was not captured" >&2
  exit 1
fi

duration_ms=$(( (finished_ns - started_ns) / 1000000 ))
if (( duration_ms < 1 )); then
  duration_ms=1
fi
node "$project_root/runtime/qualification/create-fex-phase0-evidence.mjs" \
  "$duration_ms" "$run_directory/instrumentation.raw" "$run_directory/logcat.raw" "$evidence"
node "$project_root/runtime/tests/verify-fex-phase0-evidence.mjs" "$evidence"
