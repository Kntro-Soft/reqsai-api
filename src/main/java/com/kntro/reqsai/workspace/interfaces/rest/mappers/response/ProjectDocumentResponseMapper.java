package com.kntro.reqsai.workspace.interfaces.rest.mappers.response;

import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectDocumentResponse;

public final class ProjectDocumentResponseMapper {

    private ProjectDocumentResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static ProjectDocumentResponse toResponse(ProjectDocument document) {
        return new ProjectDocumentResponse(
                document.getId(),
                document.getProjectId(),
                document.getName(),
                document.getDocumentType().name(),
                document.getStatus().name(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
