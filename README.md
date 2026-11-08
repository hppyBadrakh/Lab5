# E-Commerce Order Subsystem

ICSI304 coursework. The Week 7 work is a layered Spring Boot order subsystem in `com.ecommerce.order`. The Week 5 structural pattern code remains in `mn.edu.num.sad.ecommerce`.

## Requirements
- JDK 21 (`JAVA_HOME` must point at it)
- No Maven install needed: use the wrapper

## Build and test
```powershell
.\mvnw.cmd clean verify
```
On Linux, macOS, or Git Bash, use `./mvnw clean verify`.

The build compiles with `-Xlint:all` and fails on any compiler warning.

## Run
```powershell
.\mvnw.cmd spring-boot:run
```

| URL | Purpose |
|---|---|
| `POST /api/v1/orders` | Create an order (201 + `Location`) |
| `GET /api/v1/orders/{id}` | Read an order (200, or 404) |
| `POST /api/v1/orders/{id}/cancel` | Cancel an order (200, or 409 if the state forbids it) |
| http://localhost:8080/swagger-ui.html | Swagger UI |
| http://localhost:8080/v3/api-docs | OpenAPI document |

Errors are RFC 7807 `ProblemDetail` responses.

Orders live in memory, so they disappear when the application stops.

## Demo
```bash
bash scripts/demo.sh
```
This builds the jar if needed, starts the application, walks through the 201, 200, 404, 400, and 409 cases with `curl`, and stops the application. If something already answers on `BASE_URL` (default `http://localhost:8080`), the script uses that instead.

## Architecture
```text
Presentation -> Application -> Domain <- Infrastructure
```
| Package | Contents |
|---|---|
| `presentation` | `OrderController`, DTOs, `GlobalExceptionHandler` |
| `application` | `OrderApplicationService`, `OrderRepository` port, application exceptions |
| `domain` | `Order`, `OrderItem`, `Money`, `OrderStatus` (pure Java) |
| `infrastructure` | `InMemoryOrderRepository` adapter |
| `export` | Bonus Pipe-and-Filter CSV pipeline (depends on `domain` only) |

`ArchitectureLayerEnforcementTest` enforces these rules with ArchUnit. See [ADR-0005](docs/adr/0005-layered-mvc-for-orders.md) and the [M2 progress note](docs/m2-progress.md).

`legacy/LegacyOrderEndpoint.java` is the "before" code. It sits outside `src/` and is not compiled.

## Branches
- `m5-behaviour`: baseline
- `feature/styles-I-sbs`: Week 7 work
- `m6-styles`: integration branch and pull request target
