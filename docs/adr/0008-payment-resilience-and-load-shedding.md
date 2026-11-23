# ADR-0008: Payment Resilience and Load Shedding

## Status

Proposed (2026-11-23). Change to Accepted once a peer has approved the pull request.

Builds on [ADR-0006](0006-hexagonal-ports-adapters.md) (the hexagon) and [ADR-0007](0007-monolith-to-distributed-slice.md) (the Order Service slice).

## Context

Paying an order now calls the third-party payment gateway through a new outbound port, `PaymentPort`. The call is synchronous, and the request thread waits for it. In the target design of ADR-0007, payment moves to an asynchronous Payment Service. That service and the message broker do not exist before M3, so until then the Order Service talks to the provider itself.

We load-tested the Week 10 endpoints with the gateway degraded as in scenario S2: 60% of calls take 5 s, and our client gives up after a 3 s read timeout. Load was 120 payments/s plus 20 reads/s on an unrelated endpoint. Details are in [measurement-evidence.md](../quality/measurement-evidence.md). Without mitigation:

- **All 200 Tomcat worker threads were busy.** Each slow call holds a thread for 3 s, and the load needs about 216 such threads. The CPU stayed nearly idle.
- **60.1% of payments failed with HTTP 500.** Payment p50 was 4.9 s and p95 was 8.4 s, longer than the timeout, because requests also queued for a free thread.
- **The failure spread.** `GET /orders/{id}` never calls the gateway, yet its p95 rose from about 2 ms to 5.5 s.
- **The 3 s timeout was already in place, and the pool saturated anyway.** A timeout limits how long a call waits, not how many calls wait.

A second, smaller risk is a flash sale (scenario S3): 20 to 1,200 order placements per second within 10 s. The in-memory service survives that today, but the PostgreSQL pool of 30 connections planned for M3 will not.

## Decision

We protect the payment call and the checkout ingress with **Resilience4j**. All of it lives in `infrastructure.resilience`, outside the domain and the use case:

1. **Circuit breaker `paymentService`** on `ResilientPaymentAdapter.processPayment`.
   - It is count-based over the last 10 calls and opens when at least 50% have failed or were slower than 1 s, after a minimum of 5 calls.
   - It stays OPEN for 5 s, then allows 3 trial calls in HALF_OPEN.
   - While OPEN, no thread waits for the gateway.
2. **Semaphore bulkhead `paymentGateway`.**
   - At most 25 calls may wait for the gateway at the same time, and none queue.
   - It was added after measuring the breaker on its own. The breaker learns about slowness only when calls complete, and in the first 3 s about 360 calls started before it opened. The busy-thread peak was still 195 of 200.
   - A full bulkhead is our own capacity decision, so `BulkheadFullException` is ignored by the breaker.
3. **Degraded fallback `deferPayment`.**
   - This runs on a failure, an open circuit or a full bulkhead.
   - It stores the payment intent in `DeferredPaymentStore` and returns `DEFERRED_PENDING`.
   - The use case moves the order to the new status `PAYMENT_PENDING`, and the API answers `202 Accepted` with the intent token.
   - Calling `PUT .../payment` again retries the charge.
4. **Rate limiter `checkout`** in `CheckoutRateLimitFilter`.
   - It applies only to `POST /api/v1/orders`, with 150 permits per second and no waiting.
   - A request without a permit gets `429 Too Many Requests`, `Retry-After: 1` and an RFC 7807 body before it reaches the domain.
   - This is a service-level capacity guard. The per-client limits in [api-gateway.md](../api-gateway.md) are a separate, earlier layer.
5. **Fitness functions.**
   - `QualityArchitectureFitnessTest` keeps Resilience4j annotations in `infrastructure.resilience`, keeps Resilience4j out of `domain` and `application`, requires every `PaymentPort` implementation to live there, and lets only that package reach the blocking gateway client.
   - CI runs these rules together with `ResilientPaymentAdapterTest`, which shows that the circuit trips, short-circuits in under 5 ms (p95) without calling the gateway, recovers through HALF_OPEN, and defers on a full bulkhead.
6. **A measurable baseline is kept.**
   - `payment.resilience.enabled=false` swaps in `UnprotectedPaymentAdapter`, so the "before" numbers can be measured again at any time.
   - It is for benchmarks only.

## Consequences

Measured benefits (same degraded load):

| | Before | After |
|---|---|---|
| 5xx responses | 60.1% | 0 |
| Payment p95 | 8,431 ms | 2.7 ms |
| Busy threads | 200 / 200 | 26 / 200 |
| Unrelated `GET` p95 | 5,541 ms | 1.9 ms |
| Checkout intents kept | 39.9% | 100% |

On a normal day (S1: 100 checkouts/s, healthy gateway) the protection cost nothing measurable: payment p95 was 53.0 ms, which is 50 ms of gateway time plus 3 ms.

Operational costs:

1. **Eventual consistency and a settlement duty.** The customer sees "payment pending" before any money has moved. Someone must settle every `PAYMENT_PENDING` order: a reconciliation worker, plus cancellation and an e-mail if the charge then fails. That worker does not exist yet, and the intents are only in memory, so a restart loses them. Until M3, deferred orders need a manual retry, and support has a new status to explain.
2. **Thresholds that must be tuned and watched.**
   - The window size, the failure and slow-call thresholds, the bulkhead size and the limit of 150/s were all set against one laptop. They are wrong for other hardware or traffic.
   - Measured example: on a cold JVM the bulkhead deferred 38 healthy payments in the first second after start-up.
   - A breaker that opens on a short blip defers payments that would have succeeded.
   - Operations needs dashboards and alerts on `/actuator/circuitbreakers`, `/actuator/circuitbreakerevents` and `/actuator/ratelimiters`, which are now exposed. Exposing them is itself a decision to secure them before production.
3. **The rate limiter turns customers away.**
   - In the measured surge, 85.3% of order attempts got 429, although today's in-memory service could have served them all.
   - The limit is also per instance: three instances admit 450/s, so it has to be re-derived from the database capacity whenever the deployment scales.
4. **More moving parts.**
   - The change adds three Resilience4j instances, AOP proxies (a self-invocation inside the adapter would silently bypass the breaker), a new order status, a 202 response and the Actuator dependency.
   - The circular buffers and counters also take a small amount of heap.

## Rejected Alternatives

- **More worker threads (vertical scaling), for example 200 to 1,000.**
  - This only moves the limit. At 120 payments/s with 3 s calls, 1,000 threads saturate at about 4.6 times this load (216 threads needed today), or sooner if the gateway gets slower.
  - Each platform thread reserves about 1 MB of stack.
  - Customers would still wait 3 s and then get an error.
- **Only a shorter read timeout.**
  - The baseline already had a 3 s timeout and still saturated (measured, Run 1). A 2 s timeout still needs about 144 threads at this load and fails every slow payment.
- **Virtual threads (`spring.threads.virtual.enabled=true`).**
  - These remove the thread limit, but then nothing bounds the concurrent calls to a gateway that is already struggling.
  - Every customer still waits for the full timeout and then gets an error.
  - This is worth adopting later, together with the bulkhead, not instead of it.
- **Retry with backoff.**
  - On a gateway that is already overloaded, retries multiply the load and the waiting.
  - We retry only by deferring, after the gateway has recovered.
- **Resilience4j TimeLimiter.**
  - It needs an asynchronous call (`CompletableFuture`), and the gateway client is blocking. The read timeout already plays its role.

## M3 Draft

What Milestone 3 has to deliver so that this decision holds in production:

1. **Durable state.** Use PostgreSQL with a JPA adapter for `OrderRepository`. Deferred payment intents move into the same database, written in the same transaction as the order's change to `PAYMENT_PENDING` (transactional outbox).
2. **Reconciliation worker.** A scheduled job settles pending intents while the circuit is CLOSED, marks orders PAID, or cancels them after N failed attempts and notifies the customer. Target: settlement within 15 minutes of the gateway recovering (utility tree leaf "Settle deferred payments").
3. **Payment Service extraction (ADR-0007, step 3).** Once the broker exists, `PaymentPort` becomes an adapter that publishes `PaymentRequested`. The circuit breaker and bulkhead move with the provider client into the Payment Service, so the Order Service no longer holds a request thread for payment at all.
4. **Measure again.** Repeat S1 and S3 against PostgreSQL, adding the Hikari pool saturation metric (target ≤ 85%), and re-derive the rate limit from the measured pool capacity.
5. **Operations.** Secure the Actuator endpoints, add alerts on breaker state changes and on the deferred-intent count, and add a warm-up step before an instance takes traffic.
