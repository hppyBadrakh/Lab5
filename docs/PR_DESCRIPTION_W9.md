## Summary

This PR turns the Week 7 layered order slice into a DDD hexagon for the Order Processing context, which is our core domain.

- **Block A:** `docs/contexts.md` has the context map. Order is the Core domain, Inventory is Supporting (customer-supplier), and Payment is Generic (open host service). It also has the ubiquitous language glossary and the business invariants.
- **Block B:** immutable Java 21 records `Money` (`BigDecimal` + `Currency`), `Sku`, `Quantity`, `OrderId`, `CustomerId` and `ShippingAddress`. `OrderItem` is an entity with its own line id and no public setters or constructor.
- **Block C:** `Order` is the aggregate root. It enforces the 10,000 limit (in the order currency), currency uniformity, non-empty placement, `DRAFT -> PLACED -> PAID / CANCELLED`, and the 1..999 quantity on merged lines. Every check runs before mutation.
- **Ports:** `OrderRepository` and `DomainEventPublisher` live in `domain.port.out`, and `ManageOrdersUseCase` in `domain.port.in`. Adapters live in `infrastructure.adapter.{in,out}`. The domain has no Spring, Jakarta or ORM types.
- **Block D:** `OrderPlacedDomainEvent` and `OrderCancelledDomainEvent` are published after the save. `OrderAuditEventListener` records placed orders, and a failing listener cannot fail checkout.
- **Block E:** `docs/adr/0006-hexagonal-ports-adapters.md` chooses Ports and Adapters over Clean Architecture. ADR-0005 is marked superseded.
- **Fitness tests:** `HexagonalArchitectureFitnessTest` has 10 ArchUnit rules, including the handout's `domainMustBePure`, an onion rule and a cycle check. CI runs build, pure domain tests, architecture and full verify for `m6-ddd`.

## API changes

`POST /api/v1/orders` now takes `sku`, a decimal `unitPrice` with at most 2 decimals, and a required `shippingAddress`. A new order comes back as `PLACED`. `POST /api/v1/orders/{id}/pay` is new. A total above 10,000 returns 400. `scripts/demo.sh` and the README show the new payload.

## Reflection

1. **Fragile Invariant Resolved:** Before the Order aggregate root, an order had no total limit, accepted any item quantity, and exposed a public `transitionTo` that let any caller drive the lifecycle, whereas the aggregate now accepts items only in DRAFT, caps the total at 10,000 in the order currency, and changes status only through `placeOrder`, `markAsPaid` and `cancel`.
2. **Dangerous Primitive Wrapped:** The free-text currency `String` together with `Money.of(double)` was our most hazardous primitive, because `"usd"`, `"XYZ"` and binary rounding errors all slipped through, while the new `Money` record pairs a `BigDecimal` with a real `java.util.Currency` and refuses to mix currencies.
3. **Side-Effect Decoupling:** Publishing `OrderPlacedDomainEvent` after the save lets the audit listener (and later inventory reservation) react without adding code to the Order aggregate or the place-order use case, and a failing listener cannot fail or undo checkout.
4. **Operational Trade-Off:** In adopting Hexagonal Architecture, we accepted hand-written DTO-to-command-to-domain mapping and more packages to follow in exchange for a framework-free domain whose 68 tests run without starting Spring.

## Verification

`./mvnw clean verify` passes locally with 0 failures and no compiler warnings. I checked that `HexagonalArchitectureFitnessTest` fails when a Spring annotation is added to a domain class, or when the controller depends on `OrderRepository`.
