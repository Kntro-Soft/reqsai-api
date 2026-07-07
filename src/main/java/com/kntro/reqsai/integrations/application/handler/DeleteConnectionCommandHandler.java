package com.kntro.reqsai.integrations.application.handler;

import com.kntro.reqsai.integrations.application.command.DeleteConnectionCommand;
import com.kntro.reqsai.integrations.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.integrations.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.integrations.domain.model.IntegrationConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes an organization connection. Project targets referencing it are removed by the FK
 * {@code ON DELETE CASCADE}.
 */
@Component
@RequiredArgsConstructor
public class DeleteConnectionCommandHandler {

    private final IntegrationConnectionRepository connections;

    @Transactional
    public void handle(DeleteConnectionCommand command) {
        IntegrationConnection connection = connections
                .findByIdAndOrganizationId(command.connectionId(), command.organizationId())
                .orElseThrow(() -> IntegrationsExceptions.connectionNotFound(command.connectionId()));
        connections.delete(connection);
    }
}
