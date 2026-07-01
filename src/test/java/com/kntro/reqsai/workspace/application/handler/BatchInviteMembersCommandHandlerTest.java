package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.BatchInviteMembersCommand;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.service.OrganizationAdminAccessService;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Batch Invite Members")
@ExtendWith(MockitoExtension.class)
class BatchInviteMembersCommandHandlerTest {

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private MemberRepository members;
    @InjectMocks
    private OrganizationAdminAccessService access;

    private BatchInviteMembersCommandHandler handler;

    private void initHandler() {
        handler = new BatchInviteMembersCommandHandler(organizations, members, access);
    }

    private BatchInviteMembersCommand.Invitation invite(String email, OrgRole role) {
        return new BatchInviteMembersCommand.Invitation(email, "Name", role);
    }

    @Test
    @DisplayName("owner invites several members atomically")
    void owner_invites_batch() {
        UUID ownerId = UUID.randomUUID();
        Organization org = OrganizationMother.active().withOwnerId(ownerId).build();
        UUID orgId = org.getId();

        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        when(members.save(any(Member.class))).thenAnswer(i -> i.getArgument(0));
        initHandler();

        List<Member> result = handler.handle(new BatchInviteMembersCommand(orgId,
                List.of(invite("a@example.com", OrgRole.MEMBER), invite("b@example.com", OrgRole.ADMIN)), ownerId));

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(m -> m.getStatus() == MemberStatus.PENDING);
        verify(members, times(2)).save(any(Member.class));
    }

    @Test
    @DisplayName("duplicate email inside the batch fails the whole request")
    void duplicate_in_batch_fails() {
        UUID ownerId = UUID.randomUUID();
        Organization org = OrganizationMother.active().withOwnerId(ownerId).build();
        UUID orgId = org.getId();

        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        initHandler();

        assertThatThrownBy(() -> handler.handle(new BatchInviteMembersCommand(orgId,
                List.of(invite("dup@example.com", OrgRole.MEMBER), invite("dup@example.com", OrgRole.MEMBER)), ownerId)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("email already present in the org fails the request")
    void existing_email_fails() {
        UUID ownerId = UUID.randomUUID();
        Organization org = OrganizationMother.active().withOwnerId(ownerId).build();
        UUID orgId = org.getId();

        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        when(members.existsByOrganizationIdAndEmailAndStatusIn(eq(orgId), eq("taken@example.com"), any())).thenReturn(true);
        initHandler();

        assertThatThrownBy(() -> handler.handle(new BatchInviteMembersCommand(orgId,
                List.of(invite("taken@example.com", OrgRole.MEMBER)), ownerId)))
                .isInstanceOf(DomainException.class);
        verify(members, never()).save(any());
    }

    @Test
    @DisplayName("a regular member cannot batch invite")
    void member_cannot_invite() {
        UUID ownerId = UUID.randomUUID();
        Organization org = OrganizationMother.active().withOwnerId(ownerId).build();
        UUID orgId = org.getId();
        UUID memberUser = UUID.randomUUID();
        Member memberRow = new Member(orgId, memberUser, "mem@example.com", "Mem", OrgRole.MEMBER,
                MemberStatus.ACTIVE, ownerId, java.time.Instant.now());

        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        when(members.findByOrganizationIdAndUserIdAndStatus(orgId, memberUser, MemberStatus.ACTIVE)).thenReturn(Optional.of(memberRow));
        initHandler();

        assertThatThrownBy(() -> handler.handle(new BatchInviteMembersCommand(orgId,
                List.of(invite("x@example.com", OrgRole.MEMBER)), memberUser)))
                .isInstanceOf(DomainException.class);
        verify(members, never()).save(any());
    }
}
