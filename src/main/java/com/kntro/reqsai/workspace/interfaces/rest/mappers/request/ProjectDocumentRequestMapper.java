package com.kntro.reqsai.workspace.interfaces.rest.mappers.request;

import com.kntro.reqsai.workspace.application.command.CreateProjectDocumentCommand;
import com.kntro.reqsai.workspace.application.command.DeleteProjectDocumentCommand;
import com.kntro.reqsai.workspace.application.command.UpdateProjectDocumentCommand;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateProjectDocumentRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateProjectDocumentRequest;

import java.util.UUID;

public final class ProjectDocumentRequestMapper {

    private ProjectDocumentRequestMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static CreateProjectDocumentCommand toCommand(
            UUID orgId,
            UUID projectId,
            CreateProjectDocumentRequest request,
            UUID requestedBy) {
        return new CreateProjectDocumentCommand(
                orgId,
                projectId,
                request.name(),
                request.documentType(),
                requestedBy);
    }

    public static UpdateProjectDocumentCommand toCommand(
            UUID orgId,
            UUID projectId,
            UUID documentId,
            UpdateProjectDocumentRequest request,
            UUID requestedBy) {
        return new UpdateProjectDocumentCommand(
                orgId,
                projectId,
                documentId,
                request.name(),
                request.documentType(),
                requestedBy);
    }

    public static DeleteProjectDocumentCommand toDeleteCommand(
            UUID orgId,
            UUID projectId,
            UUID documentId,
            UUID requestedBy) {
        return new DeleteProjectDocumentCommand(orgId, projectId, documentId, requestedBy);
    }
}
