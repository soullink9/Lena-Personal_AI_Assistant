#!/usr/bin/env bash
# Checks what Lena understands on her own (android/.../Commands.kt: timers, alarms, "stop") against
# example sentences, with the rest of the app replaced by the stand-ins in Stubs.kt.
#
#   tools/commands_check/run.sh        (needs kotlinc, which GitHub's Ubuntu runners have; nothing is downloaded)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

if ! command -v kotlinc >/dev/null; then
  echo "kotlinc isn't installed here; skipping the Commands check (CI runs it)." >&2
  exit 0
fi

# The app's file as it is, without its package and Android import, so it compiles beside the stand-ins.
sed -e '/^package /d' -e '/^import android\./d' \
  "$repo/android/app/src/main/kotlin/io/github/soullink9/lena/Commands.kt" > "$work/Commands.kt"
kotlinc -nowarn "$work/Commands.kt" "$here/Stubs.kt" "$here/CommandsCheck.kt" -include-runtime -d "$work/check.jar" 2>&1 \
  | grep -v "^warning" || true
java -jar "$work/check.jar"
