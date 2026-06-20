package com.kntro.reqsai.iam.application.command;

import java.util.UUID;

/**
 * Accepts the Terms and Conditions for the given user's linked account.
 *
 * @param userId       the authenticated user's id (from the JWT {@code sub} claim)
 * @param termsVersion the version string of the T&C being accepted (e.g. {@code "2026-01"})
 */
public record AcceptTermsCommand(UUID userId, String termsVersion) {
}
