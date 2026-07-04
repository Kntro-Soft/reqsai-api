package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.workspace.api.ProjectSnapshot;
import com.kntro.reqsai.workspace.application.port.GlossaryRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.ProjectRepository;
import com.kntro.reqsai.workspace.domain.model.Glossary;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.domain.model.Permission;
import com.kntro.reqsai.workspace.domain.model.Project;
import com.kntro.reqsai.workspace.mothers.GlossaryBuilder;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import com.kntro.reqsai.workspace.mothers.ProjectMother;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayName("Application: WorkspaceModuleApi")
@ExtendWith(MockitoExtension.class)
class WorkspaceModuleApiImplTest {

    @Mock
    private ProjectRepository projects;

    @Mock
    private GlossaryRepository glossaries;

    @Mock
    private OrganizationRepository organizations;

    @Mock
    private ProjectPermissionService projectPermissions;

    @InjectMocks
    private WorkspaceModuleApiImpl api;

    @Nested
    @DisplayName("findProjectSnapshot")
    class FindProjectSnapshot {

        @Test
        @DisplayName("should return snapshot with constraints and glossary terms")
        void should_return_full_snapshot() {
            Project project = ProjectMother.standard()
                    .withName("Payment Platform")
                    .withDescription("Handles payments.")
                    .build();
            project.addConstraint("Must comply with PCI-DSS.");
            project.addConstraint("Max response time 200ms.");

            Glossary glossary = GlossaryBuilder.aGlossary().withProjectId(project.getId()).build();
            UUID anyUser = UUID.randomUUID();
            glossary.addTerm("Sprint", "Fixed-length iteration.", anyUser);
            glossary.addTerm("Backlog", "Prioritized list of features.", anyUser);

            when(projects.findById(project.getId())).thenReturn(Optional.of(project));
            when(glossaries.findByProjectId(project.getId())).thenReturn(Optional.of(glossary));

            Optional<ProjectSnapshot> result = api.findProjectSnapshot(project.getId());

            assertThat(result).isPresent();
            ProjectSnapshot snap = result.get();
            assertThat(snap.projectId()).isEqualTo(project.getId());
            assertThat(snap.name()).isEqualTo("Payment Platform");
            assertThat(snap.description()).isEqualTo("Handles payments.");
            assertThat(snap.constraints()).containsExactlyInAnyOrder(
                    "Must comply with PCI-DSS.", "Max response time 200ms.");
            assertThat(snap.glossaryTerms()).hasSize(2);
            assertThat(snap.glossaryTerms()).extracting("term")
                    .containsExactlyInAnyOrder("Sprint", "Backlog");
        }

        @Test
        @DisplayName("should return snapshot with empty glossary when none provisioned")
        void should_return_snapshot_with_empty_glossary() {
            Project project = ProjectMother.standard().build();
            when(projects.findById(project.getId())).thenReturn(Optional.of(project));
            when(glossaries.findByProjectId(project.getId())).thenReturn(Optional.empty());

            Optional<ProjectSnapshot> result = api.findProjectSnapshot(project.getId());

            assertThat(result).isPresent();
            assertThat(result.get().glossaryTerms()).isEmpty();
            assertThat(result.get().constraints()).isEmpty();
        }

        @Test
        @DisplayName("should return empty when project does not exist")
        void should_return_empty_when_project_not_found() {
            UUID unknownId = UUID.randomUUID();
            when(projects.findById(unknownId)).thenReturn(Optional.empty());

            Optional<ProjectSnapshot> result = api.findProjectSnapshot(unknownId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("callerHasProjectPermission (tenant-resolved)")
    class CallerHasProjectPermission {

        private final UUID projectId = UUID.randomUUID();
        private final UUID userId = UUID.randomUUID();

        @AfterEach
        void clearTenant() {
            TenantContext.clear();
        }

        @Test
        @DisplayName("should delegate to ProjectPermissionService with the organization of the bound tenant")
        void should_delegate_with_current_tenant_org() {
            Organization org = OrganizationMother.active().build();
            TenantContext.setCurrentTenant(org.getId().toString());
            when(organizations.findById(org.getId())).thenReturn(Optional.of(org));
            when(projectPermissions.hasPermission(org, projectId, userId, Permission.SESSION_RUN)).thenReturn(true);

            assertThat(api.callerHasProjectPermission(projectId, userId, "SESSION_RUN")).isTrue();
        }

        @Test
        @DisplayName("should deny when the permission is not granted")
        void should_deny_without_permission() {
            Organization org = OrganizationMother.active().build();
            TenantContext.setCurrentTenant(org.getId().toString());
            when(organizations.findById(org.getId())).thenReturn(Optional.of(org));
            when(projectPermissions.hasPermission(eq(org), eq(projectId), eq(userId), any())).thenReturn(false);

            assertThat(api.callerHasProjectPermission(projectId, userId, "SESSION_READ")).isFalse();
        }

        @Test
        @DisplayName("should deny when no tenant is bound to the thread")
        void should_deny_without_tenant() {
            assertThat(api.callerHasProjectPermission(projectId, userId, "SESSION_READ")).isFalse();
        }

        @Test
        @DisplayName("should deny when the bound tenant is not a UUID")
        void should_deny_on_malformed_tenant() {
            TenantContext.setCurrentTenant("not-a-uuid");

            assertThat(api.callerHasProjectPermission(projectId, userId, "SESSION_READ")).isFalse();
        }

        @Test
        @DisplayName("should deny when the tenant organization does not exist")
        void should_deny_on_unknown_org() {
            UUID orgId = UUID.randomUUID();
            TenantContext.setCurrentTenant(orgId.toString());
            when(organizations.findById(orgId)).thenReturn(Optional.empty());

            assertThat(api.callerHasProjectPermission(projectId, userId, "SESSION_DECIDE")).isFalse();
        }

        @Test
        @DisplayName("should reject unknown permission names")
        void should_reject_unknown_permission() {
            assertThatThrownBy(() -> api.callerHasProjectPermission(projectId, userId, "NOT_A_PERMISSION"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
