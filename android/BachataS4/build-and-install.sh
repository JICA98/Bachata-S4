#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

info()    { echo -e "${CYAN}[INFO]${NC} $*"; }
success() { echo -e "${GREEN}[OK]${NC} $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }

VARIANT="debug"
UNINSTALL_FIRST=false
BUNDLE_ONLY=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        debug|release)
            VARIANT="$1"
            ;;
        --uninstall-first)
            UNINSTALL_FIRST=true
            ;;
        --bundle-only)
            BUNDLE_ONLY=true
            ;;
        *)
            error "Unknown argument '$1'. Use: [debug|release] [--uninstall-first] [--bundle-only]"
            ;;
    esac
    shift
done

VARIANT_CAP="${VARIANT^}"

APP_NAME="BachataS4"
PACKAGE="com.bachatas4.android"
ACTIVITY=".MainActivity"

if $BUNDLE_ONLY; then
    GRADLE_TASK="bundle${VARIANT_CAP}"
    ARTIFACT_DIR="app/build/outputs/bundle/${VARIANT}"
    ARTIFACT_EXT="aab"
else
    GRADLE_TASK="assemble${VARIANT_CAP}"
    ARTIFACT_DIR="app/build/outputs/apk/${VARIANT}"
    ARTIFACT_EXT="apk"
fi

info "Building ${APP_NAME} ${ARTIFACT_EXT^^} variant: ${VARIANT_CAP}"
./gradlew "$GRADLE_TASK" --quiet

if $BUNDLE_ONLY; then
    ARTIFACT_PATH=$(find "$ARTIFACT_DIR" -maxdepth 1 -type f -name "*.aab" | sort | head -1)
else
    ARTIFACT_PATH=$(find "$ARTIFACT_DIR" -maxdepth 1 -type f -name "*.apk" ! -name "*-unsigned.apk" | sort | head -1)
fi

if [[ -z "$ARTIFACT_PATH" ]]; then
    error "${ARTIFACT_EXT^^} not found in $ARTIFACT_DIR"
fi

success "Build complete → ${ARTIFACT_PATH}"

if $BUNDLE_ONLY; then
    info "Bundle-only mode. Skipping install and launch."
    exit 0
fi

DEVICE_LIST=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ -z "$DEVICE_LIST" ]]; then
    error "No Android device connected"
fi

for DEVICE in $DEVICE_LIST; do
    info "Installing on device: $DEVICE"
    if $UNINSTALL_FIRST; then
        adb -s "$DEVICE" uninstall "$PACKAGE" >/dev/null 2>&1 || true
    fi
    adb -s "$DEVICE" install -r "$ARTIFACT_PATH"
    info "Launching ${PACKAGE}${ACTIVITY} on $DEVICE"
    adb -s "$DEVICE" shell am start -n "${PACKAGE}/${PACKAGE}${ACTIVITY}" && success "App launched!"
done
