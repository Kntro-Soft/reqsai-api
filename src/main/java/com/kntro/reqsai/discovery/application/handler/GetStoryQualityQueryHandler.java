package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.StoryQualityAssessmentRepository;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.query.GetStoryQualityQuery;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.StoryQualityAssessment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reads the latest stored quality assessment of a story; 404 when the story has not been evaluated yet. */
@Component
@RequiredArgsConstructor
public class GetStoryQualityQueryHandler {

    private final UserStoryRepository stories;
    private final StoryQualityAssessmentRepository assessments;

    @Transactional(readOnly = true)
    public StoryQualityAssessment handle(GetStoryQualityQuery query) {
        stories.findByIdAndProjectId(query.storyId(), query.projectId())
                .orElseThrow(() -> DiscoveryExceptions.userStoryNotFound(query.storyId()));
        return assessments.findByStoryId(query.storyId())
                .orElseThrow(() -> DiscoveryExceptions.storyQualityNotEvaluated(query.storyId()));
    }
}
