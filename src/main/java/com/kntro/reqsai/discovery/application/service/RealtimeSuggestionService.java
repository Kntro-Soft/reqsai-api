package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.*;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
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
     *              triggers never re-process the same segments.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void suggest(UUID sessionId, boolean force) {
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
            return;
        }

        int watermark = session.getLastSuggestedSequence();
        List<TranscriptSegment> pending = segments.findFinalBySessionIdAfter(sessionId, watermark);
        if (pending.isEmpty()) {
            log.debug("No new final segments past watermark {} for session {}", watermark, sessionId);
            return;
        }

        String text = pending.stream()
                .map(TranscriptSegment::getText)
                .collect(Collectors.joining(" "))
                .strip();

        if (text.isBlank() || !generation.isAvailable()) {
            log.debug("Nothing to generate (blank or generation unavailable) for session {}", sessionId);
            return;
        }

        // Cadence: stream, don't batch. Fire when enough NEW text has accrued OR enough time has
        // elapsed since the last pass with transcript still waiting — whichever comes first — never
        // with zero new content (guarded by the empty check above). `force` (stop flush) bypasses both.
        if (!force && !shouldGenerate(session, text.length())) {
            return;
        }

        int maxSequence = pending.getLast().getSequence();
        GenerationContext context = buildContext(session, text);
        GenerationResult result = generation.generate(text, session.getLanguage().value(), context);

        List<Suggestion> created = suggestionCreation.createSuggestions(result, sessionId, session.getProjectId());

        // Persist ONLY the watermark + cadence timestamp with a scoped UPDATE. Do NOT mutate + save the
        // whole aggregate here: this pass loaded the session seconds ago (before the LLM call), so a full
        // save would carry a stale last_sequence and clobber the value the concurrent transcript-append
        // path advanced meanwhile — corrupting the segment sequence and freezing live transcription.
        sessions.advanceSuggestionWatermark(sessionId, maxSequence, Instant.now());

        log.info("Realtime suggestion for session {}: {} suggestions from {} segments (watermark {} -> {}, force={})", sessionId, created.size(), pending.size(), watermark, maxSequence, force);
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
        UUID projectId = session.getProjectId();
        float[] queryEmbedding = tryEmbed(recentText);

        List<GenerationContext.StorySummary> backlog = retrieveBacklog(projectId, queryEmbedding).stream()
                .map(s -> new GenerationContext.StorySummary(
                        s.getId(), s.getTitle(), s.getRole(), s.getAction(), s.getBenefit()))
                .toList();
        List<GenerationContext.PendingSuggestion> alreadySuggested = pendingSuggestionSummaries(session.getId());

        if (log.isDebugEnabled()) {
            log.debug("Generation context for session {}: {} backlog stories {}; {} pending suggestions {}",
                    session.getId(), backlog.size(),
                    backlog.stream().map(s -> s.id() + ":'" + s.title() + "'").toList(),
                    alreadySuggested.size(),
                    alreadySuggested.stream().map(s -> s.id() + ":'" + s.summary() + "'").toList());
        }

        Optional<ProjectSnapshot> snapshot = queryEmbedding != null
                ? workspaceApi.findRelevantContext(projectId, queryEmbedding, contextTopK)
                : workspaceApi.findProjectSnapshot(projectId);
        return snapshot
                .map(s -> GenerationContext.from(s, backlog, alreadySuggested))
                .orElse(null);
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
     * One entry per PENDING suggestion of this session (id + story title or clarifying question). The
     * id lets the LLM target a still-pending story draft with {@code UPDATE_STORY}/{@code EDGE_CASE}
     * rather than re-emitting a near-duplicate NEW_STORY.
     */
    private List<GenerationContext.PendingSuggestion> pendingSuggestionSummaries(UUID sessionId) {
        List<Suggestion> pending = suggestions.findAllBySessionIdAndStatus(sessionId, SuggestionStatus.PENDING);
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
