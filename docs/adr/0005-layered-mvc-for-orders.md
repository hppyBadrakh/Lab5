# ADR-0005: Layered MVC for Orders

## Status

Proposed (2026-11-01). Change to Accepted once a peer has approved the pull request.

## Context

`legacy/LegacyOrderEndpoint.java` handles every order concern in one god-controller method:

- It parses a raw `Map` payload with no schema validation.
- It hardcodes business rules in the controller: a 15% VIP discount and 10% tax.
- It opens a raw JDBC connection and runs its SQL insert inside the controller.
- It returns its error JSON as a normal response, so failures look like 200 OK.

As a result, the discount and tax rules cannot be tested without a live HTTP call and database, and a tax change means editing the HTTP endpoint.

## Decision

We split the order subsystem in `com.ecommerce.order` into four layers, served as a Spring Boot REST MVC slice:

| Layer | Responsibility |
|---|---|
| `presentation` | Thin controller, validated DTOs, RFC 7807 errors |
| `application` | Use case orchestration and the `OrderRepository` port |
| `domain` | `Order`, `OrderItem`, `Money`, `OrderStatus`, in pure Java |
| `infrastructure` | `InMemoryOrderRepository` adapter (`ConcurrentHashMap`) |

Dependencies follow Presentation -> Application -> Domain <- Infrastructure, and infrastructure implements the application port. The presentation layer may read domain types only to map them to DTOs. `ArchitectureLayerEnforcementTest` fails the build on any layer breach or package cycle.

**Persistence policy:** only `OrderRepository` reaches storage, and it accepts and returns the `Order` aggregate, never a storage type. The in-memory adapter is the only implementation for now, so orders do not survive a restart.

**Transaction policy:** the transaction boundary is the public `OrderApplicationService` method. Each use case performs one load-change-save on one aggregate. The in-memory adapter provides no real transactions, so `@Transactional` will be added to those same methods when the JPA adapter arrives.

## Rejected Alternatives

- **Pipe-and-Filter for checkout:** checkout needs a synchronous, low-latency request and response, not a stream. The style fits batch export, so it is used only there.
- **Microservices:** one subsystem has no independent scaling or team boundary that would justify network calls and distributed failure handling.
- **Direct JPA Active Record:** putting persistence on the entities would bring ORM sessions and database configuration into domain tests, which is the coupling we are removing.

## Consequences

Benefits:

- Domain and service rules are tested without Spring, HTTP, or a database.
- A JPA adapter can replace the in-memory adapter without changing the domain or the service logic.

Costs:

- **Mapping boilerplate:** an order field can touch the request DTO, command, domain model, and response DTO.
- **Call indirection:** each request crosses more layers, which makes tracing a request longer.
- **Data loss on restart:** the in-memory store loses all orders and works for one instance only, so it is not ready for production.
- **Concurrent updates are not serialized:** the adapter shares live `Order` objects and has no optimistic locking, so two simultaneous cancels of one order can both succeed. The JPA adapter should close this gap with a version column.
- **Money precision:** the `Money.of(double, ...)` factory and the `double` price in the API accept binary floating point at the boundary. `BigDecimal` and two-decimal rounding keep calculations exact afterward, but the API should move to decimal strings before real payments.
