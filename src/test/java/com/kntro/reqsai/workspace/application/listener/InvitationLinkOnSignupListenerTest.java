package com.kntro.reqsai.workspace.application.listener;

import com.kntro.reqsai.iam.api.AccountVerifiedIntegrationEvent;
import com.kntro.reqsai.iam.application.port.AccountLookupPort;
import com.kntro.reqsai.workspace.application.port.InvitationRepository;
import com.kntro.reqsai.workspace.application.port.MemberRepository;
import com.kntro.reqsai.workspace.domain.model.Invitation;
import com.kntro.reqsai.workspace.domain.model.InvitationStatus;
import com.kntro.reqsai.workspace.domain.model.Member;
import com.kntro.reqsai.workspace.domain.model.MemberStatus;
import com.kntro.reqsai.workspace.domain.model.OrgRole;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Application: Invitation link-on-signup listener")
@ExtendWith(MockitoExtension.class)
class InvitationLinkOnSignupListenerTest {

    @Mock
    private InvitationRepository invitations;
    @Mock
    private MemberRepository members;
    @Mock
    private AccountLookupPort accountLookup;
    @InjectMocks
    private InvitationLinkOnSignupListener listener;

    private Invitation pendingInvitation(UUID memberId, String email) {
        return Invitation.issue(UUID.randomUUID(), "Acme", memberId, email, "Invitee", OrgRole.MEMBER,
                "raw", UUID.randomUUID(), "Owner", Instant.now().plus(1, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("verifying an invited email auto-accepts the invitation and activates the member")
    void auto_accepts_on_verification() {
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Invitation invitation = pendingInvitation(memberId, "invitee@example.com");
        Member member = new Member(invitation.getOrganizationId(), null, "invitee@example.com", "Invitee",
                OrgRole.MEMBER, MemberStatus.PENDING, UUID.randomUUID(), Instant.now());

        when(invitations.findAllByEmailIgnoreCaseAndStatus("invitee@example.com", InvitationStatus.PENDING))
                .thenReturn(List.of(invitation));
        when(accountLookup.findUserIdByAccountId(accountId)).thenReturn(Optional.of(userId));
        when(members.findByIdAndOrganizationIdAndStatusIn(any(), any(), any())).thenReturn(Optional.of(member));

        listener.onAccountVerified(new AccountVerifiedIntegrationEvent(accountId, "invitee@example.com", Instant.now()));

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getUserId()).isEqualTo(userId);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        verify(members).save(member);
        verify(invitations).save(invitation);
    }

    @Test
    @DisplayName("no pending invitations for the verified email is a no-op")
    void no_pending_is_noop() {
        UUID accountId = UUID.randomUUID();
        when(invitations.findAllByEmailIgnoreCaseAndStatus("nobody@example.com", InvitationStatus.PENDING))
                .thenReturn(List.of());

        listener.onAccountVerified(new AccountVerifiedIntegrationEvent(accountId, "nobody@example.com", Instant.now()));

        verify(accountLookup, never()).findUserIdByAccountId(any());
        verify(members, never()).save(any());
    }
}
