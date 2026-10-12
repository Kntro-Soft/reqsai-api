package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

/** A reviewer with a session link dismisses a suggestion. */
public record DismissSharedSuggestionCommand(String token, UUID suggestionId) {
}
