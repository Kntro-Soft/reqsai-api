package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.AcceptSuggestionCommand;
import com.kntro.reqsai.discovery.application.port.SuggestionRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.Priority;
import com.kntro.reqsai.discovery.domain.model.Suggestion;
import com.kntro.reqsai.discovery.domain.model.SuggestionType;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import com.kntro.reqsai.shared.application.port.EmbeddingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Handles {@link AcceptSuggestionCommand}: routes the acceptance to the appropriate backlog
 * mutation depending on the suggestion's {@link SuggestionType}:
 *
 * <ul>
 *   <li>{@code NEW_STORY}  — creates a new {@link UserStory} from the draft (or edited) fields and its
 *       draft (or edited) acceptance criteria.</li>
 *   <li>{@code UPDATE_STORY} — updates the target story's fields with the draft (or analyst edits).</li>
 *   <li>{@code EDGE_CASE}  — adds the draft (or edited) Given/When/Then criterion to the target story
 *       verbatim; when no target story is resolvable it is rejected with a domain error (kept PENDING)
 *       rather than minted as a granularity-violating standalone story.</li>
 *   <li>{@code CLARIFYING_QUESTION} — marks accepted with no story produced ({@code resolvedStoryId=null}).</li>
 * </ul>
 *
 * <p>The analyst may fully edit the draft before accepting: every {@code edited*} field on the command
 * is an optional override (blank text is treated as absent). When
 * {@link AcceptSuggestionCommand#editedAcceptanceCriteria()} is present it REPLACES the draft criteria
 * on the suggestion; when absent, the draft is committed unchanged.
 *
 * <p>Unlike AI-batch creation ({@link com.kntro.reqsai.discovery.application.service.StoryExtractionService}),
 * analyst-accepted suggestions skip the duplicate-guard check — the analyst made a conscious
 * decision. The embedding is still assigned for future similarity searches.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AcceptSuggestionCommandHandler {

    private final SuggestionRepository suggestions;
    private final UserStoryRepository storyRepo;
    private final EmbeddingPort embeddingPort;

    @Transactional
    public Suggestion handle(AcceptSuggestionCommand cmd) {
        Suggestion suggestion = suggestions.findByIdAndSessionIdForUpdate(cmd.suggestionId(), cmd.sessionId())
                .orElseThrow(() -> DiscoveryExceptions.suggestionNotFound(cmd.suggestionId()));

        // Apply the analyst's edited acceptance criteria (when sent) up front so every accept path —
        // and the SuggestionAcceptedEvent — sees the committed set rather than the raw draft.
        applyEditedCriteria(suggestion, cmd);

        UUID resolvedStoryId = switch (suggestion.getType()) {
            case NEW_STORY          -> acceptAsNewStory(suggestion, cmd);
            case UPDATE_STORY       -> acceptAsUpdate(suggestion, cmd);
            case EDGE_CASE          -> acceptAsEdgeCase(suggestion, cmd);
            case CLARIFYING_QUESTION -> null;
        };

        suggestion.accept(resolvedStoryId);
        suggestions.save(suggestion);
        log.info("Suggestion {} ({}) accepted by analyst → resolvedStoryId={}",
                suggestion.getId(), suggestion.getType(), resolvedStoryId);
        return suggestion;
    }

    // ── Type-specific acceptance logic ────────────────────────────────────────

    private UUID acceptAsNewStory(Suggestion s, AcceptSuggestionCommand cmd) {
        UserStory story = new UserStory(s.getSessionId(), s.getProjectId(),
                title(s, cmd), role(s, cmd), action(s, cmd), benefit(s, cmd),
                priority(s, cmd), storyPoints(s, cmd));
        // Carry the (possibly analyst-edited) acceptance criteria onto the story so the analyst does
        // not have to re-type them. Each row was validated non-blank when stored on the suggestion.
        for (Suggestion.DraftCriterion c : s.getDraftAcceptanceCriteria()) {
            story.addAcceptanceCriterion(c.scenario(), c.given(), c.when(), c.then());
        }
        embedIfAvailable(story);
        return storyRepo.save(story).getId();
    }

    private UUID acceptAsUpdate(Suggestion s, AcceptSuggestionCommand cmd) {
        UserStory target = resolveTarget(s);
        if (target == null) {
            log.warn("UPDATE_STORY suggestion {} has no usable target story; creating as new story", s.getId());
            return acceptAsNewStory(s, cmd);
        }
        target.updateFrom(title(s, cmd), role(s, cmd), action(s, cmd), benefit(s, cmd),
                priority(s, cmd), storyPoints(s, cmd));
        embedIfAvailable(target);
        return storyRepo.save(target).getId();
    }

    private UUID acceptAsEdgeCase(Suggestion s, AcceptSuggestionCommand cmd) {
        Suggestion.DraftCriterion criterion = s.getDraftAcceptanceCriteria().stream().findFirst().orElse(null);
        UserStory target = resolveTarget(s);
        if (target == null) {
            // No target resolvable at accept time. An edge case is a boundary/validation rule OF an
            // existing capability, so silently minting a standalone story from it would violate
            // granularity (the very thing the EDGE_CASE classification avoids). Surface a clear domain
            // error instead and leave the suggestion PENDING, so the analyst assigns a target (edit) or
            // explicitly reclassifies it rather than getting a spurious one-criterion story.
            log.warn("EDGE_CASE suggestion {} has no usable target story; rejecting accept (kept PENDING)", s.getId());
            throw DiscoveryExceptions.edgeCaseWithoutTarget(s.getId());
        }
        if (criterion == null) {
            // Nothing concrete to attach (the LLM omitted a usable Given/When/Then and the analyst sent
            // none). Accepting must still succeed; leave the target unchanged.
            log.warn("EDGE_CASE suggestion {} carries no acceptance criterion; target story {} left unchanged",
                    s.getId(), target.getId());
            return target.getId();
        }
        target.addAcceptanceCriterion(criterion.scenario(), criterion.given(), criterion.when(), criterion.then());
        return storyRepo.save(target).getId();
    }

    // ── Field resolution (edited override → draft fallback) ───────────────────

    private static String title(Suggestion s, AcceptSuggestionCommand cmd) {
        return coalesce(cmd.editedTitle(), s.getDraftTitle());
    }

    private static String role(Suggestion s, AcceptSuggestionCommand cmd) {
        return coalesce(cmd.editedRole(), s.getDraftRole());
    }

    private static String action(Suggestion s, AcceptSuggestionCommand cmd) {
        return coalesce(cmd.editedAction(), s.getDraftAction());
    }

    private static String benefit(Suggestion s, AcceptSuggestionCommand cmd) {
        return coalesce(cmd.editedBenefit(), s.getDraftBenefit());
    }

    private static Priority priority(Suggestion s, AcceptSuggestionCommand cmd) {
        return cmd.editedPriority() != null ? cmd.editedPriority() : s.getDraftPriority();
    }

    private static Integer storyPoints(Suggestion s, AcceptSuggestionCommand cmd) {
        return cmd.editedStoryPoints() != null ? cmd.editedStoryPoints() : s.getDraftStoryPoints();
    }

    /**
     * Replaces the suggestion's draft criteria with the analyst-edited set when
     * {@code editedAcceptanceCriteria} is present (non-null). Domain sanitization drops any entry
     * missing given/when/then, mirroring how the draft criteria were validated when created (the REST
     * layer's {@code @NotBlank} already rejects such entries before this point).
     */
    private static void applyEditedCriteria(Suggestion s, AcceptSuggestionCommand cmd) {
        if (cmd.editedAcceptanceCriteria() == null) {
            return;
        }
        List<Suggestion.DraftCriterion> replacement = cmd.editedAcceptanceCriteria().stream()
                .map(c -> new Suggestion.DraftCriterion(c.scenario(), c.given(), c.when(), c.then()))
                .toList();
        s.replaceDraftCriteria(replacement);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** The target story of an UPDATE_STORY / EDGE_CASE suggestion, or {@code null} when unusable. */
    private @Nullable UserStory resolveTarget(Suggestion s) {
        UUID targetId = s.getTargetStoryId();
        if (targetId == null) {
            return null;
        }
        UserStory target = storyRepo.findById(targetId).orElse(null);
        if (target == null || !target.getProjectId().equals(s.getProjectId())) {
            log.warn("Suggestion {} target story {} is missing or in another project", s.getId(), targetId);
            return null;
        }
        return target;
    }

    /**
     * Best-effort embedding of an accepted story for future similarity search.
     *
     * <p>The analyst has explicitly accepted the suggestion, so the backlog mutation must not be lost
     * to a transient embedding-provider failure (a cold model, a timed-out or refused connection —
     * these frequently surface on the first call and succeed on a retry). A failure here leaves the
     * story un-indexed ({@link UserStory#isIndexed()} {@code == false}), exactly the state produced
     * when no embedding model is configured; it can be re-indexed later. Only the embedding is
     * skipped — the accept itself still commits.
     */
    private void embedIfAvailable(UserStory story) {
        if (!embeddingPort.isAvailable()) {
            return;
        }
        try {
            story.assignEmbedding(embeddingPort.embed(story.toCanonicalText()));
        } catch (RuntimeException e) {
            log.warn("Embedding failed while accepting a suggestion for story '{}' (project {}); "
                    + "persisting it un-indexed, similarity search will skip it until re-indexed: {}",
                    story.getTitle(), story.getProjectId(), e.getMessage());
        }
    }

    /** Prefers {@code override} when present and non-blank; otherwise the draft {@code fallback}. */
    @Nullable
    private static String coalesce(@Nullable String override, @Nullable String fallback) {
        return override != null && !override.isBlank() ? override : fallback;
    }
}
