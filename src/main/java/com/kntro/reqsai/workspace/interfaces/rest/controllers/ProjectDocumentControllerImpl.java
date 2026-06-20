package com.kntro.reqsai.workspace.interfaces.rest.controllers;

import com.kntro.reqsai.workspace.application.handler.CreateProjectDocumentCommandHandler;
import com.kntro.reqsai.workspace.application.handler.DeleteProjectDocumentCommandHandler;
import com.kntro.reqsai.workspace.application.handler.GetProjectDocumentQueryHandler;
import com.kntro.reqsai.workspace.application.handler.ListProjectDocumentsQueryHandler;
import com.kntro.reqsai.workspace.application.handler.UpdateProjectDocumentCommandHandler;
import com.kntro.reqsai.workspace.application.query.GetProjectDocumentQuery;
import com.kntro.reqsai.workspace.application.query.ListProjectDocumentsQuery;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.CreateProjectDocumentRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.request.UpdateProjectDocumentRequest;
import com.kntro.reqsai.workspace.interfaces.rest.dto.response.ProjectDocumentResponse;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.request.ProjectDocumentRequestMapper;
import com.kntro.reqsai.workspace.interfaces.rest.mappers.response.ProjectDocumentResponseMapper;
import com.kntro.reqsai.workspace.interfaces.rest.swagger.ProjectDocumentController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProjectDocumentControllerImpl implements ProjectDocumentController {

    private final CreateProjectDocumentCommandHandler createProjectDocument;
    private final ListProjectDocumentsQueryHandler listProjectDocuments;
    private final GetProjectDocumentQueryHandler getProjectDocument;
    private final UpdateProjectDocumentCommandHandler updateProjectDocument;
    private final DeleteProjectDocumentCommandHandler deleteProjectDocument;

    @Override
    public ResponseEntity<ProjectDocumentResponse> createDocument(
            UUID orgId,
            UUID projectId,
            CreateProjectDocumentRequest request,
            Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        ProjectDocument document = createProjectDocument.handle(
                ProjectDocumentRequestMapper.toCommand(orgId, projectId, request, requestedBy));

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(document.getId())
                .toUri();

        return ResponseEntity.created(location)
                .body(ProjectDocumentResponseMapper.toResponse(document));
    }

    @Override
    public ResponseEntity<List<ProjectDocumentResponse>> listDocuments(UUID orgId, UUID projectId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        List<ProjectDocumentResponse> response = listProjectDocuments.handle(
                        new ListProjectDocumentsQuery(orgId, projectId, requestedBy))
                .stream()
                .map(ProjectDocumentResponseMapper::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ProjectDocumentResponse> getDocument(UUID orgId, UUID projectId, UUID documentId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        ProjectDocument document = getProjectDocument.handle(
                new GetProjectDocumentQuery(orgId, projectId, documentId, requestedBy));
        return ResponseEntity.ok(ProjectDocumentResponseMapper.toResponse(document));
    }

    @Override
    public ResponseEntity<ProjectDocumentResponse> updateDocument(
            UUID orgId,
            UUID projectId,
            UUID documentId,
            UpdateProjectDocumentRequest request,
            Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        ProjectDocument document = updateProjectDocument.handle(
                ProjectDocumentRequestMapper.toCommand(orgId, projectId, documentId, request, requestedBy));
        return ResponseEntity.ok(ProjectDocumentResponseMapper.toResponse(document));
    }

    @Override
    public ResponseEntity<Void> deleteDocument(UUID orgId, UUID projectId, UUID documentId, Authentication authentication) {
        UUID requestedBy = UUID.fromString(authentication.getName());
        deleteProjectDocument.handle(
                ProjectDocumentRequestMapper.toDeleteCommand(orgId, projectId, documentId, requestedBy));
        return ResponseEntity.noContent().build();
    }
}
