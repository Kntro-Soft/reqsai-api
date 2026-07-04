package com.kntro.reqsai.workspace.application.listener;

import com.kntro.reqsai.iam.application.port.EmailNotificationPort;
import com.kntro.reqsai.workspace.domain.event.MemberInvitedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("Application: Invitation email listener")
@ExtendWith(MockitoExtension.class)
class InvitationEmailListenerTest {

    @Mock
    private EmailNotificationPort emailNotification;
    @InjectMocks
    private InvitationEmailListener listener;

    @Test
    @DisplayName("org-only invitation -> sends the plain invitation email")
    void org_only_sends_plain_invitation_email() {
        MemberInvitedEvent event = MemberInvitedEvent.of(
                UUID.randomUUID(), UUID.randomUUID(), "Acme", "invitee@example.com", "Invitee",
                "MEMBER", "raw-token", "Owner");

        listener.onMemberInvited(event);

        verify(emailNotification).sendInvitationEmail(
                eq("invitee@example.com"), eq("Invitee"), eq("Acme"), eq("MEMBER"), eq("Owner"), eq("raw-token"));
        verify(emailNotification, never()).sendProjectInvitationEmail(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("project-scoped invitation -> sends the project invitation email with project and role names")
    void project_scoped_sends_project_invitation_email() {
        MemberInvitedEvent event = MemberInvitedEvent.of(
                UUID.randomUUID(), UUID.randomUUID(), "Acme", "invitee@example.com", "Invitee",
                "MEMBER", "raw-token", "Owner", "Apollo", "Analyst");

        listener.onMemberInvited(event);

        verify(emailNotification).sendProjectInvitationEmail(
                eq("invitee@example.com"), eq("Invitee"), eq("Acme"), eq("MEMBER"),
                eq("Apollo"), eq("Analyst"), eq("Owner"), eq("raw-token"));
        verify(emailNotification, never()).sendInvitationEmail(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
