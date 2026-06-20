# Changelog — Reqs-AI API (Backend)

All notable changes to this backend are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versioning
follows [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

_Bounded-context implementation (iam, billing, workspace, discovery, gateway) in progress._

### Added (Quality Gates — `feature/tooling-quality-gates`)

- **Spotless** (`com.diffplug.spotless:8.6.0`): Java code formatter using Eclipse formatter.
  `./gradlew spotlessCheck` enforced in CI before `compileJava`; `./gradlew spotlessApply` for
  local auto-format. Kotlin Gradle DSL files formatted with `ktfmt`. See [ADR-0016](docs/adr/0016-spotless-eclipse-java-formatting.md).
- **JaCoCo 0.8.13**: Code coverage reports (XML + HTML). `./gradlew jacocoTestReport` generates
  `build/reports/jacoco/`; minimum 50% coverage enforced via `jacocoTestCoverageVerification`.
  Codecov upload added to CI (`codecov/codecov-action@v5`). See [ADR-0017](docs/adr/0017-jacoco-codecov-coverage.md).
- **OWASP Dependency-Check**: CVE scanning via `org.owasp.dependencycheck:12.2.2`. Runs in a
  separate weekly workflow (`owasp.yml`) to avoid blocking PRs; fails only on CVSS ≥ 9.
  `owasp-suppressions.xml` for known false positives. See [ADR-0018](docs/adr/0018-owasp-dependency-check.md).
- **ArchUnit 1.4.2**: Architecture fitness functions (`ArchitectureTests.java`) enforcing domain
  purity, hexagonal layer isolation, and bounded context boundaries. Tagged `@Tag("architecture")`.
  See [ADR-0019](docs/adr/0019-archunit-architecture-fitness-functions.md).
- **Lefthook** pre-commit hook: runs `./gradlew spotlessApply` on staged `*.java` files before
  every commit.

### Added

- **Discovery — Upload Transcript + Process Transcript use cases** (full STT → LLM pipeline):
  `POST /api/sessions/{id}/upload` (multipart `file`) transcribes the audio via the configured STT
  provider and transitions the session `DRAFT → STOPPED` with the transcript and audio duration stored.
  `POST /api/sessions/{id}/process` runs the LLM requirement-generation pipeline on the stored
  transcript, extracting and persisting user stories, and transitions `STOPPED → PROCESSING → COMPLETED`
  (or `FAILED` on error, with `processingError` persisted for retry). `GET /api/sessions/{id}/transcript`
  returns the raw transcript text.
  Application layer: `UploadTranscriptCommand` + handler (load session, call STT port, save),
  `StartDiscoveryProcessingCommand` + handler (load+validate, call generation port, extract stories via
  `StoryExtractionService`, complete or fail), `GetSessionTranscriptQuery` + handler.
  Domain: `DiscoverySession.uploadTranscript(transcript, audioDurationMs)` (`DRAFT → STOPPED`, sets
  `endedAt` + `audioDurationMs`); `startedAt` now set in constructor; `startProcessing()` /
  `complete()` / `fail(reason)` transitions already present; `processingError` cleared on retry via
  `startProcessing()`.
  STT infrastructure — `TranscriptionPort` + `TranscriptionResult` record (rich: `text`,
  `detectedLanguage`, `durationMs`, `confidence`, `segments` with speaker labels; optional fields are
  null when the provider does not support them). `SttRouter` selects the adapter at runtime via
  `STT_PROVIDER` env var. Three adapters: **`WhisperAdapter`** (Spring AI `OpenAiAudioTranscriptionModel`,
  OpenAI-compatible protocol — works with `faster-whisper-server` locally and OpenAI cloud unchanged;
  returns text + language + duration from response metadata, no diarization);
  **`AssemblyAiAdapter`** (pure `RestClient` three-step async flow: upload → submit → poll; speaker
  diarization with labels "A"/"B"; `BASE_URL = https://api.assemblyai.com/v2`; `detectLanguage`
  auto-detected); **`DeepgramAdapter`** (official Deepgram Java SDK, synchronous, speaker labels "0"/"1",
  `detectLanguage(true)` required for non-English audio). All adapters throw `InfrastructureException`
  on empty transcript instead of surfacing a domain error.
  Generation infrastructure — `RequirementGenerationPort` + `GenerationResult` (list of
  `GeneratedStory` with title, role, action, benefit, priority, storyPoints, acceptance criteria).
  `RequirementGenerationRouter` selects adapter via `GENERATION_PROVIDER`. Two adapters via
  `AbstractLlmGenerationAdapter` (shared prompt construction, JSON extraction, `stripMarkdown`):
  `GeminiRequirementGenerationAdapter` and `OpenAiRequirementGenerationAdapter`.
  `StoryExtractionService` iterates generated stories: constructs `UserStory`, calls
  `UserStoryDeduplicationService.embedAndGuardDuplicates()` (skips and publishes
  `UserStoryNearDuplicateDetectedEvent` on `DUPLICATE_USER_STORY`), skips silently on construction
  validation failure, persists on success.
  Shared: `FileUploadUtils.readBytes(MultipartFile)` (shared kernel, throws
  `ResponseStatusException(422)` on I/O failure); `UNPROCESSABLE_REQUEST` added to `CommonError`;
  `GlobalExceptionHandler` now handles `ResponseStatusException` with consistent `ProblemDetail` +
  correlation ID. Integration tests (`ProcessTranscriptIntegrationTest`): full upload → process flow
  with stub STT + stub generation, GET transcript, and 422 when session is not STOPPED.
- **ADR-0013 — Exception handling strategy**: documents the two-layer pattern — domain layer
  (`XxxError` enum + `XxxExceptions` factory → `DomainException`) and infrastructure layer
  (`XxxInfrastructureError` + `XxxInfrastructureExceptions` + provider subclasses →
  `InfrastructureException`). Defines the golden rule (adapters never throw `DomainException`),
  `GlobalExceptionHandler` routing table (`DomainException` → message exposed WARN; `InfrastructureException`
  → "A server error occurred" ERROR with stacktrace; `ResponseStatusException` → reason exposed WARN),
  and package layout per bounded context.

### Fixed

- **Timestamp timezone offset (`bugfix/timestamp-timezone`)**: audit timestamps (`created_at`,
  `updated_at`) were stored with a UTC-5 offset because the Dockerfile set `TZ=America/Lima`,
  causing Hibernate to use the JVM's local timezone when writing to PostgreSQL. Fixed by changing
  `ENV TZ=UTC` in the Dockerfile and adding `hibernate.jdbc.time_zone: UTC` under
  `spring.jpa.properties.hibernate.jdbc` in `application.yml`. All migrations already used
  `TIMESTAMPTZ` columns, so no schema change was required.

### Changed

- **AI provider configuration — `local-ai` profile eliminated**: the `application-local-ai.yml`
  overlay is deleted. AI providers are now activated exclusively via env vars (`SPRING_AI_MODEL_CHAT`,
  `SPRING_AI_MODEL_EMBEDDING`, `SPRING_AI_MODEL_AUDIO_TRANSCRIPTION`) and provider selectors
  (`GENERATION_PROVIDER`, `EMBEDDING_PROVIDER`, `STT_PROVIDER`) — no profile change needed.
  `AiPingController` and `SttPingController` moved from `@Profile("local-ai")` to `@Profile("dev")`
  with `ObjectProvider<ChatModel>` / `ObjectProvider<EmbeddingModel>` / `ObjectProvider<TranscriptionModel>`
  — respond `{"ok":false,"reason":"..."}` when the provider bean is absent, so they load in dev
  regardless of AI configuration. `OpenApiConfiguration.devToolsApi` simplified from
  `@Profile({"dev","local-ai"})` to `@Profile("dev")`. `.env.example` rewritten with commented
  per-provider sections (Gemini, OpenAI, Ollama, Whisper, AssemblyAI, Deepgram). `docs/PROFILES.md`
  and `docs/LOCAL_AI.md` updated to reflect env-var-only approach.

### Fixed

- **`AssemblyAiAdapter` — correct API base URL**: `BASE_URL` changed from
  `https://api.assemblyai.com` to `https://api.assemblyai.com/v2`; the unversioned path returns 404
  on all current AssemblyAI accounts.
- **`AssemblyAiAdapter` — isolated `RestClient`**: the adapter now receives `RestClient.create()`
  instead of the shared `RestClient.Builder`, preventing Spring AI's Whisper `baseUrl` from being
  inherited and corrupting absolute URLs.
- **`DeepgramAdapter` — language detection**: added `detectLanguage(true)` to the transcription
  request; without it Deepgram defaults to the English model and returns an empty transcript for
  non-English audio.
- **`DiscoverySession.startedAt`** was never populated — now set in the constructor so the field
  is non-null from creation.
- **`DiscoverySession.audioDurationMs`** was never written — `uploadTranscript` signature extended
  to `uploadTranscript(String transcript, long audioDurationMs)`; the STT handler now passes
  `result.durationMs()` from the provider.
- **`AbstractLlmGenerationAdapter`** — removed three dead null checks (`response == null`,
  `output == null`) that the IDE flagged as always-false; replaced with a single safe guard on
  `getResult()` which can legitimately be null.

---

- **Discovery — GET endpoints for Sessions and User Stories (three scopes)**:
  `ProjectSessionController` — `GET /api/projects/{projectId}/sessions/{sessionId}` (get by id, 404 on missing
  or project mismatch) and `GET /api/projects/{projectId}/sessions` (paginated list, sorted `createdAt DESC`,
  sortable by `title`/`status`/`createdAt`).
  `ProjectStoryController` — `GET /api/projects/{projectId}/stories/{storyId}` and
  `GET /api/projects/{projectId}/stories` (paginated backlog, sortable by `title`/`priority`/`status`/`createdAt`).
  `SessionStoryController` — `GET /api/sessions/{sessionId}/stories/{storyId}` (only AI-generated stories;
  manual stories with null `sessionId` return 404) and `GET /api/sessions/{sessionId}/stories` (paginated;
  unknown `sessionId` → 404 `SESSION_NOT_FOUND`, not an empty page).
  Application layer: `GetProjectSessionQuery`, `ListProjectSessionsQuery`, `GetProjectStoryQuery`,
  `ListProjectStoriesQuery`, `GetSessionStoryQuery`, `ListSessionStoriesQuery` + their handlers; port read
  methods (`findById`, `findAllByProjectId`, `findAllBySessionId`) added to both `DiscoverySessionRepository`
  and `UserStoryRepository`; Spring Data `Page`-backed adapters; `SESSION_NOT_FOUND` / `USER_STORY_NOT_FOUND`
  error codes (404 via `EntityNotFoundException`). `UserStoryResponse` now includes `embeddingIndexed: boolean`
  (true = embedding model was available at creation, story was dedup-checked and not a duplicate; false = model
  unavailable, dedup skipped, story not yet searchable by similarity). Integration and unit tests cover
  happy-path, 404, scope isolation, manual-story exclusion from session scope, and unauthenticated access.
- **ADR-0012 — REST route design and controller naming convention**: documents the `{scope}{Resource}Controller`
  convention (controller name reflects the URL parent context, not the bounded context); full table of all three
  discovery controllers with their routes and resources; rationale for two orthogonal scopes for `UserStory`;
  consequences including the divergence from the academic report's original session-only model.
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

- **Test infrastructure — `AbstractIntegrationTest` now provides `client()`**: `@LocalServerPort` and
  `protected RestClient client()` moved from every integration test class into `AbstractIntegrationTest`.
  Removes 3 fields and 6 methods across the 6 existing test classes; new tests inherit `client()` for free.
- **Test infrastructure — `StubEmbeddingConfig` extracted to shared `testsupport` package**: the
  deterministic embedding stub (same seed → same vector, dedup exercised without Ollama/Gemini) was
  duplicated as an inner `@TestConfiguration` in three test classes. Moved to
  `com.kntro.reqsai.testsupport.StubEmbeddingConfig`; each test now uses `@Import(StubEmbeddingConfig.class)`.
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
