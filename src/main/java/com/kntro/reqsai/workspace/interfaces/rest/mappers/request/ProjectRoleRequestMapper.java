package com.kntro.reqsai.workspace.interfaces.rest.mappers.request;

import com.kntro.reqsai.workspace.application.command.CreateProjectRoleCommand;
import com.kntro.reqsai.workspace.application.command.DeleteProjectRoleCommand;
import com.kntro.reqsai.workspace.application.command.UpdateProjectRoleCommand;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateProjectRoleRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateProjectRoleRequest;

import java.util.UUID;

public final class ProjectRoleRequestMapper {

    private ProjectRoleRequestMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static CreateProjectRoleCommand toCommand(UUID orgId, UUID projectId, CreateProjectRoleRequest request, UUID requestedBy) {
        return new CreateProjectRoleCommand(orgId, projectId, request.name(), request.permissions(), requestedBy);
    }

    public static UpdateProjectRoleCommand toCommand(UUID orgId, UUID projectId, UUID roleId, UpdateProjectRoleRequest request, UUID requestedBy) {
        return new UpdateProjectRoleCommand(orgId, projectId, roleId, request.name(), request.permissions(), requestedBy);
    }

    public static DeleteProjectRoleCommand toDeleteCommand(UUID orgId, UUID projectId, UUID roleId, UUID requestedBy) {
        return new DeleteProjectRoleCommand(orgId, projectId, roleId, requestedBy);
    }
}
