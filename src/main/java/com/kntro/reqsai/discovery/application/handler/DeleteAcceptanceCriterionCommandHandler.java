package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.DeleteAcceptanceCriterionCommand;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteAcceptanceCriterionCommandHandler {

    private final UserStoryRepository stories;

    @Transactional
    public void handle(DeleteAcceptanceCriterionCommand command) {
        UserStory story = stories.findByIdAndProjectId(command.storyId(), command.projectId())
                .orElseThrow(() -> DiscoveryExceptions.userStoryNotFound(command.storyId()));
        story.removeAcceptanceCriterion(command.criterionId());
        stories.save(story);
    }
}
