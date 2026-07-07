package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.gateway.application.port.ProjectIntegrationTargetRepository;
import com.kntro.reqsai.gateway.application.query.GetProjectTargetQuery;
import com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.gateway.domain.model.ProjectIntegrationTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reads a project's Jira target, 404 ({@code INTEGRATION_CONNECTION_NOT_FOUND}) when none is set. */
@Component
@RequiredArgsConstructor
public class GetProjectTargetQueryHandler {

    private final ProjectIntegrationTargetRepository targets;

    @Transactional(readOnly = true)
    public ProjectIntegrationTarget handle(GetProjectTargetQuery query) {
        return targets.findByProjectId(query.projectId())
                .orElseThrow(() -> IntegrationsExceptions.targetNotFound(query.projectId()));
    }
}
