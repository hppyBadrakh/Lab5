# [W11] quality-scenarios - ISO 25010 scenarios, load evidence & resilience mitigations

Base: `m7-quality`. Head: `feature/quality-scenarios`.

## Summary

This PR adds quality attributes to the Order Service. First, five ISO 25010 scenarios with numeric response measures. Then the payment path was load-tested under a degraded payment gateway, and the measured bottleneck was fixed with Resilience4j. The same load was run again to show the improvement.

- **Block A:** [`docs/quality/scenarios.md`](docs/quality/scenarios.md) has five six-part scenarios: S1 performance, S2 availability, S3 scalability, S4 security, S5 maintainability. Each has a measured result or an honest "not measured yet".
- **Block B:** [`docs/quality/measurement-evidence.md`](docs/quality/measurement-evidence.md).
  - Covers four runs, with p50/p95/p99, req/s, status-code mix and Tomcat busy threads, plus a bottleneck diagnosis. Raw output is in `benchmark-results-raw.md`.
  - `loadtest/LoadTest.java` is an open-model load generator, and `scripts/benchmark.sh` repeats every run.
- **Block C:** `infrastructure.resilience`.
  - `ResilientPaymentAdapter` puts a **circuit breaker** and a **bulkhead** (max 25 concurrent gateway calls) around the new `PaymentPort`.
  - Its fallback stores the payment intent, the order becomes `PAYMENT_PENDING`, and the API answers `202 Accepted`.
  - `CheckoutRateLimitFilter` is a **rate limiter** on `POST /orders`: 150 per second, and `429` + `Retry-After` above that.
- **Block D:** [`docs/quality/utility-tree.md`](docs/quality/utility-tree.md) has seven leaves ranked (business, difficulty), a rationale for every High leaf, and a trade-off reflection.
- **Block E:** [`docs/adr/0008-payment-resilience-and-load-shedding.md`](docs/adr/0008-payment-resilience-and-load-shedding.md) covers the measured context, the decision, four operational costs, five rejected alternatives (including more threads and virtual threads), and an M3 draft.
- **Block F:**
  - `ResilientPaymentAdapterTest` shows the circuit tripping, a short-circuit under 5 ms at p95 with no gateway call, HALF_OPEN recovery, and deferral on a full bulkhead.
  - MockMvc tests: `ResilientCheckoutApiContractTest`, `RateLimitedCheckoutContractTest`, `UnprotectedBaselineContractTest`.
  - `QualityArchitectureFitnessTest` has 6 ArchUnit rules that keep Resilience4j inside `infrastructure.resilience`.
  - CI has a "Resilience and contract tests" stage and runs for `m7-quality`.
- **Bonus L9:** two distinct mitigations in one PR (circuit breaker + bulkhead, and rate limiter), each with automated tests.

## Measured result (S2, same load: 120 payments/s, 60% of gateway calls take 5 s)

| | Before | After |
|---|---:|---:|
| HTTP 500 | 60.1% | 0% |
| Payment p95 | 8,431 ms | 2.7 ms |
| Tomcat busy threads | 200 / 200 | 26 / 200 |
| Unrelated `GET` p95 | 5,541 ms | 1.9 ms |
| Checkout intents kept | 39.9% | 100% |

## API changes

`PUT /orders/{orderId}/payment` now really charges through the payment gateway. It still answers 204 on success, and adds 202 (payment deferred, order `PAYMENT_PENDING`) and 402 (declined). `POST /orders` can answer 429. `OrderStatus` gains `PAYMENT_PENDING`. The OpenAPI snapshot is updated.

## Reflection

1. **Measured Bottleneck Surprise:**
   - During our load test, a gateway that answered 60% of calls in 5 s took all 200 Tomcat worker threads, although our 3 s read timeout was already in place and the CPU stayed nearly idle.
   - As a result, 60.1% of payments failed with HTTP 500.
   - `GET /orders/{id}`, which never calls the gateway, slowed from about 2 ms to 5.5 s at p95.
2. **Mitigation vs Hardware Scaling:** Our circuit breaker plus bulkhead was better than adding CPU or worker threads.
   - The load needed about 216 threads that each wait 3 s. A faster CPU changes nothing there, and 1,000 threads would saturate at about 4.6 times this load while every customer still waits 3 s for an error.
   - The breaker and bulkhead cap gateway waiters at 25, answer 95% of payments in 2.7 ms with 202, and cut the busy threads from 200 / 200 to 26 / 200 with zero 5xx.
3. **ADR-0008 Accepted Trade-Off:**
   - In ADR-0008, we accepted eventual consistency: a customer gets 202 "payment pending" before any money has moved.
   - This requires a reconciliation worker, plus cancellation and an e-mail when a deferred charge later fails. Both are planned for M3.
   - Until then, the deferred intents live in memory and a restart loses them.
4. **Primary Utility Tree Driver:**
   - We ranked Payment Gateway Outage as (H, H) in our utility tree because the measurement showed it does not just fail payments: it takes down the whole service, read endpoints included, at the busiest moment of a sale.
   - Fixing it needed a new port, a new order status, three interacting Resilience4j tactics and an API change.

## Verification

- `./mvnw clean verify` passes: 194 tests, 0 failures, no compiler warnings under `-Xlint:all`.
- `bash scripts/benchmark.sh all` reproduces the measurements.
- `bash scripts/demo.sh` still shows the Week 10 walkthrough (healthy gateway, payment 204).

🤖 Generated with [Claude Code](https://claude.com/claude-code)
