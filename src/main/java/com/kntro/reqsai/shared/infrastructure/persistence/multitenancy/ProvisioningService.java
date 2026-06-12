package com.kntro.reqsai.shared.infrastructure.persistence.multitenancy;

/**
 * Provisions and migrates per-tenant PostgreSQL schemas.
 * <p>
 * Decoupled from any domain entity: callers pass the organization {@code slug}. The workspace BC
 * invokes {@link #provisionTenant(String)} right after an organization is activated.
 */
public interface ProvisioningService {

    /**
     * Creates {@code tenant_<slug>} and runs the {@code db/migration/tenant} migrations against it.
     * Rolls back (drops the schema) if anything fails.
     *
     * @param slug organization slug (lowercase, unique)
     */
    void provisionTenant(String slug);

    /**
     * Applies pending {@code db/migration/tenant} migrations to an already-provisioned schema.
     *
     * @param slug organization slug
     */
    void migrateExistingTenant(String slug);
}
