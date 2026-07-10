package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.BatchDeleteUserStoriesCommand;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Permanently deletes several {@link UserStory} aggregates of a project in one transaction. Only the
 * candidate ids that actually belong to the project are resolved and deleted; ids that are unknown or
 * live in another project/tenant are silently skipped (best-effort, never an error), so the returned
 * count is the number of stories actually deleted. Each deletion cascades to the story's acceptance
 * criteria via {@code orphanRemoval}.
 * <p>
 * Local delete only: it does NOT touch any external tracker (e.g. Jira) issue a story was exported to.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BatchDeleteUserStoriesCommandHandler {

    private final UserStoryRepository stories;

    /** @return the number of stories actually deleted (candidate ids not in the project are skipped). */
    @Transactional
    public int handle(BatchDeleteUserStoriesCommand command) {
        List<UserStory> found = stories.findAllByProjectIdAndIdIn(command.projectId(), command.storyIds());
        found.forEach(stories::delete);
        log.info("Batch-deleted {} of {} requested user stories for project {}",
                found.size(), command.storyIds().size(), command.projectId());
        return found.size();
    }
}
