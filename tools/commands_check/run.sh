#!/usr/bin/env bash
# Checks what Lena understands on her own (android/.../Commands.kt: timers, alarms, "stop") against
# example sentences, with the rest of the app replaced by the stand-ins in Stubs.kt.
#
#   tools/commands_check/run.sh        (needs kotlinc, or Gradle; nothing is downloaded)
#
# kotlinc is on GitHub's Ubuntu runners. Elsewhere (the Gitea runner's gradle image, a computer with Gradle) it
# uses the Kotlin compiler that comes inside Gradle itself, in Gradle's lib folder: older than the app's Kotlin,
# but Commands.kt is plain Kotlin, and it saves downloading a compiler.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# The app's file as it is, without its package and Android import, so it compiles beside the stand-ins.
sed -e '/^package /d' -e '/^import android\./d' \
  "$repo/android/app/src/main/kotlin/io/github/soullink9/lena/Commands.kt" > "$work/Commands.kt"
sources=("$work/Commands.kt" "$here/Stubs.kt" "$here/CommandsCheck.kt")

if command -v kotlinc >/dev/null; then
  kotlinc -nowarn "${sources[@]}" -include-runtime -d "$work/check.jar" 2>&1 | grep -v "^warning" || true
  java -jar "$work/check.jar"
elif command -v gradle >/dev/null; then
  lib="$(cd "$(dirname "$(readlink -f "$(command -v gradle)")")/../lib" && pwd)"
  lib_jar() { find "$lib" -maxdepth 1 -name "$1-[0-9]*.jar" | head -1; }
  stdlib="$(lib_jar kotlin-stdlib)"
  compiler="$(lib_jar kotlin-compiler-embeddable):$stdlib:$(lib_jar kotlin-script-runtime):$(lib_jar kotlin-reflect)"
  compiler="$compiler:$(lib_jar kotlin-daemon-embeddable):$(lib_jar trove4j):$(lib_jar annotations):$(lib_jar kotlinx-coroutines-core-jvm)"
  echo "Compiling with the Kotlin compiler inside Gradle ($(basename "$(lib_jar kotlin-compiler-embeddable)" .jar))"
  java -cp "$compiler" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -nowarn -no-stdlib -no-reflect \
    -classpath "$stdlib" "${sources[@]}" -d "$work/classes" 2>&1 | grep -v "^warning" || true
  java -cp "$work/classes:$stdlib" CommandsCheckKt
else
  echo "Neither kotlinc nor Gradle is installed here; skipping the Commands check (CI runs it)." >&2
  exit 0
fi
