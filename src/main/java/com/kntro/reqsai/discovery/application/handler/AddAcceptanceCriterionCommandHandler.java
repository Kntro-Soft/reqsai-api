package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.AddAcceptanceCriterionCommand;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.AcceptanceCriterion;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AddAcceptanceCriterionCommandHandler {

    private final UserStoryRepository stories;

    @Transactional
    public AcceptanceCriterion handle(AddAcceptanceCriterionCommand command) {
        UserStory story = stories.findByIdAndProjectId(command.storyId(), command.projectId())
                .orElseThrow(() -> DiscoveryExceptions.userStoryNotFound(command.storyId()));
        AcceptanceCriterion criterion = story.addAcceptanceCriterion(
                command.scenario(), command.given(), command.when(), command.then());
        stories.save(story);
        return criterion;
    }
}
