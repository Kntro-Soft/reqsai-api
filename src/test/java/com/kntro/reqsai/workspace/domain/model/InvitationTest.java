package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.support.HashUtils;
import com.kntro.reqsai.testsupport.AggregateEvents;
import com.kntro.reqsai.workspace.domain.event.MemberInvitedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Domain: Invitation Aggregate")
class InvitationTest {

    private Invitation issue(Instant expiresAt) {
        return Invitation.issue(
                UUID.randomUUID(), "Acme", UUID.randomUUID(), "invitee@example.com", "Invitee",
                OrgRole.MEMBER, "raw-token", UUID.randomUUID(), "Owner Name", expiresAt);
    }

    @Test
    @DisplayName("issue stores only the token hash and registers a MemberInvitedEvent carrying the raw token")
    void issue_hashes_token_and_registers_event() {
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        Invitation invitation = issue(expiresAt);

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(invitation.getTokenHash()).isEqualTo(HashUtils.sha256("raw-token"));
        assertThat(invitation.getTokenHash()).doesNotContain("raw-token");
        assertThat(invitation.getEmail()).isEqualTo("invitee@example.com");

        MemberInvitedEvent event = (MemberInvitedEvent) AggregateEvents.of(invitation).getFirst();
        assertThat(event.rawToken()).isEqualTo("raw-token");
        assertThat(event.organizationName()).isEqualTo("Acme");
        assertThat(event.invitedByName()).isEqualTo("Owner Name");
    }

    @Test
    @DisplayName("isValid is true only while pending and unexpired")
    void isValid_reflects_status_and_expiry() {
        Instant now = Instant.now();
        Invitation valid = issue(now.plus(1, ChronoUnit.DAYS));
        assertThat(valid.isValid(now)).isTrue();

        Invitation expired = issue(now.minus(1, ChronoUnit.DAYS));
        assertThat(expired.isValid(now)).isFalse();
    }

    @Test
    @DisplayName("markAccepted is idempotent")
    void markAccepted_is_idempotent() {
        Instant now = Instant.now();
        Invitation invitation = issue(now.plus(1, ChronoUnit.DAYS));

        invitation.markAccepted(now);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(invitation.getAcceptedAt()).isEqualTo(now);

        invitation.markAccepted(now.plusSeconds(10));
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(invitation.getAcceptedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("revoke, supersede and markExpired only transition a pending invitation")
    void terminal_transitions_only_from_pending() {
        Instant now = Instant.now();

        Invitation revoked = issue(now.plus(1, ChronoUnit.DAYS));
        revoked.revoke();
        assertThat(revoked.getStatus()).isEqualTo(InvitationStatus.REVOKED);

        Invitation superseded = issue(now.plus(1, ChronoUnit.DAYS));
        superseded.supersede();
        assertThat(superseded.getStatus()).isEqualTo(InvitationStatus.SUPERSEDED);

        Invitation expired = issue(now.plus(1, ChronoUnit.DAYS));
        expired.markExpired(now);
        assertThat(expired.getStatus()).isEqualTo(InvitationStatus.EXPIRED);

        // an accepted invitation is not clobbered by a later revoke
        Invitation accepted = issue(now.plus(1, ChronoUnit.DAYS));
        accepted.markAccepted(now);
        accepted.revoke();
        assertThat(accepted.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
    }
}
