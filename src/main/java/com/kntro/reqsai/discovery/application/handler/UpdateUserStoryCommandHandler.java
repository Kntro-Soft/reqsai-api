package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.UpdateUserStoryCommand;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manually edits the core fields of an existing {@link UserStory} for the current tenant. The story is
 * scope-checked against the project in a single lookup (404 when it does not exist in the project).
 * <p>
 * Deliberately a straight field update: unlike creation, it does NOT run deduplication or recompute
 * the similarity embedding. A manual edit is analyst intent, not a new candidate to dedup against the
 * backlog, so the story keeps its existing indexed/embedded state (the async re-index pass already
 * covers stories that need a fresh embedding).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateUserStoryCommandHandler {

    private final UserStoryRepository stories;

    @Transactional
    public UserStory handle(UpdateUserStoryCommand command) {
        UserStory story = stories.findByIdAndProjectId(command.storyId(), command.projectId())
                .orElseThrow(() -> DiscoveryExceptions.userStoryNotFound(command.storyId()));

        story.update(command.title(), command.role(), command.action(),
                command.benefit(), command.priority(), command.storyPoints());

        UserStory saved = stories.save(story);
        log.info("User story {} updated for project {}", saved.getId(), command.projectId());
        return saved;
    }
}
