#!/usr/bin/env bash
# Builds the app's release APK, names it lena-<label>.apk and writes SHA256SUMS next to it.
#
#   scripts/build_apk.sh v0.6.12      a release: lena-v0.6.12.apk (the name scripts/release_notes.py links to)
#   scripts/build_apk.sh 1a2b3c4      a test build, named after its commit
#
# Signs with your key when LENA_KEYSTORE and LENA_KEYSTORE_PASSWORD are set (the workflows load them from the
# KEYSTORE_B64 and KEYSTORE_PASSWORD secrets); otherwise it's debug-signed, which scripts/check_apk.py refuses
# for a release. Needs Gradle 8.14.3, a JDK and the Android SDK (scripts/android_sdk.sh on a runner without one).
# Used by every workflow that builds the app, on Gitea and on GitHub.
#
# Prints the APK's path last. On failure, the compiler's errors are also printed as one ::error line, which
# GitHub and Gitea show on the run without opening the full log.
set -euo pipefail
label="${1:?Usage: build_apk.sh <label>}"
repo="$(cd "$(dirname "$0")/.." && pwd)"
log="$(mktemp)"

cd "$repo/android"
if ! gradle --no-daemon assembleRelease 2>&1 | tee "$log"; then
  msg=$(grep -E '^e: |What went wrong|^> |^  > |error:|Error:' "$log" \
    | sed "s#file://$repo/##; s#$repo/##" | head -60 \
    | sed ':a;N;$!ba;s/%/%25/g;s/\n/%0A/g')
  echo "::error title=Build failed::${msg:-see the build log}"
  exit 1
fi
cd app/build/outputs/apk/release
rm -f lena-*.apk SHA256SUMS   # an earlier build's files would end up in SHA256SUMS and the release
mv app-release.apk "lena-${label}.apk"
sha256sum "lena-${label}.apk" | tee SHA256SUMS
echo "$PWD/lena-${label}.apk"
