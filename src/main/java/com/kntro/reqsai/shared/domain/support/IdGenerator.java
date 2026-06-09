package com.kntro.reqsai.shared.domain.support;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

/**
 * Central generator for aggregate identifiers.
 * <p>
 * Produces <strong>UUID v7</strong> (time-ordered) via {@code uuid-creator}. Unlike random UUID v4,
 * v7 carries a millisecond timestamp prefix, so freshly generated ids are monotonically increasing.
 * As a PostgreSQL primary key this keeps B-tree inserts at the right edge of the index (no page
 * splits / fragmentation), while preserving the global-uniqueness and client-side-generation
 * properties DDD relies on (the id exists before the aggregate is persisted).
 * <p>
 * The JDK ({@link UUID}) only ships v3/v4 factories, hence the external library.
 */
public final class IdGenerator {

    private IdGenerator() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * @return a new time-ordered (v7) UUID
     */
    public static UUID newId() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
