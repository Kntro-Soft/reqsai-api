package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.event.UserStoryNearDuplicateDetectedEvent;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Persists one AI-generated story in its own transaction.
 *
 * <p>Using {@link Propagation#REQUIRES_NEW} enables incremental WebSocket streaming: Spring
 * Modulith publishes {@code UserStoryCreatedEvent} after each story's own transaction commits,
 * so the client receives a WebSocket message per story rather than a bulk push at the end.
 *
 * <p>{@code extractOne} must always be called from a <em>different</em> Spring bean so that the
 * proxy intercepts the call and the transaction boundary is enforced. Self-invocation bypasses
 * the proxy and makes {@code REQUIRES_NEW} a no-op — the loop belongs in the caller.
 *
 * <p>When a near-duplicate is detected it is surfaced as an {@code UPDATE_STORY} suggestion (the
 * "Duplication Alert") pointing at the existing story, so the analyst can merge/update or dismiss
 * it rather than the candidate being silently dropped. A {@link UserStoryNearDuplicateDetectedEvent}
 * is still published for observability. Other domain exceptions (blank title, invalid fields) are
 * logged and skipped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StoryExtractionService {

    private final UserStoryRepository stories;
    private final UserStoryDeduplicationService deduplication;
    private final SuggestionRepository suggestions;
    private final EmbeddingPort embeddingPort;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UserStory> extractOne(GenerationResult.GeneratedStory gen, UUID sessionId, UUID projectId) {
        try {
            UserStory story = new UserStory(sessionId, projectId, gen.title(), gen.role(), gen.action(), gen.benefit(), gen.priority(), gen.storyPoints());
            if (gen.acceptanceCriteria() != null) {
                gen.acceptanceCriteria().forEach(c -> story.addAcceptanceCriterion(c.scenario(), c.given(), c.when(), c.then()));
            }
            deduplication.embedAndGuardDuplicates(story);
            return Optional.of(stories.save(story));
        } catch (DomainException e) {
            if (e.error() == DiscoveryError.DUPLICATE_USER_STORY) {
                raiseDuplicateAlert(gen, sessionId, projectId);
            } else {
                log.warn("Skipping story '{}' (session={}): {}", gen.title(), sessionId, e.getMessage());
            }
            return Optional.empty();
        }
    }

    /**
     * Surfaces a near-duplicate as an UPDATE_STORY suggestion targeting the most similar existing
     * story (Duplication Alert). Falls back to dropping (with the legacy event only) when the target
     * cannot be resolved — e.g. no embedding model configured.
     */
    private void raiseDuplicateAlert(GenerationResult.GeneratedStory gen, UUID sessionId, UUID projectId) {
        eventPublisher.publishEvent(UserStoryNearDuplicateDetectedEvent.of(sessionId, projectId, gen.title(), gen.role(), gen.action(), gen.benefit(), gen.priority(), gen.storyPoints()));

        Optional<UserStoryRepository.SimilarStory> match = Optional.empty();
        if (embeddingPort.isAvailable()) {
            UserStory candidate = new UserStory(sessionId, projectId, gen.title(), gen.role(), gen.action(), gen.benefit(), gen.priority(), gen.storyPoints());
            match = stories.findMostSimilar(projectId, embeddingPort.embed(candidate.toCanonicalText()));
        }

        if (match.isEmpty()) {
            log.warn("Near-duplicate story '{}' (session={}) detected but no target could be resolved; dropped", gen.title(), sessionId);
            return;
        }

        UserStoryRepository.SimilarStory similar = match.get();
        Suggestion alert = Suggestion.updateStory(sessionId, projectId, gen.title(), gen.role(), gen.action(), gen.benefit(), gen.priority(), gen.storyPoints(), similar.storyId());
        alert.recordSimilarity(similar.similarity());
        suggestions.save(alert);
        log.info("Near-duplicate story '{}' raised as a duplicate-alert suggestion (similarity={}, target={})", gen.title(), similar.similarity(), similar.storyId());
    }
}
