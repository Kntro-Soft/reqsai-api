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

    /**
     * Sends an invitation email for a person invited to an organization <em>and</em> assigned to a
     * project in the same act. Unlike {@link #sendInvitationEmail}, it also tells the recipient which
     * project and project-role they will get on accept, so they are not surprised after joining.
     *
     * @param toEmail          the invitee's email address
     * @param displayName      the invitee's display name (greeting)
     * @param organizationName the organization the invitee is being invited to
     * @param role             the org role being granted
     * @param projectName      the project the invitee is being assigned to on accept
     * @param projectRoleName  the project-role granted on accept
     * @param invitedByName    the inviter's name, or {@code null} when unknown
     * @param rawToken         the unhashed invitation token, placed in the acceptance link
     */
    void sendProjectInvitationEmail(String toEmail, String displayName, String organizationName, String role,
                                    String projectName, String projectRoleName, String invitedByName,
                                    String rawToken);

    /**
     * Sends a notification email to an <em>already-active</em> organization member telling them they were
     * added to a project. This is not an accept flow — there is no token; the link is a deep link to the
     * project.
     *
     * @param toEmail         the member's email address
     * @param displayName     the member's display name (greeting)
     * @param projectName     the project the member was added to
     * @param projectRoleName the project-role granted
     * @param projectId       the project id, used to build the deep link
     */
    void sendProjectAssignmentEmail(String toEmail, String displayName, String projectName,
                                    String projectRoleName, String projectId);
}
