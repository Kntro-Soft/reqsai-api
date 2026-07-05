package com.kntro.reqsai.discovery.suggestionquality;

import com.kntro.reqsai.discovery.application.command.AcceptSuggestionCommand;
import com.kntro.reqsai.discovery.application.handler.AcceptSuggestionCommandHandler;
import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.service.RealtimeSuggestionService;
import com.kntro.reqsai.discovery.application.service.SuggestionCreationService;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.valueobjects.LanguageCode;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.valueobjects.TechnicalProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * End-to-end proof of the suggestion-quality red-defect fixes on {@code feature/discovery-session-control},
 * exercised against a REAL Postgres + pgvector (Testcontainers) with only the two external non-deterministic
 * dependencies stubbed:
 * <ul>
 *   <li>the LLM ({@link ProgrammableGenerationConfig}) — the test hands it the exact {@link GenerationResult}
 *       to emit, so we control the suggestion type/title/targetStoryId;</li>
 *   <li>the embedding model ({@link ProgrammableEmbeddingConfig}) — a concept-tagged deterministic embedder,
 *       so pgvector near-duplicate dedup runs reproducibly (twins ⇒ cosine ≈ 0.97 ≥ 0.84; distinct ⇒ ≈ 0)
 *       with no OpenAI/Gemini calls.</li>
 * </ul>
 * Everything else — the dedup/classification in {@code SuggestionCreationService}, the {@code pg_advisory_xact_lock}
 * serialization in {@code RealtimeSuggestionService}/{@code PostgresSessionLockAdapter}, {@code findMostSimilar}
 * over pgvector, persistence, and the accept-handler edge-case rejection — runs for real against the tenant schema.
 *
 * <p>The suite provisions a real tenant once (via the organization REST endpoint, reusing the proven harness)
 * and then drives the application services directly under an explicit {@link TenantContext} + {@link TransactionTemplate},
 * which is what an HTTP request's filter would otherwise establish.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@Import({ProgrammableEmbeddingConfig.class, ProgrammableGenerationConfig.class})
@DisplayName("Integration: Suggestion-quality red-defect fixes (real pgvector + advisory lock)")
class SuggestionQualityRedDefectIntegrationTest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired private SuggestionCreationService suggestionCreation;
    @Autowired private RealtimeSuggestionService realtimeSuggestion;
    @Autowired private AcceptSuggestionCommandHandler acceptHandler;
    @Autowired private UserStoryRepository stories;
    @Autowired private SuggestionRepository suggestions;
    @Autowired private DiscoverySessionRepository sessionsRepo;
    @Autowired private TranscriptSegmentRepository segments;
    @Autowired private EmbeddingPort embeddingPort;
    @Autowired private ProgrammableGenerationConfig.GenerationScript script;
    @Autowired private ProjectRepository projects;
    @Autowired private PlatformTransactionManager txManager;

    private TransactionTemplate txTemplate;
    private String schema;
    private UUID orgId;

    @BeforeEach
    void provisionTenantSchema() {
        this.txTemplate = new TransactionTemplate(txManager);
        // Provision a real tenant via the org endpoint (creates schema tenant_<slug> + runs tenant Flyway,
        // including the pgvector migrations), then resolve the schema name so we can bind TenantContext.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID requestOrgId = UUID.randomUUID();
        ResponseEntity<String> res = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, requestOrgId.toString(), "ROLE_USER"))
                .header("Api-Version", "1").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((_, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String body = res.getBody();
        assertThat(body).isNotNull();
        String slug = body.replaceAll(".*\"slug\":\"([^\"]+)\".*", "$1");
        this.orgId = UUID.fromString(body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
        this.schema = "tenant_" + slug;
    }

    /** Seeds a real workspace Project in the tenant schema and returns its id — used as the discovery projectId
     *  so {@code WorkspaceModuleApi.findProjectSnapshot} resolves and the realtime pass builds a real
     *  {@code GenerationContext} (which carries the pending-suggestion ids). */
    private UUID seedProject() {
        return inTenantTx(() -> {
            TechnicalProfile profile = new TechnicalProfile(
                    List.of("Java"), List.of("Spring Boot"), List.of("Web"), List.of("PostgreSQL"),
                    "Clean Architecture", "SaaS");
            Project project = new Project(orgId, "Discovery project", "seed", profile, UUID.fromString(USER_ID));
            return projects.save(project).getId();
        });
    }

    // ── Scenario 1 (#1): accepted-twin NEW_STORY converges to UPDATE_STORY, no duplicate row ────────────

    @Test
    @DisplayName("#1 a NEW_STORY draft near-duplicating an indexed backlog story is downgraded to UPDATE_STORY")
    void acceptedTwin_downgradesToUpdate_noDuplicateStory() {
        UUID projectId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String concept = "password-reset";

        // Seed one indexed backlog story on the "password-reset" concept.
        UUID seededStoryId = inTenantTx(() -> {
            UserStory story = new UserStory(projectId,
                    "Recuperar contraseña", "usuario", "restablecer mi contraseña por correo",
                    "pueda volver a entrar", Priority.HIGH, 3);
            story.assignEmbedding(embeddingPort.embed(ProgrammableEmbeddingConfig.tag(concept) + story.toCanonicalText()));
            return stories.save(story).getId();
        });

        // The LLM emits a NEW_STORY that is a paraphrase of the seeded story (same concept ⇒ cosine ≈ 0.97).
        script.setResult(new GenerationResult(List.of(
                newStory(concept, "Restablecer contraseña olvidada", "usuario registrado",
                        "recuperar el acceso restableciendo la clave", "no quede bloqueado fuera de la cuenta"))));

        List<Suggestion> created = inTenantTx(() ->
                suggestionCreation.createSuggestions(script.next(), sessionId, projectId));

        // The draft converged into an UPDATE_STORY targeting the seeded story, with the similarity recorded.
        assertThat(created).hasSize(1);
        Suggestion s = created.getFirst();
        assertThat(s.getType()).isEqualTo(SuggestionType.UPDATE_STORY);
        assertThat(s.getTargetStoryId()).isEqualTo(seededStoryId);
        assertThat(s.getSimilarity()).isNotNull();
        assertThat(s.getSimilarity()).isGreaterThanOrEqualTo(0.84);

        // No duplicate NEW story row was minted: the only story in the project is the seeded one.
        long storyCount = inTenantTx(() -> stories.findAllByProjectId(projectId,
                org.springframework.data.domain.PageRequest.of(0, 50)).getTotalElements());
        assertThat(storyCount).isEqualTo(1);
        // And exactly one suggestion persisted, of type UPDATE_STORY.
        List<Suggestion> pending = inTenantTx(() ->
                suggestions.findAllBySessionIdAndStatus(sessionId, SuggestionStatus.PENDING));
        assertThat(pending).singleElement()
                .satisfies(p -> assertThat(p.getType()).isEqualTo(SuggestionType.UPDATE_STORY));
    }

    // ── Scenario 2 (#2): advisory-lock serializes overlapping realtime passes ───────────────────────────

    @Test
    @DisplayName("#2 two overlapping realtime passes are serialized by the advisory lock ⇒ exactly one suggestion")
    void overlappingPasses_serializedByAdvisoryLock_singleSuggestion() throws Exception {
        UUID projectId = UUID.randomUUID();
        String concept = "two-factor-auth";

        // Seed a RECORDING session with two final transcript segments past the watermark, long enough to
        // cross the 180-char min so a pass fires on the char trigger.
        UUID sessionId = inTenantTx(() -> {
            DiscoverySession session = new DiscoverySession(projectId, "Burst session", LanguageCode.of("es-PE"));
            session.startRecording(Instant.now());
            DiscoverySession saved = sessionsRepo.save(session);
            String pad = " ".repeat(0) + "El usuario quiere autenticacion de dos factores para proteger su cuenta "
                    + "y evitar accesos no autorizados durante el inicio de sesion en la plataforma.";
            segments.save(new TranscriptSegment(saved.getId(), 1, "A", pad, 0, 1000, true));
            segments.save(new TranscriptSegment(saved.getId(), 2, "A",
                    "Ademas debe soportar codigos temporales enviados por aplicacion movil.", 1000, 2000, true));
            return saved.getId();
        });

        // Both passes' LLM output is the SAME NEW_STORY (overlapping context re-surfacing one idea). Without
        // the lock, both REQUIRES_NEW passes read the empty PENDING set before either commits and each mints a
        // near-identical suggestion. With the advisory lock the later pass sees the earlier pass's committed
        // suggestion (and advanced watermark) and dedups it away.
        script.setResult(new GenerationResult(List.of(
                newStory(concept, "Autenticación de dos factores", "usuario",
                        "activar 2FA en el inicio de sesión", "mi cuenta esté más protegida"))));

        // Fire two overlapping passes concurrently, each on its own thread with its own tenant context.
        CountDownLatch startGate = new CountDownLatch(1);
        Runnable pass = () -> {
            TenantContext.setCurrentTenant(schema);
            TenantContext.setCurrentSchema(schema);
            try {
                startGate.await();
                realtimeSuggestion.suggest(sessionId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                TenantContext.clear();
            }
        };
        Thread t1 = new Thread(pass, "pass-1");
        Thread t2 = new Thread(pass, "pass-2");
        t1.start();
        t2.start();
        startGate.countDown(); // release both as simultaneously as possible
        t1.join(30_000);
        t2.join(30_000);

        // Exactly one suggestion exists for the session — the burst duplicate was prevented by serialization.
        List<Suggestion> all = inTenantTx(() ->
                suggestions.findAllBySessionIdAndStatus(sessionId, SuggestionStatus.PENDING));
        assertThat(all)
                .as("advisory lock must serialize the two passes so only one suggestion is created")
                .hasSize(1);
    }

    // ── Scenario 3 (#3): a NEW draft duplicating a still-PENDING suggestion is dropped; prompt got the ids ─

    @Test
    @DisplayName("#3 a NEW draft duplicating a still-PENDING suggestion is dropped and pending ids reach the prompt")
    void pendingTwin_dropped_andPendingIdsInContext() {
        UUID projectId = seedProject();
        String concept = "profile-photo";

        // Seed a RECORDING session with enough transcript to trigger a pass.
        UUID sessionId = inTenantTx(() -> {
            DiscoverySession session = new DiscoverySession(projectId, "Pending-twin session", LanguageCode.of("es-PE"));
            session.startRecording(Instant.now());
            DiscoverySession saved = sessionsRepo.save(session);
            segments.save(new TranscriptSegment(saved.getId(), 1, "A",
                    "El usuario desea poder cambiar su foto de perfil y tambien actualizar su nombre para "
                            + "mantener su informacion personal siempre actualizada en el sistema, en todo momento "
                            + "y de forma sencilla desde la pantalla de configuracion de su cuenta.", 0, 1000, true));
            return saved.getId();
        });

        // Seed a still-PENDING NEW_STORY suggestion on the "profile-photo" concept so the draft duplicates it.
        UUID pendingSuggestionId = inTenantTx(() -> {
            Suggestion pending = Suggestion.newStory(sessionId, projectId,
                    ProgrammableEmbeddingConfig.tag(concept) + "Editar foto de perfil", "usuario",
                    "cambiar mi foto de perfil", "mi perfil se vea actualizado", Priority.MEDIUM, 2);
            return suggestions.save(pending).getId();
        });

        // The LLM emits a NEW_STORY paraphrasing the pending one (same concept ⇒ embedding near-duplicate).
        script.setResult(new GenerationResult(List.of(
                newStory(concept, "Actualizar imagen de perfil", "usuario registrado",
                        "reemplazar la foto de mi perfil", "mostrar una imagen reciente"))));

        // Run the full realtime pass so buildContext assembles the pending-suggestion ids for the prompt.
        inTenantTx(() -> {
            realtimeSuggestion.suggest(sessionId);
            return null;
        });

        // The paraphrase was dropped: still exactly one PENDING suggestion (the seeded one), no 2nd near-dup.
        List<Suggestion> pending = inTenantTx(() ->
                suggestions.findAllBySessionIdAndStatus(sessionId, SuggestionStatus.PENDING));
        assertThat(pending).as("the pending-twin paraphrase must be dropped, not persisted").hasSize(1);
        assertThat(pending.getFirst().getId()).isEqualTo(pendingSuggestionId);

        // And the generation context carried the pending suggestion's id (so the model could target it).
        assertThat(script.lastContext()).isNotNull();
        assertThat(script.lastContext().alreadySuggested())
                .extracting(com.kntro.reqsai.discovery.application.port.GenerationContext.PendingSuggestion::id)
                .contains(pendingSuggestionId);
    }

    // ── Scenario 4 (short utterance): sub-threshold char trigger holds; force/stop flush releases it ───────

    @Test
    @DisplayName("short utterance: below min-chars no suggestion on the char trigger, but a force flush emits it")
    void shortUtterance_heldOnCharTrigger_emittedOnForceFlush() {
        UUID projectId = UUID.randomUUID();
        String concept = "logout-button";

        UUID sessionId = inTenantTx(() -> {
            DiscoverySession session = new DiscoverySession(projectId, "Short session", LanguageCode.of("es-PE"));
            session.startRecording(Instant.now());
            DiscoverySession saved = sessionsRepo.save(session);
            // A single very short final segment (< 180 chars, first pass ⇒ lastSuggestedAt == null).
            segments.save(new TranscriptSegment(saved.getId(), 1, "A", "Quiero cerrar sesión.", 0, 500, true));
            return saved.getId();
        });

        script.setResult(new GenerationResult(List.of(
                newStory(concept, "Cerrar sesión", "usuario", "cerrar mi sesión", "proteger mi cuenta"))));

        // Char trigger (force=false): the short accrued text is below the min and it is the first pass, so
        // shouldGenerate() returns false — nothing is generated and the watermark does NOT advance.
        inTenantTx(() -> {
            realtimeSuggestion.suggest(sessionId, false);
            return null;
        });
        List<Suggestion> afterCharTrigger = inTenantTx(() ->
                suggestions.findAllBySessionIdAndStatus(sessionId, SuggestionStatus.PENDING));
        assertThat(afterCharTrigger).as("short content must not fire on the char trigger").isEmpty();
        int watermarkAfterHold = inTenantTx(() ->
                sessionsRepo.findById(sessionId).orElseThrow().getLastSuggestedSequence());
        assertThat(watermarkAfterHold).as("watermark stays at 0 so the short tail is retained, not lost").isZero();

        // Stop flush (force=true): bypasses both thresholds and produces the suggestion — short content is
        // never permanently lost.
        inTenantTx(() -> {
            realtimeSuggestion.suggest(sessionId, true);
            return null;
        });
        List<Suggestion> afterFlush = inTenantTx(() ->
                suggestions.findAllBySessionIdAndStatus(sessionId, SuggestionStatus.PENDING));
        assertThat(afterFlush).as("a force/stop flush emits the previously-held short suggestion").hasSize(1);
        assertThat(afterFlush.getFirst().getType()).isEqualTo(SuggestionType.NEW_STORY);
    }

    // ── Scenario 6 (#4): targetless EDGE_CASE accept is rejected 422, no standalone story minted ──────────

    @Test
    @DisplayName("#4 accepting a targetless EDGE_CASE is rejected with EDGE_CASE_WITHOUT_TARGET (422), no story minted")
    void targetlessEdgeCase_rejected422_noStandaloneStory() {
        UUID projectId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        // Seed a PENDING EDGE_CASE suggestion with NO resolvable target (targetStoryId == null) but a real
        // criterion — exactly the state the fixed pipeline leaves when no story clears the 0.84 floor.
        UUID edgeCaseId = inTenantTx(() -> {
            Suggestion edge = Suggestion.edgeCase(sessionId, projectId,
                    "Bloqueo tras 3 intentos fallidos", "usuario", "ser bloqueado tras varios intentos",
                    "proteger la cuenta", Priority.MEDIUM, 2, "seguridad", /* targetStoryId */ null,
                    new Suggestion.DraftCriterion("Bloqueo por intentos",
                            "el usuario falló 3 veces", "intenta de nuevo", "la cuenta queda bloqueada 15 min"));
            return suggestions.save(edge).getId();
        });

        AcceptSuggestionCommand cmd = new AcceptSuggestionCommand(
                sessionId, edgeCaseId, null, null, null, null, null, null, null);

        // Call the handler under its OWN transaction (not wrapped in our txTemplate): it is @Transactional and
        // throwing marks its tx rollback-only, which would otherwise surface as UnexpectedRollbackException on
        // an enclosing commit. We just need the tenant thread-local bound while it runs.
        DomainException ex = runInTenant(() ->
                catchThrowableOfType(DomainException.class, () -> acceptHandler.handle(cmd)));

        // Rejected with the exact domain error, mapped to HTTP 422.
        assertThat(ex).isNotNull();
        assertThat(ex.error()).isEqualTo(DiscoveryError.EDGE_CASE_WITHOUT_TARGET);
        assertThat(ex.error().status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

        // No standalone story was minted, and the suggestion stays PENDING for the analyst to fix.
        long storyCount = inTenantTx(() -> stories.findAllByProjectId(projectId,
                org.springframework.data.domain.PageRequest.of(0, 50)).getTotalElements());
        assertThat(storyCount).as("a targetless edge case must not create a standalone story").isZero();
        Suggestion after = inTenantTx(() -> suggestions.findById(edgeCaseId).orElseThrow());
        assertThat(after.getStatus()).isEqualTo(SuggestionStatus.PENDING);
    }

    // ── Scenario 7 (#7): an over-long edge-case scenario is capped at 200, accept does not fail ────────────

    @Test
    @DisplayName("#7 an over-long edge-case scenario label is truncated to 200 chars (accept succeeds)")
    void overlongEdgeCaseScenario_cappedAt200() {
        UUID projectId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String longScenario = "x".repeat(350);

        // A resolvable target story so the accept path adds the criterion (rather than rejecting).
        UUID targetStoryId = inTenantTx(() -> {
            UserStory story = new UserStory(projectId, "Inicio de sesión", "usuario",
                    "iniciar sesión", "acceder al sistema", Priority.HIGH, 3);
            return stories.save(story).getId();
        });

        UUID edgeCaseId = inTenantTx(() -> {
            Suggestion edge = Suggestion.edgeCase(sessionId, projectId,
                    "Sesión expira por inactividad", "usuario", "cerrar sesión tras inactividad",
                    "proteger la cuenta", Priority.MEDIUM, 2, "seguridad", targetStoryId,
                    new Suggestion.DraftCriterion(longScenario,
                            "el usuario está inactivo 15 min", "vuelve a la app", "la sesión ha expirado"));
            // Sanitized on creation: the scenario is already capped at 200 the moment it is stored.
            assertThat(edge.getDraftAcceptanceCriteria().getFirst().scenario()).hasSize(200);
            return suggestions.save(edge).getId();
        });

        AcceptSuggestionCommand cmd = new AcceptSuggestionCommand(
                sessionId, edgeCaseId, null, null, null, null, null, null, null);

        UUID resolved = inTenantTx(() -> acceptHandler.handle(cmd).getResolvedStoryId());
        assertThat(resolved).isEqualTo(targetStoryId);

        // The criterion was added to the target story with the scenario capped at 200 — accept did NOT fail.
        UserStory target = inTenantTx(() -> stories.findById(targetStoryId).orElseThrow());
        assertThat(target.getAcceptanceCriteria()).anySatisfy(c ->
                assertThat(c.getScenario()).hasSize(200));
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────────────

    /** A NEW_STORY generation item whose canonical text is concept-tagged so the embedder controls its vector. */
    private static GenerationResult.GeneratedStory newStory(String concept, String title, String role,
                                                            String action, String benefit) {
        return new GenerationResult.GeneratedStory(
                SuggestionType.NEW_STORY,
                ProgrammableEmbeddingConfig.tag(concept) + title,
                role, action, benefit, Priority.MEDIUM, 3, List.of(), null, null);
    }

    /** Runs {@code body} inside the tenant schema and a single committed transaction (mirrors a request). */
    private <T> T inTenantTx(java.util.function.Supplier<T> body) {
        AtomicReference<T> out = new AtomicReference<>();
        TenantContext.setCurrentTenant(schema);
        TenantContext.setCurrentSchema(schema);
        try {
            txTemplate.executeWithoutResult(_ -> out.set(body.get()));
        } finally {
            TenantContext.clear();
        }
        return out.get();
    }

    /** Binds the tenant thread-local for {@code body} WITHOUT opening a transaction, so a @Transactional
     *  service invoked inside manages (and rolls back) its own transaction independently. */
    private <T> T runInTenant(java.util.function.Supplier<T> body) {
        TenantContext.setCurrentTenant(schema);
        TenantContext.setCurrentSchema(schema);
        try {
            return body.get();
        } finally {
            TenantContext.clear();
        }
    }
}
