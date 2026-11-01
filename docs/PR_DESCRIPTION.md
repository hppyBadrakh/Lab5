## Summary

I replaced the legacy order endpoint with a layered Spring Boot MVC slice in `com.ecommerce.order`. The code is split into four packages (`presentation`, `application`, `domain`, `infrastructure`), and storage sits behind an `OrderRepository` port with a `ConcurrentHashMap` adapter.

- **API:** `POST /api/v1/orders` returns 201 with `Location`, `GET /api/v1/orders/{id}` returns 200, and `POST /api/v1/orders/{id}/cancel` returns 200.
- **Errors:** 400, 404, 409, and 500 all return RFC 7807 `ProblemDetail`, and the 500 response leaks no internals.
- **Architecture checks:** `ArchitectureLayerEnforcementTest` uses ArchUnit to enforce layering, zero package cycles, and a pure Java domain.
- **Decision record:** `docs/adr/0005-layered-mvc-for-orders.md` (Status: Proposed until this PR is approved).
- **M2:** `docs/m2-progress.md` has the progress note and the updated C4 container diagram.
- **Bonus L7:** a Pipe-and-Filter CSV export in `com.ecommerce.order.export`, with a 10,000-order streaming test. It is not exposed through the API yet.

## Changes to the handout code

- `Order` now rejects mixed currencies when it is created. The handout version stored such an order and then failed with a 500 on every read.
- I added `InvalidOrderRequestException`, which maps to 400, for requests the domain rejects.
- `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler` so the 500 catch-all does not turn malformed JSON or a 405 into a 500.
- CI adds a fourth job, `Full verify`, because the three handout stages skip the export tests and the older Lab 5 tests.

## Reflection

1. **Style Selection:** I chose a four-layer style with an MVC REST slice because the legacy endpoint mixed payload parsing, the 15% discount and 10% tax rules, and raw JDBC in one method, so none of those rules could be tested alone.
2. **Cycle Prevention:** I kept dependencies pointing inward (Presentation -> Application -> Domain <- Infrastructure) and added ArchUnit rules that fail the build on a package cycle or on any non-Java dependency in the domain.
3. **Controller Isolation:** `OrderController` only validates DTOs, delegates to `OrderApplicationService`, and maps the result to `OrderResponse`, so HTTP concerns stay out of the business rules and clients cannot over-post fields such as `status`.
4. **Evolutionary Seam:** Because the `OrderRepository` port lives in the application layer and returns the `Order` aggregate, I can replace the in-memory adapter with a JPA adapter in M3 without changing the domain model or the use case logic.

## Verification

`./mvnw clean verify` passes locally with 97 tests and no compiler warnings. `OrderControllerWebMvcTest` covers 200, 201, 400, 404, 405, 409, and 500. To try the API by hand, run `bash scripts/demo.sh`.
