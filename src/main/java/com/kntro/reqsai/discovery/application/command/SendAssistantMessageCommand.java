package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

/**
 * The analyst types a message to ReqsAI in the project's assistant chat.
 *
 * @param projectId the project the chat belongs to
 * @param content   what the analyst typed
 */
public record SendAssistantMessageCommand(UUID projectId, String content) {
}
