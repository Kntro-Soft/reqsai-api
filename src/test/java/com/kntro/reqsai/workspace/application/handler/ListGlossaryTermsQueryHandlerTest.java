package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.ListGlossaryTermsQuery;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DisplayName("Application: List Glossary Terms")
@ExtendWith(MockitoExtension.class)
class ListGlossaryTermsQueryHandlerTest {

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private ProjectRepository projects;
    @Mock
    private GlossaryRepository glossaries;
    @InjectMocks
    private ListGlossaryTermsQueryHandler handler;

    @Test
    @DisplayName("should list glossary terms for an active project")
    void should_list_glossary_terms_for_an_active_project() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Organization organization = OrganizationMother.active().build();
        Project project = ProjectMother.standard().withOrganizationId(orgId).build();
        Glossary glossary = new Glossary(projectId);
        GlossaryTerm lead = glossary.addTerm("Lead", "Potential customer", UUID.randomUUID());

        when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(project));
        when(glossaries.findByProjectId(projectId)).thenReturn(Optional.of(glossary));

        List<GlossaryTerm> result = handler.handle(new ListGlossaryTermsQuery(orgId, projectId, UUID.randomUUID()));

        assertThat(result).containsExactly(lead);
    }

    @Test
    @DisplayName("should fail when glossary does not exist")
    void should_fail_when_glossary_does_not_exist() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Organization organization = OrganizationMother.active().build();
        Project project = ProjectMother.standard().withOrganizationId(orgId).build();

        when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(project));
        when(glossaries.findByProjectId(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ListGlossaryTermsQuery(orgId, projectId, UUID.randomUUID())))
                .isInstanceOf(DomainException.class);
    }
}
