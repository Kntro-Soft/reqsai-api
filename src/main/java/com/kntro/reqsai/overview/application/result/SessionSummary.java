package com.kntro.reqsai.overview.application.result;

import java.time.Instant;
import java.util.UUID;

/** A recent discovery session as the dashboard lists it. */
public record SessionSummary(UUID id, UUID projectId, String title, String status, Instant createdAt) {
}
