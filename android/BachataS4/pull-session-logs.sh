#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

PACKAGE="com.bachatas4.android"
LOGS_DIR="files/logs"
OUTPUT_ROOT="${SCRIPT_DIR}/session-logs"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

info()    { echo -e "${CYAN}[INFO]${NC} $*"; }
success() { echo -e "${GREEN}[OK]${NC} $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $*" >&2; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }

usage() {
    cat <<'EOF'
Pull Bachata S4 session logs from a connected Android device.

Usage:
  ./pull-session-logs.sh [--list]
  ./pull-session-logs.sh [--latest | --session <name> | --game <game-id-substring>]
  ./pull-session-logs.sh --all

Options:
  --list              List session folders on the device (default when no pull flag is given)
  --latest            Pull the newest session folder (default pull mode)
  --session <name>    Pull one session folder by exact name
  --game <substring>  Pull the newest session whose folder name contains the substring
  --all               Pull every retained session folder (up to 10)
  --output <dir>      Destination root (default: android/BachataS4/session-logs)

Each session folder contains:
  application.log      Bachata app lifecycle and diagnostics
  shadps4.log            Box64/shadPS4 backend stdout/stderr
  shadps4-internal.log   Copied shadPS4 internal log after session end (if present)

Requires a debug build installed on the device. App-private storage is read with:
  adb exec-out run-as com.bachatas4.android ...
EOF
}

ADB="adb"
if command -v adb.exe &>/dev/null; then
    ADB="adb.exe"
fi

MODE="list"
SESSION_NAME=""
GAME_FILTER=""
OUTPUT_ROOT="${OUTPUT_ROOT}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --list)
            MODE="list"
            ;;
        --latest)
            MODE="latest"
            ;;
        --all)
            MODE="all"
            ;;
        --session)
            [[ $# -ge 2 ]] || error "Missing value for --session"
            MODE="session"
            SESSION_NAME="$2"
            shift
            ;;
        --game)
            [[ $# -ge 2 ]] || error "Missing value for --game"
            MODE="game"
            GAME_FILTER="$2"
            shift
            ;;
        --output)
            [[ $# -ge 2 ]] || error "Missing value for --output"
            OUTPUT_ROOT="$2"
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            error "Unknown argument '$1'. Use --help for usage."
            ;;
    esac
    shift
done

DEVICE_LIST=$("$ADB" devices | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ -z "$DEVICE_LIST" ]]; then
    error "No Android device connected"
fi

DEVICE=$(echo "$DEVICE_LIST" | head -1)
if [[ $(echo "$DEVICE_LIST" | wc -l) -gt 1 ]]; then
    warn "Multiple devices connected; using $DEVICE"
fi

adb_run_as() {
    "$ADB" -s "$DEVICE" exec-out run-as "$PACKAGE" "$@"
}

list_sessions() {
    adb_run_as sh -c "cd '$LOGS_DIR' 2>/dev/null && ls -1" | tr -d '\r' | sed '/^$/d' | sort
}

ensure_package_installed() {
    if ! "$ADB" -s "$DEVICE" shell pm path "$PACKAGE" >/dev/null 2>&1; then
        error "Package $PACKAGE is not installed on $DEVICE"
    fi
}

pull_session() {
    local session="$1"
    local dest="${OUTPUT_ROOT}/${session}"
    mkdir -p "$dest"

    local pulled_any=false
    for file in application.log shadps4.log shadps4-internal.log; do
        if adb_run_as sh -c "test -f '$LOGS_DIR/$session/$file'" >/dev/null 2>&1; then
            adb_run_as cat "$LOGS_DIR/$session/$file" > "${dest}/${file}"
            pulled_any=true
            success "Wrote ${dest}/${file}"
        else
            warn "Missing ${session}/${file} on device"
        fi
    done

    if ! $pulled_any; then
        error "No log files found for session '$session'"
    fi
}

ensure_package_installed

SESSIONS=$(list_sessions || true)
if [[ -z "$SESSIONS" ]]; then
    error "No session folders found at ${PACKAGE}/${LOGS_DIR}. Launch a game session first, then stop it."
fi

case "$MODE" in
    list)
        info "Session folders on $DEVICE:"
        echo "$SESSIONS"
        info "Tip: ./pull-session-logs.sh --latest"
        ;;
    latest)
        SESSION=$(echo "$SESSIONS" | tail -1)
        info "Pulling latest session: $SESSION"
        pull_session "$SESSION"
        ;;
    session)
        if ! echo "$SESSIONS" | grep -Fxq "$SESSION_NAME"; then
            error "Session '$SESSION_NAME' not found. Use --list to see available folders."
        fi
        pull_session "$SESSION_NAME"
        ;;
    game)
        SESSION=$(echo "$SESSIONS" | grep -F "$GAME_FILTER" | tail -1 || true)
        if [[ -z "$SESSION" ]]; then
            error "No session folder matched --game '$GAME_FILTER'"
        fi
        info "Pulling newest matching session: $SESSION"
        pull_session "$SESSION"
        ;;
    all)
        while IFS= read -r session; do
            [[ -n "$session" ]] || continue
            pull_session "$session"
        done <<< "$SESSIONS"
        ;;
esac