package com.kntro.reqsai.workspace.interfaces.rest.mappers.response;

import com.kntro.reqsai.shared.application.avatar.AvatarPaths;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectResponse;

public final class ProjectResponseMapper {

    private ProjectResponseMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getOrganizationId(),
                project.getName(),
                project.getDescription(),
                project.getTechnicalProfile().programmingLanguages(),
                project.getTechnicalProfile().frameworks(),
                project.getTechnicalProfile().clientPlatforms(),
                project.getTechnicalProfile().databases(),
                project.getTechnicalProfile().architecture(),
                project.getTechnicalProfile().domain(),
                project.getStatus().name(),
                AvatarPaths.project(project.getOrganizationId(), project.getId()),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
