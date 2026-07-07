package com.kntro.reqsai.workspace.application.result;

import org.jspecify.annotations.Nullable;

/**
 * Minimal, non-sensitive invitation view for the public accept/signup screen.
 *
 * @param organizationName the organization the invitee is joining
 * @param role             the org role being granted
 * @param email            the invited email (so the signup form can prefill / lock it)
 * @param invitedByName    the inviter's display name, when known
 * @param status           the invitation status ({@code PENDING}/{@code ACCEPTED}/…)
 * @param expired          {@code true} when a still-pending invitation is past its expiry
 */
public record InvitationDetails(
        String organizationName,
        String role,
        String email,
        @Nullable String invitedByName,
        String status,
        boolean expired) {
}
