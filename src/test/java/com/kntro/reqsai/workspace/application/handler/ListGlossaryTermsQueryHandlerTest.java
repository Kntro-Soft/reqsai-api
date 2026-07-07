package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.interfaces.pagination.PageCriteria;
import com.kntro.reqsai.shared.interfaces.pagination.PageRequestFactory;
import com.kntro.reqsai.shared.interfaces.pagination.PaginationProperties;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private final PageRequestFactory pageRequestFactory = new PageRequestFactory(new PaginationProperties(20, 100));

    private ListGlossaryTermsQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ListGlossaryTermsQueryHandler(organizations, projects, glossaries, pageRequestFactory);
    }

    @Test
    @DisplayName("should list a paginated page of glossary terms for an active project")
    void should_list_glossary_terms_for_an_active_project() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Organization organization = OrganizationMother.active().build();
        Project project = ProjectMother.standard().withOrganizationId(orgId).build();
        Glossary glossary = new Glossary(projectId);
        GlossaryTerm lead = glossary.addTerm("Lead", "Potential customer", UUID.randomUUID());
        Page<GlossaryTerm> page = new PageImpl<>(List.of(lead), PageRequest.of(0, 20), 1);

        when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(project));
        when(glossaries.findByProjectId(projectId)).thenReturn(Optional.of(glossary));
        when(glossaries.findTermsByProjectId(eq(projectId), eq("lead"), any(Pageable.class))).thenReturn(page);

        Page<GlossaryTerm> result = handler.handle(new ListGlossaryTermsQuery(
                orgId, projectId, UUID.randomUUID(), PageCriteria.of(0, 20, null, null), "lead"));

        assertThat(result.getContent()).containsExactly(lead);
    }

    @Test
    @DisplayName("should pass the raw search term and pageable through to the repository")
    void should_pass_search_and_pageable_to_repository() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Organization organization = OrganizationMother.active().build();
        Project project = ProjectMother.standard().withOrganizationId(orgId).build();
        Glossary glossary = new Glossary(projectId);

        when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(projectId, orgId, ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(project));
        when(glossaries.findByProjectId(projectId)).thenReturn(Optional.of(glossary));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(glossaries.findTermsByProjectId(eq(projectId), eq(null), pageable.capture()))
                .thenReturn(new PageImpl<>(List.of()));

        handler.handle(new ListGlossaryTermsQuery(
                orgId, projectId, UUID.randomUUID(), PageCriteria.of(2, 5, null, null), null));

        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
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

        assertThatThrownBy(() -> handler.handle(new ListGlossaryTermsQuery(
                orgId, projectId, UUID.randomUUID(), PageCriteria.of(0, 20, null, null), null)))
                .isInstanceOf(DomainException.class);
    }
}
