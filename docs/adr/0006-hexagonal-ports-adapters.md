# ADR-0006: Hexagonal Ports and Adapters

## Status

Proposed (2026-11-08). Change to Accepted once a peer has approved the pull request.

Supersedes [ADR-0005](0005-layered-mvc-for-orders.md) for the Order Processing context.

## Context

ADR-0005 split the order subsystem into four layers (Presentation -> Application -> Domain <- Infrastructure). The domain package already had no Spring imports, but the Week 7 code showed symptoms that the layered style did not prevent:

- **The domain did not own its contracts.** The `OrderRepository` port lived in `application`, and the ArchUnit rule `Domain mayNotAccessAnyLayer` meant the domain could not declare what it needs from the outside world (storage, event publishing). The application layer decided those contracts instead.
- **An anemic, easily misused model.** `Order` was built from a ready-made item list. `transitionTo(OrderStatus)` was public, so any caller could drive the lifecycle. Order and customer ids were both plain `String`s and could be swapped by mistake. `Money` stored its currency as a free string (`"usd"`, `"XYZ"` were accepted) and had `of(double)` and `multiply(double)`. Quantity was an unbounded `int`, and there was no order total limit.
- **No home for side effects.** Auditing a placed order or asking Inventory to reserve stock would have to be added to `OrderApplicationService`, so the service would grow with every new reaction, and a failing reaction would fail checkout.

The course now needs a richer core domain (Order is our Core subdomain, see [contexts.md](../contexts.md)) with aggregate invariants and domain events, and persistence that can change (in-memory today, JPA in M3) without touching business rules.

## Decision

We adopt **Hexagonal Architecture (Ports and Adapters)** for the Order Processing context, with tactical DDD inside the hexagon.

| Package | Role | Contents |
|---|---|---|
| `domain.model` | Core | `Order` aggregate root, `OrderItem` entity, value objects `Money`, `Sku`, `Quantity`, `OrderId`, `CustomerId`, `ShippingAddress`, `OrderStatus` |
| `domain.event` | Core | `DomainEvent`, `OrderPlacedDomainEvent`, `OrderCancelledDomainEvent` |
| `domain.port.in` | Inbound port | `ManageOrdersUseCase` and its command records |
| `domain.port.out` | Outbound ports | `OrderRepository`, `DomainEventPublisher` |
| `application` | Use case implementation | `OrderApplicationService implements ManageOrdersUseCase`: load, call the aggregate, save, publish |
| `infrastructure.adapter.in.web` | Driving adapter | REST controller, DTOs, RFC 7807 error handler. Depends on the inbound port only |
| `infrastructure.adapter.in.event` | Driving adapter | `OrderAuditEventListener` (`@EventListener`) |
| `infrastructure.adapter.out.persistence` | Driven adapter | `InMemoryOrderRepository` |
| `infrastructure.adapter.out.event` | Driven adapter | `SpringDomainEventPublisher` (Spring `ApplicationEventPublisher`) |

Rules:

- **The domain core is pure Java 21.** It depends only on `java..` and itself: no Spring, Jakarta, ORM or library types.
- **The aggregate is the consistency boundary.** `Order` enforces the 10,000 limit, currency uniformity, non-empty placement and the `DRAFT -> PLACED -> PAID / CANCELLED` lifecycle. A rejected command leaves it unchanged. `OrderItem` has no public constructor or mutator.
- **Events are published after the save.** `Order` records events, and the application service calls `pullDomainEvents()` after `OrderRepository.save` succeeds. The publisher adapter isolates listener failures, so a side effect never fails or undoes the order change.
- **Fitness functions.** `HexagonalArchitectureFitnessTest` (ArchUnit) fails the build if the domain imports a framework, if a dependency points outward (onion rule), if a driving adapter uses the service class or an outbound port directly, if an outbound adapter does not implement a domain port, or if a package cycle appears. CI runs it as its own stage.

## Consequences

Benefits:

- The 68 domain tests (`*DomainTest`, `*AggregateTest`, `*ValueObjectTest`) run with plain JUnit, without a Spring context, in well under a second per class.
- Business invariants are guaranteed in one place. No caller can add items to a cancelled order, mix currencies or skip a lifecycle step.
- A JPA adapter, or a message broker for events, can be plugged in by implementing an outbound port, with no change to the domain or use case code.
- New reactions to `OrderPlaced` (inventory reservation, email) are new listeners, not new lines in the service.

Costs:

- **Mapping boilerplate.** A new order field now touches up to five types: request DTO, command record, value object, aggregate and response DTO. The REST adapter maps every request and response by hand.
- **More packages and indirection.** One bounded context now has nine packages. Following a request crosses the controller, the inbound port, the service, the aggregate, two outbound ports and their adapters. New team members need to learn the vocabulary first.
- **In-process events are not durable.** If the process dies after `save` and before `publish`, the event is lost. Listeners run synchronously on the request thread and add latency. A transactional outbox is needed before Inventory relies on these events.
- **Breaking API change.** The request now takes `sku` and a `BigDecimal` `unitPrice` instead of `productId`, `productName` and a `double`, requires `shippingAddress`, and the first status is `PLACED` instead of `PENDING`. Existing clients must migrate.
- **Persistence is still a prototype.** The in-memory adapter stores live aggregates and has no optimistic locking (unchanged from ADR-0005). The JPA adapter will also need a reconstitution factory on `Order` to rebuild stored state without replaying commands.
- **Accepted coupling inside the domain.** `domain.model` and `domain.event` depend on each other (the aggregate creates events, and events carry value objects). The cycle check runs across top-level packages, where `domain` is one slice.

## Rejected Alternatives

- **Clean Architecture (four rings, Uncle Bob style).** It has the same dependency rule, but it also prescribes an input boundary, a request model, a response model and a presenter for every use case. With four use cases and only one delivery mechanism (REST), that is about a dozen extra types that only copy data, so the indirection does not pay off. Ports and Adapters gives the same isolation with fewer mandatory layers, and we can still add a presenter later if a second UI appears.
- **Keep the layered MVC of ADR-0005.** It keeps ports in the application layer, so the domain still cannot own its contracts or publish events through a port, and nothing structural stops the model from becoming anemic again.
- **JPA annotations on the domain model.** Switching storage (for example PostgreSQL to DynamoDB) would then recompile and retest the business rules, and domain tests would need an ORM session. Already rejected in ADR-0005.
- **A message broker (Kafka, RabbitMQ) for domain events now.** There is no second process to consume the events yet. The `DomainEventPublisher` port lets us swap the in-process adapter for a broker adapter when Inventory becomes a separate service.
