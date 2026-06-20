package com.kntro.reqsai.workspace.interfaces.rest.mappers.response;

import com.kntro.reqsai.workspace.domain.model.ProjectRole;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectRoleResponse;

public final class ProjectRoleResponseMapper {

    private ProjectRoleResponseMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ProjectRoleResponse toResponse(ProjectRole role) {
        return new ProjectRoleResponse(
                role.getId(),
                role.getProjectId(),
                role.getName(),
                role.getPermissions(),
                role.getCreatedAt(),
                role.getUpdatedAt());
    }
}
