package com.kntro.reqsai.shared.infrastructure.ai.embedding.exception;

import com.kntro.reqsai.shared.domain.exception.CommonError;
import com.kntro.reqsai.shared.domain.exception.InfrastructureException;

/**
 * Thrown when an embedding provider returns an unexpected or malformed response.
 * Prefixes the message with the provider name for quick log triage.
 */
public class EmbeddingProviderException extends InfrastructureException {

    public EmbeddingProviderException(String provider, String reason) {
        super(CommonError.AI_SERVICE_ERROR,
                "[" + provider + "] " + reason,
                null);
    }

    public EmbeddingProviderException(String provider, String reason, Throwable cause) {
        super(CommonError.AI_SERVICE_ERROR,
                "[" + provider + "] " + reason,
                cause);
    }
}
