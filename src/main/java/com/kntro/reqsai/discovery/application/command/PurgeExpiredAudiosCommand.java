package com.kntro.reqsai.discovery.application.command;

import java.time.Instant;

/**
 * Deletes, in the current tenant, up to {@code batchSize} audios stored at or
 * before {@code storedBefore} (now minus the organization's retention period)
 * and stamps them as purged at {@code now}.
 */
public record PurgeExpiredAudiosCommand(Instant storedBefore, Instant now, int batchSize) {
}
