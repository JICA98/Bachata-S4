#!/usr/bin/env bash
set -euo pipefail

project_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
revision=e113da42beefc39c69c8944b27c19c3703bfa856
source_root="$project_root/runtime/sources/winlator-app"
java_source="$source_root/app/src/main/java/com/winlator"
cpp_source="$source_root/app/src/main/cpp/winlator"
runtime_root="$project_root/android/BachataS4/core/runtime/src/main"
manifest="$project_root/runtime/locks/winlator-vendor.sha256"

"$project_root/runtime/scripts/checkout-component.sh" \
  winlator-app https://github.com/brunodev85/winlator-app.git "$revision"

java_packages=(xserver alsaserver core math renderer sysvshm xconnector)
for package in "${java_packages[@]}"; do
  test -d "$java_source/$package"
  rm -rf "$runtime_root/java/com/winlator/$package"
done
test -d "$cpp_source"
rm -rf "$runtime_root/cpp/winlator"
mkdir -p "$runtime_root/java/com/winlator" "$runtime_root/cpp/winlator" "$(dirname "$manifest")"

temporary=$(mktemp)
trap 'rm -f "$temporary"' EXIT

copy_tree() {
  local upstream_root=$1 destination_root=$2
  while IFS= read -r -d '' source; do
    local relative=${source#"$upstream_root/"}
    local destination="$destination_root/$relative"
    mkdir -p "$(dirname "$destination")"
    cp -p "$source" "$destination"
    printf '%s  %s -> %s\n' \
      "$(sha256sum "$source" | cut -d' ' -f1)" \
      "${source#"$source_root/"}" \
      "${destination#"$project_root/"}" >> "$temporary"
  done < <(find "$upstream_root" -type f \( \
    -name '*.java' -o -name '*.c' -o -name '*.cc' -o -name '*.cpp' \
    -o -name '*.h' -o -name '*.hpp' -o -name 'CMakeLists.txt' \
  \) -print0 | sort -z)
}

for package in "${java_packages[@]}"; do
  copy_tree "$java_source/$package" "$runtime_root/java/com/winlator/$package"
done
copy_tree "$cpp_source" "$runtime_root/cpp/winlator"

{
  printf '# Winlator source manifest\n'
  printf '# url=https://github.com/brunodev85/winlator-app.git\n'
  printf '# revision=%s\n' "$revision"
  LC_ALL=C sort "$temporary"
} > "$manifest"

if find "$runtime_root/java/com/winlator" "$runtime_root/cpp/winlator" -type f \
  \( -name '*.so' -o -name '*.a' -o -name '*.jar' -o -name '*.apk' -o -name '*.exe' \) | grep -q .; then
  echo "Vendored binary detected" >&2
  exit 1
fi

printf 'vendored_revision=%s files=%s\n' "$revision" "$(grep -vc '^#' "$manifest")"
