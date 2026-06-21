package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.*;
import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.discovery.domain.model.TranscriptSegment;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates realtime user-story suggestions for a live discovery session.
 *
 * <p>Triggered by {@code RealtimeSuggestionListener}
 * after every N finalized transcript segment. Retrieves the most recent segments, enriches the
 * prompt with semantically relevant project context from the Workspace module (pgvector cosine
 * similarity), and persists any new stories through {@link StoryExtractionService} so each story
 * fires its own WebSocket notification.
 *
 * <p>When the embedding model is available, only the {@code topK} most relevant glossary terms
 * and constraints are included in the prompt. Without embeddings, the full lists are used.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimeSuggestionService {

    private final DiscoverySessionRepository sessions;
    private final TranscriptSegmentRepository segments;
    private final WorkspaceModuleApi workspaceApi;
    private final RequirementGenerationPort generation;
    private final StoryExtractionService storyExtraction;
    private final EmbeddingPort embeddingPort;

    @Value("${discovery.realtime.context-window:10}")
    private int contextWindow;

    @Value("${discovery.realtime.context-top-k:5}")
    private int contextTopK;

    @Value("${discovery.realtime.min-transcript-chars:300}")
    private int minTranscriptChars;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void suggest(UUID sessionId) {
        DiscoverySession session = sessions.findById(sessionId).orElse(null);
        if (session == null) {
            log.warn("Realtime suggestion skipped: session {} not found", sessionId);
            return;
        }

        List<TranscriptSegment> recent = segments.findRecentFinalBySessionId(sessionId, contextWindow);
        if (recent.isEmpty()) {
            log.debug("No final segments yet for session {}", sessionId);
            return;
        }

        if (!generation.isAvailable()) {
            log.debug("Generation unavailable, skipping realtime suggestions for session {}", sessionId);
            return;
        }

        // Reverse to chronological order before joining
        List<TranscriptSegment> chronological = new ArrayList<>(recent);
        java.util.Collections.reverse(chronological);
        String recentText = chronological.stream()
                .map(TranscriptSegment::getText)
                .collect(Collectors.joining(" "));

        if (recentText.length() < minTranscriptChars) {
            log.debug("Insufficient transcript context for session {} ({} chars < {} min), skipping",
                    sessionId, recentText.length(), minTranscriptChars);
            return;
        }

        GenerationContext context = buildContext(session.getProjectId(), recentText);

        GenerationResult result = generation.generate(recentText, session.getLanguage().value(), context);

        int created = 0;
        for (GenerationResult.GeneratedStory story : result.stories()) {
            if (storyExtraction.extractOne(story, sessionId, session.getProjectId()).isPresent()) {
                created++;
            }
        }
        log.info("Realtime suggestion for session {}: {} stories from {} segments (context={})",
                sessionId, created, recent.size(), context != null ? context.projectName() : "none");
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
