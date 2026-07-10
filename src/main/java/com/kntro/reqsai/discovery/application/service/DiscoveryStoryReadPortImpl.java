package com.kntro.reqsai.discovery.application.service;

import com.kntro.reqsai.discovery.api.AcceptanceCriterionView;
import com.kntro.reqsai.discovery.api.DiscoveryStoryReadPort;
import com.kntro.reqsai.discovery.api.StoryView;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.model.AcceptanceCriterion;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Package-private cross-context implementation of {@link DiscoveryStoryReadPort}. Reads {@link UserStory}
 * aggregates through the existing {@link UserStoryRepository} and maps them to boundary value records,
 * so no JPA entity crosses the module boundary.
 */
@Component
@RequiredArgsConstructor
class DiscoveryStoryReadPortImpl implements DiscoveryStoryReadPort {

    private final UserStoryRepository stories;

    @Override
    @Transactional(readOnly = true)
    public Optional<StoryView> findStory(UUID projectId, UUID storyId) {
        return stories.findByIdAndProjectId(storyId, projectId).map(DiscoveryStoryReadPortImpl::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoryView> listStories(UUID projectId) {
        return stories.findAllByProjectId(projectId, Pageable.unpaged())
                .map(DiscoveryStoryReadPortImpl::toView)
                .getContent();
    }

    private static StoryView toView(UserStory story) {
        List<AcceptanceCriterionView> criteria = story.getAcceptanceCriteria().stream()
                .map(DiscoveryStoryReadPortImpl::toView)
                .toList();
        return new StoryView(
                story.getId(),
                story.getProjectId(),
                story.getTitle(),
                story.getRole(),
                story.getAction(),
                story.getBenefit(),
                story.getPriority().name(),
                story.getStoryPoints(),
                criteria);
    }

    private static AcceptanceCriterionView toView(AcceptanceCriterion c) {
        return new AcceptanceCriterionView(c.getScenario(), c.getGiven(), c.getWhen(), c.getThen());
    }
}
