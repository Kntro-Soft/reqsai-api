package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.codebase.api.CodeModuleView;
import com.kntro.reqsai.codebase.api.CodeOverview;
import com.kntro.reqsai.codebase.api.CodebaseModuleApi;
import com.kntro.reqsai.discovery.application.port.*;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionMode;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.shared.domain.support.Assert;
import com.kntro.reqsai.workspace.api.ProjectSnapshot;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates realtime AI suggestions for a live discovery session.
 *
 * <p>Triggered by {@code RealtimeSuggestionListener} on every finalized transcript segment. To
 * stream rather than batch, a pass runs when EITHER enough new characters have accrued past the
 * watermark ({@code discovery.realtime.min-transcript-chars}) OR enough seconds have elapsed since
 * the last pass with new transcript waiting ({@code discovery.realtime.max-transcript-age-seconds})
 * — whichever comes first, and never with zero new content. It retrieves the tail segments, enriches
 * the prompt with semantically relevant project context from the Workspace module, and routes the
 * LLM output through {@link SuggestionCreationService} — which applies embedding-based postprocessing
 * and persists each suggestion in its own transaction so the client receives one WebSocket push per
 * suggestion the moment it is persisted, not after the whole pass.
 *
 * <h2>Backlog grounding</h2>
 * The generation context always includes a slice of the project's existing stories (ids included)
 * so the LLM can classify overlapping requirements as {@code UPDATE_STORY}/{@code EDGE_CASE}
 * instead of near-duplicate {@code NEW_STORY}s:
 * <ol>
 *   <li><em>Preferred:</em> pgvector top-K stories nearest to the recent transcript, merged with
 *       the newest project stories (so stories accepted seconds ago — possibly not yet ranked or
 *       indexed — are still visible).</li>
 *   <li><em>Fallback:</em> when the embedding provider is unavailable/failing or nothing is
 *       indexed yet, the most recent project stories via a plain query — the backlog is never
 *       invisible to the model.</li>
 * </ol>
 * It also lists the session's own PENDING suggestions so the model does not re-suggest what the
 * analyst has not reviewed yet (overlapping transcript windows re-surface the same idea).
 *
 * <h2>Speakers</h2>
 * When the transcript is diarized, the window reaches the model as tagged speaker turns
 * ({@link SpeakerTranscriptFormatter}) with the names and client/team sides the analyst gave, so the
 * model prioritizes what the client says (US40).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimeSuggestionService {

    /** Newest stories merged into the vector result (in-session awareness) or used as fallback. */
    private static final int RECENT_STORIES = 10;
    /** Hard cap on backlog stories injected into the prompt. */
    private static final int MAX_CONTEXT_STORIES = 15;

    private final DiscoverySessionRepository sessions;
    private final TranscriptSegmentRepository segments;
    private final WorkspaceModuleApi workspaceApi;
    private final RequirementGenerationPort generation;
    private final SuggestionCreationService suggestionCreation;
    private final EmbeddingPort embeddingPort;
    private final UserStoryRepository stories;
    private final SuggestionRepository suggestions;
    private final UserStoryReindexService reindexService;
    private final SessionLockPort sessionLock;
    private final SessionSpeakerService sessionSpeakers;
    private final CodebaseModuleApi codebase;

    /** Modules of the client's connected code placed in each prompt (the most related to the window). */
    @Value("${discovery.realtime.code-top-k:4}")
    private int codeTopK;

    @Value("${discovery.realtime.context-top-k:5}")
    private int contextTopK;

    /**
     * How many loose-recall paraphrase candidates to surface to the LLM dedup/UPDATE judge, in
     * addition to the vector/recent backlog slice. Bounded so the prompt stays small even on a large
     * backlog.
     */
    @Value("${discovery.realtime.candidate-top-k:8}")
    private int candidateTopK;

    /**
     * Cosine-similarity recall floor for {@link #candidateTopK} candidate retrieval. Deliberately far
     * below the auto-dedup bar (0.84): the embedding gate cannot separate "same capability, different
     * words" (measured 0.55–0.82) from "genuinely distinct", so we recall generously at this floor and
     * let the LLM make the precise same-capability judgement per candidate.
     */
    @Value("${discovery.realtime.candidate-recall-threshold:0.50}")
    private double candidateRecallThreshold;

    @Value("${discovery.realtime.min-transcript-chars:180}")
    private int minTranscriptChars;

    /**
     * Time-based cadence fallback: once this many seconds have elapsed since the last pass with new
     * final transcript waiting, generate even if fewer than {@link #minTranscriptChars} have accrued —
     * so short back-and-forth exchanges stream out instead of arriving as one late batch.
     */
    @Value("${discovery.realtime.max-transcript-age-seconds:22}")
    private int maxTranscriptAgeSeconds;

    /**
     * How many times one pass asks the model about the SAME transcript window when it replies with
     * something that is not the JSON contract ({@link UnparseableGenerationException}: an empty reply, an
     * answer, code, prose). When every attempt is unparseable the window is skipped — the watermark moves
     * past it — so one window that consistently makes the model misbehave (e.g. a prompt-injection attempt
     * it obeys) cannot block every later suggestion of the session. Counted per pass, so there is no
     * per-session state to keep or clean up.
     */
    @Value("${discovery.realtime.unparseable-attempts-per-window:2}")
    private int unparseableAttemptsPerWindow;

    /** Incremental pass: generate only when enough new transcripts have accrued past the watermark. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void suggest(UUID sessionId) {
        suggest(sessionId, false);
    }

    /**
     * Processes the not-yet-suggested tail of the transcript (segments past the watermark).
     *
     * @param force when {@code true} (flush on stop) generate even if the accrued text is below the
     *              minimum — so the end of the meeting is never dropped. The watermark only advances
     *              on success, so a transient failure is retried rather than lost, and overlapping
     *              triggers never re-process the same segments. The one exception is a window whose
     *              model replies stay unparseable for {@link #unparseableAttemptsPerWindow} attempts:
     *              it is skipped (watermark advanced, nothing created) instead of blocking the session.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void suggest(UUID sessionId, boolean force) {
        run(sessionId, force, false);
    }

    /**
     * "Analizar ahora" (US46): analyzes the conversation accrued since the last pass right away,
     * whatever the session's suggestion mode and however little text there is. Only while the meeting
     * is being captured.
     *
     * @return how many suggestions the pass raised (0 when there was nothing new to analyze)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int analyzeNow(UUID sessionId) {
        DiscoverySession session = sessions.findById(sessionId)
                .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(sessionId));
        Assert.isTrue(session.isLive(), "status",
                "analyzing on demand requires RECORDING or PAUSED but was " + session.getStatus(),
                DiscoveryError.INVALID_SESSION_STATUS);
        return run(sessionId, true, true);
    }

    /**
     * One suggestion pass. An automatic pass ({@code onDemand=false}, from the transcript or the stop
     * flush) does nothing while the session is in {@link SuggestionMode#MANUAL}.
     */
    private int run(UUID sessionId, boolean force, boolean onDemand) {
        // Serialize passes of THIS session: overlapping REQUIRES_NEW passes (triggered ~seconds apart)
        // must not both read the PENDING set and watermark before the earlier one commits, or the
        // earlier pass's drafts are invisible to the later pass's dedup. Take the per-session advisory
        // lock (released on commit) BEFORE loading the session and reading the watermark/PENDING set, so
        // the whole critical section — watermark + dedup reads, generation, persistence, watermark
        // advance — runs only after the previous pass has committed and is visible.
        sessionLock.lockForSuggestion(sessionId);

        DiscoverySession session = sessions.findById(sessionId).orElse(null);
        if (session == null) {
            log.warn("Realtime suggestion skipped: session {} not found", sessionId);
            return 0;
        }
        if (!onDemand && session.getSuggestionMode() == SuggestionMode.MANUAL) {
            log.debug("Realtime suggestion skipped: session {} analyzes only on demand", sessionId);
            return 0;
        }

        int watermark = session.getLastSuggestedSequence();
        List<TranscriptSegment> pending = segments.findFinalBySessionIdAfter(sessionId, watermark);
        if (pending.isEmpty()) {
            log.debug("No new final segments past watermark {} for session {}", watermark, sessionId);
            return 0;
        }

        String text = pending.stream()
                .map(TranscriptSegment::getText)
                .collect(Collectors.joining(" "))
                .strip();

        if (text.isBlank() || !generation.isAvailable()) {
            log.debug("Nothing to generate (blank or generation unavailable) for session {}", sessionId);
            return 0;
        }

        // Cadence: stream, don't batch. Fire when enough NEW text has accrued OR enough time has
        // elapsed since the last pass with transcript still waiting — whichever comes first — never
        // with zero new content (guarded by the empty check above). `force` (stop flush) bypasses both.
        if (!force && !shouldGenerate(session, text.length())) {
            return 0;
        }

        int maxSequence = pending.getLast().getSequence();
        GenerationContext context = buildContext(session, text);
        Optional<GenerationResult> result = generateWindow(sessionId, promptTranscript(sessionId, pending, text),
                session.getLanguage().value(), context, watermark, maxSequence);

        List<Suggestion> created = result
                .map(r -> suggestionCreation.createSuggestions(r, sessionId, session.getProjectId(),
                        QuoteLocator.of(pending)))
                .orElse(List.of());

        // Persist ONLY the watermark + cadence timestamp with a scoped UPDATE. Do NOT mutate + save the
        // whole aggregate here: this pass loaded the session seconds ago (before the LLM call), so a full
        // save would carry a stale last_sequence and clobber the value the concurrent transcript-append
        // path advanced meanwhile — corrupting the segment sequence and freezing live transcription.
        sessions.advanceSuggestionWatermark(sessionId, maxSequence, Instant.now());

        log.info("Realtime suggestion for session {}: {} suggestions from {} segments (watermark {} -> {}, force={}, onDemand={})", sessionId, created.size(), pending.size(), watermark, maxSequence, force, onDemand);
        return created.size();
    }

    /**
     * The window as the model reads it. With diarization, one line per speaker turn tagged with the name and
     * side the analyst gave ({@code [Ana (Cliente)]: …}), so the prompt can put the client's needs first;
     * without it, the plain joined text, as before. The cadence and the backlog retrieval keep using the
     * plain text.
     */
    private String promptTranscript(UUID sessionId, List<TranscriptSegment> pending, String plainText) {
        if (!SpeakerTranscriptFormatter.hasSpeakers(pending)) {
            return plainText;
        }
        return SpeakerTranscriptFormatter.format(pending, sessionSpeakers.rosterOf(sessionId));
    }

    /**
     * Generates for one transcript window. Only an {@link UnparseableGenerationException} (the model replied,
     * but not with the JSON contract) is retried on the same window, up to
     * {@link #unparseableAttemptsPerWindow} attempts; when all of them fail the window is given up
     * ({@link Optional#empty()}) and a warning is logged, so the caller advances the watermark past it.
     * Any other failure (network, timeout, provider error) propagates unchanged on the first attempt — the
     * watermark stays put and the window is retried on the next pass, exactly as before.
     */
    private Optional<GenerationResult> generateWindow(UUID sessionId, String text, String language,
                                                      @Nullable GenerationContext context,
                                                      int watermark, int maxSequence) {
        int attempts = Math.max(1, unparseableAttemptsPerWindow);
        for (int attempt = 1; ; attempt++) {
            try {
                return Optional.of(generation.generate(text, language, context));
            } catch (UnparseableGenerationException e) {
                if (attempt >= attempts) {
                    log.warn("Realtime suggestion for session {}: {} consecutive unparseable model replies for "
                                    + "segments {}..{}; skipping that window so later suggestions are not blocked: {}",
                            sessionId, attempt, watermark + 1, maxSequence, e.getMessage());
                    return Optional.empty();
                }
                log.info("Realtime suggestion for session {}: unparseable model reply (attempt {}/{}) for segments "
                        + "{}..{}; retrying the same window: {}", sessionId, attempt, attempts, watermark + 1,
                        maxSequence, e.getMessage());
            }
        }
    }

    /**
     * Cadence decision for an incremental pass with {@code accruedChars} of new (past-watermark)
     * transcript already confirmed non-blank: generate when either the char threshold is reached or
     * the time-since-last-pass threshold has elapsed — whichever first. The very first pass of a
     * session ({@code lastSuggestedAt == null}) waits for the char threshold so a single opening word
     * does not trigger a pass; thereafter the elapsed-time fallback keeps short exchanges streaming.
     */
    private boolean shouldGenerate(DiscoverySession session, int accruedChars) {
        if (accruedChars >= minTranscriptChars) {
            return true;
        }
        Instant lastAt = session.getLastSuggestedAt();
        if (lastAt == null) {
            log.debug("Accrued {} chars (< {} min), no prior pass for session {}; waiting for more",
                    accruedChars, minTranscriptChars, session.getId());
            return false;
        }
        long elapsedSeconds = Duration.between(lastAt, Instant.now()).getSeconds();
        if (elapsedSeconds >= maxTranscriptAgeSeconds) {
            log.debug("Accrued {} chars (< {} min) but {}s elapsed (>= {}s) for session {}; generating",
                    accruedChars, minTranscriptChars, elapsedSeconds, maxTranscriptAgeSeconds, session.getId());
            return true;
        }
        log.debug("Accrued {} chars (< {} min) and only {}s elapsed (< {}s) for session {}; waiting",
                accruedChars, minTranscriptChars, elapsedSeconds, maxTranscriptAgeSeconds, session.getId());
        return false;
    }

    // ── Context building ──────────────────────────────────────────────────────

    private @Nullable GenerationContext buildContext(DiscoverySession session, String recentText) {
        List<Suggestion> pending = suggestions.findAllBySessionIdAndStatus(session.getId(), SuggestionStatus.PENDING);
        return buildContext(session.getProjectId(), recentText, pending, "session " + session.getId());
    }

    /**
     * Generation context for text outside a session pass, such as a requirement typed in the assistant
     * chat: the same project profile and backlog retrieval as a live pass. {@code pending} lists the
     * suggestions still awaiting review in that scope, so the model targets or skips them instead of
     * duplicating them. Empty when the project no longer exists.
     */
    public Optional<GenerationContext> contextFor(UUID projectId, String text, List<Suggestion> pending) {
        return Optional.ofNullable(buildContext(projectId, text, pending, "project " + projectId));
    }

    private @Nullable GenerationContext buildContext(UUID projectId, String recentText, List<Suggestion> pending,
                                                     String scope) {
        float[] queryEmbedding = tryEmbed(recentText);

        List<GenerationContext.StorySummary> backlog = retrieveBacklog(projectId, queryEmbedding).stream()
                .map(s -> new GenerationContext.StorySummary(
                        s.getId(), s.getTitle(), s.getRole(), s.getAction(), s.getBenefit()))
                .toList();
        List<GenerationContext.PendingSuggestion> alreadySuggested = pendingSuggestionSummaries(pending);

        if (log.isDebugEnabled()) {
            log.debug("Generation context for {}: {} backlog stories {}; {} pending suggestions {}",
                    scope, backlog.size(),
                    backlog.stream().map(s -> s.id() + ":'" + s.title() + "'").toList(),
                    alreadySuggested.size(),
                    alreadySuggested.stream().map(s -> s.id() + ":'" + s.summary() + "'").toList());
        }

        Optional<ProjectSnapshot> snapshot = queryEmbedding != null
                ? workspaceApi.findRelevantContext(projectId, queryEmbedding, contextTopK)
                : workspaceApi.findProjectSnapshot(projectId);
        return snapshot
                .map(s -> GenerationContext.from(s, backlog, alreadySuggested)
                        .withCode(codeContextFor(projectId, queryEmbedding, recentText)))
                .orElse(null);
    }

    /**
     * What the client's connected code says, for the prompt: the product overview and the modules most related
     * to the conversation, keyed {@code C1}, {@code C2}… for the model to cite. Null when no code is connected or
     * the lookup fails — the copilot then works as before.
     */
    private GenerationContext.@Nullable CodeContext codeContextFor(UUID projectId, float @Nullable [] queryEmbedding,
                                                                   String recentText) {
        try {
            Optional<CodeOverview> overview = codebase.findOverview(projectId);
            if (overview.isEmpty()) {
                return null;
            }
            List<CodeModuleView> modules = codebase.findRelevantModules(projectId, queryEmbedding, recentText,
                    codeTopK);
            List<GenerationContext.CodeModuleEntry> entries = new ArrayList<>(modules.size());
            for (int i = 0; i < modules.size(); i++) {
                CodeModuleView m = modules.get(i);
                entries.add(new GenerationContext.CodeModuleEntry("C" + (i + 1), m.repository(), m.path(), m.name(),
                        m.summary(), m.capabilities(), m.businessRules(), m.url()));
            }
            log.debug("Code context for project {}: {} modules {}", projectId, entries.size(),
                    entries.stream().map(e -> e.key() + ":" + e.name()).toList());
            return new GenerationContext.CodeContext(overview.get().overview(), entries);
        } catch (RuntimeException e) {
            log.debug("Code context skipped for project {}: {}", projectId, e.getMessage());
            return null;
        }
    }

    /**
     * Backlog slice for the prompt, nearest-first, capped at {@link #MAX_CONTEXT_STORIES}:
     * <ol>
     *   <li><em>Loose-recall paraphrase candidates</em> — up to {@link #candidateTopK} stories within
     *       the {@link #candidateRecallThreshold} similarity floor. These lead the list precisely so a
     *       synonym paraphrase of an existing story (cosine 0.55–0.82, below the auto-dedup bar) is
     *       always visible to the LLM as a candidate to UPDATE rather than duplicate.</li>
     *   <li><em>Vector top-K</em> nearest to the recent transcript (unthresholded, for domain grounding).</li>
     *   <li><em>Newest project stories</em> — in-session recency complement / fallback when vector
     *       search is unavailable or empty, so the backlog is never invisible to the model.</li>
     * </ol>
     */
    private List<UserStory> retrieveBacklog(UUID projectId, float @Nullable [] queryEmbedding) {
        List<UserStory> candidates = List.of();
        List<UserStory> similar = List.of();
        if (queryEmbedding != null) {
            // The provider just embedded the transcript successfully — give stories that missed
            // their embedding at write time a second chance before searching the vector index.
            reindexService.reindexPending(projectId);
            try {
                candidates = retrieveLooseRecallCandidates(projectId, queryEmbedding);
                similar = stories.findTopSimilar(projectId, queryEmbedding, contextTopK);
            } catch (RuntimeException e) {
                log.warn("Vector backlog retrieval failed for project {}; using recent stories only: {}",
                        projectId, e.getMessage());
            }
        }
        List<UserStory> recent = stories.findRecentByProjectId(projectId, RECENT_STORIES);

        // Candidates lead (highest UPDATE/dedup relevance), then vector top-K, then newest — dedup by id.
        Map<UUID, UserStory> merged = new LinkedHashMap<>();
        for (UserStory s : candidates) merged.putIfAbsent(s.getId(), s);
        for (UserStory s : similar) merged.putIfAbsent(s.getId(), s);
        for (UserStory s : recent) merged.putIfAbsent(s.getId(), s);
        return merged.values().stream().limit(MAX_CONTEXT_STORIES).toList();
    }

    /**
     * The loose-recall paraphrase candidates (id + similarity → full story), nearest first, best-effort:
     * a candidate whose story row can no longer be loaded (deleted between calls) is skipped.
     */
    private List<UserStory> retrieveLooseRecallCandidates(UUID projectId, float[] queryEmbedding) {
        List<UserStoryRepository.SimilarStory> hits =
                stories.findSimilarCandidates(projectId, queryEmbedding, candidateRecallThreshold, candidateTopK);
        List<UserStory> resolved = new ArrayList<>(hits.size());
        for (UserStoryRepository.SimilarStory hit : hits) {
            stories.findByIdAndProjectId(hit.storyId(), projectId).ifPresent(resolved::add);
        }
        return resolved;
    }

    /**
     * One entry per PENDING suggestion in scope (id + story title or clarifying question). The id lets
     * the LLM target a still-pending story draft with {@code UPDATE_STORY}/{@code EDGE_CASE} rather than
     * re-emitting a near-duplicate NEW_STORY.
     */
    private List<GenerationContext.PendingSuggestion> pendingSuggestionSummaries(List<Suggestion> pending) {
        List<GenerationContext.PendingSuggestion> summaries = new ArrayList<>(pending.size());
        for (Suggestion s : pending) {
            String summary = s.getType() == SuggestionType.CLARIFYING_QUESTION ? s.getQuestion() : s.getDraftTitle();
            if (summary != null && !summary.isBlank()) {
                summaries.add(new GenerationContext.PendingSuggestion(s.getId(), summary));
            }
        }
        return summaries;
    }

    /** Embeds the recent transcript, degrading to {@code null} (recent-stories fallback) on failure. */
    private float @Nullable [] tryEmbed(String text) {
        if (!embeddingPort.isAvailable()) {
            return null;
        }
        try {
            return embeddingPort.embed(text);
        } catch (RuntimeException e) {
            log.warn("Embedding the recent transcript failed; building context without vector search: {}",
                    e.getMessage());
            return null;
        }
    }
}
