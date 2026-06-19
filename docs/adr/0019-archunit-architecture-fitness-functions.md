# 0019. Architecture fitness functions with ArchUnit

- Status: Accepted
- Date: 2026-06-18
- Deciders: Kntro-Soft team

## Context

Spring Modulith already enforces module boundaries at the package level (`ModularityTests`,
ADR-0002). However, it does not enforce internal layer rules within a bounded context — for example,
it cannot prevent a domain class from importing a Spring annotation or a REST controller from
reaching into the infrastructure layer directly. As the codebase grows across 5 bounded contexts
maintained by 5 developers, these internal violations are easy to introduce accidentally.

## Decision

Add **ArchUnit 1.4.2** (`archunit-junit5`) with a set of architecture fitness functions in
`src/test/java/com/kntro/reqsai/architecture/ArchitectureTests.java`.

Current rules (all tagged `@Tag("architecture")`):

| Rule                                                   | What it enforces                                                                                                                                                                                            |
|--------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `domain_must_not_use_spring`                           | Domain classes must not import `org.springframework.*` — with exemptions for `shared.domain.model` (uses Spring Data auditing by design) and `*.domain.exception` (uses `HttpStatus` for typed error codes) |
| `domain_must_not_use_jpa`                              | Domain classes must not import `jakarta.persistence.*` — with exemptions for domain model and value-object packages (Active Record pattern used intentionally per the architecture report)                  |
| `interfaces_must_not_access_infrastructure`            | REST controllers must not directly import infrastructure classes — with exemption for `interfaces.rest.swagger` packages (OpenAPI annotations are compile-time metadata)                                    |
| `bounded_contexts_must_not_directly_import_each_other` | `iam` must not import from `billing`, `workspace`, or `discovery` — enforces event-driven cross-context communication                                                                                       |

The exemptions are intentional and documented with `because(...)` clauses explaining the design choice.
Spring Modulith's `ModularityTests` remains the primary boundary enforcer; ArchUnit adds
intra-context layer rules that Modulith does not cover.

## Consequences

- Layer violations within a bounded context are caught at build time, not in code review.
- The rules are calibrated to the actual architecture (Active Record + typed HTTP status codes in
  domain exceptions) — not to an idealized hexagonal architecture the team did not choose.
- New rules should be added when a recurring violation pattern is found in code review.
- Rules that are too strict must be refined with exemptions and `because(...)` documentation —
  never deleted silently.
- ArchUnit scans compiled classes, not source; it runs as part of `./gradlew test` and adds
  negligible overhead (~1–2s).
