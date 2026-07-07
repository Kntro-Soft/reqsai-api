package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

/**
 * Command to pause an active recording.
 */
public record PauseRecordingCommand(UUID projectId, UUID sessionId) {
}
