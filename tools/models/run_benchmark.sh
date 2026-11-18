#!/usr/bin/env bash
# Times BioCAP's and BioCLIP 2's image encoders on the phone connected over adb.
#
#   tools/models/run_benchmark.sh <run-id> [am start extras...]
#
# <run-id> is a run of the Model pack workflow with build "benchmark" (export_candidate.py), whose artifacts are kept
# for a week. This installs the debug build (built first if there is none), downloads each model's artifact in turn,
# pushes it to the app's external benchmark folder on the phone and deletes it here, since disk is short on this
# machine. Then it starts EncoderBenchmarkActivity, which times each model and the installed pack's encoder one after
# another, and copies its results to build/benchmark/. The phone must be unlocked, and is best on charge, cool, with
# other apps closed. A run of four models takes about ten minutes on a recent phone.
#
# Extras go to `adb shell am start`, for example `--ei runs 40` or `--es models bioclip2-visual-8bit.onnx`, and
# `--ei threads 4` and `--ei pause 20` are the defaults. With SKIP_DOWNLOAD=1 the models already on the phone are
# timed again. The models stay on the phone afterwards; the last line printed says how to remove them.
set -euo pipefail

run_id=${1:?Please give the run ID of a Model pack workflow run with build "benchmark".}
shift
root=$(cd "$(dirname "$0")/../.." && pwd)
adb=${ADB:-$(command -v adb || echo "$HOME/Android/Sdk/platform-tools/adb")}
package=io.github.wkoller.pollenplot.debug
activity=$package/io.github.wkoller.pollenplot.bench.EncoderBenchmarkActivity
remote=/sdcard/Android/data/$package/files/benchmark
out=$root/build/benchmark

"$adb" get-state >/dev/null
apk=$root/app/build/outputs/apk/debug/app-debug.apk
if [ ! -f "$apk" ]; then
  (cd "$root" && JAVA_HOME=${JAVA_HOME:-$HOME/.local/opt/jdk-17} ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk} ./gradlew :app:assembleDebug)
fi
"$adb" install -r "$apk" >/dev/null
"$adb" shell mkdir -p "$remote"

if [ "${SKIP_DOWNLOAD:-0}" != 1 ]; then
  scratch=$(mktemp -d)
  trap 'rm -rf "$scratch"' EXIT
  for artifact in encoders-biocap encoders-bioclip2; do
    echo "Downloading $artifact."
    gh run download "$run_id" --name "$artifact" --dir "$scratch/$artifact"
    for model in "$scratch/$artifact"/*.onnx; do
      "$adb" push "$model" "$remote/" >/dev/null
      echo "Pushed $(basename "$model")."
    done
    rm -rf "${scratch:?}/$artifact"
  done
fi

"$adb" shell rm -f "$remote/results.json" "$remote/results.md"
"$adb" logcat -c
"$adb" logcat -s EncoderBenchmark:I -v raw &
logcat=$!
trap 'kill $logcat 2>/dev/null || true; rm -rf "${scratch:-/nonexistent}"' EXIT
"$adb" shell am start -S -W -n "$activity" "$@" >/dev/null

waited=0
until "$adb" shell test -f "$remote/results.md"; do
  sleep 10
  waited=$((waited + 10))
  if [ "$waited" -ge 3600 ]; then
    echo "No results after an hour; please look at the phone." >&2
    exit 1
  fi
done
sleep 1
mkdir -p "$out"
stamp=$(date +%Y%m%d-%H%M%S)
"$adb" pull "$remote/results.json" "$out/results-$stamp.json" >/dev/null
"$adb" pull "$remote/results.md" "$out/results-$stamp.md" >/dev/null
echo
cat "$out/results-$stamp.md"
echo
echo "Saved to $out/results-$stamp.md. To remove the models from the phone: $adb shell rm -r $remote"
