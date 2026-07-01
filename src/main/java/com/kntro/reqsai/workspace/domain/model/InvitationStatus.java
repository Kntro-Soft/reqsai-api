package com.kntro.reqsai.workspace.domain.model;

/**
 * Lifecycle of an organization {@link Invitation}.
 * <p>
 * {@code PENDING} → {@code ACCEPTED} (invitee joined), {@code EXPIRED} (TTL elapsed before accept),
 * {@code REVOKED} (the pending member was removed) or {@code SUPERSEDED} (a newer invitation was
 * issued for the same member via resend). Only one {@code PENDING} invitation exists per member at
 * any time.
 */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    EXPIRED,
    REVOKED,
    SUPERSEDED
}
