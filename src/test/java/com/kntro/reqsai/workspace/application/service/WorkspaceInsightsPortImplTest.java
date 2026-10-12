package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.WorkspaceInsightsRepository;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.insights.MemberHeadcount;
import com.kntro.reqsai.workspace.insights.ProjectRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: workspace insights port")
@ExtendWith(MockitoExtension.class)
class WorkspaceInsightsPortImplTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private ProjectAccessService projectAccess;
    @Mock
    private OrganizationAdminAccessService orgAccess;
    @Mock
    private WorkspaceInsightsRepository insights;
    @Mock
    private Organization organization;

    private WorkspaceInsightsPortImpl port() {
        return new WorkspaceInsightsPortImpl(organizations, projectAccess, orgAccess, insights);
    }

    @Test
    @DisplayName("an owner or admin sees every active project")
    void ownerSeesAllProjects() {
        List<ProjectRef> all = List.of(new ProjectRef(UUID.randomUUID(), "Alpha", false));
        when(organizations.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(projectAccess.accessibleProjectIds(organization, CALLER)).thenReturn(Optional.empty());
        when(insights.activeProjects(ORG_ID)).thenReturn(all);

        assertThat(port().accessibleProjects(ORG_ID, CALLER)).isEqualTo(all);
    }

    @Test
    @DisplayName("a regular member sees only the projects they reach")
    void memberSeesAssignedProjects() {
        Set<UUID> reachable = Set.of(UUID.randomUUID());
        List<ProjectRef> mine = List.of(new ProjectRef(reachable.iterator().next(), "Mine", false));
        when(organizations.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(projectAccess.accessibleProjectIds(organization, CALLER)).thenReturn(Optional.of(reachable));
        when(insights.activeProjects(ORG_ID, reachable)).thenReturn(mine);

        assertThat(port().accessibleProjects(ORG_ID, CALLER)).isEqualTo(mine);
        verify(insights, never()).activeProjects(ORG_ID);
    }

    @Test
    @DisplayName("headcount adds the synthesized owner to the member rows and counts invitations apart")
    void headcount() {
        Organization org = mock(Organization.class);
        when(org.getOwnerId()).thenReturn(OWNER_ID);
        when(organizations.findById(ORG_ID)).thenReturn(Optional.of(org));
        when(orgAccess.isOwnerOrAdmin(org, CALLER)).thenReturn(true);
        when(insights.memberCounts(ORG_ID, OWNER_ID)).thenReturn(Map.of(
                "ADMIN", Map.of("ACTIVE", 1L),
                "MEMBER", Map.of("ACTIVE", 4L, "PENDING", 2L)));

        MemberHeadcount headcount = port().memberHeadcount(ORG_ID, CALLER).orElseThrow();

        assertThat(headcount.active()).isEqualTo(6);
        assertThat(headcount.pending()).isEqualTo(2);
        assertThat(headcount.activeByRole()).containsEntry("OWNER", 1L).containsEntry("ADMIN", 1L)
                .containsEntry("MEMBER", 4L);
    }

    @Test
    @DisplayName("a regular member does not get the roster")
    void headcountIsForAdmins() {
        when(organizations.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(orgAccess.isOwnerOrAdmin(organization, CALLER)).thenReturn(false);

        assertThat(port().memberHeadcount(ORG_ID, CALLER)).isEmpty();
        verify(insights, never()).memberCounts(ORG_ID, OWNER_ID);
    }
}
