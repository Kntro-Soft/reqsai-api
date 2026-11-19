package com.kntro.reqsai.discovery.infrastructure.persistence.adapters;

import com.kntro.reqsai.discovery.application.port.StoryQualityAssessmentRepository;
import com.kntro.reqsai.discovery.domain.model.StoryQualityAssessment;
import com.kntro.reqsai.discovery.infrastructure.persistence.repositories.StoryQualityAssessmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapts {@link StoryQualityAssessmentRepository} port to Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class StoryQualityAssessmentRepositoryAdapter implements StoryQualityAssessmentRepository {

    private final StoryQualityAssessmentJpaRepository jpa;

    @Override
    public StoryQualityAssessment save(StoryQualityAssessment assessment) {
        return jpa.save(assessment);
    }

    @Override
    public Optional<StoryQualityAssessment> findByStoryId(UUID storyId) {
        return jpa.findByStoryId(storyId);
    }

    @Override
    public List<StoryQualityAssessment> findAllByStoryIdIn(Collection<UUID> storyIds) {
        return storyIds.isEmpty() ? List.of() : jpa.findAllByStoryIdIn(storyIds);
    }
}
