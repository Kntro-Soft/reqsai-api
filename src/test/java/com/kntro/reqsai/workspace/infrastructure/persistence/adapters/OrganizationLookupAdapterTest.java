package com.kntro.reqsai.workspace.infrastructure.persistence.adapters;

import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.infrastructure.persistence.repositories.OrganizationJpaRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationLookupAdapterTest {

    @Mock
    private OrganizationJpaRepository jpa;

    @Mock
    private MemberRepository members;

    @InjectMocks
    private OrganizationLookupAdapter adapter;

    private final UUID userId = UUID.randomUUID();
    private final UUID ownedOrgId = UUID.randomUUID();
    private final UUID memberOrgId = UUID.randomUUID();

    @Test
    @DisplayName("canAccess is true when the user owns the organization")
    void canAccessWhenOwner() {
        when(jpa.existsByIdAndOwnerId(ownedOrgId, userId)).thenReturn(true);

        assertThat(adapter.canAccess(ownedOrgId, userId)).isTrue();
    }

    @Test
    @DisplayName("canAccess is true when the user is an active member (not the owner)")
    void canAccessWhenActiveMember() {
        when(jpa.existsByIdAndOwnerId(memberOrgId, userId)).thenReturn(false);
        when(members.existsByOrganizationIdAndUserIdAndStatus(memberOrgId, userId, MemberStatus.ACTIVE))
                .thenReturn(true);

        assertThat(adapter.canAccess(memberOrgId, userId)).isTrue();
    }

    @Test
    @DisplayName("canAccess is false when the user is neither owner nor active member")
    void canAccessWhenNeither() {
        when(jpa.existsByIdAndOwnerId(memberOrgId, userId)).thenReturn(false);
        when(members.existsByOrganizationIdAndUserIdAndStatus(memberOrgId, userId, MemberStatus.ACTIVE))
                .thenReturn(false);

        assertThat(adapter.canAccess(memberOrgId, userId)).isFalse();
    }

    @Test
    @DisplayName("findDefaultOrganizationId prefers an owned organization")
    void defaultOrgPrefersOwned() {
        Organization owned = mock(Organization.class);
        when(owned.getId()).thenReturn(ownedOrgId);
        when(jpa.findFirstByOwnerIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(owned));

        assertThat(adapter.findDefaultOrganizationId(userId)).contains(ownedOrgId);
    }

    @Test
    @DisplayName("findDefaultOrganizationId falls back to an active member organization")
    void defaultOrgFallsBackToMembership() {
        when(jpa.findFirstByOwnerIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());
        Member membership = mock(Member.class);
        when(membership.getOrganizationId()).thenReturn(memberOrgId);
        when(members.findAllByUserIdAndStatus(userId, MemberStatus.ACTIVE)).thenReturn(List.of(membership));

        assertThat(adapter.findDefaultOrganizationId(userId)).contains(memberOrgId);
    }

    @Test
    @DisplayName("findDefaultOrganizationId is empty when the user belongs to no organization")
    void defaultOrgEmptyWhenNone() {
        when(jpa.findFirstByOwnerIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());
        when(members.findAllByUserIdAndStatus(userId, MemberStatus.ACTIVE)).thenReturn(List.of());

        assertThat(adapter.findDefaultOrganizationId(userId)).isEmpty();
    }
}
