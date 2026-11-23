# Measurement Evidence (Week 11)

Load tests against the Week 10 REST endpoints of the Order Service, before and after the mitigations of [ADR-0008](../adr/0008-payment-resilience-and-load-shedding.md). The unedited tool output is in [benchmark-results-raw.md](benchmark-results-raw.md).

## Setup and how to reproduce

| Item | Value |
|---|---|
| Machine | Intel Core i7-11800H (8 cores, 16 threads), 15.8 GB RAM, Windows 11 |
| Runtime | Temurin JDK 25.0.4, Spring Boot 3.5.16, Tomcat 200 worker threads (default), in-memory repository |
| Load generator | [`loadtest/LoadTest.java`](../../loadtest/LoadTest.java). It is an open-model generator with a constant arrival rate, like k6's `constant-arrival-rate` executor. Each request runs on its own virtual thread, so a slow server does not slow down new arrivals. It runs on the same machine as the service |
| Thread metric | `tomcat.threads.busy` from Spring Boot Actuator, sampled every 500 ms; the maximum is reported |
| Gateway | `SimulatedPaymentGatewayClient` blocks the request thread like a synchronous HTTP client. Healthy: 50 ms. Degraded: 60% of calls take 5 s, and our client gives up after its 3 s read timeout |

```bash
./mvnw -q -DskipTests package
bash scripts/benchmark.sh all      # or one of: baseline | mitigated | nominal | surge | malformed | surge-unlimited
```

The circuit-breaker-only column below was recorded before the bulkhead existed. To repeat it, add `--resilience4j.bulkhead.instances.paymentGateway.max-concurrent-calls=1000` to the `mitigated` run.

**Why not k6:** the lab names k6 or wrk. Neither could be installed on the measuring machine, so the same open-model load is produced by a JDK program that needs no installation. The report shows the same figures k6 would: p50, p95, p99, max and req/s per request type, plus the status-code mix.

## Run 1: payment gateway degradation (scenario S2)

**Load:** 120 `PUT /orders/{id}/payment` per second for 60 s on pre-placed orders, plus 20 `GET /orders/{id}` per second on an unrelated order. **Gateway:** degraded, as described above.

| Metric | Baseline (unmitigated) | Circuit breaker only | Circuit breaker + bulkhead (shipped) |
|---|---:|---:|---:|
| Payment requests | 7,201 | 7,201 | 7,201 |
| Successful payment throughput (charged or deferred) | 41.9 req/s (2,876 in 68.6 s) | 115.5 req/s | 115.2 req/s |
| Payment p50 | 4,857 ms | 1.4 ms | **1.4 ms** |
| Payment p95 | 8,431 ms | 3,002 ms | **2.7 ms** |
| Payment p99 | 8,631 ms | 3,005 ms | 3,003 ms |
| Error rate (HTTP 500) | **60.1%** (4,325) | 0% | **0%** |
| Checkout intents kept | 39.9% | 100% (278 charged, 6,923 deferred) | 100% (75 charged, 7,126 deferred) |
| Max Tomcat busy threads | **200 / 200** | 195 / 200 | **26 / 200** |
| Unrelated `GET` p50 / p95 / p99 | 2,979 / 5,541 / 5,652 ms | 1.1 / 2.8 / 26.8 ms | **1.1 / 1.9 / 2.9 ms** |

### Bottleneck diagnosis

1. **The bottleneck is the Tomcat worker pool, not the CPU.** In the baseline, each slow gateway call holds a request thread for the full 3 s read timeout. 120 payments/s × 60% slow × 3 s is about 216 threads needed, but only 200 exist. All 200 were busy, new requests waited in Tomcat's accept queue, and the waiting pushed payment p50 to 4.9 s, well above the 3 s timeout. The CPU was nearly idle, because a sleeping thread costs no CPU. That is why a faster CPU would not help.
2. **The failure spreads to endpoints that never call the gateway.** `GET /orders/{id}` only reads memory, yet its p95 rose from about 2 ms to 5.5 s. It queued for the same exhausted worker pool. This is the cascading failure that scenario S2 has to prevent.
3. **A read timeout alone does not help.** The baseline already has a 3 s timeout, and the pool still saturated. A timeout limits how long each call waits, not how many calls wait at once. This is the "naive fixed timeout" alternative rejected in ADR-0008, and it was measured rather than assumed.
4. **The circuit breaker alone left a gap in the first seconds** (the middle column). The breaker learns that the gateway is slow only when calls *complete*. In the first 3 s of the outage, about 360 payments started before 5 calls had failed, and they pushed the busy-thread peak to 195 of 200. Each half-open probe let a few slow calls through again, so p95 stayed at the 3 s timeout. A semaphore bulkhead caps concurrent gateway calls at 25. With it, the peak was 26 busy threads (at most 25 waiting on the gateway, plus fast requests in flight), payment p95 fell to 2.7 ms, and the unrelated `GET` p99 was 2.9 ms.
5. **The remaining 1% tail is intended.** Payment p99 is still 3 s. These are the calls the bulkhead lets through to test the gateway (before the breaker opens, and in each half-open phase). They are bounded to 25 at a time, so they cannot starve the pool.

## Run 2: nominal checkout (scenario S1)

**Load:** 100 checkouts/s for 300 s (`POST /orders`, then `PUT .../payment`), healthy gateway, all mitigations on.

| Request | Count | Throughput | p50 | p95 | p99 | max | Status codes |
|---|---:|---:|---:|---:|---:|---:|---|
| `POST /orders` | 30,001 | 100.0 req/s | 1.2 ms | 2.3 ms | 3.5 ms | 640 ms | 201: 100% |
| `PUT .../payment` | 30,001 | 100.0 req/s | 51.6 ms | 53.0 ms | 53.9 ms | 219 ms | 204: 29,963; 202: 38 |

Max busy threads: 68 of 200.

**Finding:** all 38 deferred payments came in the same second, 2 s after start-up. The log shows `BulkheadFullException` for each. On a cold JVM the first requests are slow, so more than 25 gateway calls overlapped and the bulkhead deferred the overflow. After warm-up there were none. The customers were not hurt (202, order `PAYMENT_PENDING`, payment retryable), but the bulkhead size is tuned for a warm instance. A warm-up step before the instance takes traffic would remove this.

## Run 3: flash surge (scenario S3)

**Load:** `POST /orders` rising from 20 to 1,200 req/s over 10 s, then 20 s at 1,200 req/s (30,098 requests).

| Configuration | Admitted (201) | Rejected (429) | Admitted rate | p50 | p95 | p99 | 5xx | Busy threads |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Rate limiter on (150/s) | 4,439 (14.7%) | 25,659 (85.3%) | 148 /s | 0.6 ms | 1.9 ms | 4.2 ms | 0 | 3 |
| Rate limiter off | 30,098 (100%) | 0 | 1,003 /s | 0.9 ms | 2.2 ms | 4.6 ms | 0 | 4 |

**Finding (against expectations):** without the limiter, the in-memory service absorbed 1,200 req/s with p95 2.2 ms. Placing an order costs microseconds when there is no database. So in this prototype the limiter's benefit is not lower latency. It is a hard cap on admitted work: 148/s instead of 1,003/s, and 26,000 fewer orders in the heap in 30 s. The limit of 150/s is sized for the M3 database pool of 30 connections, which does not exist yet. The limiter's cost was measured too: 85% of the surge was refused, and those are real customers. ADR-0008 lists this as an operational cost, and S3 has to be measured again against PostgreSQL in M3.

## Run 4: malformed and injection payloads (scenario S4)

**Load:** 125 req/s for 40 s (5,000 requests), 8 hostile payload kinds in rotation. Normal configuration, so the rate limiter was active (125/s is below its limit).

| Request | Count | p50 | p95 | p99 | max | Status codes |
|---|---:|---:|---:|---:|---:|---|
| malformed `POST /orders` | 5,000 | 3.0 ms | 5.5 ms | 78.4 ms | 539 ms | 400: 5,000 (100%) |

There were no `ERROR` lines in the application log. The p99 and the maximum come from the first requests on a cold JVM.

## Threats to validity

- Load generator and service shared one laptop, so the generator's CPU use affects the service. The absolute numbers are optimistic for a single instance and pessimistic for a dedicated server. The before/after ratios are the meaningful result.
- The gateway is simulated. A real HTTP client also spends connection-pool slots and sockets, which would make the baseline worse, not better.
- Each run was done once. Run-to-run noise was not quantified, so small differences in the tail percentiles (a few ms) are not meaningful. Differences of orders of magnitude are.
- Everything is in memory. Database effects (connection pool, locks) are not measured. That is M3 work.
