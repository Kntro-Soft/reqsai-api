package com.kntro.reqsai.workspace.interfaces.rest.mappers.response;

import com.kntro.reqsai.workspace.domain.model.ProjectConstraint;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectConstraintResponse;

public final class ProjectConstraintResponseMapper {

    private ProjectConstraintResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static ProjectConstraintResponse toResponse(ProjectConstraint constraint) {
        return new ProjectConstraintResponse(
                constraint.getId(),
                constraint.getDescription(),
                constraint.getCreatedAt(),
                constraint.getUpdatedAt());
    }
}
