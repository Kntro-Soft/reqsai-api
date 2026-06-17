package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

/**
 * Command to reset a completed/failed/stopped discovery session back to DRAFT.
 */
public record ResetSessionCommand(UUID projectId, UUID sessionId) {
}
