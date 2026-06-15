# Changelog — Reqs-AI API (Backend)

All notable changes to this backend are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versioning
follows [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

_Bounded-context implementation (iam, billing, workspace, discovery, gateway) in progress._

### Added

- **Discovery — Create Discovery Session use case** (first discovery vertical slice): `DiscoverySession`
  aggregate root with the full domain model — `projectId`, `title`, `language`, `status`, `transcript`,
  `startedAt`, `endedAt`, `audioDurationMs`, `lastSequence`, `processingError`; `SessionStatus` enum with
  all lifecycle states (`DRAFT → RECORDING → PAUSED → STOPPED → PROCESSING → COMPLETED / FAILED`) and
  inline comments mapping the streaming vs batch paths and the live-assistance loop. Transition methods
  (`startRecording`, `appendSegment`, `stopRecording`, `startProcessing`, `complete`, `fail`, `reset`)
  stashed — arrive with their own use-case slices. `CreateDiscoverySessionCommand` + handler validates
  input, persists in `DRAFT`, raises `DiscoverySessionCreatedEvent`. REST `POST /api/projects/{p}/sessions`
  with swagger interface + controller + request/response mappers. Tenant-scoped migration
  `V2__discovery_sessions.sql` (complete schema including nullable temporal and error fields).
  Unit tests (domain + handler) and integration test (full multitenant flow with Testcontainers).
- **Discovery — Create User Story (manual) use case**: `UserStory` aggregate (`sessionId` nullable for
  manually-created stories, `projectId`, `title`, `role`, `action`, `benefit`, `priority`,
  `storyPoints?`, `status = DRAFT`, 768-dim `embedding`) + `Priority` / `StoryStatus` enums;
  `CreateUserStoryCommand` + handler raising `UserStoryCreatedEvent`; repo port + adapter; REST
  `POST /api/projects/{projectId}/stories` (swagger interface + controller + request/response mappers);
  tenant migrations `V3__user_stories.sql` + `V4__user_stories_embedding.sql`. For teams that already
  have a backlog and want to upload stories directly — a product extension beyond the report's
  AI-generated stories. Acceptance criteria and the external tracker ref are deferred to their own use
  cases. 11 tests (domain + handler + multitenant E2E incl. duplicate rejection).
- **Discovery — embedding-based duplicate detection** (manual create now, AI-generation later): on
  create the story text is embedded via `EmbeddingPort` → Spring AI `EmbeddingModel` (Ollama
  `nomic-embed-text` locally / Gemini in prod, selected by `ai.model.embedding`; dedup is skipped, and
  the app still boots, when none is configured) and compared to the project's existing stories with
  pgvector cosine distance (`<=>`); a similarity ≥ `0.85` is rejected `409 DUPLICATE_USER_STORY`. Vectors
  map via `hibernate-vector` (`@JdbcTypeCode(VECTOR)` → `vector(768)`); the `vector` extension is enabled
  in `public`. Integration tests run on a `pgvector/pgvector:pg16` Testcontainer (`AbstractIntegrationTest`)
  with a deterministic embedding stub — no external AI needed.
- **ADR-0011 — API response field-selection strategy**: documents the decision to use one shared DTO
  per resource (not one per use case), the rules for which fields to include (`createdAt`/`updatedAt`
  yes; `createdBy`/`updatedBy` no; large fields in separate endpoints), the security rationale
  (OWASP API3:2023), and the future Summary/Detail split trigger. Rejected alternatives:
  `@JsonView`, sparse fieldsets (JSON:API / Google AIP-157), GraphQL.

### Changed

- **OpenAPI annotations — requests and responses enriched**: all request DTOs now carry `@Schema`
  per field with `description`, `example`, `minLength`/`maxLength`, and `requiredMode`
  (`REQUIRED` / `NOT_REQUIRED`) so Swagger UI shows constraints and examples instead of bare `string`.
  All response DTOs carry `@Schema` per field with `description`, `example`, `nullable`, and
  `allowableValues` for string-encoded enums (`status`). Swagger interfaces updated with `@Parameter`
  on every `@PathVariable` and `@ApiResponse(201)` now includes `@Content` + `@ExampleObject`
  showing the full JSON response body. Affected: `CreateDiscoverySessionRequest`,
  `CreateOrganizationRequest`, `DiscoverySessionResponse`, `OrganizationResponse`,
  `DiscoverySessionController` (swagger), `OrganizationController` (swagger).
- **`USE_CASE_PLAYBOOK.md` Step 4 updated**: documents the OpenAPI annotation conventions for
  request DTOs (`@Schema` fields, `requiredMode`), response DTOs (ADR-0011 rules + `allowableValues`,
  `nullable`), and swagger interfaces (`@Parameter`, `@Content`, `@ExampleObject`).
- **`OrganizationResponse`** now includes `createdAt` and `updatedAt` — aligns with ADR-0011 rule
  that all response DTOs expose these two audit timestamps consistently.

### Added

- **Workspace — Create Organization use case** (first business vertical slice, domain → controller):
  `Organization` aggregate in the global `public.organizations` registry with `Slug`, `GenerationSettings`
  and `PlanLimits` value objects (the last two mapped `@Embedded`; `Slug` via `@Convert`); `OrgStatus`
  adds `PENDING` for the provisioning window. `CreateOrganizationCommand` + handler validates slug
  uniqueness, persists `PENDING`, provisions the `tenant_<slug>` schema, then `activate()`s (→ `ACTIVE`,
  raising `OrganizationCreatedEvent`). REST `POST /api/organizations` split into a documented swagger
  interface + clean `@RestController` impl + dedicated request/response mappers; common migration
  `V2__organizations.sql`. This is what unblocks any tenant-scoped endpoint (creating an org provisions
  its schema). `LanguageCode` promoted to the **Shared Kernel** (shared by workspace `meetingLanguage`
  and discovery), with an `autoApply` `LanguageCodeConverter`.
- **Header-based API versioning** (Spring Framework 7 / Boot 4 native): `ApiVersioning` (base `/api`,
  `Api-Version` header, default `V1`) + `ApiVersioningConfig`; endpoints use `@PostMapping(version = V1)`.
- **Test data builders & parallel execution** ([ADR-0009](docs/adr/0009-test-data-builders-and-parallel-execution.md)):
  Datafaker-backed fluent **Builders** + **Object Mothers** (+ command mothers) in `mothers/` packages;
  Gradle `maxParallelForks` runs test classes in parallel across forked JVMs (each fork reuses its own
  Testcontainers DB + cached context), cutting the suite to ~20s.
- **Foundation regression tests**: `ApplicationContextTest` (full-context boot over a Testcontainers
  PostgreSQL — fails CI if any bean fails to wire) and `SmokeTest` (`RANDOM_PORT`, no port collisions;
  asserts health UP, readiness probe UP, anonymous requests rejected, `X-Request-ID` emitted, own
  OpenAPI served, secured actuator). These automate the previously-manual `bootRun` smoke checks so the
  boot bugs (servlet-filter auto-registration, pgvector demanding an `EmbeddingModel`, mail health) can
  only regress through a red build.

### Changed

- **API versioning moved from path to header**: routes are now `/api/<resource>` (e.g.
  `/api/organizations`) selected by the `Api-Version` header, instead of `/api/v1/<resource>` in the
  path; `SecurityConfiguration` public matchers updated (`/api/auth/**`). `Created` responses now build
  their `Location` via `ServletUriComponentsBuilder` instead of a hardcoded path.
- **Health groups**: `readiness` now includes only `readinessState` + `db`; `liveness` only
  `livenessState`. External/optional services (mail, Gemini) no longer gate readiness, so an
  unconfigured or down SMTP/AI provider cannot pull the app out of Cloud Run rotation.

### Fixed

- **`gradlew` executable bit** restored in the Git index (`100644` → `100755`) so fresh clones and CI
  runners can execute the wrapper without `chmod`.
- **Test datasource driver**: `application-test.yml` now sets
  `driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver`, overriding the production
  `org.postgresql.Driver` which rejected the Testcontainers `jdbc:tc:` URL.

---

## [0.1.0] - 2026-06-08

First infrastructure foundation — no business logic yet. Provides the cimientos every bounded
context builds on.

### Added

- **Build & tooling**: `build.gradle.kts` for Java 25 / Spring Boot 4 with Spring Modulith, Spring AI
  (Gemini + pgvector), Flyway, JJWT, Caffeine, UUID v7 (`uuid-creator`); `verifyModularity` Gradle
  task; JPA static-metamodel generation.
- **Shared Kernel**: `AuditableEntity` base (native `UUID` v7 PK, JPA auditing, identity-based
  `equals`/`hashCode`) for entities, and `AggregateRoot extends AuditableEntity` adding domain events
  for roots; soft-delete is opt-in per aggregate (not global). `Assert` (fluent validation),
  `IdGenerator`, `Email` value object, `DomainEvent`.
- **Multitenancy (schema-per-tenant)**: `TenantContext`, `CurrentTenantIdentifierResolverImpl`,
  `MultiTenantConnectionProviderImpl`, `TenantSchemaResolver` (Caffeine-cached), `ProvisioningService`,
  `TenantMigrationRunner`.
- **Security**: stateless Spring Security with RS256 JWT, split into a `TokenVerifier` port +
  `JjwtTokenVerifier` adapter (public key only, in `shared`) consumed by `JwtAuthenticationFilter`;
  token issuance (private key) is left to the `iam` context. `JwtProperties`, `SecurityConfiguration`,
  `SecurityBeans`; dev key generation script.
- **Error handling**: `ErrorCatalog` interface (per-context error codes; HTTP status per code) with
  shared `CommonError` for cross-cutting codes, minimal exception hierarchy + cross-cutting
  `Exceptions` factory, `GlobalExceptionHandler` producing RFC 9457 `ProblemDetail` (extends
  `ResponseEntityExceptionHandler`), and `CorrelationFilter` (MDC `correlationId` + `tenantId`,
  `X-Request-ID`).
- **Cross-cutting infrastructure**: JPA auditing (`AuditorAware<UUID>`), externalized CORS, and
  pagination (`PageResponse`, `PaginationProperties` with named constants, `PageRequestFactory` that
  clamps page size). OpenAPI/Swagger (`OpenApiConfiguration`, Bearer scheme, `@ConditionalOnProperty`)
  with reusable composite response annotations (`@ApiStandardErrorResponses`, `@ApiResponse*`) that
  document the RFC 9457 ProblemDetail contract.
- **Shared infrastructure layout**: organized into `cache/`, `documentation/openapi/{,annotations}`,
  `persistence/{auditing,multitenancy}`, `security/`, `web/{,error}`, and `interfaces/pagination/`,
  each with a `package-info.java`.
- **Query & pagination**: `PageResponse` (nested page metadata), `PageCriteria`, `PageRequestFactory`
  (size clamping), `SortPolicy` (whitelisted sort + `id` tie-breaker for stable paging), and
  `Specifications` (functional, null-safe JPA Specification composition — no per-entity subclass).
- **Real-time**: STOMP-over-WebSocket infra — `WebSocketConfig` + `WebSocketProperties` with a
  **switchable broker** (in-memory `SIMPLE` with heartbeats, or `RELAY` to an external broker for
  multi-instance — config only), `StompAuthChannelInterceptor` (authenticates CONNECT via the shared
  `TokenVerifier`), and the `RealtimeNotifier` port + `StompRealtimeNotifier` adapter. See ADR-0007.
- **Modules**: `shared` (OPEN) plus the `iam`, `billing`, `workspace`, `discovery`, `gateway`
  bounded-context skeletons, with boundaries verified by `ModularityTests`.
- **Configuration**: `application.yml` + `dev`/`prod`/`test` profiles under a single `reqsai.*`
  namespace; Java 25 virtual threads (`spring.threads.virtual.enabled`); no real secrets in VCS
  (prod `DB_PASSWORD` has no default); native structured logging (`logging.structured.format=ecs` in
  prod); actuator limited to `health,info,metrics,modulith` (only health public, mail health indicator
  disabled until SMTP is configured); pgvector vector-store auto-config excluded until the `discovery`
  context wires embeddings; `compose.yaml` (pgvector); Flyway migrations (`common`: event_publication,
  organizations; `tenant`: baseline). Local `compose.yaml` uses a `core` profile (PostgreSQL/pgvector
  + Mailpit for dev email) with healthchecks; Spring Boot starts it via `docker.compose.profiles.active`.
- **Verified**: boots against PostgreSQL/pgvector (Flyway applies the common migrations, security
  chain orders CorrelationFilter → JwtAuthenticationFilter, `/actuator/health` UP, protected routes
  return 401/403, Swagger/OpenAPI served). Security filters are wired into the chain (not registered
  as standalone servlet filters).
- **CI/CD & docs**: GitHub Actions (`ci`, `codeql`, `deploy` → AWS ECR/ECS Fargate), multi-stage
  `Dockerfile` (layered jar extraction for cache-friendly redeploys, non-root, `JarLauncher` exec
  entrypoint, container-aware JVM flags, `TZ=America/Lima`), Dependabot, repository governance
  (`CONTRIBUTING`, `CODEOWNERS`, `SECURITY`, templates), ADRs (`docs/adr/`) and contributor docs (the
  architecture overview lives in the README; the rationale in the ADRs).
- **AI autoconfig**: Spring AI (Gemini chat/embeddings + pgvector) auto-configurations are excluded
  so the app boots in every profile without AI credentials; the `discovery` context removes the
  exclusions and provides the Gemini key when it implements the AI pipeline.

**Author:** Gutiérrez Soto, Jhosepmyr Orlando

---

## [0.0.0] - 2026-06-06

### Added

- Initial Spring Boot project scaffold (Spring Initializr).

**Author:** Gutiérrez Soto, Jhosepmyr Orlando
