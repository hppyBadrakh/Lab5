# Quality Attribute Scenarios (Week 11)

Five ISO/IEC 25010 scenarios for the Order Service, each in the six-part SEI form. Every response measure is a number that a test or a benchmark can check. The "Measured" line points to the evidence; the numbers come from [measurement-evidence.md](measurement-evidence.md) (load tests) or from automated tests.

Common environment for the measured scenarios: one Order Service instance (Spring Boot 3.5, Tomcat with 200 worker threads, in-memory repository) on an Intel Core i7-11800H laptop, load generator on the same machine.

| # | ISO 25010 characteristic | Scenario | Measured result | Met? |
|---|---|---|---|---|
| S1 | Performance efficiency (time behaviour) | Nominal checkout load | POST p95 2.3 ms, payment p95 53.0 ms, 0 errors, 99.87% charged | Partly: latency yes, charge rate missed on cold start |
| S2 | Reliability (availability, fault tolerance) | Payment gateway degradation | 0 5xx, payment p95 2.7 ms, 26 of 200 threads busy | Yes |
| S3 | Performance efficiency (capacity) | Flash surge on checkout | 148 orders/s admitted, the rest got 429, 0 5xx | Yes (DB part deferred to M3) |
| S4 | Security (integrity) | Malformed and injection payloads | 5000 of 5000 rejected with 400, p95 5.5 ms | Yes |
| S5 | Maintainability (modifiability) | Second payment provider | Not measured; checked structurally by ArchUnit | Not yet measured |

---

## S1: Performance, nominal checkout

| Part | Value |
|---|---|
| **Source** | Customers using the web shop and the mobile app |
| **Stimulus** | 100 complete checkouts per second for 5 minutes. Each checkout is `POST /api/v1/orders` followed by `PUT /api/v1/orders/{id}/payment` (200 requests per second in total) |
| **Environment** | Normal operation. Payment gateway healthy (50 ms per charge). JVM already started |
| **Artifact** | Order Service: REST adapter, `ManageOrdersUseCase`, `Order` aggregate, `PaymentPort` adapter |
| **Response** | Every order is validated, stored, placed and charged |
| **Response measure** | `POST /orders`: p95 ≤ 150 ms and p99 ≤ 250 ms. `PUT .../payment`: p95 ≤ 150 ms including the 50 ms gateway call. Zero 5xx responses. At least 99.9% of payments charged (204) rather than deferred |

**Measured:** `POST` p50 1.2 ms, p95 2.3 ms, p99 3.5 ms. Payment p95 53.0 ms, p99 53.9 ms. 60,002 requests, 0 errors. 29,963 of 30,001 payments (99.87%) were charged. The other 38 were deferred, all within the first second after start-up (see the finding in the measurement report). **Latency and error targets met. The 99.9% charge-rate target is missed by 0.03 points**, because of the cold start; a warm-up step before the instance takes traffic is the fix (utility tree leaf "Warm-up after deploy").

## S2: Availability, payment gateway degradation (primary driver)

| Part | Value |
|---|---|
| **Source** | Third-party payment gateway (Payment context, external provider) |
| **Stimulus** | 60% of charge calls take 5,000 ms, which is beyond our 3,000 ms read timeout. Meanwhile 120 payment requests per second arrive, plus 20 reads per second on an unrelated endpoint |
| **Environment** | Peak sales period. One instance with 200 Tomcat worker threads |
| **Artifact** | `PaymentPort` adapter (`infrastructure.resilience.ResilientPaymentAdapter`) and the Tomcat worker pool |
| **Response** | The circuit breaker opens and payments are short-circuited to the degraded fallback. The fallback stores the payment intent and answers `202 Accepted` with the order in `PAYMENT_PENDING`. Endpoints that do not need the gateway keep working normally |
| **Response measure** | Zero 5xx responses. At least 95% of payment requests answered within 5 ms. At most 30 of 200 worker threads busy. Unrelated `GET` p95 ≤ 50 ms. 100% of checkout intents kept (charged or deferred, never lost) |

**Measured** (same load, before and after the mitigation):

| | Baseline | Breaker + bulkhead |
|---|---|---|
| 5xx | 60.1% | 0 |
| Payment p95 | 8,431 ms | 2.7 ms |
| Busy threads | 200 / 200 | 26 / 200 |
| Unrelated `GET` p95 | 5,541 ms | 1.9 ms |
| Intents kept | 39.9% | 100% |

**Met.**

## S3: Scalability, flash surge

| Part | Value |
|---|---|
| **Source** | A push notification for a flash sale reaching every app user at once |
| **Stimulus** | Order placement rises from 20 to 1,200 requests per second within 10 seconds, then holds 1,200 requests per second for 20 seconds |
| **Environment** | Flash sale. One instance. In M3 the instance will have a database pool of 30 connections |
| **Artifact** | Checkout ingress (`infrastructure.resilience.CheckoutRateLimitFilter`) in front of `POST /api/v1/orders` |
| **Response** | Up to 150 orders per second are admitted. Every request above that is rejected at once with `429 Too Many Requests`, a `Retry-After` header and an RFC 7807 body, before it reaches the domain or storage |
| **Response measure** | Admitted rate ≤ 150 per second (+2%). p95 of all responses ≤ 150 ms. Zero 5xx. Database pool saturation ≤ 85%, measured in M3 once a database exists |

**Measured:** 4,439 orders admitted in 30 s, which is 148 per second. 25,659 requests (85.3%) were rejected with 429. p95 1.9 ms, 0 5xx. **Met for the admission part.** The pool part cannot be measured yet. Without the limiter, the in-memory service accepted all 1,200 requests per second (p95 2.2 ms), so today the limiter protects a capacity that only M3 will introduce.

## S4: Security, malformed and injection payloads

| Part | Value |
|---|---|
| **Source** | An attacker or a broken client on the public internet |
| **Stimulus** | 5,000 order requests at 125 per second, in 8 rotating kinds: SQL injection in `sku`, `currency` and `countryCode`; a negative price; a sub-cent price; quantity 100,000; broken JSON; a truncated body |
| **Environment** | Normal operation, public ingress, rate limiter active |
| **Artifact** | Web boundary: Jackson parsing, Bean Validation on the request DTOs, `GlobalRestExceptionHandler` |
| **Response** | Every payload is rejected before it reaches the domain, with an RFC 7807 `400 Bad Request` naming the invalid fields. Nothing is stored and no stack trace leaks |
| **Response measure** | 100% answered with 400. p95 ≤ 10 ms. Zero 5xx. Zero error-level log lines |

**Measured:** 5,000 of 5,000 got 400. p50 3.0 ms, p95 5.5 ms, p99 78.4 ms (the first requests while the JVM warmed up). 0 5xx, 0 `ERROR` log lines. **Met.**

A free-text field such as `customerId` (1 to 64 characters) accepts a string like `' OR 1=1 --`. This is intended: it is stored and returned as data, never interpreted. The rule for M3 is parameter binding only (JPA or `PreparedStatement`), never string-built SQL.

## S5: Maintainability, second payment provider

| Part | Value |
|---|---|
| **Source** | The business |
| **Stimulus** | Integrate a second payment provider, the mobile wallet QPay, into checkout |
| **Environment** | Development time |
| **Artifact** | `infrastructure.resilience.gateway` (the gateway client) |
| **Response** | One new class implements `ExternalPaymentClient` and is selected by configuration. The circuit breaker, bulkhead, fallback and `PaymentPort` are reused unchanged |
| **Response measure** | At most 2 person-days. Zero changed lines in `domain..` and `application..`. All ArchUnit fitness tests green |

**Status: not measured yet.** No second provider has been built. What exists now is structural evidence:
- `QualityArchitectureFitnessTest.gatewayClientIsReachedOnlyThroughThePaymentAdapters` guarantees that only the resilience package knows the gateway client.
- `useCasesMustNotKnowResilienceTactics` guarantees that the use case sees only `PaymentPort`.

The real measurement is the effort recorded when QPay is integrated.
