package com.kntro.reqsai.workspace.application.listener;

import com.kntro.reqsai.iam.application.port.EmailNotificationPort;
import com.kntro.reqsai.workspace.domain.event.MemberInvitedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Sends the organization-invitation email when an {@link Invitation} is issued.
 * <p>
 * Reacts to {@link MemberInvitedEvent} (raised by the workspace {@code Invitation} aggregate) and
 * delegates to the IAM {@link EmailNotificationPort}. The port lives in IAM's {@code ports} named
 * interface, which the workspace module already depends on, so the cross-module call keeps
 * {@code verifyModularity} green without workspace touching IAM internals. Runs after commit in its
 * own transaction (Spring Modulith), so a mail-provider hiccup never rolls back the invite.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class InvitationEmailListener {

    private final EmailNotificationPort emailNotification;

    @ApplicationModuleListener
    void onMemberInvited(MemberInvitedEvent event) {
        emailNotification.sendInvitationEmail(
                event.email(),
                event.displayName(),
                event.organizationName(),
                event.role(),
                event.invitedByName(),
                event.rawToken());
        log.info("Invitation email sent for invitation {} (org {})", event.invitationId(), event.organizationId());
    }
}
