# [W10] distributed-slice

Base: `m7-distributed`. Head: `feature/distributed-slice`.

## Summary

This PR extracts the Order Service as the first distributed slice of the e-commerce monolith and closes Milestone 2. The hexagon from Week 9 is unchanged inside; what is new is its edge: a published REST contract, an asynchronous event side-channel, and the documents that decide where the service boundary is.

- **Block A:** `docs/service-decomposition.md` splits the monolith into five services (Order, Catalogue, Inventory, Customer, Payment), each with a private database, and says which interactions are synchronous REST and which are asynchronous events.
- **Block B:** `presentation.rest.OrderRestController` has five endpoints on noun URIs: `POST /orders` (201 + `Location`), `GET /orders/{orderId}` (200), `GET /customers/{customerId}/orders` (200), `PUT /orders/{orderId}/payment` (204) and `PUT /orders/{orderId}/cancellation` (204). Errors are RFC 7807 bodies with 400, 404 and 409.
- **OpenAPI:** springdoc serves `/swagger-ui.html` and `/v3/api-docs`. Controller and DTOs carry `@Tag`, `@Operation`, `@ApiResponse`, `@Parameter` and `@Schema`. The snapshot is `docs/openapi/rest-endpoints.json`, and `OpenApiSnapshotContractTest` fails when the live contract drifts from it.
- **Block C:** `infrastructure.messaging.OrderAuditAndNotificationSideChannelListener` handles `OrderPlaced` with `@Async` on its own bounded thread pool. `SideChannelIsolationIntegrationTest` shows that a hanging sender does not delay the 201 and a failing sender does not fail it.
- **Block D:** `docs/adr/0007-monolith-to-distributed-slice.md` records the decision, names Notification and Audit as the service we did not extract, and lists six operational costs. `docs/m2-milestone-close.md` closes M2 with the C4 container view.
- **Block E:** `OrderRestControllerContractTest` (18 MockMvc tests) and `DistributedSliceArchitectureFitnessTest` (11 ArchUnit rules). CI has a new REST contract stage and runs for `m7-distributed`.
- **Bonus L9:** `docs/api-gateway.md` with a routing table, rate limits and BFF segmentation.

## API changes

Breaking. `POST /orders/{id}/pay` and `POST /orders/{id}/cancel` (200 with a body) are now `PUT /orders/{orderId}/payment` and `PUT /orders/{orderId}/cancellation` (204, no body). `GET /customers/{customerId}/orders` is new. The REST adapter moved from `infrastructure.adapter.in.web` to `presentation.rest`, and the event listener from `infrastructure.adapter.in.event` to `infrastructure.messaging`.

## Reflection

1. **Database-Per-Service Isolation:** Giving the Order Service its own data means it can no longer join orders with customer profiles or the catalogue, so each order keeps its own snapshot of the shipping address and unit price, and `GET /customers/{customerId}/orders` returns an empty list for an unknown customer because the Order Service has no way to ask who exists.
2. **OpenAPI Constraint Value:** The `@Pattern` constraints on `sku` and `currency`, published in the schema together with `@Schema(example = "ELEC-1001")`, were the most valuable, because a client can see the exact accepted format before sending anything and a malformed payload is rejected with a field-level 400 before it reaches the domain.
3. **Event Side-Channel Resilience:** The side-channel runs `OrderPlaced` handling on a separate bounded thread pool behind an error boundary, so the 201 Created is returned while the confirmation sender is still hanging and is unaffected when it throws, at the price of events that can be lost until an outbox exists.
4. **ADR-0007 Non-Extraction Rationale:** We decided not to extract Notification and Audit into its own service because, without a broker, the Order Service would have to call it synchronously on every order, which lowers checkout availability to deliver a message nobody waits for, while the service would own no data that justifies a separate database.

## Verification

`./mvnw clean verify` passes locally: 160 tests, 0 failures, no compiler warnings under `-Xlint:all`. `bash scripts/demo.sh` against the running jar shows 201, 200, 200, 404, 400, 400, 204, 204 and 409, the Swagger UI answering at `/swagger-ui.html`, and the `[Side-Channel ...]` log lines written by `side-channel-1` instead of an `http-nio` thread.
