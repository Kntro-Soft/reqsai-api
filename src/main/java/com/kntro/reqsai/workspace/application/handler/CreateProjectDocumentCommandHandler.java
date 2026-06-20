package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.CreateProjectDocumentCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectDocumentRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceExceptions;
import com.kntro.reqsai.workspace.domain.model.DocumentStatus;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateProjectDocumentCommandHandler {

    private final OrganizationRepository organizations;
    private final ProjectRepository projects;
    private final ProjectDocumentRepository documents;

    @Transactional
    public ProjectDocument handle(CreateProjectDocumentCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));

        projects.findByIdAndOrganizationIdAndStatus(
                        command.projectId(), command.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        String normalizedName = ProjectDocument.normalizeName(command.name());
        if (documents.existsByProjectIdAndNameAndStatus(command.projectId(), normalizedName, DocumentStatus.ACTIVE)) {
            throw WorkspaceExceptions.projectDocumentAlreadyExists(normalizedName);
        }

        int maxDocuments = organization.getPlanLimits().maxDocumentsPerProject();
        if (maxDocuments != -1 && documents.countByProjectIdAndStatus(command.projectId(), DocumentStatus.ACTIVE) >= maxDocuments) {
            throw WorkspaceExceptions.projectDocumentPlanLimitExceeded(maxDocuments);
        }

        return documents.save(new ProjectDocument(command.projectId(), normalizedName, command.documentType()));
    }
}
