package com.kntro.reqsai.workspace.domain.event;

import com.kntro.reqsai.shared.domain.model.DomainEvent;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when an {@link com.kntro.reqsai.workspace.domain.model.Invitation} is issued (on invite or
 * resend). Carries the <strong>raw</strong> invitation token — for the email listener only, never
 * persisted — so the acceptance link can be built. A workspace listener sends the invitation email.
 *
 * @param invitationId   id of the invitation aggregate that raised the event
 * @param organizationId the organization the invitee is joining
 * @param organizationName human-readable organization name (for the email body)
 * @param email          the invitee's email address (recipient)
 * @param displayName    the invitee's display name (greeting)
 * @param role           the org role being granted
 * @param rawToken       the unhashed token — placed in the acceptance link, never stored
 * @param invitedByName  display name of the inviter, when known
 */
public record MemberInvitedEvent(
        UUID invitationId,
        UUID organizationId,
        String organizationName,
        String email,
        String displayName,
        String role,
        String rawToken,
        @Nullable String invitedByName,
        Instant occurredAt) implements DomainEvent {

    public static MemberInvitedEvent of(
            UUID invitationId,
            UUID organizationId,
            String organizationName,
            String email,
            String displayName,
            String role,
            String rawToken,
            @Nullable String invitedByName) {
        return new MemberInvitedEvent(invitationId, organizationId, organizationName, email, displayName,
                role, rawToken, invitedByName, Instant.now());
    }

    @Override
    public UUID aggregateId() {
        return invitationId;
    }
}
