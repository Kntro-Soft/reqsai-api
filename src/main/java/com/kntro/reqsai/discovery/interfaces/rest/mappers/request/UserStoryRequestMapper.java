package com.kntro.reqsai.discovery.interfaces.rest.mappers.request;

import com.kntro.reqsai.discovery.application.command.BatchDeleteUserStoriesCommand;
import com.kntro.reqsai.discovery.application.command.CreateUserStoryCommand;
import com.kntro.reqsai.discovery.application.command.DeleteUserStoryCommand;
import com.kntro.reqsai.discovery.application.command.UpdateUserStoryCommand;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.BatchDeleteUserStoriesRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.CreateUserStoryRequest;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.UpdateUserStoryRequest;

import java.util.UUID;

/** Maps inbound user-story request DTOs to application commands. */
public final class UserStoryRequestMapper {

    private UserStoryRequestMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static CreateUserStoryCommand toCommand(UUID projectId, CreateUserStoryRequest request) {
        return new CreateUserStoryCommand(projectId, request.title(), request.role(), request.action(), request.benefit(), request.priority(), request.storyPoints());
    }

    public static UpdateUserStoryCommand toUpdateCommand(UUID projectId, UUID storyId, UpdateUserStoryRequest request) {
        return new UpdateUserStoryCommand(projectId, storyId, request.title(), request.role(), request.action(), request.benefit(), request.priority(), request.storyPoints());
    }

    public static DeleteUserStoryCommand toDeleteCommand(UUID projectId, UUID storyId) {
        return new DeleteUserStoryCommand(projectId, storyId);
    }

    public static BatchDeleteUserStoriesCommand toBatchDeleteCommand(UUID projectId, BatchDeleteUserStoriesRequest request) {
        return new BatchDeleteUserStoriesCommand(projectId, request.storyIds());
    }
}
