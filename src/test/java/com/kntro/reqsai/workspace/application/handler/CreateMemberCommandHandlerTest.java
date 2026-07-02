package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.workspace.application.command.CreateMemberCommand;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.service.InvitationIssuer;
import com.kntro.reqsai.workspace.application.service.OrganizationAdminAccessService;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.OrgRole;
import com.kntro.reqsai.workspace.domain.model.Organization;
import com.kntro.reqsai.workspace.mothers.OrganizationMother;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Create Member")
@ExtendWith(MockitoExtension.class)
class CreateMemberCommandHandlerTest {

    @Mock
    private OrganizationRepository organizations;
    @Mock
    private MemberRepository members;
    @Mock
    private OrganizationAdminAccessService access;
    @Mock
    private InvitationIssuer invitationIssuer;
    @InjectMocks
    private CreateMemberCommandHandler handler;

    @Nested
    @DisplayName("Successful creation")
    class SuccessfulCreation {

        @Test
        @DisplayName("should create direct active member")
        void should_create_direct_active_member() {
            Organization organization = OrganizationMother.active().build();
            UUID orgId = organization.getId();
            UUID requestedBy = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            CreateMemberCommand command = new CreateMemberCommand(
                    orgId, userId, "active@example.com", "Active Member", OrgRole.MEMBER, requestedBy);

            when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
            doNothing().when(access).assertOwnerOrAdmin(organization, requestedBy, "manage organization members");
            when(members.existsByOrganizationIdAndEmailAndStatusIn(orgId, "active@example.com",
                    List.of(MemberStatus.ACTIVE, MemberStatus.PENDING))).thenReturn(false);
            when(members.existsByOrganizationIdAndUserIdAndStatus(orgId, userId, MemberStatus.ACTIVE)).thenReturn(false);
            when(members.countByOrganizationIdAndStatus(orgId, MemberStatus.ACTIVE)).thenReturn(0);
            when(members.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Member member = handler.handle(command);

            assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
            assertThat(member.getUserId()).isEqualTo(userId);
            verify(members).save(any(Member.class));
        }

        @Test
        @DisplayName("should create pending invitation when user id is missing")
        void should_create_pending_invitation_when_user_id_is_missing() {
            Organization organization = OrganizationMother.active().build();
            UUID orgId = organization.getId();
            UUID requestedBy = UUID.randomUUID();
            CreateMemberCommand command = new CreateMemberCommand(
                    orgId, null, "invitee@example.com", "Invitee", OrgRole.ADMIN, requestedBy);

            when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
            doNothing().when(access).assertOwnerOrAdmin(organization, requestedBy, "manage organization members");
            when(members.existsByOrganizationIdAndEmailAndStatusIn(orgId, "invitee@example.com",
                    List.of(MemberStatus.ACTIVE, MemberStatus.PENDING))).thenReturn(false);
            when(members.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Member member = handler.handle(command);

            assertThat(member.getStatus()).isEqualTo(MemberStatus.PENDING);
            assertThat(member.getUserId()).isNull();
        }
    }

    @Nested
    @DisplayName("Validation failures")
    class ValidationFailures {

        @Test
        @DisplayName("should fail if organization does not exist")
        void should_fail_if_organization_does_not_exist() {
            UUID orgId = UUID.randomUUID();
            CreateMemberCommand command = new CreateMemberCommand(
                    orgId, UUID.randomUUID(), "active@example.com", "Active Member", OrgRole.MEMBER, UUID.randomUUID());

            when(organizations.findById(orgId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(DomainException.class);
            verify(members, never()).save(any());
        }

        @Test
        @DisplayName("should fail if email already exists")
        void should_fail_if_email_already_exists() {
            Organization organization = OrganizationMother.active().build();
            UUID orgId = organization.getId();
            UUID requestedBy = UUID.randomUUID();
            CreateMemberCommand command = new CreateMemberCommand(
                    orgId, null, "invitee@example.com", "Invitee", OrgRole.MEMBER, requestedBy);

            when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
            doNothing().when(access).assertOwnerOrAdmin(organization, requestedBy, "manage organization members");
            when(members.existsByOrganizationIdAndEmailAndStatusIn(orgId, "invitee@example.com",
                    List.of(MemberStatus.ACTIVE, MemberStatus.PENDING))).thenReturn(true);

            assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(DomainException.class);
            verify(members, never()).save(any());
        }

        @Test
        @DisplayName("should fail if user id already exists for active member")
        void should_fail_if_user_id_already_exists_for_active_member() {
            Organization organization = OrganizationMother.active().build();
            UUID orgId = organization.getId();
            UUID requestedBy = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            CreateMemberCommand command = new CreateMemberCommand(
                    orgId, userId, "active@example.com", "Active Member", OrgRole.MEMBER, requestedBy);

            when(organizations.findById(orgId)).thenReturn(Optional.of(organization));
            doNothing().when(access).assertOwnerOrAdmin(organization, requestedBy, "manage organization members");
            when(members.existsByOrganizationIdAndEmailAndStatusIn(orgId, "active@example.com",
                    List.of(MemberStatus.ACTIVE, MemberStatus.PENDING))).thenReturn(false);
            when(members.existsByOrganizationIdAndUserIdAndStatus(orgId, userId, MemberStatus.ACTIVE)).thenReturn(true);

            assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(DomainException.class);
            verify(members, never()).save(any());
        }
    }
}
