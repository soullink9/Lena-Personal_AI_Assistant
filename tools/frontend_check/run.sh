#!/usr/bin/env bash
# Checks the app's MicroFrontend.java against the original C (TensorFlow Lite Micro's audio
# front end, copied into vendor/) on test audio. Every feature must match exactly,
# because the "Lena" wake word model was trained on the C's output.
#
#   tools/frontend_check/run.sh        (needs g++, python3 and a JDK; nothing is downloaded)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

src="$here/vendor"
lib="$src/tensorflow/lite/experimental/microfrontend/lib"
g++ -O2 -I"$src" -I"$src/kissfft" -o "$work/ref" "$here/ref.cc" \
  "$lib"/{frontend,frontend_util,window,window_util,fft,fft_util,kiss_fft_int16,filterbank,filterbank_util}.cc \
  "$lib"/{noise_reduction,noise_reduction_util,pcan_gain_control,pcan_gain_control_util,log_scale,log_scale_util,log_lut}.cc -lm

javac -d "$work/java" "$repo/android/app/src/main/java/io/github/soullink9/lena/MicroFrontend.java" "$here/Main.java"

for seed in 1 2 3; do
  python3 "$here/gen.py" x "$seed" > "$work/a.raw"
  "$work/ref" < "$work/a.raw" > "$work/c.txt"
  java -cp "$work/java" Main < "$work/a.raw" > "$work/j.txt"
  if ! cmp -s "$work/c.txt" "$work/j.txt"; then
    echo "Seed $seed: MicroFrontend.java differs from the C front end:" >&2
    diff "$work/c.txt" "$work/j.txt" | head -20 >&2
    exit 1
  fi
  echo "Seed $seed: $(wc -l < "$work/c.txt") frames match."
done
