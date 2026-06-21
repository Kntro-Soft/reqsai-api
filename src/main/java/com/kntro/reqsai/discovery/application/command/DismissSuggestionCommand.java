package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

/**
 * Analyst dismisses a pending suggestion — no backlog action taken.
 */
public record DismissSuggestionCommand(UUID sessionId, UUID suggestionId) {
}
