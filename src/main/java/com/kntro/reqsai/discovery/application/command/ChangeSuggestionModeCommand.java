package com.kntro.reqsai.discovery.application.command;

import com.kntro.reqsai.discovery.domain.model.SuggestionMode;

import java.util.UUID;

/** The analyst chooses when the assistant analyzes a session: on its own or only on demand (US46). */
public record ChangeSuggestionModeCommand(UUID projectId, UUID sessionId, SuggestionMode mode) {
}
