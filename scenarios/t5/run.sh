#!/usr/bin/env bash
# T5 - what a microbenchmark measures when it is not JMH.
#
# usage: scenarios/t5/run.sh <run-number>
#
# Both harnesses call the same code in the same container with the same heap. Any difference is
# the harness.
set -euo pipefail

RUN="${1:?run number required}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$ROOT/reports/data/t5-run$RUN-$STAMP"
COMPOSE="docker compose -f $ROOT/ops/docker-compose.yml"

JMH_FORKS="${JMH_FORKS:-3}"
JMH_WARMUP="${JMH_WARMUP:-5}"
JMH_ITERATIONS="${JMH_ITERATIONS:-5}"
# A fixed heap for both harnesses. Left to the container default the two would not even be
# running the same JVM configuration, which is the one thing this comparison cannot afford.
HEAP="${HEAP:--Xms1g -Xmx1g}"

mkdir -p "$OUT"
echo "== T5 run$RUN -> $OUT"

echo "-- building both harnesses"
(cd "$ROOT" && ./gradlew naiveJar jmhJar -q) >"$OUT/build.log" 2>&1
mkdir -p "$ROOT/build/bench"
cp "$ROOT/build/libs/naive.jar" "$ROOT/build/bench/naive.jar"
cp "$ROOT/build/libs/jvm-bench-lab-jmh.jar" "$ROOT/build/bench/jmh.jar"
cp "$ROOT/ops/Dockerfile" "$ROOT/build/bench/Dockerfile"

$COMPOSE up -d --build >"$OUT/compose.log" 2>&1
CID=$($COMPOSE ps -q bench)

$COMPOSE exec -T bench sh -c 'java -version' >"$OUT/java-version.txt" 2>&1
$COMPOSE exec -T bench sh -c 'nproc; cat /sys/fs/cgroup/cpu.max 2>/dev/null || true' \
  >"$OUT/cpu.txt" 2>&1

echo "-- naive harness"
$COMPOSE exec -T bench sh -c "java $HEAP -jar /opt/bench/naive.jar" >"$OUT/naive.json" 2>"$OUT/naive.err"

echo "-- JMH: $JMH_FORKS forks, $JMH_WARMUP warmup + $JMH_ITERATIONS measurement iterations"
$COMPOSE exec -T bench sh -c \
  "java $HEAP -jar /opt/bench/jmh.jar -f $JMH_FORKS -wi $JMH_WARMUP -i $JMH_ITERATIONS \
   -rf json -rff /opt/bench/jmh.json" >"$OUT/jmh.log" 2>&1
$COMPOSE exec -T bench sh -c 'cat /opt/bench/jmh.json' >"$OUT/jmh.json" 2>/dev/null || true

cat >"$OUT/meta.json" <<META
{
  "scenario": "t5",
  "run": $RUN,
  "heap": "$HEAP",
  "jmh_forks": $JMH_FORKS,
  "jmh_warmup_iterations": $JMH_WARMUP,
  "jmh_measurement_iterations": $JMH_ITERATIONS,
  "host": "$(uname -sm)",
  "container_cpus": 2,
  "git_commit": "$(cd "$ROOT" && git rev-parse --short HEAD 2>/dev/null || echo unknown)"
}
META

$COMPOSE down >>"$OUT/compose.log" 2>&1 || true

python3 "$ROOT/scenarios/t5/collect.py" "$OUT" >/dev/null
echo "== done: $OUT/summary.md"
cat "$OUT/summary.md"
