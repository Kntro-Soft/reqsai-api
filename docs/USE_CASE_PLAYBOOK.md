# Use-case playbook (hands-on)

The step-by-step companion to **[ADR 0010](adr/0010-use-case-vertical-slice-workflow.md)** (the *why*
and the high-level order). This is the *how*: exact folders, file-by-file, with real snippets from the
shipped **Create Discovery Session** slice. Copy the shapes, swap the names.

> Conventions referenced here: testing levels & data builders ([ADR 0008](adr/0008-testing-strategy-and-security-test-support.md)
> / [0009](adr/0009-test-data-builders-and-parallel-execution.md)), value-object mapping & migrations
> ([0003](adr/0003-schema-per-tenant-multitenancy.md)).

---

## Where everything goes (per bounded context)

Example: the `discovery` BC for the "create session" use case.

```
src/main/java/com/kntro/reqsai/discovery/
├── domain/
│   ├── model/DiscoverySession.java            (1) aggregate + behavior
│   ├── model/SessionStatus.java                    enum
│   └── exception/DiscoveryError.java, DiscoveryExceptions.java
├── application/
│   ├── command/CreateDiscoverySessionCommand.java  (2) write intent
│   ├── port/DiscoverySessionRepository.java        (2) port
│   └── handler/CreateDiscoverySessionCommandHandler.java (2) orchestration
├── infrastructure/persistence/
│   ├── repositories/DiscoverySessionJpaRepository.java        (3) Spring Data
│   ├── adapters/DiscoverySessionRepositoryAdapter.java         (3) implements port
│   └── converters/…Converter.java                              (3) VO ↔ column (if the BC has its own)
└── interfaces/rest/
    ├── swagger/DiscoverySessionController.java       (4) documented interface
    ├── controllers/DiscoverySessionControllerImpl.java (4) impl
    ├── dto/request/CreateDiscoverySessionRequest.java  (4)
    ├── dto/response/DiscoverySessionResponse.java       (4)
    └── mappers/{request,response}/...Mapper.java        (4)

src/main/resources/db/migration/tenant/V2__discovery_sessions.sql   (3) per-entity migration

src/test/java/com/kntro/reqsai/discovery/
├── domain/model/DiscoverySessionTest.java            (1) domain unit tests
├── application/handler/CreateDiscoverySessionCommandHandlerTest.java (2) handler unit test
├── interfaces/rest/CreateDiscoverySessionIntegrationTest.java        (5) E2E
└── mothers/{DiscoverySessionBuilder,DiscoverySessionMother,CreateDiscoverySessionCommandMother}.java
```

---

## Step 0 — Read the use case

From the report (§5.4): command `CreateDiscoverySessionCommand(projectId, title, language, requestedBy)`,
endpoint `POST /api/projects/{projectId}/sessions`, rule "session starts in `DRAFT`". Note which data is
tenant-scoped (sessions → tenant schema) vs global.

## Step 1 — Domain (TDD: test first → red → green)

**1a. Write the failing domain test** (`domain/model/DiscoverySessionTest.java`) using a Mother/Builder:

```java
@Test
@DisplayName("should create the session in DRAFT")
void should_create_in_draft() {
    DiscoverySession session = DiscoverySessionMother.draft().build();   // red: class doesn't exist yet
    assertThat(session.getStatus()).isEqualTo(SessionStatus.DRAFT);
}
```

**1b. Implement the aggregate** (`domain/model/DiscoverySession.java`) until green — constructor sets
`DRAFT`, behavior methods guard invariants, `registerEvent(...)` for domain events. Value objects:
single-value → `@Convert`; structured → `@Embeddable`. Add the `<Bc>Error` enum + `<Bc>Exceptions`
factory for this BC's error codes.

> Cover **all** branches here (happy + every unhappy/state-transition) — these tests are cheap.

## Step 2 — Application (command + port + handler)

**2a. Command** (`application/command/CreateDiscoverySessionCommand.java`) — a record:

```java
public record CreateDiscoverySessionCommand(UUID projectId, String title, String language, UUID requestedBy) {}
```

**2b. Port** (`application/port/DiscoverySessionRepository.java`) — an interface the app owns:

```java
public interface DiscoverySessionRepository {
    DiscoverySession save(DiscoverySession session);
    Optional<DiscoverySession> findById(UUID id);
}
```

**2c. Handler test first** (`application/handler/...HandlerTest.java`) with a **mocked port**, covering
every path:

```java
@ExtendWith(MockitoExtension.class)
class CreateDiscoverySessionCommandHandlerTest {
    @Mock DiscoverySessionRepository sessions;
    @InjectMocks CreateDiscoverySessionCommandHandler handler;

    @Test void should_create_in_draft_and_persist() {
        when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));
        DiscoverySession s = handler.handle(CreateDiscoverySessionCommandMother.valid());
        assertThat(s.getStatus()).isEqualTo(SessionStatus.DRAFT);
        verify(sessions).save(any());
    }
    @Test void should_reject_invalid_language() { /* unhappy → verify(sessions, never()).save(any()) */ }
}
```

**2d. Handler** (`application/handler/CreateDiscoverySessionCommandHandler.java`) — orchestration only,
`@Component` (+`@Transactional` for writes):

```java
@Component @RequiredArgsConstructor
public class CreateDiscoverySessionCommandHandler {
    private final DiscoverySessionRepository sessions;
    @Transactional
    public DiscoverySession handle(CreateDiscoverySessionCommand c) {
        var session = new DiscoverySession(c.projectId(), c.title(), LanguageCode.of(c.language()));
        return sessions.save(session);
    }
}
```

## Step 3 — Infrastructure (adapter + migration)

**3a. Spring Data repo + adapter** — note the subfolders: the Spring Data interface goes in
`infrastructure/persistence/repositories/`, the port adapter in `infrastructure/persistence/adapters/`,
and any JPA converter in `infrastructure/persistence/converters/`:

```java
public interface DiscoverySessionJpaRepository extends JpaRepository<DiscoverySession, UUID> {}

@Repository @RequiredArgsConstructor
public class DiscoverySessionRepositoryAdapter implements DiscoverySessionRepository {
    private final DiscoverySessionJpaRepository jpa;
    public DiscoverySession save(DiscoverySession s) { return jpa.save(s); }
    public Optional<DiscoverySession> findById(UUID id) { return jpa.findById(id); }
}
```

**3b. Migration — one table per file.** Tenant-scoped → `db/migration/tenant/`; global registry →
`db/migration/common/`. Cross-context ids (`project_id`) are plain `uuid`, **no FK**.

```sql
-- db/migration/tenant/V2__discovery_sessions.sql
CREATE TABLE discovery_sessions (
    id UUID PRIMARY KEY, project_id UUID NOT NULL, title VARCHAR(200) NOT NULL,
    language VARCHAR(8) NOT NULL, status VARCHAR(16) NOT NULL, /* …audit columns… */
);
CREATE INDEX idx_sessions_project ON discovery_sessions (project_id);
```

## Step 4 — Interface (DTOs → mappers → swagger interface → impl)

**4a. Request DTO** (`dto/request/`) — flat record, bean validation + `@Schema` per field so Swagger
shows descriptions, examples, and constraints instead of bare `string`:

```java
@Schema(description = "Request body to create a requirements-elicitation session")
public record CreateDiscoverySessionRequest(

        @Schema(description = "Descriptive title for the session",
                example = "Sprint 24 — Requirements Elicitation",
                minLength = 1, maxLength = 200,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 200)
        String title,

        @Schema(description = "BCP-47 language tag for the meeting — used by STT and AI generation",
                example = "es-PE", minLength = 2, maxLength = 8,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 8)
        String language
) {}
```

Optional fields use `requiredMode = NOT_REQUIRED` and `@Nullable`.

**4b. Response DTO** (`dto/response/`) — one shared DTO per resource (ADR-0011); `@Schema` per field
with description, example, `nullable`, and `allowableValues` for enums:

```java
@Schema(description = "Discovery session resource")
public record DiscoverySessionResponse(
        @Schema(description = "Session unique identifier", example = "019756a0-...")  UUID id,
        @Schema(description = "Current lifecycle state", example = "DRAFT",
                allowableValues = {"DRAFT","RECORDING","PAUSED","STOPPED","PROCESSING","COMPLETED","FAILED"})
        String status,
        @Schema(description = "Timestamp when recording started; null until then", nullable = true)
        Instant startedAt,
        // … all fields annotated
        @Schema(description = "Timestamp when the session was created", example = "2026-06-15T13:55:00Z")
        Instant createdAt,
        @Schema(description = "Timestamp of the last update", example = "2026-06-15T13:55:00Z")
        Instant updatedAt
) {}
```

Rules (ADR-0011): include `createdAt`/`updatedAt`; exclude `createdBy`/`updatedBy`; large fields
(transcript, embedding) in a separate endpoint.

**4c. Mappers** (`mappers/request`, `mappers/response`) — static `toCommand(...)` / `toResponse(...)`, no annotation noise.

**4d. Swagger interface** (`interfaces/rest/swagger/`) — all OpenAPI annotations here, zero in the controller impl.
Key elements: `@Tag` with description, `@Operation` with multi-line description, `@Parameter` for every
`@PathVariable`, `@ApiResponse(201)` with `@Content` + `@ExampleObject` showing the full JSON body:

```java
@RequestMapping(path = ApiVersioning.BASE + "/projects/{projectId}/sessions", produces = APPLICATION_JSON_VALUE)
@Tag(name = "Discovery Sessions", description = "Requirements-elicitation sessions — lifecycle from DRAFT to COMPLETED")
public interface DiscoverySessionController {

    @Operation(summary = "Create a discovery session", description = """
            Creates a new session in **DRAFT** under the given project, scoped to the caller's tenant.""")
    @ApiResponse(responseCode = "201", description = "Session created — Location header points to the new resource",
            content = @Content(mediaType = APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DiscoverySessionResponse.class),
                    examples = @ExampleObject(value = """
                            { "id": "019756a0-...", "status": "DRAFT", "audioDurationMs": 0, ... }""")))
    @ApiResponseBadRequest
    @ApiStandardErrorResponses
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
    @PostMapping(version = ApiVersioning.V1)
    ResponseEntity<DiscoverySessionResponse> create(
            @Parameter(description = "Project to create the session under", required = true,
                    example = "019756a0-1234-7abc-8def-000000000002")
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateDiscoverySessionRequest request);
}
```

**4e. Controller impl** (`interfaces/rest/controllers/`) — clean, `implements` the interface, builds the
`Location` with `ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")`. Add a `GroupedOpenApi`
bean for the BC in `OpenApiConfiguration` the first time it gets a controller.

## Step 5 — Integration test (acceptance, E2E)

`@SpringBootTest(RANDOM_PORT)` + Testcontainers, driven over HTTP (lives in `interfaces/rest/`). For
tenant endpoints: create an org → mint a JWT with that `orgId` (`TestJwtFactory`) → call the endpoint →
assert the row landed in the tenant schema. **Happy path + 2–3 critical unhappy** (e.g. unauthenticated).
Use random data so it's repeatable.

## Step 6 — Verify & record

```bash
./gradlew test            # unit (fast) + integration; parallel forks
./gradlew verifyModularity
```
All green → add a bullet to `CHANGELOG.md` under `[Unreleased]`.

---

## Quick checklist

- [ ] Domain aggregate/VOs + **unit tests (all branches)**
- [ ] Command/Query + port + handler + **handler unit test (mocked, all paths)**
- [ ] JPA repo + adapter + **per-entity migration** (right schema folder)
- [ ] DTOs (flat) + mappers + swagger interface + controller impl (+ BC `GroupedOpenApi` if new)
- [ ] **Integration E2E** (happy + critical unhappy, random data)
- [ ] `./gradlew test` + `verifyModularity` green
- [ ] `CHANGELOG.md` `[Unreleased]` updated
