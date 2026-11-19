package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.StoryQualityAssessment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for the latest quality assessment of each story (tenant schema). */
public interface StoryQualityAssessmentRepository {

    StoryQualityAssessment save(StoryQualityAssessment assessment);

    Optional<StoryQualityAssessment> findByStoryId(UUID storyId);

    /** The stored assessments of the given stories; a story that was never evaluated has none. */
    List<StoryQualityAssessment> findAllByStoryIdIn(Collection<UUID> storyIds);
}
