# Week 11 Evidence Checklist

Run these from a **Git Bash** terminal in the project folder. In PowerShell, `bash` starts WSL instead of Git Bash, so there you would use `& 'C:\Program Files\Git\bin\bash.exe' scripts/benchmark.sh ...`. `JAVA_HOME` must point at JDK 25.

## Local evidence

### 1. Green build
- **Run:** `./mvnw clean verify`
- **Must show:** `Tests run: 194, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

### 2. Resilience tests (Block F)
- **Run:** `./mvnw test "-Dtest=ResilientPaymentAdapterTest" "-Dsurefire.useFile=false"`
- **Must show:** 6 tests passing, including:
  - "Invariant 2: repeated gateway timeouts trip the circuit from CLOSED to OPEN"
  - "Invariant 3: an OPEN circuit short-circuits in under 5 ms (p95) without calling the gateway"
  - the bulkhead test

### 3. MockMvc and ArchUnit (Block F)
- **Run:** `./mvnw test "-Dtest=*Resilient*,*ContractTest,QualityArchitectureFitnessTest"`
- **Must show:** `ResilientCheckoutApiContractTest`, `RateLimitedCheckoutContractTest`, `UnprotectedBaselineContractTest` and `QualityArchitectureFitnessTest` (6 rules) all green.

### 4. Load test, baseline versus mitigated (Block B)
- **Run:** first `./mvnw -q -DskipTests package`, then:
  - `bash scripts/benchmark.sh baseline`
  - `bash scripts/benchmark.sh mitigated`
- **Must show:** the two Markdown tables.
  - **Baseline:** `max Tomcat busy threads observed: 200 / 200`, about 60% `500` on `pay`, and the probe p95 in seconds.
  - **Mitigated:** about `26 / 200`, only `202` and `204`, and the probe p95 around 2 ms.
- Your exact numbers will differ a little from the committed ones.

### 5. Rate limiter under a surge (Bonus)
- **Run:** `bash scripts/benchmark.sh surge`
- **Must show:** `201` at about 148/s (about 4,400 in 30 s) and the rest `429`.

### 6. Circuit breaker live
- Start `java -jar target/ecommerce-structures-1.0-SNAPSHOT.jar --payment.gateway.slow-call-ratio=1`. Then:
  1. Place 5 orders with `scripts/demo.sh` payloads or Swagger UI.
  2. Pay all 5.
  3. Open `http://localhost:8080/actuator/circuitbreakers`.
- **Must show:**
  - the first payments take about 3 s and answer 202;
  - `paymentService` shows `"state": "OPEN"`;
  - the next payment answers 202 at once;
  - the log has `[Payment Fallback]` lines.

### 7. Documents
- `docs/quality/scenarios.md`: the summary table and S2's six-part table.
- `docs/quality/measurement-evidence.md`: the Run 1 table with three columns, and the bottleneck diagnosis.
- `docs/quality/utility-tree.md`: the tree and the High-leaf rationales.
- `docs/adr/0008-payment-resilience-and-load-shedding.md`: Context, Decision, the operational costs, Rejected Alternatives and the M3 Draft.

### 8. Git history
- **Run:** `git log --oneline --graph --decorate -n 7`
- **Must show:** the Week 11 commits on `feature/quality-scenarios`, on top of `m7-quality`.

## GitHub evidence

### 9. Pull request
- **a. PR page:** the title `[W11] quality-scenarios - ...`, base `m7-quality`, and the description with the 4 reflections.
- **b. Checks:** Build, Pure domain tests, Resilience and contract tests, Architecture fitness, Full verify, all green.
- **c. Merge:** solo project, so there is no peer approval. Screenshot the "merged" entry instead. ADR-0008 is Accepted.
