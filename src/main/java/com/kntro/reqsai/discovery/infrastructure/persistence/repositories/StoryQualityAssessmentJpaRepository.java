package com.kntro.reqsai.discovery.infrastructure.persistence.repositories;

import com.kntro.reqsai.discovery.domain.model.StoryQualityAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link StoryQualityAssessment} (tenant-scoped table {@code story_quality_assessments}). */
public interface StoryQualityAssessmentJpaRepository extends JpaRepository<StoryQualityAssessment, UUID> {

    Optional<StoryQualityAssessment> findByStoryId(UUID storyId);

    List<StoryQualityAssessment> findAllByStoryIdIn(Collection<UUID> storyIds);
}
