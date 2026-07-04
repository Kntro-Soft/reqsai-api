package com.kntro.reqsai.workspace.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when an already-ACTIVE organization member is assigned <strong>directly</strong> to a project
 * (the {@code CreateProjectMemberCommandHandler} path), so a notification email can tell them they were
 * added. It is deliberately <em>not</em> raised from {@code ProjectAssignmentMaterializer} (the
 * accept-a-project-invitation path): those recipients already learned about the project from the
 * invitation email and must not get a duplicate notification.
 * <p>
 * This is an <strong>application</strong> event, not a domain event: it carries the already-resolved
 * project and role <em>names</em> (which the aggregate itself does not hold) plus the recipient email, so
 * the notification listener needs no further lookups. Published with {@code ApplicationEventPublisher};
 * the listener consumes it with {@code @ApplicationModuleListener} (Modulith AFTER_COMMIT + event
 * publication registry), so the email is sent only once the assignment has committed and is never lost.
 *
 * @param organizationId the organization owning the project
 * @param projectId      the project the member was added to (used to build the deep link)
 * @param projectName    resolved project name for the email body
 * @param memberId       the assigned organization member
 * @param roleId         the project-role granted
 * @param roleName       resolved project-role name for the email body
 * @param recipientEmail the member's email address (recipient)
 * @param recipientName  the member's display name (greeting)
 * @param occurredAt     when the assignment happened
 */
public record ProjectMemberAssignedEvent(
        UUID organizationId,
        UUID projectId,
        String projectName,
        UUID memberId,
        UUID roleId,
        String roleName,
        String recipientEmail,
        String recipientName,
        Instant occurredAt) {

    public static ProjectMemberAssignedEvent of(
            UUID organizationId,
            UUID projectId,
            String projectName,
            UUID memberId,
            UUID roleId,
            String roleName,
            String recipientEmail,
            String recipientName) {
        return new ProjectMemberAssignedEvent(organizationId, projectId, projectName, memberId, roleId,
                roleName, recipientEmail, recipientName, Instant.now());
    }
}
