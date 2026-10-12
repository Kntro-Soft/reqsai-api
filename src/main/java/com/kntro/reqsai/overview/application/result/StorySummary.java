package com.kntro.reqsai.overview.application.result;

import java.time.Instant;
import java.util.UUID;

/** A recent story as the dashboard lists it. */
public record StorySummary(UUID id, UUID projectId, String title, String status, String priority, Instant createdAt) {
}
