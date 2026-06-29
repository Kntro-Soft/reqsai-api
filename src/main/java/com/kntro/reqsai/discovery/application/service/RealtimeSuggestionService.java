package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.*;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimeSuggestionService {

    private final DiscoverySessionRepository sessions;
    private final TranscriptSegmentRepository segments;
    private final WorkspaceModuleApi workspaceApi;
    private final RequirementGenerationPort generation;
    private final SuggestionCreationService suggestionCreation;
    private final EmbeddingPort embeddingPort;

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
        GenerationContext context = buildContext(session.getProjectId(), text);
        GenerationResult result = generation.generate(text, session.getLanguage().value(), context);

        List<Suggestion> created = suggestionCreation.createSuggestions(result, sessionId, session.getProjectId());

        session.advanceSuggestedSequence(maxSequence);
        sessions.save(session);

        log.info("Realtime suggestion for session {}: {} suggestions from {} segments (watermark {} -> {}, force={})", sessionId, created.size(), pending.size(), watermark, maxSequence, force);
    }

    private GenerationContext buildContext(UUID projectId, String recentText) {
        if (embeddingPort.isAvailable()) {
            float[] queryEmbedding = embeddingPort.embed(recentText);
            return workspaceApi.findRelevantContext(projectId, queryEmbedding, contextTopK)
                    .map(GenerationContext::from)
                    .orElse(null);
        }
        return workspaceApi.findProjectSnapshot(projectId)
                .map(GenerationContext::from)
                .orElse(null);
    }
}
