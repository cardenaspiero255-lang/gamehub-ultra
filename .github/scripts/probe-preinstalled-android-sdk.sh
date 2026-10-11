#!/usr/bin/env bash
# Fast-path: trust only an SDK already provisioned with licensed tools on the
# GitHub-hosted image. Any missing component forces the pinned setup action.
set -euo pipefail

: "${GITHUB_OUTPUT:?GitHub Actions step output path is required}"
: "${GITHUB_PATH:?GitHub Actions PATH export is required}"

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
cmdline_bin="$sdk_root/cmdline-tools/latest/bin"
platform_bin="$sdk_root/platform-tools"

usable=false
if [[ -x "$cmdline_bin/sdkmanager" ]] &&
   [[ -x "$platform_bin/adb" ]] &&
   [[ -s "$sdk_root/licenses/android-sdk-license" ]]; then
  usable=true
fi

printf 'usable=%s\n' "$usable" >> "$GITHUB_OUTPUT"
if [[ "$usable" == true ]]; then
  printf '%s\n' "$cmdline_bin" "$platform_bin" >> "$GITHUB_PATH"
  echo "Using image-preinstalled, licensed Android SDK (no network download)."
else
  echo "Android SDK tools/license incomplete; pinned setup-android fallback required."
fi
