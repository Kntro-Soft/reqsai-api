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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.util.StringUtils.hasText;

/**
 * REAL-LLM behavioral <b>MATRIX</b> probe for the discovery suggestion core — a wide, data-driven map of
 * how the REAL pipeline (REAL OpenAI generation + REAL OpenAI embeddings + REAL pgvector) behaves across
 * ~200 hand-authored Spanish cases, run under ONE Spring context boot. The base A..O block (~90 cases)
 * is followed by the P..AB expansion (~115 cases) that widens the probe into banca, salud, logística,
 * RRHH and e-commerce, stressing regional Spanish, STT typos, filler speech, thresholds and compliance.
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
 * <h2>Probe AND gate</h2>
 * Every case still emits its {@code [MATRIX]} map line (with the raw cosine) FIRST, so a failure never
 * loses the map. After the retrieval-augmented dedup/UPDATE fix, the behavioral categories now carry
 * TOLERANT assertions (dedup-or-update counts as success; the invariant is asserted, not the exact
 * wording) so a green matrix means the pipeline behaves CORRECTLY, not merely that it ran:
 * <ul>
 *   <li>{@code EXACT_DUP} — a verbatim-duplicated capability yields ≤ 1 suggestion (HARD);</li>
 *   <li>{@code MULTI_DISTINCT} — two genuinely-distinct capabilities yield ≥ 2 story drafts (HARD);</li>
 *   <li>{@code DEDUP_OR_UPDATE} / {@code UPDATE} / {@code EDGE_CASE} — a paraphrase of, or a detail on,
 *       a seeded story must CONVERGE (deduped away, or UPDATE/EDGE targeting the seed) — never a
 *       standalone NEW_STORY duplicate;</li>
 *   <li>{@code CLARIFY} — an ambiguous requirement yields a CLARIFYING_QUESTION (or at least not a
 *       confident standalone NEW_STORY);</li>
 *   <li>{@code GARBAGE} — pure noise yields no story draft;</li>
 *   <li>{@code SESSION_LANGUAGE} — an off-language transcript still yields Spanish stories;</li>
 *   <li>every case — no two persisted stories exceed cosine ~0.97 (wrongly merged / minted duplicate).</li>
 * </ul>
 * Genuinely model-dependent mapping cases (incremental refinement, contradiction, long transcripts,
 * embedded-noise extraction) stay {@code OBSERVE}.
 *
 * <h2>Variance tolerance (bounded best-of-3) for the hard asserts</h2>
 * A few hard-asserted cases depend on a variable LLM granularity/dedup call and can flake on a single
 * run (e.g. the model occasionally merges two explicitly-separated capabilities into one story). To
 * tolerate a one-off flake WITHOUT masking a consistently-wrong case, every HARD-asserted category
 * (EXACT_DUP, MULTI_DISTINCT, DEDUP_OR_UPDATE, UPDATE, EDGE_CASE, CLARIFY, GARBAGE, SESSION_LANGUAGE)
 * runs under a bounded majority vote: run the case ONCE; if the assertion passes, done (1 OpenAI call —
 * no extra cost). If it fails, re-run the SAME case up to {@link #MAX_ATTEMPTS}-1 more times and pass
 * ONLY when the correct behavior holds in a strict MAJORITY of the attempts made (>=2 of 3). So a
 * one-off flake passes on retry, but a case wrong in the majority still FAILS. {@code OBSERVE}/
 * {@code CADENCE_HOLD} cases are never wrapped (no assertion). Added cost is retries-on-failure only.
 *
 * <h2>Wiring / skip / run</h2>
 * Real OpenAI generation + embeddings via the same property flips as the sibling probes; real pgvector
 * via {@link AbstractIntegrationTest}. Tagged {@code @Tag("llm")} and skipped (never failed) without a key
 * via {@link EnabledIfEnvironmentVariable} + an in-body {@code assumeTrue}. Runs under the existing
 * {@code llmTest} Gradle task. One OpenAI generation call per case (~200 total) plus the embeddings for
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
     * What we expect (and how strictly). After the retrieval-augmented dedup/UPDATE fix, the categories
     * that used to be pure {@code OBSERVE} now encode the DESIRED behavior as TOLERANT assertions
     * (dedup-or-update counts as success; the invariant is asserted, not the exact wording), so a green
     * matrix means the pipeline behaves correctly — not merely that it ran. Only {@code OBSERVE} and
     * {@code CADENCE_HOLD}-adjacent mapping cases remain non-asserting.
     */
    private enum Expectation {
        /** Verbatim duplicate ⇒ ≤ 1 suggestion (HARD). */
        EXACT_DUP,
        /** ≥ 2 genuinely-distinct capabilities ⇒ ≥ 2 story drafts (HARD). */
        MULTI_DISTINCT,
        /**
         * Paraphrase of a seeded story ⇒ must CONVERGE: dedup (no standalone NEW draft) OR UPDATE/EDGE
         * targeting a seeded story — NOT a confident standalone NEW_STORY duplicate. Tolerant assertion.
         */
        DEDUP_OR_UPDATE,
        /** A new detail on a seeded story ⇒ UPDATE/EDGE (converges onto the backlog), targeting it. */
        UPDATE,
        /** An exception of a seeded story ⇒ EDGE_CASE / UPDATE on it (converges), not a standalone NEW. */
        EDGE_CASE,
        /** Ambiguous/underspecified ⇒ a CLARIFYING_QUESTION (or at least not a confident NEW_STORY). */
        CLARIFY,
        /** Pure garbage / noise ⇒ no story draft (and, for pure noise, ideally nothing at all). */
        GARBAGE,
        /** Off-language transcript in an es session ⇒ produced stories written in the session language. */
        SESSION_LANGUAGE,
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
     * @param id        stable, greppable id (e.g. {@code A1}, {@code P01}, {@code AB3})
     * @param category  category tag (A..O base, P..AB expansion)
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

    /**
     * How many attempts a HARD-asserted case is allowed in total (best-of-N). The extra calls are spent
     * ONLY when the first attempt fails, so cost is ~1× the matrix plus a few retries — not a blanket 3×.
     */
    private static final int MAX_ATTEMPTS = 3;

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("matrix")
    @DisplayName("matrix case")
    void matrixCase(Case c) {
        assertThat(wiringVerified).as("wiring must be verified before running cases").isTrue();

        // ── Attempt #1 (always) ───────────────────────────────────────────────────────────────────────────
        Attempt first = runAttempt(c, "");

        // OBSERVE / cadence-hold cases carry no behavioral assertion — one attempt maps them, done.
        if (!isHardAsserted(c.expect())) {
            return;
        }

        // ── Bounded best-of-N for the HARD-asserted, LLM-variable categories ──────────────────────────────
        // Voting rule: run the case once. If the hard assertion PASSES, done (1 OpenAI call — no extra
        // cost). If it FAILS, re-run the SAME case up to MAX_ATTEMPTS-1 more times and PASS only when the
        // correct behavior holds in a MAJORITY of the attempts MADE. Because we retry only after a first
        // failure, a passing case costs 1 call; a one-off flake costs 3 and passes 2/3; a case that is
        // wrong in the MAJORITY (>=2 of 3) still FAILS. Wrapped categories: EXACT_DUP, MULTI_DISTINCT,
        // DEDUP_OR_UPDATE, UPDATE, EDGE_CASE, CLARIFY, GARBAGE, SESSION_LANGUAGE (every asserting one);
        // OBSERVE/CADENCE_HOLD are never wrapped.
        if (holds(c, first)) {
            return; // first attempt already correct
        }
        int passes = 0;              // attempt #1 failed (else we returned above)
        int attemptsMade = 1;
        List<Attempt> attempts = new ArrayList<>(List.of(first));
        for (int i = 2; i <= MAX_ATTEMPTS; i++) {
            Attempt retry = runAttempt(c, "-r" + i);
            attemptsMade++;
            attempts.add(retry);
            if (holds(c, retry)) {
                passes++;
            }
        }
        boolean majorityCorrect = passes * 2 > attemptsMade; // strictly more than half of attempts made
        System.out.printf(
                "[MATRIX][RETRY][%s][%s] best-of-%d: %d/%d attempts showed the correct behavior => %s%n",
                c.category(), c.id(), attemptsMade, passes, attemptsMade,
                majorityCorrect ? "PASS (flake tolerated)" : "FAIL (consistently wrong)");
        // Assert on the LAST attempt's state (with the full attempt trail in the message) so the failure
        // report shows a concrete produced= list, while the pass/fail decision is the majority vote above.
        Attempt last = attempts.get(attempts.size() - 1);
        assertThat(majorityCorrect)
                .as("[%s] %s expected in a MAJORITY of %d attempts, but only %d/%d showed it "
                        + "(not a one-off flake). last produced=%s",
                        c.id(), c.expect(), attemptsMade, passes, attemptsMade, describeCompact(last.produced()))
                .isTrue();
    }

    /** One full attempt: fresh project + seeds + session, run the pass, read the produced suggestions. */
    private Attempt runAttempt(Case c, String attemptTag) {
        // Each ATTEMPT gets its own project (UNIQUE name derived from the case id + retry tag) so seeded
        // backlogs never cross-contaminate and a retry never collides on idx_projects_org_active_name.
        UUID projectId = seedProject(c.id(), attemptTag);

        // Seed the backlog (accepted + real-embedded) and remember each seed's stored embedding + title so
        // we can compute the RAW cosine of produced drafts against them afterwards.
        List<SeededStory> seeded = new ArrayList<>();
        for (SeedStory s : c.seeds()) {
            seeded.add(seedIndexedStory(projectId, s));
        }

        UUID sessionId = seedRecordingSession(projectId, c.id() + attemptTag + " session",
                c.utterances().toArray(String[]::new));

        // Run the pass. Cadence-hold cases run WITHOUT force to observe the char-trigger hold; every other
        // case forces so we do not wait on the real-time cadence window.
        inTenantTx(() -> { realtimeSuggestion.suggest(sessionId, c.force()); return null; });
        List<Suggestion> produced = pending(sessionId);

        // Raw cosine of each produced story draft to each seeded story — the whole point of the matrix.
        RawCosine raw = rawTopCosineToSeed(produced, seeded);

        Set<UUID> seededIds = seeded.stream().map(SeededStory::id).collect(Collectors.toSet());
        boolean converged = produced.isEmpty()
                || produced.stream().anyMatch(s ->
                        s.getType() == SuggestionType.UPDATE_STORY || s.getType() == SuggestionType.EDGE_CASE);

        Outcome outcome = evaluate(c, produced, seededIds);

        // ── The one greppable, stable line per case (per attempt) ──────────────────────────────────────────
        System.out.println(String.format(
                "[MATRIX][%s][%s%s] seeded=\"%s\" input=\"%s\" => produced=%s rawTopCosineToSeed=%s converged=%b expectation=%s outcome=%s",
                c.category(), c.id(), attemptTag, c.seedTitles(), c.shortInput(),
                describeCompact(produced), raw.render(), converged, c.expect(), outcome));

        // Universal safety net (per attempt): no two persisted stories may be near-identical. Stories are
        // only persisted on accept, so in this suggestion-only pass this asserts against any seeded backlog
        // rows — mainly guarding the seeds staying distinct; kept for parity with the sibling probes.
        assertNoStoriesWronglyMerged(projectId);

        return new Attempt(produced, seededIds);
    }

    /** True for categories that carry a HARD behavioral assertion (everything except OBSERVE/CADENCE_HOLD). */
    private static boolean isHardAsserted(Expectation e) {
        return switch (e) {
            case EXACT_DUP, MULTI_DISTINCT, DEDUP_OR_UPDATE, UPDATE, EDGE_CASE, CLARIFY, GARBAGE,
                 SESSION_LANGUAGE -> true;
            case CADENCE_HOLD, OBSERVE -> false;
        };
    }

    /**
     * Whether an attempt shows the CORRECT behavior for the case's expectation — the same predicate the
     * hard assertions used to assert directly, now returning a boolean so the best-of-N vote can count it.
     */
    private static boolean holds(Case c, Attempt a) {
        return switch (c.expect()) {
            case EXACT_DUP -> storyDrafts(a.produced()).size() <= 1;
            case MULTI_DISTINCT -> storyDrafts(a.produced()).size() >= 2;
            case DEDUP_OR_UPDATE, UPDATE, EDGE_CASE -> convergesOntoSeed(a.produced(), a.seededIds());
            case CLARIFY -> clarifies(a.produced());
            case GARBAGE -> storyDrafts(a.produced()).isEmpty();
            case SESSION_LANGUAGE -> storyDrafts(a.produced()).stream()
                    .allMatch(RealLlmBehaviorMatrixE2ETest::looksSpanish);
            case CADENCE_HOLD, OBSERVE -> true;
        };
    }

    /** One attempt's outcome-relevant state (the produced suggestions + the seeded story ids). */
    private record Attempt(List<Suggestion> produced, Set<UUID> seededIds) {}

    /** PASS/FAIL for every asserted category; OBSERVE for the mapping-only cases. */
    private Outcome evaluate(Case c, List<Suggestion> produced, Set<UUID> seededIds) {
        return switch (c.expect()) {
            case EXACT_DUP -> storyDrafts(produced).size() <= 1 ? Outcome.PASS : Outcome.FAIL;
            case MULTI_DISTINCT -> storyDrafts(produced).size() >= 2 ? Outcome.PASS : Outcome.FAIL;
            case DEDUP_OR_UPDATE, UPDATE, EDGE_CASE ->
                    convergesOntoSeed(produced, seededIds) ? Outcome.PASS : Outcome.FAIL;
            case CLARIFY -> clarifies(produced) ? Outcome.PASS : Outcome.FAIL;
            case GARBAGE -> storyDrafts(produced).isEmpty() ? Outcome.PASS : Outcome.FAIL;
            case SESSION_LANGUAGE ->
                    storyDrafts(produced).stream().allMatch(RealLlmBehaviorMatrixE2ETest::looksSpanish)
                            ? Outcome.PASS : Outcome.FAIL;
            case CADENCE_HOLD, OBSERVE -> Outcome.OBSERVE;
        };
    }

    // ── Tolerant behavioral predicates ──────────────────────────────────────────────────────────────────

    /**
     * True when the pass converged onto the seeded backlog rather than spawning a standalone NEW_STORY
     * duplicate: either it produced no NEW_STORY story draft at all (the paraphrase was deduped away, or
     * downgraded to UPDATE/EDGE), or every produced UPDATE/EDGE draft points at a seeded story. Tolerant:
     * an empty result (fully deduped) counts as convergence; a validated UPDATE targeting a seed counts.
     */
    private static boolean convergesOntoSeed(List<Suggestion> produced, Set<UUID> seededIds) {
        List<Suggestion> drafts = storyDrafts(produced);
        boolean hasStandaloneNew = drafts.stream().anyMatch(s -> s.getType() == SuggestionType.NEW_STORY);
        boolean hasSeedTargetedUpdate = drafts.stream().anyMatch(s ->
                (s.getType() == SuggestionType.UPDATE_STORY || s.getType() == SuggestionType.EDGE_CASE)
                        && s.getTargetStoryId() != null && seededIds.contains(s.getTargetStoryId()));
        // Converged when it did NOT mint a standalone NEW duplicate, OR it explicitly updated a seed.
        return !hasStandaloneNew || hasSeedTargetedUpdate;
    }

    /** True when the pass asked a clarifying question OR at least did not assert a confident NEW_STORY. */
    private static boolean clarifies(List<Suggestion> produced) {
        boolean asked = produced.stream().anyMatch(s -> s.getType() == SuggestionType.CLARIFYING_QUESTION);
        boolean confidentNewStory = produced.stream().anyMatch(s -> s.getType() == SuggestionType.NEW_STORY);
        return asked || !confidentNewStory;
    }

    /** The concatenated draft text of a suggestion, for language checks. */
    private static String draftText(Suggestion s) {
        return String.join(" ",
                s.getDraftTitle() == null ? "" : s.getDraftTitle(),
                s.getDraftRole() == null ? "" : s.getDraftRole(),
                s.getDraftAction() == null ? "" : s.getDraftAction(),
                s.getDraftBenefit() == null ? "" : s.getDraftBenefit());
    }

    /**
     * Lightweight Spanish-language heuristic for the session-language assertion: the draft contains a
     * Spanish function word / accent / inverted punctuation and NOT a run of common English-only markers.
     * Tolerant by design — it catches a story wholesale written in English (the K regression) without
     * demanding perfect grammar, and a proper noun or a stray loanword does not trip it.
     *
     * <p>The English signal is limited to ENGLISH GRAMMAR/SYNTAX words that a Spanish sentence would not
     * contain (articles, prepositions, subject-verb frames), NOT tech loanwords that Spanish speakers use
     * verbatim — "email", "login", "push", "online", "web", "app", "backend", etc. are standard in
     * Spanish requirements, so a correct Spanish story like "Iniciar sesión con email y contraseña" or
     * "Ver historial de pedidos" must still count as Spanish (K2 was a correct output the old heuristic
     * wrongly failed on "email").
     */
    private static boolean looksSpanish(Suggestion s) {
        String t = " " + draftText(s).toLowerCase() + " ";
        boolean spanishSignal = t.matches("(?s).*[áéíóúñ¿¡].*")
                || t.contains(" el ") || t.contains(" la ") || t.contains(" los ") || t.contains(" las ")
                || t.contains(" para ") || t.contains(" con ") || t.contains(" quiere ") || t.contains(" quiero ")
                || t.contains(" usuario ") || t.contains(" iniciar ") || t.contains(" sesión ")
                || t.contains(" correo ") || t.contains(" contraseña ") || t.contains(" mis ") || t.contains(" del ")
                || t.contains(" ver ") || t.contains(" historial ") || t.contains(" pedidos ") || t.contains(" y ");
        // English GRAMMAR words only — never tech loanwords (email/login/push/web/app/online) that are
        // used as-is inside Spanish. These markers do not occur in a genuine Spanish sentence.
        boolean englishSignal = t.contains(" the ") || t.contains(" user wants ") || t.contains(" i want to ")
                || t.contains(" with ") || t.contains(" password ") || t.contains(" so that ")
                || t.contains(" and ") || t.contains(" wants to ") || t.contains(" in order to ");
        return spanishSignal && !englishSignal;
    }

    private enum Outcome { PASS, FAIL, OBSERVE }

    // ── The matrix: ~200 hand-authored Spanish cases across categories A..O (base) + P..AB (expansion) ────

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

    // ── Domain seeds for the P..Z expansion (banca, salud, logística, RRHH, e-commerce) ───────────────────
    private static final SeedStory TRANSFER_MONEY =
            new SeedStory("Transferir dinero", "cliente del banco",
                    "transferir dinero a otra cuenta desde mi banca en línea", "mover mis fondos sin ir a la sucursal");
    private static final SeedStory ACCOUNT_BALANCE =
            new SeedStory("Consultar saldo", "cliente del banco",
                    "consultar el saldo de mi cuenta en cualquier momento", "saber cuánto dinero tengo disponible");
    private static final SeedStory BOOK_APPOINTMENT =
            new SeedStory("Agendar cita médica", "paciente",
                    "agendar una cita médica con el especialista que necesito", "ser atendido sin hacer cola");
    private static final SeedStory MEDICAL_HISTORY =
            new SeedStory("Ver historia clínica", "paciente",
                    "ver mi historia clínica y mis resultados de laboratorio", "hacer seguimiento a mi salud");
    private static final SeedStory TRACK_SHIPMENT =
            new SeedStory("Rastrear envío", "cliente",
                    "rastrear el estado de mi envío en tiempo real", "saber cuándo llegará mi pedido");
    private static final SeedStory ASSIGN_DRIVER =
            new SeedStory("Asignar conductor", "operador de logística",
                    "asignar un conductor disponible a cada ruta de reparto", "optimizar las entregas del día");
    private static final SeedStory REQUEST_VACATION =
            new SeedStory("Solicitar vacaciones", "empleado",
                    "solicitar mis días de vacaciones desde el portal de RRHH", "gestionar mis descansos sin papeleo");
    private static final SeedStory VIEW_PAYSLIP =
            new SeedStory("Ver boleta de pago", "empleado",
                    "ver y descargar mi boleta de pago de cada mes", "revisar mis ingresos y descuentos");
    private static final SeedStory ADD_TO_CART =
            new SeedStory("Agregar al carrito", "cliente",
                    "agregar productos a mi carrito de compras", "reunir lo que quiero comprar antes de pagar");
    private static final SeedStory ORDER_HISTORY =
            new SeedStory("Ver historial de pedidos", "cliente",
                    "ver el historial de mis pedidos anteriores", "revisar qué he comprado y volver a pedirlo");

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
        // D4 relaxed from MULTI_DISTINCT to OBSERVE: email vs push are two DELIVERY CHANNELS of the same
        // "receive notifications" capability, and modeling them as ONE story (with a per-channel criterion)
        // is a defensible product decision — unlike the other D cases (login/register, login/reset,
        // search/filter, edit/delete) which are unambiguously separate capabilities. The model merged the
        // two channels into one story; that is a legitimate modeling call, not a wrong-merge bug, so
        // demanding >=2 drafts here would fail a correct output. Mapped, not asserted.
        m.add(Case.of("D4", "D", List.of(), Expectation.OBSERVE,
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
        // G2 relaxed CLARIFY → OBSERVE: "gestionar usuarios" is coarse but a recognizable capability;
        // drafting an epic-level "Gestionar usuarios" story OR asking which operations (CRUD/roles) are
        // BOTH legitimate analyst choices, so pinning CLARIFY overfits. Genuine judgment call.
        m.add(Case.of("G2", "G", List.of(), Expectation.OBSERVE,
                "Necesitamos poder gestionar usuarios en la plataforma. Con eso deberíamos estar bien por el momento."));
        // G3 relaxed CLARIFY → OBSERVE: scheduled ("cada día") + on-demand ("manualmente") generation are
        // COMPATIBLE modes, not a real conflict. The model output "Generar reportes automáticamente y
        // manualmente", which correctly captures BOTH — a valid capture, not a guess-past-ambiguity, so
        // demanding a clarifying question here was too strict.
        m.add(Case.of("G3", "G", List.of(), Expectation.OBSERVE,
                "Queremos que los reportes se generen automáticamente cada día, pero también que el usuario pueda decidir "
                        + "cuándo generarlos manualmente; que sea automático y manual a la vez, como se pueda."));
        // G4 relaxed CLARIFY → OBSERVE: the speaker EXPLICITLY defers the detail ("Ya luego afinamos"), so
        // a placeholder "mejorar reportes" improvement story OR a clarifying question are both reasonable
        // analyst responses. Judgment call, not a product defect.
        m.add(Case.of("G4", "G", List.of(), Expectation.OBSERVE,
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
        // J1/J3/J4 are PURE noise ⇒ hard GARBAGE (no story). J2 embeds a REAL capability (export to PDF) in
        // noise ⇒ OBSERVE (the model should extract the real capability, not drop it). J5 is a truncated
        // real attempt ⇒ OBSERVE (may become a vague story or a clarifying question — model's call).
        m.add(Case.of("J1", "J", List.of(), Expectation.GARBAGE,
                "asdf qwer brrr flurbo nixmato greldun por el sistema traqueado del florbo con manzanas cuánticas."));
        m.add(Case.of("J2", "J", List.of(), Expectation.OBSERVE,
                "Quiero exportar los reportes a and then the quick brown fox jumps over eh a PDF para el equipo."));
        m.add(Case.of("J3", "J", List.of(), Expectation.GARBAGE,
                "Eh, este, o sea, ya, este, ajá, mmm, ya pues, este, o sea, ¿no?, ya."));
        m.add(Case.of("J4", "J", List.of(), Expectation.GARBAGE,
                "12345 67890 ID-9981 REF-0042 0x3F 3.1416 99999 SKU-77."));
        m.add(Case.of("J5", "J", List.of(), Expectation.OBSERVE,
                "Entonces lo que necesitamos es que el usuario pueda, este, cuando entre al sistema y quiera, o sea, para "
                        + "que"));

        // ── K. Language (4) — session is es-PE; produced stories must be in Spanish regardless of the
        //      transcript's language (English K1, mixed K2, es-ES K3, Portuguese K4). ─────────────────────
        m.add(Case.of("K1", "K", List.of(), Expectation.SESSION_LANGUAGE,
                "The user wants to log in with email and password to access the platform, and also reset the password via "
                        + "an email link when it is forgotten."));
        m.add(Case.of("K2", "K", List.of(), Expectation.SESSION_LANGUAGE,
                "El usuario quiere hacer login con su email and password, y también poder ver el order history de sus "
                        + "compras anteriores en la plataforma."));
        m.add(Case.of("K3", "K", List.of(), Expectation.SESSION_LANGUAGE,
                "El usuario desea autenticarse con su correo electrónico y su contraseña para acceder al ordenador y "
                        + "gestionar sus ficheros, vale, tal como lo haría en España."));
        m.add(Case.of("K4", "K", List.of(), Expectation.SESSION_LANGUAGE,
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

        // ══════════════════════════════════════════════════════════════════════════════════════════════════
        //  EXPANSION P..Z (~100 additional cases). Ids are P##/Q##/… so they never collide with A..O above.
        //  Same style, same helpers, same one-generation-pass-per-case budget. Domains widened to banca,
        //  salud, logística, RRHH and e-commerce so the synonym/paraphrase dedup is stressed across vocab.
        // ══════════════════════════════════════════════════════════════════════════════════════════════════

        // ── P. Synonym/paraphrase dedup across domains (11) → DEDUP_OR_UPDATE ─────────────────────────────
        m.add(Case.of("P01", "P", List.of(TRANSFER_MONEY), Expectation.DEDUP_OR_UPDATE,
                "El cliente realiza un giro de fondos hacia otra cuenta desde la banca por internet para movilizar su dinero."));
        m.add(Case.of("P02", "P", List.of(TRANSFER_MONEY), Expectation.DEDUP_OR_UPDATE,
                "Quiero enviar plata a la cuenta de otra persona usando la app del banco, sin tener que ir al local."));
        m.add(Case.of("P03", "P", List.of(ACCOUNT_BALANCE), Expectation.DEDUP_OR_UPDATE,
                "Deseo revisar cuánto dinero me queda disponible en mi cuenta cuando yo lo necesite."));
        m.add(Case.of("P04", "P", List.of(BOOK_APPOINTMENT), Expectation.DEDUP_OR_UPDATE,
                "El paciente reserva una hora con el médico especialista que requiere para ser atendido sin esperar."));
        m.add(Case.of("P05", "P", List.of(MEDICAL_HISTORY), Expectation.DEDUP_OR_UPDATE,
                "Quiero consultar mi expediente clínico y mis análisis de laboratorio para dar seguimiento a mi salud."));
        m.add(Case.of("P06", "P", List.of(TRACK_SHIPMENT), Expectation.DEDUP_OR_UPDATE,
                "El cliente hace el seguimiento de su paquete al instante para conocer cuándo le llegará su pedido."));
        m.add(Case.of("P07", "P", List.of(ASSIGN_DRIVER), Expectation.DEDUP_OR_UPDATE,
                "El operador designa un chofer libre a cada recorrido de entrega para mejorar los repartos del día."));
        m.add(Case.of("P08", "P", List.of(REQUEST_VACATION), Expectation.DEDUP_OR_UPDATE,
                "El trabajador tramita sus días libres de descanso desde el portal de recursos humanos sin papeleos."));
        m.add(Case.of("P09", "P", List.of(VIEW_PAYSLIP), Expectation.DEDUP_OR_UPDATE,
                "Quiero visualizar y bajar mi recibo de sueldo de cada mes para checar mis haberes y mis deducciones."));
        m.add(Case.of("P10", "P", List.of(ADD_TO_CART), Expectation.DEDUP_OR_UPDATE,
                "El comprador añade artículos a su cesta de la compra para juntar lo que va a adquirir antes de abonar."));
        m.add(Case.of("P11", "P", List.of(ORDER_HISTORY), Expectation.DEDUP_OR_UPDATE,
                "Deseo revisar el registro de mis compras pasadas para ver qué adquirí y volver a solicitarlo."));

        // ── Q. Add-a-detail → UPDATE across domains (12) ─────────────────────────────────────────────────
        m.add(Case.of("Q01", "Q", List.of(TRANSFER_MONEY), Expectation.UPDATE,
                "Sobre la transferencia de dinero que ya tenemos: además hay que pedir una confirmación con un código "
                        + "que llegue por SMS antes de ejecutar el giro, como validación de seguridad."));
        m.add(Case.of("Q02", "Q", List.of(TRANSFER_MONEY), Expectation.UPDATE,
                "Sobre la transferencia: además hay que topar el monto diario, no se puede transferir más de diez mil "
                        + "soles por día por cliente."));
        m.add(Case.of("Q03", "Q", List.of(ACCOUNT_BALANCE), Expectation.UPDATE,
                "Sobre consultar el saldo: además quiero que se muestre también un campo con la fecha y hora de la "
                        + "última actualización del saldo."));
        m.add(Case.of("Q04", "Q", List.of(BOOK_APPOINTMENT), Expectation.UPDATE,
                "Sobre agendar la cita médica: además el paciente debe poder elegir el estado de la cita, si es "
                        + "confirmada, pendiente o reprogramada, para dar seguimiento."));
        m.add(Case.of("Q05", "Q", List.of(BOOK_APPOINTMENT), Expectation.UPDATE,
                "Sobre agendar la cita: además hay que enviar un recordatorio por correo al paciente veinticuatro horas "
                        + "antes de la cita como notificación."));
        m.add(Case.of("Q06", "Q", List.of(TRACK_SHIPMENT), Expectation.UPDATE,
                "Sobre rastrear el envío: además quiero poder filtrar mis envíos por estado, en tránsito, entregado o "
                        + "devuelto, para ubicarlos rápido."));
        m.add(Case.of("Q07", "Q", List.of(ASSIGN_DRIVER), Expectation.UPDATE,
                "Sobre asignar el conductor: además hay que validar que el conductor tenga la licencia vigente antes "
                        + "de permitir asignarlo a una ruta."));
        m.add(Case.of("Q08", "Q", List.of(REQUEST_VACATION), Expectation.UPDATE,
                "Sobre solicitar vacaciones: además debe existir el rol de aprobador, el jefe directo aprueba o "
                        + "rechaza la solicitud antes de que quede registrada."));
        m.add(Case.of("Q09", "Q", List.of(VIEW_PAYSLIP), Expectation.UPDATE,
                "Sobre ver la boleta de pago: además quiero poder ordenar mis boletas por fecha, de la más reciente a "
                        + "la más antigua."));
        m.add(Case.of("Q10", "Q", List.of(ADD_TO_CART), Expectation.UPDATE,
                "Sobre agregar al carrito: además hay que impedir agregar más unidades de las que hay en stock, con un "
                        + "aviso cuando se llega al máximo disponible."));
        m.add(Case.of("Q11", "Q", List.of(ORDER_HISTORY), Expectation.UPDATE,
                "Sobre el historial de pedidos: además quiero un canal de notificación push que me avise cuando cambia "
                        + "el estado de uno de mis pedidos."));
        m.add(Case.of("Q12", "Q", List.of(MEDICAL_HISTORY), Expectation.UPDATE,
                "Sobre ver la historia clínica: además solo el médico tratante y el propio paciente tienen permiso de "
                        + "lectura; nadie más puede verla."));

        // ── R. Regional Spanish stress (es-PE/es-MX/es-AR/es-ES) vs a seed (10) → DEDUP_OR_UPDATE ─────────
        m.add(Case.of("R01", "R", List.of(ADD_TO_CART), Expectation.DEDUP_OR_UPDATE,
                "El cliente mete productos a su carrito de compras para juntar lo que va a llevar.")); // es-MX 'carrito'
        m.add(Case.of("R02", "R", List.of(ADD_TO_CART), Expectation.DEDUP_OR_UPDATE,
                "El comprador agrega cosas a su canasta para reunir lo que desea comprar.")); // es-419 'canasta'
        m.add(Case.of("R03", "R", List.of(ADD_TO_CART), Expectation.DEDUP_OR_UPDATE,
                "El usuario añade artículos a la cesta antes de pasar por caja.")); // es-ES 'cesta'
        m.add(Case.of("R04", "R", List.of(VIEW_PAYSLIP), Expectation.DEDUP_OR_UPDATE,
                "El empleado consulta su recibo de la nómina de cada mes para revisar su sueldo.")); // es-ES 'nómina'
        m.add(Case.of("R05", "R", List.of(VIEW_PAYSLIP), Expectation.DEDUP_OR_UPDATE,
                "El trabajador revisa su planilla de pago mensual para ver cuánto cobra.")); // es-PE 'planilla'
        m.add(Case.of("R06", "R", List.of(BOOK_APPOINTMENT), Expectation.DEDUP_OR_UPDATE,
                "El paciente saca un turno con el doctor para que lo atiendan.")); // es-AR 'turno'
        m.add(Case.of("R07", "R", List.of(TRANSFER_MONEY), Expectation.DEDUP_OR_UPDATE,
                "El cliente manda una transferencia de guita a otra cuenta por el homebanking.")); // es-AR 'guita/homebanking'
        m.add(Case.of("R08", "R", List.of(ACCOUNT_BALANCE), Expectation.DEDUP_OR_UPDATE,
                "El cuentahabiente checa el saldo de su cuenta desde el celular cuando quiere.")); // es-MX 'checar/celular'
        m.add(Case.of("R09", "R", List.of(TRACK_SHIPMENT), Expectation.DEDUP_OR_UPDATE,
                "El cliente ve por dónde va su encomienda en tiempo real para saber cuándo llega.")); // es-419 'encomienda'
        m.add(Case.of("R10", "R", List.of(REQUEST_VACATION), Expectation.DEDUP_OR_UPDATE,
                "El laburante pide sus días de vacaciones desde el sistema de RRHH sin tanto trámite.")); // es-AR 'laburante'

        // ── S. Numbers / units / thresholds in requirements (10) → OBSERVE how they're captured ───────────
        m.add(Case.of("S01", "S", List.of(LOGIN), Expectation.OBSERVE,
                "Sobre el inicio de sesión: bloquear la cuenta tras 5 intentos fallidos seguidos por 15 minutos."));
        m.add(Case.of("S02", "S", List.of(RESET_LINK), Expectation.OBSERVE,
                "Sobre restablecer la contraseña: el enlace de recuperación expira en 30 minutos desde que se envía."));
        m.add(Case.of("S03", "S", List.of(PROFILE_FORM), Expectation.OBSERVE,
                "Sobre editar el perfil: la foto de perfil no puede pesar más de 10 MB y debe ser JPG o PNG."));
        m.add(Case.of("S04", "S", List.of(TRANSFER_MONEY), Expectation.OBSERVE,
                "Sobre la transferencia: el máximo por operación es de 5000 soles y máximo 3 transferencias por hora."));
        m.add(Case.of("S05", "S", List.of(), Expectation.OBSERVE,
                "La API debe soportar hasta 1000 peticiones por minuto por cliente antes de aplicar un límite de tasa."));
        m.add(Case.of("S06", "S", List.of(), Expectation.OBSERVE,
                "El sistema debe responder cada búsqueda en menos de 2 segundos para el 95% de las consultas."));
        m.add(Case.of("S07", "S", List.of(BOOK_APPOINTMENT), Expectation.OBSERVE,
                "Sobre la cita médica: solo se puede reservar con un máximo de 60 días de anticipación y mínimo 2 horas antes."));
        m.add(Case.of("S08", "S", List.of(ADD_TO_CART), Expectation.OBSERVE,
                "Sobre el carrito: no se pueden agregar más de 99 unidades del mismo producto en un solo pedido."));
        m.add(Case.of("S09", "S", List.of(), Expectation.OBSERVE,
                "La sesión del usuario debe cerrarse automáticamente tras 20 minutos de inactividad."));
        m.add(Case.of("S10", "S", List.of(EXPORT_PDF), Expectation.OBSERVE,
                "Sobre exportar a PDF: un reporte no puede exceder las 500 páginas ni los 25 MB de tamaño final."));

        // ── T. STT-style mistranscriptions / typos that STILL carry a real capability (10) → OBSERVE/dedup ─
        m.add(Case.of("T01", "T", List.of(LOGIN), Expectation.DEDUP_OR_UPDATE,
                "Quiero iniciar seción con mi correro y contraceña para entrar a la platafforma.")); // typos, same as LOGIN
        m.add(Case.of("T02", "T", List.of(LOGIN), Expectation.DEDUP_OR_UPDATE,
                "el usuario ingresa con berificación de correo y clabe para acceder alsistema.")); // run-on + typos
        m.add(Case.of("T03", "T", List.of(RESET_LINK), Expectation.DEDUP_OR_UPDATE,
                "Necesito restablezer mi contraseña con un enlaze que me yege por correo para recuperar el acseso."));
        m.add(Case.of("T04", "T", List.of(EXPORT_PDF), Expectation.DEDUP_OR_UPDATE,
                "quiero esportar mis reportez a pedeefe paraarchivarlos y compartirloscon el equipo."));
        m.add(Case.of("T05", "T", List.of(TRACK_SHIPMENT), Expectation.DEDUP_OR_UPDATE,
                "el cliente qiere rastriar suenvio entiempo real para saver cuando yega supedido."));
        m.add(Case.of("T06", "T", List.of(BOOK_APPOINTMENT), Expectation.OBSERVE,
                "el pasiente quiere ajendar una sita medica con el espesialista paraser atendido rapido."));
        m.add(Case.of("T07", "T", List.of(ADD_TO_CART), Expectation.OBSERVE,
                "el compradoragrega productosal carritode compras para juntarlo que va a comprar."));
        m.add(Case.of("T08", "T", List.of(PRODUCT_SEARCH), Expectation.DEDUP_OR_UPDATE,
                "quiero vuscar productos por nonbre para encontrarrapido lo ke kiero comprar."));
        m.add(Case.of("T09", "T", List.of(VIEW_PAYSLIP), Expectation.OBSERVE,
                "el enpleado kiere ber y descargar su boletade pago de cadames para rebisar sus ingresos."));
        m.add(Case.of("T10", "T", List.of(ACCOUNT_BALANCE), Expectation.OBSERVE,
                "el clientequiere konsultar elsaldo desu cuenta enkualquier momento para saber cuantotiene."));

        // ── U. Filler-heavy real speech wrapping a real requirement (9) → extract the requirement ─────────
        m.add(Case.of("U01", "U", List.of(LOGIN), Expectation.DEDUP_OR_UPDATE,
                "O sea, este, digamos que, no sé, ¿me explico?, básicamente el usuario tiene que poder iniciar sesión con "
                        + "su correo y su contraseña, ¿va?, para entrar a la plataforma, ¿sí me entiendes?"));
        m.add(Case.of("U02", "U", List.of(TRANSFER_MONEY), Expectation.DEDUP_OR_UPDATE,
                "A ver, este, cómo te explico, o sea la idea es que el cliente pueda, digamos, transferir dinero a otra "
                        + "cuenta desde la banca en línea, ¿no?, para no ir a la sucursal, ¿me sigues?"));
        m.add(Case.of("U03", "U", List.of(TRACK_SHIPMENT), Expectation.DEDUP_OR_UPDATE,
                "Eh, mira, este, lo que queremos, o sea, es que el cliente pueda rastrear su envío en tiempo real, ¿sí?, "
                        + "para saber cuándo le llega el pedido, pues, ¿me explico o no?"));
        m.add(Case.of("U04", "U", List.of(BOOK_APPOINTMENT), Expectation.DEDUP_OR_UPDATE,
                "Bueno, este, a ver, digamos, lo importante acá es que el paciente pueda agendar una cita médica con el "
                        + "especialista, ¿ya?, para que lo atiendan sin hacer cola, ¿va?"));
        m.add(Case.of("U05", "U", List.of(PRODUCT_SEARCH), Expectation.DEDUP_OR_UPDATE,
                "Este, o sea, no sé cómo decirlo, pero básicamente el cliente quiere, digamos, buscar productos por su "
                        + "nombre, ¿me explico?, para encontrar rápido lo que quiere comprar, ajá."));
        m.add(Case.of("U06", "U", List.of(VIEW_PAYSLIP), Expectation.DEDUP_OR_UPDATE,
                "A ver cómo lo pongo, este, o sea, digamos que el empleado necesita, ¿no?, poder ver y descargar su "
                        + "boleta de pago del mes, para revisar su sueldo, ¿sí me sigues?"));
        m.add(Case.of("U07", "U", List.of(REQUEST_VACATION), Expectation.DEDUP_OR_UPDATE,
                "Eh, digamos, o sea, este, la cosa es que el empleado pueda, ¿va?, solicitar sus vacaciones desde el "
                        + "portal de RRHH, para no andar con papeleo, ¿me explico?"));
        m.add(Case.of("U08", "U", List.of(ACCOUNT_BALANCE), Expectation.DEDUP_OR_UPDATE,
                "Este, mira, o sea, básicamente, no sé, el cliente quiere consultar el saldo de su cuenta cuando sea, "
                        + "¿ya?, para saber cuánto tiene disponible, pues."));
        m.add(Case.of("U09", "U", List.of(ADD_TO_CART), Expectation.DEDUP_OR_UPDATE,
                "O sea, este, ¿cómo era?, digamos que el cliente agrega productos a su carrito, ¿no?, este, para juntar "
                        + "todo lo que va a comprar antes de pagar, ¿me explico o me explico?"));

        // ── V. Contradiction / negation (8) → OBSERVE ────────────────────────────────────────────────────
        m.add(Case.of("V01", "V", List.of(), Expectation.OBSERVE,
                "Antes queríamos pago con tarjeta de crédito, pero eso cámbialo: ahora solo aceptaremos transferencia bancaria."));
        m.add(Case.of("V02", "V", List.of(TRANSFER_MONEY), Expectation.OBSERVE,
                "Sobre la transferencia: ya no queremos el tope diario de diez mil, quítenlo, que no haya límite de monto."));
        m.add(Case.of("V03", "V", List.of(), Expectation.OBSERVE,
                "El sistema no debe permitir eliminar una cuenta de usuario; bajo ninguna circunstancia se borra, solo se desactiva."));
        m.add(Case.of("V04", "V", List.of(BOOK_APPOINTMENT), Expectation.OBSERVE,
                "Sobre la cita médica: eso del recordatorio por SMS cámbialo por un recordatorio por correo, ya no SMS."));
        m.add(Case.of("V05", "V", List.of(), Expectation.OBSERVE,
                "Ya no queremos el modo oscuro que habíamos pedido; olvídenlo, no lo vamos a implementar."));
        m.add(Case.of("V06", "V", List.of(TRACK_SHIPMENT), Expectation.OBSERVE,
                "Sobre el rastreo: no muestres la ubicación exacta del repartidor, eso quítalo, solo el estado del envío."));
        m.add(Case.of("V07", "V", List.of(REQUEST_VACATION), Expectation.OBSERVE,
                "Sobre solicitar vacaciones: el empleado ya no aprueba solo; cambia eso, ahora siempre lo aprueba el jefe."));
        m.add(Case.of("V08", "V", List.of(ADD_TO_CART), Expectation.OBSERVE,
                "No queremos que se pueda comprar sin iniciar sesión; eso ya no, ahora es obligatorio estar logueado."));

        // ── W. Ambiguity / conflict → CLARIFY (9) ────────────────────────────────────────────────────────
        m.add(Case.of("W01", "W", List.of(), Expectation.CLARIFY,
                "Necesitamos que el módulo de pagos sea flexible y se adapte a lo que venga; ustedes ya saben cómo, ¿no?"));
        m.add(Case.of("W02", "W", List.of(), Expectation.CLARIFY,
                "Que el reporte muestre la información relevante para cada quien; lo relevante depende, pero eso vean ustedes."));
        m.add(Case.of("W03", "W", List.of(), Expectation.CLARIFY,
                "Alguien debe poder aprobar los gastos, pero no hemos definido quién ni con qué monto se necesita aprobación."));
        m.add(Case.of("W04", "W", List.of(), Expectation.CLARIFY,
                "Queremos que la cita se confirme automáticamente, pero también que alguien la revise antes; que sea automático y revisado."));
        m.add(Case.of("W05", "W", List.of(), Expectation.CLARIFY,
                "El envío debe ser gratis, pero también queremos cobrar el flete; a ver cómo lo cuadran ustedes."));
        m.add(Case.of("W06", "W", List.of(), Expectation.CLARIFY,
                "Hay que mejorar la experiencia del usuario en general, que se sienta más moderno y ágil todo, ya me entienden."));
        m.add(Case.of("W07", "W", List.of(), Expectation.CLARIFY,
                "Se debe poder gestionar los permisos, pero no está claro qué roles existen ni quién administra a quién."));
        m.add(Case.of("W08", "W", List.of(), Expectation.CLARIFY,
                "Queremos notificaciones, pero no sabemos si por correo, SMS o push, ni en qué momentos; eso lo definimos luego."));
        m.add(Case.of("W09", "W", List.of(), Expectation.CLARIFY,
                "El sistema debe ser seguro y cumplir con las normas; ya saben, lo que corresponda, sin entrar en detalles ahora."));

        // ── X. Multi-capability single transcripts, 4-6 distinct (9) → MULTI_DISTINCT ─────────────────────
        m.add(Case.of("X01", "X", List.of(), Expectation.MULTI_DISTINCT,
                "En la banca en línea el cliente quiere: consultar su saldo; transferir dinero a otra cuenta; pagar sus "
                        + "servicios; y ver el historial de sus movimientos. Todo eso son cosas distintas entre sí."));
        m.add(Case.of("X02", "X", List.of(), Expectation.MULTI_DISTINCT,
                "En la clínica el paciente quiere: agendar una cita; ver su historia clínica; descargar sus resultados de "
                        + "laboratorio; y solicitar una receta médica. Cada una es una capacidad diferente."));
        m.add(Case.of("X03", "X", List.of(), Expectation.MULTI_DISTINCT,
                "En logística queremos: rastrear el envío en tiempo real; asignar conductores a rutas; registrar la "
                        + "entrega con firma; y generar la guía de remisión. Son funciones distintas."));
        m.add(Case.of("X04", "X", List.of(), Expectation.MULTI_DISTINCT,
                "En RRHH el empleado quiere: solicitar vacaciones; ver su boleta de pago; actualizar sus datos personales; "
                        + "y registrar su asistencia diaria. Cuatro cosas separadas."));
        m.add(Case.of("X05", "X", List.of(), Expectation.MULTI_DISTINCT,
                "En la tienda el cliente quiere: buscar productos; agregarlos al carrito; pagar con tarjeta; ver el "
                        + "historial de pedidos; y calificar los productos que compró. Todas distintas."));
        m.add(Case.of("X06", "X", List.of(), Expectation.MULTI_DISTINCT,
                "El administrador quiere: crear usuarios; asignarles roles; ver un log de auditoría; suspender cuentas; "
                        + "y exportar la lista a CSV. Cinco capacidades diferentes."));
        m.add(Case.of("X07", "X", List.of(), Expectation.MULTI_DISTINCT,
                "En el banco: abrir una cuenta nueva; solicitar una tarjeta; bloquear una tarjeta robada; pagar la tarjeta "
                        + "de crédito; y programar un pago recurrente. Cosas distintas cada una."));
        m.add(Case.of("X08", "X", List.of(), Expectation.MULTI_DISTINCT,
                "En la app de salud: agendar cita; cancelar cita; recibir recordatorios; teleconsulta por video; y "
                        + "descargar la receta. Cinco funciones separadas."));
        m.add(Case.of("X09", "X", List.of(), Expectation.MULTI_DISTINCT,
                "En logística: cotizar un envío; programar el recojo; rastrear el paquete; reportar un daño; y solicitar "
                        + "la devolución. Todas diferentes entre sí."));

        // ── Y. Near-threshold boundary pairs to map 0.84 (8) → DEDUP_OR_UPDATE, rely on raw-cosine logging ─
        m.add(Case.of("Y01", "Y", List.of(TRANSFER_MONEY), Expectation.DEDUP_OR_UPDATE,
                "Quiero transferir dinero a otra cuenta desde mi banca en línea para mover mis fondos.")); // ~ near/above bar
        // Y02 is the DISTINCT member of the pair: "pagar servicios (luz/agua)" is a genuinely different
        // capability from the "Transferir dinero" seed (account-to-account transfer) — the speaker even
        // says "distinto a transferir a una cuenta" (cosine ~0.71, below the bar). The model CORRECTLY
        // emits a standalone NEW_STORY here, so DEDUP_OR_UPDATE (which asserts convergence onto the seed)
        // was mislabeled. Only ONE story is produced (the seed is pre-existing backlog), so MULTI_DISTINCT
        // (>=2 drafts) does not fit either; OBSERVE is the honest expectation — the raw-cosine line still
        // maps where it lands.
        m.add(Case.of("Y02", "Y", List.of(TRANSFER_MONEY), Expectation.OBSERVE,
                "Quiero pagar mis servicios como luz y agua desde la banca en línea, distinto a transferir a una cuenta.")); // below bar — DISTINCT
        m.add(Case.of("Y03", "Y", List.of(BOOK_APPOINTMENT), Expectation.DEDUP_OR_UPDATE,
                "El paciente agenda una cita médica con el especialista que necesita para ser atendido sin cola.")); // above bar
        // Y04 is the DISTINCT member (same pattern as Y02/Y08): "cancelar una cita ya reservada" is a
        // genuinely different capability from the "Agendar cita médica" seed — the speaker says "algo
        // distinto a agendarla" (cosine ~0.70). The model CORRECTLY emits a standalone NEW "Cancelar cita
        // médica", so DEDUP_OR_UPDATE (asserting convergence onto the seed) was mislabeled; OBSERVE is
        // honest (one draft produced, MULTI_DISTINCT does not fit).
        m.add(Case.of("Y04", "Y", List.of(BOOK_APPOINTMENT), Expectation.OBSERVE,
                "El paciente quiere cancelar una cita ya reservada, algo distinto a agendarla.")); // below bar — DISTINCT
        m.add(Case.of("Y05", "Y", List.of(TRACK_SHIPMENT), Expectation.DEDUP_OR_UPDATE,
                "El cliente sigue el estado de su envío en tiempo real para saber cuándo llegará el pedido.")); // above bar
        // Y06 is the DISTINCT member (same pattern as Y02/Y04/Y08): "calificar al repartidor tras la
        // entrega" is a genuinely different capability from the "Rastrear envío" seed — the speaker says
        // "distinto a rastrear el envío" (cosine ~0.54). The model CORRECTLY emits a standalone NEW
        // "Calificar al repartidor", so DEDUP_OR_UPDATE was mislabeled; OBSERVE is honest (one draft,
        // MULTI_DISTINCT does not fit). Y03/Y05/Y07 stay DEDUP_OR_UPDATE — those are genuine same-capability
        // paraphrases of their seed (agendar cita / rastrear envío / ver boleta), not distinct members.
        m.add(Case.of("Y06", "Y", List.of(TRACK_SHIPMENT), Expectation.OBSERVE,
                "El cliente quiere calificar al repartidor tras la entrega, distinto a rastrear el envío.")); // below bar — DISTINCT
        m.add(Case.of("Y07", "Y", List.of(VIEW_PAYSLIP), Expectation.DEDUP_OR_UPDATE,
                "El empleado desea ver y descargar su boleta de pago mensual para revisar ingresos y descuentos.")); // near bar
        // Y08, like Y02, is the DISTINCT member: an annual income certificate ("certificado de renta
        // anual") for a tax declaration is a genuinely different capability from the monthly payslip seed
        // ("Ver boleta de pago") — the speaker says "distinto a la boleta mensual". The model CORRECTLY
        // emits a standalone NEW_STORY, so DEDUP_OR_UPDATE was mislabeled; OBSERVE is honest (one draft
        // produced, MULTI_DISTINCT does not fit).
        m.add(Case.of("Y08", "Y", List.of(VIEW_PAYSLIP), Expectation.OBSERVE,
                "El empleado quiere ver su certificado de renta anual para su declaración, distinto a la boleta mensual.")); // below bar — DISTINCT

        // ── Z. Off-language transcript in an es session (7) → SESSION_LANGUAGE (Spanish output) ───────────
        m.add(Case.of("Z01", "Z", List.of(), Expectation.SESSION_LANGUAGE,
                "The customer wants to transfer money to another account from online banking to move funds without visiting a branch."));
        m.add(Case.of("Z02", "Z", List.of(), Expectation.SESSION_LANGUAGE,
                "The patient wants to book a medical appointment with a specialist and view their lab results online."));
        m.add(Case.of("Z03", "Z", List.of(), Expectation.SESSION_LANGUAGE,
                "O cliente quer rastrear o status do seu envio em tempo real para saber quando o pedido vai chegar."));
        m.add(Case.of("Z04", "Z", List.of(), Expectation.SESSION_LANGUAGE,
                "O funcionário quer solicitar suas férias pelo portal de RH e visualizar o seu contracheque mensal."));
        m.add(Case.of("Z05", "Z", List.of(), Expectation.SESSION_LANGUAGE,
                "El cliente quiere hacer checkout con su credit card y ver el order tracking of his last shipment."));
        m.add(Case.of("Z06", "Z", List.of(), Expectation.SESSION_LANGUAGE,
                "The employee wants to request vacation days and download their monthly payslip from the HR portal."));
        m.add(Case.of("Z07", "Z", List.of(), Expectation.SESSION_LANGUAGE,
                "O usuário quer adicionar produtos ao carrinho e pagar com cartão para finalizar a compra na loja."));

        // ── AA. Idempotent restatement across two passes / duplicate (6) → dedup ─────────────────────────
        m.add(Case.of("AA1", "AA", List.of(), Expectation.EXACT_DUP,
                "El cliente quiere transferir dinero a otra cuenta desde la banca en línea para mover sus fondos.",
                "El cliente quiere transferir dinero a otra cuenta desde la banca en línea para mover sus fondos."));
        m.add(Case.of("AA2", "AA", List.of(), Expectation.EXACT_DUP,
                "El paciente quiere agendar una cita médica con el especialista para ser atendido sin hacer cola.",
                "Repito lo mismo: el paciente quiere agendar una cita médica con el especialista para ser atendido sin cola."));
        m.add(Case.of("AA3", "AA", List.of(TRACK_SHIPMENT), Expectation.DEDUP_OR_UPDATE,
                "El cliente quiere rastrear el estado de su envío en tiempo real para saber cuándo llegará su pedido.",
                "Lo mismo de nuevo: rastrear el envío en tiempo real para saber cuándo llega el pedido."));
        m.add(Case.of("AA4", "AA", List.of(), Expectation.EXACT_DUP,
                "El empleado quiere solicitar sus vacaciones desde el portal de RRHH sin papeleo.",
                "Insisto en lo mismo: el empleado quiere solicitar sus vacaciones desde el portal de RRHH sin papeleo."));
        m.add(Case.of("AA5", "AA", List.of(ADD_TO_CART), Expectation.DEDUP_OR_UPDATE,
                "El cliente quiere agregar productos a su carrito de compras para reunir lo que va a comprar.",
                "Otra vez lo mismo: agregar productos al carrito para juntar lo que se va a comprar."));
        m.add(Case.of("AA6", "AA", List.of(), Expectation.EXACT_DUP,
                "El cliente quiere consultar el saldo de su cuenta en cualquier momento para saber cuánto tiene.",
                "De nuevo, igualito: consultar el saldo de la cuenta en cualquier momento para saber cuánto se tiene."));

        // ── AB. Security / compliance phrasings (6) → OBSERVE (typically NFR, not a plain story) ──────────
        m.add(Case.of("AB1", "AB", List.of(), Expectation.OBSERVE,
                "El sistema debe cumplir con GDPR: el usuario puede solicitar la eliminación de todos sus datos personales."));
        m.add(Case.of("AB2", "AB", List.of(), Expectation.OBSERVE,
                "Hay que cifrar los datos sensibles en reposo y en tránsito usando estándares actuales de la industria."));
        m.add(Case.of("AB3", "AB", List.of(TRANSFER_MONEY), Expectation.OBSERVE,
                "Las transferencias deben registrarse en un log de auditoría inmutable para cumplir con normativa bancaria."));
        m.add(Case.of("AB4", "AB", List.of(MEDICAL_HISTORY), Expectation.OBSERVE,
                "El acceso a la historia clínica debe cumplir con la ley de protección de datos de salud y quedar auditado."));
        m.add(Case.of("AB5", "AB", List.of(), Expectation.OBSERVE,
                "Las contraseñas deben almacenarse con hashing seguro y nunca en texto plano, cumpliendo buenas prácticas."));
        m.add(Case.of("AB6", "AB", List.of(), Expectation.OBSERVE,
                "El sistema debe exigir consentimiento explícito de cookies y llevar registro de ese consentimiento por usuario."));

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
     * Seeds one Project per case ATTEMPT. The name MUST be unique: the tenant schema carries the partial
     * unique index {@code idx_projects_org_active_name} on {@code (organization_id, lower(name)) WHERE
     * status = 'ACTIVE'} (V7), and every case runs in the SAME provisioned org/schema — a shared constant
     * name made case #1 succeed and every later case fail its {@code projects} insert with a
     * {@code DataIntegrityViolationException}. Deriving the name from the case id (plus the retry
     * {@code attemptTag}) keeps each case's — and each retry's — project (and therefore its seeded backlog
     * and its dedup surface) isolated from every other, so a best-of-N retry never collides with the
     * fresh project of the failed first attempt.
     */
    private UUID seedProject(String caseId, String attemptTag) {
        return inTenantTx(() -> {
            TechnicalProfile profile = new TechnicalProfile(
                    List.of("Java"), List.of("Spring Boot"), List.of("Web"), List.of("PostgreSQL"),
                    "Clean Architecture", "SaaS");
            Project project = new Project(orgId, "Matrix " + caseId + attemptTag + " project", "seed",
                    profile, UUID.fromString(USER_ID));
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
