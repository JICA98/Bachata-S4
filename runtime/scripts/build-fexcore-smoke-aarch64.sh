#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
LOCK_PATH="${PROJECT_ROOT}/runtime/locks/components.lock.json"
FEX_SOURCE="${PROJECT_ROOT}/runtime/sources/fex"
BUILD_DIR="${PROJECT_ROOT}/runtime/build/fexcore-smoke-build"
STAGE_DIR="${PROJECT_ROOT}/runtime/build/fexcore-smoke-stage"
PATCH_PATH="${PROJECT_ROOT}/runtime/patches/fex-fexcore-only.patch"
SMOKE_SOURCE="${PROJECT_ROOT}/runtime/probes/fexcore-smoke.cpp"
VERIFIER="${PROJECT_ROOT}/runtime/tests/verify-fexcore-smoke-build.mjs"

mapfile -t FEX_LOCK < <(node -e '
const { readFileSync } = require("node:fs");
const lock = JSON.parse(readFileSync(process.argv[1], "utf8"));
const component = lock.components?.find(({ name }) => name === "fex");
if (!component || !/^https:\/\//.test(component.url) || !/^[0-9a-f]{40}$/.test(component.revision)) process.exit(1);
console.log(component.url);
console.log(component.revision);
' "${LOCK_PATH}")

if [[ ${#FEX_LOCK[@]} -ne 2 ]]; then
  echo "invalid fex component lock" >&2
  exit 1
fi
FEX_URL=${FEX_LOCK[0]}
FEX_REVISION=${FEX_LOCK[1]}

bash "${PROJECT_ROOT}/runtime/scripts/checkout-component.sh" fex "${FEX_URL}" "${FEX_REVISION}"

FEX_SUBMODULES=(
  External/unordered_dense
  External/rpmalloc
  External/xxhash
  External/fmt
  External/range-v3
  Source/Common/cpp-optparse
)
git -C "${FEX_SOURCE}" submodule update --init --depth 1 --jobs 8 -- "${FEX_SUBMODULES[@]}"

if [[ $(git -C "${FEX_SOURCE}" rev-parse HEAD) != "${FEX_REVISION}" ]]; then
  echo "fex checkout does not match components lock" >&2
  exit 1
fi
git -C "${FEX_SOURCE}" submodule foreach --quiet --recursive '
  if [ -n "$(git status --porcelain)" ]; then
    echo "dirty fex submodule: $name" >&2
    exit 1
  fi
'

git -C "${FEX_SOURCE}" apply --check "${PATCH_PATH}"
git -C "${FEX_SOURCE}" apply "${PATCH_PATH}"

cmake -S "${FEX_SOURCE}" -B "${BUILD_DIR}" -G Ninja \
  -DCMAKE_SYSTEM_NAME=Linux \
  -DCMAKE_SYSTEM_PROCESSOR=aarch64 \
  -DCMAKE_C_COMPILER=clang \
  -DCMAKE_CXX_COMPILER=clang++ \
  -DCMAKE_C_COMPILER_TARGET=aarch64-linux-gnu \
  -DCMAKE_CXX_COMPILER_TARGET=aarch64-linux-gnu \
  -DCMAKE_FIND_ROOT_PATH=/usr/aarch64-linux-gnu \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX="${STAGE_DIR}" \
  -DBUILD_FEXCORE_ONLY=ON \
  -DFEXCORE_SMOKE_SOURCE="${SMOKE_SOURCE}" \
  -DBUILD_TESTING=OFF \
  -DBUILD_FEX_LINUX_TESTS=OFF \
  -DBUILD_THUNKS=OFF \
  -DBUILD_FEXCONFIG=OFF \
  -DENABLE_GDB_SYMBOLS=OFF \
  -DENABLE_LTO=OFF \
  -DENABLE_JEMALLOC_GLIBC_ALLOC=OFF \
  -DENABLE_OFFLINE_TELEMETRY=OFF \
  -DENABLE_VIXL_DISASSEMBLER=OFF \
  -DENABLE_VIXL_SIMULATOR=OFF \
  -DENABLE_ZYDIS=OFF \
  -DENABLE_FEXCORE_PROFILER=OFF

cmake --build "${BUILD_DIR}" --target fexcore-smoke --parallel
rm -rf "${STAGE_DIR}"
mkdir -p "${STAGE_DIR}"
cmake --install "${BUILD_DIR}"
aarch64-linux-gnu-strip "${STAGE_DIR}/bin/fexcore-smoke"
node "${VERIFIER}" "${STAGE_DIR}/bin/fexcore-smoke"
