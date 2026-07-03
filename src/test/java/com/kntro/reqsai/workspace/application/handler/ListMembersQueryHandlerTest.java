package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.iam.application.port.AccountLookupPort;
import com.kntro.reqsai.iam.application.port.AccountLookupPort.UserProfile;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.query.ListMembersQuery;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.OrgRole;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("Application: List Members")
@ExtendWith(MockitoExtension.class)
class ListMembersQueryHandlerTest {

    private static final List<MemberStatus> ACTIVE_AND_PENDING =
            List.of(MemberStatus.ACTIVE, MemberStatus.PENDING);

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private MemberRepository members;
    @Mock
    private AccountLookupPort accountLookup;
    @InjectMocks
    private ListMembersQueryHandler handler;

    private Member activeMember(UUID orgId, UUID userId, OrgRole role) {
        return new Member(orgId, userId, "u" + UUID.randomUUID() + "@example.com", "User", role,
                MemberStatus.ACTIVE, UUID.randomUUID(), Instant.now());
    }

    @Test
    @DisplayName("prepends the implicit owner as an OWNER row read from the IAM profile")
    void prepends_owner_row() {
        UUID ownerId = UUID.randomUUID();
        Organization org = OrganizationMother.active().withOwnerId(ownerId).build();
        UUID orgId = org.getId();
        Member regular = activeMember(orgId, UUID.randomUUID(), OrgRole.MEMBER);

        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        when(members.findAllByOrganizationIdAndStatusIn(orgId, ACTIVE_AND_PENDING)).thenReturn(List.of(regular));
        when(accountLookup.findProfileByUserId(ownerId))
                .thenReturn(Optional.of(new UserProfile("owner@example.com", "Owner Name")));

        List<Member> result = handler.handle(new ListMembersQuery(orgId, ownerId));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUserId()).isEqualTo(ownerId);
        assertThat(result.get(0).getRole()).isEqualTo(OrgRole.OWNER);
        assertThat(result.get(0).getDisplayName()).isEqualTo("Owner Name");
        assertThat(result.get(1)).isEqualTo(regular);
    }

    @Test
    @DisplayName("dedupes a stale owner member row so the owner appears exactly once with the OWNER role")
    void dedupes_owner_member_row() {
        UUID ownerId = UUID.randomUUID();
        Organization org = OrganizationMother.active().withOwnerId(ownerId).build();
        UUID orgId = org.getId();
        Member staleOwnerRow = activeMember(orgId, ownerId, OrgRole.MEMBER);
        Member other = activeMember(orgId, UUID.randomUUID(), OrgRole.ADMIN);

        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        when(members.findAllByOrganizationIdAndStatusIn(orgId, ACTIVE_AND_PENDING))
                .thenReturn(List.of(staleOwnerRow, other));
        when(accountLookup.findProfileByUserId(ownerId))
                .thenReturn(Optional.of(new UserProfile("owner@example.com", "Owner Name")));

        List<Member> result = handler.handle(new ListMembersQuery(orgId, ownerId));

        assertThat(result).hasSize(2);
        assertThat(result).filteredOn(m -> ownerId.equals(m.getUserId())).hasSize(1);
        assertThat(result.get(0).getRole()).isEqualTo(OrgRole.OWNER);
        assertThat(result).contains(other);
    }

    @Test
    @DisplayName("omits the owner when their profile cannot be resolved")
    void omits_owner_without_profile() {
        UUID ownerId = UUID.randomUUID();
        Organization org = OrganizationMother.active().withOwnerId(ownerId).build();
        UUID orgId = org.getId();
        Member regular = activeMember(orgId, UUID.randomUUID(), OrgRole.MEMBER);

        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        when(members.findAllByOrganizationIdAndStatusIn(orgId, ACTIVE_AND_PENDING)).thenReturn(List.of(regular));
        when(accountLookup.findProfileByUserId(ownerId)).thenReturn(Optional.empty());

        List<Member> result = handler.handle(new ListMembersQuery(orgId, ownerId));

        assertThat(result).containsExactly(regular);
    }
}
