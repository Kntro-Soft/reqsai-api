package com.kntro.reqsai.discovery.application.port;

import com.kntro.reqsai.discovery.domain.model.AssistantMessageRole;

/** One earlier message of the assistant chat, given to the model so follow-up questions make sense. */
public record ChatTurn(AssistantMessageRole role, String content) {
}
