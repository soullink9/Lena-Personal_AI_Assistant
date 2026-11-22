#!/usr/bin/env bash
# Checks how much conversation a request to Open WebUI carries and when a quiet chat is compacted
# (Compaction.kt) away from Android: with Context Compaction on, a request stays under its threshold so her
# answer never waits for a summary. Plain Kotlin, so it runs on any computer with kotlinc or Gradle (Gradle's
# own Kotlin compiler, as commands_check does).
#
#   tests/app/compaction_check/run.sh        (nothing is downloaded)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
src="$repo/android/app/src/main/kotlin/io/github/soullink9/lena"
sources=("$src/Compaction.kt" "$here/CompactionCheck.kt")

if command -v kotlinc >/dev/null; then
  kotlinc -nowarn "${sources[@]}" -include-runtime -d "$work/check.jar" 2>&1 | grep -v "^warning" || true
  java -jar "$work/check.jar"
elif command -v gradle >/dev/null; then
  lib="$(cd "$(dirname "$(readlink -f "$(command -v gradle)")")/../lib" && pwd)"
  lib_jar() { find "$lib" -maxdepth 1 -name "$1-[0-9]*.jar" | head -1; }
  stdlib="$(lib_jar kotlin-stdlib)"
  compiler="$(lib_jar kotlin-compiler-embeddable):$stdlib:$(lib_jar kotlin-script-runtime):$(lib_jar kotlin-reflect)"
  compiler="$compiler:$(lib_jar kotlin-daemon-embeddable):$(lib_jar trove4j):$(lib_jar annotations):$(lib_jar kotlinx-coroutines-core-jvm)"
  java -cp "$compiler" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -nowarn -no-stdlib -no-reflect \
    -classpath "$stdlib" "${sources[@]}" -d "$work/classes" 2>&1 | grep -v "^warning" || true
  java -cp "$work/classes:$stdlib" CompactionCheckKt
else
  echo "Neither kotlinc nor Gradle is installed here; skipping the compaction check (CI runs it)." >&2
  exit 0
fi
