# AGENTS.md

## Purpose
- ReqsAI is a B2B SaaS backend for AI-assisted requirements elicitation. The core business flow is: create an organization, provision an isolated tenant schema, create projects, capture discovery sessions, transcribe/process them, and generate/export user stories.
- Read [docs/PROJECT_CONTEXT.md](docs/PROJECT_CONTEXT.md) for business goals and personas, and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) plus `assets/diagrams/**` for the full domain and C4 view.

## Big Picture
- This is a modular monolith on Java 25 + Spring Boot 4 + Spring Modulith + PostgreSQL/pgvector. Bounded contexts live under `com.kntro.reqsai`: `workspace`, `discovery`, `iam`, `billing`, `gateway`, plus open `shared`.
- `shared` is the only module other contexts may depend on directly; module boundaries are enforced by [src/test/java/com/kntro/reqsai/ModularityTests.java](src/test/java/com/kntro/reqsai/ModularityTests.java) and [src/test/java/com/kntro/reqsai/architecture/ArchitectureTests.java](src/test/java/com/kntro/reqsai/architecture/ArchitectureTests.java).
- Each context follows a vertical-slice hexagonal layout: `domain`, `application`, `infrastructure`, `interfaces`. Use [docs/USE_CASE_PLAYBOOK.md](docs/USE_CASE_PLAYBOOK.md) as the canonical “how to add a use case” guide.

## Multi-Tenancy And Data Model
- The most important architectural rule is schema-per-tenant multitenancy. `public.organizations` is global; tenant data lives in `tenant_<slug>` schemas.
- Tenant binding happens per request from JWT `orgId` in [JwtAuthenticationFilter.java](src/main/java/com/kntro/reqsai/shared/infrastructure/security/JwtAuthenticationFilter.java), stored in [TenantContext.java](src/main/java/com/kntro/reqsai/shared/infrastructure/persistence/multitenancy/TenantContext.java), and resolved to a schema by shared multitenancy infrastructure.
- New tenant tables go in `src/main/resources/db/migration/tenant/`. Global tables go in `db/migration/common/`. Cross-context references are plain UUIDs; do not add cross-context foreign keys.
- Organization creation is special: [CreateOrganizationCommandHandler.java](src/main/java/com/kntro/reqsai/workspace/application/handler/CreateOrganizationCommandHandler.java) deliberately avoids `@Transactional`, persists the org as `PENDING`, provisions the schema with Flyway, then activates it.

## API, Security, And Realtime
- The real API versioning strategy is header-based, not `/api/v1` paths. Routes use `/api/...` and handlers declare `version = ApiVersioning.V1`; clients send `Api-Version: 1`. Trust [ApiVersioning.java](src/main/java/com/kntro/reqsai/shared/infrastructure/configuration/ApiVersioning.java) and [ApiVersioningConfig.java](src/main/java/com/kntro/reqsai/shared/infrastructure/configuration/ApiVersioningConfig.java) over older docs.
- JWT verification is cross-cutting in `shared`; token issuance belongs in `iam`. Protected HTTP tests should either use `@WithMockReqsaiUser` or send a real RS256 token from `TestJwtFactory`.
- Error responses are RFC 9457 `ProblemDetail` with `code` and `correlationId`; see [GlobalExceptionHandler.java](src/main/java/com/kntro/reqsai/shared/infrastructure/web/error/GlobalExceptionHandler.java). Reuse per-context `*Error` enums instead of adding business codes to `shared`.
- Realtime uses STOMP over WebSocket at `/ws`. Business code should depend on the `RealtimeNotifier` port, not on messaging APIs directly; see [docs/REALTIME.md](docs/REALTIME.md).

## Coding Patterns That Matter Here
- Aggregates extend `AggregateRoot`; non-root entities extend `AuditableEntity`. IDs are UUID v7 generated in constructors, never `@GeneratedValue`.
- Domain objects keep invariants inside constructors/methods with `Assert`. Example: [DiscoverySession.java](src/main/java/com/kntro/reqsai/discovery/domain/model/DiscoverySession.java) encodes the full session state machine and raises domain events from state transitions.
- Application handlers orchestrate but should stay thin. Example: [StartDiscoveryProcessingCommandHandler.java](src/main/java/com/kntro/reqsai/discovery/application/handler/StartDiscoveryProcessingCommandHandler.java) drives the session lifecycle and delegates per-story persistence.
- Repository ports live in the application layer; adapters live under `infrastructure/persistence/adapters`; Spring Data interfaces stay internal under `repositories/`. Do not inject `JpaRepository` into handlers/controllers.
- Request/response DTOs are flat records. Swagger/OpenAPI annotations live on `interfaces/rest/swagger/*`; controller implementations stay in `interfaces/rest/controllers/*`.
- Static mapper classes under `interfaces/rest/mappers/**` are the norm; avoid adding annotation-mapper frameworks unless the repo adopts one.
- Incremental story processing relies on nested transactions: [StoryExtractionService.java](src/main/java/com/kntro/reqsai/discovery/application/service/StoryExtractionService.java) uses `REQUIRES_NEW` so per-story events can stream before the outer transaction ends.

## Testing And Developer Workflow
- Primary commands: `./gradlew bootRun`, `./gradlew build`, `./gradlew test`, `./gradlew verifyModularity`, `./gradlew spotlessApply`, `./gradlew jacocoTestReport`.
- Formatting is enforced with Spotless and a pre-commit hook from [lefthook.yml](lefthook.yml). If you change Java/Gradle files, expect formatting to be part of the workflow.
- Integration tests use Testcontainers PostgreSQL, not H2, because multitenancy and pgvector behavior matter. The base class is [AbstractIntegrationTest.java](src/test/java/com/kntro/reqsai/testsupport/AbstractIntegrationTest.java).
- A representative end-to-end multitenant test is [CreateDiscoverySessionIntegrationTest.java](src/test/java/com/kntro/reqsai/discovery/interfaces/rest/CreateDiscoverySessionIntegrationTest.java): create org, mint JWT with `orgId`, call the endpoint, then assert the row landed in the tenant schema.
- For new features, mirror the existing pyramid: domain tests first, handler tests with mocked ports, integration tests for the full HTTP + tenant-routing path, and architecture/modularity checks green at the end.

## AI And External Integrations
- AI is isolated inside `discovery` behind three capabilities: generation, embeddings, and STT. Providers are swappable by config, not profiles; see [docs/LOCAL_AI.md](docs/LOCAL_AI.md).
- `application.yml` defaults AI models to `none`, so do not assume AI beans are active in every environment. Production defaults to Gemini for chat/embeddings; local STT can run through the `ai` Docker Compose profile.
- Local infrastructure lives in [compose.yaml](compose.yaml): `core` starts Postgres + Mailpit, `ai` adds Whisper, `app` runs the API containerized. Normal local dev is still `./gradlew bootRun`.

## Working Agreement For Agents
- Before adding a new endpoint or use case, inspect the nearest existing slice in the same bounded context and copy its folder placement, DTO shape, mapper style, and test shape.
- Prefer updating documentation when you find drift. This repo already contains at least some stale guidance around API versioning, so verify behavior in code before assuming a doc is current.
- Keep changes bounded to one context when possible. If a feature crosses contexts, communicate via shared contracts, domain events, or UUID references instead of direct internal-package coupling.
- When in doubt, the highest-signal sources are: `docs/adr/**`, `docs/USE_CASE_PLAYBOOK.md`, `src/test/java/com/kntro/reqsai/ModularityTests.java`, and the closest shipped vertical slice.
