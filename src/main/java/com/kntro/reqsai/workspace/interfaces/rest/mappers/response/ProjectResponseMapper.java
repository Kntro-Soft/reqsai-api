package com.kntro.reqsai.workspace.interfaces.rest.mappers.response;

import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.TechnicalProfileDto;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectResponse;

public final class ProjectResponseMapper {

    private ProjectResponseMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ProjectResponse toResponse(Project project) {
        TechnicalProfileDto technicalProfileDto = new TechnicalProfileDto(
                project.getTechnicalProfile().programmingLanguages(),
                project.getTechnicalProfile().frameworks(),
                project.getTechnicalProfile().clientPlatforms(),
                project.getTechnicalProfile().databases(),
                project.getTechnicalProfile().architecture(),
                project.getTechnicalProfile().domain()
        );

        return new ProjectResponse(
                project.getId(),
                project.getOrganizationId(),
                project.getName(),
                project.getDescription(),
                technicalProfileDto,
                project.getStatus().name(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
