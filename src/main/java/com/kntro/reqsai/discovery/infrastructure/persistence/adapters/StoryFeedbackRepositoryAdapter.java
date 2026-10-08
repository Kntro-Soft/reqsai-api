package com.kntro.reqsai.discovery.infrastructure.persistence.adapters;

import com.kntro.reqsai.discovery.application.port.StoryFeedbackRepository;
import com.kntro.reqsai.discovery.domain.model.StoryFeedback;
import com.kntro.reqsai.discovery.infrastructure.persistence.repositories.StoryFeedbackJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Adapts {@link StoryFeedbackRepository} port to Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class StoryFeedbackRepositoryAdapter implements StoryFeedbackRepository {

    private final StoryFeedbackJpaRepository jpa;

    @Override
    public StoryFeedback save(StoryFeedback feedback) {
        return jpa.save(feedback);
    }

    @Override
    public List<StoryFeedback> findAllByStoryIds(Collection<UUID> storyIds) {
        return storyIds.isEmpty() ? List.of() : jpa.findAllByStoryIdInOrderByCreatedAtAsc(storyIds);
    }

    @Override
    public long countByShareLink(UUID shareLinkId) {
        return jpa.countByShareLinkId(shareLinkId);
    }
}
