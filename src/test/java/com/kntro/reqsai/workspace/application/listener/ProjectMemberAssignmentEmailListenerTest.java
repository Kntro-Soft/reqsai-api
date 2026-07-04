package com.kntro.reqsai.workspace.application.listener;

import com.kntro.reqsai.iam.application.port.EmailNotificationPort;
import com.kntro.reqsai.workspace.application.event.ProjectMemberAssignedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@DisplayName("Application: Project-member assignment email listener")
@ExtendWith(MockitoExtension.class)
class ProjectMemberAssignmentEmailListenerTest {

    @Mock
    private EmailNotificationPort emailNotification;
    @InjectMocks
    private ProjectMemberAssignmentEmailListener listener;

    @Test
    @DisplayName("assignment event -> sends the project-assignment notification with the project id as link target")
    void sends_project_assignment_notification() {
        UUID projectId = UUID.randomUUID();
        ProjectMemberAssignedEvent event = ProjectMemberAssignedEvent.of(
                UUID.randomUUID(), projectId, "Apollo", UUID.randomUUID(), UUID.randomUUID(), "Analyst",
                "member@example.com", "Member");

        listener.onProjectMemberAssigned(event);

        verify(emailNotification).sendProjectAssignmentEmail(
                "member@example.com", "Member", "Apollo", "Analyst", projectId.toString());
    }
}
