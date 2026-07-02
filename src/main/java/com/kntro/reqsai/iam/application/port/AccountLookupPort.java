package com.kntro.reqsai.iam.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Cross-module lookup exposed by the IAM context so other modules can resolve an authenticated
 * caller's account email without reaching into IAM internals.
 * <p>
 * The inverse of {@code OrganizationLookupPort} (which the workspace implements for IAM): here IAM
 * both declares and implements the port, and the workspace consumes it (e.g. to enforce that an
 * invitation is accepted only by the account whose email was invited). Declared in the {@code ports}
 * named interface so Spring Modulith allows the cross-module dependency.
 */
public interface AccountLookupPort {

    /**
     * Resolves the email of the account behind a user id (the JWT {@code sub}).
     *
     * @param userId the authenticated user id
     * @return the normalized account email, or empty when the user/account is unknown
     */
    Optional<String> findEmailByUserId(UUID userId);

    /**
     * Resolves the user id (JWT {@code sub}, used as {@code Member.userId}/{@code Organization.ownerId})
     * for an account. Needed by the invitation link-on-signup listener, which only has the account id
     * from {@code AccountVerifiedEvent} but must link the member to the user id.
     *
     * @param accountId the account id
     * @return the linked user id, or empty when no profile exists yet
     */
    Optional<UUID> findUserIdByAccountId(UUID accountId);

    /**
     * Resolves the display profile (email + full name) behind a user id. Used by the workspace to
     * render the organization owner in the member roster: the owner is implicit (no member row), so
     * its profile is read here rather than denormalized on a member.
     *
     * @param userId the user id (e.g. {@code Organization.ownerId})
     * @return the owner's email and display name, or empty when the user/account is unknown
     */
    Optional<UserProfile> findProfileByUserId(UUID userId);

    /** A user's public-facing identity, resolved across the account (email) and user (name) aggregates. */
    record UserProfile(String email, String displayName) {}
}
