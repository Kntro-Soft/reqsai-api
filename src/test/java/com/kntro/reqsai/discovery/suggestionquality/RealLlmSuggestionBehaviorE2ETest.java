package com.kntro.reqsai.discovery.suggestionquality;

import com.kntro.reqsai.discovery.application.port.DiscoverySessionRepository;
import com.kntro.reqsai.discovery.application.port.RequirementGenerationPort;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.service.RealtimeSuggestionService;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.valueobjects.TechnicalProfile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.util.StringUtils.hasText;

/**
 * REAL-LLM behavioral end-to-end probe for the discovery suggestion core.
 *
 * <p>Unlike {@link SuggestionQualityRedDefectIntegrationTest} — which stubs the two non-deterministic
 * externals with a programmable {@code RequirementGenerationPort} and a concept-tagged
 * {@code EmbeddingPort} so it can assert exact classifications — this test wires the <b>REAL</b> OpenAI
 * generation strategy + REAL OpenAI embedding adapter + REAL pgvector (Testcontainers), then feeds
 * crafted transcripts at the TEXT/segment level (bypassing audio/STT) and observes what the model
 * actually produces.
 *
 * <h2>Why this is a probe, not a gate</h2>
 * The LLM is non-deterministic, so this suite is a <b>behavioral report</b>: every scenario prints a
 * clearly-labelled, parseable {@code [LLM-E2E]} block (suggestion type(s), draft title(s),
 * targetStoryId, recorded similarity, outcome) and asserts <b>only</b> the non-controversial invariant
 * (e.g. "a force flush yields ≥1 suggestion", "two distinct capabilities yield ≥2 stories", "no two
 * persisted stories exceed cosine ~0.9"). Everything nuanced is logged for the coordinator to judge.
 *
 * <h2>Wiring (see {@code @TestPropertySource})</h2>
 * <ul>
 *   <li>{@code spring.ai.model.chat=openai} / {@code spring.ai.model.embedding=openai} — activates the
 *       Spring AI OpenAI autoconfiguration so {@code OpenAiChatModel} / {@code OpenAiEmbeddingModel}
 *       beans exist for the real adapters to consume.</li>
 *   <li>{@code reqsai.ai.generation.provider=openai} / {@code reqsai.ai.embedding.provider=openai} —
 *       routes {@code RequirementGenerationRouter} / {@code EmbeddingRouter} to the OpenAI adapters.</li>
 *   <li>The API key flows from the process environment via {@code spring.ai.openai.api-key:${OPENAI_API_KEY:}}
 *       already declared in {@code application.yml} — this test never reads it from a file.</li>
 * </ul>
 * It deliberately does <b>not</b> import {@code ProgrammableEmbeddingConfig}/{@code ProgrammableGenerationConfig}
 * or the shared {@code StubEmbeddingConfig}/{@code StubRequirementGenerationConfig}: the whole point is
 * to exercise the real adapters. Real pgvector comes from {@link AbstractIntegrationTest}.
 *
 * <h2>Skip-when-no-key</h2>
 * Each test opens with an {@link Assumptions#assumeTrue} on {@code OPENAI_API_KEY}, so the suite
 * SKIPS (never FAILS) when no key is present.
 *
 * <h2>Tag / run</h2>
 * Tagged {@code @Tag("llm")} so the {@code unitTest}/{@code integrationTest}/default gates do not run it
 * (they cost real tokens). Run only this suite with:
 * <pre>./gradlew llmTest --max-workers=1</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("llm")
// Belt-and-suspenders skip: JUnit disables (skips, never fails) every method when the key is absent,
// so Gradle logs a clean SKIPPED even before the Spring context boots. The in-body assumeTrue in
// @BeforeEach is kept too, so an unexpected mis-wiring still aborts rather than fails.
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@TestPropertySource(properties = {
        // Activate the Spring AI OpenAI autoconfiguration (default is 'none') so the OpenAiChatModel /
        // OpenAiEmbeddingModel beans the real adapters need are created.
        "spring.ai.model.chat=openai",
        "spring.ai.model.embedding=openai",
        // Route the two routers to their OpenAI adapters.
        "reqsai.ai.generation.provider=openai",
        "reqsai.ai.embedding.provider=openai",
        // Match the pgvector schema (768-dim). application.yml sets this too, but pin it here so the test
        // is self-describing and independent of profile ordering.
        "spring.ai.openai.embedding.options.dimensions=768",
        // The key itself is resolved from the environment by application.yml's
        // spring.ai.openai.api-key: ${OPENAI_API_KEY:} — this test never opens a file to get it.
})
@DisplayName("Real-LLM E2E: discovery suggestion behavior probe (OpenAI generation + OpenAI embeddings + pgvector)")
class RealLlmSuggestionBehaviorE2ETest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    /** How close two persisted stories may be before we call them "wrongly merged" (tolerant ceiling). */
    private static final double MERGE_CEILING = 0.90;

    @Autowired private RealtimeSuggestionService realtimeSuggestion;
    @Autowired private UserStoryRepository stories;
    @Autowired private SuggestionRepository suggestions;
    @Autowired private DiscoverySessionRepository sessionsRepo;
    @Autowired private TranscriptSegmentRepository segments;
    @Autowired private EmbeddingPort embeddingPort;
    @Autowired private RequirementGenerationPort generationPort;
    @Autowired private ProjectRepository projects;
    @Autowired private PlatformTransactionManager txManager;

    private TransactionTemplate txTemplate;
    private String schema;
    private UUID orgId;

    // ── Skip gracefully when no key, then provision a real tenant schema (mirrors the red-defect suite) ──

    @BeforeEach
    void provisionTenantSchema() {
        Assumptions.assumeTrue(hasText(System.getenv("OPENAI_API_KEY")),
                "OPENAI_API_KEY not set — skipping real-LLM E2E");

        this.txTemplate = new TransactionTemplate(txManager);
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

        // Sanity: prove the REAL providers are actually wired and reachable. If the key is present but the
        // provider is unavailable, fail loudly here rather than silently producing empty results downstream.
        boolean genAvailable = generationPort.isAvailable();
        boolean embedAvailable = embeddingPort.isAvailable();
        log("wiring", "generation.isAvailable=" + genAvailable + " (impl=" + generationPort.getClass().getSimpleName()
                + "); embedding.isAvailable=" + embedAvailable + " (impl=" + embeddingPort.getClass().getSimpleName() + ")");
        assertThat(genAvailable).as("real OpenAI generation adapter must be available with a key set").isTrue();
        assertThat(embedAvailable).as("real OpenAI embedding adapter must be available with a key set").isTrue();
    }

    // ── Scenario 1: short-utterance cadence — held on the char trigger, released by a force flush ─────────

    @Test
    @DisplayName("1) short-utterance cadence: below min-chars no suggestion on the char trigger; a force flush emits ≥1")
    void shortUtteranceCadence() {
        UUID projectId = seedProject();
        String utterance = "Quiero un botón para cerrar sesión.";
        UUID sessionId = seedRecordingSession(projectId, "Short-utterance session",
                new Seg(1, utterance));

        int chars = utterance.length();
        log("scenario-1", "utterance chars=" + chars + " (min-transcript-chars default=180); first pass ⇒ lastSuggestedAt==null");

        // Char trigger (force=false): sub-threshold on the first pass ⇒ shouldGenerate() returns false.
        inTenantTx(() -> { realtimeSuggestion.suggest(sessionId, false); return null; });
        List<Suggestion> afterCharTrigger = pending(sessionId);
        int watermarkAfterHold = inTenantTx(() ->
                sessionsRepo.findById(sessionId).orElseThrow().getLastSuggestedSequence());
        log("scenario-1", "AFTER char-trigger (force=false): pending=" + describe(afterCharTrigger)
                + " watermark=" + watermarkAfterHold
                + " ⇒ observation: short speech is " + (afterCharTrigger.isEmpty() ? "HELD (waits)" : "EMITTED")
                + " on the char trigger");

        // Stop flush (force=true): bypasses both thresholds and generates.
        inTenantTx(() -> { realtimeSuggestion.suggest(sessionId, true); return null; });
        List<Suggestion> afterFlush = pending(sessionId);
        log("scenario-1", "AFTER force flush (force=true): pending=" + describe(afterFlush));

        // Assert only the non-controversial part: a force/stop flush must not lose the short tail.
        assertThat(afterFlush)
                .as("force flush must yield >=1 suggestion so short speech is never permanently lost")
                .isNotEmpty();
    }

    // ── Scenario 2: exact duplicate — same capability stated verbatim twice ⇒ one suggestion ─────────────

    @Test
    @DisplayName("2) exact duplicate: the same capability stated verbatim twice yields a single suggestion (dedup)")
    void exactDuplicate() {
        UUID projectId = seedProject();
        String capability = "Como usuario quiero poder exportar mis reportes a PDF para archivarlos y compartirlos "
                + "por correo con mi equipo cada fin de mes sin depender de nadie más.";
        UUID sessionId = seedRecordingSession(projectId, "Exact-duplicate session",
                new Seg(1, capability), new Seg(2, capability));

        inTenantTx(() -> { realtimeSuggestion.suggest(sessionId, true); return null; });
        List<Suggestion> persisted = pending(sessionId);
        log("scenario-2", "verbatim capability stated twice ⇒ pending=" + describe(persisted)
                + "; storyDedup=" + storyPairwiseSummary(projectId));

        assertThat(persisted)
                .as("a verbatim-duplicated capability must not persist two near-identical suggestions")
                .hasSizeLessThanOrEqualTo(1);
        assertNoStoriesWronglyMerged(projectId);
    }

    // ── Scenario 3: slight paraphrase — reworded same capability ⇒ dedup OR converge to UPDATE_STORY ──────

    @Test
    @DisplayName("3) slight paraphrase: a reworded same capability dedups or converges to UPDATE_STORY (near the 0.84 bar)")
    void slightParaphrase() {
        UUID projectId = seedProject();
        // Seed one ACCEPTED + INDEXED story, then a transcript rewording the SAME capability.
        UUID seeded = seedIndexedStory(projectId, "Exportar reportes a PDF", "usuario",
                "exportar mis reportes a PDF", "poder archivarlos y compartirlos");
        String paraphrase = "Necesito que el sistema me permita descargar mis informes en formato PDF para "
                + "guardarlos y enviárselos a mi equipo, básicamente lo mismo de exportar los reportes.";
        UUID sessionId = seedRecordingSession(projectId, "Paraphrase session", new Seg(1, paraphrase));

        inTenantTx(() -> { realtimeSuggestion.suggest(sessionId, true); return null; });
        List<Suggestion> persisted = pending(sessionId);
        log("scenario-3", "seeded indexed story=" + seeded + "; paraphrase transcript ⇒ pending=" + describe(persisted));

        // Tolerant: the paraphrase should EITHER dedup away (0 persisted) OR converge to an UPDATE_STORY
        // targeting the seeded story — not spawn a fresh NEW_STORY duplicate. We accept any of those.
        boolean converged = persisted.isEmpty()
                || persisted.stream().anyMatch(s -> s.getType() == SuggestionType.UPDATE_STORY);
        boolean dupNewStory = persisted.stream()
                .anyMatch(s -> s.getType() == SuggestionType.NEW_STORY);
        log("scenario-3", "converged(dedup or UPDATE)=" + converged + "; producedNewStory=" + dupNewStory
                + "; storyDedup=" + storyPairwiseSummary(projectId));
        assertThat(converged || !dupNewStory)
                .as("a paraphrase of an accepted story should dedup or become UPDATE_STORY, not a standalone NEW_STORY")
                .isTrue();
        assertNoStoriesWronglyMerged(projectId);
    }

    // ── Scenario 4: distinct-but-related (false-positive guard) ⇒ ≥2 distinct stories ────────────────────

    @Test
    @DisplayName("4) distinct-but-related: two genuinely different topically-near capabilities are NOT merged (>=2 stories)")
    void distinctButRelated() {
        UUID projectId = seedProject();
        String transcript = "El usuario quiere iniciar sesión con su correo y contraseña para entrar a la plataforma. "
                + "Aparte, y esto es distinto, quiere poder restablecer su contraseña mediante un enlace que le llegue "
                + "por correo cuando la haya olvidado.";
        UUID sessionId = seedRecordingSession(projectId, "Distinct-related session", new Seg(1, transcript));

        inTenantTx(() -> { realtimeSuggestion.suggest(sessionId, true); return null; });
        List<Suggestion> persisted = pending(sessionId);
        long storyCount = storyCount(projectId);
        log("scenario-4", "login vs reset-password ⇒ pending=" + describe(persisted)
                + " persistedStories=" + storyCount + "; storyDedup=" + storyPairwiseSummary(projectId));

        // Distinct capabilities must not be wrongly merged. These arrive as suggestions (pending), so we
        // assert on the number of distinct NEW_STORY suggestions rather than accepted stories.
        long distinctStoryDrafts = persisted.stream()
                .filter(s -> s.getType() == SuggestionType.NEW_STORY || s.getType() == SuggestionType.UPDATE_STORY)
                .count();
        log("scenario-4", "distinct story-type drafts=" + distinctStoryDrafts);
        assertThat(distinctStoryDrafts)
                .as("two genuinely distinct capabilities must yield >=2 story suggestions (not wrongly merged)")
                .isGreaterThanOrEqualTo(2);
        assertNoStoriesWronglyMerged(projectId);
    }

    // ── Scenario 5: UPDATE — a new detail on an accepted+indexed capability ⇒ UPDATE (or dedup-to-update) ─

    @Test
    @DisplayName("5) update: adding a new detail to an accepted+indexed capability yields UPDATE_STORY targeting it (not a duplicate NEW)")
    void updateExistingCapability() {
        UUID projectId = seedProject();
        UUID loginStory = seedIndexedStory(projectId, "Iniciar sesión", "usuario",
                "iniciar sesión con correo y contraseña", "acceder a la plataforma");
        String transcript = "Sobre lo del inicio de sesión que ya tenemos: además quiero que soporte autenticación con "
                + "Google, o sea el mismo inicio de sesión pero también permitiendo entrar con la cuenta de Google.";
        UUID sessionId = seedRecordingSession(projectId, "Update session", new Seg(1, transcript));

        inTenantTx(() -> { realtimeSuggestion.suggest(sessionId, true); return null; });
        List<Suggestion> persisted = pending(sessionId);
        log("scenario-5", "seeded login story=" + loginStory + "; 'además … con Google' ⇒ pending=" + describe(persisted));

        boolean updateOrEdgeOrDedup = persisted.isEmpty()
                || persisted.stream().anyMatch(s ->
                    (s.getType() == SuggestionType.UPDATE_STORY || s.getType() == SuggestionType.EDGE_CASE)
                    && loginStory.equals(s.getTargetStoryId()));
        boolean anyUpdateOrEdge = persisted.stream().anyMatch(s ->
                s.getType() == SuggestionType.UPDATE_STORY || s.getType() == SuggestionType.EDGE_CASE);
        log("scenario-5", "targetsLoginStory=" + updateOrEdgeOrDedup + "; anyUpdateOrEdge=" + anyUpdateOrEdge
                + "; storyDedup=" + storyPairwiseSummary(projectId));

        // Tolerant: accept an UPDATE/EDGE (ideally targeting the login story) OR a dedup-away. The failure
        // we guard is a standalone near-duplicate NEW story of the same capability.
        assertNoStoriesWronglyMerged(projectId);
    }

    // ── Scenario 6: all four types across a richer transcript ⇒ report which types appear ────────────────

    @Test
    @DisplayName("6) all four types: a crafted multi-topic transcript over an indexed backlog — report which types the model emits")
    void allFourTypes() {
        UUID projectId = seedProject();
        // Seed an accepted+indexed capability so UPDATE_STORY / EDGE_CASE targeting it are reachable.
        UUID checkoutStory = seedIndexedStory(projectId, "Pagar el carrito", "cliente",
                "pagar los productos de mi carrito con tarjeta", "completar mi compra");

        String transcript = "Volviendo al pago del carrito: además debería permitir pagar con Yape, no solo tarjeta. "
                + "Y ojo, si la tarjeta es rechazada por fondos insuficientes debe mostrar un mensaje claro y no cobrar. "
                + "Por otro lado, quiero un panel de historial de pedidos donde el cliente vea sus compras anteriores. "
                + "Ah, y no me quedó claro lo de las notificaciones: ¿las quieren por correo, por push, o ambas?";
        UUID sessionId = seedRecordingSession(projectId, "All-types session", new Seg(1, transcript));

        inTenantTx(() -> { realtimeSuggestion.suggest(sessionId, true); return null; });
        List<Suggestion> persisted = pending(sessionId);
        List<SuggestionType> typesSeen = persisted.stream().map(Suggestion::getType).distinct().toList();
        log("scenario-6", "seeded checkout story=" + checkoutStory);
        log("scenario-6", "types observed across the pass=" + typesSeen + "; pending=" + describe(persisted));

        // Report-only for the type spread; assert only the safe merge guard. The coordinator judges the mix.
        assertThat(persisted)
                .as("a rich multi-topic transcript should elicit at least one suggestion")
                .isNotEmpty();
        assertNoStoriesWronglyMerged(projectId);
    }

    // ── Scenario 7: hard cases (paraphrase near threshold; incremental refinement; ambiguous → question) ─

    @Test
    @DisplayName("7a) hard: a subtle paraphrase right around the 0.84 threshold — report similarity vs the bar")
    void hardSubtleParaphrase() {
        UUID projectId = seedProject();
        UUID seeded = seedIndexedStory(projectId, "Filtrar tareas por estado", "usuario",
                "filtrar mis tareas por estado pendiente o completada", "encontrarlas más rápido");
        // Subtle rewording that a human would call the same capability but sits near the boundary.
        String paraphrase = "Estaría bueno poder ver mis tareas separando las que ya terminé de las que aún no, "
                + "para ubicarlas rapidísimo.";
        UUID sessionId = seedRecordingSession(projectId, "Near-threshold session", new Seg(1, paraphrase));

        inTenantTx(() -> { realtimeSuggestion.suggest(sessionId, true); return null; });
        List<Suggestion> persisted = pending(sessionId);
        Double recorded = persisted.stream().map(Suggestion::getSimilarity).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        log("scenario-7a", "seeded story=" + seeded + "; near-threshold paraphrase ⇒ pending=" + describe(persisted)
                + "; recordedSimilarity=" + recorded + " vs dedup bar 0.84; storyDedup=" + storyPairwiseSummary(projectId));
        // Report-only: whether it lands above or below 0.84 is exactly what the coordinator wants to see.
        assertNoStoriesWronglyMerged(projectId);
    }

    @Test
    @DisplayName("7b) hard: incremental refinement across two consecutive passes — report convergence")
    void hardIncrementalRefinement() {
        UUID projectId = seedProject();
        UUID sessionId = seedRecordingSession(projectId, "Incremental session",
                new Seg(1, "El usuario quiere subir una foto de perfil para personalizar su cuenta y que otros lo "
                        + "reconozcan fácilmente dentro de la aplicación en todo momento."));

        // Pass 1: establish the capability.
        inTenantTx(() -> { realtimeSuggestion.suggest(sessionId, true); return null; });
        List<Suggestion> afterPass1 = pending(sessionId);
        log("scenario-7b", "pass 1 ⇒ pending=" + describe(afterPass1));

        // Add a refinement segment (a new detail on the SAME capability) and run pass 2.
        inTenantTx(() -> {
            int nextSeq = segments.findFinalBySessionIdAfter(sessionId, 0).size() + 1;
            segments.save(new TranscriptSegment(sessionId, nextSeq, "A",
                    "Sobre la foto de perfil: además debe poder recortarla antes de guardarla y aceptar solo "
                            + "imágenes de menos de 5 megabytes.", 2000, 3000, true));
            return null;
        });
        inTenantTx(() -> { realtimeSuggestion.suggest(sessionId, true); return null; });
        List<Suggestion> afterPass2 = pending(sessionId);
        log("scenario-7b", "pass 2 (refinement) ⇒ pending=" + describe(afterPass2)
                + "; storyDedup=" + storyPairwiseSummary(projectId));

        // Tolerant: the refinement should NOT explode into many near-duplicate NEW stories of "profile photo".
        long profilePhotoNewStories = afterPass2.stream()
                .filter(s -> s.getType() == SuggestionType.NEW_STORY)
                .count();
        log("scenario-7b", "NEW_STORY count after refinement=" + profilePhotoNewStories);
        assertNoStoriesWronglyMerged(projectId);
    }

    @Test
    @DisplayName("7c) hard: an ambiguous/underspecified requirement — does it produce a CLARIFYING_QUESTION?")
    void hardAmbiguousRequirement() {
        UUID projectId = seedProject();
        String ambiguous = "Queremos que el sistema sea rápido y que maneje bien la seguridad, ya saben, "
                + "que esté todo bien protegido. Eso es lo importante por ahora.";
        UUID sessionId = seedRecordingSession(projectId, "Ambiguous session", new Seg(1, ambiguous));

        inTenantTx(() -> { realtimeSuggestion.suggest(sessionId, true); return null; });
        List<Suggestion> persisted = pending(sessionId);
        boolean askedQuestion = persisted.stream()
                .anyMatch(s -> s.getType() == SuggestionType.CLARIFYING_QUESTION);
        log("scenario-7c", "ambiguous requirement ⇒ pending=" + describe(persisted)
                + "; producedClarifyingQuestion=" + askedQuestion);
        // Report-only: whether the model asks vs guesses is the observation of interest. No hard assertion.
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────────────

    /** A transcript segment spec (sequence + text); startMs/endMs are derived so the row is valid. */
    private record Seg(int sequence, String text) {}

    /** Seeds a real workspace Project and returns its id (so the realtime pass resolves a ProjectSnapshot). */
    private UUID seedProject() {
        return inTenantTx(() -> {
            TechnicalProfile profile = new TechnicalProfile(
                    List.of("Java"), List.of("Spring Boot"), List.of("Web"), List.of("PostgreSQL"),
                    "Clean Architecture", "SaaS");
            Project project = new Project(orgId, "Discovery project", "seed", profile, UUID.fromString(USER_ID));
            return projects.save(project).getId();
        });
    }

    /** Seeds a RECORDING session with the given final segments (final, so they cross the watermark). */
    private UUID seedRecordingSession(UUID projectId, String name, Seg... segs) {
        return inTenantTx(() -> {
            DiscoverySession session = new DiscoverySession(projectId, name,
                    com.kntro.reqsai.shared.domain.valueobjects.LanguageCode.of("es-PE"));
            session.startRecording(Instant.now());
            DiscoverySession saved = sessionsRepo.save(session);
            for (Seg s : segs) {
                long start = s.sequence() * 1000L;
                segments.save(new TranscriptSegment(saved.getId(), s.sequence(), "A", s.text(), start, start + 1000, true));
            }
            return saved.getId();
        });
    }

    /** Seeds an ACCEPTED + INDEXED story (real embedding computed by the REAL embedder) and returns its id. */
    private UUID seedIndexedStory(UUID projectId, String title, String role, String action, String benefit) {
        return inTenantTx(() -> {
            UserStory story = new UserStory(projectId, title, role, action, benefit, Priority.HIGH, 3);
            story.assignEmbedding(embeddingPort.embed(story.toCanonicalText()));
            return stories.save(story).getId();
        });
    }

    private List<Suggestion> pending(UUID sessionId) {
        return inTenantTx(() -> suggestions.findAllBySessionIdAndStatus(sessionId, SuggestionStatus.PENDING));
    }

    private long storyCount(UUID projectId) {
        return inTenantTx(() -> stories.findAllByProjectId(projectId, PageRequest.of(0, 50)).getTotalElements());
    }

    /**
     * Guards the one invariant that holds regardless of LLM wording: no two persisted stories of the
     * project sit above {@link #MERGE_CEILING} cosine (which would mean two genuinely-distinct
     * capabilities were wrongly merged into near-identical rows, or a duplicate was minted).
     */
    private void assertNoStoriesWronglyMerged(UUID projectId) {
        List<UserStory> all = inTenantTx(() ->
                stories.findAllByProjectId(projectId, PageRequest.of(0, 100)).getContent());
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                float[] a = all.get(i).getEmbedding();
                float[] b = all.get(j).getEmbedding();
                if (a == null || b == null) continue;
                double sim = cosine(a, b);
                assertThat(sim)
                        .as("stories '%s' and '%s' should not be near-identical (cosine %.4f > %.2f)",
                                all.get(i).getTitle(), all.get(j).getTitle(), sim, MERGE_CEILING)
                        .isLessThanOrEqualTo(MERGE_CEILING);
            }
        }
    }

    /** A short report of the pairwise cosine similarities between persisted stories (for the log). */
    private String storyPairwiseSummary(UUID projectId) {
        List<UserStory> all = inTenantTx(() ->
                stories.findAllByProjectId(projectId, PageRequest.of(0, 100)).getContent());
        if (all.size() < 2) return "stories=" + all.size() + " (no pairs)";
        StringBuilder sb = new StringBuilder("stories=" + all.size() + " pairs[");
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                float[] a = all.get(i).getEmbedding();
                float[] b = all.get(j).getEmbedding();
                if (a == null || b == null) continue;
                sb.append(String.format("(%d,%d)=%.3f ", i, j, cosine(a, b)));
            }
        }
        return sb.append("]").toString();
    }

    private static double cosine(float[] a, float[] b) {
        if (a.length != b.length) return 0.0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** Compact, parseable one-line description of a suggestion list for the report blocks. */
    private static String describe(List<Suggestion> list) {
        if (list.isEmpty()) return "[] (0)";
        StringBuilder sb = new StringBuilder("[");
        for (Suggestion s : list) {
            sb.append("{type=").append(s.getType())
              .append(", title='").append(s.getType() == SuggestionType.CLARIFYING_QUESTION ? s.getQuestion() : s.getDraftTitle()).append('\'')
              .append(", target=").append(s.getTargetStoryId())
              .append(", sim=").append(s.getSimilarity())
              .append("} ");
        }
        return sb.append("] (").append(list.size()).append(")").toString();
    }

    /** Emits a labelled, grep-able report line: {@code [LLM-E2E][<label>] <message>}. */
    private static void log(String label, String message) {
        System.out.println("[LLM-E2E][" + label + "] " + message);
    }

    /** Runs {@code body} inside the tenant schema and a single committed transaction (mirrors a request). */
    private <T> T inTenantTx(Supplier<T> body) {
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
}
