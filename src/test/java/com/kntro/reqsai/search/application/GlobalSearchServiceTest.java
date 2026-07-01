package com.kntro.reqsai.search.application;

import com.kntro.reqsai.discovery.search.DiscoverySearchPort;
import com.kntro.reqsai.shared.application.search.ProjectScope;
import com.kntro.reqsai.shared.application.search.SearchHit;
import com.kntro.reqsai.shared.application.search.SearchHitType;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.workspace.search.WorkspaceSearchPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Global Search")
@ExtendWith(MockitoExtension.class)
class GlobalSearchServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID CALLER_ID = UUID.randomUUID();

    @Mock
    private WorkspaceSearchPort workspaceSearch;
    @Mock
    private DiscoverySearchPort discoverySearch;

    private GlobalSearchService service() {
        return new GlobalSearchService(workspaceSearch, discoverySearch);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static SearchHit project(String name) {
        UUID id = UUID.randomUUID();
        return new SearchHit(SearchHitType.PROJECT, id, name, null, id);
    }

    @Test
    @DisplayName("blank query returns an empty list without touching any port")
    void blank_query_returns_empty() {
        TenantContext.setCurrentTenant(ORG_ID.toString());

        assertThat(service().search("   ", 8, CALLER_ID)).isEmpty();
        verifyNoInteractions(workspaceSearch, discoverySearch);
    }

    @Test
    @DisplayName("no bound tenant returns an empty list without touching any port")
    void no_tenant_returns_empty() {
        // TenantContext not set

        assertThat(service().search("checkout", 8, CALLER_ID)).isEmpty();
        verifyNoInteractions(workspaceSearch, discoverySearch);
    }

    @Test
    @DisplayName("merges hits across all types and caps to the requested limit")
    void merges_and_caps() {
        TenantContext.setCurrentTenant(ORG_ID.toString());
        ProjectScope scope = ProjectScope.unrestricted();
        when(workspaceSearch.resolveProjectScope(ORG_ID, CALLER_ID)).thenReturn(scope);

        List<SearchHit> fiveProjects = IntStream.range(0, 5).mapToObj(i -> project("p" + i)).toList();
        when(workspaceSearch.searchProjects(eq("checkout"), anyInt(), eq(ORG_ID), eq(scope))).thenReturn(fiveProjects);
        when(discoverySearch.searchUserStories(eq("checkout"), anyInt(), eq(scope)))
                .thenReturn(List.of(new SearchHit(SearchHitType.USER_STORY, UUID.randomUUID(), "story", null, UUID.randomUUID())));
        when(workspaceSearch.searchOrganizations(eq("checkout"), anyInt(), eq(CALLER_ID)))
                .thenReturn(List.of(new SearchHit(SearchHitType.ORGANIZATION, UUID.randomUUID(), "Org", "org", null)));
        when(workspaceSearch.searchMembers(eq("checkout"), anyInt(), eq(ORG_ID), eq(CALLER_ID)))
                .thenReturn(List.of(new SearchHit(SearchHitType.MEMBER, UUID.randomUUID(), "Jane", "jane@acme.io", null)));

        List<SearchHit> result = service().search("checkout", 6, CALLER_ID);

        assertThat(result).hasSize(6); // 5 projects + 1 story, orgs/members trimmed by cap
        assertThat(result).extracting(SearchHit::type)
                .containsExactly(SearchHitType.PROJECT, SearchHitType.PROJECT, SearchHitType.PROJECT,
                        SearchHitType.PROJECT, SearchHitType.PROJECT, SearchHitType.USER_STORY);
    }

    @Test
    @DisplayName("limit above the hard cap is clamped to MAX_LIMIT")
    void limit_is_clamped_to_max() {
        TenantContext.setCurrentTenant(ORG_ID.toString());
        when(workspaceSearch.resolveProjectScope(ORG_ID, CALLER_ID)).thenReturn(ProjectScope.unrestricted());
        when(workspaceSearch.searchProjects(anyString(), anyInt(), any(), any())).thenReturn(List.of());
        when(discoverySearch.searchUserStories(anyString(), anyInt(), any())).thenReturn(List.of());
        when(workspaceSearch.searchOrganizations(anyString(), anyInt(), any())).thenReturn(List.of());
        when(workspaceSearch.searchMembers(anyString(), anyInt(), any(), any())).thenReturn(List.of());

        service().search("x", 1000, CALLER_ID);

        // each per-type search is invoked with the clamped limit, never the raw 1000
        verify(workspaceSearch).searchProjects(eq("x"), eq(GlobalSearchService.MAX_LIMIT), eq(ORG_ID), any());
        verify(workspaceSearch, never()).searchProjects(anyString(), eq(1000), any(), any());
    }
}
