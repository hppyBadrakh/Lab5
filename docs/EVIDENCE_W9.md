# Week 9 Evidence Checklist

Run these from a Git Bash terminal in the project folder. If `clean` fails with `Failed to delete target`, the VS Code Java extension was rebuilding in the background. Run the command again.

## Local evidence

### 1. Hexagon package tree
- **Run (PowerShell):** `tree /F src\main\java\com\ecommerce\order`
- **Must show:** `domain\model`, `domain\event`, `domain\port\in`, `domain\port\out`, `application`, and `infrastructure\adapter\in\{web,event}` and `infrastructure\adapter\out\{persistence,event}`.

### 2. Green build
- **Run:** `./mvnw clean verify`
- **Must show:** the final `Tests run: ..., Failures: 0, Errors: 0` line and `BUILD SUCCESS`.

### 3. Pure domain tests without Spring
- **Run:** `./mvnw test "-Dtest=*DomainTest,*AggregateTest,*ValueObjectTest" "-Dsurefire.useFile=false"`
- **Must show:** `Order Aggregate Root - domain invariant tests` and its nested groups (Line items, 10,000 order limit, Lifecycle, Domain events), sub-second times, no Spring Boot banner, and `BUILD SUCCESS`.

### 4. Hexagonal architecture fitness
- **Run:** `./mvnw test -Dtest=HexagonalArchitectureFitnessTest "-Dsurefire.useFile=false"`
- **Must show:** `Tests run: 10, Failures: 0` and `BUILD SUCCESS`.
- **Optional, stronger evidence:** add `@org.springframework.stereotype.Component` above `public record Sku` and run the test again. It must fail with `domainMustBePure`. Then undo the change.

### 5. Live demo
- **Run:** `./mvnw -q -DskipTests package` and then `bash scripts/demo.sh`
- **Must show:** 201 `PLACED`, 200, 404, 400 with `invalidFields`, 400 `would exceed the limit of 10000.00 USD`, 200 `PAID`, 200 `CANCELLED`, 409.
- **Also show:** the `[AUDIT] Captured OrderPlaced event` line in `target/demo-app.log`.

### 6. Documents
- `docs/contexts.md`: the context map and the ubiquitous language table.
- `docs/adr/0006-hexagonal-ports-adapters.md`: Status, Context, Decision, Consequences, Rejected Alternatives.

### 7. Git history
- **Run:** `git log --oneline --graph --decorate -n 8`
- **Must show:** the 6 Week 9 commits on `feature/ddd-bootstrap`, on top of `m6-ddd`.

## GitHub evidence

### 8. Pull request, checks and approval
- **a. PR page:** the title `[W9] ddd-bootstrap - rich aggregate, value objects & hexagonal ports`, base `m6-ddd`, head `feature/ddd-bootstrap`, and the description with the 4 reflections.
- **b. Checks tab:** Build, Pure domain tests, Hexagonal architecture fitness and Full verify, all green.
- **c. Approval:** a peer's "Approved" entry. Only a second GitHub account can approve.
- **After approval:** change ADR-0006 Status to `Accepted (approved by <reviewer> on <date>)`, then commit and push.

## Bonus L9: domain expert review

Ask a classmate to act as product owner or domain expert and leave a PR review comment that answers these questions in their own words:

1. Do the glossary terms in `docs/contexts.md` (Draft, Place, Pay, Cancel, Order limit, SKU) match how the business talks?
2. Is 10,000 in the order currency the right limit, or should it be converted to USD?
3. Should a PAID order be cancellable directly, or only through a refund?
4. Is it right that the same SKU at a different price is rejected instead of added as a second line?

Screenshot their comment and your reply.
