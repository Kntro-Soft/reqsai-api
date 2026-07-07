package com.kntro.reqsai.workspace.application.command;

import java.util.UUID;

/**
 * Accepts a pending invitation. The caller proves both possession of the token and ownership of the
 * invited email (the accept handler resolves the caller's account email and requires an exact,
 * case-insensitive match).
 *
 * @param rawToken  the raw invitation token from the acceptance link
 * @param callerId  the authenticated user id (becomes the member's linked {@code userId})
 */
public record AcceptInvitationCommand(String rawToken, UUID callerId) {
}
