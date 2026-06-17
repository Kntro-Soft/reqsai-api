package com.kntro.reqsai.discovery.infrastructure.exception;

import com.kntro.reqsai.discovery.domain.exception.DiscoveryError;
import com.kntro.reqsai.shared.domain.exception.InfrastructureException;

/**
 * Factory for Discovery infrastructure exceptions — the infrastructure counterpart of
 * {@link com.kntro.reqsai.discovery.domain.exception.DiscoveryExceptions}.
 *
 * <p>Adapters must use this factory instead of constructing {@link InfrastructureException}
 * inline or importing domain exceptions. Centralising messages here makes them easy to update
 * and keeps adapters free of formatting logic.
 *
 * @see DiscoveryInfrastructureError
 */
public final class DiscoveryInfrastructureExceptions {

    private DiscoveryInfrastructureExceptions() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    // Generation

    public static InfrastructureException generationUnavailable() {
        return new InfrastructureException(
                DiscoveryInfrastructureError.REQUIREMENT_GENERATION_UNAVAILABLE,
                "Requirement generation service is not configured or unavailable",
                null);
    }

    public static InfrastructureException generationFailed(String reason) {
        return new InfrastructureException(
                DiscoveryError.REQUIREMENT_GENERATION_FAILED,
                "Requirement generation failed: " + reason,
                null);
    }

    public static InfrastructureException generationFailed(String reason, Throwable cause) {
        return new InfrastructureException(
                DiscoveryError.REQUIREMENT_GENERATION_FAILED,
                "Requirement generation failed: " + reason,
                cause);
    }

    // Transcription

    public static InfrastructureException transcriptionUnavailable() {
        return new InfrastructureException(
                DiscoveryInfrastructureError.TRANSCRIPTION_UNAVAILABLE,
                "Transcription service is not configured or unavailable",
                null);
    }

    public static InfrastructureException transcriptionFailed(String reason) {
        return new InfrastructureException(
                DiscoveryInfrastructureError.TRANSCRIPTION_UNAVAILABLE,
                "Transcription failed: " + reason,
                null);
    }

    public static InfrastructureException transcriptionFailed(String reason, Throwable cause) {
        return new InfrastructureException(
                DiscoveryInfrastructureError.TRANSCRIPTION_UNAVAILABLE,
                "Transcription failed: " + reason,
                cause);
    }
}
