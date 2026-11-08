#!/usr/bin/env bash
# Installs the Android SDK the app's build needs, straight from Google, for a runner that doesn't have one (the
# Gitea runner's jobs run in the official gradle:8.14.3-jdk21 image, which has Gradle and a JDK but no SDK).
#
#   scripts/android_sdk.sh            (installs into $ANDROID_HOME, default /opt/android-sdk)
#
# Google's command-line tools are downloaded from dl.google.com and checked against the SHA-256 Google publishes
# for them (developer.android.com/studio, "Command line tools only"); they then install Android 16's platform
# (compileSdk 36). Android's build tools come the same way: the Android Gradle Plugin fetches the version it
# wants by itself once the licences are accepted. Nothing comes from GitHub.
#
# To move to newer command-line tools, take the Linux file name and its SHA-256 from that page.
set -euo pipefail
sdk="${ANDROID_HOME:-/opt/android-sdk}"
tools_zip="commandlinetools-linux-15859902_latest.zip"
tools_sha256="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"
sdkmanager="$sdk/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$sdkmanager" ]; then
  work="$(mktemp -d)"
  curl -fsSL -o "$work/$tools_zip" "https://dl.google.com/android/repository/$tools_zip"
  echo "$tools_sha256  $work/$tools_zip" | sha256sum -c -
  unzip -q "$work/$tools_zip" -d "$work"
  mkdir -p "$sdk/cmdline-tools"
  mv "$work/cmdline-tools" "$sdk/cmdline-tools/latest"   # sdkmanager insists on this layout
  rm -rf "$work"
fi

# The licences must be accepted once per SDK, or Gradle refuses to build. (yes stops when sdkmanager has read
# enough, which isn't a failure.)
{ yes || true; } | "$sdkmanager" --sdk_root="$sdk" --licenses > /dev/null
"$sdkmanager" --sdk_root="$sdk" "platforms;android-36"
echo "Android SDK ready in $sdk"
