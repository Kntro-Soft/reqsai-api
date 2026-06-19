package com.kntro.reqsai.discovery.application.command;

import java.util.UUID;

/**
 * Opens a live STT stream for a discovery session that is already in {@code RECORDING} status.
 * The handler validates the status and delegates to {@link com.kntro.reqsai.discovery.application.port.StreamingTranscriptionPort}.
 *
 * @param sessionId the discovery session that will receive transcript segments
 */
public record StartSttStreamCommand(UUID sessionId) {}
