package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.application.query.GetProjectStoryQuery;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetProjectStoryQueryHandler {

    private final UserStoryRepository stories;

    @Transactional(readOnly = true)
    public UserStory handle(GetProjectStoryQuery query) {
        UserStory story = stories.findById(query.storyId())
                .orElseThrow(() -> DiscoveryExceptions.userStoryNotFound(query.storyId()));
        if (!story.getProjectId().equals(query.projectId())) {
            throw DiscoveryExceptions.userStoryNotFound(query.storyId());
        }
        return story;
    }
}
