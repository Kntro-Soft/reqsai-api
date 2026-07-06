package com.kntro.reqsai.discovery.application.port;

import java.util.UUID;

/**
 * Serializes the realtime generation passes of a single discovery session.
 *
 * <p>Each pass runs in its own {@link org.springframework.transaction.annotation.Propagation#REQUIRES_NEW}
 * transaction. Under read-committed isolation two passes triggered ~seconds apart can both read the
 * PENDING suggestion set and the last-suggested watermark before the earlier pass commits, so the
 * earlier pass's freshly-persisted drafts are invisible to the later pass's dedup and both burst out
 * near-duplicate suggestions. Taking a per-session lock at the very start of a pass forces the passes
 * to run one after another: the later pass blocks until the earlier one commits and therefore sees its
 * committed suggestions and advanced watermark before it reads them.
 */
public interface SessionLockPort {

    /**
     * Acquires a session-scoped lock held for the remainder of the CURRENT transaction (released
     * automatically on commit or rollback). Blocks until the lock is granted, so it must be called
     * inside the transaction whose critical section (watermark + dedup reads, generation, persistence)
     * must be serialized against other passes of the same session.
     *
     * @param sessionId the discovery session to serialize passes for
     */
    void lockForSuggestion(UUID sessionId);
}
