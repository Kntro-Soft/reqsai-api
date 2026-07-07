package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

/**
 * Command to resume a paused recording.
 */
public record ResumeRecordingCommand(UUID projectId, UUID sessionId) {
}
