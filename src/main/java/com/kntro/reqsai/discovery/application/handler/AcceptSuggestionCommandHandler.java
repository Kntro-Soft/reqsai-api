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

import java.util.UUID;

/**
 * Handles {@link AcceptSuggestionCommand}: routes the acceptance to the appropriate backlog
 * mutation depending on the suggestion's {@link SuggestionType}:
 *
 * <ul>
 *   <li>{@code NEW_STORY}  — creates a new {@link UserStory} from the draft fields.</li>
 *   <li>{@code UPDATE_STORY} — updates the target story's fields with the draft (or analyst edits).</li>
 *   <li>{@code EDGE_CASE}  — adds an acceptance criterion to the target story.</li>
 *   <li>{@code CLARIFYING_QUESTION} — marks accepted with no story produced ({@code resolvedStoryId=null}).</li>
 * </ul>
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
        Suggestion suggestion = suggestions.findByIdAndSessionId(cmd.suggestionId(), cmd.sessionId())
                .orElseThrow(() -> DiscoveryExceptions.suggestionNotFound(cmd.suggestionId()));

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
        String title  = coalesce(cmd.editedTitle(),  s.getDraftTitle());
        String role   = coalesce(cmd.editedRole(),   s.getDraftRole());
        String action = coalesce(cmd.editedAction(), s.getDraftAction());
        String benefit = coalesce(cmd.editedBenefit(), s.getDraftBenefit());
        Priority priority = cmd.editedPriority() != null ? cmd.editedPriority() : s.getDraftPriority();
        Integer storyPoints = cmd.editedStoryPoints() != null ? cmd.editedStoryPoints() : s.getDraftStoryPoints();

        UserStory story = new UserStory(s.getSessionId(), s.getProjectId(),
                title, role, action, benefit, priority, storyPoints);
        embedIfAvailable(story);
        return storyRepo.save(story).getId();
    }

    private UUID acceptAsUpdate(Suggestion s, AcceptSuggestionCommand cmd) {
        UUID targetId = s.getTargetStoryId();
        if (targetId == null) {
            // Target was not resolved at creation time — fall back to creating a new story
            log.warn("UPDATE_STORY suggestion {} has no targetStoryId; creating as new story", s.getId());
            return acceptAsNewStory(s, cmd);
        }
        UserStory target = storyRepo.findById(targetId).orElse(null);
        if (target == null) {
            log.warn("UPDATE_STORY suggestion {} target story {} no longer exists; creating as new story",
                    s.getId(), targetId);
            return acceptAsNewStory(s, cmd);
        }

        target.updateFrom(
                coalesce(cmd.editedTitle(),   s.getDraftTitle()),
                coalesce(cmd.editedRole(),    s.getDraftRole()),
                coalesce(cmd.editedAction(),  s.getDraftAction()),
                coalesce(cmd.editedBenefit(), s.getDraftBenefit()),
                cmd.editedPriority() != null ? cmd.editedPriority() : s.getDraftPriority(),
                cmd.editedStoryPoints() != null ? cmd.editedStoryPoints() : s.getDraftStoryPoints());
        return storyRepo.save(target).getId();
    }

    private UUID acceptAsEdgeCase(Suggestion s, AcceptSuggestionCommand cmd) {
        UUID targetId = s.getTargetStoryId();
        if (targetId == null) {
            // No target found at suggestion time — create as a new standalone story
            log.warn("EDGE_CASE suggestion {} has no targetStoryId; creating as new story", s.getId());
            return acceptAsNewStory(s, cmd);
        }
        UserStory target = storyRepo.findById(targetId).orElse(null);
        if (target == null) {
            log.warn("EDGE_CASE suggestion {} target story {} no longer exists; creating as new story",
                    s.getId(), targetId);
            return acceptAsNewStory(s, cmd);
        }

        // Use the draft as a Gherkin criterion on the target story
        String effectiveAction = coalesce(cmd.editedAction(), s.getDraftAction());
        String effectiveBenefit = coalesce(cmd.editedBenefit(), s.getDraftBenefit());
        String effectiveTitle = coalesce(cmd.editedTitle(), s.getDraftTitle());

        target.addAcceptanceCriterion(
                "Edge case: " + effectiveTitle,
                s.getDraftRole() != null ? "the user is " + s.getDraftRole() : "the system is in scope",
                effectiveAction,
                effectiveBenefit);
        return storyRepo.save(target).getId();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void embedIfAvailable(UserStory story) {
        if (embeddingPort.isAvailable()) {
            story.assignEmbedding(embeddingPort.embed(story.toCanonicalText()));
        }
    }

    @Nullable
    private static String coalesce(@Nullable String override, @Nullable String fallback) {
        return override != null ? override : fallback;
    }
}
