package com.kntro.reqsai.workspace.interfaces.rest.mappers.request;

import com.kntro.reqsai.workspace.application.command.AddProjectConstraintCommand;
import com.kntro.reqsai.workspace.application.command.DeleteProjectConstraintCommand;
import com.kntro.reqsai.workspace.application.command.UpdateProjectConstraintCommand;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.AddProjectConstraintRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateProjectConstraintRequest;

import java.util.UUID;

public final class ProjectConstraintRequestMapper {

    private ProjectConstraintRequestMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static AddProjectConstraintCommand toCommand(
            UUID orgId,
            UUID projectId,
            AddProjectConstraintRequest request,
            UUID requestedBy) {
        return new AddProjectConstraintCommand(orgId, projectId, request.description(), requestedBy);
    }

    public static UpdateProjectConstraintCommand toCommand(
            UUID orgId,
            UUID projectId,
            UUID constraintId,
            UpdateProjectConstraintRequest request,
            UUID requestedBy) {
        return new UpdateProjectConstraintCommand(
                orgId, projectId, constraintId, request.description(), requestedBy);
    }

    public static DeleteProjectConstraintCommand toDeleteCommand(
            UUID orgId,
            UUID projectId,
            UUID constraintId,
            UUID requestedBy) {
        return new DeleteProjectConstraintCommand(orgId, projectId, constraintId, requestedBy);
    }
}
