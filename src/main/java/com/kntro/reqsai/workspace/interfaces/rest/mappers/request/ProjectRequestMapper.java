package com.kntro.reqsai.workspace.interfaces.rest.mappers.request;

import com.kntro.reqsai.workspace.application.command.CreateProjectCommand;
import com.kntro.reqsai.workspace.application.command.UpdateProjectCommand;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateProjectRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateProjectRequest;
import java.util.UUID;

public final class ProjectRequestMapper {

    private ProjectRequestMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static CreateProjectCommand toCommand(UUID orgId, CreateProjectRequest request, UUID requestedBy) {
        return new CreateProjectCommand(
                orgId,
                request.name(),
                request.description(),
                request.programmingLanguages(),
                request.frameworks(),
                request.clientPlatforms(),
                request.databases(),
                request.architecture(),
                request.domain(),
                requestedBy
        );
    }

    public static UpdateProjectCommand toCommand(UUID orgId, UUID projectId, UpdateProjectRequest request, UUID requestedBy) {
        return new UpdateProjectCommand(
                orgId,
                projectId,
                request.name(),
                request.description(),
                request.programmingLanguages(),
                request.frameworks(),
                request.clientPlatforms(),
                request.databases(),
                request.architecture(),
                request.domain(),
                requestedBy
        );
    }
}
