# 0017. Code coverage with JaCoCo + Codecov

- Status: Accepted
- Date: 2026-06-18
- Deciders: Kntro-Soft team

## Context

There is no code coverage measurement. The team cannot tell which parts of the domain are tested,
which are not, or whether a PR reduces coverage. For a project using DDD and hexagonal architecture,
untested domain rules are the highest-risk blind spot.

## Decision

Use **JaCoCo 0.8.15** (bundled with Gradle — no extra plugin needed) for coverage measurement 
and **Codecov** for report hosting and PR annotations.

Configuration:
- `jacocoTestReport` generates XML (for Codecov) and HTML (for local inspection).
- Excludes: `BackendReqsaiApplication.class` (no testable logic) and `Q*.class` (JPA static metamodel).
- `jacocoTestCoverageVerification` enforces a minimum of **50% line coverage** — intentionally low
  for the MVP phase. The threshold will be raised by 10% per sprint as feature modules are completed.
- `tasks.named("check") { dependsOn("jacocoTestReport") }` — coverage runs with every `./gradlew build`.
- CI uploads `build/reports/jacoco/test/jacocoTestReport.xml` to Codecov via `codecov-action@v5`.
  `fail_ci_if_error: false` — a Codecov outage must not block deployments.

GitHub secret required: `CODECOV_TOKEN` (obtained from codecov.io after connecting the repository).

## Consequences

- Coverage is visible on every PR via Codecov annotations and a README badge (to be added).
- The 50% threshold prevents catastrophic regressions without blocking early development.
- JaCoCo measures line and branch coverage; it cannot measure mutation coverage (not needed for MVP).
- Testcontainers must be running (Docker) for integration tests to contribute to coverage — pure
  unit tests alone will likely not reach 50% for database-heavy bounded contexts.
