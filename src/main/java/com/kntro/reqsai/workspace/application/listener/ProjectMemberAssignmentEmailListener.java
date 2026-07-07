package com.kntro.reqsai.workspace.application.listener;

import com.kntro.reqsai.iam.application.port.EmailNotificationPort;
import com.kntro.reqsai.workspace.application.event.ProjectMemberAssignedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Sends the "you were added to a project" notification when an already-active member is assigned directly
 * to a project (the {@code CreateProjectMemberCommandHandler} path).
 * <p>
 * Reacts to {@link ProjectMemberAssignedEvent} and delegates to the IAM {@link EmailNotificationPort}
 * (the {@code ports} named interface the workspace already depends on, so the cross-module call keeps
 * {@code verifyModularity} green). Runs after commit in its own transaction (Spring Modulith), so a
 * mail-provider hiccup never rolls back the assignment. This notification is only produced for the
 * direct-assignment path — accepting a project invitation does not raise this event, so those members
 * never get a duplicate of the project-invitation email they already received.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class ProjectMemberAssignmentEmailListener {

    private final EmailNotificationPort emailNotification;

    @ApplicationModuleListener
    void onProjectMemberAssigned(ProjectMemberAssignedEvent event) {
        emailNotification.sendProjectAssignmentEmail(
                event.recipientEmail(),
                event.recipientName(),
                event.projectName(),
                event.roleName(),
                event.projectId().toString());
        log.info("Project-assignment email sent for member {} (project {})", event.memberId(), event.projectId());
    }
}
