# Changelog — Reqs-AI API (Backend)

All notable changes to this backend are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versioning
follows [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

_Bounded-context implementation (iam, billing, workspace, discovery, gateway) in progress._

### Added

- **Foundation regression tests**: `ApplicationContextTest` (full-context boot over a Testcontainers
  PostgreSQL — fails CI if any bean fails to wire) and `SmokeTest` (`RANDOM_PORT`, no port collisions;
  asserts health UP, readiness probe UP, anonymous requests rejected, `X-Request-ID` emitted, own
  OpenAPI served, secured actuator). These automate the previously-manual `bootRun` smoke checks so the
  boot bugs (servlet-filter auto-registration, pgvector demanding an `EmbeddingModel`, mail health) can
  only regress through a red build.

### Changed

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
- **Security**: stateless Spring Security with RS256 JWT (`JwtProperties`, `JwtTokenService`,
  `JwtAuthenticationFilter`, `SecurityConfiguration`, `SecurityBeans`); dev key generation script.
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
- **Modules**: `shared` (OPEN) plus the `iam`, `billing`, `workspace`, `discovery`, `gateway`
  bounded-context skeletons, with boundaries verified by `ModularityTests`.
- **Configuration**: `application.yml` + `dev`/`prod`/`test` profiles under a single `reqsai.*`
  namespace; Java 25 virtual threads (`spring.threads.virtual.enabled`); no real secrets in VCS
  (prod `DB_PASSWORD` has no default); native structured logging (`logging.structured.format=ecs` in
  prod); actuator limited to `health,info,metrics,modulith` (only health public, mail health indicator
  disabled until SMTP is configured); pgvector vector-store auto-config excluded until the `discovery`
  context wires embeddings; `compose.yaml` (pgvector); Flyway migrations (`common`: event_publication,
  organizations; `tenant`: baseline).
- **Verified**: boots against PostgreSQL/pgvector (Flyway applies the common migrations, security
  chain orders CorrelationFilter → JwtAuthenticationFilter, `/actuator/health` UP, protected routes
  return 401/403, Swagger/OpenAPI served). Security filters are wired into the chain (not registered
  as standalone servlet filters).
- **CI/CD & docs**: GitHub Actions (`ci`, `codeql`, `deploy` → AWS ECR/ECS Fargate), multi-stage
  `Dockerfile`, Dependabot, repository governance (`CONTRIBUTING`, `CODEOWNERS`, `SECURITY`,
  templates), architecture reference (`docs/ARCHITECTURE.md`), ADRs (`docs/adr/`) and contributor docs.

**Author:** Gutiérrez Soto, Jhosepmyr Orlando

---

## [0.0.0] - 2026-06-06

### Added

- Initial Spring Boot project scaffold (Spring Initializr).

**Author:** Gutiérrez Soto, Jhosepmyr Orlando
