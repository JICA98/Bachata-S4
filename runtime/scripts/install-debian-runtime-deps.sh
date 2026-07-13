#!/usr/bin/env bash
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive

test "$(id -u)" -eq 0 || {
    echo "This script must run as root" >&2
    exit 1
}

dpkg --add-architecture arm64
apt-get update -qq

arch_tools=(
    ca-certificates git nodejs cmake ninja-build
    clang llvm gcc g++ gcc-aarch64-linux-gnu g++-aarch64-linux-gnu
    binutils binutils-aarch64-linux-gnu pkg-config file
    patchelf python3 python3-pyelftools dpkg-dev xz-utils zstd zip
    unzip curl
)

arch_dev=(
    libc6-dev:amd64 libsdl2-dev:amd64 libvulkan-dev:amd64
)

arch_amd64=(
    libc6:amd64 libgcc-s1:amd64 libstdc++6:amd64
    libsdl2-2.0-0:amd64 libvulkan1:amd64
    libudev1:amd64 libuuid1:amd64
    libx11-6:amd64 libx11-xcb1:amd64 libxcursor1:amd64
    libxext6:amd64 libxfixes3:amd64 libxi6:amd64 libxrandr2:amd64
    libxrender1:amd64 libxss1:amd64
    libxcb1:amd64 libxcb-dri3-0:amd64 libxcb-present0:amd64
    libxcb-randr0:amd64 libxcb-render0:amd64 libxcb-shm0:amd64
    libxcb-sync1:amd64 libxau6:amd64 libxdmcp6:amd64
    libxkbcommon0:amd64 libdbus-1-3:amd64 libsystemd0:amd64
    zlib1g:amd64 libdrm2:amd64
)

arch_arm64=(
    libc6:arm64 libgcc-s1:arm64 libstdc++6:arm64
    libvulkan1:arm64 libudev1:arm64 libuuid1:arm64
    libx11-6:arm64 libx11-xcb1:arm64 libxcursor1:arm64
    libxext6:arm64 libxfixes3:arm64 libxi6:arm64 libxrandr2:arm64
    libxrender1:arm64 libxss1:arm64
    libxcb1:arm64 libxcb-dri3-0:arm64 libxcb-present0:arm64
    libxcb-randr0:arm64 libxcb-render0:arm64 libxcb-shm0:arm64
    libxcb-sync1:arm64 libxau6:arm64 libxdmcp6:arm64
    libxkbcommon0:arm64 libdbus-1-3:arm64 libsystemd0:arm64
    zlib1g:arm64 libdrm2:arm64
)

arch_indep=(
    ca-certificates xkb-data
)

all_packages=("${arch_tools[@]}" "${arch_dev[@]}" "${arch_amd64[@]}" "${arch_arm64[@]}" "${arch_indep[@]}")

echo "Installing ${#all_packages[@]} packages..."
apt-get install -y --no-install-recommends "${all_packages[@]}"

echo "=== Installed runtime packages ==="
for entry in "${arch_tools[@]}" "${arch_dev[@]}" "${arch_amd64[@]}" "${arch_arm64[@]}" "${arch_indep[@]}"; do
    pkg="${entry%%:*}"
    dpkg-query -W -f='${Package} ${Architecture} ${Version} ${source:Package} ${source:Version}\n' "$pkg" 2>/dev/null || echo "MISSING: $entry" >&2
done | sort -u

echo "Debian runtime dependencies installed successfully."
