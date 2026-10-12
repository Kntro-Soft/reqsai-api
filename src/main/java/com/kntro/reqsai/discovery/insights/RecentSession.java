package com.kntro.reqsai.discovery.insights;

import java.time.Instant;
import java.util.UUID;

/**
 * A recently created discovery session, reduced to what a dashboard row shows.
 */
public record RecentSession(UUID id, UUID projectId, String title, String status, Instant createdAt) {
}
