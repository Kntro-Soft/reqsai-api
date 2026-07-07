package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

/**
 * Command to start AI-based requirement extraction for a discovery session.
 * The session must be in {@code STOPPED} or {@code FAILED} status.
 */
public record StartDiscoveryProcessingCommand(UUID sessionId) {}
