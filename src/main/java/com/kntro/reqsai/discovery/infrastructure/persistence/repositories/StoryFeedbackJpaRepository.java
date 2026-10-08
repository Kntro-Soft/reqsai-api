package com.kntro.reqsai.discovery.infrastructure.persistence.repositories;

import com.kntro.reqsai.discovery.domain.model.StoryFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link StoryFeedback} (tenant-scoped table {@code story_feedback}). */
public interface StoryFeedbackJpaRepository extends JpaRepository<StoryFeedback, UUID> {

    List<StoryFeedback> findAllByStoryIdInOrderByCreatedAtAsc(Collection<UUID> storyIds);

    long countByShareLinkId(UUID shareLinkId);
}
