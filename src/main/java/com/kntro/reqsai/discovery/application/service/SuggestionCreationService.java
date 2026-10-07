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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Creates {@link Suggestion} entities from AI-generated output, applying embedding-based
 * postprocessing to override or refine the LLM's classification before persisting.
 *
 * <h2>Duplicate filter</h2>
 * Each story draft is compared with the session's PENDING story suggestions and with the drafts already
 * kept in this pass. {@link SuggestionDedupPolicy#repeats} drops a draft only when it adds nothing — no
 * new number, no new criterion, no new word in its title, role or action — to a related earlier draft
 * (same title, embedding similarity at or above the dedup threshold, or linked by the model). A distinct
 * rule of the same domain (a cancellation penalty next to the booking it applies to) is always kept.
 *
 * <h2>Classification rules</h2>
 * <ol>
 *   <li>The LLM sees the backlog (with story ids) in its prompt and may return a {@code targetStoryId}
 *       for {@code UPDATE_STORY}/{@code EDGE_CASE}. A returned target is validated against the project
 *       (hallucinated/foreign ids are discarded) and, when valid, wins over embedding search.</li>
 *   <li>A draft whose {@code targetStoryId} is one of this session's PENDING suggestions is dropped when
 *       it only restates that suggestion; when it adds something it is kept as a {@code NEW_STORY}
 *       (a pending suggestion is not a story yet, so nothing can be updated). It never keeps the linked
 *       suggestion's title, which the model copies into the update or edge case it meant: the title comes
 *       from the draft's own content ({@link SuggestionTitles#forLinkedDraft}).</li>
 *   <li>LLM emits {@code NEW_STORY}: embed candidate text → closest accepted+indexed story at or above
 *       the dedup threshold ({@code discovery.realtime.dedup-similarity-threshold}) AND with the same
 *       intent ({@link SuggestionDedupPolicy#sameRequirementAs})? → downgrade to {@code UPDATE_STORY}
 *       against it (recording the similarity); otherwise keep as {@code NEW_STORY}.</li>
 *   <li>LLM emits {@code UPDATE_STORY}/{@code EDGE_CASE} without a usable target: resolve it by
 *       embedding search; an {@code UPDATE_STORY} that still has no target degrades to {@code NEW_STORY}.</li>
 *   <li>An {@code UPDATE_STORY} carries only the acceptance criteria its target does not have yet, and
 *       is dropped when it would change nothing: same narrative and no new criteria.</li>
 *   <li>LLM emits {@code CLARIFYING_QUESTION}: forward as-is (no embedding needed).</li>
 *   <li>A {@code NEW_STORY} never takes a title another story suggestion of the queue already has (pending,
 *       or kept earlier in this pass); it gets one from its own content instead
 *       ({@link SuggestionTitles#uniqueNewStoryTitle}).</li>
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
    private final SuggestionDedupPolicy dedupPolicy;

    /**
     * Processes a {@link GenerationResult} and creates one {@link Suggestion} per LLM output item.
     *
     * @return list of persisted suggestions (never null, may be empty)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Suggestion> createSuggestions(GenerationResult result, UUID sessionId, UUID projectId) {
        List<Suggestion> created = new ArrayList<>();

        // A pass sees only new transcript, but the model still re-surfaces ideas already awaiting review.
        // Compare every story draft with the session's PENDING story suggestions and with the drafts kept
        // earlier in this pass. Only a draft that adds nothing to a related earlier one is dropped: the
        // embedding says two drafts are related, never that a new rule (a penalty, a notification) is not.
        List<Suggestion> pending = suggestions.findAllBySessionIdAndStatus(sessionId, SuggestionStatus.PENDING);
        Set<String> seenQuestions = pending.stream()
                .map(s -> normalize(s.getQuestion()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        List<EarlierDraft> earlierDrafts = embedPendingDrafts(pending);
        // The prompt lists the PENDING suggestions with their ids, so the model may point a draft at one.
        Map<UUID, EarlierDraft> pendingById = new HashMap<>();
        earlierDrafts.forEach(d -> pendingById.put(d.suggestionId(), d));
        // Story titles already in the queue; a NEW_STORY of this pass never takes one of them.
        SuggestionTitles titles = new SuggestionTitles();
        earlierDrafts.forEach(d -> titles.reserve(d.draft()));

        int skippedDuplicate = 0;
        int skippedNoOpUpdate = 0;
        int keptLinkedToPending = 0;
        int failed = 0;

        int skippedIncoherent = 0;

        for (GenerationResult.GeneratedStory generated : result.stories()) {
            GenerationResult.GeneratedStory gen = generated;
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

            float[] draftEmbedding = tryEmbed(candidateText(gen));

            // The model pointed this draft at a PENDING suggestion. Drop it when it only restates that
            // suggestion; when it adds something (a rule, another actor or action), keep it as a NEW_STORY
            // — a pending suggestion is not a story yet, so there is nothing to update. The model copies the
            // linked suggestion's title into the update or edge case it meant, so the kept story gets a title
            // of its own: otherwise accepting it would create a second story with the pending one's title.
            EarlierDraft linked = gen.targetStoryId() == null ? null : pendingById.get(gen.targetStoryId());
            if (linked != null) {
                SuggestionDedupPolicy.Verdict verdict = dedupPolicy.repeats(SuggestionDedupPolicy.Draft.of(gen),
                        linked.draft(), cosineOrNull(draftEmbedding, linked.embedding()), true);
                if (verdict.duplicate()) {
                    skippedDuplicate++;
                    log.debug("Skipping story suggestion '{}' restating PENDING suggestion {} ({}) (session={})",
                            gen.title(), linked.suggestionId(), verdict.reason(), sessionId);
                    continue;
                }
                keptLinkedToPending++;
                String title = titles.forLinkedDraft(gen, linked.draft());
                log.debug("Keeping story suggestion '{}' linked to PENDING suggestion {} as NEW_STORY '{}' ({}) "
                        + "(session={})", gen.title(), linked.suggestionId(), title, verdict.reason(), sessionId);
                gen = asNewStory(gen, title);
            }

            EarlierDraft twin = findDuplicate(gen, draftEmbedding, earlierDrafts, linked, sessionId);
            if (twin != null) {
                skippedDuplicate++;
                continue;
            }

            try {
                Suggestion suggestion = classifyAndCreate(gen, sessionId, projectId, draftEmbedding, titles);
                if (suggestion == null) {
                    skippedNoOpUpdate++;
                    continue;
                }
                created.add(suggestions.save(suggestion)); // Spring Data publishes events on commit
                // Guard the rest of this same pass against a repeat of what was just kept, and its title.
                SuggestionDedupPolicy.Draft kept = SuggestionDedupPolicy.Draft.of(suggestion);
                earlierDrafts.add(new EarlierDraft(suggestion.getId(), kept, draftEmbedding));
                titles.reserve(kept);
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

        log.info("Suggestions created for session {}: {} created, {} duplicate-skipped, {} no-op-update-skipped, "
                        + "{} incoherent-skipped, {} failed, {} kept despite a PENDING link (from {} stories + "
                        + "{} questions)",
                sessionId, created.size(), skippedDuplicate, skippedNoOpUpdate, skippedIncoherent, failed,
                keptLinkedToPending, result.stories().size(), result.questions().size());
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

    /**
     * The session's PENDING story suggestions as comparable drafts, each with the embedding of its
     * candidate text when the provider is available (best-effort: {@code null} on failure).
     */
    private List<EarlierDraft> embedPendingDrafts(List<Suggestion> pending) {
        List<EarlierDraft> drafts = new ArrayList<>();
        for (Suggestion s : pending) {
            if (s.getType() == SuggestionType.CLARIFYING_QUESTION || s.getDraftTitle() == null) {
                continue;
            }
            String text = "%s. As %s, I want to %s, so that %s.".formatted(
                    s.getDraftTitle(), s.getDraftRole(), s.getDraftAction(), s.getDraftBenefit());
            drafts.add(new EarlierDraft(s.getId(), SuggestionDedupPolicy.Draft.of(s), tryEmbed(text)));
        }
        return drafts;
    }

    /**
     * The first earlier draft (other than {@code skip}, already judged) that {@code gen} repeats, or
     * {@code null}. The similarity bar alone never decides: see {@link SuggestionDedupPolicy}.
     */
    private @Nullable EarlierDraft findDuplicate(GenerationResult.GeneratedStory gen, float @Nullable [] embedding,
                                                 List<EarlierDraft> earlierDrafts, @Nullable EarlierDraft skip,
                                                 UUID sessionId) {
        SuggestionDedupPolicy.Draft draft = SuggestionDedupPolicy.Draft.of(gen);
        for (EarlierDraft earlier : earlierDrafts) {
            if (earlier == skip) {
                continue;
            }
            Double cosine = cosineOrNull(embedding, earlier.embedding());
            SuggestionDedupPolicy.Verdict verdict = dedupPolicy.repeats(draft, earlier.draft(), cosine, false);
            if (verdict.duplicate()) {
                log.debug("Skipping duplicate story suggestion '{}' of '{}' ({}) (session={})",
                        gen.title(), earlier.draft().title(), verdict.reason(), sessionId);
                return earlier;
            }
            if (cosine != null && cosine >= dedupPolicy.similarityThreshold()) {
                log.debug("Keeping story suggestion '{}' although similar to '{}' (cosine {}): {} (session={})",
                        gen.title(), earlier.draft().title(), cosine, verdict.reason(), sessionId);
            }
        }
        return null;
    }

    /** The draft as an untargeted NEW_STORY titled {@code title}, keeping its other fields and criteria. */
    private static GenerationResult.GeneratedStory asNewStory(GenerationResult.GeneratedStory gen, String title) {
        return new GenerationResult.GeneratedStory(SuggestionType.NEW_STORY,
                title, gen.role(), gen.action(), gen.benefit(), gen.priority(), gen.storyPoints(),
                gen.acceptanceCriteria(), null, null);
    }

    private static @Nullable Double cosineOrNull(float @Nullable [] a, float @Nullable [] b) {
        return a == null || b == null ? null : cosineSimilarity(a, b);
    }

    /** A story draft a later one may repeat, with the embedding of its candidate text when known. */
    private record EarlierDraft(@Nullable UUID suggestionId, SuggestionDedupPolicy.Draft draft,
                                float @Nullable [] embedding) {}

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

    /** Accent-, case- and punctuation-insensitive key for duplicate comparison; null/blank → null. */
    static @Nullable String normalize(@Nullable String value) {
        return SuggestionDedupPolicy.normalize(value);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds a NEW_STORY suggestion, carrying the LLM's proposed acceptance criteria on the draft, under a
     * title no other story suggestion of the queue has (see {@link SuggestionTitles#uniqueNewStoryTitle}).
     */
    private static Suggestion newStoryOf(GenerationResult.GeneratedStory gen, UUID sessionId, UUID projectId,
                                         SuggestionTitles titles) {
        String title = titles.uniqueNewStoryTitle(gen);
        if (!title.equals(gen.title())) {
            log.debug("NEW_STORY '{}' retitled '{}': another suggestion of the queue has that title (session={})",
                    gen.title(), title, sessionId);
        }
        return Suggestion.newStory(sessionId, projectId,
                title, gen.role(), gen.action(), gen.benefit(),
                gen.priority(), gen.storyPoints(), draftCriteriaOf(gen));
    }

    /**
     * Builds an EDGE_CASE suggestion carrying the single boundary criterion the LLM proposed (its
     * first acceptance criterion, when present) to add to the target story on accept.
     */
    private static Suggestion edgeCaseOf(GenerationResult.GeneratedStory gen, UUID sessionId, UUID projectId,
                                         @Nullable UUID targetStoryId) {
        List<Suggestion.DraftCriterion> criteria = draftCriteriaOf(gen);
        Suggestion.DraftCriterion criterion = criteria.isEmpty() ? null : criteria.getFirst();
        return Suggestion.edgeCase(sessionId, projectId,
                gen.title(), gen.role(), gen.action(), gen.benefit(),
                gen.priority(), gen.storyPoints(), gen.relatedTopic(), targetStoryId, criterion);
    }

    /** Maps the LLM's proposed acceptance criteria to draft criteria (empty when none). */
    private static List<Suggestion.DraftCriterion> draftCriteriaOf(GenerationResult.GeneratedStory gen) {
        return gen.acceptanceCriteria() == null ? List.of()
                : gen.acceptanceCriteria().stream()
                        .map(c -> new Suggestion.DraftCriterion(c.scenario(), c.given(), c.when(), c.then()))
                        .toList();
    }

    /**
     * Classifies one draft and builds its suggestion, or returns {@code null} when it is an
     * {@code UPDATE_STORY} that would change nothing (see {@link #updateOf}).
     */
    private @Nullable Suggestion classifyAndCreate(GenerationResult.GeneratedStory gen, UUID sessionId,
                                                   UUID projectId, float @Nullable [] precomputedEmbedding,
                                                   SuggestionTitles titles) {
        SuggestionType llmType = gen.type() != null ? gen.type() : SuggestionType.NEW_STORY;
        // The LLM saw the backlog with ids; validate what it returned before trusting it.
        UserStory llmTarget = validatedTarget(gen.targetStoryId(), projectId);
        UUID llmTargetId = llmTarget != null ? llmTarget.getId() : null;

        // Diagnostic: the raw LLM decision before any server-side re-classification. When UPDATE_STORY
        // is "never chosen" this line proves whether it is the model or our validation dropping it.
        log.debug("LLM classification for '{}' (session={}): rawType={} rawTargetStoryId={} validatedTarget={}",
                gen.title(), sessionId, gen.type(), gen.targetStoryId(), llmTargetId);

        if (embeddingPort.isAvailable()) {
            float[] embedding = precomputedEmbedding != null ? precomputedEmbedding
                    : embeddingPort.embed(candidateText(gen));

            return switch (llmType) {
                case NEW_STORY -> {
                    // Converge a NEW draft onto the ACCEPTED backlog when it repeats an accepted+indexed
                    // story: the closest story must clear the dedup bar AND the policy must judge it the
                    // same requirement. A similar story of the same domain with another intent or a new
                    // rule (booking vs. its cancellation penalty) keeps the draft as a NEW_STORY — turning
                    // it into an update would overwrite that story's narrative on accept.
                    UserStoryRepository.SimilarStory closest = stories.findMostSimilar(projectId, embedding).orElse(null);
                    if (closest != null && closest.similarity() >= dedupPolicy.similarityThreshold()) {
                        UserStory twin = stories.findByIdAndProjectId(closest.storyId(), projectId).orElse(null);
                        if (twin != null) {
                            SuggestionDedupPolicy.Verdict verdict = dedupPolicy.sameRequirementAs(
                                    SuggestionDedupPolicy.Draft.of(gen), SuggestionDedupPolicy.Draft.of(twin),
                                    closest.similarity());
                            if (verdict.duplicate()) {
                                log.debug("LLM NEW_STORY downgraded to UPDATE_STORY against accepted backlog "
                                        + "(sim={}, target={}): {}", closest.similarity(), closest.storyId(),
                                        verdict.reason());
                                yield updateOf(gen, sessionId, projectId, twin, closest.similarity());
                            }
                            log.debug("LLM NEW_STORY '{}' kept although similar to story {} (sim={}): {}",
                                    gen.title(), closest.storyId(), closest.similarity(), verdict.reason());
                        }
                    }
                    yield newStoryOf(gen, sessionId, projectId, titles);
                }
                case EDGE_CASE -> {
                    // Resolve the target: the LLM's validated pick, else the closest story BUT only when
                    // it clears the dedup floor. Without a floor a weak nearest-neighbor (0.4) got
                    // attached, and — worse — a targetless edge case became a standalone story on accept,
                    // violating granularity. Leaving targetStoryId null lets the accept handler surface a
                    // clear "no target" error instead of minting a spurious story.
                    UUID targetStoryId = llmTargetId != null ? llmTargetId
                            : resolveEdgeCaseTargetByEmbedding(projectId, embedding);
                    yield edgeCaseOf(gen, sessionId, projectId, targetStoryId);
                }
                case UPDATE_STORY -> {
                    // The model explicitly said "this refines an existing story". Honor that intent:
                    // resolve to its target, else the closest existing story by embedding — only demote
                    // to NEW_STORY when the project genuinely has no story to point at.
                    UserStory target = llmTarget != null ? llmTarget
                            : stories.findMostSimilar(projectId, embedding)
                                    .flatMap(s -> stories.findByIdAndProjectId(s.storyId(), projectId))
                                    .orElse(null);
                    if (target == null) {
                        log.debug("LLM UPDATE_STORY has no target (backlog empty), creating as NEW_STORY");
                        yield newStoryOf(gen, sessionId, projectId, titles);
                    }
                    yield updateOf(gen, sessionId, projectId, target, null);
                }
                default -> newStoryOf(gen, sessionId, projectId, titles);
            };
        }

        // No embedding available — trust the LLM classification, using its (validated) target
        return switch (llmType) {
            case EDGE_CASE -> edgeCaseOf(gen, sessionId, projectId, llmTargetId);
            case UPDATE_STORY -> {
                if (llmTarget == null) {
                    log.debug("LLM UPDATE_STORY has no usable target and no embedding model; creating as NEW_STORY");
                    yield newStoryOf(gen, sessionId, projectId, titles);
                }
                yield updateOf(gen, sessionId, projectId, llmTarget, null);
            }
            default -> newStoryOf(gen, sessionId, projectId, titles);
        };
    }

    /**
     * An UPDATE_STORY of {@code target} carrying only the acceptance criteria the story does not state
     * yet, or {@code null} when it would change nothing: the proposed narrative is essentially the
     * story's own (see {@link SuggestionDedupPolicy#sameNarrative}) and no criterion is new. Such a
     * proposal only repeats the story, which is noise in the analyst's queue.
     */
    private @Nullable Suggestion updateOf(GenerationResult.GeneratedStory gen, UUID sessionId, UUID projectId,
                                          UserStory target, @Nullable Double similarity) {
        SuggestionDedupPolicy.Draft current = SuggestionDedupPolicy.Draft.of(target);
        List<Suggestion.DraftCriterion> added = dedupPolicy.criteriaMissingFrom(draftCriteriaOf(gen), current.criteria());
        if (added.isEmpty() && dedupPolicy.sameNarrative(SuggestionDedupPolicy.Draft.of(gen), current)) {
            log.debug("Skipping no-op UPDATE_STORY '{}' for story {}: same narrative, no new acceptance criteria "
                    + "(session={})", gen.title(), target.getId(), sessionId);
            return null;
        }
        Suggestion update = Suggestion.updateStory(sessionId, projectId,
                gen.title(), gen.role(), gen.action(), gen.benefit(),
                gen.priority(), gen.storyPoints(), target.getId(), added);
        if (similarity != null) {
            update.recordSimilarity(similarity);
        }
        return update;
    }

    /**
     * The closest story to {@code embedding} for an EDGE_CASE fallback, but only when it clears the
     * dedup floor — a weak nearest neighbor is not a real "belongs to this story" match, so return null
     * and let the accept handler surface a no-target error rather than attach the edge case to (or, on
     * accept, mint a standalone story from) a story it does not belong to.
     */
    private @Nullable UUID resolveEdgeCaseTargetByEmbedding(UUID projectId, float[] embedding) {
        return stories.findMostSimilar(projectId, embedding)
                .filter(s -> s.similarity() >= dedupPolicy.similarityThreshold())
                .map(UserStoryRepository.SimilarStory::storyId)
                .orElse(null);
    }

    /** The story the LLM-returned id denotes when it is a real story of this project; {@code null} otherwise. */
    private @Nullable UserStory validatedTarget(@Nullable UUID targetStoryId, UUID projectId) {
        if (targetStoryId == null) return null;
        UserStory story = stories.findByIdAndProjectId(targetStoryId, projectId).orElse(null);
        if (story == null) {
            log.debug("LLM returned targetStoryId {} that is not a story of project {}; ignoring it",
                    targetStoryId, projectId);
        }
        return story;
    }
}
