# E-Commerce Order Subsystem

ICSI304 coursework. The order subsystem in `com.ecommerce.order` is the Order Service: a Spring Boot application built as a DDD hexagon (Week 9) and extracted as the first distributed slice with a published REST contract and an asynchronous event side-channel (Week 10). The Week 5 structural pattern code remains in `mn.edu.num.sad.ecommerce` and stands for the rest of the monolith.

## Requirements
- JDK 25 LTS (`JAVA_HOME` must point at it)
- No Maven install needed: use the wrapper

## Build and test
```powershell
.\mvnw.cmd clean verify
```
On Linux, macOS, or Git Bash, use `./mvnw clean verify`.

The build compiles with `-Xlint:all` and fails on any compiler warning.

Run only the pure domain tests (no Spring context), only the resilience and REST contract tests, or only the architecture rules:
```bash
./mvnw test "-Dtest=*DomainTest,*AggregateTest,*ValueObjectTest"
./mvnw test "-Dtest=*Resilient*,*ContractTest"
./mvnw test "-Dtest=*ArchitectureFitnessTest"
```

`OpenApiSnapshotContractTest` compares the live OpenAPI document with `docs/openapi/rest-endpoints.json`. After an intended API change, refresh the snapshot and commit it:
```bash
./mvnw test -Dtest=OpenApiSnapshotContractTest "-Dopenapi.snapshot.update=true"
```

## Run
```powershell
.\mvnw.cmd spring-boot:run
```

| URL | Purpose |
|---|---|
| `POST /api/v1/orders` | Place an order (201 + `Location`, status `PLACED`; 400; 429 above 150 orders/s) |
| `GET /api/v1/orders/{orderId}` | Read an order (200, or 404) |
| `GET /api/v1/customers/{customerId}/orders` | List a customer's orders, newest first (200, possibly empty) |
| `PUT /api/v1/orders/{orderId}/payment` | Charge the order through the payment gateway: 204 (PAID), 202 (gateway unavailable, payment deferred, order `PAYMENT_PENDING`), 402, 404, 409 |
| `PUT /api/v1/orders/{orderId}/cancellation` | Cancel an order (204, or 404, 409 if already cancelled) |
| http://localhost:8080/swagger-ui.html | Swagger UI |
| http://localhost:8080/v3/api-docs | OpenAPI document (snapshot: `docs/openapi/rest-endpoints.json`) |
| http://localhost:8080/actuator/circuitbreakers | Circuit breaker state (also `/actuator/circuitbreakerevents`, `/actuator/ratelimiters`, `/actuator/health`) |

Example request body:
```json
{
  "customerId": "CUST-1",
  "shippingAddress": {"street": "Peace Ave 1", "city": "Ulaanbaatar", "postalCode": "14200", "countryCode": "MN"},
  "items": [{"sku": "ELEC-1001", "unitPrice": 25.50, "currency": "USD", "quantity": 2}]
}
```

Errors are RFC 7807 `ProblemDetail` responses. Orders live in memory, so they disappear when the application stops.

After an order is placed, the audit entry and the customer confirmation run on a separate `side-channel-*` thread pool. The `201` does not wait for them and cannot be failed by them.

## Demo
```bash
bash scripts/demo.sh
```
This builds the jar if needed, starts the application, walks through the 201, 200, 200 (customer order list), 404, 400, 400 (over the 10,000 limit), 204 (payment), 204 (cancellation) and 409 cases with `curl`, checks the Swagger UI, prints the side-channel log lines, and stops the application. If something already answers on `BASE_URL` (default `http://localhost:8080`), the script uses that instead.

## Load tests (Week 11)
```bash
./mvnw -q -DskipTests package
bash scripts/benchmark.sh all
```
This starts the jar with each configuration, applies the load with `loadtest/LoadTest.java` (JDK only, no install) and appends p50/p95/p99, req/s, status codes and the busy Tomcat threads to `target/benchmark-results.md`. The simulated payment gateway is configured with `payment.gateway.*`; `payment.resilience.enabled=false` runs the unprotected baseline. Results and analysis: [docs/quality/measurement-evidence.md](docs/quality/measurement-evidence.md).

## Architecture
```text
 presentation.rest ─> domain.port.in ─> application ─> domain.port.out <─┬── adapter.out.persistence
                                              │                          └── adapter.out.event
                                              v                                    │
                                 domain.model + domain.event                       │ OrderPlaced (async)
                                       (pure Java 25)                              v
                                                                       infrastructure.messaging
```
| Package | Contents |
|---|---|
| `domain.model` | `Order` aggregate root, `OrderItem` entity, value objects `Money`, `Sku`, `Quantity`, `OrderId`, `CustomerId`, `ShippingAddress`, `OrderStatus` |
| `domain.event` | `OrderPlacedDomainEvent`, `OrderCancelledDomainEvent` |
| `domain.port.in` | `ManageOrdersUseCase` (inbound port) |
| `domain.port.out` | `OrderRepository`, `DomainEventPublisher`, `PaymentPort` (outbound ports) |
| `application` | `OrderApplicationService` (implements the inbound port) and application exceptions |
| `presentation.rest` | `OrderRestController`, `GlobalRestExceptionHandler` (RFC 7807), `OpenApiConfiguration` |
| `presentation.rest.dto` | Request and response records with Bean Validation and `@Schema` |
| `infrastructure.messaging` | Asynchronous side-channel: `OrderAuditAndNotificationSideChannelListener`, its thread pool, `OrderNotificationSender` |
| `infrastructure.adapter.out.persistence` | `InMemoryOrderRepository` |
| `infrastructure.adapter.out.event` | `SpringDomainEventPublisher` |
| `infrastructure.resilience` | `ResilientPaymentAdapter` (circuit breaker, bulkhead, deferring fallback), `DeferredPaymentStore`, `CheckoutRateLimitFilter` (429), `UnprotectedPaymentAdapter` (benchmark baseline only) |
| `infrastructure.resilience.gateway` | `ExternalPaymentClient` and the `SimulatedPaymentGatewayClient` used until a real provider is connected |
| `export` | Bonus Pipe-and-Filter CSV pipeline (depends on the domain only) |

`HexagonalArchitectureFitnessTest`, `DistributedSliceArchitectureFitnessTest` and `QualityArchitectureFitnessTest` enforce these rules with ArchUnit.

## Documents
| Document | Contents |
|---|---|
| [docs/contexts.md](docs/contexts.md) | Context map and ubiquitous language |
| [docs/service-decomposition.md](docs/service-decomposition.md) | Monolith split into five services, database-per-service, sync versus async |
| [ADR-0006](docs/adr/0006-hexagonal-ports-adapters.md) | Hexagonal ports and adapters |
| [ADR-0007](docs/adr/0007-monolith-to-distributed-slice.md) | Order Service as a distributed slice, and the service we did not extract |
| [docs/m2-milestone-close.md](docs/m2-milestone-close.md) | Milestone 2 technical close with the C4 container view |
| [docs/api-gateway.md](docs/api-gateway.md) | API gateway sketch (bonus) |
| [docs/quality/scenarios.md](docs/quality/scenarios.md) | Five ISO 25010 quality attribute scenarios with numeric response measures |
| [docs/quality/measurement-evidence.md](docs/quality/measurement-evidence.md) | Load test results, before and after, with bottleneck diagnosis |
| [docs/quality/utility-tree.md](docs/quality/utility-tree.md) | Prioritized utility tree and trade-off reflection |
| [ADR-0008](docs/adr/0008-payment-resilience-and-load-shedding.md) | Circuit breaker, bulkhead and rate limiter, their costs, and the M3 draft |

`legacy/LegacyOrderEndpoint.java` is the "before" code. It sits outside `src/` and is not compiled.

## Branches
- `m5-behaviour`: baseline
- `feature/styles-I-sbs`: Week 7 work (layered MVC)
- `m6-styles`: Week 7 integration branch
- `m6-ddd`: Week 9 integration branch and pull request target
- `feature/ddd-bootstrap`: Week 9 work (DDD and hexagonal architecture)
- `m7-distributed`: Week 10 integration branch and pull request target
- `feature/distributed-slice`: Week 10 work (distributed slice, REST contract, OpenAPI, side-channel)
- `m7-quality`: Week 11 integration branch and pull request target
- `feature/quality-scenarios`: Week 11 work (quality scenarios, load tests, resilience mitigations, ADR-0008)
