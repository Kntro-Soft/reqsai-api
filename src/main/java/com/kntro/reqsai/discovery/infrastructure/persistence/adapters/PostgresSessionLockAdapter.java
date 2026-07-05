package com.kntro.reqsai.discovery.infrastructure.persistence.adapters;

import com.kntro.reqsai.discovery.application.port.SessionLockPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Postgres transaction-scoped advisory lock ({@code pg_advisory_xact_lock}) keyed on the discovery
 * session, chosen over an in-JVM {@code ReentrantLock} because the realtime passes run in separate
 * {@code REQUIRES_NEW} transactions: only a DB lock held until COMMIT guarantees the next pass sees the
 * previous pass's committed suggestions and advanced watermark (an in-JVM lock released when the method
 * returns can free before its transaction commits, reopening the read-committed race, and would not
 * hold across multiple app instances).
 *
 * <p>The lock is released automatically when the transaction commits or rolls back, so callers never
 * unlock explicitly. The session {@link UUID} is folded into the {@code bigint} key the advisory-lock
 * function expects.
 */
@Component
class PostgresSessionLockAdapter implements SessionLockPort {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void lockForSuggestion(UUID sessionId) {
        long key = lockKey(sessionId);
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:key)")
                .setParameter("key", key)
                .getSingleResult();
    }

    /** Folds the 128-bit session id into the 64-bit key {@code pg_advisory_xact_lock(bigint)} takes. */
    private static long lockKey(UUID sessionId) {
        return sessionId.getMostSignificantBits() ^ sessionId.getLeastSignificantBits();
    }
}
