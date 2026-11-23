# E-Commerce Order Subsystem

ICSI304 coursework. The order subsystem in `com.ecommerce.order` is a Spring Boot application built as a DDD hexagon (Week 9). The Week 5 structural pattern code remains in `mn.edu.num.sad.ecommerce`.

## Requirements
- JDK 21 (`JAVA_HOME` must point at it)
- No Maven install needed: use the wrapper

## Build and test
```powershell
.\mvnw.cmd clean verify
```
On Linux, macOS, or Git Bash, use `./mvnw clean verify`.

The build compiles with `-Xlint:all` and fails on any compiler warning.

Run only the pure domain tests (no Spring context) or only the architecture rules:
```bash
./mvnw test "-Dtest=*DomainTest,*AggregateTest,*ValueObjectTest"
./mvnw test -Dtest=HexagonalArchitectureFitnessTest
```

## Run
```powershell
.\mvnw.cmd spring-boot:run
```

| URL | Purpose |
|---|---|
| `POST /api/v1/orders` | Place an order (201 + `Location`, status `PLACED`) |
| `GET /api/v1/orders/{id}` | Read an order (200, or 404) |
| `POST /api/v1/orders/{id}/pay` | Mark a placed order as paid (200, or 409) |
| `POST /api/v1/orders/{id}/cancel` | Cancel an order (200, or 409 if the state forbids it) |
| http://localhost:8080/swagger-ui.html | Swagger UI |
| http://localhost:8080/v3/api-docs | OpenAPI document |

Example request body:
```json
{
  "customerId": "CUST-1",
  "shippingAddress": {"street": "Peace Ave 1", "city": "Ulaanbaatar", "postalCode": "14200", "countryCode": "MN"},
  "items": [{"sku": "ELEC-1001", "unitPrice": 25.50, "currency": "USD", "quantity": 2}]
}
```

Errors are RFC 7807 `ProblemDetail` responses. Orders live in memory, so they disappear when the application stops.

## Demo
```bash
bash scripts/demo.sh
```
This builds the jar if needed, starts the application, walks through the 201, 200, 404, 400, 400 (over the 10,000 limit), 200 (pay), 200 (cancel) and 409 cases with `curl`, and stops the application. If something already answers on `BASE_URL` (default `http://localhost:8080`), the script uses that instead.

## Architecture
```text
 adapter.in.web ──┐                                      ┌── adapter.out.persistence
 adapter.in.event ┴─> domain.port.in ─> application ─> domain.port.out <─┴── adapter.out.event
                                              │
                                              v
                                 domain.model + domain.event   (pure Java 21)
```
| Package | Contents |
|---|---|
| `domain.model` | `Order` aggregate root, `OrderItem` entity, value objects `Money`, `Sku`, `Quantity`, `OrderId`, `CustomerId`, `ShippingAddress`, `OrderStatus` |
| `domain.event` | `OrderPlacedDomainEvent`, `OrderCancelledDomainEvent` |
| `domain.port.in` | `ManageOrdersUseCase` (inbound port) |
| `domain.port.out` | `OrderRepository`, `DomainEventPublisher` (outbound ports) |
| `application` | `OrderApplicationService` (implements the inbound port) and application exceptions |
| `infrastructure.adapter.in.web` | REST controller, DTOs, `GlobalExceptionHandler` |
| `infrastructure.adapter.in.event` | `OrderAuditEventListener` |
| `infrastructure.adapter.out.persistence` | `InMemoryOrderRepository` |
| `infrastructure.adapter.out.event` | `SpringDomainEventPublisher` |
| `export` | Bonus Pipe-and-Filter CSV pipeline (depends on the domain only) |

`HexagonalArchitectureFitnessTest` enforces these rules with ArchUnit. See [docs/contexts.md](docs/contexts.md) for the context map and ubiquitous language, and [ADR-0006](docs/adr/0006-hexagonal-ports-adapters.md) for the decision.

`legacy/LegacyOrderEndpoint.java` is the "before" code. It sits outside `src/` and is not compiled.

## Branches
- `m5-behaviour`: baseline
- `feature/styles-I-sbs`: Week 7 work (layered MVC)
- `m6-styles`: Week 7 integration branch
- `m6-ddd`: Week 9 integration branch and pull request target
- `feature/ddd-bootstrap`: Week 9 work (DDD and hexagonal architecture)
