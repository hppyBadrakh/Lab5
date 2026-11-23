# Utility Tree (Week 11)

SEI utility tree for the Order Service. Each leaf is ranked **(business importance, architectural difficulty)** with H, M or L, and links to a scenario in [scenarios.md](scenarios.md) where one exists.

```text
Utility (Order Service)
├── Availability & fault tolerance
│   ├── Payment gateway outage ................ (H, H)  S2: 0 5xx, ≥95% of payments ≤ 5 ms, ≤ 30/200 threads   ◀ primary driver
│   └── Settle deferred payments .............. (H, M)  Every PAYMENT_PENDING order settled or cancelled ≤ 15 min after the gateway recovers
├── Performance efficiency
│   ├── Nominal checkout latency .............. (H, M)  S1: POST p95 ≤ 150 ms, payment p95 ≤ 150 ms at 100 checkouts/s
│   └── Warm-up after deploy .................. (L, M)  0 deferred payments in the first minute after start-up
├── Scalability (capacity)
│   └── Flash sale traffic surge .............. (M, M)  S3: admit ≤ 150 orders/s, shed the rest with 429, DB pool ≤ 85% (M3)
├── Security
│   └── Malformed & injection payloads ........ (M, L)  S4: 100% rejected with 400, p95 ≤ 10 ms
└── Maintainability (modifiability)
    └── Add a payment provider (QPay) ......... (L, L)  S5: ≤ 2 person-days, 0 changed lines in domain.. and application..
```

| Leaf | Rank | Scenario | Status |
|---|---|---|---|
| Payment gateway outage | (H, H) | S2 | Mitigated and measured: circuit breaker, bulkhead and fallback |
| Settle deferred payments | (H, M) | none yet | **Open.** The intents are stored, but no reconciliation worker exists yet (ADR-0008, M3) |
| Nominal checkout latency | (H, M) | S1 | Latency targets met (POST p95 2.3 ms, payment p95 53.0 ms) |
| Warm-up after deploy | (L, M) | S1 finding | Open. 38 payments deferred on a cold start |
| Flash sale traffic surge | (M, M) | S3 | Rate limiter shipped. The database part waits for M3 |
| Malformed & injection payloads | (M, L) | S4 | Met (5000 of 5000 rejected, p95 5.5 ms) |
| Add a payment provider | (L, L) | S5 | Structure ready; effort not measured |

## Rationale for every High leaf

**Payment gateway outage (H, H), the primary driver.**
- *Business importance H.* Every order that is paid goes through the gateway. The measurement showed that a degraded gateway does not just fail payments: it took down the entire service. 60.1% of payments ended in HTTP 500, and a read-only endpoint went from about 2 ms to 5.5 s at p95. During a sales peak, that loses revenue directly and spends customer trust on the slowest moment of checkout.
- *Difficulty H.* The fix is not local. It needed:
  - a new outbound port;
  - a new lifecycle state in the aggregate (`PAYMENT_PENDING`);
  - three Resilience4j tactics that interact: the breaker, the bulkhead, and a full bulkhead that must *not* trip the breaker;
  - a fallback store;
  - an API change (202 Accepted).
  
  Even the first version, with the breaker only, was not enough, as the 195 of 200 busy threads in the middle column of Run 1 show. The thresholds were tuned against measurements.

**Settle deferred payments (H, M).**
- *Business importance H.* A deferred payment is money the shop has not received for goods it may already ship. If deferred intents are never settled, the availability gained above turns into revenue lost later.
- *Difficulty M.* The building blocks exist: the stored intents, `PUT .../payment` that retries the charge, and the circuit breaker that tells when the gateway is back. What is missing is a scheduled worker and durable storage. Both are standard and planned for M3.

**Nominal checkout latency (H, M).**
- *Business importance H.* Checkout latency is directly linked to conversion, and S1 is the load every normal day carries.
- *Difficulty M, not L.* Today's numbers (POST p95 2.3 ms) are easy because everything is in memory. In M3, PostgreSQL, a transaction per order and an outbox write will consume most of the 150 ms budget. Keeping it then is real design work.

## Trade-off reflection

We put the architecture budget on **availability during a gateway outage (H, H)** and accepted that this weakens three other qualities:

1. **Consistency.**
   - *What we gave up:* the customer gets "202 Accepted, payment pending" before any money has moved. If the deferred charge later fails, the order has to be cancelled and the customer told.
   - *Why we accepted it:* a deferred payment that occasionally fails costs less than a checkout that fails for everyone. We traded strong consistency for availability, and the price is the open (H, M) leaf "Settle deferred payments".
2. **Scalability as admitted business.**
   - *What we gave up:* during the measured surge, the rate limiter refused 85% of customers, although the in-memory service could have served all of them.
   - *Why we accepted it:* we did this on purpose, to protect the M3 database pool. The leaf is therefore only (M, M). A refused customer can retry; a database at 100% fails every customer at once.
3. **Maintainability was ranked (L, L) on purpose.**
   - *What we gave up:* little or no investment in making a second provider easy to add.
   - *Why we accepted it:* if adding QPay takes 3 days instead of 2, no revenue is lost today. If the gateway slows down for 10 minutes at peak, every checkout in those 10 minutes is at risk, as Run 1 shows.

   The hexagonal structure from ADR-0006 already gives most of the modifiability for free, so no extra budget went there.

Not every leaf is (H, H). With three days left before a sales peak, we would cut the warm-up leaf first: it costs at most a few deferred, still retryable payments after a deploy.
