package com.kntro.reqsai.workspace.interfaces.rest.mappers.request;

import com.kntro.reqsai.workspace.application.command.CreateProjectMemberCommand;
import com.kntro.reqsai.workspace.application.command.DeleteProjectMemberCommand;
import com.kntro.reqsai.workspace.application.command.UpdateProjectMemberCommand;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateProjectMemberRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateProjectMemberRequest;

import java.util.UUID;

public final class ProjectMemberRequestMapper {

    private ProjectMemberRequestMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static CreateProjectMemberCommand toCommand(UUID orgId, UUID projectId, CreateProjectMemberRequest request, UUID requestedBy) {
        return new CreateProjectMemberCommand(orgId, projectId, request.memberId(), request.roleId(), requestedBy);
    }

    public static UpdateProjectMemberCommand toCommand(UUID orgId, UUID projectId, UUID assignmentId, UpdateProjectMemberRequest request, UUID requestedBy) {
        return new UpdateProjectMemberCommand(orgId, projectId, assignmentId, request.roleId(), requestedBy);
    }

    public static DeleteProjectMemberCommand toDeleteCommand(UUID orgId, UUID projectId, UUID assignmentId, UUID requestedBy) {
        return new DeleteProjectMemberCommand(orgId, projectId, assignmentId, requestedBy);
    }
}
