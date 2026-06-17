package com.kntro.reqsai.discovery.infrastructure.exception;

import com.kntro.reqsai.shared.domain.exception.InfrastructureException;

/**
 * Thrown when an embedding provider returns an unexpected or malformed response.
 * Prefixes the message with the provider name for quick log triage.
 */
public class EmbeddingProviderException extends InfrastructureException {

    public EmbeddingProviderException(String provider, String reason) {
        super(DiscoveryInfrastructureError.EMBEDDING_FAILED,
                "[" + provider + "] " + reason,
                null);
    }

    public EmbeddingProviderException(String provider, String reason, Throwable cause) {
        super(DiscoveryInfrastructureError.EMBEDDING_FAILED,
                "[" + provider + "] " + reason,
                cause);
    }
}
