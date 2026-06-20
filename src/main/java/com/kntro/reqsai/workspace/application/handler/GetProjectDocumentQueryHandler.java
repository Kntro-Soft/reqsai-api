package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectDocumentRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.GetProjectDocumentQuery;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.DocumentStatus;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetProjectDocumentQueryHandler {

    private final OrganizationRepository organizations;
    private final ProjectRepository projects;
    private final ProjectDocumentRepository documents;

    @Transactional(readOnly = true)
    public ProjectDocument handle(GetProjectDocumentQuery query) {
        organizations.findById(query.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(query.organizationId()));

        projects.findByIdAndOrganizationIdAndStatus(
                        query.projectId(), query.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(query.projectId()));

        return documents.findByIdAndProjectIdAndStatus(query.documentId(), query.projectId(), DocumentStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectDocumentNotFound(query.documentId()));
    }
}
