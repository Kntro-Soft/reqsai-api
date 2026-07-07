package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.UpdateGlossaryTermCommand;
import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Glossary;
import com.kntro.reqsai.workspace.domain.model.GlossaryTerm;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Project;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Update Glossary Term")
@ExtendWith(MockitoExtension.class)
class UpdateGlossaryTermCommandHandlerTest {

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private ProjectRepository projects;
    @Mock
    private GlossaryRepository glossaries;
    @InjectMocks
    private UpdateGlossaryTermCommandHandler handler;

    @Test
    @DisplayName("should update glossary term successfully")
    void should_update_glossary_term_successfully() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        Organization organization = OrganizationMother.active().build();
        Project project = ProjectMother.standard().withOrganizationId(orgId).build();
        Glossary glossary = new Glossary(projectId);
        GlossaryTerm lead = glossary.addTerm("Lead", "Potential customer", requestedBy);

        when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE)).thenReturn(Optional.of(project));
        when(glossaries.findByProjectId(projectId)).thenReturn(Optional.of(glossary));
        when(glossaries.save(any(Glossary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GlossaryTerm result = handler.handle(new UpdateGlossaryTermCommand(orgId, projectId, lead.getId(), "Opportunity", "Qualified lead", requestedBy));

        assertThat(result.getTerm()).isEqualTo("Opportunity");
        assertThat(result.getDefinition()).isEqualTo("Qualified lead");
        verify(glossaries).save(glossary);
    }

    @Test
    @DisplayName("should fail if update collides with another term")
    void should_fail_if_update_collides_with_another_term() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        Organization organization = OrganizationMother.active().build();
        Project project = ProjectMother.standard().withOrganizationId(orgId).build();
        Glossary glossary = new Glossary(projectId);
        glossary.addTerm("Lead", "Potential customer", requestedBy);
        GlossaryTerm opportunity = glossary.addTerm("Opportunity", "Qualified lead", requestedBy);

        when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE)).thenReturn(Optional.of(project));
        when(glossaries.findByProjectId(projectId)).thenReturn(Optional.of(glossary));

        assertThatThrownBy(() -> handler.handle(new UpdateGlossaryTermCommand(orgId, projectId, opportunity.getId(), " lead ", "Collision", requestedBy)))
                .isInstanceOf(DomainException.class);
        verify(glossaries, never()).save(any());
    }
}
