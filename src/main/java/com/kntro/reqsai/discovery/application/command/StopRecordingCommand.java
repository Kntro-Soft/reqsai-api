package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

/**
 * Command to stop recording a session.
 */
public record StopRecordingCommand(UUID projectId, UUID sessionId) {
}
