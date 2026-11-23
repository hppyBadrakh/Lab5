# ADR-0007: Monolith to Distributed Slice

## Status

Accepted (2026-11-23). Proposed 2026-11-22. Solo project: there is no separate peer reviewer, so the author accepted it when the pull request was merged.

Builds on [ADR-0006](0006-hexagonal-ports-adapters.md). It does not supersede it: the hexagon stays, and this ADR decides where the hexagon's process boundary is.

## Context

The e-commerce code base is one deployable. Checkout is a synchronous in-process chain (`CheckoutFacade`: inventory, then payment, then order), and every module can reach every other module's classes and data. Three forces now push against that:

- **Deployment lockstep.** Order placement is the Core subdomain ([contexts.md](../contexts.md)) and changes weekly. Catalogue and customer code change rarely, but every order change redeploys and retests them.
- **Shared failure domain.** A slow payment provider or a heavy catalogue query uses the same threads and, from Milestone 3, the same connection pool as order placement. The one request that earns money fails together with everything else.
- **Side effects on the request path.** Since ADR-0006 the audit listener reacts to `OrderPlaced`, but it ran synchronously on the HTTP thread. Each new reaction (confirmation e-mail, stock reservation) would add its latency to checkout.

ADR-0006 already gave the Order context a framework-free core and ports for storage and events, so it is the one module that can be cut out without first untangling it. The full target is in [service-decomposition.md](../service-decomposition.md).

## Decision

We extract **one** slice, the **Order Service**, and leave the rest of the monolith as it is.

1. **Own process and own data.** The Order Service is the Spring Boot application in `com.ecommerce.order`. It owns its storage (`order_db`; in memory until the PostgreSQL adapter in Milestone 3) and shares no table, class or connection pool with the monolith. Customers and products are referenced by id and SKU only; the address and unit price are copied into the order as snapshots.
2. **REST contract with noun URIs** in `presentation.rest`. The HTTP method is the verb. State changes are sub-resources that are put in place.

| Method and URI | Success | Errors |
|---|---|---|
| `POST /api/v1/orders` | 201 + `Location` | 400 |
| `GET /api/v1/orders/{orderId}` | 200 | 404 |
| `GET /api/v1/customers/{customerId}/orders` | 200 | 400 |
| `PUT /api/v1/orders/{orderId}/payment` | 204 | 404, 409 |
| `PUT /api/v1/orders/{orderId}/cancellation` | 204 | 404, 409 |

   Every error is an RFC 7807 `application/problem+json` body.
3. **The contract is published and guarded.** springdoc generates OpenAPI 3 at `/v3/api-docs` and `/swagger-ui.html`. A snapshot is committed in `docs/openapi/rest-endpoints.json`, and `OpenApiSnapshotContractTest` fails the build when the live contract drifts from it, so an API change is always a visible diff in review.
4. **Asynchronous event side-channel** in `infrastructure.messaging`. `OrderPlaced` is handed to a dedicated, bounded thread pool (`@Async`), where it is audited and the customer confirmation is sent. The `201 Created` does not wait for it and cannot be failed by it.
5. **Fitness functions.** `DistributedSliceArchitectureFitnessTest` fails the build if the Order Service and the monolith depend on each other, if a controller or DTO lives outside `presentation.rest`, if a DTO leaks out of the REST adapter, or if an event listener is synchronous.

### Service evaluated but NOT extracted: Notification and Audit

We evaluated making the side-channel consumer its own **Notification and Audit Service** with its own database, fed by `OrderPlaced`. We decided **not** to extract it now. It stays inside the Order Service process as `OrderAuditAndNotificationSideChannelListener`.

- **There is no broker yet, so extraction would add coupling instead of removing it.** Without a message broker, the Order Service would have to call the new service over synchronous REST. That puts a second service on the path of every order (0.99 x 0.99 = 98% availability instead of 99%) to deliver a message nobody is waiting for. The alternative, introducing a broker and an outbox this week, is Milestone 3 scope.
- **It owns no data that needs a database of its own.** Today it keeps an audit list and sends one stateless message per order. Database-per-service would give it a datastore, a schema, backups and a pipeline to protect a log line.
- **Nothing forces independent scaling or ownership.** It handles exactly one message per placed order, so its load is the Order Service's load. The same person owns both; no second team is blocked by sharing a deployable.
- **The isolation we need is already there in-process.** The side-channel has its own thread pool and error boundary, which `SideChannelIsolationIntegrationTest` proves: a hanging or failing sender neither delays nor fails the `201`.
- **The seam is kept, so the decision is cheap to reverse.** The listener is reached only through events, and the sender is the `OrderNotificationSender` interface. ArchUnit forbids any other package from depending on `infrastructure.messaging`. Extracting it later means a broker adapter for `DomainEventPublisher` and moving one package.

**Revisit when** a broker is in place (Milestone 3) **and** a second producer needs notifications, for example payment receipts from the Payment Service or shipping updates. Then notification stops being a detail of ordering and deserves its own service.

## Consequences

Benefits:

- Order placement deploys, scales and fails on its own. A catalogue or customer change no longer redeploys checkout.
- Clients integrate against a published, versioned contract (`/api/v1`, OpenAPI), not against classes.
- New reactions to `OrderPlaced` are added on the side-channel and cost the checkout request nothing.
- One slice was moved, not five. The rest of the monolith keeps working while we learn what distribution costs.

Operational costs:

- **Duplicated data and eventual consistency.** Address and price snapshots in orders can differ from the customer profile and the catalogue. `GET /customers/{id}/orders` returns an empty list for an unknown customer, because the Order Service cannot ask who exists. Cross-service consistency needs compensating events instead of a rollback.
- **Side-channel events can be lost.** The channel is in-process and not durable. An event is lost if the process stops between the save and the hand-off, and the bounded queue (500) rejects events under overload instead of slowing checkout. Until the outbox and broker of Milestone 3, the audit trail is best effort.
- **A request can no longer be followed in one stack trace.** The audit line is written on a `side-channel-*` thread after the response. Debugging needs a correlation id and distributed tracing (OpenTelemetry), which is Milestone 3 work.
- **More to run.** A second deployable means its own pipeline, port, health check and, later, database with backups and migrations. An API gateway is needed in front ([api-gateway.md](../api-gateway.md)).
- **The contract is now a promise.** The OpenAPI snapshot must be regenerated and reviewed on every API change, and breaking changes need a `/api/v2`.
- **Breaking API change in this step.** `POST /orders/{id}/pay` and `POST /orders/{id}/cancel` (200 with a body) became `PUT /orders/{orderId}/payment` and `PUT /orders/{orderId}/cancellation` (204 without a body). Clients read the order afterwards if they need the new state.

## Rejected Alternatives

- **Services that share one database ("distributed monolith").** It keeps SQL joins, but a schema migration by one team still breaks the others at runtime, so we would pay for the network without gaining autonomy.
- **Extract all five services now.** Four of them have no stable contract yet, and we have no broker, tracing or outbox. We would multiply the operational costs above by five before learning from one.
- **Synchronous orchestration over REST** (the `CheckoutFacade` chain, but across the network). Checkout availability becomes the product of every service's availability (0.99^4 = 96%) and latencies add up.
- **Events only, no REST for placing orders.** The checkout page needs an immediate `201` or a field-level `400`. A purely asynchronous command would force the client to poll for a validation error.
- **A message broker for the side-channel now.** There is still only one process consuming the events. The `DomainEventPublisher` port lets a broker adapter replace the in-process one without touching the domain.
