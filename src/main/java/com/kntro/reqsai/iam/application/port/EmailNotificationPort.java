package com.kntro.reqsai.iam.application.port;

/**
 * Port for sending transactional email notifications. Implemented in {@code infrastructure} by an
 * {@code EmailRouter} that delegates to the provider selected via {@code EMAIL_PROVIDER}.
 */
public interface EmailNotificationPort {
    void sendVerificationEmail(String toEmail, String firstName, String rawToken);

    void sendPasswordResetEmail(String toEmail, String firstName, String rawToken);

    /**
     * Sends an organization-invitation email with a tokenized acceptance link.
     *
     * @param toEmail          the invitee's email address
     * @param displayName      the invitee's display name (greeting)
     * @param organizationName the organization the invitee is being invited to
     * @param role             the org role being granted
     * @param invitedByName    the inviter's name, or {@code null} when unknown
     * @param rawToken         the unhashed invitation token, placed in the acceptance link
     */
    void sendInvitationEmail(String toEmail, String displayName, String organizationName, String role,
                             String invitedByName, String rawToken);
}
