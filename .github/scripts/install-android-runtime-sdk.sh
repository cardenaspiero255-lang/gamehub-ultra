#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_HOME:-/usr/local/lib/android/sdk}"
IMAGE_DIR="$SDK_ROOT/system-images/android-35/google_apis/x86_64"
missing=()

[ -x "$SDK_ROOT/emulator/emulator" ] || missing+=("emulator")
[ -x "$SDK_ROOT/platform-tools/adb" ] || missing+=("platform-tools")
if [ ! -s "$IMAGE_DIR/system.img" ] || [ ! -s "$IMAGE_DIR/ramdisk.img" ] || [ ! -e "$IMAGE_DIR/data/empty_data_disk" ]; then
  missing+=("system-images;android-35;google_apis;x86_64")
fi

if [ "${#missing[@]}" -eq 0 ]; then
  echo "Android emulator runtime already present."
  exit 0
fi

for attempt in 1 2 3; do
  echo "Installing Android emulator runtime, attempt ${attempt}/3: ${missing[*]}"
  if sdkmanager "${missing[@]}"; then
    if [ -x "$SDK_ROOT/emulator/emulator" ] &&
       [ -x "$SDK_ROOT/platform-tools/adb" ] &&
       [ -s "$IMAGE_DIR/system.img" ] &&
       [ -s "$IMAGE_DIR/ramdisk.img" ] &&
       [ -e "$IMAGE_DIR/data/empty_data_disk" ]; then
      exit 0
    fi
  fi

  rm -rf "$SDK_ROOT/.temp"
  if printf '%s\n' "${missing[@]}" | grep -Fxq "system-images;android-35;google_apis;x86_64"; then
    rm -rf       "$SDK_ROOT/system-images/android-35/google_apis/x86_64"       "$SDK_ROOT/system-images/android-35/google_apis/.installer"       "$SDK_ROOT/system-images/android-35/.installer"
  fi
  if [ "$attempt" -lt 3 ]; then
    sleep $((attempt * 10))
  fi
done

echo "Android emulator runtime installation failed after retries." >&2
exit 1
