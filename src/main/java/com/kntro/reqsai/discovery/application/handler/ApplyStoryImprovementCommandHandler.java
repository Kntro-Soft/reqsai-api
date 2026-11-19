package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.ApplyStoryImprovementCommand;
import com.kntro.reqsai.discovery.application.port.StoryDraft;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.service.PendingValues;
import com.kntro.reqsai.discovery.application.service.StoryQualityAssessor;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.StoryQualityAssessment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Applies an improved story the analyst reviewed (US73), in one transaction: the title, role, action and benefit are
 * updated and the acceptance criteria are replaced by the proposal's, then the quality is evaluated and stored.
 * <p>
 * It does what a manual edit does, through the same domain methods ({@link UserStory#update}, removing and adding
 * criteria), so the same invariants hold (required fields, lengths). It keeps the story's priority, estimate and
 * <b>status</b>: like the manual update, it does not send an approved story back to draft, so the analyst decides
 * whether to review it again. It does not touch the generated text ({@code generated_text}, US71), so an applied
 * improvement counts as a rewrite of what the AI first proposed. It makes no AI call and costs no allowance.
 * <p>
 * A {@code [[...]]} token still present in any text answers {@code IMPROVEMENT_PENDING_VALUES} (422) and changes
 * nothing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApplyStoryImprovementCommandHandler {

    private final UserStoryRepository stories;
    private final StoryQualityAssessor qualityAssessor;

    @Transactional
    public AppliedStoryImprovement handle(ApplyStoryImprovementCommand command) {
        UserStory story = stories.findByIdAndProjectId(command.storyId(), command.projectId())
                .orElseThrow(() -> DiscoveryExceptions.userStoryNotFound(command.storyId()));
        StoryDraft proposal = command.proposal();
        List<String> pending = PendingValues.in(proposal);
        if (!pending.isEmpty()) {
            throw DiscoveryExceptions.improvementPendingValues(pending);
        }

        story.update(proposal.title(), proposal.role(), proposal.action(), proposal.benefit(),
                story.getPriority(), story.getStoryPoints());
        List<UUID> previous = story.getAcceptanceCriteria().stream().map(c -> c.getId()).toList();
        previous.forEach(story::removeAcceptanceCriterion);
        proposal.acceptanceCriteria().forEach(c ->
                story.addAcceptanceCriterion(c.scenario(), c.given(), c.when(), c.then()));

        UserStory saved = stories.save(story);
        StoryQualityAssessment quality = qualityAssessor.evaluate(saved);
        log.info("Improvement applied to story {} of project {}: {} criteria replaced by {}, quality score {}",
                saved.getId(), command.projectId(), previous.size(), proposal.acceptanceCriteria().size(),
                quality.getScore());
        return new AppliedStoryImprovement(saved, quality);
    }
}
