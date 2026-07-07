package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

/**
 * Instructs the system to transcribe {@code audioBytes} via Whisper and save the result in the
 * session identified by {@code sessionId}, transitioning it from {@code DRAFT} to {@code STOPPED}.
 */
public record UploadTranscriptCommand(UUID sessionId, byte[] audioBytes, String filename) {
}
