package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.interfaces.pagination.PageCriteria;
import com.kntro.reqsai.shared.interfaces.pagination.PageRequestFactory;
import com.kntro.reqsai.shared.interfaces.pagination.PaginationProperties;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectConstraintRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.application.query.ListProjectConstraintsQuery;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.domain.model.ProjectConstraint;
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

@DisplayName("Application: List Project Constraints")
@ExtendWith(MockitoExtension.class)
class ListProjectConstraintsQueryHandlerTest {

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private ProjectRepository projects;
    @Mock
    private ProjectConstraintRepository constraints;

    private final PageRequestFactory pageRequestFactory = new PageRequestFactory(new PaginationProperties(20, 100));

    private ListProjectConstraintsQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ListProjectConstraintsQueryHandler(organizations, projects, constraints, pageRequestFactory);
    }

    @Test
    @DisplayName("should list a paginated page of project constraints")
    void should_list_project_constraints_successfully() {
        Organization organization = OrganizationMother.active().build();
        Project project = ProjectMother.standard().withOrganizationId(organization.getId()).build();
        ProjectConstraint sap = project.addConstraint("Must integrate with SAP");
        ProjectConstraint azure = project.addConstraint("Must use Azure AD");
        Page<ProjectConstraint> page = new PageImpl<>(List.of(sap, azure), PageRequest.of(0, 20), 2);

        when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(project.getId(), organization.getId(), ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(project));
        when(constraints.findByProjectId(eq(project.getId()), eq(null), any(Pageable.class))).thenReturn(page);

        Page<ProjectConstraint> result = handler.handle(new ListProjectConstraintsQuery(
                organization.getId(), project.getId(), UUID.randomUUID(), PageCriteria.of(0, 20, null, null), null));

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("should pass the search term and pageable through to the repository")
    void should_pass_search_and_pageable_to_repository() {
        Organization organization = OrganizationMother.active().build();
        Project project = ProjectMother.standard().withOrganizationId(organization.getId()).build();

        when(organizations.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(projects.findByIdAndOrganizationIdAndStatus(project.getId(), organization.getId(), ProjectStatus.ACTIVE))
                .thenReturn(Optional.of(project));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(constraints.findByProjectId(eq(project.getId()), eq("sap"), pageable.capture()))
                .thenReturn(new PageImpl<>(List.of()));

        handler.handle(new ListProjectConstraintsQuery(
                organization.getId(), project.getId(), UUID.randomUUID(), PageCriteria.of(1, 5, null, null), "sap"));

        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("should fail when organization does not exist")
    void should_fail_when_organization_does_not_exist() {
        UUID orgId = UUID.randomUUID();
        when(organizations.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ListProjectConstraintsQuery(
                orgId, UUID.randomUUID(), UUID.randomUUID(), PageCriteria.of(0, 20, null, null), null)))
                .isInstanceOf(com.kntro.reqsai.shared.domain.exception.DomainException.class);
    }
}
