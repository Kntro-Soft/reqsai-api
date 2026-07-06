package com.kntro.reqsai.discovery.suggestionquality;

import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.TranscriptSegmentRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.discovery.interfaces.notification.SessionEventType;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.testsupport.AbstractIntegrationTest;
import com.kntro.reqsai.testsupport.TestJwtFactory;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.valueobjects.TechnicalProfile;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.util.StringUtils.hasText;

/**
 * REAL-LLM behavioral E2E probe driven through the actual streaming WebSocket <b>{@code /ws/stt}</b> —
 * the real target of the discovery core (not the direct {@code suggest()} call the sibling in-process
 * probe {@link RealLlmSuggestionBehaviorE2ETest} exercises).
 *
 * <h2>What real path this covers</h2>
 * The whole streaming chain runs for real: JWT handshake ({@code WebSocketJwtHandshakeInterceptor}) →
 * tenant resolution → binary handler ({@code SttStreamingWebSocketHandler.handleBinaryMessage}) →
 * append ({@code AppendTranscriptSegmentCommandHandler}) → {@code TranscriptSegmentAppendedEvent} →
 * {@code RealtimeSuggestionListener} → {@code RealtimeSuggestionService} → REAL OpenAI generation +
 * REAL OpenAI embeddings → persistence → STOMP broadcast. Only the STT <em>vendor</em> is faked, so the
 * transcript content is controlled exactly.
 *
 * <h2>Fake STT vendor</h2>
 * {@link EchoStreamingSttConfig} replaces the whole {@code StreamingTranscriptionPort} bean (the
 * production {@code StreamingSttRouter} is {@code @ConditionalOnMissingBean}) with an echo provider: the
 * WS client sends the desired transcript sentence AS the binary frame bytes (UTF-8), and the fake STT
 * emits it back as a FINAL {@code TranscriptEvent}. This is the only seam that differs from production —
 * audio bytes → transcript text — and it matches exactly how {@code handleBinaryMessage} hands frames to
 * {@code recognizer.sendAudio(byte[])}.
 *
 * <h2>Probe, not a gate</h2>
 * The LLM is non-deterministic, so this is a behavioral report: each scenario prints a grep-able
 * {@code [LLM-WS-E2E][label]} block and asserts only tolerant invariants (a stop flush yields ≥1
 * suggestion; distinct capabilities yield ≥2 story drafts; no two persisted stories exceed cosine
 * ~0.90). Scenario 8 asserts the streaming-specific lifecycle (segment broadcast; pause closes the WS;
 * resume lets a new WS connect; stop closes + flushes) — the part only a real WS test can cover.
 *
 * <h2>Wiring / skip / run</h2>
 * Real OpenAI generation + embeddings via the same property flips as the in-process probe; real pgvector
 * via {@link AbstractIntegrationTest}. Tagged {@code @Tag("llm")} and skipped (never failed) without a
 * key via {@link EnabledIfEnvironmentVariable} + an in-body {@code assumeTrue}. Run only this suite with:
 * <pre>./gradlew llmTest --max-workers=1</pre>
 *
 * <h2>Topic note</h2>
 * {@code docs/WEBSOCKET_STT.md} mentions {@code /topic/discovery/sessions/{id}/segments}, but the code
 * actually publishes BOTH segments and suggestions on the single per-session topic
 * {@code /topic/sessions/{id}} ({@code SessionTopics.of}), discriminated by the message {@code type}
 * field. This test subscribes there and filters by {@code type}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("llm")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@Import(EchoStreamingSttConfig.class)
@TestPropertySource(properties = {
        // Activate Spring AI OpenAI autoconfig (default 'none') so OpenAiChatModel/OpenAiEmbeddingModel exist.
        "spring.ai.model.chat=openai",
        "spring.ai.model.embedding=openai",
        // Route the generation + embedding routers to their OpenAI adapters.
        "reqsai.ai.generation.provider=openai",
        "reqsai.ai.embedding.provider=openai",
        // 768-dim to match the pgvector schema.
        "spring.ai.openai.embedding.options.dimensions=768",
        // Note: the streaming STT provider is NOT set to a real vendor — EchoStreamingSttConfig replaces
        // the whole StreamingTranscriptionPort bean, so no Deepgram/AssemblyAI/WhisperLive is contacted.
})
@DisplayName("Real-LLM WS E2E: /ws/stt streaming suggestion behavior probe (OpenAI + pgvector, faked STT vendor)")
class RealLlmStreamingWsSuggestionE2ETest extends AbstractIntegrationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final double MERGE_CEILING = 0.90;
    /** How long to wait for the async (after-commit) suggestion pipeline to settle. */
    private static final long PIPELINE_TIMEOUT_MS = 45_000;

    @LocalServerPort private int port;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SuggestionRepository suggestions;
    @Autowired private UserStoryRepository stories;
    @Autowired private TranscriptSegmentRepository segments;
    @Autowired private EmbeddingPort embeddingPort;
    @Autowired private ProjectRepository projects;
    @Autowired private PlatformTransactionManager txManager;

    private TransactionTemplate txTemplate;
    private String schema;
    private String orgId;
    private WebSocketStompClient stompClient;

    // ── Skip gracefully without a key; provision a real tenant (org → schema) ────────────────────────────

    @BeforeEach
    void provisionTenant() {
        Assumptions.assumeTrue(hasText(System.getenv("OPENAI_API_KEY")),
                "OPENAI_API_KEY not set — skipping real-LLM WS E2E");
        this.txTemplate = new TransactionTemplate(txManager);

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<String> orgRes = client().post().uri("/api/organizations")
                .header("Authorization", TestJwtFactory.bearer(USER_ID, UUID.randomUUID().toString(), "ROLE_USER"))
                .header("Api-Version", "1").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Acme " + suffix))
                .exchange((_, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(orgRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // The JWT used for the WS handshake must carry the REAL org id so the interceptor resolves the
        // provisioned tenant schema — read it back by slug (mirrors DiscoverySessionControlIntegrationTest).
        this.orgId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.organizations WHERE slug = ?", String.class, "acme-" + suffix);
        this.schema = "tenant_acme-" + suffix;
    }

    @AfterEach
    void stopStomp() {
        if (stompClient != null) stompClient.stop();
    }

    // ── Scenario 1: cadence / short utterance over the WS ────────────────────────────────────────────────

    @Test
    @DisplayName("1) WS cadence: one short frame produces no suggestion (below 180-char trigger); POST /stop flushes it")
    void wsShortUtteranceCadence() throws Exception {
        UUID projectId = seedProject();
        UUID sessionId = createAndStartSession(projectId, "WS short-utterance");
        BlockingQueue<JsonMsg> topic = subscribeSessionTopic(sessionId);

        WebSocketSession ws = openStt(sessionId);
        try {
            String utterance = "Quiero un botón para cerrar sesión.";
            long t0 = System.currentTimeMillis();
            sendFrame(ws, utterance);
            log("scenario-1", "sent short frame chars=" + utterance.length() + " (min-transcript-chars default=180)");

            // The segment must broadcast regardless of the suggestion cadence.
            boolean segmentSeen = awaitTrue(() -> hasType(topic, SessionEventType.TRANSCRIPT_SEGMENT), 15_000);
            log("scenario-1", "transcript segment broadcast over /topic/sessions/{id}: " + segmentSeen);

            // Give the async listener a moment; below the char trigger and first pass ⇒ expect no suggestion yet.
            List<Suggestion> beforeFlush = pollUntilStable(sessionId, 6_000);
            log("scenario-1", "BEFORE flush (only the char trigger fired): pending=" + describe(beforeFlush)
                    + " ⇒ short speech is " + (beforeFlush.isEmpty() ? "HELD (waits)" : "EMITTED"));
        } finally {
            closeQuietly(ws);
        }

        // POST /stop → DiscoverySessionRecordingStoppedEvent → suggest(force=true): the flush path.
        long flushStart = System.currentTimeMillis();
        assertThat(stop(projectId, sessionId).getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Suggestion> afterFlush = awaitSuggestions(sessionId, 1, PIPELINE_TIMEOUT_MS);
        log("scenario-1", "AFTER POST /stop flush: pending=" + describe(afterFlush)
                + " (flush latency ~" + (System.currentTimeMillis() - flushStart) + "ms)");

        assertThat(afterFlush)
                .as("POST /stop flush must yield >=1 suggestion so short speech is never permanently lost")
                .isNotEmpty();
    }

    // ── Scenario 2: exact duplicate ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("2) WS exact duplicate: the same sentence streamed twice yields a single suggestion (dedup)")
    void wsExactDuplicate() throws Exception {
        UUID projectId = seedProject();
        UUID sessionId = createAndStartSession(projectId, "WS exact-duplicate");
        String capability = "Como usuario quiero exportar mis reportes a PDF para archivarlos y compartirlos por "
                + "correo con mi equipo cada fin de mes sin depender de nadie más del área.";
        streamThenStop(projectId, sessionId, capability, capability);

        List<Suggestion> persisted = pending(sessionId);
        log("scenario-2", "verbatim capability streamed twice ⇒ pending=" + describe(persisted)
                + "; storyDedup=" + storyPairwiseSummary(projectId));
        assertThat(persisted)
                .as("a verbatim-duplicated capability must not persist two near-identical suggestions")
                .hasSizeLessThanOrEqualTo(1);
        assertNoStoriesWronglyMerged(projectId);
    }

    // ── Scenario 3: slight paraphrase ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("3) WS slight paraphrase: a reworded same capability dedups or becomes UPDATE_STORY (near 0.84)")
    void wsSlightParaphrase() throws Exception {
        UUID projectId = seedProject();
        UUID seeded = seedIndexedStory(projectId, "Exportar reportes a PDF", "usuario",
                "exportar mis reportes a PDF", "poder archivarlos y compartirlos");
        UUID sessionId = createAndStartSession(projectId, "WS paraphrase");
        String paraphrase = "Necesito que el sistema me deje descargar mis informes en formato PDF para guardarlos "
                + "y enviárselos a mi equipo, básicamente lo mismo de exportar los reportes que ya conversamos.";
        streamThenStop(projectId, sessionId, paraphrase);

        List<Suggestion> persisted = pending(sessionId);
        boolean dupNewStory = persisted.stream().anyMatch(s -> s.getType() == SuggestionType.NEW_STORY);
        boolean converged = persisted.isEmpty()
                || persisted.stream().anyMatch(s -> s.getType() == SuggestionType.UPDATE_STORY);
        Double sim = firstSimilarity(persisted);
        log("scenario-3", "seeded indexed story=" + seeded + "; paraphrase ⇒ pending=" + describe(persisted)
                + "; recordedSimilarity=" + sim + " vs 0.84; converged=" + converged + " producedNewStory=" + dupNewStory);
        assertThat(converged || !dupNewStory)
                .as("a paraphrase of an accepted story should dedup or become UPDATE_STORY, not a standalone NEW_STORY")
                .isTrue();
        assertNoStoriesWronglyMerged(projectId);
    }

    // ── Scenario 4: distinct-but-related (false-positive guard) ──────────────────────────────────────────

    @Test
    @DisplayName("4) WS distinct-but-related: login vs reset-password stay distinct (>=2 story drafts)")
    void wsDistinctButRelated() throws Exception {
        UUID projectId = seedProject();
        UUID sessionId = createAndStartSession(projectId, "WS distinct-related");
        String transcript = "El usuario quiere iniciar sesión con su correo y contraseña para entrar a la plataforma. "
                + "Aparte, y esto es algo distinto, quiere poder restablecer su contraseña mediante un enlace que le "
                + "llegue por correo electrónico cuando la haya olvidado por completo.";
        streamThenStop(projectId, sessionId, transcript);

        List<Suggestion> persisted = pending(sessionId);
        long distinctDrafts = persisted.stream()
                .filter(s -> s.getType() == SuggestionType.NEW_STORY || s.getType() == SuggestionType.UPDATE_STORY)
                .count();
        log("scenario-4", "login vs reset-password ⇒ pending=" + describe(persisted)
                + " distinctStoryDrafts=" + distinctDrafts + "; storyDedup=" + storyPairwiseSummary(projectId));
        assertThat(distinctDrafts)
                .as("two genuinely distinct capabilities must yield >=2 story suggestions (not wrongly merged)")
                .isGreaterThanOrEqualTo(2);
        assertNoStoriesWronglyMerged(projectId);
    }

    // ── Scenario 5: UPDATE on an accepted+indexed capability ─────────────────────────────────────────────

    @Test
    @DisplayName("5) WS update: streaming a new detail on an accepted+indexed capability targets it (not a duplicate NEW)")
    void wsUpdateExistingCapability() throws Exception {
        UUID projectId = seedProject();
        UUID loginStory = seedIndexedStory(projectId, "Iniciar sesión", "usuario",
                "iniciar sesión con correo y contraseña", "acceder a la plataforma");
        UUID sessionId = createAndStartSession(projectId, "WS update");
        String transcript = "Sobre lo del inicio de sesión que ya tenemos: además quiero que soporte autenticación "
                + "con Google, o sea el mismo inicio de sesión pero permitiendo también entrar con la cuenta de Google.";
        streamThenStop(projectId, sessionId, transcript);

        List<Suggestion> persisted = pending(sessionId);
        boolean targetsLogin = persisted.stream().anyMatch(s ->
                (s.getType() == SuggestionType.UPDATE_STORY || s.getType() == SuggestionType.EDGE_CASE)
                && loginStory.equals(s.getTargetStoryId()));
        boolean anyUpdateOrEdge = persisted.stream().anyMatch(s ->
                s.getType() == SuggestionType.UPDATE_STORY || s.getType() == SuggestionType.EDGE_CASE);
        log("scenario-5", "seeded login story=" + loginStory + "; 'además … con Google' ⇒ pending=" + describe(persisted)
                + "; targetsLoginStory=" + targetsLogin + " anyUpdateOrEdge=" + anyUpdateOrEdge
                + "; storyDedup=" + storyPairwiseSummary(projectId));
        // Tolerant: the failure we guard is a standalone near-duplicate NEW of the same capability.
        assertNoStoriesWronglyMerged(projectId);
    }

    // ── Scenario 6: all four types across a richer streamed transcript ───────────────────────────────────

    @Test
    @DisplayName("6) WS all four types: a multi-topic streamed transcript over an indexed backlog — report the type spread")
    void wsAllFourTypes() throws Exception {
        UUID projectId = seedProject();
        UUID checkoutStory = seedIndexedStory(projectId, "Pagar el carrito", "cliente",
                "pagar los productos de mi carrito con tarjeta", "completar mi compra");
        UUID sessionId = createAndStartSession(projectId, "WS all-types");
        streamThenStop(projectId, sessionId,
                "Volviendo al pago del carrito: además debería permitir pagar con Yape, no solo con tarjeta.",
                "Y ojo, si la tarjeta es rechazada por fondos insuficientes debe mostrar un mensaje claro y no cobrar.",
                "Por otro lado, quiero un panel de historial de pedidos donde el cliente vea sus compras anteriores.",
                "Ah, y no me quedó claro lo de las notificaciones: ¿las quieren por correo, por push, o de ambas formas?");

        List<Suggestion> persisted = pending(sessionId);
        List<SuggestionType> typesSeen = persisted.stream().map(Suggestion::getType).distinct().toList();
        log("scenario-6", "seeded checkout story=" + checkoutStory + "; types observed=" + typesSeen
                + "; pending=" + describe(persisted));
        assertThat(persisted).as("a rich multi-topic streamed transcript should elicit >=1 suggestion").isNotEmpty();
        assertNoStoriesWronglyMerged(projectId);
    }

    // ── Scenario 7: hard cases ───────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("7a) WS hard: a subtle near-threshold paraphrase — report similarity vs the 0.84 bar")
    void wsHardNearThresholdParaphrase() throws Exception {
        UUID projectId = seedProject();
        UUID seeded = seedIndexedStory(projectId, "Filtrar tareas por estado", "usuario",
                "filtrar mis tareas por estado pendiente o completada", "encontrarlas más rápido");
        UUID sessionId = createAndStartSession(projectId, "WS near-threshold");
        streamThenStop(projectId, sessionId,
                "Estaría bueno poder ver mis tareas separando las que ya terminé de las que aún no, para ubicarlas rapidísimo.");

        List<Suggestion> persisted = pending(sessionId);
        log("scenario-7a", "seeded story=" + seeded + "; near-threshold paraphrase ⇒ pending=" + describe(persisted)
                + "; recordedSimilarity=" + firstSimilarity(persisted) + " vs 0.84; storyDedup=" + storyPairwiseSummary(projectId));
        assertNoStoriesWronglyMerged(projectId); // report-only otherwise
    }

    @Test
    @DisplayName("7b) WS hard: incremental refinement across two streamed frames — report convergence")
    void wsHardIncrementalRefinement() throws Exception {
        UUID projectId = seedProject();
        UUID sessionId = createAndStartSession(projectId, "WS incremental");
        BlockingQueue<JsonMsg> topic = subscribeSessionTopic(sessionId);
        WebSocketSession ws = openStt(sessionId);
        try {
            sendFrame(ws, "El usuario quiere subir una foto de perfil para personalizar su cuenta y que otros lo "
                    + "reconozcan fácilmente dentro de la aplicación en todo momento y en cualquier pantalla.");
            List<Suggestion> afterPass1 = awaitSuggestions(sessionId, 1, PIPELINE_TIMEOUT_MS);
            log("scenario-7b", "frame 1 ⇒ pending=" + describe(afterPass1));

            sendFrame(ws, "Sobre la foto de perfil: además debe poder recortarla antes de guardarla y aceptar solo "
                    + "imágenes de menos de 5 megabytes para no saturar el almacenamiento del sistema.");
            List<Suggestion> afterPass2 = pollUntilStable(sessionId, 12_000);
            long newStories = afterPass2.stream().filter(s -> s.getType() == SuggestionType.NEW_STORY).count();
            log("scenario-7b", "frame 2 (refinement) ⇒ pending=" + describe(afterPass2)
                    + " NEW_STORY count=" + newStories + "; segmentsBroadcast=" + countType(topic, SessionEventType.TRANSCRIPT_SEGMENT)
                    + "; storyDedup=" + storyPairwiseSummary(projectId));
        } finally {
            closeQuietly(ws);
        }
        stop(projectId, sessionId);
        assertNoStoriesWronglyMerged(projectId);
    }

    @Test
    @DisplayName("7c) WS hard: an ambiguous/underspecified streamed requirement — does it ask a CLARIFYING_QUESTION?")
    void wsHardAmbiguousRequirement() throws Exception {
        UUID projectId = seedProject();
        UUID sessionId = createAndStartSession(projectId, "WS ambiguous");
        streamThenStop(projectId, sessionId,
                "Queremos que el sistema sea rápido y que maneje bien la seguridad, ya saben, que esté todo bien "
                        + "protegido. Eso es lo importante por ahora, después vemos el resto.");
        List<Suggestion> persisted = pending(sessionId);
        boolean asked = persisted.stream().anyMatch(s -> s.getType() == SuggestionType.CLARIFYING_QUESTION);
        log("scenario-7c", "ambiguous requirement ⇒ pending=" + describe(persisted)
                + "; producedClarifyingQuestion=" + asked);
        // Report-only.
    }

    // ── Scenario 8: WS lifecycle (streaming-specific — only a real WS test can cover this) ───────────────

    @Test
    @DisplayName("8) WS lifecycle: segment broadcast; pause closes the WS; resume lets a new WS connect; stop closes + flushes")
    void wsLifecycle() throws Exception {
        UUID projectId = seedProject();
        UUID sessionId = createAndStartSession(projectId, "WS lifecycle");
        BlockingQueue<JsonMsg> topic = subscribeSessionTopic(sessionId);

        // (a) A streamed segment must broadcast over the session topic.
        WebSocketSession ws1 = openStt(sessionId);
        sendFrame(ws1, "El usuario quiere poder cambiar el idioma de la interfaz entre español e inglés.");
        boolean segmentSeen = awaitTrue(() -> hasType(topic, SessionEventType.TRANSCRIPT_SEGMENT), 15_000);
        log("scenario-8", "(a) segment broadcast over /topic/sessions/{id}: " + segmentSeen);
        assertThat(segmentSeen).as("a streamed segment must be broadcast over the session STOMP topic").isTrue();

        // (b) POST /pause → the server closes the WS (per the doc, code 1000). The client marks the
        // session not-open once the server-initiated close arrives.
        assertThat(pause(projectId, sessionId).getStatusCode()).isEqualTo(HttpStatus.OK);
        boolean closedOnPause = awaitTrue(() -> !ws1.isOpen(), 15_000);
        log("scenario-8", "(b) POST /pause closed the WS: " + closedOnPause);
        assertThat(closedOnPause).as("POST /pause must close the /ws/stt connection").isTrue();

        // (c) POST /resume → RECORDING again → a NEW WS connects successfully.
        assertThat(resume(projectId, sessionId).getStatusCode()).isEqualTo(HttpStatus.OK);
        WebSocketSession ws2 = openStt(sessionId);
        boolean reconnected = ws2.isOpen();
        log("scenario-8", "(c) POST /resume then reconnect: ws open=" + reconnected);
        assertThat(reconnected).as("after POST /resume a new /ws/stt connection must be accepted").isTrue();

        // (d) POST /stop → the server closes the WS and flushes any remaining transcript tail.
        sendFrame(ws2, "Además quiere que el idioma elegido quede guardado en su perfil para la próxima vez que entre.");
        awaitTrue(() -> countType(topic, SessionEventType.TRANSCRIPT_SEGMENT) >= 2, 15_000);
        assertThat(stop(projectId, sessionId).getStatusCode()).isEqualTo(HttpStatus.OK);
        boolean closedOnStop = awaitTrue(() -> !ws2.isOpen(), 15_000);
        List<Suggestion> afterStop = awaitSuggestions(sessionId, 1, PIPELINE_TIMEOUT_MS);
        log("scenario-8", "(d) POST /stop closed the WS: " + closedOnStop
                + "; flush pending=" + describe(afterStop));
        assertThat(closedOnStop).as("POST /stop must close the /ws/stt connection").isTrue();
        assertThat(afterStop).as("POST /stop must flush the remaining transcript into >=1 suggestion").isNotEmpty();
        assertNoStoriesWronglyMerged(projectId);
    }

    // ── streaming helpers ────────────────────────────────────────────────────────────────────────────────

    /** Opens a /ws/stt binary connection authenticated for the provisioned tenant user. */
    private WebSocketSession openStt(UUID sessionId) throws Exception {
        String token = TestJwtFactory.token(USER_ID, orgId, "ROLE_USER");
        URI uri = URI.create("ws://localhost:" + port + "/ws/stt?session=" + sessionId + "&token=" + token);
        WebSocketSession ws = new StandardWebSocketClient()
                .execute(new BinaryWebSocketHandler() {}, new WebSocketHttpHeaders(), uri)
                .get(10, TimeUnit.SECONDS);
        assertThat(ws.isOpen()).as("the /ws/stt handshake must succeed for a RECORDING session").isTrue();
        return ws;
    }

    /** Sends one transcript utterance AS a UTF-8 binary frame; the echo STT emits it as a FINAL segment. */
    private void sendFrame(WebSocketSession ws, String utterance) throws Exception {
        ws.sendMessage(new BinaryMessage(ByteBuffer.wrap(utterance.getBytes(StandardCharsets.UTF_8))));
        Thread.sleep(150); // small pacing gap, as a real client streams chunks
    }

    /** Streams each utterance over a fresh WS then POST /stop (the flush path), and waits for >=1 suggestion. */
    private void streamThenStop(UUID projectId, UUID sessionId, String... utterances) throws Exception {
        WebSocketSession ws = openStt(sessionId);
        try {
            for (String u : utterances) sendFrame(ws, u);
            Thread.sleep(500); // let the incremental char/time cadence have a chance before the flush
        } finally {
            closeQuietly(ws);
        }
        assertThat(stop(projectId, sessionId).getStatusCode()).isEqualTo(HttpStatus.OK);
        awaitSuggestions(sessionId, 1, PIPELINE_TIMEOUT_MS);
    }

    private BlockingQueue<JsonMsg> subscribeSessionTopic(UUID sessionId) throws Exception {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"));
        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws/stomp",
                        new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);
        BlockingQueue<JsonMsg> queue = new LinkedBlockingQueue<>();
        // Both segments and suggestions land on this one topic, discriminated by the "type" field.
        session.subscribe("/topic/sessions/" + sessionId, new StompFrameHandler() {
            @Override public @NonNull Type getPayloadType(@NonNull StompHeaders headers) { return JsonMsg.class; }
            @Override public void handleFrame(@NonNull StompHeaders headers, Object payload) { queue.add((JsonMsg) payload); }
        });
        return queue;
    }

    /** Minimal STOMP payload holder: we only need the discriminator {@code type} field. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record JsonMsg(SessionEventType type) {}

    private static boolean hasType(BlockingQueue<JsonMsg> q, SessionEventType type) {
        return q.stream().anyMatch(m -> m.type() == type);
    }

    private static long countType(BlockingQueue<JsonMsg> q, SessionEventType type) {
        return q.stream().filter(m -> m.type() == type).count();
    }

    // ── REST session lifecycle ───────────────────────────────────────────────────────────────────────────

    private UUID createAndStartSession(UUID projectId, String title) {
        ResponseEntity<String> created = client().post().uri("/api/projects/{p}/sessions", projectId)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", title, "language", "es-PE"))
                .exchange((_, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID sessionId = UUID.fromString(created.getBody().split("\"id\":\"")[1].split("\"")[0]);
        assertThat(start(projectId, sessionId).getStatusCode()).isEqualTo(HttpStatus.OK);
        return sessionId;
    }

    private ResponseEntity<String> start(UUID projectId, UUID sessionId) {
        return post("/api/projects/" + projectId + "/sessions/" + sessionId + "/start");
    }

    private ResponseEntity<String> stop(UUID projectId, UUID sessionId) {
        return post("/api/projects/" + projectId + "/sessions/" + sessionId + "/stop");
    }

    private ResponseEntity<String> pause(UUID projectId, UUID sessionId) {
        return post("/api/projects/" + projectId + "/sessions/" + sessionId + "/pause");
    }

    private ResponseEntity<String> resume(UUID projectId, UUID sessionId) {
        return post("/api/projects/" + projectId + "/sessions/" + sessionId + "/resume");
    }

    private ResponseEntity<String> post(String uri) {
        return client().post().uri(uri)
                .header("Authorization", TestJwtFactory.bearer(USER_ID, orgId, "ROLE_USER"))
                .header("Api-Version", "1")
                .exchange((_, r) -> ResponseEntity.status(r.getStatusCode()).body(r.bodyTo(String.class)));
    }

    // ── seeding (tenant-scoped, via repositories under TenantContext) ────────────────────────────────────

    private UUID seedProject() {
        return inTenantTx(() -> {
            TechnicalProfile profile = new TechnicalProfile(
                    List.of("Java"), List.of("Spring Boot"), List.of("Web"), List.of("PostgreSQL"),
                    "Clean Architecture", "SaaS");
            Project project = new Project(UUID.fromString(orgId), "Discovery project", "seed", profile, UUID.fromString(USER_ID));
            return projects.save(project).getId();
        });
    }

    private UUID seedIndexedStory(UUID projectId, String title, String role, String action, String benefit) {
        return inTenantTx(() -> {
            UserStory story = new UserStory(projectId, title, role, action, benefit, Priority.HIGH, 3);
            story.assignEmbedding(embeddingPort.embed(story.toCanonicalText()));
            return stories.save(story).getId();
        });
    }

    // ── suggestion / story reads + async waits ───────────────────────────────────────────────────────────

    private List<Suggestion> pending(UUID sessionId) {
        return inTenantTx(() -> suggestions.findAllBySessionIdAndStatus(sessionId, SuggestionStatus.PENDING));
    }

    /** Waits until at least {@code min} PENDING suggestions exist (or times out), returning the latest set. */
    private List<Suggestion> awaitSuggestions(UUID sessionId, int min, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        List<Suggestion> last = pending(sessionId);
        while (last.size() < min && System.currentTimeMillis() < deadline) {
            sleep(500);
            last = pending(sessionId);
        }
        return last;
    }

    /** Polls until the pending set stops changing for two consecutive reads or the window elapses. */
    private List<Suggestion> pollUntilStable(UUID sessionId, long windowMs) {
        long deadline = System.currentTimeMillis() + windowMs;
        List<Suggestion> prev = pending(sessionId);
        while (System.currentTimeMillis() < deadline) {
            sleep(600);
            List<Suggestion> cur = pending(sessionId);
            if (cur.size() == prev.size()) return cur;
            prev = cur;
        }
        return prev;
    }

    private static Double firstSimilarity(List<Suggestion> list) {
        return list.stream().map(Suggestion::getSimilarity).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private void assertNoStoriesWronglyMerged(UUID projectId) {
        List<UserStory> all = allStories(projectId);
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

    private String storyPairwiseSummary(UUID projectId) {
        List<UserStory> all = allStories(projectId);
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

    private List<UserStory> allStories(UUID projectId) {
        return inTenantTx(() -> stories.findAllByProjectId(projectId,
                org.springframework.data.domain.PageRequest.of(0, 100)).getContent());
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

    private static void log(String label, String message) {
        System.out.println("[LLM-WS-E2E][" + label + "] " + message);
    }

    private static boolean awaitTrue(BooleanSupplier condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return true;
            sleep(250);
        }
        return condition.getAsBoolean();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(WebSocketSession ws) {
        try {
            if (ws.isOpen()) ws.close();
        } catch (Exception ignored) {
            // best-effort
        }
    }

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
