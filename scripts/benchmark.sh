#!/usr/bin/env bash
# Reproduces the measurements in docs/quality/measurement-evidence.md.
# Each run starts the packaged application with its own settings on port 8080, applies the load with
# loadtest/LoadTest.java (JDK 21+, no install needed) and stops the application again.
#
#   ./mvnw -q -DskipTests package
#   bash scripts/benchmark.sh all          # baseline, mitigated, nominal, surge, malformed (about 10 minutes)
#   bash scripts/benchmark.sh baseline     # or: mitigated | nominal | surge | malformed | surge-unlimited
# Circuit-breaker-only variant: add --resilience4j.bulkhead.instances.paymentGateway.max-concurrent-calls=1000 to the mitigated run.
#
# Results are appended to target/benchmark-results.md; application logs go to target/bench-<run>.log.
set -euo pipefail
cd "$(dirname "$0")/.."

JAR=$(ls target/*.jar 2>/dev/null | grep -v original | head -1 || true)
[ -n "$JAR" ] || { echo "Build first: ./mvnw -q -DskipTests package"; exit 1; }
OUT=target/benchmark-results.md
BASE=http://localhost:8080
DEGRADED="--payment.gateway.slow-call-ratio=0.6 --payment.gateway.slow-latency=5s --payment.gateway.read-timeout=3s"

run() { # run <name> "<app args>" <loadtest args...>
  local name=$1 app_args=$2; shift 2
  echo "== $name: java -jar $JAR $app_args"
  # shellcheck disable=SC2086
  java -jar "$JAR" $app_args > "target/bench-$name.log" 2>&1 &
  local pid=$!
  for _ in $(seq 1 60); do
    curl -s -o /dev/null "$BASE/actuator/health" && break
    sleep 1
  done
  echo "" >> "$OUT"
  echo "<!-- run: $name | app: $app_args | load: $* -->" >> "$OUT"
  java loadtest/LoadTest.java "$@" --base "$BASE" --out "$OUT" || true
  kill "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
  sleep 2
}

case "${1:-all}" in
  baseline|all)
    run baseline "--payment.resilience.enabled=false --checkout.rate-limit.enabled=false $DEGRADED" \
      payment --rate 120 --duration 60 --probe-rate 20 ;;&
  mitigated|all)
    run mitigated "--checkout.rate-limit.enabled=false $DEGRADED" \
      payment --rate 120 --duration 60 --probe-rate 20 ;;&
  nominal|all)
    run nominal "" nominal --rate 100 --duration 300 ;;&
  surge|all)
    run surge "" surge --from 20 --to 1200 --ramp 10 --hold 20 ;;&
  malformed|all)
    run malformed "" malformed --rate 125 --duration 40 ;;&
  surge-unlimited)
    run surge-unlimited "--checkout.rate-limit.enabled=false" surge --from 20 --to 1200 --ramp 10 --hold 20 ;;
  *) ;;
esac
echo "Results appended to $OUT"
