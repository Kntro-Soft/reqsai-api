package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.DiscoverySession;

/**
 * Persistence port for the {@link DiscoverySession} aggregate. Tenant-scoped: the active schema is set
 * per request from the JWT {@code orgId}.
 */
public interface DiscoverySessionRepository {

    DiscoverySession save(DiscoverySession session);
}
