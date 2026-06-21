package com.kntro.reqsai.discovery.application.query;

import java.util.UUID;

/**
 * Returns all {@code PENDING} suggestions for a given discovery session.
 */
public record ListPendingSuggestionsQuery(UUID sessionId) {
}
