package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.application.port.GenerationResult;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionStatus;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Creates {@link Suggestion} entities from AI-generated output, applying embedding-based
 * postprocessing to override or refine the LLM's classification before persisting.
 *
 * <h2>Classification rules</h2>
 * <ol>
 *   <li>The LLM sees the backlog (with story ids) in its prompt and may return a {@code targetStoryId}
 *       for {@code UPDATE_STORY}/{@code EDGE_CASE}. A returned target is validated against the project
 *       (hallucinated/foreign ids are discarded) and, when valid, wins over embedding search.</li>
 *   <li>LLM emits {@code NEW_STORY}: embed candidate text → similarity ≥ {@link UserStory#DUPLICATE_THRESHOLD}?
 *       → downgrade to {@code UPDATE_STORY}; otherwise keep as {@code NEW_STORY}.</li>
 *   <li>LLM emits {@code UPDATE_STORY}/{@code EDGE_CASE} without a usable target: resolve it by
 *       embedding search; an {@code UPDATE_STORY} that still has no target degrades to {@code NEW_STORY}.</li>
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

        // Overlapping context windows re-surface the same idea every trigger. Dedup the LLM output
        // against suggestions already PENDING for this session (and against this same batch) by
        // normalized title / question so the analyst is not flooded with repeats.
        List<Suggestion> pending = suggestions.findAllBySessionIdAndStatus(sessionId, SuggestionStatus.PENDING);
        Set<String> seenTitles = pending.stream()
                .map(s -> normalize(s.getDraftTitle()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> seenQuestions = pending.stream()
                .map(s -> normalize(s.getQuestion()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        int skippedDuplicate = 0;
        int failed = 0;

        for (GenerationResult.GeneratedStory gen : result.stories()) {
            String key = normalize(gen.title());
            if (key != null && !seenTitles.add(key)) {
                skippedDuplicate++;
                log.debug("Skipping duplicate story suggestion '{}' (session={})", gen.title(), sessionId);
                continue;
            }
            try {
                Suggestion suggestion = classifyAndCreate(gen, sessionId, projectId);
                created.add(suggestions.save(suggestion)); // Spring Data publishes events on commit
                log.debug("Suggestion created: type={} session={} title='{}'",
                        suggestion.getType(), sessionId, suggestion.getDraftTitle());
            } catch (Exception e) {
                failed++;
                // Don't swallow silently: log the full stack so real bugs (e.g. a broken similarity
                // lookup) surface instead of looking like "0 suggestions".
                log.error("Failed to create story suggestion '{}' (session={}) — unexpected error",
                        gen.title(), sessionId, e);
            }
        }

        for (GenerationResult.GeneratedQuestion q : result.questions()) {
            String key = normalize(q.question());
            if (key != null && !seenQuestions.add(key)) {
                skippedDuplicate++;
                log.debug("Skipping duplicate clarifying-question suggestion (session={})", sessionId);
                continue;
            }
            try {
                Suggestion suggestion = Suggestion.clarifyingQuestion(sessionId, projectId, q.question());
                created.add(suggestions.save(suggestion));
                log.debug("Clarifying-question suggestion created: session={}", sessionId);
            } catch (Exception e) {
                failed++;
                log.error("Failed to create clarifying-question suggestion (session={}) — unexpected error",
                        sessionId, e);
            }
        }

        log.info("Suggestions created for session {}: {} created, {} duplicate-skipped, {} failed "
                        + "(from {} stories + {} questions)",
                sessionId, created.size(), skippedDuplicate, failed,
                result.stories().size(), result.questions().size());
        return created;
    }

    /** Trim + lowercase for duplicate comparison; null/blank → null (no key). */
    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.strip().toLowerCase();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Suggestion classifyAndCreate(GenerationResult.GeneratedStory gen, UUID sessionId, UUID projectId) {
        SuggestionType llmType = gen.type() != null ? gen.type() : SuggestionType.NEW_STORY;
        // The LLM saw the backlog with ids; validate what it returned before trusting it.
        UUID llmTarget = validatedTarget(gen.targetStoryId(), projectId);

        if (embeddingPort.isAvailable()) {
            String candidateText = "%s. As %s, I want to %s, so that %s.".formatted(
                    gen.title(), gen.role(), gen.action(), gen.benefit());
            float[] embedding = embeddingPort.embed(candidateText);

            return switch (llmType) {
                case NEW_STORY -> {
                    UserStoryRepository.SimilarStory closest = stories.findMostSimilar(projectId, embedding).orElse(null);
                    if (closest != null && closest.similarity() >= UserStory.DUPLICATE_THRESHOLD) {
                        log.debug("LLM NEW_STORY upgraded to UPDATE_STORY (sim={}, target={})",
                                closest.similarity(), closest.storyId());
                        yield Suggestion.updateStory(sessionId, projectId,
                                gen.title(), gen.role(), gen.action(), gen.benefit(),
                                gen.priority(), gen.storyPoints(), closest.storyId());
                    }
                    yield Suggestion.newStory(sessionId, projectId,
                            gen.title(), gen.role(), gen.action(), gen.benefit(),
                            gen.priority(), gen.storyPoints());
                }
                case EDGE_CASE -> {
                    UUID targetStoryId = llmTarget != null ? llmTarget
                            : stories.findMostSimilar(projectId, embedding)
                                    .map(UserStoryRepository.SimilarStory::storyId)
                                    .orElse(null);
                    yield Suggestion.edgeCase(sessionId, projectId,
                            gen.title(), gen.role(), gen.action(), gen.benefit(),
                            gen.priority(), gen.storyPoints(), gen.relatedTopic(), targetStoryId);
                }
                case UPDATE_STORY -> {
                    UUID targetStoryId = llmTarget != null ? llmTarget
                            : stories.findMostSimilar(projectId, embedding)
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

        // No embedding available — trust the LLM classification, using its (validated) target
        return switch (llmType) {
            case EDGE_CASE -> Suggestion.edgeCase(sessionId, projectId,
                    gen.title(), gen.role(), gen.action(), gen.benefit(),
                    gen.priority(), gen.storyPoints(), gen.relatedTopic(), llmTarget);
            case UPDATE_STORY -> {
                if (llmTarget == null) {
                    log.debug("LLM UPDATE_STORY has no usable target and no embedding model; creating as NEW_STORY");
                    yield Suggestion.newStory(sessionId, projectId,
                            gen.title(), gen.role(), gen.action(), gen.benefit(),
                            gen.priority(), gen.storyPoints());
                }
                yield Suggestion.updateStory(sessionId, projectId,
                        gen.title(), gen.role(), gen.action(), gen.benefit(),
                        gen.priority(), gen.storyPoints(), llmTarget);
            }
            default -> Suggestion.newStory(sessionId, projectId,
                    gen.title(), gen.role(), gen.action(), gen.benefit(),
                    gen.priority(), gen.storyPoints());
        };
    }

    /** The LLM-returned target id when it denotes a real story of this project; {@code null} otherwise. */
    private @Nullable UUID validatedTarget(@Nullable UUID targetStoryId, UUID projectId) {
        if (targetStoryId == null) return null;
        if (stories.findByIdAndProjectId(targetStoryId, projectId).isPresent()) {
            return targetStoryId;
        }
        log.debug("LLM returned targetStoryId {} that is not a story of project {}; ignoring it",
                targetStoryId, projectId);
        return null;
    }
}
