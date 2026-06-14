# 0009. Test data builders and parallel test execution

- Status: Accepted
- Date: 2026-06-13
- Deciders: Kntro-Soft team
- Complements: [0008](0008-testing-strategy-and-security-test-support.md)

## Context

ADR 0008 set the test pyramid and the security helpers. As bounded contexts multiply we need to
standardize three things that 0008 left open:

1. **What exactly is tested at each level** (so we neither under-test logic nor duplicate it).
2. **How test data is built** so it scales without the classic Object-Mother method explosion.
3. **How the suite stays fast** as integration tests grow — Testcontainers was painfully slow before
   (~5 min, mostly spent starting a database per test), and we picked Gradle partly for parallelism.

## Decision

### 1. What to test, and where

| Level                | Runtime                                       | Coverage policy                                                                                                                                                                                                                                                      |
|----------------------|-----------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Unit**             | no Spring, no DB; collaborators **mocked**    | **Exhaustive.** Every domain invariant (value objects, aggregates, entities) and **every application handler path — happy AND unhappy**. They are cheap and fast, so all branches are covered here. One command/query handler per use case → one focused test class. |
| **Slice** (optional) | `@WebMvcTest` / `@DataJpaTest`                | Web- or persistence-only concerns when a full context is overkill.                                                                                                                                                                                                   |
| **Integration**      | `@SpringBootTest` + Testcontainers PostgreSQL | **Selective.** The happy path end-to-end **plus 2–3 critical unhappy paths** (auth rejected, conflict/`409`, not-found). Do **not** re-assert every validation branch — that's already unit-covered. These are the expensive tests; keep them few and high-value.    |
| **Architecture**     | `ModularityTests` (`@Tag("modularity")`)      | Every build.                                                                                                                                                                                                                                                         |

This matches the test pyramid (Fowler): a wide base of fast unit tests, a narrow band of integration
tests. The reason handlers are split one-per-use-case is precisely so each gets a focused, exhaustive
unit test with mocks.

**What an integration test is (and is not).** It verifies that *real* collaborators work together across
a boundary (real Spring wiring + real DB via Testcontainers) — exactly the things unit tests fake: the
real DB mapping/round-trip (e.g. `@Embeddable`/converter/`jsonb`), real tenant-schema provisioning, the
real security chain, and the HTTP contract (status, `Location`, serialization). It is **not** a vague
"does everything work"; it asserts the specific cross-boundary behavior that mocks cannot.

**Placement.** A test mirrors the package of its **entry point**, and the `*IntegrationTest` name +
`@Tag("integration")` is what marks it (not the folder):
- **API / end-to-end** integration test (`@SpringBootTest(RANDOM_PORT)`, driven over HTTP through the
  controller) → lives next to the controller, under `interfaces/rest/`.
- **Application-level** integration test (`@SpringBootTest`, drives the handler directly with a real
  repo/DB, no HTTP) → lives under `application/`.

Unit tests already mirror their target's package (`domain/model/`, `domain/valueobjects/`,
`application/handler/`); test data builders live in `mothers/`.

### 2. Test data — Object Mother **and** Test Data Builder (hybrid)

We use **both**, because they solve different problems and compose well (Object Mother's weakness is
that it doesn't cope with data variation — a Builder fixes that):

- **`XxxBuilder`** (Test Data Builder) — fluent, with **Datafaker** random-but-valid defaults for every
  field, `.withX()` mutators and `.build()`. Absorbs variation; a test sets only the fields it asserts.
- **`XxxMother`** (Object Mother) — named business scenarios (`pending()`, `active()`) that **return a
  Builder**, so a test can read intent *and* still customize before building.
- **`CreateXxxCommandMother`** — `valid()` / `minimal()` / `withX()` plus a family of **invalid-input
  factories** (`withBlankName()`, `withInvalidSlug()`, …) feeding the exhaustive unit cases.

Convention: one `mothers/` package per bounded-context test tree; Mother delegates to Builder; data is
random by default (Datafaker / `UUID.randomUUID()`); one test class per value object. Modeled on
`risk-screening-api`.

### 3. Speed and parallelism

- **Container reuse.** The `jdbc:tc:postgresql` URL (see 0008) starts **one** database container per test
  **JVM** and reuses it for every class in that JVM; Spring caches the application context across
  `@SpringBootTest` classes with identical config. So a container + context start **once per fork**, not
  per test class — this is what removed the multi-minute startup tax. (Local dev can persist containers
  across builds with `.withReuse(true)` + `~/.testcontainers.properties → testcontainers.reuse.enable=true`;
  not enabled in CI.)
- **Parallel execution.** Gradle `maxParallelForks = availableProcessors / 2` runs test **classes** in
  parallel across forked JVMs. Each fork owns its own container → no shared DB → no cross-fork collision.
  `forkEvery` stays `0` so each fork reuses its JVM/container/context. (Gradle parallelizes classes, not
  methods.)
- **Isolation over fixed keys.** Tests never depend on a fixed unique value; they generate random keys
  (e.g. a random `slug` per run) so even classes in the same fork don't collide, and the integration
  test is repeatable. DB cleanup between tests only where a test actually needs a clean slate.
- We do **not** enable JUnit intra-JVM method parallelism for integration tests (shared DB/context would
  race); process-level forks are the safe, code-change-free lever.

## Consequences

- Fast feedback: all logic/branches live in cheap unit tests; integration tests stay few but meaningful.
- Test data scales without a Mother-method explosion — the Builder carries variation, the Mother carries
  intent.
- Wall-clock drops via parallel forks + per-fork container/context reuse; randomized data keeps parallel
  runs collision-free.
- Trade-off: `maxParallelForks = N` can start up to N database containers at once (RAM); tune the divisor
  for constrained CI runners.
