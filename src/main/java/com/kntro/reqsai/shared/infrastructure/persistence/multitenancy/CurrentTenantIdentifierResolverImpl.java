package com.kntro.reqsai.shared.infrastructure.persistence.multitenancy;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.hibernate.cfg.MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER;

/**
 * Tells Hibernate which schema the current session belongs to.
 * <p>
 * Returns the schema stored in {@link TenantContext}, falling back to
 * {@link TenantContext#DEFAULT_SCHEMA "public"} when no tenant is bound (startup, global data,
 * unauthenticated requests). Self-registers via {@link HibernatePropertiesCustomizer}.
 */
@Component
@Slf4j
public class CurrentTenantIdentifierResolverImpl implements CurrentTenantIdentifierResolver<String>, HibernatePropertiesCustomizer {

    @Override
    public String resolveCurrentTenantIdentifier() {
        String schema = TenantContext.getCurrentSchema();
        if (schema == null) {
            return TenantContext.DEFAULT_SCHEMA;
        }
        log.trace("Resolved current tenant schema: {}", schema);
        return schema;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
