package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.ChangeUserStoryStatusCommand;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.StoryStatus;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the review decision on a user story (approve, reject, back to draft). The transition rules
 * live in {@link UserStory#changeReviewStatus}; the story fields, criteria and embedding are untouched.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChangeUserStoryStatusCommandHandler {

    private final UserStoryRepository stories;

    @Transactional
    public UserStory handle(ChangeUserStoryStatusCommand command) {
        UserStory story = stories.findByIdAndProjectId(command.storyId(), command.projectId())
                .orElseThrow(() -> DiscoveryExceptions.userStoryNotFound(command.storyId()));
        StoryStatus previous = story.getStatus();
        story.changeReviewStatus(command.status());
        UserStory saved = stories.save(story);
        log.info("User story {} moved from {} to {} for project {}", saved.getId(), previous, saved.getStatus(), command.projectId());
        return saved;
    }
}
