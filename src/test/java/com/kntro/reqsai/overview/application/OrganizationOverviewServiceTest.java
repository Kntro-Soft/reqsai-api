package com.kntro.reqsai.overview.application;

import com.kntro.reqsai.codebase.api.CodeFootprint;
import com.kntro.reqsai.codebase.api.CodebaseModuleApi;
import com.kntro.reqsai.discovery.insights.BacklogInsights;
import com.kntro.reqsai.discovery.insights.DiscoveryInsightsPort;
import com.kntro.reqsai.discovery.insights.ProjectActivity;
import com.kntro.reqsai.discovery.insights.SessionInsights;
import com.kntro.reqsai.overview.application.result.OrganizationOverview;
import com.kntro.reqsai.overview.application.result.ProjectActivitySummary;
import com.kntro.reqsai.overview.domain.exception.OverviewError;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.workspace.insights.MemberHeadcount;
import com.kntro.reqsai.workspace.insights.ProjectRef;
import com.kntro.reqsai.workspace.insights.WorkspaceInsightsPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Application: organization overview")
@ExtendWith(MockitoExtension.class)
class OrganizationOverviewServiceTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-07T15:30:00Z");

    @Mock
    private WorkspaceInsightsPort workspaceInsights;
    @Mock
    private DiscoveryInsightsPort discovery;
    @Mock
    private CodebaseModuleApi code;

    private OrganizationOverviewService service() {
        return new OrganizationOverviewService(workspaceInsights, discovery, code, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static BacklogInsights emptyBacklog() {
        return new BacklogInsights(0, Map.of(), Map.of(), 0, 0, 0, 0, 0, 0, 0, List.of(), List.of());
    }

    private static SessionInsights emptySessions() {
        return new SessionInsights(0, Map.of(), List.of(), 0, 0, 0, 0, List.of());
    }

    @Test
    @DisplayName("ranks the busiest projects first and ignores activity of projects the caller cannot see")
    void topProjects() {
        TenantContext.setCurrentTenant(ORG.toString());
        ProjectRef alpha = new ProjectRef(UUID.randomUUID(), "Alpha", false);
        ProjectRef beta = new ProjectRef(UUID.randomUUID(), "Beta", true);
        ProjectRef quiet = new ProjectRef(UUID.randomUUID(), "Quiet", false);
        UUID hidden = UUID.randomUUID();
        when(workspaceInsights.accessibleProjects(ORG, CALLER)).thenReturn(List.of(alpha, beta, quiet));
        when(workspaceInsights.memberHeadcount(ORG, CALLER)).thenReturn(
                Optional.of(new MemberHeadcount(3, 1, Map.of("OWNER", 1L, "ADMIN", 0L, "MEMBER", 2L))));
        List<UUID> ids = List.of(alpha.id(), beta.id(), quiet.id());
        when(discovery.backlog(eq(ids), any(), eq(0))).thenReturn(emptyBacklog());
        when(discovery.sessions(eq(ids), any(), eq(0))).thenReturn(emptySessions());
        Instant earlier = NOW.minusSeconds(3600);
        when(discovery.activityByProject(ids)).thenReturn(List.of(
                new ProjectActivity(alpha.id(), 1, 1, earlier),
                new ProjectActivity(beta.id(), 4, 3, NOW),
                new ProjectActivity(hidden, 99, 99, NOW)));
        when(code.footprint(ids)).thenReturn(CodeFootprint.none());

        OrganizationOverview overview = service().overview(ORG, CALLER);

        assertThat(overview.projects()).isEqualTo(3);
        assertThat(overview.members().active()).isEqualTo(3);
        assertThat(overview.weeks()).isEqualTo(12);
        assertThat(overview.backlog().createdByWeek()).hasSize(12);
        assertThat(overview.backlog().recent()).isEmpty();
        assertThat(overview.topProjects()).extracting(ProjectActivitySummary::name).containsExactly("Beta", "Alpha");
        assertThat(overview.topProjects().getFirst().demo()).isTrue();
    }

    @Test
    @DisplayName("a caller who reaches no project gets an empty overview without touching discovery counts or code")
    void noProjects() {
        TenantContext.setCurrentTenant(ORG.toString());
        when(workspaceInsights.accessibleProjects(ORG, CALLER)).thenReturn(List.of());
        when(workspaceInsights.memberHeadcount(ORG, CALLER)).thenReturn(Optional.empty());
        when(discovery.backlog(eq(List.of()), any(), anyInt())).thenReturn(emptyBacklog());
        when(discovery.sessions(eq(List.of()), any(), anyInt())).thenReturn(emptySessions());
        when(discovery.activityByProject(List.of())).thenReturn(List.of());

        OrganizationOverview overview = service().overview(ORG, CALLER);

        assertThat(overview.projects()).isZero();
        assertThat(overview.members()).isNull();
        assertThat(overview.topProjects()).isEmpty();
        assertThat(overview.code()).isEqualTo(CodeFootprint.none());
        verifyNoInteractions(code);
    }

    @Test
    @DisplayName("refuses an organization other than the tenant bound to the request")
    void tenantMismatch() {
        TenantContext.setCurrentTenant(UUID.randomUUID().toString());

        assertThatThrownBy(() -> service().overview(ORG, CALLER))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(OverviewError.OVERVIEW_ORGANIZATION_MISMATCH));
        verifyNoInteractions(workspaceInsights, discovery, code);
    }

    @Test
    @DisplayName("refuses when no tenant is bound")
    void noTenant() {
        assertThatThrownBy(() -> service().overview(ORG, CALLER)).isInstanceOf(DomainException.class);
    }
}
