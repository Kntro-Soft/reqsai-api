package com.kntro.reqsai.workspace.domain.model;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;
import com.kntro.reqsai.shared.domain.support.Assert;
import com.kntro.reqsai.shared.domain.support.HashUtils;
import com.kntro.reqsai.shared.domain.valueobjects.Email;
import com.kntro.reqsai.workspace.domain.event.MemberInvitedEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Tokenized organization invitation bound to a PENDING {@link Member}. Lives in the global
 * {@code public.invitations} registry (like {@code members}). The raw token is never persisted — only
 * its SHA-256 hex digest is stored; the raw value travels solely in {@link MemberInvitedEvent} for the
 * email listener.
 * <p>
 * The invitation carries the acceptance lifecycle for its member: issued {@code PENDING} → consumed via
 * {@link #markAccepted(Instant)}, or invalidated via {@link #markExpired(Instant)} / {@link #revoke()} /
 * {@link #supersede()}. Only one {@code PENDING} invitation exists per member at a time (resend
 * supersedes the previous one).
 */
@Entity
@Table(name = "invitations", schema = "public")
@Getter
public class Invitation extends AggregateRoot {

    @Column(name = "organization_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "member_id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID memberId;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private OrgRole role;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private InvitationStatus status;

    @Column(name = "invited_by", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID invitedBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private @Nullable Instant acceptedAt;

    protected Invitation() {
        super();
    }

    private Invitation(
            UUID organizationId,
            UUID memberId,
            String email,
            OrgRole role,
            String tokenHash,
            UUID invitedBy,
            Instant expiresAt) {
        super();
        this.organizationId = Assert.notNull(organizationId, "organizationId");
        this.memberId = Assert.notNull(memberId, "memberId");
        this.email = Email.of(email).value();
        this.role = Assert.notNull(role, "role");
        this.tokenHash = Assert.notBlank(tokenHash, "tokenHash");
        this.status = InvitationStatus.PENDING;
        this.invitedBy = Assert.notNull(invitedBy, "invitedBy");
        this.expiresAt = Assert.notNull(expiresAt, "expiresAt");
    }

    /**
     * Issues a new PENDING invitation, storing only the hash of {@code rawToken}, and registers a
     * {@link MemberInvitedEvent} so the email listener can send the acceptance link after commit.
     *
     * @param rawToken         the unhashed token — never persisted, only carried in the event
     * @param organizationName organization name for the email body (not stored)
     * @param displayName      invitee display name for the email greeting (not stored)
     * @param invitedByName    inviter display name for the email, when known (not stored)
     */
    public static Invitation issue(
            UUID organizationId,
            String organizationName,
            UUID memberId,
            String email,
            String displayName,
            OrgRole role,
            String rawToken,
            UUID invitedBy,
            @Nullable String invitedByName,
            Instant expiresAt) {
        Invitation invitation = new Invitation(
                organizationId, memberId, email, role, HashUtils.sha256(rawToken), invitedBy, expiresAt);
        invitation.registerEvent(MemberInvitedEvent.of(
                invitation.getId(), organizationId, organizationName, invitation.email,
                displayName, role.name(), rawToken, invitedByName));
        return invitation;
    }

    /** {@code true} while the invitation is still PENDING and not past its expiry. */
    public boolean isValid(Instant now) {
        return status == InvitationStatus.PENDING && expiresAt.isAfter(now);
    }

    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    public boolean isAccepted() {
        return status == InvitationStatus.ACCEPTED;
    }

    /** Marks the invitation ACCEPTED (idempotent — no-op if already accepted). */
    public void markAccepted(Instant now) {
        if (status == InvitationStatus.ACCEPTED) {
            return;
        }
        this.status = InvitationStatus.ACCEPTED;
        this.acceptedAt = Assert.notNull(now, "now");
    }

    /** Marks a still-pending invitation EXPIRED. */
    public void markExpired(Instant now) {
        if (status == InvitationStatus.PENDING) {
            this.status = InvitationStatus.EXPIRED;
        }
    }

    /** Marks the invitation REVOKED (its pending member was removed). */
    public void revoke() {
        if (status == InvitationStatus.PENDING) {
            this.status = InvitationStatus.REVOKED;
        }
    }

    /** Marks the invitation SUPERSEDED (a newer invitation was issued for the same member). */
    public void supersede() {
        if (status == InvitationStatus.PENDING) {
            this.status = InvitationStatus.SUPERSEDED;
        }
    }
}
