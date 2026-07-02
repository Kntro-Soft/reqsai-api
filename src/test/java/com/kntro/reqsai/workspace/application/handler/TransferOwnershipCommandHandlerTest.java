package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.TransferOwnershipCommand;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Transfer Ownership")
@ExtendWith(MockitoExtension.class)
class TransferOwnershipCommandHandlerTest {

    private static final List<MemberStatus> ACTIVE_ONLY = List.of(MemberStatus.ACTIVE);

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private MemberRepository members;
    @InjectMocks
    private TransferOwnershipCommandHandler handler;

    private Member activeMember(UUID orgId, UUID userId, OrgRole role) {
        return new Member(orgId, userId, "u" + UUID.randomUUID() + "@example.com", "User", role,
                MemberStatus.ACTIVE, UUID.randomUUID(), Instant.now());
    }

    @Test
    @DisplayName("owner transfers to an active member and previous owner is demoted to ADMIN")
    void owner_transfers_to_active_member() {
        UUID ownerId = UUID.randomUUID();
        Organization org = OrganizationMother.active().withOwnerId(ownerId).build();
        UUID orgId = org.getId();
        UUID newOwnerUser = UUID.randomUUID();
        Member target = activeMember(orgId, newOwnerUser, OrgRole.MEMBER);
        Member ownerMember = activeMember(orgId, ownerId, OrgRole.MEMBER);

        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        when(members.findByIdAndOrganizationIdAndStatusIn(target.getId(), orgId, ACTIVE_ONLY)).thenReturn(Optional.of(target));
        when(members.findByOrganizationIdAndUserIdAndStatus(orgId, ownerId, MemberStatus.ACTIVE)).thenReturn(Optional.of(ownerMember));
        when(organizations.save(any(Organization.class))).thenAnswer(i -> i.getArgument(0));
        when(members.save(any(Member.class))).thenAnswer(i -> i.getArgument(0));

        Organization result = handler.handle(new TransferOwnershipCommand(orgId, target.getId(), ownerId));

        assertThat(result.getOwnerId()).isEqualTo(newOwnerUser);
        assertThat(target.getRole()).isEqualTo(OrgRole.OWNER);
        assertThat(ownerMember.getRole()).isEqualTo(OrgRole.ADMIN);
        verify(organizations).save(org);
    }

    @Test
    @DisplayName("owner transfers and previous owner gets a new ADMIN member row when none existed")
    void previous_owner_row_created_when_absent() {
        UUID ownerId = UUID.randomUUID();
        Organization org = OrganizationMother.active().withOwnerId(ownerId).build();
        UUID orgId = org.getId();
        UUID newOwnerUser = UUID.randomUUID();
        Member target = activeMember(orgId, newOwnerUser, OrgRole.ADMIN);

        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        when(members.findByIdAndOrganizationIdAndStatusIn(target.getId(), orgId, ACTIVE_ONLY)).thenReturn(Optional.of(target));
        when(members.findByOrganizationIdAndUserIdAndStatus(orgId, ownerId, MemberStatus.ACTIVE)).thenReturn(Optional.empty());
        when(organizations.save(any(Organization.class))).thenAnswer(i -> i.getArgument(0));
        when(members.save(any(Member.class))).thenAnswer(i -> i.getArgument(0));

        Organization result = handler.handle(new TransferOwnershipCommand(orgId, target.getId(), ownerId));

        assertThat(result.getOwnerId()).isEqualTo(newOwnerUser);
        verify(members, atLeastOnce()).save(any(Member.class));
    }

    @Test
    @DisplayName("non-owner caller is rejected")
    void non_owner_rejected() {
        UUID ownerId = UUID.randomUUID();
        Organization org = OrganizationMother.active().withOwnerId(ownerId).build();

        when(organizations.findById(org.getId())).thenReturn(Optional.of(org));

        assertThatThrownBy(() -> handler.handle(new TransferOwnershipCommand(org.getId(), UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(DomainException.class);
        verify(organizations, never()).save(any());
    }

    @Test
    @DisplayName("target that is not an active member is rejected")
    void target_not_active_member_rejected() {
        UUID ownerId = UUID.randomUUID();
        Organization org = OrganizationMother.active().withOwnerId(ownerId).build();
        UUID targetId = UUID.randomUUID();

        when(organizations.findById(org.getId())).thenReturn(Optional.of(org));
        when(members.findByIdAndOrganizationIdAndStatusIn(targetId, org.getId(), ACTIVE_ONLY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new TransferOwnershipCommand(org.getId(), targetId, ownerId)))
                .isInstanceOf(DomainException.class);
        verify(organizations, never()).save(any());
    }
}
