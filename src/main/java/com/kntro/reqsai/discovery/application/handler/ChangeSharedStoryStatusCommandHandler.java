package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.command.ChangeSharedStoryStatusCommand;
import com.kntro.reqsai.discovery.application.command.ChangeUserStoryStatusCommand;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Lets a reviewer with a session link record the review decision on a story the session produced. Stories
 * of other sessions, or of none, read as unknown.
 */
@Component
@RequiredArgsConstructor
public class ChangeSharedStoryStatusCommandHandler {

    private final SharedLinkGateway gateway;
    private final SharedSessionReader reader;
    private final ChangeUserStoryStatusCommandHandler changeStatus;

    public UserStory handle(ChangeSharedStoryStatusCommand command) {
        return gateway.withActiveSessionLink(command.token(), link -> {
            if (!link.canReview()) {
                throw DiscoveryExceptions.shareLinkReadOnly();
            }
            if (!reader.isSessionStory(link, command.storyId())) {
                throw DiscoveryExceptions.userStoryNotFound(command.storyId());
            }
            return changeStatus.handle(
                    new ChangeUserStoryStatusCommand(link.getProjectId(), command.storyId(), command.status()));
        });
    }
}
