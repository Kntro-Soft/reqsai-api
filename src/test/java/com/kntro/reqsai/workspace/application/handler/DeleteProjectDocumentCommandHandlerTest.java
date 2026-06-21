package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.DeleteProjectDocumentCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectDocumentRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.DocumentStatus;
import com.kntro.reqsai.workspace.domain.model.DocumentType;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import com.kntro.reqsai.workspace.mothers.ProjectMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Delete Project Document")
@ExtendWith(MockitoExtension.class)
class DeleteProjectDocumentCommandHandlerTest {

    @Mock private OrganizationRepository organizations;
    @Mock private ProjectRepository projects;
    @Mock private ProjectDocumentRepository documents;
    @InjectMocks private DeleteProjectDocumentCommandHandler handler;

    @Test
    @DisplayName("should delete project document successfully")
    void should_delete_project_document_successfully() {
        Organization organization = OrganizationMother.active().build();
        UUID orgId = organization.getId();
        UUID projectId = UUID.randomUUID();
        ProjectDocument document = new ProjectDocument(projectId, "Business Rules v1", DocumentType.BUSINESS_RULES);
        DeleteProjectDocumentCommand command = new DeleteProjectDocumentCommand(orgId, projectId, document.getId(), UUID.randomUUID());

        when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(ProjectMother.standard().withOrganizationId(orgId).build()));
        when(documents.findByIdAndProjectIdAndStatus(document.getId(), projectId, DocumentStatus.ACTIVE))
                .thenReturn(Optional.of(document));

        handler.handle(command);

        verify(documents).delete(document);
    }

    @Test
    @DisplayName("should fail if project document does not exist")
    void should_fail_if_project_document_does_not_exist() {
        Organization organization = OrganizationMother.active().build();
        UUID orgId = organization.getId();
        UUID projectId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        DeleteProjectDocumentCommand command = new DeleteProjectDocumentCommand(orgId, projectId, documentId, UUID.randomUUID());

        when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(ProjectMother.standard().withOrganizationId(orgId).build()));
        when(documents.findByIdAndProjectIdAndStatus(documentId, projectId, DocumentStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(DomainException.class);
    }
}
