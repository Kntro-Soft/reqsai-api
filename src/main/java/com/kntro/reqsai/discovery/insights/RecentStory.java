package com.kntro.reqsai.discovery.insights;

import java.time.Instant;
import java.util.UUID;

/** A recently created user story, reduced to what a dashboard row shows. */
public record RecentStory(UUID id, UUID projectId, String title, String status, String priority, Instant createdAt) {
}
