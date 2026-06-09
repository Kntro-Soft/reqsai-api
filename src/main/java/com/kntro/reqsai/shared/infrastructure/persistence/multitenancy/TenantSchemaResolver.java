package com.kntro.reqsai.shared.infrastructure.persistence.multitenancy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Maps a tenant id (the JWT {@code orgId} claim) to its PostgreSQL schema name.
 * <p>
 * Looks up the organization's {@code slug} in the global {@code public.organizations} registry and
 * builds {@code tenant_<slug>}. Results are cached (Caffeine, {@code tenantSchemas}) since the
 * mapping is stable for the lifetime of an organization. Falls back to {@code public} on any miss
 * or error so a bad token never crosses into another tenant's data.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantSchemaResolver {

    /** Prefix applied to every tenant schema: {@code tenant_<slug>}. */
    public static final String SCHEMA_PREFIX = "tenant_";

    private final JdbcTemplate jdbcTemplate;

    @Cacheable(value = "tenantSchemas", key = "#tenantId")
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
}
