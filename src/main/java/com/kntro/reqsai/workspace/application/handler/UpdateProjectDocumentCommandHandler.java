package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.workspace.application.command.UpdateProjectDocumentCommand;
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
public class UpdateProjectDocumentCommandHandler {

    private final OrganizationRepository organizations;
    private final ProjectRepository projects;
    private final ProjectDocumentRepository documents;

    @Transactional
    public ProjectDocument handle(UpdateProjectDocumentCommand command) {
        Organization organization = organizations.findById(command.organizationId())
                .orElseThrow(() -> WorkspaceExceptions.organizationNotFound(command.organizationId()));

        projects.findByIdAndOrganizationIdAndStatus(
                        command.projectId(), command.organizationId(), ProjectStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectNotFound(command.projectId()));

        ProjectDocument document = documents.findByIdAndProjectIdAndStatus(
                        command.documentId(), command.projectId(), DocumentStatus.ACTIVE)
                .orElseThrow(() -> WorkspaceExceptions.projectDocumentNotFound(command.documentId()));

        String normalizedName = ProjectDocument.normalizeName(command.name());
        if (documents.existsByProjectIdAndNameAndIdNotAndStatus(
                command.projectId(), normalizedName, command.documentId(), DocumentStatus.ACTIVE)) {
            throw WorkspaceExceptions.projectDocumentAlreadyExists(normalizedName);
        }

        int maxDocuments = organization.getPlanLimits().maxDocumentsPerProject();
        if (maxDocuments != -1 && documents.countByProjectIdAndStatus(command.projectId(), DocumentStatus.ACTIVE) > maxDocuments) {
            throw WorkspaceExceptions.projectDocumentPlanLimitExceeded(maxDocuments);
        }

        document.updateMetadata(normalizedName, command.documentType());
        return documents.save(document);
    }
}
