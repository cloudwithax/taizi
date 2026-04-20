#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./adbshot.sh [top|bottom|both] [output]

Examples:
  ./adbshot.sh top
  ./adbshot.sh bottom /tmp/bottom.png
  ./adbshot.sh both /tmp/rgds

Notes:
  - On this RG dual-screen device, screencap uses SurfaceFlinger display ids:
      top    -> 0
      bottom -> 1
  - If mode is "both", output is treated as a prefix and the script writes:
      <prefix>-top.png
      <prefix>-bottom.png
  - If output is omitted, files are written in the current directory.
  - Set ADB_SERIAL to target a specific device when more than one is attached.
EOF
}

die() {
  echo "error: $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"
}

adb_cmd() {
  if [[ -n "${ADB_SERIAL:-}" ]]; then
    adb -s "$ADB_SERIAL" "$@"
  else
    adb "$@"
  fi
}

ensure_device() {
  local state
  if ! state="$(adb_cmd get-state 2>/dev/null)"; then
    die "adb device not available"
  fi
  [[ "$state" == "device" ]] || die "adb state is '$state', expected 'device'"
}

capture() {
  local display_id="$1"
  local outfile="$2"

  adb_cmd exec-out screencap -d "$display_id" -p >"$outfile"
  file "$outfile" | grep -q "PNG image data" || die "capture failed for display $display_id: $outfile"
  echo "$outfile"
}

main() {
  require_cmd adb
  require_cmd file
  ensure_device

  local mode="${1:-top}"
  local output="${2:-}"
  local default_prefix="screenshot"

  case "$mode" in
    top)
      capture 0 "${output:-${default_prefix}-top.png}"
      ;;
    bottom)
      capture 1 "${output:-${default_prefix}-bottom.png}"
      ;;
    both)
      local prefix="${output:-$default_prefix}"
      capture 0 "${prefix}-top.png"
      capture 1 "${prefix}-bottom.png"
      ;;
    -h|--help|help)
      usage
      ;;
    *)
      usage >&2
      die "invalid mode: $mode"
      ;;
  esac
}

main "$@"
