package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.gateway.application.command.SaveProjectTargetCommand;
import com.kntro.reqsai.gateway.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.gateway.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.gateway.domain.model.ProjectIntegrationTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates or replaces the single Jira push target of a project (upsert). Validates the referenced
 * connection exists in the tenant ({@code INTEGRATION_CONNECTION_NOT_FOUND} otherwise).
 */
@Component
@RequiredArgsConstructor
public class SaveProjectTargetCommandHandler {

    private final ProjectIntegrationTargetRepository targets;
    private final IntegrationConnectionRepository connections;

    @Transactional
    public ProjectIntegrationTarget handle(SaveProjectTargetCommand command) {
        connections.findById(command.connectionId())
                .orElseThrow(() -> IntegrationsExceptions.connectionNotFound(command.connectionId()));

        ProjectIntegrationTarget target = targets.findByProjectId(command.projectId())
                .map(existing -> {
                    existing.update(command.connectionId(), command.jiraProjectKey(), command.issueTypeName());
                    return existing;
                })
                .orElseGet(() -> new ProjectIntegrationTarget(
                        command.projectId(), command.connectionId(),
                        command.jiraProjectKey(), command.issueTypeName()));
        return targets.save(target);
    }
}
