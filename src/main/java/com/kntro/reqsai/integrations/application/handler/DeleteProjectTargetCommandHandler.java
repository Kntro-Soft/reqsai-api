package com.kntro.reqsai.integrations.application.handler;

import com.kntro.reqsai.integrations.application.command.DeleteProjectTargetCommand;
import com.kntro.reqsai.integrations.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.integrations.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.integrations.domain.model.ProjectIntegrationTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Deletes a project's Jira target (404 when none is configured). */
@Component
@RequiredArgsConstructor
public class DeleteProjectTargetCommandHandler {

    private final ProjectIntegrationTargetRepository targets;

    @Transactional
    public void handle(DeleteProjectTargetCommand command) {
        ProjectIntegrationTarget target = targets.findByProjectId(command.projectId())
                .orElseThrow(() -> IntegrationsExceptions.targetNotFound(command.projectId()));
        targets.delete(target);
    }
}
