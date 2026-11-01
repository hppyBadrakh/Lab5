# Peer Review: [W7] styles-I

Fill this in against a classmate's real pull request, then post it as a review comment on that PR. Base every score on something you checked yourself.

| Field | Value |
|---|---|
| Reviewer | |
| Author | |
| Pull request link | |
| Commit reviewed | |
| Review date | |

## How to review

1. Check out the branch and run `./mvnw clean verify`.
2. Read the package structure, the controller, the domain imports, and the ADR.
3. Score each area from 0 to 10, and cite a file and line for any deduction.

## 1. Layer isolation

Score: __/10

- [ ] `presentation`, `application`, `domain`, and `infrastructure` packages each contain real code
- [ ] The domain has no Spring, persistence, or Jackson imports
- [ ] `OrderRepository` is in `application`, and its adapter is in `infrastructure`
- [ ] The repository returns `Order`, never a row or a persistence type

Evidence:

Suggested change:

## 2. MVC slice and error mapping

Score: __/10

- [ ] The controller only maps DTOs and delegates, with no business formulas
- [ ] The API does not expose domain objects directly
- [ ] POST returns 201 with a `Location` header, and GET returns 200
- [ ] 400, 404, 409, and 500 return RFC 7807 `ProblemDetail`
- [ ] The 500 response leaks no stack trace or internal message
- [ ] MockMvc tests cover 200, 201, 400, 404, 409, and 500

Evidence:

Suggested change:

## 3. ADR-0005

Score: __/10

- [ ] Nygard form, with a title of 5 words or fewer
- [ ] The Context names concrete symptoms from the legacy code
- [ ] The Decision states a persistence and transaction policy
- [ ] The Consequences list at least 2 operational costs
- [ ] Rejected alternatives give real reasons

Evidence:

Suggested change:

## 4. Package cycles

Score: __/10

- [ ] No package cycles (how you checked: )
- [ ] An automated test enforces the rule, not only a convention
- [ ] Any extra package, such as `export`, also keeps to the dependency rule

Evidence:

Suggested change:

## 5. CI and build

Score: __/10

- [ ] `./mvnw clean verify` passes on my machine, with no compiler warnings
- [ ] The test count and the CI run on the PR look right
- [ ] CI runs the architecture tests and the MVC tests
- [ ] The PR description has at least 80 words and all 4 reflection sentences

Evidence:

Suggested change:

## Recommendation

Choose one: Approved / Approved with comments / Changes requested

Most important thing to fix:

What the author did well:
