#!/usr/bin/env bash
set -euo pipefail

ROOT="$(mktemp -d)"
trap 'rm -rf "$ROOT"' EXIT

SDK_ROOT="$ROOT/sdk"
FAKE_BIN="$ROOT/bin"
STATE="$ROOT/state"
STEP_SCRIPT="$ROOT/install-step.sh"
mkdir -p "$FAKE_BIN" "$STATE"

mkdir -p \
  "$SDK_ROOT/platforms/android-35" \
  "$SDK_ROOT/platforms/android-36" \
  "$SDK_ROOT/emulator" \
  "$SDK_ROOT/platform-tools" \
  "$SDK_ROOT/system-images/android-35/google_apis/x86_64/data"

printf 'android35' > "$SDK_ROOT/platforms/android-35/android.jar"
printf 'android36' > "$SDK_ROOT/platforms/android-36/android.jar"
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
STATE="${SDKMANAGER_TEST_STATE:?}"
COUNT_FILE="$STATE/count"
CALLS_FILE="$STATE/calls"
count=0
[ ! -f "$COUNT_FILE" ] || count="$(cat "$COUNT_FILE")"
count=$((count + 1))
printf '%s' "$count" > "$COUNT_FILE"
printf '%s\n' "$*" >> "$CALLS_FILE"

if [ "$count" -eq 1 ]; then
  exit 1
fi

for pkg in "$@"; do
  case "$pkg" in
    "build-tools;36.0.0")
      mkdir -p "$ANDROID_HOME/build-tools/36.0.0"
      printf '#!/usr/bin/env bash\nexit 0\n' > "$ANDROID_HOME/build-tools/36.0.0/aapt2"
      chmod +x "$ANDROID_HOME/build-tools/36.0.0/aapt2"
      ;;
    "platforms;android-35")
      mkdir -p "$ANDROID_HOME/platforms/android-35"
      printf 'android35' > "$ANDROID_HOME/platforms/android-35/android.jar"
      ;;
    "platforms;android-36")
      mkdir -p "$ANDROID_HOME/platforms/android-36"
      printf 'android36' > "$ANDROID_HOME/platforms/android-36/android.jar"
      ;;
    "emulator")
      mkdir -p "$ANDROID_HOME/emulator"
      printf '#!/usr/bin/env bash\nexit 0\n' > "$ANDROID_HOME/emulator/emulator"
      chmod +x "$ANDROID_HOME/emulator/emulator"
      ;;
    "platform-tools")
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
chmod +x "$FAKE_BIN/sdkmanager"

cat > "$FAKE_BIN/sleep" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$FAKE_BIN/sleep"

awk '
  $0 == "      - name: Install Android SDK packages" { step = 1; next }
  step && $0 ~ /^      - name:/ { exit }
  step && $0 == "        run: |" { run = 1; next }
  run {
    sub(/^          /, "")
    print
  }
' .github/workflows/android.yml > "$STEP_SCRIPT"

if [ ! -s "$STEP_SCRIPT" ]; then
  echo "Could not extract the Android SDK installation step."
  exit 1
fi

SDKMANAGER_TEST_STATE="$STATE" \
ANDROID_HOME="$SDK_ROOT" \
PATH="$FAKE_BIN:$PATH" \
bash "$STEP_SCRIPT"

test -s "$IMAGE_DIR/system.img"
test -s "$IMAGE_DIR/ramdisk.img"
test -e "$IMAGE_DIR/data/empty_data_disk"

if grep -Fq "system-images;android-35;google_apis;x86_64" "$STATE/calls"; then
  echo "Healthy API 35 system image was unexpectedly scheduled for installation."
  exit 1
fi

echo "Healthy API 35 system image survived a transient retry for another package."
