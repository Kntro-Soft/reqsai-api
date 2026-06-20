package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.UpdateProjectDocumentCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectDocumentRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.DocumentStatus;
import com.kntro.reqsai.workspace.domain.model.DocumentType;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import com.kntro.reqsai.workspace.domain.valueobjects.PlanLimits;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Update Project Document")
@ExtendWith(MockitoExtension.class)
class UpdateProjectDocumentCommandHandlerTest {

    @Mock private OrganizationRepository organizations;
    @Mock private ProjectRepository projects;
    @Mock private ProjectDocumentRepository documents;
    @InjectMocks private UpdateProjectDocumentCommandHandler handler;

    @Test
    @DisplayName("should update project document successfully")
    void should_update_project_document_successfully() {
        Organization organization = OrganizationMother.active().withPlanLimits(PlanLimits.free()).build();
        UUID orgId = organization.getId();
        UUID projectId = UUID.randomUUID();
        ProjectDocument document = new ProjectDocument(projectId, "Business Rules v1", DocumentType.BUSINESS_RULES);
        UpdateProjectDocumentCommand command = new UpdateProjectDocumentCommand(
                orgId, projectId, document.getId(), "Technical Spec v1", DocumentType.TECHNICAL_SPEC, UUID.randomUUID());

        when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(ProjectMother.standard().withOrganizationId(orgId).build()));
        when(documents.findByIdAndProjectIdAndStatus(document.getId(), projectId, DocumentStatus.ACTIVE))
                .thenReturn(Optional.of(document));
        when(documents.existsByProjectIdAndNameAndIdNotAndStatus(projectId, "Technical Spec v1", document.getId(), DocumentStatus.ACTIVE))
                .thenReturn(false);
        when(documents.countByProjectIdAndStatus(projectId, DocumentStatus.ACTIVE)).thenReturn(1);
        when(documents.save(any(ProjectDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectDocument updated = handler.handle(command);

        assertThat(updated.getName()).isEqualTo("Technical Spec v1");
        assertThat(updated.getDocumentType()).isEqualTo(DocumentType.TECHNICAL_SPEC);
        verify(documents).save(document);
    }

    @Test
    @DisplayName("should fail if update causes duplicate document name")
    void should_fail_if_update_causes_duplicate_document_name() {
        Organization organization = OrganizationMother.active().build();
        UUID orgId = organization.getId();
        UUID projectId = UUID.randomUUID();
        ProjectDocument document = new ProjectDocument(projectId, "Business Rules v1", DocumentType.BUSINESS_RULES);
        UpdateProjectDocumentCommand command = new UpdateProjectDocumentCommand(
                orgId, projectId, document.getId(), " technical spec v1 ", DocumentType.TECHNICAL_SPEC, UUID.randomUUID());

        when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(ProjectMother.standard().withOrganizationId(orgId).build()));
        when(documents.findByIdAndProjectIdAndStatus(document.getId(), projectId, DocumentStatus.ACTIVE))
                .thenReturn(Optional.of(document));
        when(documents.existsByProjectIdAndNameAndIdNotAndStatus(projectId, "technical spec v1", document.getId(), DocumentStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(DomainException.class);
        verify(documents, never()).save(any());
    }
}
