package com.kntro.reqsai.workspace.application.handler;

import com.kntro.reqsai.iam.application.port.AccountLookupPort;
import com.kntro.reqsai.shared.domain.exception.DomainException;
import com.kntro.reqsai.shared.domain.support.HashUtils;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantSchemaResolver;
import com.kntro.reqsai.workspace.application.command.AcceptInvitationCommand;
import com.kntro.reqsai.workspace.application.port.InvitationRepository;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.application.port.OrganizationRepository;
import com.kntro.reqsai.workspace.application.result.AcceptInvitationResult;
import com.kntro.reqsai.workspace.application.service.ProjectAssignmentMaterializer;
import com.kntro.reqsai.workspace.domain.exception.WorkspaceError;
import com.kntro.reqsai.workspace.domain.model.Invitation;
import com.kntro.reqsai.workspace.domain.model.InvitationStatus;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Accept Invitation")
@ExtendWith(MockitoExtension.class)
class AcceptInvitationCommandHandlerTest {

    private static final String RAW_TOKEN = "the-raw-token";

    @Mock
    private InvitationRepository invitations;
    @Mock
    private MemberRepository members;
    @Mock
    private OrganizationRepository organizations;
    @Mock
    private AccountLookupPort accountLookup;
    @Mock
    private ProjectAssignmentMaterializer projectAssignmentMaterializer;
    @Mock
    private TenantSchemaResolver tenantSchemaResolver;
    @InjectMocks
    private AcceptInvitationCommandHandler handler;

    private Organization org() {
        return OrganizationMother.active().build();
    }

    private Invitation invitation(Organization org, UUID memberId, String email, Instant expiresAt) {
        return Invitation.issue(org.getId(), org.getName(), memberId, email, "Invitee",
                OrgRole.MEMBER, RAW_TOKEN, UUID.randomUUID(), "Owner", expiresAt);
    }

    private Invitation projectInvitation(Organization org, UUID memberId, String email, UUID projectId, UUID roleId) {
        return Invitation.issue(org.getId(), org.getName(), memberId, email, "Invitee",
                OrgRole.MEMBER, RAW_TOKEN, UUID.randomUUID(), "Owner",
                Instant.now().plus(1, ChronoUnit.DAYS), projectId, roleId, "Project", "Analyst");
    }

    private Member pendingMember(Organization org, String email) {
        return new Member(org.getId(), null, email, "Invitee", OrgRole.MEMBER,
                MemberStatus.PENDING, UUID.randomUUID(), Instant.now());
    }

    @Test
    @DisplayName("links the member and marks the invitation accepted on a matching email")
    void accept_links_and_activates() {
        Organization org = org();
        UUID callerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Invitation invitation = invitation(org, memberId, "invitee@example.com", Instant.now().plus(1, ChronoUnit.DAYS));
        Member member = pendingMember(org, "invitee@example.com");

        when(invitations.findByTokenHash(HashUtils.sha256(RAW_TOKEN))).thenReturn(Optional.of(invitation));
        when(organizations.findById(org.getId())).thenReturn(Optional.of(org));
        when(accountLookup.findEmailByUserId(callerId)).thenReturn(Optional.of("INVITEE@example.com"));
        when(members.findByIdAndOrganizationIdAndStatusIn(any(), any(), any())).thenReturn(Optional.of(member));

        AcceptInvitationResult result = handler.handle(new AcceptInvitationCommand(RAW_TOKEN, callerId));

        assertThat(result.organizationId()).isEqualTo(org.getId());
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getUserId()).isEqualTo(callerId);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        verify(members).save(member);
        verify(projectAssignmentMaterializer, never()).assign(any(), any(), any());
    }

    @Test
    @DisplayName("project-scoped invitation materializes the project assignment on accept")
    void accept_materializes_project_assignment() {
        Organization org = org();
        UUID callerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Invitation invitation = projectInvitation(org, memberId, "invitee@example.com", projectId, roleId);
        Member member = pendingMember(org, "invitee@example.com");

        when(invitations.findByTokenHash(HashUtils.sha256(RAW_TOKEN))).thenReturn(Optional.of(invitation));
        when(organizations.findById(org.getId())).thenReturn(Optional.of(org));
        when(accountLookup.findEmailByUserId(callerId)).thenReturn(Optional.of("invitee@example.com"));
        when(members.findByIdAndOrganizationIdAndStatusIn(any(), any(), any())).thenReturn(Optional.of(member));
        when(tenantSchemaResolver.resolveTenantSchema(org.getId().toString())).thenReturn("tenant_acme");

        handler.handle(new AcceptInvitationCommand(RAW_TOKEN, callerId));

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        verify(projectAssignmentMaterializer).assign(eq(projectId), eq(roleId), eq(member.getId()));
    }

    @Test
    @DisplayName("unknown token -> 404")
    void unknown_token_not_found() {
        when(invitations.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new AcceptInvitationCommand(RAW_TOKEN, UUID.randomUUID())))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(WorkspaceError.INVITATION_NOT_FOUND);
    }

    @Test
    @DisplayName("expired invitation -> 410 and is marked EXPIRED")
    void expired_gone() {
        Organization org = org();
        Invitation invitation = invitation(org, UUID.randomUUID(), "invitee@example.com",
                Instant.now().minus(1, ChronoUnit.DAYS));

        when(invitations.findByTokenHash(anyString())).thenReturn(Optional.of(invitation));
        when(organizations.findById(org.getId())).thenReturn(Optional.of(org));

        assertThatThrownBy(() -> handler.handle(new AcceptInvitationCommand(RAW_TOKEN, UUID.randomUUID())))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(WorkspaceError.INVITATION_EXPIRED);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
        verify(members, never()).save(any());
    }

    @Test
    @DisplayName("email mismatch -> 403 and no linking")
    void email_mismatch_forbidden() {
        Organization org = org();
        UUID callerId = UUID.randomUUID();
        Invitation invitation = invitation(org, UUID.randomUUID(), "invitee@example.com",
                Instant.now().plus(1, ChronoUnit.DAYS));

        when(invitations.findByTokenHash(anyString())).thenReturn(Optional.of(invitation));
        when(organizations.findById(org.getId())).thenReturn(Optional.of(org));
        when(accountLookup.findEmailByUserId(callerId)).thenReturn(Optional.of("someone-else@example.com"));

        assertThatThrownBy(() -> handler.handle(new AcceptInvitationCommand(RAW_TOKEN, callerId)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).error())
                .isEqualTo(WorkspaceError.INVITATION_EMAIL_MISMATCH);
        verify(members, never()).save(any());
    }

    @Test
    @DisplayName("already accepted -> idempotent 200, no re-linking")
    void already_accepted_idempotent() {
        Organization org = org();
        UUID memberId = UUID.randomUUID();
        Invitation invitation = invitation(org, memberId, "invitee@example.com", Instant.now().plus(1, ChronoUnit.DAYS));
        invitation.markAccepted(Instant.now());

        when(invitations.findByTokenHash(anyString())).thenReturn(Optional.of(invitation));
        when(organizations.findById(org.getId())).thenReturn(Optional.of(org));

        AcceptInvitationResult result = handler.handle(new AcceptInvitationCommand(RAW_TOKEN, UUID.randomUUID()));

        assertThat(result.memberId()).isEqualTo(memberId);
        verify(members, never()).save(any());
        verify(members, never()).findByIdAndOrganizationIdAndStatusIn(any(), any(), any());
    }
}
