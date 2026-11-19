package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.EvaluateStoryQualityCommand;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.service.StoryQualityAssessor;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.StoryQualityAssessment;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates the quality of a story (US68) and stores the result as its latest assessment. Asking again
 * re-evaluates and replaces it. The story is scope-checked against the project (404 when it is not in it).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EvaluateStoryQualityCommandHandler {

    private final UserStoryRepository stories;
    private final StoryQualityAssessor assessor;

    @Transactional
    public StoryQualityAssessment handle(EvaluateStoryQualityCommand command) {
        UserStory story = stories.findByIdAndProjectId(command.storyId(), command.projectId())
                .orElseThrow(() -> DiscoveryExceptions.userStoryNotFound(command.storyId()));
        StoryQualityAssessment assessment = assessor.evaluate(story);
        log.info("Story {} evaluated for project {}: quality score {}",
                story.getId(), command.projectId(), assessment.getScore());
        return assessment;
    }
}
