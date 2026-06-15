package com.kntro.reqsai.discovery.interfaces.rest.mappers.request;

import com.kntro.reqsai.discovery.application.command.CreateDiscoverySessionCommand;
import com.kntro.reqsai.discovery.interfaces.rest.dto.request.CreateDiscoverySessionRequest;

import java.util.UUID;

/** Maps inbound discovery-session request DTOs to application commands. */
public final class DiscoverySessionRequestMapper {

    private DiscoverySessionRequestMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static CreateDiscoverySessionCommand toCommand(UUID projectId, CreateDiscoverySessionRequest request) {
        return new CreateDiscoverySessionCommand(projectId, request.title(), request.language());
    }
}
