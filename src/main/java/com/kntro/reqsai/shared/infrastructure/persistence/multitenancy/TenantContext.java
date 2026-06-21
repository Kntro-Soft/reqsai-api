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

    /**
     * Captures the current tenant and schema into an immutable snapshot.
     * Falls back to {@link #DEFAULT_SCHEMA} when no tenant is bound.
     * Call this in domain event factory methods ({@code of()}) — the originating thread
     * still has the context at that point; async listener threads do not.
     */
    public static TenantSnapshot capture() {
        String t = CURRENT_TENANT.get();
        String s = CURRENT_SCHEMA.get();
        return new TenantSnapshot(
                t != null ? t : DEFAULT_SCHEMA,
                s != null ? s : DEFAULT_SCHEMA);
    }

    /**
     * Runs {@code action} under the given tenant context, clearing the thread-local in a
     * {@code finally} block. Useful when the caller runs on a thread that has no filter-managed
     * context (e.g. STT streaming callbacks, async event consumers).
     */
    public static void runWith(TenantSnapshot snapshot, Runnable action) {
        setCurrentTenant(snapshot.tenantId());
        setCurrentSchema(snapshot.tenantSchema());
        try {
            action.run();
        } finally {
            clear();
        }
    }

    /** Immutable snapshot of the tenant coordinates at a point in time. */
    public record TenantSnapshot(String tenantId, String tenantSchema) {}
}
