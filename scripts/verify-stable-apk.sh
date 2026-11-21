#!/usr/bin/env bash
set -euo pipefail
APK="${1:?Usage: verify-stable-apk.sh apk-path}"
EXPECTED="$(tr -d '[:space:]' < signing/expected-cert-sha256.txt | tr '[:lower:]' '[:upper:]')"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
APKSIGNER="$SDK/build-tools/35.0.0/apksigner"
AAPT="$SDK/build-tools/35.0.0/aapt"
test -x "$APKSIGNER" || { echo "Missing apksigner" >&2; exit 1; }
test -x "$AAPT" || { echo "Missing aapt" >&2; exit 1; }
test -s "$APK" || { echo "Missing APK" >&2; exit 1; }
"$APKSIGNER" verify --min-sdk-version 29 --verbose "$APK"
ACTUAL="$("$APKSIGNER" verify --print-certs "$APK" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -1 | tr '[:lower:]' '[:upper:]')"
test -n "$ACTUAL" || { echo "No certificate" >&2; exit 1; }
if [[ "$ACTUAL" != "$EXPECTED" ]]; then
  echo "BLOCKED: APK signed by unexpected certificate" >&2
  exit 1
fi
"$AAPT" dump badging "$APK" | grep -F "package: name='com.newsvoice.tts.stable'" >/dev/null ||
    { echo "BLOCKED: wrong Android package ID" >&2; exit 1; }
echo "PASS: stable APK certificate and app identity verified"
