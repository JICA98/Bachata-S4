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
FLAVOR="fdroid"
UNINSTALL_FIRST=false
BUNDLE_ONLY=false

ADB="adb"
if command -v adb.exe &>/dev/null; then
    ADB="adb.exe"
fi

while [[ $# -gt 0 ]]; do
    case "$1" in
        debug|release)
            VARIANT="$1"
            ;;
        --fdroid)
            FLAVOR="fdroid"
            ;;
        --playstore)
            FLAVOR="playstore"
            ;;
        --flavor)
            if [[ $# -lt 2 ]]; then
                error "Missing argument for --flavor"
            fi
            FLAVOR="$2"
            shift
            ;;
        --uninstall-first)
            UNINSTALL_FIRST=true
            ;;
        --bundle-only)
            BUNDLE_ONLY=true
            ;;
        *)
            error "Unknown argument '$1'. Use: [debug|release] [--fdroid|--playstore] [--uninstall-first] [--bundle-only]"
            ;;
    esac
    shift
done

FLAVOR_CAP="${FLAVOR^}"
VARIANT_CAP="${VARIANT^}"

APP_NAME="BachataS4"
PACKAGE="com.bachatas4.android"
ACTIVITY=".MainActivity"

if $BUNDLE_ONLY; then
    GRADLE_TASK="bundle${FLAVOR_CAP}${VARIANT_CAP}"
    ARTIFACT_DIR="app/build/outputs/bundle/${FLAVOR}${VARIANT_CAP}"
    ARTIFACT_EXT="aab"
else
    if [[ "$VARIANT" != "debug" ]]; then
        error "Only debug APKs can be installed directly. Release artifacts require external signing."
    fi
    GRADLE_TASK="assemble${FLAVOR_CAP}${VARIANT_CAP}"
    ARTIFACT_DIR="app/build/outputs/apk/${FLAVOR}/${VARIANT}"
    ARTIFACT_EXT="apk"
fi

# Clean up Windows Zone.Identifier metadata files to prevent resource merger errors
find . -name '*Zone.Identifier' -delete 2>/dev/null || true

info "Building ${APP_NAME} ${ARTIFACT_EXT^^} flavor: ${FLAVOR_CAP}, variant: ${VARIANT_CAP}"
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

DEVICE_LIST=$("$ADB" devices | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ -z "$DEVICE_LIST" ]]; then
    error "No Android device connected"
fi

for DEVICE in $DEVICE_LIST; do
    info "Installing on device: $DEVICE"
    if $UNINSTALL_FIRST; then
        "$ADB" -s "$DEVICE" uninstall "$PACKAGE" >/dev/null 2>&1 || true
    fi
    "$ADB" -s "$DEVICE" install -r "$ARTIFACT_PATH"
    info "Launching ${PACKAGE}${ACTIVITY} on $DEVICE"
    "$ADB" -s "$DEVICE" shell am start -n "${PACKAGE}/${PACKAGE}${ACTIVITY}" && success "App launched!"
done
