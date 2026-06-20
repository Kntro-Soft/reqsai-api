package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.UpdateAcceptanceCriterionCommand;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.AcceptanceCriterion;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateAcceptanceCriterionCommandHandler {

    private final UserStoryRepository stories;

    /**
     * Updates the criterion and returns it so the controller can map the response
     * without an extra DB round-trip.
     */
    @Transactional
    public AcceptanceCriterion handle(UpdateAcceptanceCriterionCommand command) {
        UserStory story = stories.findByIdAndProjectId(command.storyId(), command.projectId())
                .orElseThrow(() -> DiscoveryExceptions.userStoryNotFound(command.storyId()));
        AcceptanceCriterion updated = story.updateAcceptanceCriterion(command.criterionId(), command.scenario(), command.given(), command.when(), command.then());
        stories.save(story);
        return updated;
    }
}
