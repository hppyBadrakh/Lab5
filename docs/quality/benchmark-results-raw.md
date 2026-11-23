# Raw Benchmark Output (Week 11)

Unedited output of `scripts/benchmark.sh` (loadtest/LoadTest.java), recorded 2026-11-23 on an Intel Core i7-11800H (8 cores / 16 threads), 15.8 GB RAM, Windows 11, Temurin JDK 25.0.4, Spring Boot 3.5.16 (Tomcat, 200 worker threads). Load generator and application ran on the same machine. Analysis: [measurement-evidence.md](measurement-evidence.md).

<!-- run: baseline | app: --payment.resilience.enabled=false --checkout.rate-limit.enabled=false --payment.gateway.slow-call-ratio=0.6 --payment.gateway.slow-latency=5s --payment.gateway.read-timeout=3s | load: payment --rate 120 --duration 60 --probe-rate 20 -->
### payment under gateway degradation

- Load: 120 payments/s for 60 s, plus 20 reads/s on an unrelated endpoint
- Wall time: 68.6 s; max Tomcat busy threads observed: 200 / 200

| Request | Count | Throughput (req/s) | p50 ms | p95 ms | p99 ms | max ms | Status codes |
|---|---:|---:|---:|---:|---:|---:|---|
| pay | 7201 | 105.0 | 4857.3 | 8431.2 | 8630.9 | 8682.3 | 204: 2876 (39.9%), 500: 4325 (60.1%) |
| probe GET /orders/{id} | 1201 | 17.5 | 2978.5 | 5541.3 | 5652.3 | 5684.0 | 200: 1201 (100.0%) |


<!-- run: circuit-breaker-only (before the bulkhead was added) | app: --checkout.rate-limit.enabled=false --payment.gateway.slow-call-ratio=0.6 --payment.gateway.slow-latency=5s --payment.gateway.read-timeout=3s | load: payment --rate 120 --duration 60 --probe-rate 20 -->
### payment under gateway degradation

- Load: 120 payments/s for 60 s, plus 20 reads/s on an unrelated endpoint
- Wall time: 62.3 s; max Tomcat busy threads observed: 195 / 200

| Request | Count | Throughput (req/s) | p50 ms | p95 ms | p99 ms | max ms | Status codes |
|---|---:|---:|---:|---:|---:|---:|---|
| pay | 7201 | 115.5 | 1.4 | 3001.8 | 3004.9 | 3195.1 | 202: 6923 (96.1%), 204: 278 (3.9%) |
| probe GET /orders/{id} | 1201 | 19.3 | 1.1 | 2.8 | 26.8 | 340.0 | 200: 1201 (100.0%) |


<!-- run: mitigated | app: --checkout.rate-limit.enabled=false --payment.gateway.slow-call-ratio=0.6 --payment.gateway.slow-latency=5s --payment.gateway.read-timeout=3s | load: payment --rate 120 --duration 60 --probe-rate 20 -->
### payment under gateway degradation

- Load: 120 payments/s for 60 s, plus 20 reads/s on an unrelated endpoint
- Wall time: 62.5 s; max Tomcat busy threads observed: 26 / 200

| Request | Count | Throughput (req/s) | p50 ms | p95 ms | p99 ms | max ms | Status codes |
|---|---:|---:|---:|---:|---:|---:|---|
| pay | 7201 | 115.2 | 1.4 | 2.7 | 3002.7 | 3039.2 | 202: 7126 (99.0%), 204: 75 (1.0%) |
| probe GET /orders/{id} | 1201 | 19.2 | 1.1 | 1.9 | 2.9 | 19.3 | 200: 1201 (100.0%) |


<!-- run: nominal | app:  | load: nominal --rate 100 --duration 300 -->
### nominal checkout

- Load: 100 checkouts/s for 300 s (POST /orders, then PUT /orders/{id}/payment)
- Wall time: 300.1 s; max Tomcat busy threads observed: 68 / 200

| Request | Count | Throughput (req/s) | p50 ms | p95 ms | p99 ms | max ms | Status codes |
|---|---:|---:|---:|---:|---:|---:|---|
| pay | 30001 | 100.0 | 51.6 | 53.0 | 53.9 | 218.8 | 202: 38 (0.1%), 204: 29963 (99.9%) |
| place | 30001 | 100.0 | 1.2 | 2.3 | 3.5 | 639.5 | 201: 30001 (100.0%) |


<!-- run: surge | app:  | load: surge --from 20 --to 1200 --ramp 10 --hold 20 -->
### flash surge on checkout

- Load: POST /orders ramping 20 -> 1200 req/s in 10 s, then 20 s at 1200 req/s
- Wall time: 30.0 s; max Tomcat busy threads observed: 3 / 200

| Request | Count | Throughput (req/s) | p50 ms | p95 ms | p99 ms | max ms | Status codes |
|---|---:|---:|---:|---:|---:|---:|---|
| place | 30098 | 1002.7 | 0.6 | 1.9 | 4.2 | 432.9 | 201: 4439 (14.7%), 429: 25659 (85.3%) |


<!-- run: surge-unlimited | app: --checkout.rate-limit.enabled=false | load: surge --from 20 --to 1200 --ramp 10 --hold 20 -->
### flash surge on checkout

- Load: POST /orders ramping 20 -> 1200 req/s in 10 s, then 20 s at 1200 req/s
- Wall time: 30.0 s; max Tomcat busy threads observed: 4 / 200

| Request | Count | Throughput (req/s) | p50 ms | p95 ms | p99 ms | max ms | Status codes |
|---|---:|---:|---:|---:|---:|---:|---|
| place | 30098 | 1002.7 | 0.9 | 2.2 | 4.6 | 489.6 | 201: 30098 (100.0%) |


<!-- run: malformed | app:  | load: malformed --rate 125 --duration 40 -->
### malformed and injection payloads

- Load: 125 req/s for 40 s, 8 payload kinds in rotation
- Wall time: 40.0 s; max Tomcat busy threads observed: 14 / 200

| Request | Count | Throughput (req/s) | p50 ms | p95 ms | p99 ms | max ms | Status codes |
|---|---:|---:|---:|---:|---:|---:|---|
| malformed POST /orders | 5000 | 125.0 | 3.0 | 5.5 | 78.4 | 538.7 | 400: 5000 (100.0%) |

