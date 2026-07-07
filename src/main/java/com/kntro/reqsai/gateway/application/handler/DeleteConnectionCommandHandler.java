package com.kntro.reqsai.gateway.application.handler;

import com.kntro.reqsai.gateway.application.command.DeleteConnectionCommand;
import com.kntro.reqsai.gateway.application.port.IntegrationConnectionRepository;
import com.kntro.reqsai.gateway.domain.exception.IntegrationsExceptions;
import com.kntro.reqsai.gateway.domain.model.IntegrationConnection;
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
