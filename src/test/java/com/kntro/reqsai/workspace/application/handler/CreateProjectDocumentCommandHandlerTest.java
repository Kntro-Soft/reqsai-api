package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.CreateProjectDocumentCommand;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectDocumentRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.DocumentStatus;
import com.kntro.reqsai.workspace.domain.model.DocumentType;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectDocument;
import com.kntro.reqsai.workspace.domain.model.ProjectStatus;
import com.kntro.reqsai.workspace.domain.valueobjects.PlanLimits;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import com.kntro.reqsai.workspace.mothers.ProjectMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

@DisplayName("Application: Create Project Document")
@ExtendWith(MockitoExtension.class)
class CreateProjectDocumentCommandHandlerTest {

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private ProjectRepository projects;
    @Mock
    private ProjectDocumentRepository documents;
    @InjectMocks
    private CreateProjectDocumentCommandHandler handler;

    @Nested
    @DisplayName("Successful creation")
    class SuccessfulCreation {

        @Test
        @DisplayName("should create project document successfully and persist it")
        void should_create_project_document_successfully() {
            Organization organization = OrganizationMother.active().withPlanLimits(PlanLimits.free()).build();
            UUID orgId = organization.getId();
            UUID projectId = UUID.randomUUID();
            Project project = ProjectMother.standard().withOrganizationId(orgId).build();
            CreateProjectDocumentCommand command = new CreateProjectDocumentCommand(
                    orgId, projectId, "Business Rules v1", DocumentType.BUSINESS_RULES, UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                    .thenReturn(Optional.of(project));
            when(documents.existsByProjectIdAndNameAndStatus(projectId, "Business Rules v1", DocumentStatus.ACTIVE))
                    .thenReturn(false);
            when(documents.countByProjectIdAndStatus(projectId, DocumentStatus.ACTIVE)).thenReturn(0);
            when(documents.save(any(ProjectDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProjectDocument document = handler.handle(command);

            assertThat(document.getProjectId()).isEqualTo(projectId);
            assertThat(document.getName()).isEqualTo("Business Rules v1");
            assertThat(document.getDocumentType()).isEqualTo(DocumentType.BUSINESS_RULES);
            assertThat(document.getStatus()).isEqualTo(DocumentStatus.ACTIVE);
            verify(documents).save(any(ProjectDocument.class));
        }
    }

    @Nested
    @DisplayName("Validation failures")
    class ValidationFailures {

        @Test
        @DisplayName("should fail if organization does not exist")
        void should_fail_if_organization_does_not_exist() {
            UUID orgId = UUID.randomUUID();
            CreateProjectDocumentCommand command = new CreateProjectDocumentCommand(
                    orgId, UUID.randomUUID(), "Business Rules v1", DocumentType.BUSINESS_RULES, UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(documents, never()).save(any());
        }

        @Test
        @DisplayName("should fail if project does not exist or does not belong to organization")
        void should_fail_if_project_does_not_exist() {
            Organization organization = OrganizationMother.active().build();
            UUID orgId = organization.getId();
            UUID projectId = UUID.randomUUID();
            CreateProjectDocumentCommand command = new CreateProjectDocumentCommand(
                    orgId, projectId, "Business Rules v1", DocumentType.BUSINESS_RULES, UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(documents, never()).save(any());
        }

        @Test
        @DisplayName("should fail if document name already exists in project")
        void should_fail_if_document_name_already_exists() {
            Organization organization = OrganizationMother.active().build();
            UUID orgId = organization.getId();
            UUID projectId = UUID.randomUUID();
            CreateProjectDocumentCommand command = new CreateProjectDocumentCommand(
                    orgId, projectId, "Business Rules v1", DocumentType.BUSINESS_RULES, UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                    .thenReturn(Optional.of(ProjectMother.standard().withOrganizationId(orgId).build()));
            when(documents.existsByProjectIdAndNameAndStatus(projectId, "Business Rules v1", DocumentStatus.ACTIVE))
                    .thenReturn(true);

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(documents, never()).save(any());
        }

        @Test
        @DisplayName("should fail if project document plan limit is exceeded")
        void should_fail_if_project_document_plan_limit_is_exceeded() {
            Organization organization = OrganizationMother.active()
                    .withPlanLimits(new PlanLimits(3, 1, 1, 100_000L, 50))
                    .build();
            UUID orgId = organization.getId();
            UUID projectId = UUID.randomUUID();
            CreateProjectDocumentCommand command = new CreateProjectDocumentCommand(
                    orgId, projectId, "Business Rules v1", DocumentType.BUSINESS_RULES, UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                    .thenReturn(Optional.of(ProjectMother.standard().withOrganizationId(orgId).build()));
            when(documents.existsByProjectIdAndNameAndStatus(projectId, "Business Rules v1", DocumentStatus.ACTIVE))
                    .thenReturn(false);
            when(documents.countByProjectIdAndStatus(projectId, DocumentStatus.ACTIVE)).thenReturn(1);

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(documents, never()).save(any());
        }
    }
}
