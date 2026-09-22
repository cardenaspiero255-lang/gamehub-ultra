#!/usr/bin/env bash
set -euo pipefail

echo "== GameHub Ultra / Codex environment check =="

echo "Java:"
if command -v java >/dev/null 2>&1; then
  java -version 2>&1 | head -n 1
else
  echo "NOT FOUND"
fi

echo "Gradle:"
if command -v gradle >/dev/null 2>&1; then
  gradle --version | sed -n '1,8p'
else
  echo "NOT FOUND"
fi

echo "Proxy variables:"
for name in HTTP_PROXY HTTPS_PROXY ALL_PROXY NO_PROXY http_proxy https_proxy all_proxy no_proxy; do
  if [[ -n "${!name:-}" ]]; then echo "$name=SET"; else echo "$name=UNSET"; fi
done

candidates=()
[[ -n "${ANDROID_HOME:-}" ]] && candidates+=("$ANDROID_HOME")
[[ -n "${ANDROID_SDK_ROOT:-}" ]] && candidates+=("$ANDROID_SDK_ROOT")
candidates+=(
  "$HOME/Android/Sdk"
  "$HOME/android-sdk"
  "/opt/android-sdk"
  "/opt/android-sdk-linux"
  "/usr/local/lib/android/sdk"
)

sdk=""
for candidate in "${candidates[@]}"; do
  if [[ -d "$candidate" ]]; then sdk="$candidate"; break; fi
done

if [[ -z "$sdk" ]]; then
  echo "Android SDK: NOT FOUND"
  exit 2
fi

echo "Android SDK: $sdk"
[[ -x "$sdk/platform-tools/adb" ]] && echo "platform-tools: OK" || echo "platform-tools: MISSING"
[[ -f "$sdk/platforms/android-35/android.jar" ]] && echo "platform android-35: OK" || echo "platform android-35: MISSING"
[[ -x "$sdk/build-tools/35.0.0/aapt2" ]] && echo "build-tools 35.0.0: OK" || echo "build-tools 35.0.0: MISSING"

escaped_sdk=${sdk//\\/\\\\}
escaped_sdk=${escaped_sdk//:/\\:}
escaped_sdk=${escaped_sdk// /\\ }
printf 'sdk.dir=%s\n' "$escaped_sdk" > local.properties
echo "local.properties: configured for this workspace"
echo "Environment check complete."
