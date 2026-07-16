#!/usr/bin/env bash
set -euo pipefail

project_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$project_root"

if ! command -v aarch64-linux-gnu-gcc >/dev/null 2>&1; then
  echo "Missing aarch64-linux-gnu-gcc. Run: sudo runtime/scripts/install-debian-runtime-deps.sh"
  exit 1
fi

runtime/scripts/build-shadps4-x86_64.sh
runtime/scripts/build-box64-host.sh
bash runtime/scripts/build-shadps4-arm64.sh
node runtime/scripts/stage-debian-runtime.mjs
node runtime/scripts/package-runtime.mjs
