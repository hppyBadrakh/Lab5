# Week 10 Evidence Checklist

Run these from a Git Bash terminal in the project folder. If `clean` fails with `Failed to delete target`, the VS Code Java extension was rebuilding in the background. Run the command again.

## Local evidence

### 1. Package tree of the slice
- **Run (PowerShell):** `tree /F src\main\java\com\ecommerce\order`
- **Must show:** `presentation\rest` with `OrderRestController`, `GlobalRestExceptionHandler`, `OpenApiConfiguration`; `presentation\rest\dto`; `infrastructure\messaging`; and no `infrastructure\adapter\in`.

### 2. Green build (15%)
- **Run:** `./mvnw clean verify`
- **Must show:** `Tests run: 160, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

### 3. REST contract tests (20%)
- **Run:** `./mvnw test "-Dtest=*ContractTest" "-Dsurefire.useFile=false"`
- **Must show:** `OrderRestControllerContractTest` with 18 tests and `OpenApiSnapshotContractTest` with 4 tests, 0 failures.

### 4. Live demo of all status codes (20%)
- **Run:** `./mvnw -q -DskipTests package` and then `bash scripts/demo.sh`
- **Must show:** 201 with `Location`, 200, 200 (customer order list), 404, 400 with `invalidFields`, 400 `would exceed the limit`, 204, 204, 409 `State Transition Conflict`, 200 `CANCELLED`.

### 5. OpenAPI (15%)
- **Run:** `./mvnw spring-boot:run`, then open http://localhost:8080/swagger-ui.html
- **Must show:** the "Orders" tag with 5 operations. Expand `POST /api/v1/orders` to show the example body, the 201 `Location` header and the 400 `application/problem+json` response. Expand the `OrderItemRequest` schema to show the `sku` pattern.
- **Snapshot:** `docs/openapi/rest-endpoints.json` is committed. To show the drift check, change a `@Schema(example = ...)` in `OrderItemRequest`, run `./mvnw test -Dtest=OpenApiSnapshotContractTest`, and see it fail with `REST contract changed`. Then undo the change.
- **Refreshing the snapshot after an intended change:** `./mvnw test -Dtest=OpenApiSnapshotContractTest "-Dopenapi.snapshot.update=true"`

### 6. Event side-channel (15%)
- **Run:** `./mvnw test -Dtest=SideChannelIsolationIntegrationTest "-Dsurefire.useFile=false"`
- **Must show:** 2 tests, 0 failures, and a `[Side-Channel ERROR]` log line on a `side-channel-` thread while the test still passes.
- **Also show:** in `target/demo-app.log` after the demo, the `[Side-Channel IN]` and `[Side-Channel OK]` lines with thread `side-channel-1`, not `http-nio-8080-exec-*`.

### 7. Architecture fitness
- **Run:** `./mvnw test "-Dtest=*ArchitectureFitnessTest" "-Dsurefire.useFile=false"`
- **Must show:** `DistributedSliceArchitectureFitnessTest` 11 tests and `HexagonalArchitectureFitnessTest` 10 tests, 0 failures.
- **Optional, stronger evidence:** remove `@Async(...)` from `OrderAuditAndNotificationSideChannelListener.onOrderPlaced` and run the test again. It must fail with `eventListenersAreAsynchronousAndLiveInMessaging`. Then undo the change.

### 8. Documents (20%)
- `docs/service-decomposition.md`: five services, database-per-service rules, sync versus async table.
- `docs/adr/0007-monolith-to-distributed-slice.md`: Status, Context, Decision, the section "Service evaluated but NOT extracted", Consequences with operational costs, Rejected Alternatives.
- `docs/m2-milestone-close.md`: verified baseline, C4 container view, what is locked.
- `docs/api-gateway.md`: bonus sketch.

## GitHub evidence

### 9. Branches
`m7-distributed` does not exist yet. The Week 9 pull request (`feature/ddd-bootstrap` -> `m6-ddd`) must be merged first, so that this pull request shows only Week 10 changes.

```bash
git fetch origin
git checkout m6-ddd && git pull          # after the W9 pull request is merged
git checkout -b m7-distributed && git push -u origin m7-distributed
git checkout feature/distributed-slice && git push -u origin feature/distributed-slice
```

### 10. Pull request, checks and approval (10%)
- **a. PR page:** title `[W10] distributed-slice`, base `m7-distributed`, head `feature/distributed-slice`, description from `docs/PR_DESCRIPTION_W10.md` including the 4 reflection sentences.
- **b. Checks tab:** Build, Pure domain tests, REST contract tests, Architecture fitness and Full verify, all green.
- **c. Approval:** a peer's "Approved" entry. Only a second GitHub account can approve.
- **After approval:** change the Status of ADR-0007 (and ADR-0006) to `Accepted (approved by <reviewer> on <date>)`, tick the two open boxes in `docs/m2-milestone-close.md`, then commit and push.
