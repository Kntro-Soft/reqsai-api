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
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.util.StringUtils.hasText;

/**
 * REAL-LLM behavioral <b>MATRIX</b> probe for the discovery suggestion core — a wide, data-driven map of
 * how the REAL pipeline (REAL OpenAI generation + REAL OpenAI embeddings + REAL pgvector) behaves across
 * ~90 hand-authored Spanish cases, run under ONE Spring context boot.
 *
 * <h2>Relationship to the sibling probes</h2>
 * <ul>
 *   <li>{@link RealLlmSuggestionBehaviorE2ETest} — a handful of in-process scenarios, each its own
 *       {@code @Test}, calling {@code suggest()} directly.</li>
 *   <li>{@link RealLlmStreamingWsSuggestionE2ETest} — the same behavior through the real {@code /ws/stt}
 *       WebSocket (proves the streaming plumbing).</li>
 *   <li><b>this</b> — the same in-process seam as the first sibling ({@code suggest()} called directly,
 *       NOT the WS: behavior is identical and this must be fast/cheap/reliable for ~90 cases), but
 *       widened into a parameterized matrix that emits one greppable {@code [MATRIX]} line per case and
 *       captures the RAW cosine of each produced draft to the seeded backlog — mapping exactly where the
 *       0.84 dedup bar catches vs. misses.</li>
 * </ul>
 *
 * <h2>Why the raw cosine matters</h2>
 * The recorded {@code Suggestion.similarity} is populated only when a NEW draft is actually downgraded to
 * UPDATE (≥ 0.84). To see where a case <em>lands</em> — including the ones that stay NEW just under the
 * bar — this test, for every case with a seeded backlog, re-embeds each produced NEW_STORY/UPDATE_STORY
 * draft via the REAL {@link EmbeddingPort} and computes cosine directly against each seeded story's
 * stored embedding, logging the actual number (never {@code null}).
 *
 * <h2>Probe, not a gate</h2>
 * The LLM is non-deterministic, so nearly every case is {@code OBSERVE} (logged, never fails the build) so
 * the whole matrix always completes and produces a full map. Hard assertions fire ONLY for the few
 * unambiguous invariants:
 * <ul>
 *   <li>{@code EXACT_DUP} — a verbatim-duplicated capability yields ≤ 1 suggestion;</li>
 *   <li>{@code MULTI_DISTINCT} — two genuinely-distinct capabilities yield ≥ 2 story drafts;</li>
 *   <li>every case — no two persisted stories exceed cosine ~0.97 (wrongly merged / minted duplicate).</li>
 * </ul>
 *
 * <h2>Wiring / skip / run</h2>
 * Real OpenAI generation + embeddings via the same property flips as the sibling probes; real pgvector
 * via {@link AbstractIntegrationTest}. Tagged {@code @Tag("llm")} and skipped (never failed) without a key
 * via {@link EnabledIfEnvironmentVariable} + an in-body {@code assumeTrue}. Runs under the existing
 * {@code llmTest} Gradle task. One OpenAI generation call per case (~90 total) plus the embeddings for
 * seeding + raw-cosine capture.
 * <pre>./gradlew llmTest --max-workers=1</pre>
 *
 * <h2>One context, per-case isolation</h2>
 * {@link TestInstance.Lifecycle#PER_CLASS} keeps a single instance (and thus a single provisioned tenant
 * org/schema) across all parameterized invocations, so the Spring context and the tenant are created
 * once. Each case seeds its OWN {@code Project}, so seeded backlogs never cross-contaminate and the
 * pairwise-merge invariant is evaluated per project.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("llm")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@TestPropertySource(properties = {
        // Activate the Spring AI OpenAI autoconfiguration so the OpenAiChatModel / OpenAiEmbeddingModel
        // beans the real adapters need are created.
        "spring.ai.model.chat=openai",
        "spring.ai.model.embedding=openai",
        // Route the two routers to their OpenAI adapters.
        "reqsai.ai.generation.provider=openai",
        "reqsai.ai.embedding.provider=openai",
        // 768-dim to match the pgvector schema.
        "spring.ai.openai.embedding.options.dimensions=768",
        // The key flows from OPENAI_API_KEY via application.yml — never read from a file here.
})
@DisplayName("Real-LLM MATRIX E2E: ~90-case discovery suggestion behavior map (OpenAI generation + OpenAI embeddings + pgvector)")
class RealLlmBehaviorMatrixE2ETest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    /** How close two persisted stories may be before we call them "wrongly merged" (tolerant ceiling). */
    private static final double MERGE_CEILING = 0.97;
    /** The dedup bar the server applies (discovery.realtime.dedup-similarity-threshold default). */
    private static final double DEDUP_BAR = 0.84;

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
    private boolean wiringVerified;

    // ── Expectations ─────────────────────────────────────────────────────────────────────────────────────

    /**
     * What we expect (and how strictly). All but {@code EXACT_DUP} and {@code MULTI_DISTINCT} are OBSERVE
     * (logged, never failing) because they depend on model judgement — the point is to MAP behavior.
     */
    private enum Expectation {
        /** Verbatim duplicate ⇒ ≤ 1 suggestion (HARD). */
        EXACT_DUP,
        /** ≥ 2 genuinely-distinct capabilities ⇒ ≥ 2 story drafts (HARD). */
        MULTI_DISTINCT,
        /** Paraphrase of a seeded story ⇒ dedup or UPDATE (OBSERVE — report raw cosine vs. bar). */
        DEDUP_OR_UPDATE,
        /** A new detail on a seeded story ⇒ UPDATE/EDGE targeting it (OBSERVE). */
        UPDATE,
        /** An exception of a seeded story ⇒ EDGE_CASE (OBSERVE). */
        EDGE_CASE,
        /** Ambiguous/underspecified ⇒ a CLARIFYING_QUESTION (OBSERVE). */
        CLARIFY,
        /** Garbage / mistranscription ⇒ ideally nothing coherent (OBSERVE). */
        GARBAGE,
        /** Below the char trigger without force ⇒ nothing yet (OBSERVE the cadence hold). */
        CADENCE_HOLD,
        /** Anything else worth mapping — pure OBSERVE. */
        OBSERVE
    }

    // ── A seed story spec + a case record ─────────────────────────────────────────────────────────────────

    /** A backlog story to seed (accepted + real-embedded) before the pass. */
    private record SeedStory(String title, String role, String action, String benefit) {}

    /**
     * One matrix case.
     *
     * @param id        stable, greppable id (e.g. {@code A1})
     * @param category  one-letter category (A..O)
     * @param seeds     backlog to seed (may be empty)
     * @param utterances one or more final transcript segments (Spanish)
     * @param force     whether to call {@code suggest(force=true)} (all but the cadence-hold cases)
     * @param expect    the expectation / assertion strictness
     */
    private record Case(String id, String category, List<SeedStory> seeds, List<String> utterances,
                        boolean force, Expectation expect) {
        static Case of(String id, String category, List<SeedStory> seeds, Expectation expect, String... utterances) {
            return new Case(id, category, seeds, List.of(utterances), true, expect);
        }
        static Case noForce(String id, String category, List<SeedStory> seeds, Expectation expect, String... utterances) {
            return new Case(id, category, seeds, List.of(utterances), false, expect);
        }
        String shortInput() {
            String joined = String.join(" | ", utterances);
            return joined.length() <= 90 ? joined : joined.substring(0, 87) + "...";
        }
        String seedTitles() {
            return seeds.isEmpty() ? "-" : seeds.stream().map(SeedStory::title).collect(Collectors.joining("; "));
        }
    }

    // ── Provision the tenant once (PER_CLASS instance) and prove real providers are wired ─────────────────

    @BeforeEach
    void provisionOnce() {
        Assumptions.assumeTrue(hasText(System.getenv("OPENAI_API_KEY")),
                "OPENAI_API_KEY not set — skipping real-LLM MATRIX E2E");
        if (this.schema != null) {
            return; // already provisioned for this PER_CLASS instance
        }
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

        boolean genAvailable = generationPort.isAvailable();
        boolean embedAvailable = embeddingPort.isAvailable();
        log("wiring", "generation.isAvailable=" + genAvailable + " (impl=" + generationPort.getClass().getSimpleName()
                + "); embedding.isAvailable=" + embedAvailable + " (impl=" + embeddingPort.getClass().getSimpleName() + ")");
        assertThat(genAvailable).as("real OpenAI generation adapter must be available with a key set").isTrue();
        assertThat(embedAvailable).as("real OpenAI embedding adapter must be available with a key set").isTrue();
        this.wiringVerified = true;
    }

    // ── The single parameterized entry point over the whole matrix ────────────────────────────────────────

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("matrix")
    @DisplayName("matrix case")
    void matrixCase(Case c) {
        assertThat(wiringVerified).as("wiring must be verified before running cases").isTrue();

        // Each case gets its own project (UNIQUE name derived from the case id) so seeded backlogs never
        // cross-contaminate and the per-case project insert does not collide on idx_projects_org_active_name.
        UUID projectId = seedProject(c.id());

        // Seed the backlog (accepted + real-embedded) and remember each seed's stored embedding + title so
        // we can compute the RAW cosine of produced drafts against them afterwards.
        List<SeededStory> seeded = new ArrayList<>();
        for (SeedStory s : c.seeds()) {
            seeded.add(seedIndexedStory(projectId, s));
        }

        UUID sessionId = seedRecordingSession(projectId, c.id() + " session",
                c.utterances().toArray(String[]::new));

        // Run the pass. Cadence-hold cases run WITHOUT force to observe the char-trigger hold; every other
        // case forces so we do not wait on the real-time cadence window.
        inTenantTx(() -> { realtimeSuggestion.suggest(sessionId, c.force()); return null; });
        List<Suggestion> produced = pending(sessionId);

        // Raw cosine of each produced story draft to each seeded story — the whole point of the matrix.
        RawCosine raw = rawTopCosineToSeed(produced, seeded);

        boolean converged = produced.isEmpty()
                || produced.stream().anyMatch(s ->
                        s.getType() == SuggestionType.UPDATE_STORY || s.getType() == SuggestionType.EDGE_CASE);

        Outcome outcome = evaluate(c, produced);

        // ── The one greppable, stable line per case ──────────────────────────────────────────────────────
        System.out.println(String.format(
                "[MATRIX][%s][%s] seeded=\"%s\" input=\"%s\" => produced=%s rawTopCosineToSeed=%s converged=%b expectation=%s outcome=%s",
                c.category(), c.id(), c.seedTitles(), c.shortInput(),
                describeCompact(produced), raw.render(), converged, c.expect(), outcome));

        // ── Hard invariants ONLY (everything else is OBSERVE and already logged) ───────────────────────────
        if (c.expect() == Expectation.EXACT_DUP) {
            assertThat(storyDrafts(produced))
                    .as("[%s] a verbatim-duplicated capability must not persist two near-identical story drafts", c.id())
                    .hasSizeLessThanOrEqualTo(1);
        }
        if (c.expect() == Expectation.MULTI_DISTINCT) {
            assertThat(storyDrafts(produced).size())
                    .as("[%s] genuinely distinct capabilities must yield >=2 story drafts (not wrongly merged)", c.id())
                    .isGreaterThanOrEqualTo(2);
        }
        // Universal safety net: no two persisted stories may be near-identical. Stories are only persisted
        // on accept, so in this suggestion-only pass this asserts against any seeded backlog rows — a
        // produced NEW draft is never persisted as a story here, so this mainly guards the seeds staying
        // distinct, but it is kept for parity with the sibling probes and future-proofing.
        assertNoStoriesWronglyMerged(projectId);
    }

    /** PASS/FAIL for the hard cases; OBSERVE for everything else. */
    private Outcome evaluate(Case c, List<Suggestion> produced) {
        return switch (c.expect()) {
            case EXACT_DUP -> storyDrafts(produced).size() <= 1 ? Outcome.PASS : Outcome.FAIL;
            case MULTI_DISTINCT -> storyDrafts(produced).size() >= 2 ? Outcome.PASS : Outcome.FAIL;
            default -> Outcome.OBSERVE;
        };
    }

    private enum Outcome { PASS, FAIL, OBSERVE }

    // ── The matrix: ~90 hand-authored Spanish cases across categories A..O ────────────────────────────────

    private static final SeedStory EXPORT_PDF =
            new SeedStory("Exportar reportes a PDF", "usuario", "exportar mis reportes a PDF",
                    "poder archivarlos y compartirlos con mi equipo");
    private static final SeedStory LOGIN =
            new SeedStory("Iniciar sesión", "usuario", "iniciar sesión con correo y contraseña",
                    "acceder a la plataforma");
    private static final SeedStory CHECKOUT =
            new SeedStory("Pagar el carrito", "cliente", "pagar los productos de mi carrito con tarjeta",
                    "completar mi compra");
    private static final SeedStory FILTER_TASKS =
            new SeedStory("Filtrar tareas por estado", "usuario",
                    "filtrar mis tareas por estado pendiente o completada", "encontrarlas más rápido");
    private static final SeedStory RESET_LINK =
            new SeedStory("Restablecer contraseña", "usuario",
                    "restablecer mi contraseña mediante un enlace enviado por correo", "recuperar el acceso");
    private static final SeedStory PROFILE_FORM =
            new SeedStory("Editar perfil", "usuario", "editar los datos de mi perfil",
                    "mantener mi información al día");
    private static final SeedStory PRODUCT_SEARCH =
            new SeedStory("Buscar productos", "cliente", "buscar productos por nombre",
                    "encontrar rápido lo que quiero comprar");

    static List<Case> matrix() {
        List<Case> m = new ArrayList<>();

        // ── A. Exact / near-exact duplicate (5) ──────────────────────────────────────────────────────────
        String expCap = "Como usuario quiero exportar mis reportes a PDF para archivarlos y compartirlos por correo "
                + "con mi equipo cada fin de mes sin depender de nadie más del área de sistemas.";
        m.add(Case.of("A1", "A", List.of(), Expectation.EXACT_DUP, expCap, expCap));
        m.add(Case.of("A2", "A", List.of(), Expectation.EXACT_DUP,
                expCap, "Eh, o sea, este, " + expCap));
        m.add(Case.of("A3", "A", List.of(), Expectation.EXACT_DUP,
                "Quiero poder exportar mis reportes mensuales a PDF para archivarlos y enviarlos por correo a todo mi equipo.",
                "Quiero poder exportar mis reportes mensuales a PDF para archivarlos y enviarlos por correo a todo mi equipo."));
        m.add(Case.of("A4", "A", List.of(), Expectation.EXACT_DUP,
                "Necesito EXPORTAR los reportes a PDF, para archivarlos y compartirlos con el equipo cada mes.",
                "necesito exportar los reportes a pdf para archivarlos y compartirlos con el equipo cada mes"));
        m.add(Case.of("A5", "A", List.of(EXPORT_PDF), Expectation.DEDUP_OR_UPDATE,
                "Como usuario quiero exportar mis reportes a PDF para archivarlos y compartirlos con mi equipo, "
                        + "tal cual lo que ya habíamos acordado antes."));

        // ── B. Mild paraphrase (5) ───────────────────────────────────────────────────────────────────────
        m.add(Case.of("B1", "B", List.of(EXPORT_PDF), Expectation.DEDUP_OR_UPDATE,
                "Para archivarlos y compartirlos con mi equipo, quiero exportar mis reportes a PDF."));
        m.add(Case.of("B2", "B", List.of(EXPORT_PDF), Expectation.DEDUP_OR_UPDATE,
                "Los reportes deben poder ser exportados a PDF por el usuario para archivarlos y compartirlos."));
        m.add(Case.of("B3", "B", List.of(EXPORT_PDF), Expectation.DEDUP_OR_UPDATE,
                "Quiero guardar mis reportes en PDF para archivarlos y compartirlos con el equipo."));
        m.add(Case.of("B4", "B", List.of(LOGIN), Expectation.DEDUP_OR_UPDATE,
                "El usuario ingresa a la plataforma autenticándose con su correo y su contraseña."));
        m.add(Case.of("B5", "B", List.of(LOGIN), Expectation.DEDUP_OR_UPDATE,
                "Necesito que la persona pueda entrar al sistema poniendo su correo electrónico y su clave, "
                        + "para así acceder a la plataforma y usar todas sus funciones sin problema alguno."));

        // ── C. Synonym-heavy paraphrase — the hard case (8) ──────────────────────────────────────────────
        m.add(Case.of("C1", "C", List.of(EXPORT_PDF), Expectation.DEDUP_OR_UPDATE,
                "Necesito descargar mis informes en formato PDF para guardarlos y remitirlos a mi equipo de trabajo."));
        m.add(Case.of("C2", "C", List.of(EXPORT_PDF), Expectation.DEDUP_OR_UPDATE,
                "Quisiera bajar los documentos de reportería como archivos PDF y así conservarlos y difundirlos al área."));
        m.add(Case.of("C3", "C", List.of(LOGIN), Expectation.DEDUP_OR_UPDATE,
                "El colaborador se autentica con sus credenciales de correo y clave para ingresar al aplicativo."));
        m.add(Case.of("C4", "C", List.of(FILTER_TASKS), Expectation.DEDUP_OR_UPDATE,
                "Deseo depurar mi listado de pendientes según su condición, ya sea abiertos o cerrados, para hallarlos veloz."));
        m.add(Case.of("C5", "C", List.of(PRODUCT_SEARCH), Expectation.DEDUP_OR_UPDATE,
                "El comprador realiza una búsqueda de artículos por su denominación para dar rápido con lo que desea adquirir."));
        // Regional wording variants (es-PE / es-419 / es-ES) of the same capability.
        m.add(Case.of("C6", "C", List.of(CHECKOUT), Expectation.DEDUP_OR_UPDATE,
                "El cliente cancela los productos de su carrito con su tarjeta para concretar la compra.")); // es-PE 'cancelar'=pay
        m.add(Case.of("C7", "C", List.of(CHECKOUT), Expectation.DEDUP_OR_UPDATE,
                "El usuario abona el importe de su cesta con tarjeta bancaria para finalizar el pedido.")); // es-ES 'cesta'/'abonar'
        m.add(Case.of("C8", "C", List.of(PROFILE_FORM), Expectation.DEDUP_OR_UPDATE,
                "Quiero modificar los datos de mi cuenta para tener mi información actualizada."));

        // ── D. Distinct-but-related / false-positive guard (8) ───────────────────────────────────────────
        m.add(Case.of("D1", "D", List.of(), Expectation.MULTI_DISTINCT,
                "El usuario quiere iniciar sesión con su correo y contraseña para entrar a la plataforma. "
                        + "Aparte, y esto es distinto, quiere registrarse creando una cuenta nueva con sus datos personales."));
        m.add(Case.of("D2", "D", List.of(), Expectation.MULTI_DISTINCT,
                "El usuario quiere iniciar sesión con su correo y contraseña. Y aparte, algo diferente, quiere poder "
                        + "restablecer su contraseña con un enlace que le llegue por correo cuando la olvide."));
        m.add(Case.of("D3", "D", List.of(), Expectation.MULTI_DISTINCT,
                "Quiero exportar mis reportes a PDF para archivarlos. Y por otro lado, algo distinto, quiero exportar "
                        + "los mismos reportes a Excel para poder hacer cálculos con las cifras."));
        m.add(Case.of("D4", "D", List.of(), Expectation.MULTI_DISTINCT,
                "Quiero recibir notificaciones por correo electrónico cuando haya novedades. Y aparte, cosa distinta, "
                        + "quiero recibir notificaciones push en el celular para enterarme al instante."));
        m.add(Case.of("D5", "D", List.of(), Expectation.MULTI_DISTINCT,
                "El usuario quiere buscar productos escribiendo su nombre. Y separadamente, algo diferente, quiere filtrar "
                        + "los productos por categoría y por rango de precio para acotar los resultados."));
        m.add(Case.of("D6", "D", List.of(), Expectation.MULTI_DISTINCT,
                "Quiero ver la lista de todos mis pedidos en una tabla. Y aparte, distinto, quiero abrir el detalle de "
                        + "un pedido específico para ver sus productos, montos y estado de envío."));
        m.add(Case.of("D7", "D", List.of(), Expectation.MULTI_DISTINCT,
                "El administrador quiere editar los datos de un usuario existente. Y por otro lado, cosa distinta, quiere "
                        + "eliminar por completo la cuenta de un usuario del sistema."));
        m.add(Case.of("D8", "D", List.of(), Expectation.MULTI_DISTINCT,
                "Quiero crear una nueva tarea con su título y descripción. Y aparte, algo diferente, quiero actualizar "
                        + "el estado de una tarea que ya existe para moverla de pendiente a en progreso."));

        // ── E. UPDATE — add a detail to a seeded accepted story (8) ──────────────────────────────────────
        m.add(Case.of("E1", "E", List.of(LOGIN), Expectation.UPDATE,
                "Sobre el inicio de sesión que ya tenemos: además quiero que soporte verificación en dos pasos con un "
                        + "código enviado al celular, o sea el mismo login pero con 2FA para más seguridad."));
        m.add(Case.of("E2", "E", List.of(LOGIN), Expectation.UPDATE,
                "Sobre el inicio de sesión: además debe cumplir que si el usuario deja el correo o la contraseña vacíos, "
                        + "el sistema muestre un mensaje de error indicando qué campo falta, como criterio de aceptación."));
        m.add(Case.of("E3", "E", List.of(LOGIN), Expectation.UPDATE,
                "Sobre el inicio de sesión: además hay que limitar los intentos, tras cinco intentos fallidos se bloquea "
                        + "la cuenta por quince minutos para evitar ataques de fuerza bruta."));
        m.add(Case.of("E4", "E", List.of(PROFILE_FORM), Expectation.UPDATE,
                "Sobre editar el perfil: además del nombre y correo, quiero que el formulario incluya un campo de teléfono "
                        + "de contacto para completar los datos del usuario."));
        m.add(Case.of("E5", "E", List.of(PROFILE_FORM), Expectation.UPDATE,
                "Sobre editar el perfil: además el correo que se ingrese debe validarse con un formato válido, si no "
                        + "cumple no se guarda y se avisa al usuario."));
        m.add(Case.of("E6", "E", List.of(FILTER_TASKS), Expectation.UPDATE,
                "Sobre el filtro de tareas por estado: además quiero poder ordenar las tareas por fecha de vencimiento, "
                        + "ascendente o descendente, para priorizar mejor."));
        m.add(Case.of("E7", "E", List.of(LOGIN), Expectation.UPDATE,
                "Sobre el inicio de sesión: además hay que distinguir roles, si entra un administrador ve el panel de "
                        + "gestión y si entra un usuario normal ve solo su tablero."));
        m.add(Case.of("E8", "E", List.of(PRODUCT_SEARCH), Expectation.UPDATE,
                "Sobre la búsqueda de productos: además quiero un filtro para mostrar solo los productos que están en stock "
                        + "y ocultar los agotados."));

        // ── F. EDGE_CASE — exceptions of a seeded story (8) ──────────────────────────────────────────────
        m.add(Case.of("F1", "F", List.of(CHECKOUT), Expectation.EDGE_CASE,
                "Sobre el pago del carrito: si la tarjeta es inválida o está vencida, el sistema debe rechazar el pago y "
                        + "mostrar un mensaje claro sin cobrar nada."));
        m.add(Case.of("F2", "F", List.of(PRODUCT_SEARCH), Expectation.EDGE_CASE,
                "Sobre la búsqueda de productos: si no hay ningún resultado que coincida, mostrar un mensaje de 'sin "
                        + "resultados' con una sugerencia para ampliar la búsqueda."));
        m.add(Case.of("F3", "F", List.of(RESET_LINK), Expectation.EDGE_CASE,
                "Sobre restablecer la contraseña: si el enlace de recuperación ya expiró, debe mostrarse un aviso y ofrecer "
                        + "generar un enlace nuevo."));
        m.add(Case.of("F4", "F", List.of(CHECKOUT), Expectation.EDGE_CASE,
                "Sobre el pago: si el monto del carrito supera el límite permitido por la pasarela, no se procesa y se "
                        + "informa al cliente el máximo permitido."));
        m.add(Case.of("F5", "F", List.of(EXPORT_PDF), Expectation.EDGE_CASE,
                "Sobre exportar a PDF: si el usuario está sin conexión al momento de exportar, se debe encolar la exportación "
                        + "y avisarle que se completará al reconectarse."));
        m.add(Case.of("F6", "F", List.of(PROFILE_FORM), Expectation.EDGE_CASE,
                "Sobre editar el perfil: si dos personas editan el mismo perfil a la vez, el segundo guardado debe advertir "
                        + "que los datos cambiaron y no pisar los del primero."));
        m.add(Case.of("F7", "F", List.of(EXPORT_PDF), Expectation.EDGE_CASE,
                "Sobre exportar reportes: si el usuario no tiene permiso para ver ese reporte, la exportación debe negarse "
                        + "con un error de permiso denegado."));
        m.add(Case.of("F8", "F", List.of(PRODUCT_SEARCH), Expectation.EDGE_CASE,
                "Sobre la búsqueda: si la consulta tarda demasiado y se agota el tiempo de espera, mostrar un mensaje de "
                        + "tiempo agotado e invitar a reintentar."));

        // ── G. Clarifying question (6) ───────────────────────────────────────────────────────────────────
        m.add(Case.of("G1", "G", List.of(), Expectation.CLARIFY,
                "Queremos que el sistema sea rápido y que maneje bien la seguridad, ya saben, que esté todo bien protegido. "
                        + "Eso es lo importante por ahora."));
        m.add(Case.of("G2", "G", List.of(), Expectation.CLARIFY,
                "Necesitamos poder gestionar usuarios en la plataforma. Con eso deberíamos estar bien por el momento."));
        m.add(Case.of("G3", "G", List.of(), Expectation.CLARIFY,
                "Queremos que los reportes se generen automáticamente cada día, pero también que el usuario pueda decidir "
                        + "cuándo generarlos manualmente; que sea automático y manual a la vez, como se pueda."));
        m.add(Case.of("G4", "G", List.of(), Expectation.CLARIFY,
                "Habría que mejorar la parte de reportes, hacerla más completa y útil para todos. Ya luego afinamos el detalle."));
        m.add(Case.of("G5", "G", List.of(), Expectation.CLARIFY,
                "Se debe poder aprobar las solicitudes desde el sistema. No tengo claro quién las aprueba todavía."));
        m.add(Case.of("G6", "G", List.of(), Expectation.CLARIFY,
                "Queremos una función para exportar los datos, pero no está claro en qué formato ni con qué campos exactamente."));

        // ── H. Cadence / timing (5) — the only cases that exercise the non-force path ────────────────────
        // H1: a very short utterance WITHOUT force stays below the 180-char trigger on the first pass ⇒ hold.
        m.add(Case.noForce("H1", "H", List.of(), Expectation.CADENCE_HOLD,
                "Quiero un botón para cerrar sesión."));
        // H2: the SAME short utterance WITH force generates (proves force releases the tail).
        m.add(Case.of("H2", "H", List.of(), Expectation.OBSERVE,
                "Quiero un botón para cerrar sesión."));
        // H3: a medium (~180-char) utterance WITHOUT force should cross the char trigger on its own.
        m.add(Case.noForce("H3", "H", List.of(), Expectation.OBSERVE,
                "El usuario quiere poder cambiar el idioma de la interfaz entre español e inglés desde la configuración "
                        + "de su cuenta, y que la elección quede guardada para las próximas veces que ingrese al sistema."));
        // H4: a long utterance WITHOUT force clearly crosses the trigger.
        m.add(Case.noForce("H4", "H", List.of(), Expectation.OBSERVE,
                "El usuario quiere administrar sus notificaciones desde un panel donde pueda activar o desactivar cada tipo "
                        + "de aviso, elegir el canal por correo o push, definir un horario de no molestar y guardar todas esas "
                        + "preferencias en su perfil para que se respeten en cada sesión posterior sin tener que reconfigurar."));
        // H5: a burst of several short segments WITHOUT force — together they cross the trigger.
        m.add(Case.noForce("H5", "H", List.of(), Expectation.OBSERVE,
                "Quiero exportar a PDF.", "También quiero exportar a Excel.", "Y quiero exportar a CSV.",
                "Además quiero programar la exportación cada semana.", "Y recibirla por correo automáticamente."));

        // ── I. Multi-story single transcript (5) ─────────────────────────────────────────────────────────
        m.add(Case.of("I1", "I", List.of(), Expectation.MULTI_DISTINCT,
                "El usuario quiere iniciar sesión con correo y contraseña. Y por separado, algo distinto, quiere buscar "
                        + "productos por nombre para encontrarlos rápido."));
        m.add(Case.of("I2", "I", List.of(), Expectation.MULTI_DISTINCT,
                "Quiero iniciar sesión con correo y contraseña. Aparte quiero exportar reportes a PDF. Y además, cosa "
                        + "distinta, quiero buscar productos por su nombre en el catálogo."));
        m.add(Case.of("I3", "I", List.of(), Expectation.MULTI_DISTINCT,
                "El usuario quiere iniciar sesión. Aparte quiere restablecer su contraseña por correo. Aparte quiere editar "
                        + "su perfil. Y aparte, distinto a todo lo anterior, quiere ver el historial de sus pedidos."));
        m.add(Case.of("I4", "I", List.of(), Expectation.MULTI_DISTINCT,
                "Quiero crear tareas nuevas con título y descripción. Y por otro lado, algo distinto, quiero filtrar las "
                        + "tareas por estado para ver solo las pendientes."));
        m.add(Case.of("I5", "I", List.of(LOGIN), Expectation.OBSERVE,
                "Sobre el inicio de sesión: además quiero que soporte 2FA. Y aparte, algo totalmente distinto, quiero un "
                        + "buscador de productos por nombre para el catálogo público."));

        // ── J. Mistranscription / garbage (5) ────────────────────────────────────────────────────────────
        m.add(Case.of("J1", "J", List.of(), Expectation.GARBAGE,
                "asdf qwer brrr flurbo nixmato greldun por el sistema traqueado del florbo con manzanas cuánticas."));
        m.add(Case.of("J2", "J", List.of(), Expectation.GARBAGE,
                "Quiero exportar los reportes a and then the quick brown fox jumps over eh a PDF para el equipo."));
        m.add(Case.of("J3", "J", List.of(), Expectation.GARBAGE,
                "Eh, este, o sea, ya, este, ajá, mmm, ya pues, este, o sea, ¿no?, ya."));
        m.add(Case.of("J4", "J", List.of(), Expectation.GARBAGE,
                "12345 67890 ID-9981 REF-0042 0x3F 3.1416 99999 SKU-77."));
        m.add(Case.of("J5", "J", List.of(), Expectation.GARBAGE,
                "Entonces lo que necesitamos es que el usuario pueda, este, cuando entre al sistema y quiera, o sea, para "
                        + "que"));

        // ── K. Language (4) ──────────────────────────────────────────────────────────────────────────────
        m.add(Case.of("K1", "K", List.of(), Expectation.OBSERVE,
                "The user wants to log in with email and password to access the platform, and also reset the password via "
                        + "an email link when it is forgotten."));
        m.add(Case.of("K2", "K", List.of(), Expectation.OBSERVE,
                "El usuario quiere hacer login con su email and password, y también poder ver el order history de sus "
                        + "compras anteriores en la plataforma."));
        m.add(Case.of("K3", "K", List.of(), Expectation.OBSERVE,
                "El usuario desea autenticarse con su correo electrónico y su contraseña para acceder al ordenador y "
                        + "gestionar sus ficheros, vale, tal como lo haría en España."));
        m.add(Case.of("K4", "K", List.of(), Expectation.OBSERVE,
                "O usuário quer entrar no sistema com e-mail e senha para acessar a plataforma e ver o histórico de pedidos."));

        // ── L. Incremental refinement across passes (5) — multi-utterance in one forced pass ─────────────
        m.add(Case.of("L1", "L", List.of(), Expectation.OBSERVE,
                "El usuario quiere subir una foto de perfil para personalizar su cuenta.",
                "Sobre la foto de perfil: además debe poder recortarla antes de guardarla.",
                "Sobre la foto de perfil: además solo se aceptan imágenes de menos de 5 megabytes."));
        m.add(Case.of("L2", "L", List.of(), Expectation.OBSERVE,
                "Quiero un buscador de productos por nombre.",
                "Sobre el buscador: refinándolo, además debe buscar por nombre y también por categoría."));
        m.add(Case.of("L3", "L", List.of(), Expectation.OBSERVE,
                "Quiero recibir un reporte diario por correo cada mañana.",
                "Corrijo lo del reporte diario: mejor que sea semanal, no diario, los lunes a primera hora."));
        m.add(Case.of("L4", "L", List.of(), Expectation.OBSERVE,
                "El usuario quiere iniciar sesión con correo y contraseña.",
                "Sobre ese inicio de sesión: extendiéndolo, además debe permitir entrar con huella dactilar en el celular."));
        m.add(Case.of("L5", "L", List.of(), Expectation.OBSERVE,
                "Quiero exportar todos los reportes de todos los meses a PDF.",
                "Acotando lo de exportar: en realidad solo hace falta exportar el reporte del mes en curso, no todos."));

        // ── M. Contradiction / negation (4) ──────────────────────────────────────────────────────────────
        m.add(Case.of("M1", "M", List.of(), Expectation.OBSERVE,
                "Antes pedimos exportar los reportes a Excel, pero eso ya no aplica; olvídenlo, no lo vamos a necesitar."));
        m.add(Case.of("M2", "M", List.of(), Expectation.OBSERVE,
                "No queremos que el sistema envíe notificaciones por SMS bajo ninguna circunstancia; nada de mensajes de texto."));
        m.add(Case.of("M3", "M", List.of(), Expectation.OBSERVE,
                "Quiero que el usuario pueda pagar con tarjeta de crédito. Pensándolo mejor, no, mejor que pague solo con "
                        + "Yape y no habilitemos la tarjeta por ahora."));
        m.add(Case.of("M4", "M", List.of(LOGIN), Expectation.OBSERVE,
                "Sobre el inicio de sesión: quitemos el criterio de bloquear la cuenta tras cinco intentos fallidos, ya no "
                        + "lo queremos, que no se bloquee nunca."));

        // ── N. Long transcript, 6+ capabilities (3) ──────────────────────────────────────────────────────
        m.add(Case.of("N1", "N", List.of(), Expectation.OBSERVE,
                "Repasemos todo: el usuario quiere iniciar sesión con correo y contraseña; quiere registrarse creando una "
                        + "cuenta; quiere restablecer su contraseña por correo; quiere buscar productos por nombre; quiere "
                        + "filtrar productos por categoría; quiere agregar productos al carrito; quiere pagar el carrito con "
                        + "tarjeta; y quiere ver el historial de sus pedidos anteriores."));
        m.add(Case.of("N2", "N", List.of(), Expectation.OBSERVE,
                "En el panel de administración: crear usuarios; editar usuarios; eliminar usuarios; asignar roles a los "
                        + "usuarios; ver un log de auditoría de acciones; exportar la lista de usuarios a CSV; y suspender "
                        + "temporalmente una cuenta sin borrarla."));
        m.add(Case.of("N3", "N", List.of(), Expectation.OBSERVE,
                "Para la app móvil queremos: notificaciones push configurables; modo oscuro; soporte multi-idioma español e "
                        + "inglés; inicio de sesión biométrico; sincronización sin conexión; y compartir contenido por redes "
                        + "sociales desde la propia aplicación."));

        // ── O. Threshold-boundary pairs (5) — crafted to sit around 0.84; rely on raw-cosine logging ─────
        m.add(Case.of("O1", "O", List.of(EXPORT_PDF), Expectation.DEDUP_OR_UPDATE,
                "Quiero exportar mis reportes a PDF para archivarlos y compartirlos con mi equipo.")); // ~ near/above bar
        m.add(Case.of("O2", "O", List.of(EXPORT_PDF), Expectation.DEDUP_OR_UPDATE,
                "Quiero generar un tablero interactivo de indicadores en pantalla, distinto a exportar archivos.")); // below bar
        m.add(Case.of("O3", "O", List.of(FILTER_TASKS), Expectation.DEDUP_OR_UPDATE,
                "Deseo filtrar mis tareas por su estado, pendientes o completadas, para hallarlas más rápido.")); // above bar
        m.add(Case.of("O4", "O", List.of(FILTER_TASKS), Expectation.DEDUP_OR_UPDATE,
                "Estaría bueno ver mis tareas separando las que ya terminé de las que aún no, para ubicarlas rapidísimo.")); // near bar
        m.add(Case.of("O5", "O", List.of(LOGIN), Expectation.DEDUP_OR_UPDATE,
                "Quiero cerrar sesión de forma segura desde cualquier pantalla, distinto a iniciar sesión.")); // below bar

        return m;
    }

    // ── raw-cosine capture ────────────────────────────────────────────────────────────────────────────────

    /** A seeded story with its stored embedding, kept so drafts can be scored against it after the pass. */
    private record SeededStory(UUID id, String title, float[] embedding) {}

    /** The top raw cosine of any produced story draft to any seeded story, plus a per-seed breakdown. */
    private record RawCosine(Double top, String breakdown) {
        String render() {
            if (top == null) return "n/a" + (breakdown.isEmpty() ? "" : " " + breakdown);
            return String.format("%.4f", top) + (breakdown.isEmpty() ? "" : " " + breakdown);
        }
    }

    /**
     * For every produced NEW_STORY/UPDATE_STORY/EDGE_CASE draft, embeds its canonical candidate text via the
     * REAL embedder and computes cosine to every seeded story's stored embedding. Returns the single top
     * cosine (draft × seed) and a compact per-seed breakdown, so the report shows the ACTUAL number vs the
     * 0.84 bar — never null when a backlog was seeded and at least one story draft was produced.
     */
    private RawCosine rawTopCosineToSeed(List<Suggestion> produced, List<SeededStory> seeded) {
        if (seeded.isEmpty()) {
            return new RawCosine(null, "(no seed)");
        }
        List<Suggestion> drafts = produced.stream()
                .filter(s -> s.getType() != SuggestionType.CLARIFYING_QUESTION && s.getDraftTitle() != null)
                .toList();
        if (drafts.isEmpty()) {
            return new RawCosine(null, "(no story draft produced; bar=" + DEDUP_BAR + ")");
        }
        Double top = null;
        StringBuilder bd = new StringBuilder("[");
        for (Suggestion d : drafts) {
            float[] draftEmb = inTenantTx(() -> embeddingPort.embed(candidateText(d)));
            for (SeededStory seed : seeded) {
                double sim = cosine(draftEmb, seed.embedding());
                if (top == null || sim > top) top = sim;
                bd.append(String.format("(%s->%s)=%.4f ",
                        abbreviate(d.getDraftTitle()), abbreviate(seed.title()), sim));
            }
        }
        bd.append("bar=").append(DEDUP_BAR).append("]");
        return new RawCosine(top, bd.toString());
    }

    /** Canonical draft text fed to the embedder — mirrors SuggestionCreationService.candidateText. */
    private static String candidateText(Suggestion s) {
        return "%s. As %s, I want to %s, so that %s.".formatted(
                s.getDraftTitle(), s.getDraftRole(), s.getDraftAction(), s.getDraftBenefit());
    }

    // ── seeding (tenant-scoped, via repositories under TenantContext) ─────────────────────────────────────

    /**
     * Seeds one Project per case. The name MUST be unique per case: the tenant schema carries the partial
     * unique index {@code idx_projects_org_active_name} on {@code (organization_id, lower(name)) WHERE
     * status = 'ACTIVE'} (V7), and every case runs in the SAME provisioned org/schema — a shared constant
     * name made case #1 succeed and every later case fail its {@code projects} insert with a
     * {@code DataIntegrityViolationException}. Deriving the name from the case id keeps each case's project
     * (and therefore its seeded backlog and its dedup surface) isolated from every other case.
     */
    private UUID seedProject(String caseId) {
        return inTenantTx(() -> {
            TechnicalProfile profile = new TechnicalProfile(
                    List.of("Java"), List.of("Spring Boot"), List.of("Web"), List.of("PostgreSQL"),
                    "Clean Architecture", "SaaS");
            Project project = new Project(orgId, "Matrix " + caseId + " project", "seed", profile, UUID.fromString(USER_ID));
            return projects.save(project).getId();
        });
    }

    private UUID seedRecordingSession(UUID projectId, String name, String... utterances) {
        return inTenantTx(() -> {
            DiscoverySession session = new DiscoverySession(projectId, name,
                    com.kntro.reqsai.shared.domain.valueobjects.LanguageCode.of("es-PE"));
            session.startRecording(Instant.now());
            DiscoverySession saved = sessionsRepo.save(session);
            int seq = 1;
            for (String text : utterances) {
                long start = seq * 1000L;
                segments.save(new TranscriptSegment(saved.getId(), seq, "A", text, start, start + 1000, true));
                seq++;
            }
            return saved.getId();
        });
    }

    /** Seeds an ACCEPTED + INDEXED story (real embedding) and returns it with its stored embedding. */
    private SeededStory seedIndexedStory(UUID projectId, SeedStory s) {
        return inTenantTx(() -> {
            UserStory story = new UserStory(projectId, s.title(), s.role(), s.action(), s.benefit(), Priority.HIGH, 3);
            float[] embedding = embeddingPort.embed(story.toCanonicalText());
            story.assignEmbedding(embedding);
            UUID id = stories.save(story).getId();
            return new SeededStory(id, s.title(), embedding);
        });
    }

    // ── reads ─────────────────────────────────────────────────────────────────────────────────────────────

    private List<Suggestion> pending(UUID sessionId) {
        return inTenantTx(() -> suggestions.findAllBySessionIdAndStatus(sessionId, SuggestionStatus.PENDING));
    }

    private static List<Suggestion> storyDrafts(List<Suggestion> produced) {
        return produced.stream()
                .filter(s -> s.getType() == SuggestionType.NEW_STORY
                        || s.getType() == SuggestionType.UPDATE_STORY
                        || s.getType() == SuggestionType.EDGE_CASE)
                .toList();
    }

    /**
     * Guards the one story-level invariant that holds regardless of LLM wording: no two persisted stories
     * of the project sit above {@link #MERGE_CEILING}. In this suggestion-only pass the only persisted
     * stories are the seeds, so this asserts the seeded backlog stays distinct.
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

    /** Compact, parseable rendering of a suggestion list for the {@code produced=} field. */
    private static String describeCompact(List<Suggestion> list) {
        if (list.isEmpty()) return "[]";
        return list.stream()
                .map(s -> s.getType() + ":'" + (s.getType() == SuggestionType.CLARIFYING_QUESTION
                        ? s.getQuestion() : s.getDraftTitle()) + "'"
                        + (s.getSimilarity() != null ? "@" + String.format("%.3f", s.getSimilarity()) : "")
                        + (s.getTargetStoryId() != null ? "->" + shortId(s.getTargetStoryId()) : ""))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String abbreviate(String value) {
        if (value == null) return "?";
        String v = value.strip();
        return v.length() <= 24 ? v : v.substring(0, 21) + "...";
    }

    private static String shortId(UUID id) {
        return id == null ? "null" : id.toString().substring(0, 8);
    }

    private static void log(String label, String message) {
        System.out.println("[MATRIX][" + label + "] " + message);
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
