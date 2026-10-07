#!/usr/bin/env bash
set -euo pipefail

ROOT="$(mktemp -d)"
trap 'rm -rf "$ROOT"' EXIT

SDK_ROOT="$ROOT/sdk"
FAKE_BIN="$ROOT/bin"
STATE="$ROOT/state"
mkdir -p   "$FAKE_BIN"   "$STATE"   "$SDK_ROOT/emulator"   "$SDK_ROOT/platform-tools"   "$SDK_ROOT/system-images/android-35/google_apis/x86_64/data"

printf '#!/usr/bin/env bash\nexit 0\n' > "$SDK_ROOT/emulator/emulator"
printf '#!/usr/bin/env bash\nexit 0\n' > "$SDK_ROOT/platform-tools/adb"
chmod +x "$SDK_ROOT/emulator/emulator" "$SDK_ROOT/platform-tools/adb"

IMAGE_DIR="$SDK_ROOT/system-images/android-35/google_apis/x86_64"
printf 'healthy-system' > "$IMAGE_DIR/system.img"
printf 'healthy-ramdisk' > "$IMAGE_DIR/ramdisk.img"
: > "$IMAGE_DIR/data/empty_data_disk"

cat > "$FAKE_BIN/sdkmanager" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$SDKMANAGER_TEST_STATE/calls"
exit 0
EOF
chmod +x "$FAKE_BIN/sdkmanager"

SDKMANAGER_TEST_STATE="$STATE" ANDROID_HOME="$SDK_ROOT" PATH="$FAKE_BIN:$PATH" bash .github/scripts/install-android-runtime-sdk.sh

if [ -f "$STATE/calls" ]; then
  echo "Healthy Android emulator runtime was unexpectedly reinstalled."
  cat "$STATE/calls"
  exit 1
fi

rm "$SDK_ROOT/emulator/emulator"
cat > "$FAKE_BIN/sdkmanager" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
count=0
[ ! -f "$SDKMANAGER_TEST_STATE/count" ] || count="$(cat "$SDKMANAGER_TEST_STATE/count")"
count=$((count + 1))
printf '%s' "$count" > "$SDKMANAGER_TEST_STATE/count"
printf '%s\n' "$*" >> "$SDKMANAGER_TEST_STATE/calls"

if [ "$count" -eq 1 ]; then
  exit 1
fi

for pkg in "$@"; do
  case "$pkg" in
    emulator)
      mkdir -p "$ANDROID_HOME/emulator"
      printf '#!/usr/bin/env bash\nexit 0\n' > "$ANDROID_HOME/emulator/emulator"
      chmod +x "$ANDROID_HOME/emulator/emulator"
      ;;
    platform-tools)
      mkdir -p "$ANDROID_HOME/platform-tools"
      printf '#!/usr/bin/env bash\nexit 0\n' > "$ANDROID_HOME/platform-tools/adb"
      chmod +x "$ANDROID_HOME/platform-tools/adb"
      ;;
    "system-images;android-35;google_apis;x86_64")
      dir="$ANDROID_HOME/system-images/android-35/google_apis/x86_64"
      mkdir -p "$dir/data"
      printf 'installed-system' > "$dir/system.img"
      printf 'installed-ramdisk' > "$dir/ramdisk.img"
      : > "$dir/data/empty_data_disk"
      ;;
  esac
done
EOF
cat > "$FAKE_BIN/sleep" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$FAKE_BIN/sdkmanager" "$FAKE_BIN/sleep"

SDKMANAGER_TEST_STATE="$STATE" ANDROID_HOME="$SDK_ROOT" PATH="$FAKE_BIN:$PATH" bash .github/scripts/install-android-runtime-sdk.sh

test -x "$SDK_ROOT/emulator/emulator"
test -x "$SDK_ROOT/platform-tools/adb"
test -s "$IMAGE_DIR/system.img"
test -s "$IMAGE_DIR/ramdisk.img"
test -e "$IMAGE_DIR/data/empty_data_disk"

tail -n 2 "$STATE/calls" | grep -Fxq "emulator"
if tail -n 2 "$STATE/calls" | grep -Fq "system-images;android-35;google_apis;x86_64"; then
  echo "Healthy API 35 system image was unexpectedly scheduled during emulator-only retry."
  exit 1
fi

echo "Android runtime installer preserves healthy packages and retries only missing components."
