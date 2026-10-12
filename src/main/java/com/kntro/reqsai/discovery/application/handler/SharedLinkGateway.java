package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.application.port.ShareLinkRepository;
import com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions;
import com.kntro.reqsai.discovery.domain.model.ShareLink;
import com.kntro.reqsai.shared.domain.support.HashUtils;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext;
import com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantSchemaResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Entry point for anonymous share-link requests. The visitor has no JWT to derive the tenant from, so
 * the link found by its token hash (in the global registry) names the organization; the work then runs
 * bound to that organization's schema, like the public avatar endpoints do. Unknown, revoked and expired
 * links all answer the same {@code SHARE_LINK_UNAVAILABLE}, so a guesser learns nothing.
 */
@Component
@RequiredArgsConstructor
public class SharedLinkGateway {

    private final ShareLinkRepository links;
    private final TenantSchemaResolver tenantSchemaResolver;

    /** Runs {@code work} for an active link that shares a project's backlog. */
    public <T> T withActiveLink(String token, Function<ShareLink, T> work) {
        return run(token, false, work);
    }

    /** Runs {@code work} for an active link that follows a discovery session. */
    public <T> T withActiveSessionLink(String token, Function<ShareLink, T> work) {
        return run(token, true, work);
    }

    private <T> T run(String token, boolean sessionLink, Function<ShareLink, T> work) {
        ShareLink link = (token == null || token.isBlank() ? null
                : links.findByTokenHash(HashUtils.sha256(token)).orElse(null));
        if (link == null || !link.isActive(Instant.now()) || link.isSessionLink() != sessionLink) {
            throw DiscoveryExceptions.shareLinkUnavailable();
        }
        String tenantId = link.getOrganizationId().toString();
        AtomicReference<T> result = new AtomicReference<>();
        TenantContext.runWith(
                new TenantContext.TenantSnapshot(tenantId, tenantSchemaResolver.resolveTenantSchema(tenantId)),
                () -> result.set(work.apply(link)));
        return result.get();
    }
}
