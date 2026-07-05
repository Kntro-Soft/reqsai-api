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
 * <p>Triggered by {@code RealtimeSuggestionListener} after every N finalized transcript segments.
 * Retrieves the most recent segments, enriches the prompt with semantically relevant project
 * context from the Workspace module, and routes the LLM output through
 * {@link SuggestionCreationService} — which applies embedding-based postprocessing and persists
 * each suggestion in its own transaction so the client receives one WebSocket push per suggestion.
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

    @Value("${discovery.realtime.context-top-k:5}")
    private int contextTopK;

    @Value("${discovery.realtime.min-transcript-chars:300}")
    private int minTranscriptChars;

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

        if (!force && text.length() < minTranscriptChars) {
            log.debug("Accrued {} chars (< {} min) past watermark for session {}; waiting for more",
                    text.length(), minTranscriptChars, sessionId);
            return;
        }
        if (text.isBlank() || !generation.isAvailable()) {
            log.debug("Nothing to generate (blank or generation unavailable) for session {}", sessionId);
            return;
        }

        int maxSequence = pending.getLast().getSequence();
        GenerationContext context = buildContext(session, text);
        GenerationResult result = generation.generate(text, session.getLanguage().value(), context);

        List<Suggestion> created = suggestionCreation.createSuggestions(result, sessionId, session.getProjectId());

        session.advanceSuggestedSequence(maxSequence);
        sessions.save(session);

        log.info("Realtime suggestion for session {}: {} suggestions from {} segments (watermark {} -> {}, force={})", sessionId, created.size(), pending.size(), watermark, maxSequence, force);
    }

    // ── Context building ──────────────────────────────────────────────────────

    private @Nullable GenerationContext buildContext(DiscoverySession session, String recentText) {
        UUID projectId = session.getProjectId();
        float[] queryEmbedding = tryEmbed(recentText);

        List<GenerationContext.StorySummary> backlog = retrieveBacklog(projectId, queryEmbedding).stream()
                .map(s -> new GenerationContext.StorySummary(
                        s.getId(), s.getTitle(), s.getRole(), s.getAction(), s.getBenefit()))
                .toList();
        List<String> alreadySuggested = pendingSuggestionSummaries(session.getId());

        if (log.isDebugEnabled()) {
            log.debug("Generation context for session {}: {} backlog stories {}; {} pending suggestions {}",
                    session.getId(), backlog.size(),
                    backlog.stream().map(s -> s.id() + ":'" + s.title() + "'").toList(),
                    alreadySuggested.size(), alreadySuggested);
        }

        Optional<ProjectSnapshot> snapshot = queryEmbedding != null
                ? workspaceApi.findRelevantContext(projectId, queryEmbedding, contextTopK)
                : workspaceApi.findProjectSnapshot(projectId);
        return snapshot
                .map(s -> GenerationContext.from(s, backlog, alreadySuggested))
                .orElse(null);
    }

    /**
     * Backlog slice for the prompt: vector top-K nearest to the recent transcript merged with the
     * newest project stories (dedup by id, nearest/newest first, capped), or the newest stories
     * alone when vector search is unavailable or empty.
     */
    private List<UserStory> retrieveBacklog(UUID projectId, float @Nullable [] queryEmbedding) {
        List<UserStory> similar = List.of();
        if (queryEmbedding != null) {
            try {
                similar = stories.findTopSimilar(projectId, queryEmbedding, contextTopK);
            } catch (RuntimeException e) {
                log.warn("Vector backlog retrieval failed for project {}; using recent stories only: {}",
                        projectId, e.getMessage());
            }
        }
        List<UserStory> recent = stories.findRecentByProjectId(projectId, RECENT_STORIES);

        Map<UUID, UserStory> merged = new LinkedHashMap<>();
        for (UserStory s : similar) merged.putIfAbsent(s.getId(), s);
        for (UserStory s : recent) merged.putIfAbsent(s.getId(), s);
        return merged.values().stream().limit(MAX_CONTEXT_STORIES).toList();
    }

    /** One line per PENDING suggestion of this session (story title or clarifying question). */
    private List<String> pendingSuggestionSummaries(UUID sessionId) {
        List<Suggestion> pending = suggestions.findAllBySessionIdAndStatus(sessionId, SuggestionStatus.PENDING);
        List<String> summaries = new ArrayList<>(pending.size());
        for (Suggestion s : pending) {
            String summary = s.getType() == SuggestionType.CLARIFYING_QUESTION ? s.getQuestion() : s.getDraftTitle();
            if (summary != null && !summary.isBlank()) {
                summaries.add(summary);
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
