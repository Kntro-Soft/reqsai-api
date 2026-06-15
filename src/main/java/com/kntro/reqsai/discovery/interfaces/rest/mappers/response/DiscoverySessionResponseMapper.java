package com.kntro.reqsai.discovery.interfaces.rest.mappers.response;

import com.kntro.reqsai.discovery.domain.model.DiscoverySession;
import com.kntro.reqsai.discovery.interfaces.rest.dto.response.DiscoverySessionResponse;

/** Maps the {@link DiscoverySession} aggregate to its response DTO. */
public final class DiscoverySessionResponseMapper {

    private DiscoverySessionResponseMapper() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static DiscoverySessionResponse toResponse(DiscoverySession session) {
        return new DiscoverySessionResponse(
                session.getId(),
                session.getProjectId(),
                session.getTitle(),
                session.getLanguage().value(),
                session.getStatus().name());
    }
}
