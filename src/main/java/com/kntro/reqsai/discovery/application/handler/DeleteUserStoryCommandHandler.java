package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.DeleteUserStoryCommand;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Permanently deletes a single {@link UserStory} of the current tenant. The story is scope-checked
 * against the project in a single lookup (404 when it does not exist in the project), mirroring the
 * update path. Deletion is a hard delete (consistent with document deletion): removing the aggregate
 * cascades to its acceptance criteria via {@code orphanRemoval}.
 * <p>
 * This is a Reqs-AI-local delete only. It does NOT touch any external tracker (e.g. Jira) issue the
 * story was previously exported to — the remote issue, if any, is left untouched.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteUserStoryCommandHandler {

    private final UserStoryRepository stories;

    @Transactional
    public void handle(DeleteUserStoryCommand command) {
        UserStory story = stories.findByIdAndProjectId(command.storyId(), command.projectId())
                .orElseThrow(() -> DiscoveryExceptions.userStoryNotFound(command.storyId()));

        stories.delete(story);
        log.info("User story {} deleted for project {}", command.storyId(), command.projectId());
    }
}
