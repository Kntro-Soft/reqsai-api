package com.kntro.reqsai.shared.infrastructure.persistence.multitenancy;

/**
 * Thread-local holder for the current tenant and its PostgreSQL schema.
 * <p>
 * Each HTTP request is handled by a dedicated thread; the {@link ThreadLocal} keeps tenant state
 * isolated per request even under concurrency.
 * <p>
 * Lifecycle (see {@code JwtAuthenticationFilter}):
 * <ol>
 *   <li>The filter extracts the tenant id (JWT {@code orgId} claim) and resolves its schema.</li>
 *   <li>It calls {@link #setCurrentTenant} / {@link #setCurrentSchema}.</li>
 *   <li>Hibernate's {@code CurrentTenantIdentifierResolver} reads the schema when opening a session.</li>
 *   <li>The filter calls {@link #clear()} in a {@code finally} block — <strong>mandatory</strong>
 *       to avoid thread-pool leakage between requests.</li>
 * </ol>
 */
public final class TenantContext {

    /** Default schema for global/public data and unauthenticated requests. */
    public static final String DEFAULT_SCHEMA = "public";

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_SCHEMA = new ThreadLocal<>();

    private TenantContext() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    public static void setCurrentTenant(String tenant) {
        CURRENT_TENANT.set(tenant);
    }

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void setCurrentSchema(String schema) {
        CURRENT_SCHEMA.set(schema);
    }

    public static String getCurrentSchema() {
        return CURRENT_SCHEMA.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_SCHEMA.remove();
    }
}
