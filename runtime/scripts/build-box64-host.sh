#!/usr/bin/env bash
set -euo pipefail

project_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
LOCK_PATH="$project_root/runtime/locks/components.lock.json"
mapfile -t BOX64_LOCK < <(
  node - "$LOCK_PATH" <<'NODE'
const { readFileSync } = require("node:fs");

let lock;
try {
  lock = JSON.parse(readFileSync(process.argv[2], "utf8"));
} catch {
  console.error("invalid box64 component lock");
  process.exit(1);
}

const box64 = Array.isArray(lock.components)
  ? lock.components.find((component) => component?.name === "box64")
  : undefined;
if (
  !box64 ||
  box64.name !== "box64" ||
  typeof box64.url !== "string" ||
  !/^https:\/\//.test(box64.url) ||
  typeof box64.revision !== "string" ||
  !/^[0-9a-f]{40}$/.test(box64.revision)
) {
  console.error("invalid box64 component lock");
  process.exit(1);
}

process.stdout.write(`${box64.url}\n${box64.revision}\n`);
NODE
)
if [[ ${#BOX64_LOCK[@]} -ne 2 ]]; then
  echo "invalid box64 component lock" >&2
  exit 1
fi
BOX64_URL=${BOX64_LOCK[0]}
BOX64_REVISION=${BOX64_LOCK[1]}
bash "$project_root/runtime/scripts/checkout-component.sh" box64 "$BOX64_URL" "$BOX64_REVISION"

source_dir="$project_root/runtime/sources/box64"
build_dir="$project_root/runtime/build/box64-host"
stage_dir="$project_root/runtime/build/box64-host-stage"
quick_exit_patch="$project_root/runtime/patches/box64-cxa-quick-exit.patch"
vulkan_qcom_patch="$project_root/runtime/patches/box64-vulkan-dispatch-tile-qcom.patch"
vex_write_opcode_patch="$project_root/runtime/patches/box64-vex-write-opcode.patch"
native_write_opcode_patch="$project_root/runtime/patches/box64-native-write-opcode.patch"
bachata_thread_affinity_patch="$project_root/runtime/patches/box64-bachata-thread-affinity.patch"

for tool in cc cmake ninja aarch64-linux-gnu-gcc aarch64-linux-gnu-g++ readelf; do
  command -v "$tool" >/dev/null || { echo "$tool is required" >&2; exit 1; }
done
test "$(git -C "$source_dir" rev-parse HEAD)" = "$BOX64_REVISION"
if ! git -C "$source_dir" apply --reverse --check "$quick_exit_patch" 2>/dev/null; then
  git -C "$source_dir" apply --check "$quick_exit_patch"
  git -C "$source_dir" apply "$quick_exit_patch"
fi
if ! git -C "$source_dir" apply --reverse --check "$vulkan_qcom_patch" 2>/dev/null; then
  git -C "$source_dir" apply --check "$vulkan_qcom_patch"
  git -C "$source_dir" apply "$vulkan_qcom_patch"
fi
if ! git -C "$source_dir" apply --reverse --check "$vex_write_opcode_patch" 2>/dev/null; then
  git -C "$source_dir" apply --check "$vex_write_opcode_patch"
  git -C "$source_dir" apply "$vex_write_opcode_patch"
fi
if ! git -C "$source_dir" apply --reverse --check "$native_write_opcode_patch" 2>/dev/null; then
  git -C "$source_dir" apply --check "$native_write_opcode_patch"
  git -C "$source_dir" apply "$native_write_opcode_patch"
fi
if ! git -C "$source_dir" apply --reverse --check "$bachata_thread_affinity_patch" 2>/dev/null; then
  git -C "$source_dir" apply --check "$bachata_thread_affinity_patch"
  git -C "$source_dir" apply "$bachata_thread_affinity_patch"
fi

mkdir -p "$build_dir"
affinity_test="$build_dir/test_bachata_thread_affinity"
cc -Wall -Wextra -Werror "$source_dir/tests/test_bachata_thread_affinity.c" -o "$affinity_test"
"$affinity_test"

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
printf 'box64_host=%s\nrevision=%s\n' "$stage_dir/box64" "$BOX64_REVISION"
