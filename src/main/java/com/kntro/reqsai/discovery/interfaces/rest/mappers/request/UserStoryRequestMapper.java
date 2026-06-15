package com.kntro.reqsai.discovery.interfaces.rest.mappers.request;

import com.kntro.reqsai.discovery.application.command.CreateUserStoryCommand;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.CreateUserStoryRequest;

import java.util.UUID;

/** Maps inbound user-story request DTOs to application commands. */
public final class UserStoryRequestMapper {

    private UserStoryRequestMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static CreateUserStoryCommand toCommand(UUID projectId, CreateUserStoryRequest request) {
        return new CreateUserStoryCommand(projectId, request.title(), request.role(), request.action(), request.benefit(), request.priority(), request.storyPoints());
    }
}
