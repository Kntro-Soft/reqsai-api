package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

/**
 * Command to start recording a requirements-elicitation session.
 *
 * @param projectId   the project the session belongs to
 * @param sessionId   the identifier of the session to start
 */
public record StartRecordingCommand(UUID projectId, UUID sessionId) {
}
