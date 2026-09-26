#!/usr/bin/env bash
set -euo pipefail

macro="baseline-profile/src/main/java/com/cardenaspiero255/gamehubultra/baselineprofile/GameHubMacrobenchmark.kt"
baseline="baseline-profile/src/main/java/com/cardenaspiero255/gamehubultra/baselineprofile/BaselineProfileGenerator.kt"

test -f "$macro"
test -f "$baseline"

benchmark_count="$(grep -Ec '^    fun (coldStartup|navigationToLibrary|navigationToSettings)\(' "$macro")"
iteration_count="$(grep -Ec 'iterations = 5,' "$macro")"

if [ "$benchmark_count" -ne 3 ]; then
  echo "Expected exactly 3 required GameHub macrobenchmarks; found $benchmark_count."
  exit 1
fi

if [ "$iteration_count" -ne 3 ]; then
  echo "Each of the 3 macrobenchmarks must keep iterations = 5; found $iteration_count occurrences."
  exit 1
fi

grep -Fq 'val timeoutMs = if (runningOnEmulator) 2_000L else 8_000L' "$macro" || {
  echo "Expected emulator-only 2s navigation lookup timeout with the 8s physical-device timeout preserved."
  exit 1
}

grep -Fq 'repeat(3)' "$baseline" || {
  echo "Baseline startup retry safety must remain at 3 attempts."
  exit 1
}

echo "Benchmark fast-path contract is intact: 3 benchmarks, 5 iterations each, emulator-only shorter UI lookup, baseline retries preserved."
