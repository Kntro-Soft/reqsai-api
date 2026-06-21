package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Creates {@link Suggestion} entities from AI-generated output, applying embedding-based
 * postprocessing to override or refine the LLM's classification before persisting.
 *
 * <h2>Classification rules</h2>
 * <ol>
 *   <li>LLM emits {@code NEW_STORY}: embed candidate text → similarity ≥ {@link UserStory#DUPLICATE_THRESHOLD}?
 *       → downgrade to {@code UPDATE_STORY}; otherwise keep as {@code NEW_STORY}.</li>
 *   <li>LLM emits {@code EDGE_CASE}: embed → find closest existing story → set {@code targetStoryId}
 *       (even if similarity is low; the LLM already decided it's an edge case).</li>
 *   <li>LLM emits {@code CLARIFYING_QUESTION}: forward as-is (no embedding needed).</li>
 * </ol>
 *
 * <p>Each suggestion is persisted in its own {@link Propagation#REQUIRES_NEW} transaction so that
 * its {@code SuggestionCreatedEvent} is published after each commit, enabling incremental
 * WebSocket streaming — the same pattern used by {@link StoryExtractionService}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SuggestionCreationService {

    private final SuggestionRepository suggestions;
    private final UserStoryRepository stories;
    private final EmbeddingPort embeddingPort;

    /**
     * Processes a {@link GenerationResult} and creates one {@link Suggestion} per LLM output item.
     *
     * @return list of persisted suggestions (never null, may be empty)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Suggestion> createSuggestions(GenerationResult result, UUID sessionId, UUID projectId) {
        List<Suggestion> created = new ArrayList<>();

        for (GenerationResult.GeneratedStory gen : result.stories()) {
            try {
                Suggestion suggestion = classifyAndCreate(gen, sessionId, projectId);
                created.add(suggestions.save(suggestion)); // Spring Data publishes events on commit
                log.debug("Suggestion created: type={} session={} title='{}'",
                        suggestion.getType(), sessionId, suggestion.getDraftTitle());
            } catch (Exception e) {
                log.warn("Skipping story suggestion '{}' (session={}): {}", gen.title(), sessionId, e.getMessage());
            }
        }

        for (GenerationResult.GeneratedQuestion q : result.questions()) {
            try {
                Suggestion suggestion = Suggestion.clarifyingQuestion(sessionId, projectId, q.question());
                created.add(suggestions.save(suggestion));
                log.debug("Clarifying-question suggestion created: session={}", sessionId);
            } catch (Exception e) {
                log.warn("Skipping clarifying-question suggestion (session={}): {}", sessionId, e.getMessage());
            }
        }

        log.info("Suggestions created for session {}: {} total ({} from stories, {} questions)",
                sessionId, created.size(), result.stories().size(), result.questions().size());
        return created;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Suggestion classifyAndCreate(GenerationResult.GeneratedStory gen, UUID sessionId, UUID projectId) {
        SuggestionType llmType = gen.type() != null ? gen.type() : SuggestionType.NEW_STORY;

        if (embeddingPort.isAvailable()) {
            String candidateText = "%s. As %s, I want to %s, so that %s.".formatted(
                    gen.title(), gen.role(), gen.action(), gen.benefit());
            float[] embedding = embeddingPort.embed(candidateText);

            return switch (llmType) {
                case NEW_STORY -> {
                    UserStoryRepository.SimilarStory closest = stories.findMostSimilar(projectId, embedding).orElse(null);
                    if (closest != null && closest.similarity() >= UserStory.DUPLICATE_THRESHOLD) {
                        log.debug("LLM NEW_STORY upgraded to UPDATE_STORY (sim={:.2f}, target={})".formatted(
                                closest.similarity(), closest.storyId()));
                        yield Suggestion.updateStory(sessionId, projectId,
                                gen.title(), gen.role(), gen.action(), gen.benefit(),
                                gen.priority(), gen.storyPoints(), closest.storyId());
                    }
                    yield Suggestion.newStory(sessionId, projectId,
                            gen.title(), gen.role(), gen.action(), gen.benefit(),
                            gen.priority(), gen.storyPoints());
                }
                case EDGE_CASE -> {
                    UUID targetStoryId = stories.findMostSimilar(projectId, embedding)
                            .map(UserStoryRepository.SimilarStory::storyId)
                            .orElse(null);
                    yield Suggestion.edgeCase(sessionId, projectId,
                            gen.title(), gen.role(), gen.action(), gen.benefit(),
                            gen.priority(), gen.storyPoints(), gen.relatedTopic(), targetStoryId);
                }
                case UPDATE_STORY -> {
                    UUID targetStoryId = stories.findMostSimilar(projectId, embedding)
                            .map(UserStoryRepository.SimilarStory::storyId)
                            .orElse(null);
                    if (targetStoryId == null) {
                        log.debug("LLM UPDATE_STORY has no target (no similar story), creating as NEW_STORY");
                        yield Suggestion.newStory(sessionId, projectId,
                                gen.title(), gen.role(), gen.action(), gen.benefit(),
                                gen.priority(), gen.storyPoints());
                    }
                    yield Suggestion.updateStory(sessionId, projectId,
                            gen.title(), gen.role(), gen.action(), gen.benefit(),
                            gen.priority(), gen.storyPoints(), targetStoryId);
                }
                default -> Suggestion.newStory(sessionId, projectId,
                        gen.title(), gen.role(), gen.action(), gen.benefit(),
                        gen.priority(), gen.storyPoints());
            };
        }

        // No embedding available — trust the LLM classification without target resolution
        return switch (llmType) {
            case EDGE_CASE -> Suggestion.edgeCase(sessionId, projectId,
                    gen.title(), gen.role(), gen.action(), gen.benefit(),
                    gen.priority(), gen.storyPoints(), gen.relatedTopic(), null);
            default -> Suggestion.newStory(sessionId, projectId,
                    gen.title(), gen.role(), gen.action(), gen.benefit(),
                    gen.priority(), gen.storyPoints());
        };
    }
}
