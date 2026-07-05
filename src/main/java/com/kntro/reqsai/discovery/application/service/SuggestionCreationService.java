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
import org.springframework.beans.factory.annotation.Value;
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
     * Cosine threshold above which a fresh draft is treated as a near-duplicate of a PENDING
     * suggestion or existing story. Deliberately a touch below {@link UserStory#DUPLICATE_THRESHOLD}
     * (0.85) so paraphrases the strict duplicate gate misses ("Autenticación de dos factores" vs
     * "Soporte para autenticación de dos factores en inicio de sesión") are still caught.
     */
    @Value("${discovery.realtime.dedup-similarity-threshold:0.84}")
    private double dedupSimilarityThreshold;

    /**
     * Processes a {@link GenerationResult} and creates one {@link Suggestion} per LLM output item.
     *
     * @return list of persisted suggestions (never null, may be empty)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Suggestion> createSuggestions(GenerationResult result, UUID sessionId, UUID projectId) {
        List<Suggestion> created = new ArrayList<>();

        // Overlapping context windows re-surface the same idea every trigger. Dedup the LLM output
        // against suggestions already PENDING for this session (and against this same batch) so the
        // analyst is not flooded with repeats. Two layers:
        //   1. normalized-title / question exact match (accent- and punctuation-insensitive) — kills
        //      "Recuperar contraseña" suggested twice in one pass and verbatim cross-pass repeats;
        //   2. embedding near-duplicate — kills paraphrases ("Autenticación de dos factores" vs
        //      "Soporte para 2FA en inicio de sesión") the string match cannot see.
        List<Suggestion> pending = suggestions.findAllBySessionIdAndStatus(sessionId, SuggestionStatus.PENDING);
        Set<String> seenTitles = pending.stream()
                .map(s -> normalize(s.getDraftTitle()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> seenQuestions = pending.stream()
                .map(s -> normalize(s.getQuestion()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        // Embeddings of already-PENDING story drafts (computed once), plus the drafts accepted so far
        // in THIS pass, so a later paraphrase in the same LLM response is also caught.
        List<float[]> priorDraftEmbeddings = embedPendingDrafts(pending);

        int skippedDuplicate = 0;
        int failed = 0;

        int skippedIncoherent = 0;

        for (GenerationResult.GeneratedStory gen : result.stories()) {
            // Quality bar: the prompt asks the model to emit nothing for garbled fragments, but a
            // missing core field still slips through occasionally. A draft cannot become a valid
            // story without title/role/action/benefit, so drop it here rather than let the factory
            // throw and log it as a "failure" (that read like a real bug). Deliberately minimal —
            // only a clearly-safe structural check, no language- or content-specific heuristics.
            if (isIncoherent(gen)) {
                skippedIncoherent++;
                log.debug("Skipping incoherent story suggestion (missing core field) title='{}' (session={})",
                        gen.title(), sessionId);
                continue;
            }

            String key = normalize(gen.title());
            if (key != null && !seenTitles.add(key)) {
                skippedDuplicate++;
                log.debug("Skipping title-duplicate story suggestion '{}' (session={})", gen.title(), sessionId);
                continue;
            }

            float[] draftEmbedding = tryEmbed(candidateText(gen));
            if (draftEmbedding != null && isNearDuplicateOf(draftEmbedding, priorDraftEmbeddings)) {
                skippedDuplicate++;
                log.debug("Skipping embedding-duplicate story suggestion '{}' (session={})", gen.title(), sessionId);
                continue;
            }

            try {
                Suggestion suggestion = classifyAndCreate(gen, sessionId, projectId, draftEmbedding);
                created.add(suggestions.save(suggestion)); // Spring Data publishes events on commit
                if (draftEmbedding != null) {
                    priorDraftEmbeddings.add(draftEmbedding); // guard the rest of this same pass
                }
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

        log.info("Suggestions created for session {}: {} created, {} duplicate-skipped, {} incoherent-skipped, "
                        + "{} failed (from {} stories + {} questions)",
                sessionId, created.size(), skippedDuplicate, skippedIncoherent, failed,
                result.stories().size(), result.questions().size());
        return created;
    }

    /** A draft that cannot become a valid story: any of title/role/action/benefit blank. */
    private static boolean isIncoherent(GenerationResult.GeneratedStory gen) {
        return isBlank(gen.title()) || isBlank(gen.role()) || isBlank(gen.action()) || isBlank(gen.benefit());
    }

    private static boolean isBlank(@Nullable String s) {
        return s == null || s.isBlank();
    }

    /** Canonical draft text fed to the embedding model (title + full user-story sentence). */
    private static String candidateText(GenerationResult.GeneratedStory gen) {
        return "%s. As %s, I want to %s, so that %s.".formatted(
                gen.title(), gen.role(), gen.action(), gen.benefit());
    }

    /** Embeds each PENDING story draft's candidate text (best-effort; skips nulls/failures). */
    private List<float[]> embedPendingDrafts(List<Suggestion> pending) {
        List<float[]> embeddings = new ArrayList<>();
        if (!embeddingPort.isAvailable()) {
            return embeddings;
        }
        for (Suggestion s : pending) {
            if (s.getType() == SuggestionType.CLARIFYING_QUESTION || s.getDraftTitle() == null) {
                continue;
            }
            String text = "%s. As %s, I want to %s, so that %s.".formatted(
                    s.getDraftTitle(), s.getDraftRole(), s.getDraftAction(), s.getDraftBenefit());
            float[] e = tryEmbed(text);
            if (e != null) {
                embeddings.add(e);
            }
        }
        return embeddings;
    }

    /** True when {@code candidate} is within the dedup threshold of any prior draft embedding. */
    private boolean isNearDuplicateOf(float[] candidate, List<float[]> priorDraftEmbeddings) {
        for (float[] prior : priorDraftEmbeddings) {
            double sim = cosineSimilarity(candidate, prior);
            if (sim >= dedupSimilarityThreshold) {
                log.debug("Draft is a near-duplicate of a pending draft (cosine={})", sim);
                return true;
            }
        }
        return false;
    }

    /** Embeds {@code text}, degrading to {@code null} (skip embedding checks) on failure/unavailable. */
    private float @Nullable [] tryEmbed(String text) {
        if (!embeddingPort.isAvailable()) {
            return null;
        }
        try {
            return embeddingPort.embed(text);
        } catch (RuntimeException e) {
            log.warn("Embedding a draft suggestion failed; skipping embedding-based dedup for it: {}",
                    e.getMessage());
            return null;
        }
    }

    /** Cosine similarity in [-1, 1]; 0 when either vector is zero-length or lengths differ. */
    static double cosineSimilarity(float[] a, float[] b) {
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

    /**
     * Trim + accent-fold + lowercase + collapse whitespace + strip surrounding punctuation for
     * duplicate comparison; null/blank → null (no key). Accent-insensitive so "sesión" and "sesion"
     * collide, matching how STT and the LLM inconsistently emit diacritics.
     */
    static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        String stripped = java.text.Normalizer.normalize(value.strip(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")          // drop combining diacritics
                .toLowerCase()
                .replaceAll("[^\\p{Alnum}\\s]", " ") // punctuation → space
                .replaceAll("\\s+", " ")             // collapse whitespace
                .strip();
        return stripped.isBlank() ? null : stripped;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Suggestion classifyAndCreate(GenerationResult.GeneratedStory gen, UUID sessionId, UUID projectId,
                                         float @Nullable [] precomputedEmbedding) {
        SuggestionType llmType = gen.type() != null ? gen.type() : SuggestionType.NEW_STORY;
        // The LLM saw the backlog with ids; validate what it returned before trusting it.
        UUID llmTarget = validatedTarget(gen.targetStoryId(), projectId);

        // Diagnostic: the raw LLM decision before any server-side re-classification. When UPDATE_STORY
        // is "never chosen" this line proves whether it is the model or our validation dropping it.
        log.debug("LLM classification for '{}' (session={}): rawType={} rawTargetStoryId={} validatedTarget={}",
                gen.title(), sessionId, gen.type(), gen.targetStoryId(), llmTarget);

        if (embeddingPort.isAvailable()) {
            float[] embedding = precomputedEmbedding != null ? precomputedEmbedding
                    : embeddingPort.embed(candidateText(gen));

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
                    // The model explicitly said "this refines an existing story". Honor that intent:
                    // resolve to its target, else the closest existing story by embedding — only demote
                    // to NEW_STORY when the project genuinely has no story to point at.
                    UUID targetStoryId = llmTarget != null ? llmTarget
                            : stories.findMostSimilar(projectId, embedding)
                                    .map(UserStoryRepository.SimilarStory::storyId)
                                    .orElse(null);
                    if (targetStoryId == null) {
                        log.debug("LLM UPDATE_STORY has no target (backlog empty), creating as NEW_STORY");
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
