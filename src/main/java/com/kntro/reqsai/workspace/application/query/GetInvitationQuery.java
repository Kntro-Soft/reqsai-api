package com.kntro.reqsai.workspace.application.query;

/**
 * Public lookup of an invitation by its raw token, for the accept/signup screen.
 *
 * @param rawToken the raw invitation token from the acceptance link
 */
public record GetInvitationQuery(String rawToken) {
}
