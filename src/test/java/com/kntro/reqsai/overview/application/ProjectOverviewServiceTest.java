package com.kntro.reqsai.overview.application;

import com.kntro.reqsai.codebase.api.CodeFootprint;
import com.kntro.reqsai.codebase.api.CodebaseModuleApi;
import com.kntro.reqsai.discovery.insights.BacklogInsights;
import com.kntro.reqsai.discovery.insights.DiscoveryInsightsPort;
import com.kntro.reqsai.discovery.insights.SessionInsights;
import com.kntro.reqsai.discovery.insights.WeeklyCount;
import com.kntro.reqsai.overview.application.result.ProjectOverview;
import com.kntro.reqsai.overview.domain.exception.OverviewError;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import com.kntro.reqsai.workspace.insights.ProjectKnowledge;
import com.kntro.reqsai.workspace.insights.WorkspaceInsightsPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Application: project overview")
@ExtendWith(MockitoExtension.class)
class ProjectOverviewServiceTest {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-07T15:30:00Z");
    private static final Instant SINCE = Instant.parse("2026-07-20T00:00:00Z");

    @Mock
    private WorkspaceModuleApi workspace;
    @Mock
    private WorkspaceInsightsPort workspaceInsights;
    @Mock
    private DiscoveryInsightsPort discovery;
    @Mock
    private CodebaseModuleApi code;

    private ProjectOverviewService service() {
        return new ProjectOverviewService(workspace, workspaceInsights, discovery, code,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void permissions(String... granted) {
        Set<String> allowed = Set.of(granted);
        lenient().when(workspace.callerHasProjectPermission(eq(PROJECT), eq(CALLER), any()))
                .thenAnswer(call -> allowed.contains(call.<String>getArgument(2)));
    }

    private static BacklogInsights backlog() {
        return new BacklogInsights(5, Map.of("DRAFT", 5L), Map.of("LOW", 5L), 5, 21, 8, 1, 2, 3, 4,
                List.of(new WeeklyCount(LocalDate.parse("2026-10-05"), 5)), List.of());
    }

    private static SessionInsights sessions() {
        return new SessionInsights(2, Map.of("COMPLETED", 2L),
                List.of(new WeeklyCount(LocalDate.parse("2026-09-28"), 2)), 6, 2, 1, 3, List.of());
    }

    @Test
    @DisplayName("an owner (every permission) gets every section, zero-padded to 12 weeks")
    void everySection() {
        permissions("STORY_READ", "SESSION_READ", "GLOSSARY_READ", "CONSTRAINT_READ", "DOCUMENT_READ", "MEMBER_READ",
                "INTEGRATION_READ");
        when(workspaceInsights.projectKnowledge(PROJECT)).thenReturn(Optional.of(new ProjectKnowledge(4, 3, 2, 1)));
        when(discovery.backlog(Set.of(PROJECT), SINCE, 5)).thenReturn(backlog());
        when(discovery.sessions(Set.of(PROJECT), SINCE, 5)).thenReturn(sessions());
        CodeFootprint footprint = new CodeFootprint(1, 1, 0, 7, 90, NOW);
        when(code.footprint(Set.of(PROJECT))).thenReturn(footprint);

        ProjectOverview overview = service().overview(PROJECT, CALLER);

        assertThat(overview.generatedAt()).isEqualTo(NOW);
        assertThat(overview.weeks()).isEqualTo(12);
        assertThat(overview.backlog().total()).isEqualTo(5);
        assertThat(overview.backlog().storyPoints().total()).isEqualTo(21);
        assertThat(overview.backlog().coverage().withAcceptanceCriteria()).isEqualTo(2);
        assertThat(overview.backlog().createdByWeek()).hasSize(12);
        assertThat(overview.backlog().createdByWeek().getLast().count()).isEqualTo(5);
        assertThat(overview.discovery().assistance().acceptanceRate()).isEqualTo(0.75);
        assertThat(overview.discovery().createdByWeek()).hasSize(12);
        assertThat(overview.knowledge().glossaryTerms()).isEqualTo(4);
        assertThat(overview.knowledge().assignedMembers()).isEqualTo(1);
        assertThat(overview.code()).isEqualTo(footprint);
    }

    @Test
    @DisplayName("sections the caller cannot read are left out and never queried")
    void onlyPermittedSections() {
        permissions("STORY_READ", "GLOSSARY_READ");
        when(workspaceInsights.projectKnowledge(PROJECT)).thenReturn(Optional.of(new ProjectKnowledge(4, 3, 2, 1)));
        when(discovery.backlog(any(), any(), anyInt())).thenReturn(backlog());

        ProjectOverview overview = service().overview(PROJECT, CALLER);

        assertThat(overview.backlog()).isNotNull();
        assertThat(overview.discovery()).isNull();
        assertThat(overview.code()).isNull();
        assertThat(overview.knowledge().glossaryTerms()).isEqualTo(4);
        assertThat(overview.knowledge().constraints()).isNull();
        assertThat(overview.knowledge().documents()).isNull();
        assertThat(overview.knowledge().assignedMembers()).isNull();
        verify(discovery, never()).sessions(any(), any(), anyInt());
        verifyNoInteractions(code);
    }

    @Test
    @DisplayName("no knowledge permission at all drops the knowledge section")
    void noKnowledgeSection() {
        permissions("SESSION_READ");
        when(workspaceInsights.projectKnowledge(PROJECT)).thenReturn(Optional.of(new ProjectKnowledge(4, 3, 2, 1)));
        when(discovery.sessions(any(), any(), anyInt())).thenReturn(sessions());

        assertThat(service().overview(PROJECT, CALLER).knowledge()).isNull();
    }

    @Test
    @DisplayName("a caller who may read nothing of the project is denied before anything is counted")
    void denied() {
        permissions();

        assertThatThrownBy(() -> service().overview(PROJECT, CALLER))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(OverviewError.OVERVIEW_ACCESS_DENIED));
        verifyNoInteractions(workspaceInsights, discovery, code);
    }

    @Test
    @DisplayName("a project that does not exist is a 404")
    void notFound() {
        permissions("STORY_READ");
        when(workspaceInsights.projectKnowledge(PROJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().overview(PROJECT, CALLER))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.error()).isEqualTo(OverviewError.OVERVIEW_PROJECT_NOT_FOUND));
    }
}
