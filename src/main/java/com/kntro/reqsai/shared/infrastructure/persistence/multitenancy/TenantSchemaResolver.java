package com.kntro.reqsai.shared.infrastructure.persistence.multitenancy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Maps a tenant id (the JWT {@code orgId} claim) to its PostgreSQL schema name.
 * <p>
 * Looks up the organization's {@code slug} in the global {@code public.organizations} registry and
 * builds {@code tenant_<slug>}, falling back to {@code public} on any miss/error so a bad token never
 * crosses into another tenant's data.
 * <p>
 * <strong>Caching.</strong> Successful resolutions are cached (Caffeine, {@code tenantSchemas}) because
 * the {@code id → slug} mapping is stable for an organization's lifetime; this avoids a DB hit on every
 * request. Spring does <em>not</em> invalidate on writes, so:
 * <ul>
 *   <li><strong>New organization:</strong> nothing to do — its id was never queried, so it's a cache
 *       miss that hits the DB.</li>
 *   <li><strong>Fallback is never cached</strong> ({@code unless}): a {@code PENDING}/unknown tenant
 *       resolves to {@code public} but isn't stored, so once it's activated the next call re-queries
 *       and picks up the real schema automatically — no eviction needed.</li>
 *   <li><strong>Slug change / deactivation</strong> (rare): the {@code workspace} context must evict
 *       the entry — {@code @CacheEvict(value = "tenantSchemas", key = "#orgId")} — on those events.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantSchemaResolver {

    /** Prefix applied to every tenant schema: {@code tenant_<slug>}. */
    public static final String SCHEMA_PREFIX = "tenant_";

    private final JdbcTemplate jdbcTemplate;

    @Cacheable(
            value = "tenantSchemas",
            key = "#tenantId",
            unless = "#result == T(com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantContext).DEFAULT_SCHEMA")
    public String resolveTenantSchema(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return TenantContext.DEFAULT_SCHEMA;
        }
        try {
            String slug = jdbcTemplate.queryForObject(
                    "SELECT slug FROM public.organizations WHERE id = ?::uuid AND status <> 'PENDING'",
                    String.class,
                    tenantId);
            if (slug != null) {
                String schema = SCHEMA_PREFIX + slug.toLowerCase();
                log.debug("Resolved schema {} for tenant {}", schema, tenantId);
                return schema;
            }
        } catch (Exception e) {
            log.error("Error resolving schema for tenant {} — defaulting to public", tenantId, e);
        }
        log.warn("No provisioned schema for tenant {} — defaulting to public", tenantId);
        return TenantContext.DEFAULT_SCHEMA;
    }

    /**
     * Evicts the cached {@code id → schema} mapping for a tenant. Must be called after slug changes,
     * deactivation or deletion so a stale schema is never resolved for a tenant whose schema changed.
     */
    @CacheEvict(value = "tenantSchemas", key = "#tenantId")
    public void evictTenantSchema(String tenantId) {
        log.debug("Evicted cached schema mapping for tenant {}", tenantId);
    }
}
