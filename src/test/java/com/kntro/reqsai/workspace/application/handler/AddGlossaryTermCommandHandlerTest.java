package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.AddGlossaryTermCommand;
import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Glossary;
import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Project;
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

@DisplayName("Application: Add Glossary Term")
@ExtendWith(MockitoExtension.class)
class AddGlossaryTermCommandHandlerTest {

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private ProjectRepository projects;
    @Mock
    private GlossaryRepository glossaries;
    @InjectMocks
    private AddGlossaryTermCommandHandler handler;

    @Nested
    @DisplayName("Successful creation")
    class SuccessfulCreation {

        @Test
        @DisplayName("should create glossary term when organization project and glossary exist")
        void should_create_glossary_term_when_organization_project_and_glossary_exist() {
            UUID orgId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            UUID requestedBy = UUID.randomUUID();
            Organization organization = OrganizationMother.active()
                    .withPlanLimits(new PlanLimits(3, 1, 10, 100_000L, 50))
                    .build();
            Project project = ProjectMother.standard().withOrganizationId(orgId).build();
            Glossary glossary = new Glossary(projectId);
            AddGlossaryTermCommand command = new AddGlossaryTermCommand(orgId, projectId, "Lead", "Potential customer", requestedBy);

            when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                    .thenReturn(Optional.of(project));
            when(glossaries.findByProjectId(projectId)).thenReturn(Optional.of(glossary));
            when(glossaries.save(any(Glossary.class))).thenAnswer(invocation -> invocation.getArgument(0));

            GlossaryTerm result = handler.handle(command);

            assertThat(result.getTerm()).isEqualTo("Lead");
            assertThat(result.getDefinition()).isEqualTo("Potential customer");
            verify(glossaries).save(glossary);
        }
    }

    @Nested
    @DisplayName("Validation failures")
    class ValidationFailures {

        @Test
        @DisplayName("should fail if organization does not exist")
        void should_fail_if_organization_does_not_exist() {
            UUID orgId = UUID.randomUUID();
            AddGlossaryTermCommand command = new AddGlossaryTermCommand(orgId, UUID.randomUUID(), "Lead", "Potential customer", UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(glossaries, never()).save(any());
        }

        @Test
        @DisplayName("should fail if project is not active in organization")
        void should_fail_if_project_is_not_active_in_organization() {
            UUID orgId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            Organization organization = OrganizationMother.active().build();
            AddGlossaryTermCommand command = new AddGlossaryTermCommand(orgId, projectId, "Lead", "Potential customer", UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(glossaries, never()).save(any());
        }

        @Test
        @DisplayName("should fail if glossary does not exist for project")
        void should_fail_if_glossary_does_not_exist_for_project() {
            UUID orgId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            Organization organization = OrganizationMother.active().build();
            Project project = ProjectMother.standard().withOrganizationId(orgId).build();
            AddGlossaryTermCommand command = new AddGlossaryTermCommand(orgId, projectId, "Lead", "Potential customer", UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                    .thenReturn(Optional.of(project));
            when(glossaries.findByProjectId(projectId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(glossaries, never()).save(any());
        }

        @Test
        @DisplayName("should fail if glossary term limit is exceeded")
        void should_fail_if_glossary_term_limit_is_exceeded() {
            UUID orgId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            UUID requestedBy = UUID.randomUUID();
            Organization organization = OrganizationMother.active()
                    .withPlanLimits(new PlanLimits(3, 1, 10, 100_000L, 1))
                    .build();
            Project project = ProjectMother.standard().withOrganizationId(orgId).build();
            Glossary glossary = new Glossary(projectId);
            glossary.addTerm("Lead", "Potential customer", requestedBy);
            AddGlossaryTermCommand command = new AddGlossaryTermCommand(orgId, projectId, "Opportunity", "Qualified lead", requestedBy);

            when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                    .thenReturn(Optional.of(project));
            when(glossaries.findByProjectId(projectId)).thenReturn(Optional.of(glossary));

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(glossaries, never()).save(any());
        }

        @Test
        @DisplayName("should fail if term already exists")
        void should_fail_if_term_already_exists() {
            UUID orgId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            UUID requestedBy = UUID.randomUUID();
            Organization organization = OrganizationMother.active().build();
            Project project = ProjectMother.standard().withOrganizationId(orgId).build();
            Glossary glossary = new Glossary(projectId);
            glossary.addTerm("Lead", "Potential customer", requestedBy);
            AddGlossaryTermCommand command = new AddGlossaryTermCommand(orgId, projectId, " lead ", "Another definition", requestedBy);

            when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
            when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                    .thenReturn(Optional.of(project));
            when(glossaries.findByProjectId(projectId)).thenReturn(Optional.of(glossary));

            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(DomainException.class);
            verify(glossaries, never()).save(any());
        }
    }
}
