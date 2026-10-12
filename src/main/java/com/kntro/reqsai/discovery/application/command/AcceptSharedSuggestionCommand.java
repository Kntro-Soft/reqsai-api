package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

/** A reviewer with a session link accepts a suggestion, optionally editing the draft first. */
public record AcceptSharedSuggestionCommand(String token, UUID suggestionId, AcceptSuggestionCommand accept) {
}
