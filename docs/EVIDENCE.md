# Week 7 Evidence Checklist

Take these screenshots in order. Open a Git Bash terminal in the project folder unless a step says otherwise (in VS Code: Terminal > New Terminal > Git Bash).

If `clean` fails with `Failed to delete target`, the VS Code Java extension was rebuilding in the background. Run the command again.

## Local evidence

### 1. Project tree with the 4 packages
- **Open:** a PowerShell terminal, or the VS Code Explorer.
- **Run:** `tree /F src\main\java\com\ecommerce`
- **Must show:** `presentation`, `application`, `domain`, `infrastructure`, and `export`, with their classes.

### 2. Green build
- **Run:** `./mvnw clean verify`
- **Must show:** the last `Tests run: 97, Failures: 0, Errors: 0, Skipped: 0` line and `BUILD SUCCESS`.

### 3. MockMvc status code tests
- **Run:** `./mvnw test -Dtest=OrderControllerWebMvcTest "-Dsurefire.reportFormat=plain" "-Dsurefire.useFile=false"`
- **Must show:** the test names with 200, 201, 400, 404, 409, and 500 in them, `Tests run: 11, Failures: 0`, and `BUILD SUCCESS`.
- **Note:** a stack trace appears in this output on purpose. The 500 test forces an error, and the server logs it. The HTTP response stays generic, which the test asserts.

### 4. Architecture rules with zero cycles
- **Run:** `./mvnw test -Dtest=ArchitectureLayerEnforcementTest "-Dsurefire.reportFormat=plain" "-Dsurefire.useFile=false"`
- **Must show:** `packagesAreFreeOfCycles`, `layersPointInward`, `domainIsPureJava`, `Tests run: 8, Failures: 0`, and `BUILD SUCCESS`.

### 5. Live demo
- **Run:** `./mvnw -q -DskipTests package` and then `bash scripts/demo.sh`
- **Must show:** all 6 steps, ideally across two screenshots:
  1. POST: `HTTP/1.1 201` with the `Location` header
  2. GET: `HTTP/1.1 200`, with the same order id
  3. GET unknown id: `HTTP/1.1 404` with `application/problem+json`
  4. Invalid body: `HTTP/1.1 400` with `invalidFields`
  5. First cancel: `HTTP/1.1 200` with `CANCELLED`
  6. Second cancel: `HTTP/1.1 409` with `application/problem+json`
- **Optional:** run `./mvnw spring-boot:run` and open http://localhost:8080/swagger-ui.html to show Swagger UI.

### 6. ADR-0005
- **Open:** `docs/adr/0005-layered-mvc-for-orders.md` on GitHub after pushing, or use the Markdown preview in VS Code (`Ctrl+Shift+V`).
- **Must show:** the title and Status, Context, Decision, Rejected Alternatives, and Consequences. This takes 2 screenshots.

### 7. M2 note with the C4 diagram
- **Open:** `docs/m2-progress.md` on GitHub after pushing. GitHub renders Mermaid. The VS Code preview may show only the code.
- **Must show:** the progress paragraph and the rendered diagram, with `future` on the PostgreSQL and JPA boxes.

### 8. Git history
- **Run:** `git log --oneline --graph --decorate --all -n 16`
- **Must show:** `m5-behaviour` and `m6-styles` on `f83f3ea`, and `feature/styles-I-sbs` at the top with the commits for each block.

## GitHub evidence

Do these after the branch is pushed and the PR exists.

### 9. Pull request, checks, and approval
- **a. PR page:** the title `[W7] styles-I - layered mvc slice & in-memory repository`, the base `m6-styles`, the head `feature/styles-I-sbs`, and the description with the 4 reflections.
- **b. Checks tab:** Compile, MVC slice tests, Architecture tests, and Full verify, all with green checks.
- **c. Approval:** the reviewer's green "Approved" entry in the PR timeline. Only a second account can give this.
- **After approval:** change the ADR Status to `Accepted (approved by <reviewer> on <date>)`, then commit and push.

## Bonus evidence

### 10. Export pipeline and CSV sample
- **Run:** `./mvnw test -Dtest=OrderExportPipelineTest "-Dsurefire.reportFormat=plain" "-Dsurefire.useFile=false"`
- **Must show:** `shouldStreamTenThousandOrders`, `Tests run: 9, Failures: 0`, and `BUILD SUCCESS`.
- **Then run:** `cat target/export/order-export-sample.csv`
- **Must show:** the header and 3 rows. Row 3 shows a spreadsheet formula neutralized as `'=SUM(A1:A9)`.
