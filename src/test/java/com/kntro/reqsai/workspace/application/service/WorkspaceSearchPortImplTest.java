package com.kntro.reqsai.workspace.application.service;

import com.kntro.reqsai.shared.application.search.ProjectScope;
import com.kntro.reqsai.shared.application.search.SearchHit;
import com.kntro.reqsai.shared.application.search.SearchHitType;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.port.WorkspaceLexicalSearchRepository;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.OrgRole;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import com.kntro.reqsai.workspace.search.WorkspaceSearchPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Application: Workspace Search Port")
@ExtendWith(MockitoExtension.class)
class WorkspaceSearchPortImplTest {

    @Mock private OrganizationRepository organizations;
    @Mock private MemberRepository members;
    @Mock private ProjectAccessService projectAccess;
    @Mock private WorkspaceLexicalSearchRepository lexicalSearch;

    private WorkspaceSearchPort port() {
        return new WorkspaceSearchPortImpl(organizations, members, projectAccess, lexicalSearch);
    }

    private Member activeMember(UUID orgId, UUID userId) {
        return new Member(orgId, userId, "u@example.com", "User", OrgRole.MEMBER,
                MemberStatus.ACTIVE, UUID.randomUUID(), Instant.now());
    }

    @Test
    @DisplayName("owner or admin resolves to an unrestricted project scope")
    void owner_resolves_unrestricted_scope() {
        Organization org = OrganizationMother.active().build();
        UUID caller = org.getOwnerId();
        when(organizations.findById(org.getId())).thenReturn(Optional.of(org));
        when(projectAccess.accessibleProjectIds(org, caller)).thenReturn(Optional.empty());

        ProjectScope scope = port().resolveProjectScope(org.getId(), caller);

        assertThat(scope.all()).isTrue();
    }

    @Test
    @DisplayName("regular member resolves to a scope restricted to assigned projects")
    void member_resolves_restricted_scope() {
        Organization org = OrganizationMother.active().build();
        UUID caller = UUID.randomUUID();
        UUID assigned = UUID.randomUUID();
        when(organizations.findById(org.getId())).thenReturn(Optional.of(org));
        when(projectAccess.accessibleProjectIds(org, caller)).thenReturn(Optional.of(Set.of(assigned)));

        ProjectScope scope = port().resolveProjectScope(org.getId(), caller);

        assertThat(scope.all()).isFalse();
        assertThat(scope.projectIds()).containsExactly(assigned);
    }

    @Test
    @DisplayName("unrestricted project search hits the org wide query")
    void searchProjects_unrestricted_uses_org_query() {
        UUID orgId = UUID.randomUUID();
        SearchHit hit = new SearchHit(SearchHitType.PROJECT, UUID.randomUUID(), "Checkout", null, UUID.randomUUID());
        when(lexicalSearch.searchProjectsInOrganization(orgId, "check", 8)).thenReturn(List.of(hit));

        List<SearchHit> result = port().searchProjects("check", 8, orgId, ProjectScope.unrestricted());

        assertThat(result).containsExactly(hit);
        verify(lexicalSearch, never()).searchProjectsInIds(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("restricted project search is scoped to the accessible ids")
    void searchProjects_restricted_uses_id_query() {
        UUID orgId = UUID.randomUUID();
        UUID assigned = UUID.randomUUID();
        when(lexicalSearch.searchProjectsInIds(eq(orgId), eq(Set.of(assigned)), eq("check"), eq(8)))
                .thenReturn(List.of());

        port().searchProjects("check", 8, orgId, ProjectScope.restrictedTo(Set.of(assigned)));

        verify(lexicalSearch).searchProjectsInIds(orgId, Set.of(assigned), "check", 8);
        verify(lexicalSearch, never()).searchProjectsInOrganization(any(), any(), anyInt());
    }

    @Test
    @DisplayName("empty scope short circuits project search")
    void searchProjects_empty_scope_returns_empty() {
        List<SearchHit> result = port().searchProjects("x", 8, UUID.randomUUID(), ProjectScope.restrictedTo(Set.of()));

        assertThat(result).isEmpty();
        verifyNoInteractions(lexicalSearch);
    }

    @Test
    @DisplayName("unrestricted glossary term search hits the tenant wide query")
    void searchGlossaryTerms_unrestricted_uses_tenant_query() {
        SearchHit hit = new SearchHit(SearchHitType.GLOSSARY_TERM, UUID.randomUUID(), "Sprint",
                "A time-box.", UUID.randomUUID());
        when(lexicalSearch.searchGlossaryTermsInTenant("spr", 8)).thenReturn(List.of(hit));

        List<SearchHit> result = port().searchGlossaryTerms("spr", 8, ProjectScope.unrestricted());

        assertThat(result).containsExactly(hit);
        verify(lexicalSearch, never()).searchGlossaryTermsInIds(any(), any(), anyInt());
    }

    @Test
    @DisplayName("restricted glossary term search is scoped to the accessible ids")
    void searchGlossaryTerms_restricted_uses_id_query() {
        UUID assigned = UUID.randomUUID();
        when(lexicalSearch.searchGlossaryTermsInIds(eq(Set.of(assigned)), eq("spr"), eq(8)))
                .thenReturn(List.of());

        port().searchGlossaryTerms("spr", 8, ProjectScope.restrictedTo(Set.of(assigned)));

        verify(lexicalSearch).searchGlossaryTermsInIds(Set.of(assigned), "spr", 8);
        verify(lexicalSearch, never()).searchGlossaryTermsInTenant(any(), anyInt());
    }

    @Test
    @DisplayName("empty scope short circuits glossary term search")
    void searchGlossaryTerms_empty_scope_returns_empty() {
        List<SearchHit> result = port().searchGlossaryTerms("x", 8, ProjectScope.restrictedTo(Set.of()));

        assertThat(result).isEmpty();
        verifyNoInteractions(lexicalSearch);
    }

    @Test
    @DisplayName("unrestricted document search hits the tenant wide query")
    void searchDocuments_unrestricted_uses_tenant_query() {
        SearchHit hit = new SearchHit(SearchHitType.DOCUMENT, UUID.randomUUID(), "Business Rules v1",
                "BUSINESS_RULES", UUID.randomUUID());
        when(lexicalSearch.searchDocumentsInTenant("bus", 8)).thenReturn(List.of(hit));

        List<SearchHit> result = port().searchDocuments("bus", 8, ProjectScope.unrestricted());

        assertThat(result).containsExactly(hit);
        verify(lexicalSearch, never()).searchDocumentsInIds(any(), any(), anyInt());
    }

    @Test
    @DisplayName("restricted document search is scoped to the accessible ids")
    void searchDocuments_restricted_uses_id_query() {
        UUID assigned = UUID.randomUUID();
        when(lexicalSearch.searchDocumentsInIds(eq(Set.of(assigned)), eq("bus"), eq(8)))
                .thenReturn(List.of());

        port().searchDocuments("bus", 8, ProjectScope.restrictedTo(Set.of(assigned)));

        verify(lexicalSearch).searchDocumentsInIds(Set.of(assigned), "bus", 8);
        verify(lexicalSearch, never()).searchDocumentsInTenant(any(), anyInt());
    }

    @Test
    @DisplayName("empty scope short circuits document search")
    void searchDocuments_empty_scope_returns_empty() {
        List<SearchHit> result = port().searchDocuments("x", 8, ProjectScope.restrictedTo(Set.of()));

        assertThat(result).isEmpty();
        verifyNoInteractions(lexicalSearch);
    }

    @Test
    @DisplayName("member search is denied for a caller outside the organization")
    void searchMembers_denied_for_non_member() {
        UUID orgId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        when(organizations.findAllByOwnerId(caller)).thenReturn(List.of());
        when(members.findAllByUserIdAndStatus(caller, MemberStatus.ACTIVE)).thenReturn(List.of());

        List<SearchHit> result = port().searchMembers("jane", 8, orgId, caller);

        assertThat(result).isEmpty();
        verify(lexicalSearch, never()).searchMembers(any(), any(), anyInt());
    }

    @Test
    @DisplayName("member search runs for a caller that belongs to the organization")
    void searchMembers_runs_for_member() {
        UUID orgId = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        SearchHit hit = new SearchHit(SearchHitType.MEMBER, UUID.randomUUID(), "Jane", "jane@acme.io", null);
        when(organizations.findAllByOwnerId(caller)).thenReturn(List.of());
        when(members.findAllByUserIdAndStatus(caller, MemberStatus.ACTIVE)).thenReturn(List.of(activeMember(orgId, caller)));
        when(lexicalSearch.searchMembers(orgId, "jane", 8)).thenReturn(List.of(hit));

        List<SearchHit> result = port().searchMembers("jane", 8, orgId, caller);

        assertThat(result).containsExactly(hit);
    }

    @Test
    @DisplayName("organization search is scoped to the caller own organizations")
    void searchOrganizations_scoped_to_caller_orgs() {
        UUID caller = UUID.randomUUID();
        Organization owned = OrganizationMother.active().withOwnerId(caller).build();
        SearchHit hit = new SearchHit(SearchHitType.ORGANIZATION, owned.getId(), "Acme", "acme", null);
        when(organizations.findAllByOwnerId(caller)).thenReturn(List.of(owned));
        when(members.findAllByUserIdAndStatus(caller, MemberStatus.ACTIVE)).thenReturn(List.of());
        when(lexicalSearch.searchOrganizations(Set.of(owned.getId()), "acme", 8)).thenReturn(List.of(hit));

        List<SearchHit> result = port().searchOrganizations("acme", 8, caller);

        assertThat(result).containsExactly(hit);
    }

    @Test
    @DisplayName("organization search returns empty when the caller belongs to none")
    void searchOrganizations_empty_when_no_orgs() {
        UUID caller = UUID.randomUUID();
        when(organizations.findAllByOwnerId(caller)).thenReturn(List.of());
        when(members.findAllByUserIdAndStatus(caller, MemberStatus.ACTIVE)).thenReturn(List.of());

        assertThat(port().searchOrganizations("acme", 8, caller)).isEmpty();
        verify(lexicalSearch, never()).searchOrganizations(any(), any(), anyInt());
    }
}
