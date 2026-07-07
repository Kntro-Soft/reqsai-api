package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.CreateUserStoryCommand;
import com.kntro.reqsai.discovery.application.service.UserStoryDeduplicationService;
import com.kntro.reqsai.discovery.application.port.UserStoryRepository;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manually creates a {@link UserStory} in {@code DRAFT} for the current tenant. Persistence routes to
 * the tenant schema automatically (set per request from the JWT {@code orgId}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreateUserStoryCommandHandler {

    private final UserStoryRepository stories;
    private final UserStoryDeduplicationService deduplication;

    @Transactional
    public UserStory handle(CreateUserStoryCommand command) {
        UserStory story = new UserStory(command.projectId(), command.title(), command.role(), command.action(), command.benefit(), command.priority(), command.storyPoints());

        deduplication.embedAndGuardDuplicates(story);

        UserStory saved = stories.save(story);
        log.info("User story {} created for project {}", saved.getId(), command.projectId());
        return saved;
    }
}
