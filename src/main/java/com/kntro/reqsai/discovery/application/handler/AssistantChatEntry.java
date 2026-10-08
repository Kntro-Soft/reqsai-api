package com.kntro.reqsai.discovery.application.handler;

import com.kntro.reqsai.discovery.domain.model.AssistantMessage;
import com.kntro.reqsai.discovery.domain.model.Suggestion;

import java.util.List;

/** A chat message with the suggestions it raised, in their current review state (none for analyst messages). */
public record AssistantChatEntry(AssistantMessage message, List<Suggestion> suggestions) {
}
