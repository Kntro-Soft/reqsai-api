# 0010. Use-case (vertical slice) development workflow

- Status: Accepted
- Date: 2026-06-14
- Deciders: Kntro-Soft team
- Builds on: [0002](0002-modular-monolith-with-spring-modulith.md) (modular monolith), [0008](0008-testing-strategy-and-security-test-support.md) + [0009](0009-test-data-builders-and-parallel-execution.md) (testing)

## Context

We implement the product one **use case at a time** (e.g. *Create Organization*, *Create Discovery
Session*). We need a single, repeatable recipe — package layout, the order of work, and where tests and
migrations go — so any contributor (or a fresh AI session) builds a use case the same way, with the
domain protected and the tests at the right level. This ADR is the decision and the high-level order; the **hands-on, copy-paste companion with concrete
folders, snippets and the TDD red→green sequence is [`docs/USE_CASE_PLAYBOOK.md`](../USE_CASE_PLAYBOOK.md)**.

## Decision

### Architecture & naming

Each bounded context uses **DDD + Hexagonal (ports & adapters) + CQRS-lite**:

- **CQRS-lite** = we separate **Commands** (writes: `CreateXxxCommand` + handler) from **Queries**
  (reads: `GetXxxQuery`/`ListXxxQuery` + handler). It is *not* event-sourced CQRS — same model, no
  separate read store. Calling it "CQRS" is correct in this lightweight sense.
- **Hexagonal** = the application layer depends on **ports** (interfaces it owns); **adapters** in
  infrastructure/interfaces implement them. The domain depends on nothing outward.

### Package layout per use case (inside a BC)

```
<bc>/
├── domain/
│   ├── model/            aggregates, entities, enums (business rules live here)
│   ├── valueobjects/     value objects
│   ├── event/            domain events (records implementing DomainEvent)
│   └── exception/        <Bc>Error (ErrorCatalog) + <Bc>Exceptions factory
├── application/
│   ├── command/          CreateXxxCommand (write intent)         ┐ CQRS
│   ├── query/            GetXxxQuery / ListXxxQuery (read intent) ┘
│   ├── port/             repository/notifier PORT interfaces
│   └── handler/          one handler per use case (Create…, Get…)
├── infrastructure/
│   └── persistence/
│       ├── repositories/ Spring Data JPA repositories
│       ├── adapters/     RepositoryAdapter (implements the application port)
│       └── converters/   JPA AttributeConverters for value objects
└── interfaces/
    └── rest/
        ├── swagger/      documented INTERFACE (@Tag/@Operation/@ApiVersioning, see ADR 0010 notes)
        ├── controllers/  @RestController impl (implements the swagger interface)
        ├── dto/{request,response}/   flat DTOs
        └── mappers/{request,response}/  request→command, aggregate→response
```

### The order — inside-out, TDD (red → green) per layer

Build from the domain outward; the integration test is the outer bookend.

| # | Step                                                                                                                                                                                                                                                     | Test (write first, watch it fail)                                                                                                         |
|---|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| 0 | Read the use case in the report (entities, command/query, endpoint, rules).                                                                                                                                                                              | —                                                                                                                                         |
| 1 | **Domain.** Aggregate + value objects + enums + the behavior method(s) for this use case; one `<Bc>Error`/`<Bc>Exceptions`.                                                                                                                              | **Domain unit tests** (one class per aggregate/VO): invariants + state transitions, happy **and** unhappy. Mothers/Builders per ADR 0009. |
| 2 | **Application.** The `Command`/`Query`, the repository **port**, and the **handler** (orchestration only).                                                                                                                                               | **Handler unit test** with a **mocked port** — cover **every** path (happy + each unhappy).                                               |
| 3 | **Infrastructure.** Spring Data repo + adapter implementing the port; **per-entity migration** (one table = one script; `tenant/` for org-scoped data, `common/` for the global `public` registry).                                                      | (covered by the integration test)                                                                                                         |
| 4 | **Interface.** Request/Response DTOs (flat) + mappers + swagger interface (`@RequestMapping(ApiVersioning.BASE + …)`, `@PostMapping(version = V1)`, reuse `@ApiStandardErrorResponses`) + controller impl; `Location` via `ServletUriComponentsBuilder`. | —                                                                                                                                         |
| 5 | **Integration (acceptance).** `@SpringBootTest` + Testcontainers, driven through the entry point (HTTP → lives in `interfaces/rest/`; or the handler → `application/`).                                                                                  | **Happy path + 2–3 critical unhappy** (auth rejected, conflict, not-found). Random data for isolation.                                    |
| 6 | **Verify & record.** `./gradlew test` + `verifyModularity` all green; add an entry to `CHANGELOG.md` `[Unreleased]`.                                                                                                                                     | —                                                                                                                                         |

**Why inside-out (not controller-first):** the domain is the core and the most-tested; handlers and
controllers are thin. Writing the aggregate test first (red) drives the rules; the integration test at
the end proves the real wiring (DB mapping, provisioning, security, HTTP) that the mocked unit tests fake.

### Conventions to honour (from other ADRs)

- **Test levels & data:** exhaustive unit tests (mocked), selective integration tests — ADR 0008/0009.
  Object Mother returns a Builder; Datafaker random-valid defaults; `maxParallelForks`.
- **Multitenancy:** org-scoped tables go in the **tenant** schema (no schema qualifier on `@Table`,
  routed by the JWT `orgId`); the global `organizations` registry is in `public`. Migrations split:
  `db/migration/tenant/` vs `db/migration/common/`, **one per entity** (never grouped).
- **Value-object mapping:** single-value → `@Convert`; structured/queryable → `@Embeddable`;
  document-like → `@JdbcTypeCode(SqlTypes.JSON)`.
- **Controllers:** swagger interface + impl split; flat response DTOs; header-based API versioning.

## Consequences

- Any contributor/session follows the same steps → consistent, reviewable slices (cf. `workspace`
  create-organization, `discovery` create-session).
- TDD keeps the domain correct and fast to change; the integration test gives end-to-end confidence
  without re-asserting every branch.
- Slightly more ceremony per use case (ports, mappers, swagger interface) — accepted as the cost of a
  modular monolith that can split later.
