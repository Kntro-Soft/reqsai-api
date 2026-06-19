package com.kntro.reqsai.workspace.interfaces.rest.mappers.request;

import com.kntro.reqsai.workspace.application.command.CreateProjectCommand;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateProjectRequest;
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
}
