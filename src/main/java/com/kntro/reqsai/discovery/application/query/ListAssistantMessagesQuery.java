package com.kntro.reqsai.discovery.application.query;

import java.util.UUID;

/**
 * The newest messages of a project's assistant chat.
 *
 * @param projectId the project the chat belongs to
 * @param limit     how many messages to return, newest kept (capped by the handler)
 */
public record ListAssistantMessagesQuery(UUID projectId, int limit) {
}
