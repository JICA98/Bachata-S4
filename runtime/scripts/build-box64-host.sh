#!/usr/bin/env bash
set -euo pipefail

project_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
source_dir="$project_root/runtime/sources/box64"
build_dir="$project_root/runtime/build/box64-host"
stage_dir="$project_root/runtime/build/box64-host-stage"
expected_revision=50c8b90b09b433ab0767de44af2d0731cb0748b7
quick_exit_patch="$project_root/runtime/patches/box64-cxa-quick-exit.patch"

for tool in cmake ninja aarch64-linux-gnu-gcc aarch64-linux-gnu-g++ readelf; do
  command -v "$tool" >/dev/null || { echo "$tool is required" >&2; exit 1; }
done
test "$(git -C "$source_dir" rev-parse HEAD)" = "$expected_revision"
if ! git -C "$source_dir" apply --reverse --check "$quick_exit_patch" 2>/dev/null; then
  git -C "$source_dir" apply --check "$quick_exit_patch"
  git -C "$source_dir" apply "$quick_exit_patch"
fi

cmake -S "$source_dir" -B "$build_dir" -G Ninja \
  -DCMAKE_SYSTEM_NAME=Linux \
  -DCMAKE_SYSTEM_PROCESSOR=aarch64 \
  -DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc \
  -DCMAKE_CXX_COMPILER=aarch64-linux-gnu-g++ \
  -DARM64=ON \
  -DARM_DYNAREC=ON \
  -DBAD_SIGNAL=ON \
  -DNO_LIB_INSTALL=ON \
  -DNO_CONF_INSTALL=ON \
  -DCMAKE_BUILD_TYPE=Release
cmake --build "$build_dir" --target box64

binary="$build_dir/box64"
test -f "$binary"
header=$(readelf -h "$binary")
grep -Eq 'Class:[[:space:]]+ELF64' <<<"$header"
grep -Eq 'Machine:[[:space:]]+AArch64' <<<"$header"
if readelf -d "$binary" | sed -n 's/.*Shared library: \[\([^]]*\)\].*/\1/p' \
    | grep -Ev '^(libc\.so\.6|libm\.so\.6|libresolv\.so\.2|ld-linux-aarch64\.so\.1)$'; then
  echo "Box64 host binary has an unsupported dynamic dependency" >&2
  exit 1
fi

rm -rf "$stage_dir"
install -Dm755 "$binary" "$stage_dir/box64"
printf 'box64_host=%s\nrevision=%s\n' "$stage_dir/box64" "$expected_revision"
