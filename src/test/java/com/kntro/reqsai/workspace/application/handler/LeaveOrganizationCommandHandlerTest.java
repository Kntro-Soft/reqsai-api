package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.LeaveOrganizationCommand;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Leave Organization")
@ExtendWith(MockitoExtension.class)
class LeaveOrganizationCommandHandlerTest {

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private MemberRepository members;
    @InjectMocks
    private LeaveOrganizationCommandHandler handler;

    @Test
    @DisplayName("active member leaves and is deactivated")
    void active_member_leaves() {
        UUID ownerId = UUID.randomUUID();
        Organization org = OrganizationMother.active().withOwnerId(ownerId).build();
        UUID memberUser = UUID.randomUUID();
        Member member = new Member(org.getId(), memberUser, "m@example.com", "M", OrgRole.MEMBER,
                MemberStatus.ACTIVE, ownerId, Instant.now());

        when(organizations.findById(org.getId())).thenReturn(Optional.of(org));
        when(members.findByOrganizationIdAndUserIdAndStatus(org.getId(), memberUser, MemberStatus.ACTIVE)).thenReturn(Optional.of(member));
        when(members.save(any(Member.class))).thenAnswer(i -> i.getArgument(0));

        handler.handle(new LeaveOrganizationCommand(org.getId(), memberUser));

        assertThat(member.getStatus()).isEqualTo(MemberStatus.INACTIVE);
        verify(members).save(member);
    }

    @Test
    @DisplayName("owner cannot leave")
    void owner_cannot_leave() {
        UUID ownerId = UUID.randomUUID();
        Organization org = OrganizationMother.active().withOwnerId(ownerId).build();

        when(organizations.findById(org.getId())).thenReturn(Optional.of(org));

        assertThatThrownBy(() -> handler.handle(new LeaveOrganizationCommand(org.getId(), ownerId)))
                .isInstanceOf(DomainException.class);
        verify(members, never()).save(any());
    }

    @Test
    @DisplayName("non-member caller is rejected")
    void non_member_rejected() {
        UUID ownerId = UUID.randomUUID();
        Organization org = OrganizationMother.active().withOwnerId(ownerId).build();
        UUID stranger = UUID.randomUUID();

        when(organizations.findById(org.getId())).thenReturn(Optional.of(org));
        when(members.findByOrganizationIdAndUserIdAndStatus(org.getId(), stranger, MemberStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new LeaveOrganizationCommand(org.getId(), stranger)))
                .isInstanceOf(DomainException.class);
        verify(members, never()).save(any());
    }
}
